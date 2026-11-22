package com.deepfocus.app.detection

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.deepfocus.app.service.DeepFocusService
import com.deepfocus.app.service.KeepAliveManager
import com.deepfocus.app.service.ServiceRestartReceiver
import com.deepfocus.app.util.PermissionHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 守护开关 + 无障碍连接状态管理器（单例）
 *
 * 职责：
 * 1. 维护守护开关状态（是否开启专注锁机守护）
 * 2. 维护无障碍服务连接状态与「被系统关闭」标记
 * 3. 守护开关切换时启动 / 停止前台保活服务并注册 / 取消保活任务
 */
object AppDetectionManager {

    /** SharedPreferences 文件名 */
    private const val PREFS_NAME = "deep_focus_prefs"

    /** 守护开关状态的存储 Key */
    private const val KEY_PROTECTION_ENABLED = "protection_enabled"

    /** 无障碍服务被系统关闭的标记，跨进程存活 */
    private const val KEY_ACCESSIBILITY_DISABLED = "accessibility_disabled"

    /** SharedPreferences 实例，通过 init 方法初始化 */
    private var prefs: SharedPreferences? = null

    /** Application 上下文，用于启动服务 */
    private var appContext: Context? = null

    /** 无障碍服务是否已连接（可观察的 StateFlow，UI 可监听服务运行状态） */
    private val _isAccessibilityConnected = MutableStateFlow(false)
    val isAccessibilityConnected: StateFlow<Boolean> = _isAccessibilityConnected.asStateFlow()

    /** 守护功能是否开启 */
    @Volatile
    var isProtectionEnabled: Boolean = false
        private set

    /**
     * 无障碍服务是否被系统/用户关闭
     *
     * 与 isAccessibilityConnected 区分：
     * - isAccessibilityConnected=false 仅表示当前进程没有连上（可能只是被 ROM 杀进程后未重绑）
     * - isAccessibilityDisabled=true 表示系统设置里的无障碍开关已被关闭，
     *   此时 Android 不允许 App 自行开启，只能引导用户手动恢复
     */
    private val _isAccessibilityDisabled = MutableStateFlow(false)
    val isAccessibilityDisabled: StateFlow<Boolean> = _isAccessibilityDisabled.asStateFlow()

    /**
     * 标记无障碍服务已被系统关闭
     * 由 KeepAliveManager.checkAccessibilityHealth 在检测到系统设置里的开关已关闭时调用
     *
     * 持久化：ColorOS 的典型表现是"杀进程 + 关开关"，
     * 进程被杀后内存态全部丢失，用户重新打开 App 时又看到"正在恢复中"，
     * 永远不知道守护其实已被系统关闭。写入 SharedPreferences 让状态跨进程存活。
     */
    fun markAccessibilityDisabled() {
        _isAccessibilityDisabled.value = true
        prefs?.edit()?.putBoolean(KEY_ACCESSIBILITY_DISABLED, true)?.apply()
    }

    /**
     * 标记无障碍服务已恢复连接
     * AppMonitorService.onServiceConnected 时调用，清除「已关闭」标记
     */
    fun markAccessibilityRestored() {
        _isAccessibilityDisabled.value = false
        prefs?.edit()?.remove(KEY_ACCESSIBILITY_DISABLED)?.apply()
    }

