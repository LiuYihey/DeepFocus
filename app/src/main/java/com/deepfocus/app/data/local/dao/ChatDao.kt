package com.deepfocus.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.deepfocus.app.data.local.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

/**
 * 聊天消息 DAO
 *
 * 消息按**会话**（conversationId）分组 —— 「开启新对话」只是开一段新线程，
 * 旧消息不删，只是归到别的会话里。
 */
@Dao
interface ChatDao {

    /** 观察全部消息（按时间升序） */
    @Query("SELECT * FROM chat_messages ORDER BY createdAt ASC, id ASC")
    fun observeAll(): Flow<List<ChatMessageEntity>>

    /** 观察某个会话的消息（按时间升序） */
    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC")
    fun observeByConversation(conversationId: Long): Flow<List<ChatMessageEntity>>

    /** 观察最近 N 条（用于构建上下文窗口） */
    @Query("SELECT * FROM chat_messages ORDER BY createdAt DESC, id DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<ChatMessageEntity>

    /**
     * 观察某会话里最近 N 条（用于构建上下文窗口）
     *
     * 只取 id > [afterId] 的 —— 早于压缩水位的消息已经被摘要吃掉了，
     * 不再重复喂给模型（这就是「上下文不过载」的执行点）。
     */
    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId AND id > :afterId ORDER BY createdAt DESC, id DESC LIMIT :limit")
    suspend fun getRecentByConversation(conversationId: Long, afterId: Long, limit: Int): List<ChatMessageEntity>

    /**
     * 观察最新一条大肥鱼说的话。
     *
     * 桌宠用它把 AI 的台词顶到气泡上：用户不用打开 App，
     * 就能在桌面上看到大肥鱼说了什么。
     *
     * kind 过滤掉 SYSTEM —— 系统事件不该用气泡播报。
     */
    @Query("SELECT * FROM chat_messages WHERE role = 'FISH' AND kind != 'SYSTEM' ORDER BY createdAt DESC, id DESC LIMIT 1")
    fun observeLatestFishMessage(): Flow<ChatMessageEntity?>

    /** 取最新一条大肥鱼说的话（单次） */
    @Query("SELECT * FROM chat_messages WHERE role = 'FISH' AND kind != 'SYSTEM' ORDER BY createdAt DESC, id DESC LIMIT 1")
    suspend fun getLatestFishMessage(): ChatMessageEntity?

    /**
     * 取某会话的全部消息（按时间正序）
     *
     * 用来压交接笔记 —— 一段对话结束时，把它读一遍压成几百字。
     */
    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC")
    suspend fun getByConversationForSummary(conversationId: Long): List<ChatMessageEntity>

    /** 获取全部消息（单次） */
    @Query("SELECT * FROM chat_messages ORDER BY createdAt ASC, id ASC")
    suspend fun getAll(): List<ChatMessageEntity>

    /** 插入消息 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatMessageEntity): Long

    /** 清空全部消息 */
    @Query("DELETE FROM chat_messages")
    suspend fun clearAll()

    /** 删除某个会话的全部消息（用户删会话时用） */
    @Query("DELETE FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun deleteByConversation(conversationId: Long)

    /** 统计消息总数 */
    @Query("SELECT COUNT(*) FROM chat_messages")
    suspend fun count(): Int

    /** 统计某会话的消息数 */
    @Query("SELECT COUNT(*) FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun countByConversation(conversationId: Long): Int
}
