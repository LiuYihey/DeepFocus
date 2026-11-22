package com.deepfocus.app.ai

import java.util.concurrent.TimeUnit

/**
 * 专注与用量的「人话」文案中枢
 *
 * 大肥鱼要能说出「他今天才坐了 12 分钟」这种话，
 * 就不能直接把数据库里的毫秒数丢给模型 —— 那只会得到一句
 * 「用户今日专注时长为 720000 毫秒」。
 *
 * 这里统一负责把数字翻译成陪伴者口吻的事实：
 * - 分场景：正在专注 / 刚专注完 / 还没开始 / 已经专注很久
 * - 不说「0 分钟」，不足一分钟就说「还没满一分钟」
 * - 屏幕时间用陪伴口吻，不是查岗
 */
object FocusNarrative {

    /** 一分钟 */
    private const val MINUTE = 60_000L

    /** 超过这个时长算「已经专注很久」 */
    const val LONG_FOCUS_MS = 2 * 60 * MINUTE

    /** 「刚专注完」的判定窗口：结束后 5 分钟内都算刚结束 */
    const val JUST_FINISHED_WINDOW_MS = 5 * MINUTE

    /**
     * 时长 → 人话
     *
     * 47 分钟 / 1 小时 13 分钟 / 2 小时 / 不到 1 分钟
     */
    fun duration(millis: Long): String {
        if (millis < MINUTE) return "不到 1 分钟"
        val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours <= 0L -> "$minutes 分钟"
            minutes == 0L -> "$hours 小时"
            else -> "$hours 小时 $minutes 分钟"
        }
    }

    /**
     * 专注事实：给模型看的「你亲眼看到的事」
     *
     * @param world 世界快照
     * @param alias 大肥鱼对用户的称呼（来自人格卡），空则退回「对方」
     */
    fun focusFacts(world: WorldContext, alias: String): String {
        val who = subject(alias)
        val total = world.todayFocusMillis
        val sessions = world.todaySessionCount
        val parts = mutableListOf<String>()

        when {
            // 正在专注，但刚开始
            world.isFocusing && world.currentFocusMillis < MINUTE ->
                parts += "${who}刚坐下开始专注，还没满一分钟"

            // 正在专注
            world.isFocusing ->
                parts += "${who}正在专注，这一段已经 ${duration(world.currentFocusMillis)}了"

            // 刚从一个专注里出来
            world.justFinishedFocus ->
                parts += if (world.lastFocusMillis >= MINUTE) {
                    "${who}刚从一个 ${duration(world.lastFocusMillis)}的专注里出来"
                } else {
                    "${who}刚从一个没坐多久的专注里出来"
                }

            // 今天还没开始
            sessions == 0 ->
                parts += "${who}今天还没开始过专注，一次都没坐下来"

            // 坐了但不足一分钟
            total < MINUTE ->
                parts += "${who}今天才刚开始，还没满一分钟"

            // 已经专注很久
            total >= LONG_FOCUS_MS ->
                parts += "${who}今天已经专注了 ${duration(total)}，分成 $sessions 段，中间几乎没停过"

            // 普通情况
            else ->
                parts += "${who}今天专注了 ${duration(total)}，分成 $sessions 段"
        }

        // 累计口径补充：正在专注 / 刚结束时，把今天的总数也摆出来
        if (world.isFocusing || world.justFinishedFocus) {
            parts += "今天一共专注了 ${duration(total)}，分成 $sessions 段"
        }

        // 上一段：让大肥鱼能接「上一段你坐了多久」这种话
        if (!world.isFocusing && !world.justFinishedFocus && world.lastFocusMillis >= MINUTE) {
            parts += "上一段是 ${duration(world.lastFocusMillis)}"
        }

        return parts.joinToString("，") + "。"
    }

    /**
     * 屏幕时间：陪伴口吻，不是查岗
     *
     * 只说「在哪些 App 上花了多少时间」，不排名、不评价、不跟昨天比。
     */
    fun screenTimeFacts(world: WorldContext, alias: String): String {
        val who = subject(alias)
        if (world.appUsage.isEmpty()) return ""

        val total = world.totalScreenTime
        val top = world.appUsage.take(3).joinToString("、") {
            "${it.appName} ${duration(it.durationMillis)}"
        }

        return when {
            total < 30 * MINUTE -> "${who}今天几乎没怎么碰手机。"
            total >= 4 * 60 * MINUTE ->
                "${who}今天在手机上待了 ${duration(total)}，其中$top。"
            else -> "${who}今天在手机上花了不少时间：$top。"
        }
    }

    /**
     * 主动催专注时用的事实（必须具体到数字）
     *
     * 「今天才 12 分钟，再来一段？」这种话需要事实支撑，
     * 泛泛的「该专注了」谁都能说，说不出差别。
     */
    fun nudgeFacts(world: WorldContext, alias: String): String {
        val who = subject(alias)
        val parts = mutableListOf<String>()
        parts += "${who}今天到现在专注了 ${duration(world.todayFocusMillis)}，分成 ${world.todaySessionCount} 段"
        if (world.lastFocusMillis >= MINUTE) {
            parts += "上一段是 ${duration(world.lastFocusMillis)}"
        }
        if (world.totalScreenTime >= 60 * MINUTE) {
            parts += "手机已经刷了 ${duration(world.totalScreenTime)}"
        }
        return parts.joinToString("，") + "。"
    }

    /**
     * 记忆召回用的关键词串：把当前话题和状态压成几个词，方便做相关度匹配
     */
    fun recallQuery(world: WorldContext): String {
        val apps = world.appUsage.take(3).joinToString(" ") { it.appName }
        return buildString {
            append("今天 专注 分钟 段 上次 ")
            append(TimeUnit.MILLISECONDS.toMinutes(world.todayFocusMillis))
            append(' ')
            append(world.todaySessionCount)
            append(' ')
            if (world.lastFocusMillis > 0) append(TimeUnit.MILLISECONDS.toMinutes(world.lastFocusMillis))
            append(' ')
            append(apps)
        }
    }

    /** 用户称呼：拿不到就用「对方」，不要用「用户」这种产品词 */
    private fun subject(alias: String): String =
        alias.trim().ifBlank { "对方" }
}