    /**
     * 初始化守护开关状态
     * 从 SharedPreferences 恢复 isProtectionEnabled 状态
     * 应在 Application 或 Activity 启动时调用
     *
     * 注意：此方法不启动前台服务。init 可能在后台进程（如无障碍服务重连）中被调用，
     * Android 12+ 后台启动前台服务受限会导致失败。FGS 启动交由前台调用方负责：
     * - MainActivity.startDeepFocusService()（前台，安全）
     * - BootReceiver（BOOT_COMPLETED 豁免 BAL 限制）
     * - setProtectionEnabled(true)（用户在 UI 操作，前台）
     *
     * @param context 上下文
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isProtectionEnabled = prefs?.getBoolean(KEY_PROTECTION_ENABLED, false) ?: false
        // 恢复「无障碍已被系统关闭」标记：
        // ColorOS 杀进程后内存态丢失，用户回到 App 会误以为"正在恢复中"。
        // 但若此刻系统开关其实已恢复（用户已手动开回来），应以系统实际状态为准 → 清除标记。
        // 注意：必须无条件以系统实际状态为准，不能只在标记为 true 时才去查系统开关 ——
        // 否则进程重建后标记会一直挂着，设置页永远显示"已被系统关闭"（实测误报）。
        if (!PermissionHelper.checkAccessibilityPermission(context)) {
            _isAccessibilityDisabled.value =
                prefs?.getBoolean(KEY_ACCESSIBILITY_DISABLED, false) ?: false
        } else {
            _isAccessibilityDisabled.value = false
            prefs?.edit()?.remove(KEY_ACCESSIBILITY_DISABLED)?.apply()
        }
    }

    /**
     * 设置守护开关（专注锁机守护）
     * 同时将状态持久化到 SharedPreferences
     * 开启时启动前台保活服务并注册保活任务，关闭时停止
     *
     * @param enabled 是否开启守护
     */
    fun setProtectionEnabled(enabled: Boolean) {
        isProtectionEnabled = enabled
        prefs?.edit()?.putBoolean(KEY_PROTECTION_ENABLED, enabled)?.apply()
        if (enabled) {
            startForegroundService()
            // 注册 AlarmManager 第三方守护者，独立于 app 进程
            // 确保前台服务被 ROM 杀死后能被定时拉起
            appContext?.let { ServiceRestartReceiver.schedule(it) }
            // 启动 JobScheduler + AlarmManager 双路保活，
            // 进程被 ColorOS 杀掉后由系统拉起并自动重绑无障碍服务
            appContext?.let { KeepAliveManager.schedule(it) }
        } else {
            stopForegroundService()
            // 取消闹钟，守护关闭后不再定时拉起服务
            appContext?.let { ServiceRestartReceiver.cancel(it) }
            // 取消保活任务，避免守护关闭后仍被系统拉起
            appContext?.let { KeepAliveManager.cancel(it) }
        }
    }

    /**
     * 启动前台保活服务
     * 幂等：重复调用安全
     */
    private fun startForegroundService() {
        val ctx = appContext ?: return
        try {
            val intent = Intent(ctx, DeepFocusService::class.java)
            ctx.startForegroundService(intent)
        } catch (e: Exception) {
            // 启动失败静默忽略（如 Android 12+ 后台启动前台服务限制）
        }
    }

    /**
     * 停止前台保活服务
     */
    private fun stopForegroundService() {
        val ctx = appContext ?: return
        try {
            val intent = Intent(ctx, DeepFocusService::class.java)
            ctx.stopService(intent)
        } catch (e: Exception) {
            // 停止失败静默忽略
        }
    }

    /**
     * 设置无障碍服务连接状态
     * 通过 StateFlow 通知所有监听者（如 UI），服务崩溃/断开时 UI 能立即感知
     *
     * 当服务首次连接且用户尚未操作过守护开关时，自动开启守护，
     * 解决「权限都开了但守护不生效」的核心痛点：守护开关默认 false。
     *
     * 连接时确保前台服务在运行（即使守护已开启，也可能因系统回收导致服务死掉）。
     */
    fun setAccessibilityConnected(connected: Boolean) {
        val wasConnected = _isAccessibilityConnected.value
        _isAccessibilityConnected.value = connected
        // 无障碍服务从断开 → 连接 时，仅在用户从未操作过守护开关的情况下自动开启
        if (connected && !wasConnected && !isProtectionEnabled) {
            // 通过 contains 区分两种场景：
            // - 用户从未操作过（key 不存在）→ 自动开启守护，让专注锁机立即生效
            // - 用户显式关闭过（key 存在，值为 false）→ 不自动开启，尊重用户选择
            val hasUserOperated = prefs?.contains(KEY_PROTECTION_ENABLED) ?: false
            if (!hasUserOperated) {
                setProtectionEnabled(true)
            }
        }
        // 无障碍服务连接且守护开启时，确保前台服务也在运行（双引擎兜底）
        if (connected && isProtectionEnabled) {
            startForegroundService()
        }
    }
}
