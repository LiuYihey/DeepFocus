package com.deepfocus.app.pet

import android.content.Context

/**
 * 桌宠的「只在手机桌面出现」闸门
 *
 * 用户要求（2026-11-19）：
 * > 注意打开了 app 就不显示桌宠，只在手机桌面时出现桌宠，也不干扰其它应用使用。
 *
 * 所以桌宠不再是「飘在所有应用上面」的悬浮窗 ——
 * **只有当前前台是系统桌面（Launcher）时**才把悬浮窗加进去；
 * 一进别的应用（包括我们自己的 App）立刻把窗口移除。
 *
 * 判定手法：
 * - 用 `UsageStatsManager` 取最近一段时间里**最后一条 ACTIVITY_RESUMED** 的包名。
 *   这是唯一在普通权限下能可靠拿到「当前前台是谁」的途径
 *   （`getRunningTasks` 从 Android 5 就受限了）。
 * - 系统桌面可能有多个（默认桌面 + 第三方桌面），所以用
 *   `queryIntentActivities(ACTION_MAIN + CATEGORY_HOME)` 把**所有**桌面包名都算进来，
 *   不写死 `com.android.launcher3`。
 *
 * 为什么先看「最后一条 RESUMED」而不是「上一条 PAUSED」：
 * 从 A 应用按 Home 回到桌面时，桌面会发 ACTIVITY_RESUMED，
 * 而 A 只发 PAUSED —— 只看 PAUSED 会误判成「还在 A」。
 */
object HomeScreenGate {

    /**
     * 增量查询的尾部窗口
     *
     * 每次轮询只看最近 60 秒的新事件，剩下的靠 [cachedForeground] 记住 ——
     * 否则用户在一个页面上静止超过 15 秒，窗口里一条 RESUMED 都没有，
     * 会被误判成「取不到前台」而退回「保守显示桌宠」，
     * 表现为「坐在 App 里不动，桌宠又爬出来了」（实测踩过）。
     */
    private const val TAIL_MS = 60_000L

    /** 首次（进程刚起）冷查询的回看窗口：一整天足够找到最后一条 RESUMED */
    private const val COLD_LOOKBACK_MS = 24 * 60 * 60 * 1000L

    /** 上一次查到的前台包名（进程内缓存） */
    @Volatile
    private var cachedForeground: String? = null

    /** 是否已经有可信缓存 */
    @Volatile
    private var hasCache = false

    /**
     * 当前前台是不是系统桌面
     *
     * @return true = 在桌面（可以显示桌宠）；false = 在别的应用或锁屏（应当隐藏）；
     *         null = 拿不到（没有 PACKAGE_USAGE_STATS 权限）
     */
    fun isOnHomeScreen(context: Context): Boolean? {
        val foreground = foregroundPackage(context) ?: return null
        return foreground in homePackages(context)
    }

    /**
     * 所有系统桌面的包名
     *
     * 用 CATEGORY_HOME 解析，而不是比对 `com.android.launcher3` ——
     * 模拟器 / 各家 ROM / 第三方桌面包名都不一样。
     */
    fun homePackages(context: Context): Set<String> {
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_HOME)
        }
        return try {
            context.packageManager
                .queryIntentActivities(intent, 0)
                .mapNotNull { it.activityInfo?.packageName }
                .toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    /**
     * 当前前台包名
     *
     * @return null = 取不到（无权限 / 设备上完全没有可读事件）
     */
    fun foregroundPackage(context: Context): String? {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE)
            as? android.app.usage.UsageStatsManager ?: return null
        val end = System.currentTimeMillis()
        val from = if (hasCache) end - TAIL_MS else end - COLD_LOOKBACK_MS
        val events = try {
            manager.queryEvents(from, end)
        } catch (e: Exception) {
            return cachedForeground
        } ?: return cachedForeground

        var last: String? = if (hasCache) cachedForeground else null
        val event = android.app.usage.UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            // ACTIVITY_RESUMED 在 API 29 才有这个常量名，
            // 旧版本叫 MOVE_TO_FOREGROUND，**数值都是 1** —— 直接用字面量最省事。
            if (event.eventType == EVENT_ACTIVITY_RESUMED) {
                val pkg = event.packageName ?: continue
                // android / 系统 UI 不是「用户在用的应用」，跳过
                if (pkg == "android") continue
                last = pkg
            }
        }
        if (last != null) {
            cachedForeground = last
            hasCache = true
        }
        return last
    }

    /** UsageEvents.Event.MOVE_TO_FOREGROUND（API 29+ 改名 ACTIVITY_RESUMED），数值 1 */
    private const val EVENT_ACTIVITY_RESUMED = 1
}
