package com.deepfocus.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 大肥鱼的记忆条目
 *
 * 借鉴 dsh-fish-memory / dsh-meow-memory 的分层设计：
 * - 关系记忆（RELATION）：关于用户本身的事（叫什么、喜欢什么、几点睡），
 *   半衰期长，是"她认识你"的核心。
 * - 任务记忆（TASK）：做过的事（昨天专注了多久、读完了哪本书），
 *   半衰期短，是"我们一起经历過什么"的载体。
 *
 * 关键约束（来自 dsh-fish-memory 实测结论）：
 * 「在陪伴产品里，假装记住了比没记住更伤。」
 * 所以记忆分三态：
 * - CANDIDATE：刚捕捉到、还没确认 —— 不参与对话注入，只在记忆页里等人点头
 * - CONFIRMED：用户确认过 —— 参与注入
 * - ARCHIVED：过期或用户删的 —— 不再出现
 *
 * 半衰期：默认 30 天。过期后权重趋近 0，但不会立刻消失（她"记得但记不太清"）。
 */
@Entity(tableName = "fish_memories")
data class FishMemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 记忆分类：RELATION / TASK */
    val kind: String = KIND_RELATION,

    /** 记忆内容，一句话 */
    val content: String,

    /** 状态：CANDIDATE / CONFIRMED */
    val status: String = STATUS_CANDIDATE,

    /** 长期事实标记：每轮固定注入 2 条，不受半衰期影响 */
    val isPinned: Boolean = false,

    /** 创建时间 */
    val createdAt: Long = System.currentTimeMillis(),

    /** 最后一次被注入/复述的时间（半衰期从此刻起算） */
    val lastRecalledAt: Long = System.currentTimeMillis(),

    /** 被复述过多少次（越多说明越该保留） */
    val recallCount: Int = 0,

    /**
     * 产生这条记忆的会话 id
     *
     * 用户删掉一段对话时，这段对话里抽出来的记忆要跟着消失 ——
     * 否则大肥鱼会一边说「我不记得了」一边又提起被删掉的事。
     * null = 不属于任何会话（专注记住的、用户手动确认的）。
     */
    val conversationId: Long? = null
) {
    companion object {
        const val KIND_RELATION = "RELATION"
        const val KIND_TASK = "TASK"

        /**
         * 只在当天有效的事（今天要考试、明天要交材料）
         *
         * 不用新表新字段 —— kind 本来就是字符串列，
         * 多一种取值不涉及数据库结构变更，不需要迁移。
         * 过期后不再参与注入：昨天的考试今天别再问。
         */
        const val KIND_TODAY = "TODAY"

        const val STATUS_CANDIDATE = "CANDIDATE"
        const val STATUS_CONFIRMED = "CONFIRMED"

        /** 关系记忆半衰期（天） */
        const val HALF_LIFE_DAYS_RELATION = 60

        /** 任务记忆半衰期（天） */
        const val HALF_LIFE_DAYS_TASK = 30

        /** 当天有效的事半衰期（天） */
        const val HALF_LIFE_DAYS_TODAY = 1
    }
}
