package com.deepfocus.app.accessibility

import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.deepfocus.app.MainActivity
import com.deepfocus.app.util.FocusSessionState

/**
 * 专注锁机的拉回执行器
 *
 * ## 为什么不能直接 startActivity
 *
 * Android 10（API 29）起，后台启动 Activity 受 **BAL（Background Activity
 * Launch）** 限制。前台服务发 `startActivity` 把 App 拉回前台，
 * 在多数机型上会被系统**静默拦截** —— 不抛异常，就是没反应。
 * 于是「专注锁机」看起来在跑，实际根本拉不回来。
 *
 * ## 两条可行路径
 *
 * 1. **无障碍服务拉回**（本类的主路径）
 *    无障碍服务由系统托管、拥有 BAL 豁免，
 *    在 `TYPE_WINDOW_STATE_CHANGED` 事件里做拦截最稳。
 *
 * 2. 前台服务轮询拉回（`DeepFocusService` 的兜底）
 *    部分 ROM 允许前台服务 `startActivity`，所以留着做备份。
 *
 * 两条路径都汇到 [recall]，但**有冷却时间**，避免两条路径重复拉回。
 */
object FocusRecallController {

    /** 两次拉回之间的最小间隔（毫秒） */
    private const val RECALL_COOLDOWN_MS = 1500L

    /** 上次拉回的时间 */
    @Volatile
    private var lastRecallAt = 0L

    /**
     * 系统界面 / 桌面 / 最近任务的包名 —— 这些不算「切走」，要豁免。
     *
     * 不豁免的话，用户打开最近任务看一眼其他 App 就会被拽回来，
     * 会觉得这 App 失控、进而想卸载。
     */
    private val EXEMPT_PACKAGES = setOf(
        "com.android.systemui",
        "com.android.launcher",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher",
        "com.android.launcher3.settings",
        "com.sec.android.app.launcher",          // 三星桌面
        "com.miui.home",                          // 小米桌面
        "com.huawei.android.launcher",            // 华为桌面
        "com.oppo.launcher",                      // OPPO 桌面
        "com.coloros.launcher",                   // ColorOS 桌面
        "com.vivo.launcher",                      // vivo 桌面
        "com.bbk.launcher2",                      // vivo 新桌面
        "com.bbk.appmanager"                      // 跳过
    )

    /**
     * 判断某个包名是否应该豁免（不算切走）
     *
     * @param packageName 前台包名
     * @param selfPackage 自己的包名
     */
    fun isExempt(packageName: String?, selfPackage: String): Boolean {
        if (packageName == null) return true
        if (packageName == selfPackage) return true
        if (packageName in EXEMPT_PACKAGES) return true
        // 系统 UI 的各种子包（状态栏、导航栏、通知面板、系统对话框）
        if (packageName.startsWith("com.android.systemui")) return true
        // 权限控制器、系统设置页 —— 用户去开权限时不该被拉回
        if (packageName == "com.android.settings") return true
        if (packageName.startsWith("com.android.permissioncontroller")) return true
        return false
    }

    /**
     * 执行拉回
     *
     * @param context 上下文
     * @return 是否真的拉回了（冷却期内会返回 false）
     */
    fun recall(context: Context): Boolean {
        // 只在专注进行中才拉回
        if (!FocusSessionState.isActive) return false

        val now = System.currentTimeMillis()
        if (now - lastRecallAt < RECALL_COOLDOWN_MS) return false
        lastRecallAt = now

        return try {
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(MainActivity.EXTRA_FOCUS_MODE_RECALL, true)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            // 启动失败静默忽略，下次轮询会重试
            false
        }
    }

    /**
     * 无障碍事件里判断要不要拉回
     *
     * @param event 无障碍事件
     * @param selfPackage 自己的包名
     * @return 是否应该拉回
     */
    fun shouldRecallOnEvent(event: AccessibilityEvent?, selfPackage: String): Boolean {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return false

        val pkg = event.packageName?.toString() ?: return false
        if (isExempt(pkg, selfPackage)) return false

        // 事件里带自己包名但不是本 App 自己的 Activity —— 也算切走
        // （比如别的 App 声明了相同的 intent filter）
        return true
    }
}
