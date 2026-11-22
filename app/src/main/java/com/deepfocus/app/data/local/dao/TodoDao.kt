package com.deepfocus.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.deepfocus.app.data.local.entity.TodoEntity
import kotlinx.coroutines.flow.Flow

/**
 * 今日待办 DAO
 */
@Dao
interface TodoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(todo: TodoEntity): Long

    @Query("DELETE FROM todos WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 某天的全部待办（未完成在前） */
    @Query("SELECT * FROM todos WHERE dayKey = :dayKey ORDER BY done ASC, createdAt ASC")
    fun observeByDay(dayKey: String): Flow<List<TodoEntity>>

    /** 某天的全部待办（同步读，给 agent tool 用） */
    @Query("SELECT * FROM todos WHERE dayKey = :dayKey ORDER BY done ASC, createdAt ASC")
    suspend fun getByDay(dayKey: String): List<TodoEntity>

    /** 某天还没做完的 */
    @Query("SELECT * FROM todos WHERE dayKey = :dayKey AND done = 0 ORDER BY createdAt ASC")
    suspend fun getUndoneByDay(dayKey: String): List<TodoEntity>

    /** 标记完成/取消完成 */
    @Query("UPDATE todos SET done = :done, doneAt = :doneAt WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean, doneAt: Long = System.currentTimeMillis())

    /** 清掉某天的（跨天时调用） */
    @Query("DELETE FROM todos WHERE dayKey != :dayKey")
    suspend fun deleteOtherDays(dayKey: String)
}
