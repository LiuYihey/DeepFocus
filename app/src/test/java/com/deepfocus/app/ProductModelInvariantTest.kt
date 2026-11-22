package com.deepfocus.app

import com.deepfocus.app.ai.FishMood
import com.deepfocus.app.ai.FishMoodResolver
import com.deepfocus.app.ai.FishStateEngine
import com.deepfocus.app.ai.MoodStore
import com.deepfocus.app.ai.StickerCatalog
import com.deepfocus.app.ai.StickerMarker
import com.deepfocus.app.data.local.entity.FishStateEntity
import com.deepfocus.app.data.repository.FishRepository
import com.deepfocus.app.pet.PetActionLibrary
import com.deepfocus.app.pet.PetActionTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 产品模型的不变量测试
 *
 * 锁住几条容易被后续改动悄悄破坏的决策：
 * 1. 喂食**涨一点好感度**（+1），且不能替代专注
 * 2. **没有「心情（mood）」数值维度** —— 情绪由好感度档位推导
 * 3. 「饿」的判定**只看距上次喂食是否超过 24 小时**，与饱腹度无关
 * 4. **所有「在吃东西」的动作都要挂 FEED**，不能只有一两个
 *    （2026-11-18 用户决定：「可以有多个吃东西的表情包随机出现吗」）
 * 5. 状态词是**她自己写的**（`set_mood`），不是机器词表
 *
 * 这些是纯逻辑，不需要设备，跑 JVM 单元测试即可。
 */
class ProductModelInvariantTest {

    // ===== 3. 「饿」的判定只看 24 小时 =====

    @Test
    fun `从没喂过算饿`() {
        val state = FishStateEntity(id = 1, lastFedAt = 0L)
        assertTrue(FishStateEngine.isHungry(state, now = 1_700_000_000_000L))
    }

    @Test
    fun `刚喂过不饿`() {
        val now = 1_700_000_000_000L
        val state = FishStateEntity(id = 1, lastFedAt = now - 60_000L)
        assertFalse(FishStateEngine.isHungry(state, now))
    }

    @Test
    fun `超过24小时就饿`() {
        val now = 1_700_000_000_000L
        val justUnder = FishStateEngine.HUNGRY_AFTER_MILLIS - 1_000L
        val state = FishStateEntity(id = 1, lastFedAt = now - justUnder)
        assertFalse(FishStateEngine.isHungry(state, now))

        val over = FishStateEngine.HUNGRY_AFTER_MILLIS + 1_000L
        val state2 = FishStateEntity(id = 1, lastFedAt = now - over)
        assertTrue(FishStateEngine.isHungry(state2, now))
    }

    @Test
    fun `饱腹度很高也照样算饿`() {
        // 饱腹度 100（刚吃饱），但 25 小时没喂 → 判定为饿。
        // 这条规则就是「饿只看时间线」的证明。
        val now = 1_700_000_000_000L
        val state = FishStateEntity(
            id = 1,
            satiety = 100,
            lastFedAt = now - 25 * 60 * 60 * 1000L
        )
        assertTrue(FishStateEngine.isHungry(state, now))
    }

    @Test
    fun `饱腹度很低但刚喂过也不算饿`() {
        val now = 1_700_000_000_000L
        val state = FishStateEntity(
            id = 1,
            satiety = 0,
            lastFedAt = now - 60_000L
        )
        assertFalse(FishStateEngine.isHungry(state, now))
    }

    // ===== 1 + 2. 状态分档 =====

    @Test
    fun `饿了时优先于好感度档位`() {
        // 好感度 350（最高档），但饿了 → 应该报 STARVING，不是 DEVOTED
        val mood = FishMoodResolver.resolve(
            affinity = 350,
            satiety = 100,
            isHungry = true
        )
        assertEquals(FishMood.Tier.STARVING, mood.tier)
    }

    @Test
    fun `不饿时按好感度分档`() {
        val mood = FishMoodResolver.resolve(
            affinity = 350,
            satiety = 50,
            isHungry = false
        )
        assertEquals(FishMood.Tier.DEVOTED, mood.tier)
    }

    @Test
    fun `刚睡醒优先`() {
        val mood = FishMoodResolver.resolve(
            affinity = 350,
            satiety = 100,
            isHungry = true,
            justWokeUp = true
        )
        assertEquals(FishMood.Tier.GROGGY, mood.tier)
    }

    @Test
    fun `吃饱了走 FED 档`() {
        val mood = FishMoodResolver.resolve(
            affinity = 10,
            satiety = 95,
            isHungry = false
        )
        assertEquals(FishMood.Tier.FED, mood.tier)
    }

