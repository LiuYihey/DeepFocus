package com.deepfocus.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.deepfocus.app.data.local.entity.ChatConversationEntity
import kotlinx.coroutines.flow.Flow

/**
 * 会话 DAO
 *
 * 会话 = 一段对话线程。用户可以开新对话（归档旧的），
 * 记忆靠 [ChatConversationEntity.summary] 跨会话延续。
 */
@Dao
interface ChatConversationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: ChatConversationEntity): Long

    @Update
    suspend fun update(conversation: ChatConversationEntity)

    /** 全部会话，按最近活动倒序（会话列表页用） */
    @Query("SELECT * FROM chat_conversations ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ChatConversationEntity>>

    /** 全部会话（含摘要），单次读取 */
    @Query("SELECT * FROM chat_conversations ORDER BY updatedAt DESC")
    suspend fun getAll(): List<ChatConversationEntity>

    @Query("SELECT * FROM chat_conversations WHERE id = :id")
    suspend fun getById(id: Long): ChatConversationEntity?

    /** 最近 N 个会话（用于拼「上一段对话」的交接上下文） */
    @Query("SELECT * FROM chat_conversations WHERE id != :excludeId ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun getRecent(excludeId: Long, limit: Int): List<ChatConversationEntity>

    /** 会话总数 */
    @Query("SELECT COUNT(*) FROM chat_conversations")
    suspend fun count(): Int

    /** 写摘要 + 压缩水位 */
    @Query("UPDATE chat_conversations SET summary = :summary, compactedUpToId = :upToId, updatedAt = :now WHERE id = :id")
    suspend fun saveSummary(id: Long, summary: String, upToId: Long, now: Long = System.currentTimeMillis())

    /** 更新标题 */
    @Query("UPDATE chat_conversations SET title = :title, updatedAt = :now WHERE id = :id")
    suspend fun saveTitle(id: Long, title: String, now: Long = System.currentTimeMillis())

    /** 刷新活动时间 */
    @Query("UPDATE chat_conversations SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long = System.currentTimeMillis())

    /** 删除会话（连带消息由 ChatDao 负责） */
    @Query("DELETE FROM chat_conversations WHERE id = :id")
    suspend fun deleteById(id: Long)
}
