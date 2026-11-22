package com.deepfocus.app.ui.onboarding

import android.content.Context
import com.deepfocus.app.util.PermissionHelper

/**
 * 首启引导状态存储
 *
 * 只记一件事：**用户是否已经看过（或主动跳过）了首启权限引导页**。
 *
 * 为什么不每次都弹：
 * - 用户可能拒绝权限（不想被锁），反复弹引导是骚扰
 * - 用户授权后也不该再被拦一次
 *
 * 但**只要必需权限还没拿到，引导页就要能再进**（设置页提供入口），
 * 所以判断条件是「没看过引导」而不是「没授权」。
 */
object OnboardingStore {

    private const val PREFS_NAME = "deep_focus_onboarding"
    private const val KEY_GUIDE_SEEN = "permission_guide_seen"

    /** 是否需要展示首启权限引导 */
    fun needsPermissionGuide(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_GUIDE_SEEN, false)) return false
        return !hasRequiredPermissions(context)
    }

    /**
     * 必需权限是否都已就绪
     *
     * 使用情况访问 + 无障碍，两个都拿到，锁机才能真正工作。
     */
    fun hasRequiredPermissions(context: Context): Boolean {
        return PermissionHelper.checkUsageStatsPermission(context) &&
            PermissionHelper.checkAccessibilityPermission(context)
    }

    /** 标记已看过引导（用户授权完成或主动跳过） */
    fun markGuideSeen(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_GUIDE_SEEN, true)
            .apply()
    }
}
