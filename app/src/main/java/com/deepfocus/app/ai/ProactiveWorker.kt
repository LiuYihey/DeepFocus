package com.deepfocus.app.ai

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * 大肥鱼的主动 agent —— 后台唤醒任务
 *
 * 用户要求（2026-11-18）：
 * > 「注意我们去参照 dsh proactive agent 的插件设计，
 * > 我们的的大肥鱼也是定时随机唤醒的主动agent。」
 * > 「注意大肥鱼唤醒时主动发的消息可以通过通知栏出现，
 * > 所以用户不在app 内也能收到大肥鱼的消息。」
 *
 * 为什么用 WorkManager 而不是现有的进程内调度器
 * --------------------------------------------
 * 原来的 [ProactiveScheduler] 是个**进程内 coroutine 循环**：
 * App 进程一被系统杀掉，它就停了。
 * 而主动 agent 的价值恰恰在于「用户不在 App 里的时候她也在」。
 *
 * WorkManager 由系统调度：
 * - 进程死了照样能跑起来（会拉起进程）
 * - 不需要 SCHEDULE_EXACT_ALARM 那类敏感权限
 * - 最短周期 15 分钟（系统限制），够用了
 *
 * 两条路并存：
 * - **进程内调度器**保留 —— App 活着的时候它能更频繁地冒泡（10 分钟一跳）
 * - **WorkManager** 兜底 —— App 不在时保证她还会醒
 */
@HiltWorker
class ProactiveWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val scheduler: ProactiveScheduler
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            android.util.Log.i(TAG, "主动唤醒：跑一次主动判定")
            // 跑一次主动判定（跟进程内调度器同一个入口，逻辑不分叉）
            scheduler.tickOnce()

            // 待办催办：Worker 场景下即使调度器这次没命中，
            // 也要保证「用户写了待办但没人催」不会发生。
            nagTodoIfNeeded()

            android.util.Log.i(TAG, "主动唤醒：完成")
            Result.success()
        } catch (e: Exception) {
            android.util.Log.w(TAG, "主动唤醒失败: ${e.message}", e)
            // 失败就重试，别让一次网络抖动把这一轮吞掉
            Result.retry()
        }
    }

    /**
     * 有没做完的待办 → 让她自己催一句
     *
     * 用户要求（2026-11-19）：
     * > 「注意这个通知的内容是大肥鱼 agent 自己生成输出的内容，
     * >  而不是我们硬编码好的通知内容。」
     *
     * 所以这里**只负责触发**，文案全部交给 [ProactiveScheduler.forceTodoNag]
     * 走模型生成。生成失败（没配 API / 网络挂了）就什么都不发、
     * 也不记账 —— 下一次唤醒还能重试。
     *
     * 「一天只催一次」的记账在 `forceTodoNag` 内部，且**只有通知真的
     * 发出去了才记**（没给通知权限时不能把当天的机会用掉）。
     */
    private suspend fun nagTodoIfNeeded() {
        val line = scheduler.forceTodoNag()
        if (line != null) {
            android.util.Log.i(TAG, "待办催办已发出（agent 生成）：$line")
        }
    }

    companion object {
        private const val TAG = "ProactiveWorker"

        /** WorkManager 任务名 */
        private const val WORK_NAME = "deep_focus_proactive_wake"

        /** 周期（分钟）—— WorkManager 最短 15 分钟 */
        private const val INTERVAL_MINUTES = 30L

        /**
         * 排上后台唤醒任务
         *
         * 幂等：重复调用不会排出多个任务。
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ProactiveWorker>(
                INTERVAL_MINUTES, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                // KEEP：已经有任务在跑就别重排，否则会重置下次执行时间
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /** 停掉后台唤醒（用户关掉主动行为时用） */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
