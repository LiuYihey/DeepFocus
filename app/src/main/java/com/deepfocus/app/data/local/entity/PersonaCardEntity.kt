package com.deepfocus.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 自定义人格卡实体
 *
 * 用户在「当前生效的人格卡」上改完之后，另存为一张自定义卡，
 * 存进自定义人格卡包 —— 与内置人格卡包是两套，互不影响。
 *
 * 内置卡不落库：它们的正文在 `assets/personas/builtin_cards.json`，
 * 由 App 静态提供、只读。
 */
@Entity(tableName = "persona_cards")
data class PersonaCardEntity(
    /** 卡 id，形如 `custom_<uuid>` */
    @PrimaryKey
    val id: String,

    /** 展示名，用户可改 */
    val displayName: String,

    /** 一句话定位 */
    val tagline: String,

    /** 一句话自我定位，会被拼进 system prompt */
    val identity: String,

    /** 性格标签，逗号分隔 */
    val traits: String,

    /** 说话风格描述 */
    val speechStyle: String,

    /** 口头禅，逗号分隔 */
    val catchphrases: String,

    /** 背景故事/世界观 */
    val backstory: String,

    /** 禁忌话题，逗号分隔 */
    val taboos: String,

    /** 主动开口的动机文案 */
    val proactiveMotivation: String,

    /** 温度：0~2，写入 API 请求 */
    val temperature: Float,

    /** 创建时间戳 */
    val createdAt: Long = System.currentTimeMillis()
)
