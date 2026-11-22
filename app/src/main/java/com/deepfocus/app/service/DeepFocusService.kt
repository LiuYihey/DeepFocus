package com.deepfocus.app.service

import android.app.AlarmManager
import android.app.Notification
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.deepfocus.app.DeepFocusApp
import com.deepfocus.app.MainActivity
import com.deepfocus.app.R
import com.deepfocus.app.detection.AppDetectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 前台保活 + 专注锁机轮询服务
 *
 * 核心作用（专注锁机）：
 * 专注计时期间用户切到别的应用 → 把 DeepFocus 拉回前台。
 * 国产 ROM（MIUI/EMUI/ColorOS/vivo 等）会丢弃或延迟无障碍事件，
 * 因此本服务每 1.5 秒通过 UsageStatsManager 主动轮询前台应用作为兜底引擎。
 */
class DeepFocusService : android.app.Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null

    /**
     * 上次检测到的前台包名缓存
     *
     * 问题：getForegroundPackage() 在用户停留同一应用 >5s 后，
     * queryEvents 无新事件、queryUsageStats 的 lastTimeUsed 可能不刷新，
     * 两个策略都返回 null，导致整个检测块被跳过。
     *
     * 解决：每次成功获取前台包名时缓存，getForegroundPackage() 返回 null 时
     * 回退使用缓存值（CACHE_VALIDITY_MS 内有效）。
     */
    private var lastKnownForeground: String? = null
    private var lastKnownForegroundTime: Long = 0L

    override fun onCreate() {
        super.onCreate()
        // 确保守护开关状态已从 SharedPreferences 恢复
        // 正常流程下 DeepFocusApp.onCreate 已调用 init，但本服务可能被
        // AlarmManager / BootReceiver 独立拉起（进程刚重建），双重保险确保状态就绪
        AppDetectionManager.init(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        // 需求 1+4：前台服务启动时确认保活任务已注册
        // 服务常驻时进程存活，无障碍服务不会被解绑；服务被杀死后由保活任务重建进程
        KeepAliveManager.schedule(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 启动 / 重启轮询任务
        startPollingIfNeeded()
        // 返回 START_STICKY，服务被杀后系统会尝试重建
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    /**
     * 修复 Bug 3：用户从最近任务列表划掉 app 时触发
     * 原代码无此回调，导致用户划掉 app 后服务被杀、轮询停摆，
     * 无障碍服务虽显示"已启用"但实际已失效（进程被杀，事件无法被处理）。
     *
     * 此处调度一个一次性的 AlarmManager 闹钟，1 秒后拉起服务：
     * - 若守护开关已关闭，ServiceRestartReceiver.onReceive 会直接 return，不重启
     * - 若守护开关仍开启，则拉起前台服务恢复轮询引擎
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        scheduleRestart()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        pollingJob?.cancel()
        serviceScope.cancel()
        // 修复 Bug 3：服务被系统回收时调度重启
        // 与 onTaskRemoved 相同的恢复机制，确保服务在任何被杀场景下都能恢复
        // 若守护开关已关闭，ServiceRestartReceiver 会直接 return，不会错误重启
        scheduleRestart()
        super.onDestroy()
    }

    /**
     * 调度一次性服务重启闹钟（修复 Bug 3）
     * 1 秒后触发 ServiceRestartReceiver，由其检查守护开关状态决定是否拉起服务
     */
    private fun scheduleRestart() {
        if (!AppDetectionManager.isProtectionEnabled) return
        try {
            val alarmManager = getSystemService(android.content.Context.ALARM_SERVICE) as? AlarmManager
                ?: return
            val intent = android.content.Intent(this, ServiceRestartReceiver::class.java).apply {
                action = ServiceRestartReceiver.ACTION_RESTART
            }
            val pendingIntent = android.app.PendingIntent.getBroadcast(
                this,
                ServiceRestartReceiver.REQUEST_CODE_RESTART,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            // 一次性闹钟，1 秒后触发（setExact 比 setInexactRepeating 更及时）
            // Android 12+ 精确闹钟需要 SCHEDULE_EXACT_ALARM 权限且用户可随时撤销，
            // 未授权时 setExactAndAllowWhileIdle 会抛 SecurityException →
            // 降级为 setAndAllowWhileIdle（Doze 下仍可唤醒，仅时间不精确）。
            val triggerAt = android.os.SystemClock.elapsedRealtime() + 1000L
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
                !KeepAliveManager.canScheduleExactAlarms(this)
            ) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            // 调度失败静默忽略，依赖 START_STICKY 和周期闹钟兜底
        }
    }

    /**
     * 启动前台应用轮询任务（幂等，重复调用安全）
     * 每 POLL_INTERVAL_MS 毫秒检测一次前台应用：
     * 1. 心跳检测无障碍服务连接状态，断开时尝试触发重连
     * 2. 若专注模式激活且用户切到其他应用 → 拉回 DeepFocus（专注锁机）
     *
     * getForegroundPackage() 在用户停留同一应用 >5s 后可能返回 null，
     * 因此内部回退使用上次缓存的前台包名（CACHE_VALIDITY_MS 内有效），
     * 保证长停留场景下检测不中断。
     */
    private fun startPollingIfNeeded() {
        if (pollingJob?.isActive == true) return
        var heartbeatCounter = 0
        pollingJob = serviceScope.launch {
            while (true) {
                try {
                    // 每 HEARTBEAT_INTERVAL_TICKS 次轮询做一次心跳检测
                    heartbeatCounter++
                    if (heartbeatCounter >= HEARTBEAT_INTERVAL_TICKS) {
                        heartbeatCounter = 0
                        performAccessibilityHeartbeat()
                    }

                    // 获取前台包名（getForegroundPackage 内部已集成缓存策略）
                    val fg = getForegroundPackage()

                    if (fg != null) {
                        // 专注模式防分心检测（专注锁机）
                        // 用户在专注计时中切到其他应用 → 立刻拉回 DeepFocus
                        //
                        // 豁免桌面/最近任务/系统 UI：用户看一眼其他 App 就被拽回来，
                        // 会觉得这 App 失控。详见 FocusRecallController.isExempt。
                        //
                        // 注：Android 10+ 后台启动 Activity 受 BAL 限制，
                        // 这里的 startActivity 可能被静默拦截。
                        // 主路径在无障碍服务（AppMonitorService.onAccessibilityEvent），
                        // 这里只作兜底 —— 部分 ROM 允许前台服务启动 Activity。
                        if (com.deepfocus.app.util.FocusSessionState.isActive &&
                            !com.deepfocus.app.accessibility.FocusRecallController
                                .isExempt(fg, packageName)
                        ) {
                            com.deepfocus.app.accessibility.FocusRecallController
                                .recall(this@DeepFocusService)
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // 协程取消必须重新抛出，否则服务销毁后协程会延迟退出
                    throw e
                } catch (e: Exception) {
                    // 单次轮询失败不影响后续轮询
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * 无障碍服务心跳检测
     *
     * 注意：Android 不允许代码直接开启无障碍服务，只能引导用户去系统设置开启。
     * 此处的作用：
     * 1. 确保前台服务自身存活（防止被系统回收导致轮询中断）
     * 2. 当无障碍断开时，前台服务自身的轮询引擎继续作为兜底
     *    （isAccessibilityConnected 状态已通过 StateFlow 自动同步给 UI，
     *     UI 会在权限页提示用户重新启用）
     */
    private fun performAccessibilityHeartbeat() {
        if (!AppDetectionManager.isProtectionEnabled) return
        // 重提升前台状态，防止系统在低内存时把服务降级
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
        } catch (e: Exception) {
            // 重提升失败静默忽略（如服务已被销毁）
        }
        // 需求 1+4：无障碍服务健康自检
        // - 系统开关仍在但服务没连上 → 保活任务会重建进程并触发系统自动重绑
        // - 系统开关已被关闭 → 打标记，由设置页引导卡片提示用户手动重新开启
        KeepAliveManager.checkAccessibilityHealth(this)
    }

    /**
     * 通过 UsageStatsManager 获取当前前台应用包名
     *
     * 三级策略检测，兼顾精确性、性能和可靠性：
     *
     * 策略 1（轻量）：queryEvents 查询最近 QUERY_WINDOW_MS 内的 ACTIVITY_RESUMED 事件
     *   —— 精确检测应用切换，IPC 开销低
     *   —— 窗口必须显著大于 UsageStats 的事件入库延迟：
     *     事件是批量延迟入库的，若窗口太短，延迟入库的切换事件会被永久错过，
     *     缓存就会一直停在旧包名（曾导致「使用时长到期」不弹提醒）
     *
     * 策略 2（缓存）：当 queryEvents 返回空（用户停留同一应用无新事件）时，
     *   优先使用 lastKnownForeground 缓存（2h 内有效）
     *   —— 避免每 1.5s 调用昂贵的 queryUsageStats(2h)，这是性能优化的关键
     *   —— 用户没切换应用 = 仍在缓存的应用里，用缓存值驱动检测是正确的
     *
     * 策略 3（兜底）：无缓存或缓存过期时，queryUsageStats 查询 2h 窗口取 lastTimeUsed 最大的应用
     *   —— 重量级 IPC 调用，只在首次启动或缓存过期（2h）时执行
     *
     * 关键：必须使用 queryEvents / queryUsageStats（返回所有应用数据），不能用 queryEventsForSelf
     * （后者只返回调用应用自身事件，无法监控其他应用）
     *
     * @return 前台包名，无权限或无数据时返回 null
     */
    private fun getForegroundPackage(): String? {
        val usm = getSystemService(USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val now = System.currentTimeMillis()

        // 策略 1：queryEvents 查询事件流（轻量）
        val events = try {
            usm.queryEvents(now - QUERY_WINDOW_MS, now)
        } catch (e: Exception) {
            null
        }

        if (events != null) {
            var lastFgPackage: String? = null
            val resumeEventType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED
            } else {
                @Suppress("DEPRECATION")
                android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND
            }
            while (events.hasNextEvent()) {
                val event = android.app.usage.UsageEvents.Event()
                events.getNextEvent(event)
                if (event.eventType == resumeEventType) {
                    lastFgPackage = event.packageName
                }
            }
            if (lastFgPackage != null) {
                // 检测到应用切换事件，更新缓存并返回
                lastKnownForeground = lastFgPackage
                lastKnownForegroundTime = now
                return lastFgPackage
            }
            // queryEvents 返回空 = 5s 内无应用切换 = 用户仍在同一应用
            // 策略 2：优先使用缓存，避免每 1.5s 调用 queryUsageStats(2h) 的性能灾难
            val cached = lastKnownForeground
            val cachedAge = now - lastKnownForegroundTime
            if (cached != null && cachedAge < CACHE_VALIDITY_MS) {
                return cached
            }
        } else {
            // queryEvents 本身异常时，也尝试缓存
            val cached = lastKnownForeground
            val cachedAge = now - lastKnownForegroundTime
            if (cached != null && cachedAge < CACHE_VALIDITY_MS) {
                return cached
            }
        }

        // 策略 3：queryUsageStats 兜底（重量级，只在无事件且无缓存时调用）
        return try {
            val stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                now - STATS_WINDOW_MS,
                now
            )
            val result = stats?.maxByOrNull { it.lastTimeUsed }?.packageName
            if (result != null) {
                lastKnownForeground = result
                lastKnownForegroundTime = now
            }
            result
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 构建前台通知
     *
     * 需求 2：应用已取消一切用户可见通知，也不申请通知权限。
     * 但 Android 规定前台服务必须关联一条通知才能保持存活，
     * 因此这里只保留一条"最低优先级、无声、无角标、不可交互"的占位通知 ——
     * 它不会出现在通知列表中打扰用户，仅用于满足系统对前台服务的强制要求。
     */
    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, DeepFocusApp.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_shield)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        /** 前台通知 ID */
        const val NOTIFICATION_ID = 1001
        /** 轮询间隔（毫秒）- 1.5 秒，兼顾响应速度与电量 */
        private const val POLL_INTERVAL_MS = 1500L
        /** queryEvents 查询时间窗口（毫秒）- 查询最近 60 秒事件
         *
         * 修复：原 5s 窗口会永久错过「延迟入库」的切换事件。
         * UsageStats 事件是批量异步入库的，入库延迟可达数十秒；
         * 一旦错过窗口，该切换事件就再也读不到，
         * 前台包名缓存会一直停在旧值（曾导致「使用时长到期」既不弹提醒也不恢复拦截）。
         * 60s 窗口可覆盖常见入库延迟，且 IPC 开销与 5s 基本相同。
         */
        private const val QUERY_WINDOW_MS = 60_000L
        /** queryUsageStats 兜底查询窗口（毫秒）- 2 小时 */
        private const val STATS_WINDOW_MS = 2 * 60 * 60 * 1000L
        /** 心跳检测间隔（轮询次数）- 每 30 次轮询检测一次，约 45 秒 */
        private const val HEARTBEAT_INTERVAL_TICKS = 30
        /** 前台包名缓存有效期（毫秒）- 2 小时 */
        private const val CACHE_VALIDITY_MS = 2 * 60 * 60 * 1000L
    }
}
