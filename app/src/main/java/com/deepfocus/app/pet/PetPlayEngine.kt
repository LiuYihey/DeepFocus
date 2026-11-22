package com.deepfocus.app.pet

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * 取全局共享 [PetPlayEngine] 的 Hilt 入口
 *
 * 桌宠是 Service、首页是 Composable —— 两边都不是 ViewModel，
 * 拿不到构造注入。用 EntryPoint 从 Application 上下文取同一个单例，
 * 这样「最近播过」的记忆才是真的共享的。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PetPlayEngineEntryPoint {
    fun petPlayEngine(): PetPlayEngine
}

/**
 * 桌宠互动引擎
 *
 * 把 109 个动作变成「玩法」而不是「随机循环」。
 *
 * ## 设计原则
 *
 * 1. **动作要有来由** —— 每个动作都对���某个用户行为或某个状态有响应，
 *    而不是随机乱播。随机会让桌宠显得「没在理你」。
 * 2. **反馈要成对** —— 玩家做了 X，桌宠给 Y，中间不能断。
 *    喂食 → 吃；摸头 → 舒服；拖动 → 被拎起来；甩出去 → 落地。
 * 3. **不重复** —— 刚播过的动作短期内不再出现，
 *    否则玩几次就腻了。
 * 4. **有惊喜但不吵闹** —— 恶作剧类动作（烟花、孔明灯、魔术）
 *    低频触发，是「惊喜」不是「噪音」。
 *
 * ## 玩法清单
 *
 * | 玩法 | 触发 | 反馈 |
 * |---|---|---|
 * | 投喂 | 用户喂食 | 白饭类动作（吃白饭 / 端饭碗） |
 * | 摸头 | 点击桌宠 | 撸毛 / 伸懒腰 / 抖毛 / 呆住 |
 * | 逗猫棒 | 快速拖动桌宠 | 追泡泡 / 追陀螺 / 抛接球 |
 * | 甩飞 | 用力拖动后松手 | 被拎悬空 → 重力下蹲 → 弹起奔跑 |
 * | 送礼 | 好感度里程碑 | 收红包 / 拆礼物 / 变魔术 |
 * | 思考 | AI 开始思考 | 思考冒泡 / 深度思考 |
 * | 工作 | 专注中 | 写代码 / 整理归档 / 忙个不停 |
 * | 陪伴 | 长时间没互动 | 看书 / 发呆 / 哼歌 / 碎碎念 |
 * | 节日 | 特定日期 | 堆雪人 / 端午粽子 / 中秋月饼 / 圣诞树 |
 * | 恶作剧 | 低频随机 | 放烟花 / 孔明灯 / 放河灯 / 魔术 |
 *
 * ## 是共享单例
 *
 * 首页大肥鱼和桌面桌宠**用同一份「最近播过」记忆** ——
 * 首页刚播过的动作，桌宠不会紧接着再播一遍。
 */
