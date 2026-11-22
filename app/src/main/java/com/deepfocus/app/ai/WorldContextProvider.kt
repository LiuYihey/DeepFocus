package com.deepfocus.app.ai

import android.app.usage.UsageStatsManager
import android.content.Context
import com.deepfocus.app.util.PermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * 世界感知快照
 *
 * 大肥鱼「知道」的现实信息打包成一段自然语言，塞进 prompt。
 * 它知道自己今天用了哪些 App、用了多久、专注了多久、现在什么状态。
 */
data class WorldContext(
    /** 今日各 App 使用时长（毫秒），已按时间降序 */
    val appUsage: List<AppUsage> = emptyList(),
    /** 今日总使用时长（毫秒） */
    val totalScreenTime: Long = 0,
    /** 今日专注总时长（毫秒） */
    val todayFocusMillis: Long = 0,
    /** 本次专注时长（毫秒），未在专注中为 0 */
    val currentFocusMillis: Long = 0,
    /** 当前是否在专注 */
    val isFocusing: Boolean = false,
    /** 今日专注了几段（含正在进行的那一段） */
    val todaySessionCount: Int = 0,
    /** 上一段专注的时长（毫秒），今天没专注过则为 0 */
    val lastFocusMillis: Long = 0,
    /** 刚刚结束一段专注（结束不到 5 分钟） */
    val justFinishedFocus: Boolean = false,
    /** 饱腹度 0~100 */
    val satiety: Int = 70,
    /** 是否饿了（距上次喂食超过 24 小时） */
    val isHungry: Boolean = false,
    /** 好感度 */
    val affinity: Int = 0,
    /** 是否在睡觉 */
    val isSleeping: Boolean = false,
    /** 最近一次旅行的目的地 */
    val travelDestination: String? = null,
    /** 最近一次旅行的心情 */
    val travelMood: String? = null,
    /** 去过的地方（供模型自己聊旅行） */
    val visitedDestinations: List<String> = emptyList()
) {
    /** 是否几乎没有任何世界信息（避免把空快照塞给模型） */
    val isEmpty: Boolean
        get() = appUsage.isEmpty() && todayFocusMillis == 0L && totalScreenTime == 0L
}

/**
 * 单个 App 的使用记录
 */
data class AppUsage(
    val packageName: String,
    val appName: String,
    val durationMillis: Long
)

/**
 * 世界感知提供器
 *
 * 负责把 UsageStats（今天用了什么）+ Room（专注时长）+ FishState（状态）
 * 汇总成 [WorldContext]。
 */
class WorldContextProvider(
    private val context: Context
) {

    /**
     * 采集今日 App 使用情况
     */
    suspend fun collectAppUsage(): List<AppUsage> = withContext(Dispatchers.IO) {
        if (!PermissionHelper.checkUsageStatsPermission(context)) return@withContext emptyList()

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE)
            as UsageStatsManager

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val beginTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val stats = runCatching {
            usageStatsManager.queryAndAggregateUsageStats(beginTime, endTime)
        }.getOrNull() ?: return@withContext emptyList()

        val pm = context.packageManager
        stats.values
            .filter { it.totalTimeInForeground > 30_000L } // 过滤掉误触级别的碎片
            .sortedByDescending { it.totalTimeInForeground }
            .take(8)
            .map { stat ->
                val label = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(stat.packageName, 0)).toString()
                }.getOrDefault(stat.packageName)
                AppUsage(
                    packageName = stat.packageName,
                    appName = label,
                    durationMillis = stat.totalTimeInForeground
                )
            }
    }

    /**
     * 把世界快照转成给模型看的自然语言
     *
     * 刻意写成「你亲眼看到的事实」而不是「数据报表」，
     * 这样模型更容易以角色口吻说出来，而不是复述统计。
     *
     * 文案细节：
     * - 不出现「用户」这种产品词，直接用大肥鱼对对方的称呼
     * - 不出现「0 分钟」，不足一分钟说「还没满一分钟」
     * - 屏幕时间用陪伴口吻，别像查岗
     * - 不出现「好感度 120」这种数值，好感度换成一句关系状态
     *
     * @param world 世界快照
     * @param userAlias 大肥鱼对用户的称呼（来自人格卡）
     */
    fun describe(world: WorldContext, userAlias: String = ""): String {
        val lines = mutableListOf<String>()

        lines += FocusNarrative.focusFacts(world, userAlias)

        FocusNarrative.screenTimeFacts(world, userAlias)
            .takeIf { it.isNotBlank() }
            ?.let { lines += it }

        if (!world.travelDestination.isNullOrBlank()) {
            val mood = world.travelMood?.takeIf { it.isNotBlank() }
            lines += if (mood != null) {
                "你最近去了${world.travelDestination}，回来心情是「$mood」。"
            } else {
                "你最近去了${world.travelDestination}。"
            }
        }

        // 状态描述：让反应分档对模型可见
        val stateWord = when {
            world.isHungry -> "你饿坏了，一直惦记着大白饭"
            world.satiety > 85 -> "你刚吃饱，懒洋洋的，特别满足"
            else -> "你肚子不饿不饱"
        }
        // 好感度不报数值，换成一句关系状态 —— 免得大肥鱼把「好感度 120」念出来
        val bondWord = when {
            world.affinity >= FishMoodResolver.AFFINITY_DEVOTED -> "它已经离不开你了"
            world.affinity >= FishMoodResolver.AFFINITY_BONDED -> "你们已经很有默契了"
            world.affinity >= FishMoodResolver.AFFINITY_CLOSE -> "你很喜欢对方"
            world.affinity >= FishMoodResolver.AFFINITY_FAMILIAR -> "你们已经挺熟了"
            world.affinity >= FishMoodResolver.AFFINITY_SHY -> "你们刚认识没多久，还有点客气"
            else -> "你们刚认识，你还有点怕生"
        }
        lines += "$stateWord，$bondWord。"

        if (world.isSleeping) {
            lines += "你刚刚睡醒，还有点迷糊。"
        }

        return lines.joinToString("\n")
    }
}
