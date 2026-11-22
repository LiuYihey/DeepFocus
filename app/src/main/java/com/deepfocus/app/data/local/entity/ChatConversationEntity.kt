package com.deepfocus.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 会话（对话线程）
 *
 * 用户可以「开一段新对话」—— 但大肥鱼的记忆是连贯的，
 * 新对话只是把旧对话**归档**，不是把她忘掉。
 *
 * 这个表就是「归档」的载体，同时兼任**上下文压缩的账本**：
 *
 * - [summary]：这段会话的压缩摘要（handoff 笔记）。
 *   存「这段对话里发生了什么、用户在意什么、约定是什么」，
 *   新会话开局时注入它 —— 这样记忆连贯，但不用把全文再喂一遍。
 * - [compactedUpToId]：最后一条被写进摘要的消息 id。
 *   上下文窗口只取 id 大于它的消息 —— 早期原文已被摘要替代，
 *   不再占用 token，这就是「上下文不过载」的账本。
 *
 * 类比 dsh-memory（MIT，github.com/haitang1/dsh-memory）的
 * `rollout_summaries/`：每个会话一份摘要，周期性地并进全局记忆。
 * 本项目把「全局记忆」那一层交给 [FishMemoryEntity]（长期记忆），
 * 会话摘要只负责「上一段对话发生了什么」。
 */
@Entity(tableName = "chat_conversations")
data class ChatConversationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 会话标题（取首条用户消息的前若干字） */
    val title: String,

    /** 会话摘要（handoff 笔记），可空 */
    val summary: String? = null,

    /** 最后一条被压缩进摘要的消息 id；0 = 还没压过 */
    val compactedUpToId: Long = 0,

    /** 创建时间 */
    val createdAt: Long = System.currentTimeMillis(),

    /** 最后一次活动时间（切会话、收发消息都更新） */
    val updatedAt: Long = System.currentTimeMillis(),

    /** 已归档（用户手动收起来，不再出现在当前会话里） */
    val archived: Boolean = false
)