@Singleton
class PetPlayEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val random = Random.Default

    /** 最近播过的动作 id，避免立刻重复 */
    private val recent = ArrayDeque<String>()

    /**
     * 登场短剧的发牌堆（shuffle bag）
     *
     * 用户反馈：「我看还是经常能看到重复的 GIF，
     * 可以确保随机不放回的展示一轮后再重新开始吗？」
     *
     * 之前只记住最近 3 段 —— 44 个动作里挑一个，
     * 撞上最近 3 段的概率看着不高，但**短剧播完一次要好几秒**，
     * 用户盯着看的时间跨度长，实际感受就是「怎么又是这个」。
     *
     * 发牌堆的做法：把全部候选**洗牌**成一叠，从上往下取；
     * 取完一整轮（44 个都播过了）才重新洗牌。
     * 于是保证「一轮之内每个动作都出现且只出现一次」。
     *
     * 新一轮洗牌时会把**上一轮最后一个**挪到最前面，
     * 避免「刚播完的立刻又播」。
     */
    private val appearanceBag = ShuffleBag(random)

    /**
     * 喂食动作的发牌堆（shuffle bag）
     *
     * 和登场短剧一样是「一轮之内不重复」。
     * 能上台的吃的动作有 16 个 —— 只避开最近 6 个的话，
     * 玩家连着喂 7 次还是可能撞回同一个动作。
     *
     * 发牌堆保证「一轮 16 个喂食动作都出现且只出现一次」，
     * 取完才重新洗牌。
     */
    private val feedBag = ShuffleBag(random)

    /** 上次恶作剧的时间 */
    private var lastTrickAt = 0L

    /** 上次节日动作播过的「节日 key」，同一天不重复 */
    private var lastFestivalKey: String? = null

    /** 最近一次互动时间 */
    private var lastInteractionAt = System.currentTimeMillis()

    /** 当前正在播的动作 */
    var currentAction: PetAction? = null
        private set

    /**
     * 记录一次用户互动（任何形式的接触都算）
     *
     * 用来判断「用户是不是很久没理它了」——
     * 久没互动时它会自己找点事做（陪伴类动作）。
     */
    fun onUserInteraction() {
        lastInteractionAt = System.currentTimeMillis()
    }

    /** 距离上次互动过了多久 */
    fun idleMillis(): Long = System.currentTimeMillis() - lastInteractionAt

    /**
     * 挑一个动作
     *
     * @param tags 想要的玩法标签
     * @param avoidRecent 是否避开最近播过的
     */
    fun pick(tags: Set<String>, avoidRecent: Boolean = true): PetAction? {
        // 只从**素材真的在 APK 里**的动作里挑。
        //
        // 2026-11-19 用户要求把隔离动作（归一化宽度 > 768px）的素材
        // 从 APK 删掉，只留 72 个（66 个能上台 + 6 个补 PET/GIFT/THINK 玩法）。
        // 但动作库里仍有 109 条定义 —— 直接遍历 ALL 会抽到素材已被删的 id，
        // 表现是「动作触发了但动画播不出来」（PetActionLoader 静默失败）。
        val shipped = PetActionLibrary.ALL.filter { it.id in StageCatalog.SHIPPED_ACTIONS }
        val pool = shipped.filter { action ->
            action.tags.any { it in tags } && (!avoidRecent || action.id !in recent)
        }
        // 全都被避开了就放宽限制，别让桌宠卡住
        val fallback = if (pool.isEmpty()) {
            shipped.filter { action -> action.tags.any { it in tags } }
        } else pool
        if (fallback.isEmpty()) return null
        return fallback[random.nextInt(fallback.size)]
    }

    /**
     * 按 id 取动作，但只认**素材真的在 APK 里**的
     *
     * 2026-11-19 起隔离动作（归一化宽度 > 768px）的素材已从 APK 删除，
     * `PetActionLibrary.byId` 对这些 id 仍然返回非空 ——
     * 直接用它就永远走不到 `?:` 的兜底分支，动画静默播不出来。
     */
    private fun shippedById(id: String): PetAction? =
        PetActionLibrary.byId(id)?.takeIf { it.id in StageCatalog.SHIPPED_ACTIONS }

    /** 记下刚播过的动作 */
    private fun remember(action: PetAction) {
        recent.addLast(action.id)
        while (recent.size > 6) recent.removeFirst()
        currentAction = action
    }

    /**
     * 抽一段登场短剧
     *
     * 发牌堆：全部候选洗牌成一叠，从上往下取，
     * 取完一整轮才重新洗牌 —— **一轮之内不重复**。
     *
     * @param candidates 候选动作 id 列表（首页会先过滤掉素材缺失的）
     * @return 抽到的动作 id；候选为空时返回 null
     */
    fun nextAppearance(candidates: List<String>): String? =
        appearanceBag.next(candidates)

    // ==================== 具体玩法 ====================

    /**
     * 投喂反馈
     *
     * 吃的动作有 17 个，随机挑一个 —— 每次喂食都有新鲜感。
     *
     * 用**发牌堆**挑：一轮之内每个能上台的吃的动作只出现一次，
     * 取完一轮才重新洗牌（见 [feedBag]）。
     *
     * 只从**能上台的**动作里挑（[StageCatalog.STAGE_ACTIONS]）：
     * `dakou-chi-lingshi` 归一化后宽 897px，超过 768px 上限，
     * 在首页上会显得小一号 —— 舞台上的角色高度必须一致。
     */
    fun onFeed(): PetAction? {
        val feedIds = PetActionLibrary.ALL
            .filter { PetActionTag.FEED in it.tags }
            .filter { it.id in StageCatalog.STAGE_ACTIONS }
            .map { it.id }
        val id = feedBag.next(feedIds) ?: return null
        val action = PetActionLibrary.byId(id) ?: return null
        remember(action)
        return action
    }

    /**
     * 摸头 / 点击反馈
     *
     * 被人摸是亲昵行为，动作要「软」一点：
     * 撸毛、伸懒腰、抖毛、呆住、照镜子。
     */
    fun onPet(): PetAction? {
        val a = pick(setOf(PetActionTag.PET, PetActionTag.MOOD)) ?: return null
        remember(a)
        return a
    }

    /**
     * 逗猫棒反馈（快速拖动）
     */
    fun onPlay(): PetAction? {
        val a = pick(setOf(PetActionTag.PLAY)) ?: return null
        remember(a)
        return a
    }

    /**
     * 被拎起来（拖动中）
     */
    fun onGrabbed(): PetAction? {
        val a = shippedById("beishubiao-tuozhuai-xuankong-fankui")
            ?: pick(setOf(PetActionTag.THROWN))
        a?.let { remember(it) }
        return a
    }

    /**
     * 落地反馈（甩飞之后）
     *
     * 序列：被拎悬空 → 重力压扁 → 弹起跑掉。
     * 这是三段式物理反馈，不是单个动作。
     */
    fun onLanded(): List<PetAction> {
        val seq = listOfNotNull(
            shippedById("yuandi-zhongli-xiadun-yasuo"),
            shippedById("yuandi-zuozhuan-benpao")
        )
        seq.firstOrNull()?.let { remember(it) }
        return seq
    }

    /**
     * 送礼反馈（好感度里程碑）
     */
    fun onGift(): PetAction? {
        val a = pick(setOf(PetActionTag.GIFT, PetActionTag.TRICK)) ?: return null
        remember(a)
        return a
    }

    /**
     * 思考反馈（AI 开始思考时）
     */
    fun onThinking(): PetAction? {
        val a = pick(setOf(PetActionTag.THINK)) ?: return null
        remember(a)
        return a
    }

    /**
     * 工作反馈（专注中）
     */
    fun onWorking(): PetAction? {
        val a = pick(setOf(PetActionTag.WORK)) ?: return null
        remember(a)
        return a
    }

    /**
     * 陪伴反馈（长时间没互动，它自己找点事做）
     *
     * @param idleMillis 闲置时长
     */
    fun onIdle(idleMillis: Long): PetAction? {
        // 刚 interacted 过就别急着自言自语
        if (idleMillis < 3 * 60 * 1000L) return null
        val a = pick(setOf(PetActionTag.IDLE, PetActionTag.TALK)) ?: return null
        remember(a)
        return a
    }

    /**
     * 睡眠反馈（该睡了）
     */
    fun onSleep(): PetAction? {
        val a = shippedById("shuijiao-kaishi")
            ?: pick(setOf(PetActionTag.SLEEP))
        a?.let { remember(it) }
        return a
    }

    /**
     * 醒来反馈
     */
    fun onWake(): PetAction? {
        val a = shippedById("shuijiao-qilai")
            ?: pick(setOf(PetActionTag.SLEEP))
        a?.let { remember(it) }
        return a
    }

    /**
     * 节日反馈
     *
     * 按当天日期挑一个应景动作：
     * - 冬天 → 堆雪人
     * - 端午 → 粽子
     * - 中秋 → 月饼
     * - 圣诞元旦 → 圣诞树
     * - 平时 → 写福字 / 套糖画
     *
     * 同一天只播一次，播完换成别的（不重复）。
     */
    fun onFestival(): PetAction? {
        val cal = Calendar.getInstance()
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)

        // 节日 key = 年-月-日，同一天不重复播
        val todayKey = "${cal.get(Calendar.YEAR)}-$month-$day"

        // 候选：先看今天是不是特殊日子，再退到通用节日动作
        val candidates = when {
            month == 2 && day == 1 -> listOf("zhongqiu-shangyue-chi-yuebing")
            month == 5 && day == 5 -> listOf("chi-zongzi")
            month == 6 && day == 25 -> listOf("chi-zongzi")
            month == 10 && day == 1 -> listOf("chi-chongyanggao")
            month == 12 && (day == 24 || day == 25) -> listOf("zhuangdian-shengdanshu")
            month == 12 && day == 31 -> listOf("zhuangdian-shengdanshu")
            month == 1 && day == 1 -> listOf("zhuangdian-shengdanshu")
            month == 11 || month == 12 || month == 1 || month == 2 -> {
                // 冬天：堆雪人、换装、围炉
                listOf("duixueren", "zhengti-huanzhuang-shise", "shuan-huoguo")
            }
            else -> listOf("xie-fuzi", "taotang-nanguadeng", "fang-yanhua")
        }
            // 同样只认 APK 里真的有的素材，否则抽到隔离动作就静默播不出来
            .filter { it in StageCatalog.SHIPPED_ACTIONS }
            .filter { lastFestivalKey != it }

        if (candidates.isEmpty()) return null
        val id = candidates[random.nextInt(candidates.size)]
        val action = PetActionLibrary.byId(id) ?: return null
        lastFestivalKey = id
        remember(action)
        return action
    }

    /**
     * 恶作剧 / 惊喜（低频随机）
     *
     * 这是「惊喜」不是「噪音」：
     * - 至少 30 分钟冷却
     * - 3% 概率触发
     * - 动作本身很有表现力（烟花、孔明灯、河灯、魔术、凭空生花）
     */
    fun onTrick(): PetAction? {
        val now = System.currentTimeMillis()
        if (now - lastTrickAt < 30 * 60 * 1000L) return null
        if (random.nextInt(100) > 3) return null

        val a = pick(setOf(PetActionTag.TRICK)) ?: return null
        remember(a)
        lastTrickAt = now
        return a
    }

    /**
     * 是不是深夜（该睡了）
     */
    fun isNightTime(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour >= 23 || hour < 6
    }

    /**
     * 资源路径
     */
    fun assetPath(action: PetAction): String = PetActionLibrary.assetPath(action)

    /**
     * 静态兜底路径
     */
    fun stillPath(action: PetAction): String = PetActionLibrary.stillPath(action)
}
