package com.deepfocus.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.deepfocus.app.detection.AppDetectionManager
import com.deepfocus.app.service.DeepFocusService
import com.deepfocus.app.service.KeepAliveManager
import com.deepfocus.app.util.FocusSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 应用监控无障碍服务
 *
 * 职责：
 * 1. 【看门狗】周期性确保 DeepFocusService 存活——无障碍服务由系统托管、生命周期最长，
 *    是最可靠的前台服务守护者。前台服务被 ROM 杀死后必须由无障碍服务周期拉起，
 *    否则专注锁机的轮询引擎（专注期间把 App 拉回前台）会停摆。
 * 2. 上报无障碍连接状态，供设置页展示与保活逻辑判断。
 */
class AppMonitorService : AccessibilityService() {

    /**
     * 服务级协程作用域，绑定服务生命周期，onUnbind 时统一取消。
     *
     * 注意：onUnbind 后系统可能重新绑定服务（onServiceConnected 再次调用），
     * 此时旧的 serviceScope 已被 cancel，新的 launch 会静默失败。
     * 因此 onServiceConnected 中必须重新创建 serviceScope。
     */
    private var serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 前台服务看门狗 Job，onUnbind 时随 serviceScope 一并取消 */
    private var watchdogJob: Job? = null

    /** 主线程 Handler，用于延迟上报断连（修复 B：防 ROM 临时解绑→重绑的 UI 闪烁） */
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        initializeService()
    }

    /**
     * 无障碍服务的统一初始化逻辑
     *
     * onServiceConnected（首次绑定）与 onRebind（系统重新绑定）共用，
     * 保证两条路径下的状态完全一致：协程作用域、看门狗、保活任务。
     */
    private fun initializeService() {
        // 修复 B：取消待执行的断连上报——系统在 onUnbind 后快速重新绑定时不报故障
        // 注意：此处取消的是 companion 中的进程级任务，
        // 因此新实例（重新绑定后创建的实例）也能取消旧实例挂的任务
        pendingDisconnectReport?.let { mainHandler.removeCallbacks(it) }
        pendingDisconnectReport = null
        // 重建协程作用域：服务可能被系统断开后重新绑定，
        // 旧 scope 已在 onUnbind 中 cancel，必须重建才能正常 launch
        if (!serviceScope.isActive) {
            serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        }
        // 修复 Bug 2：必须先 init(this) 再 setAccessibilityConnected(true)
        // 原顺序倒置：setAccessibilityConnected 在 init 之前调用，导致 prefs 为 null，
        // hasUserOperated 检查失效，用户手动关闭守护后会被自动重新打开。
        // 正确顺序：先 init 恢复 SharedPreferences，再通知连接状态，hasUserOperated 才能正确判断。
        AppDetectionManager.init(this)
        // 需求 1+4：服务重新连上 → 清除「已被系统关闭」标记，并重排保活任务
        AppDetectionManager.markAccessibilityRestored()
        AppDetectionManager.setAccessibilityConnected(true)
        // 重排保活任务：进程重建后 JobScheduler / AlarmManager 需要重新确认已注册
        KeepAliveManager.schedule(this)
        // 启动前台服务看门狗
        startForegroundServiceWatchdog()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 专注锁机的拉回主路径。
        //
        // 为什么必须走这里：Android 10 起后台启动 Activity 受 BAL 限制，
        // 前台服务发 startActivity 会被系统静默拦截 —— 不抛异常，就是没反应。
        // 无障碍服务有 BAL 豁免，在窗口变化事件里拉回才真的拉得动。
        // 前台服务那侧保留轮询拉回作为兜底（部分 ROM 允许）。
        if (event == null) return
        if (!FocusSessionState.isActive) return
        if (!AppDetectionManager.isProtectionEnabled) return

        val self = packageName ?: return
        if (!FocusRecallController.shouldRecallOnEvent(event, self)) return

        FocusRecallController.recall(this)
    }

    /**
     * 前台服务看门狗
     *
     * 每 WATCHDOG_INTERVAL_MS 检查一次，确保 DeepFocusService 在运行：
     * - DeepFocusService.onStartCommand → startPollingIfNeeded 是幂等的（pollingJob 活跃时直接 return）
     * - 故重复 startForegroundService 安全：服务已在运行时只是多一次 onStartCommand 调用，无副作用
     * - 无障碍服务拥有 BAL 豁免，可在后台启动前台服务
     *
     * 解决"前台服务被 ROM 杀死后无自愈"的根本问题：
     * 无障碍服务由系统托管，生命周期最长，是唯一可靠的前台服务守护者。
     * 专注锁机的轮询引擎依赖前台服务存活，必须由看门狗周期拉起恢复完整链路。
     */
    private fun startForegroundServiceWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = serviceScope.launch {
            while (isActive) {
                try {
                    // 仅在守护开启时拉起前台服务，避免关闭状态下常驻通知
                    if (AppDetectionManager.isProtectionEnabled) {
                        val serviceIntent = Intent(this@AppMonitorService, DeepFocusService::class.java)
                        startForegroundService(serviceIntent)
                    }
                } catch (e: Exception) {
                    // 启动失败静默忽略，下个周期重试
                }
                delay(WATCHDOG_INTERVAL_MS)
            }
        }
    }

    override fun onInterrupt() {
        // 空实现
    }

    /**
     * 需求 3：系统重新绑定无障碍服务时回调
     *
     * 背景：onUnbind 返回 true 后，系统在需要时会调用 onRebind 重新建立绑定。
     * 与 onServiceConnected 相同的初始化逻辑 —— 重建协程作用域、重启前台服务看门狗、
     * 重新登记保活任务、重新加载拦截列表。
     */
    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
        initializeService()
    }

    /**
     * 需求 3：服务被销毁时的兜底
     *
     * 进程被杀时 onUnbind 不一定被回调（进程直接消失），
     * 此时需要靠保活任务重建进程、由系统重新绑定无障碍服务。
     * 这里仅登记保活任务，不做无效的"自己重连"尝试 ——
     * Android 不允许应用自行开启无障碍服务，自行重连本就不可行。
     */
    override fun onDestroy() {
        watchdogJob?.cancel()
        serviceScope.cancel()
        if (AppDetectionManager.isProtectionEnabled) {
            KeepAliveManager.schedule(this)
        }
        super.onDestroy()
    }

    /**
     * 用户从最近任务划掉应用时回调
     *
     * 需求 3：划掉应用不应关闭无障碍服务。
     * Manifest 已设 stopWithTask=false 让系统不自动停止本服务，
     * 这里再登记一次保活任务，确保进程被 ROM 清理后能被拉起重建。
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (AppDetectionManager.isProtectionEnabled) {
            KeepAliveManager.schedule(this)
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        // 修复 B：延迟上报断连，过滤 ROM 临时解绑→重绑的瞬态（<15s）
        // 原代码立即 setAccessibilityConnected(false) 导致 UI 闪烁「故障」，
        // 即使 1 秒后系统重新绑定用户也已看到错误提示。
        // 改为 15 秒后若仍未重新绑定，才真正上报断连。
        // 用数组持有 Runnable 自身引用：Kotlin 的 lambda 无法直接引用自身定义的局部变量，
        // 通过 holder 实现"身份校验"（防止旧任务覆盖新任务）
        val holder = arrayOfNulls<Runnable>(1)
        val report = Runnable {
            // 身份校验：若期间已被新的断连任务替换（新的 onUnbind），本任务已过期，直接退出。
            // 否则旧的 runnable 会把新任务置空，导致新的断连永远不上报。
            if (pendingDisconnectReport !== holder[0]) return@Runnable
            pendingDisconnectReport = null
            AppDetectionManager.setAccessibilityConnected(false)
            // 需求 1+4：断连后立即做一次无障碍健康自检
            // - 若系统开关仍开启 → 说明只是进程被杀，保活任务会重建进程并自动重绑
            // - 若系统开关已被关闭 → 立刻提示用户手动重新开启（设置页引导卡片）
            KeepAliveManager.checkAccessibilityHealth(this@AppMonitorService)
        }
        holder[0] = report
        pendingDisconnectReport = report
        mainHandler.postDelayed(report, DISCONNECT_REPORT_DELAY_MS)
        watchdogJob?.cancel()
        serviceScope.cancel()
        // 需求 1+4：断连即重排保活任务，尽早恢复无障碍绑定
        KeepAliveManager.schedule(this)
        // 需求 3：返回 true —— 告知系统"可以重新绑定"，
        // 系统重新绑定本服务时会回调 onRebind，走与 onServiceConnected 相同的初始化路径。
        // 这是「程序退出重启时无障碍不被关闭」的关键：默认返回 false 时系统不再回调绑定。
        return true
    }

    companion object {
        /** 看门狗间隔（毫秒）- 60 秒，平衡及时性与电量 */
        private const val WATCHDOG_INTERVAL_MS = 60_000L
        /** 断连上报延迟（毫秒）- 15 秒，过滤 ROM 临时解绑→重绑的瞬态 */
        private const val DISCONNECT_REPORT_DELAY_MS = 15_000L

        /**
         * 主线程 Handler（进程级）
         * 必须与 pendingDisconnectReport 同为进程级，
         * 否则新实例（系统重新绑定时创建）无法取消旧实例挂的延迟任务
         */
        private val mainHandler = Handler(Looper.getMainLooper())

        /**
         * 待执行的断连上报 Runnable（进程级）
         *
         * 必须是【进程级】而非实例级（实测修复）：
         * 系统重新绑定无障碍服务时会创建**新的 AppMonitorService 实例**（实例 B），
         * 而 onUnbind 是旧实例（实例 A）挂的延迟任务。
         * 若该 Runnable 存在实例字段里，实例 B 的 onServiceConnected 拿到的是自己的 null，
         * 取消不掉实例 A 的那条任务 → 15 秒后旧任务触发 setAccessibilityConnected(false)，
         * 造成「服务明明已绑定，设置页却一直显示『正在恢复中』」的假故障（实测复现）。
         *
         * AppDetectionManager 本身就是进程级单例，断连状态必须与之同生命周期。
         */
        private var pendingDisconnectReport: Runnable? = null
    }
}
