package com.deepfocus.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 今日待办
 *
 * 用户要求（2026-11-18）：
 * > 「增加一个 todo 菜单栏选项，这里可以添加今日 todo list，
 * > 大肥鱼会知道用户今天的计划。并且唤醒时督促用户完成，
 * > 大肥鱼的话可以通过通知栏让用户看到。」
 *
 * **「今日」是硬约束**：todo 是按天过的，不是无限 backlog。
 * 每条都记着是哪天的（[dayKey]，yyyyMMdd），
 * 跨天就不再是今天的待办了 —— 大肥鱼第二天醒来看到的是新的空清单，
 * 而不是昨天那堆没做完的（那属于「昨天没做完」，是另一回事）。
 */
@Entity(tableName = "todos")
data class TodoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 待办内容，一句话 */
    val content: String,

    /** 属于哪一天（yyyyMMdd，本地时区） */
    val dayKey: String,

    /** 是否已完成 */
    val done: Boolean = false,

    /** 完成时间 */
    val doneAt: Long = 0L,

    /** 创建时间 */
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        /** 未完成 */
        const val DONE_NO = 0

        /** 已完成 */
        const val DONE_YES = 1
    }
}