    @Test
    fun `六档里程碑与内置卡阈值一致`() {
        // 好感度档位必须和 6 张内置卡一一对应
        val thresholds = listOf(
            FishMoodResolver.AFFINITY_SHY,
            FishMoodResolver.AFFINITY_FAMILIAR,
            FishMoodResolver.AFFINITY_CLOSE,
            FishMoodResolver.AFFINITY_BONDED,
            FishMoodResolver.AFFINITY_DEVOTED
        )
        assertEquals(listOf(10, 50, 100, 200, 350), thresholds)
    }

    // ===== 4. 所有「在吃东西」的动作都要挂 FEED =====

    /**
     * 2026-11-18 用户决定：
     * 「点击大白饭后大肥鱼吃饭的表情包只有一个吗，
     * 不可以有多个吃东西的表情包随机出现吗」
     *
     * 所以 FEED 标签必须覆盖**所有**吃东西的动作，
     * 而且**不能只有一两个**（只有一个就等于没随机）。
     */
    @Test
    fun `所有吃东西的动作都挂 FEED`() {
        val feedIds = PetActionLibrary.ALL
            .filter { PetActionTag.FEED in it.tags }
            .map { it.id }
            .toSet()

        val shouldBeFeed = listOf(
            "chi-baifan",          // 抱着一大碗白饭猛吃
            "chi-wancan",          // 端着饭碗认真吃
            "chi-wucan",           // 捧着午餐盒吃饭
            "chi-changshoumian",   // 抱着超长的手擀面拉扯着吃
            "chi-zongzi",          // 捧着一只粽子剥
            "chijiaozi",           // 捧着饺子蘸醋吃
            "chitangyuan",         // 举着一颗汤圆，小口咬开
            "chi-labazhou",        // 端着一碗粥呼噜呼噜喝
            "chi-xigua",           // 抱着半个西瓜啃
            "chi-tanghulu",        // 举着一串糖葫芦啃
            "chi-niangao",         // 捧着年糕大口咬
            "chi-qingtuan",        // 举着一枚青团小心咬
            "chi-chongyanggao",    // 小口啃重阳糕
            "chi-dazhaxie",        // 举着大虾和大蟹一起啃
            "chi-zaocan",          // 端着早茶慢慢吃
            "chi-bingqilin-ronghua", // 抱着冰麒麟冰块
            "dakou-chi-lingshi"    // 抓起零食大口往嘴里塞
        )
        for (id in shouldBeFeed) {
            assertTrue("「$id」是吃东西的动作，该挂 FEED", feedIds.contains(id))
        }
    }

    @Test
    fun `FEED 动作数量够随机`() {
        // 「只有一个表情包」= 没有随机可言。
        // 少于 8 个就不够随机了。
        val feedCount = PetActionLibrary.ALL.count { PetActionTag.FEED in it.tags }
        assertTrue("FEED 动作只有 $feedCount 个，不够随机", feedCount >= 8)
    }

    @Test
    fun `白饭动作都在动作库里`() {
        assertNotNull(PetActionLibrary.byId("chi-baifan"))
        assertNotNull(PetActionLibrary.byId("chi-wancan"))
    }

    @Test
    fun `端详硬币不当喂食反馈`() {
        // chi-token 是「举着一枚硬币端详」—— 不是在吃东西
        val feedIds = PetActionLibrary.ALL
            .filter { PetActionTag.FEED in it.tags }
            .map { it.id }
            .toSet()
        assertFalse("「chi-token」不是吃东西，不该挂 FEED", feedIds.contains("chi-token"))
    }

    @Test
    fun `动作库数量没变`() {
        assertEquals(109, PetActionLibrary.ALL.size)
    }

    @Test
    fun `动作序号连续且与 id 对得上`() {
        PetActionLibrary.ALL.forEachIndexed { idx, action ->
            assertEquals("序号断层：${action.id}", idx + 1, action.index)
        }
        assertEquals(
            PetActionLibrary.ALL.size,
            PetActionLibrary.ALL.map { it.id }.distinct().size
        )
    }

    // ===== 1. 喂食涨好感度，但不能替代专注 =====

    /**
     * 2026-11-18 用户决定：
     * 「点击大白饭后饱腹度增加，同时好感度也可以慢慢增加」，
     * 并且好感度要能解锁人格卡。
     *
     * 但**量级必须压住** —— 喂一次 +1，专注 10 分钟也才 +1。
     * 不然「躺着点大白饭」比「真的专注」划算，好感度就贬值了。
     */
    @Test
    fun `喂一次饭只涨 1 点好感度`() {
        assertEquals(1, FishRepository.AFFINITY_PER_FEED)
    }

