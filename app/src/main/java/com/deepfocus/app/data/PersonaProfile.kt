package com.deepfocus.app.data

/**
 * 生效人格
 *
 * 这是**算出来的**，不是存出来的：
 * 生效人格 = 当前底稿（内置卡 / 自定义卡）+ 覆盖层。
 *
 * 底稿来自 [BuiltinPersonaCatalog]（内置包）或 Room 里的自定义卡；
 * 覆盖层是用户对当前卡的改动，只存改过的字段。
 *
 * 用户自己起的名字（[name]）和称呼（[userAlias]）不来自任何卡 ——
 * 换卡不冲掉它们。
 */
data class PersonaProfile(
    /** 大肥鱼的名字（bootstrap 时用户起的，换卡不冲掉） */
    val name: String,

    /** 一句话自我定位，会被拼进 system prompt */
    val identity: String,

    /** 性格标签，逗号分隔 */
    val traits: String,

    /** 说话风格描述 */
    val speechStyle: String,

    /** 口头禅，逗号分隔 */
    val catchphrases: String,

    /** 背景故事/世界观，会影响它主动聊什么 */
    val backstory: String,

    /** 禁忌话题，逗号分隔。命中时它会回避 */
    val taboos: String,

    /** 大肥鱼称呼用户的方式 */
    val userAlias: String,

    /**
     * 主动开口的动机文案
     *
     * 借鉴 dsh-speak-up 的核心设计：让用户自己改写「她为什么要主动说话」。
     * 用户的动机文案会替换掉默认的「系统让我提醒你」——
     * 这一步很关键：模型收到"系统让我提醒你"会输出通知腔，
     * 收到角色口吻的动机才会输出人话。
     *
     * 用户改这段文案 = 认领这个角色，是零成本的高黏性钩子。
     */
    val proactiveMotivation: String,

    /** 温度：0~2，写入 API 请求 */
    val temperature: Float
)
