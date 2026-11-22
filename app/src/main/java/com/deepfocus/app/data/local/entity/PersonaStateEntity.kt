package com.deepfocus.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 人格状态实体（单条，id = 1）
 *
 * 存三样东西：
 * 1. **用户自有字段** —— [userAlias]
 *    它对用户的称呼。换卡不冲掉它。
 * 2. **覆盖层** —— 8 个可空字段，只存被用户改过的。
 *    null = 沿用底稿的值。
 * 3. **底稿选择** —— [baseCardId] / [selectedTierThreshold]
 *
 * 生效人格 = 当前底稿（内置卡 / 自定义卡）+ 覆盖层。
 *
 * 注意：**大肥鱼的名字不在这里** —— 真源是 `user_fish.name`。
 * 多只大肥鱼各自有名字，而这张状态表是全局单行，存不下。
 */
@Entity(tableName = "persona_state")
data class PersonaStateEntity(
    /** 固定主键 1 */
    @PrimaryKey
    val id: Int = 1,

    /** 当前底稿：自定义卡 id；null = 跟随好感度自动选内置卡 */
    val baseCardId: String? = null,

    /**
     * 选定底稿时所在的好感度档位门槛
     *
     * 用来判断有没有跨档：跨了 → 强制切回内置卡。
     */
    val selectedTierThreshold: Int = 0,

    /** 改过的「你是谁」，null = 沿用底稿 */
    val identity: String? = null,

    /** 改过的性格标签，null = 沿用底稿 */
    val traits: String? = null,

    /** 改过的说话风格，null = 沿用底稿 */
    val speechStyle: String? = null,

    /** 改过的口头禅，null = 沿用底稿 */
    val catchphrases: String? = null,

    /** 改过的来历，null = 沿用底稿 */
    val backstory: String? = null,

    /** 改过的禁忌话题，null = 沿用底稿 */
    val taboos: String? = null,

    /** 改过的主动开口动机，null = 沿用底稿 */
    val proactiveMotivation: String? = null,

    /** 改过的温度，null = 沿用底稿 */
    val temperature: Float? = null,

    /** 大肥鱼称呼用户的方式 */
    val userAlias: String = "鱼片",

    /** 更新时间戳 */
    val updatedAt: Long = System.currentTimeMillis()
)