    @Test
    fun `喂饭涨的好感度不超过专注 10 分钟的量`() {
        // 喂一次 = 1 点；专注 10 分钟 = 1 点。
        // 喂饭**不能**比专注划算。
        val focusTenMin = FishRepository.focusAffinityBonus(10 * 60)
        assertEquals(1, focusTenMin)
        assertTrue(
            "喂一次（${FishRepository.AFFINITY_PER_FEED}）不能多于专注 10 分钟（$focusTenMin）",
            FishRepository.AFFINITY_PER_FEED <= focusTenMin
        )
    }

    @Test
    fun `专注不足 1 分钟不给好感度`() {
        assertEquals(0, FishRepository.focusAffinityBonus(0))
        assertEquals(0, FishRepository.focusAffinityBonus(59))
    }

    @Test
    fun `专注时长换算成好感度`() {
        // 不足一个 10 分钟间隔也给 1 —— 免得「专注了 3 分钟」一点反馈都没有
        assertEquals(1, FishRepository.focusAffinityBonus(60))
        assertEquals(1, FishRepository.focusAffinityBonus(3 * 60))
        assertEquals(1, FishRepository.focusAffinityBonus(10 * 60))
        assertEquals(2, FishRepository.focusAffinityBonus(20 * 60))
        assertEquals(6, FishRepository.focusAffinityBonus(60 * 60))
    }

    @Test
    fun `光靠喂饭刷不满最高档人格卡`() {
        // 最高档门槛 350 点。每天最多喂 5 次，每次 +1 = 一天 +5。
        // 喂满 70 天才够 —— 专注才是主线，喂饭只是「顺手也表示亲近」。
        val daysOfFeedingToMax =
            FishMoodResolver.AFFINITY_DEVOTED / (FishRepository.MAX_FEED_PER_DAY * FishRepository.AFFINITY_PER_FEED)
        assertTrue(
            "喂饭刷满最高档只要 $daysOfFeedingToMax 天，太便宜了",
            daysOfFeedingToMax >= 30
        )
    }

    // ===== 5. 状态词是模型自己写的 =====

    @Test
    fun `接受短状态词`() {
        assertTrue(MoodStore.isAcceptableMood("吃撑了"))
        assertTrue(MoodStore.isAcceptableMood("有点想你"))
        assertTrue(MoodStore.isAcceptableMood("困得睁不开眼"))
    }

    @Test
    fun `拒绝空状态词`() {
        assertFalse(MoodStore.isAcceptableMood(null))
        assertFalse(MoodStore.isAcceptableMood(""))
        assertFalse(MoodStore.isAcceptableMood("   "))
    }

    @Test
    fun `拒绝太长的状态词`() {
        // 太长的是句子，不是状态词 —— 塞进胶囊会把首页挤爆
        val tooLong = "我今天有点想你因为早上那碗粥太好喝了"
        assertFalse(MoodStore.isAcceptableMood(tooLong))
        // 边界：正好 12 字收，13 字不收
        assertTrue(MoodStore.isAcceptableMood("一二三四五六七八九十甲乙"))
        assertFalse(MoodStore.isAcceptableMood("一二三四五六七八九十甲乙丙"))
    }

    // ===== 6. 待办催办：没发出去就不记账 =====

    /**
     * 2026-11-18 修的 bug：
     * 用户还没给通知权限时，第一次唤醒把「今天催过」写下了，
     * 但通知被静默跳过了 —— 之后一整天都不会再催，
     * 用户永远收不到待办提醒。
     *
     * 契约：**通知真的发出去，才记「今天催过」**。
     * 所以「是否记账」必须由 `notifyProactive` 的返回值决定。
     *
     * 这条锁的是**源码结构**（因为 FishNotifier 依赖 Android Context，
     * 没法直接 JVM 测），但它能挡住「谁又改回无条件记账」这种回归。
     */
    @Test
    fun `待办催办只在通知发出去时才记账`() {
        // 2026-11-19 重构：催办逻辑从 Worker 挪到了 ProactiveScheduler.forceTodoNag
        // （因为文案要由 agent 生成，Worker 只负责触发）
        val src = readSource("ai/ProactiveScheduler.kt")
        assertTrue(
            "forceTodoNag 必须按 notifyProactive 的返回值决定要不要记账",
            src.contains("if (sent) notifier.markTodoNagSentToday()")
        )
    }

