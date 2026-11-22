package com.deepfocus.app.data.repository

import com.deepfocus.app.data.local.dao.TodoDao
import com.deepfocus.app.data.local.entity.TodoEntity
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 今日待办仓库
 *
 * 待办是**按天**的 —— 每条记着 dayKey，
 * 「今天的计划」和「昨天的遗留」是两件事。
 */
@Singleton
class TodoRepository @Inject constructor(
    private val todoDao: TodoDao
) {

    /** 今天的日期 key（yyyyMMdd，本地时区） */
    fun todayKey(): String {
        val c = Calendar.getInstance()
        return String.format(
            "%04d%02d%02d",
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH) + 1,
            c.get(Calendar.DAY_OF_MONTH)
        )
    }

    /** 观察今天的待办 */
    fun observeToday(): Flow<List<TodoEntity>> = todoDao.observeByDay(todayKey())

    /** 今天的待办（同步读） */
    suspend fun getToday(): List<TodoEntity> = todoDao.getByDay(todayKey())

    /** 今天还没做完的 */
    suspend fun getTodayUndone(): List<TodoEntity> = todoDao.getUndoneByDay(todayKey())

    /** 加一条待办 */
    suspend fun add(content: String): Long {
        val text = content.trim()
        if (text.isEmpty()) return 0L
        return todoDao.insert(
            TodoEntity(content = text, dayKey = todayKey())
        )
    }

    /** 勾选/取消 */
    suspend fun setDone(id: Long, done: Boolean) = todoDao.setDone(id, done)

    /** 删除一条 */
    suspend fun delete(id: Long) = todoDao.deleteById(id)
}
