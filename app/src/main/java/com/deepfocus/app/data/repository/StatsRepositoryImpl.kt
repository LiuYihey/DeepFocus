package com.deepfocus.app.data.repository

import com.deepfocus.app.data.local.dao.FocusSessionDao
import com.deepfocus.app.data.local.entity.FocusSessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * 统计仓库实现类
 * 封装 FocusSessionDao，提供专注会话相关的数据访问方法
 * @param focusSessionDao 专注会话 DAO
 */
class StatsRepositoryImpl(
    private val focusSessionDao: FocusSessionDao
) {

    /**
     * 记录一条专注会话
     * @param targetApp 目标应用包名
     * @param startTime 会话开始时间戳（毫秒）
     * @param endTime 会话结束时间戳（毫秒），可为空表示会话未结束
     * @param quizPassed 是否通过测验
     * @param durationSeconds 会话时长（秒）
     * @return 新插入会话的 ID
     */
    suspend fun recordFocusSession(
        targetApp: String,
        startTime: Long,
        endTime: Long?,
        quizPassed: Boolean,
        durationSeconds: Int
    ): Long {
        val entity = FocusSessionEntity(
            targetApp = targetApp,
            startTime = startTime,
            endTime = endTime,
            quizPassed = quizPassed,
            durationSeconds = durationSeconds
        )
        return focusSessionDao.insert(entity)
    }

    /**
     * 关闭所有「未结束」的会话行
     *
     * 场景：进程被系统杀掉时，正在进行的会话来不及回填 endTime，
     * 数据库里就留下一行 endTime=null 的孤儿。它会让「今日专注时长」一直算不准，
     * 也会让接管会话时找到错误的行。
     *
     * 下次开始新会话前先调用，把孤儿行按时长补齐。
     */
    suspend fun closeOrphanSessions() {
        val now = System.currentTimeMillis()
        val open = focusSessionDao.getOpenSession() ?: return
        val duration = ((now - open.startTime) / 1000).toInt().coerceAtLeast(0)
        focusSessionDao.updateEndTime(open.id, now, duration)
    }

    /**
     * 更新指定专注会话的结束时间和时长
     * @param id 会话 ID
     * @param endTime 结束时间戳（毫秒）
     * @param duration 会话时长（秒）
     */
    suspend fun updateFocusSession(id: Long, endTime: Long, duration: Int) {
        focusSessionDao.updateEndTime(id, endTime, duration)
    }

    /**
     * 获取今日的专注会话列表（以 Flow 形式返回，可观察数据变化）
     * @param startOfDay 今日起始时间戳（毫秒）
     * @return 今日专注会话的 Flow
     */
    fun getTodayStats(startOfDay: Long): Flow<List<FocusSessionEntity>> {
        return focusSessionDao.getTodaySessions(startOfDay)
    }

    /**
     * 获取指定时间范围内的专注会话列表
     * 用于趋势图按日聚合统计
     * @param start 起始时间戳（毫秒）
     * @param end 结束时间戳（毫秒）
     * @return 符合条件的会话列表
     */
    suspend fun getSessionsByDateRange(start: Long, end: Long): List<FocusSessionEntity> {
        return focusSessionDao.getSessionsByDateRange(start, end)
    }

    /**
     * 获取累计专注总时长（秒）
     * @return 累计专注总时长，若无记录则返回 0
     */
    suspend fun getTotalStats(): Long {
        return focusSessionDao.getTotalFocusTime()
    }

    /**
     * 查找尚未结束的会话（进程内会话被新实例接管时用）
     *
     * @return endTime 为 null 的那一行，没有则 null
     */
    suspend fun getOpenSession(): FocusSessionEntity? = focusSessionDao.getOpenSession()

    /**
     * 获取今日专注总时长（毫秒）
     *
     * 供大肥鱼「知道用户今天专注了多久」使用。
     * 统计口径：以今日 0 点为界，累加所有专注会话的时长。
     *
     * **包含正在进行中的那一段**：正在专注时那一行 endTime=null、durationSeconds=0，
     * 只累加 durationSeconds 会把这一段漏掉 —— 大肥鱼就会说「今天还没专注过」，
     * 而用户明明正在专注。这里用「现在 - startTime」补上正在跑的那段。
     *
     * @return 今日专注毫秒数，无记录时返回 0
     */
    suspend fun getTodayFocusDurationMillis(): Long {
        val startOfDay = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        val now = System.currentTimeMillis()
        val sessions = focusSessionDao.getSessionsByDateRange(startOfDay, now)
        val finished = sessions.sumOf { it.durationSeconds } * 1000L
        // 正在进行中的会话：endTime 为 null，用实时经过时间补齐
        val running = sessions.filter { it.endTime == null }
            .sumOf { (now - it.startTime).coerceAtLeast(0L) }
        return finished + running
    }
}