    /**
     * 2026-11-19 用户要求：
     * > 「注意这个通知的内容是大肥鱼 agent 自己生成输出的内容，
     * >  而不是我们硬编码好的通知内容。」
     *
     * 所以催办文案必须来自 `fishBrain.runProactive(...)` 的返回，
     * 不能是拼出来的模板字符串。
     */
    @Test
    fun `待办催办文案必须由 agent 生成`() {
        val src = readSource("ai/ProactiveScheduler.kt")
        val fn = src.substringAfter("suspend fun forceTodoNag")
            .substringBefore("private fun jitter")
        assertTrue(
            "forceTodoNag 必须调用 fishBrain.runProactive 拿文案",
            fn.contains("fishBrain.runProactive(")
        )
        assertTrue(
            "通知正文必须用 agent 返回的 content",
            fn.contains("body = content")
        )
        assertFalse(
            "不能硬编码催办模板（例如「没做完呢，别拖了」）",
            fn.contains("没做完呢") || fn.contains("别拖了")
        )
        assertFalse(
            "Worker 里也不该再拼文案 —— 它只负责触发",
            readSource("ai/ProactiveWorker.kt").contains("没做完呢")
        )
    }

    @Test
    fun `notifyProactive 返回是否真的发出去了`() {
        val notifierSrc = readSource("ai/FishNotifier.kt")
        assertTrue(
            "FishNotifier.notifyProactive 必须返回 Boolean（发出去 = true）",
            notifierSrc.contains("fun notifyProactive(") &&
                notifierSrc.contains("): Boolean {")
        )
    }

    // ===== 7. 表情包：模型自己决定发不发（2026-11-22 用户反馈）=====

    @Test
    fun `模型没选表情包时不配图`() {
        assertNull(
            "模型没选 id 时不能凭空配一张（用户要求：自行选择是否发送表情包）",
            StickerMarker.resolve(null)
        )
    }

    @Test
    fun `模型选的表情包按 id 取`() {
        val sticker = StickerMarker.resolve("st_cheer_01")
        assertNotNull(sticker)
        assertEquals("st_cheer_01", sticker!!.id)
    }

    @Test
    fun `清单外的表情包 id 不配图`() {
        assertNull(
            "模型编出来的 id 不能拿去配图",
            StickerMarker.resolve("st_不存在的_99")
        )
    }

    @Test
    fun `没有关键词兜底`() {
        val src = readSource("ai/StickerCatalog.kt")
        assertFalse(
            "不能再按关键词猜表情包（用户反馈：agent 没读表情包含义就随便发一个糊弄）",
            src.contains("fun guessByKeyword(")
        )
    }

    @Test
    fun `表情包清单带着含义进 prompt`() {
        val catalog = StickerCatalog.catalogForPrompt()
        assertTrue(
            "表情包清单必须带描述，模型才知道每张图是什么",
            catalog.contains("st_cheer_01") && catalog.contains("：")
        )
    }

    // ===== 8. 聊天：会话 / 记忆连贯（2026-11-22 用户需求）=====

    @Test
    fun `聊天消息带会话归属`() {
        val src = readSource("ai/FishBrain.kt")
        assertTrue(
            "聊天消息必须挂 conversationId，否则开新对话会丢历史",
            src.contains("conversationId = conversationId")
        )
    }

    @Test
    fun `新对话会把旧会话压成交接笔记`() {
        val src = readSource("ai/ConversationManager.kt")
        assertTrue(
            "开启新对话必须压交接笔记（记忆连贯靠它）",
            src.contains("suspend fun startNew(") && src.contains("summarize(previous)")
        )
    }

    @Test
    fun `交接笔记会注入下一段对话`() {
        val src = readSource("ai/FishBrain.kt")
        assertTrue(
            "新会话开局必须注入交接笔记",
            src.contains("handoffNote()") && src.contains("【上一段对话")
        )
    }

    @Test
    fun `删会话会连带删记忆`() {
        val src = readSource("ai/ConversationManager.kt")
        assertTrue(
            "删会话必须连带删这段对话抽出来的记忆",
            src.contains("memoryDao.deleteByConversation(id)")
        )
    }

    /** 读 main 源集里的一个文件（测试跑在 JVM 上，读不到 classpath 里的源码） */
    private fun readSource(relativePath: String): String {
        val candidates = listOf(
            "../app/src/main/java/com/deepfocus/app/$relativePath",
            "src/main/java/com/deepfocus/app/$relativePath",
            "../app/src/main/java/com/deepfocus/app/" + relativePath.removePrefix("ai/")
        )
        for (c in candidates) {
            val f = java.io.File(c)
            if (f.exists()) return f.readText()
        }
        throw AssertionError(
            "读不到源码 $relativePath，试过的路径：${candidates.joinToString()}"
        )
    }
}
