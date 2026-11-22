package com.deepfocus.app.ai

/**
 * 生图厂商预设
 *
 * 与聊天厂商（[AiProvider]）是**两套独立的清单** —— 用户可能聊天用一家、生图用另一家。
 *
 * @param id 唯一 id，持久化用
 * @param displayName 展示名
 * @param baseUrl 接口根地址（不含生图路径）
 * @param defaultModel 建议的默认模型
 * @param modelHints 备选模型，供用户在设置页一键切换
 * @param imagePath 生图路径（相对 baseUrl）
 * @param referenceMode 参考图怎么带 —— 见 [ImageGenReferenceMode]
 * @param note 备注，显示在设置页
 */
data class ImageGenProvider(
    /** 唯一 id，持久化用 */
    val id: String,
    /** 展示名 */
    val displayName: String,
    /** 接口根地址（不含生图路径） */
    val baseUrl: String,
    /** 建议的默认模型 */
    val defaultModel: String,
    /** 备选模型，供用户在设置页一键切换 */
    val modelHints: List<String> = emptyList(),
    /** 生图路径（相对 baseUrl） */
    val imagePath: String = "/images/edits",
    /** 参考图怎么带 */
    val referenceMode: ImageGenReferenceMode = ImageGenReferenceMode.JSON_BASE64,
    /** 备注，显示在设置页 */
    val note: String = ""
)

/**
 * 参考图的传递方式
 *
 * 两种协议长得不一样，必须在客户端层区分 —— 否则参考图根本送不进去，
 * 角色一致性就断了。
 *
 * - [JSON_BASE64]：请求体是 JSON，参考图以 data URI 塞在 `images[].image_url`。
 *   MiniMax 走这个。
 * - [MULTIPART`：请求体是 multipart/form-data，参考图作为文件流上传。
 *   OpenAI 原生 `images.edit` 走这个。
 */
enum class ImageGenReferenceMode {
    /** JSON + base64 data URI（MiniMax） */
    JSON_BASE64,

    /** multipart/form-data 文件上传（OpenAI 原生） */
    MULTIPART
}

/**
 * 内置生图厂商清单
 */
object ImageGenProviders {

    const val CUSTOM_ID = "custom"

    /** MiniMax —— H3-Image */
    private val MINIMAX = ImageGenProvider(
        id = "minimax",
        displayName = "MiniMax",
        baseUrl = "https://api.minimax.cn/v1",
        defaultModel = "MiniMax-H3-Image",
        modelHints = listOf("MiniMax-H3-Image"),
        imagePath = "/images/edits",
        referenceMode = ImageGenReferenceMode.JSON_BASE64,
        note = "参考图以 base64 传；必须带参考图，否则角色会变脸"
    )

    /** OpenAI 原生 —— 兼容 /v1/images/edits */
    private val OPENAI = ImageGenProvider(
        id = "openai",
        displayName = "OpenAI 兼容",
        baseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-image-1",
        modelHints = listOf("gpt-image-1", "dall-e-3"),
        imagePath = "/images/edits",
        referenceMode = ImageGenReferenceMode.MULTIPART,
        note = "参考图以 multipart 文件上传"
    )

    /** 全部预设 */
    val ALL: List<ImageGenProvider> = listOf(MINIMAX, OPENAI)

    /** 按 id 取预设，取不到就回落到 MiniMax */
    fun byId(id: String?): ImageGenProvider =
        ALL.firstOrNull { it.id == id } ?: MINIMAX
}
