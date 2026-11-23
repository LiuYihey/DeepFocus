package com.deepfocus.app.ai

import android.content.Context
import android.util.Log
import com.deepfocus.app.data.local.dao.ChatDao
import com.deepfocus.app.data.local.dao.FishMemoryDao
import com.deepfocus.app.data.local.entity.ChatMessageEntity
import com.deepfocus.app.data.local.entity.FishMemoryEntity
import com.deepfocus.app.util.FocusSessionState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 主动行为调度器
 *
 * 在应用进程存活期间跑一个循环，定期问引擎「要不要开口」。
 * 掷骰判定完全本地，命中了才调模型 —— 省电省额度。
 *
 * 调度节奏（对齐 dsh-heartbeat 的「60 分钟一跳 + ±20% 抖动」）：
 * 固定间隔容易被用户摸出规律，所以每次检查间隔会随机 ±20%。
 */
@Singleton
class ProactiveScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: ProactiveEngine,
    private val fishBrain: FishBrain,
    private val chatDao: ChatDao,
    private val fishStateEngine: FishStateEngine,
    private val aiConfigStore: AiConfigStore,
    private val memoryDao: FishMemoryDao,
    private val notifier: FishNotifier,
    private val todoRepository: com.deepfocus.app.data.repository.TodoRepository,
    private val conversationManager: ConversationManager
) {

    companion object {
        private const val TAG = "ProactiveScheduler"

        /** 调度循环的间隔（毫秒），实际会带 ±20% 抖动 */
        private const val TICK_INTERVAL_MS = 10 * 60 * 1000L

        /** 抖动比例 */
        private const val JITTER_RATIO = 0.2

        /** 调度器 Job 的持有者 key */
        private const val JOB_KEY = "deep_focus_proactive_job"
    }

    /** 当前调度 Job */
    private var job: Job? = null

    /** 连续没掷中的次数（用于命中线加成） */
    private var missStreak = 0

    /** 上次检查时间 */
    private var lastCheckAt = 0L

    /** 上次真正开口时间 */
    private var lastProactiveAt = 0L

    /** 已触发的动作记录（用于冷却判定） */
    private val recentActions = mutableListOf<Pair<ProactiveAction, Long>>()

    /** 今日已开口次数 */
    private var todayProactiveCount = 0

    /** 今日计数所属的日期 */
    private var todayKey: String = ""

    /**
     * 启动调度
     */
    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch {
            Log.i(TAG, "主动行为调度器已启动")
            while (isActive) {
                val interval = jitter(TICK_INTERVAL_MS)
                delay(interval)
                try {
                    tick()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "主动行为检查异常: ${e.message}")
                }
            }
        }
    }

    /**
     * 停止调度
     */
    fun stop() {
        job?.cancel()
        job = null
    }

    /**
     * 跑一次主动判定（不启动循环）
     *
     * 给 [ProactiveWorker] 用 —— 后台唤醒时走这条路，
     * 跟进程内循环是**同一份逻辑**，不分叉。
     */
    suspend fun tickOnce() {
        tick()
    }

    /**
     * 执行一次检查
     */
    suspend fun tick() {
        val now = System.currentTimeMillis()

        // 跨天重置
        val currentDay = dayKey(now)
        if (currentDay != todayKey) {
            todayKey = currentDay
            todayProactiveCount = 0
        }

        // 状态衰减
        val state = fishStateEngine.tick()

        // 没有配置 API 就不做无谓的检查
        val config = aiConfigStore.config.value
        if (!config.isValid || !config.enabled) return

        // 睡眠判定：长时间没互动且精力低 → 睡觉
        if (fishStateEngine.shouldSleep()) {
            fishStateEngine.sleep()
            // 「睡觉时总结上下文」——
            // 参照 DeepSeek Harness 的 dsh-memory：睡觉是这个整理的时刻。
            // 把今天这段对话压成一张交接笔记，下次（哪怕是新对话）她还记得。
            runCatching { conversationManager.summarize(conversationManager.ensureCurrent()) }
                .onFailure { Log.w(TAG, "睡觉前整理上下文失败：${it.message}") }
            fishBrain.pushEvent("大肥鱼睡着了。", "SLEEP")
            return
        }

        // 若之前在睡，现在醒了
        if (state.isSleeping && !fishStateEngine.shouldSleep()) {
            val slept = fishStateEngine.wake()
            val minutes = slept / 60000
            if (minutes >= 5) {
                fishBrain.pushEvent("大肥鱼睡了 $minutes 分钟，刚醒。", "WAKE")
            }
        }

        // 清理过期动作记录
        recentActions.removeAll { now - it.second > 60 * 60 * 1000L }

        // 待确认的记忆：有一条就说明对方刚说过什么值得记住的事，
        // 大肥鱼可以顺口提一句「诶你上次说的那个…」
        val pendingMemory = memoryDao.countByStatus(FishMemoryEntity.STATUS_CANDIDATE) > 0

        val decision = engine.decide(
            now = now,
            lastCheckAt = lastCheckAt,
            lastProactiveAt = lastProactiveAt,
            missStreak = missStreak,
            recentActions = recentActions,
            intensity = 40,
            todayProactiveCount = todayProactiveCount,
            isFocusing = FocusSessionState.isActive,
            isSleeping = state.isSleeping,
            isHungry = FishStateEngine.isHungry(state, now),
            hasPendingMemory = pendingMemory,
            // 2026-11-18 用户要求：大肥鱼知道用户今天的计划，唤醒时督促完成
            hasUndoneTodo = todoRepository.getTodayUndone().isNotEmpty()
        )

        lastCheckAt = now

        if (!decision.triggered) {
            missStreak++
            Log.d(TAG, "不开口：${decision.reason}")
            return
        }

        val action = decision.action ?: return
        missStreak = 0

        // 专注时如果是「催专注」「催待办」，得带上具体的东西
        var extraHint = ""
        if (action == ProactiveAction.NUDGE) {
            // 催专注必须带数字。「该专注了」谁都能说，
            // 「今天才 12 分钟，再来一段」才是这条鱼在陪他。
            extraHint = nudgeHint()
        } else if (action == ProactiveAction.TODO_NAG) {
            // 催办必须带具体的事。「还有事没做完」谁都能说，
            // 「那个 XX 还没弄吧」才像她记得你的计划。
            extraHint = todoNagHint()
        }

        val motive = decision.motive.takeIf { it.isNotBlank() }
            ?.let { name -> ProactiveMotive.entries.firstOrNull { it.name == name } }

        val message = fishBrain.runProactive(action, extraHint, motive)
        if (message != null) {
            lastProactiveAt = now
            todayProactiveCount++
            recentActions.add(action to now)
            Log.i(TAG, "主动开口：${action.name}（${decision.reason}）")

            // 消息分流（2026-11-22 用户要求）：
            // - 催待办（TODO_NAG）→ 通知栏，用户不在 App 也能被提醒
            // - 其它主动消息 → 不发通知，交给桌宠气泡
            //   （DesktopPetService 订阅最新 FISH 消息）；没开桌宠就只在 App 内可见
            //
            // 没给通知权限就静默跳过（FishNotifier 内部判断）。
            if (action == ProactiveAction.TODO_NAG) {
                val sent = notifier.notifyProactive("大肥鱼", message.content)
                // 这次要是正好催了待办，就记下「今天催过」，
                // 免得 Worker 兜底时再催一遍同样的事
                if (sent) notifier.markTodoNagSentToday()
            }
        }
    }

    /**
     * 强制让她催一次待办 —— **内容由她自己生成**
     *
     * 用户要求（2026-11-19）：
     * > 「注意这个通知的内容是大肥鱼 agent 自己生成输出的内容，
     * >  而不是我们硬编码好的通知内容。」
     *
     * 原来这里是硬编码模板「你今天还有「X」没做完呢，别拖了～」——
     * 谁看了都知道不是她说的。现在改成走 [FishBrain.runProactive]：
     * - 她拿得到今天没做完的事（`todoNagHint`）
     * - 她按自己当前的人格卡和状态决定怎么说
     * - 生成失败（没配 API / 网络挂了）就**不发**，也不记账，
     *   等下一次唤醒重试 —— 绝不退回硬编码文案
     *
     * @return 真正发出去的那句话；null = 不需要催 / 生成失败 / 今天已催过
     */
    suspend fun forceTodoNag(): String? {
        if (notifier.wasTodoNagSentToday()) return null
        if (todoRepository.getTodayUndone().isEmpty()) return null

        val message = try {
            fishBrain.runProactive(
                action = ProactiveAction.TODO_NAG,
                extraHint = todoNagHint(),
                motive = null
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "催办内容生成失败: ${e.message}")
            null
        } ?: return null

        val content = message.content.trim()
        if (content.isEmpty()) return null

        val sent = notifier.notifyProactive(
            title = "大肥鱼",
            body = content,
            id = FishNotifier.NOTIFICATION_ID_TODO_NAG
        )
        if (sent) notifier.markTodoNagSentToday()
        return content.takeIf { sent }
    }

    /**
     * 间隔抖动：实际间隔 = 基准 × (1 ± 20%)
     */
    private fun jitter(base: Long): Long {
        val factor = 1.0 + (Math.random() * 2 - 1) * JITTER_RATIO
        return (base * factor).toLong()
    }

    /**
     * 催专注时的具体事实
     *
     * 拿不到数据就退回一句空话，不硬编数字。
     */
    private suspend fun nudgeHint(): String {
        val world = fishBrain.buildWorldContext()
        val alias = fishBrain.getPersona().userAlias
        return FocusNarrative.nudgeFacts(world, alias)
    }

    /**
     * 催办时的具体事实
     *
     * 把用户今天还没做完的事列出来 ——
     * 模型自己决定怎么提，但她得知道提的是哪几件。
     */
    private suspend fun todoNagHint(): String {
        val undone = todoRepository.getTodayUndone()
        if (undone.isEmpty()) return "用户今天没有待办了。"
        val list = undone.take(3).joinToString("、") { it.content }
        return "用户今天还没做完的事：$list。" +
            "挑一件提一下就行，别一口气全念出来。"
    }

    private fun dayKey(time: Long): String {
        val c = Calendar.getInstance().apply { this.timeInMillis = time }
        return String.format(
            "%04d%02d%02d",
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH) + 1,
            c.get(Calendar.DAY_OF_MONTH)
        )
    }
}
