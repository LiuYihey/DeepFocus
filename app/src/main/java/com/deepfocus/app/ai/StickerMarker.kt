package com.deepfocus.app.ai

/**
 * 表情包标记解析
 *
 * 模型在回复末尾写 `[表情:st_cheer_01]`，这里把它拆出来：
 * 正文留给用户看，id 存进 `ChatMessageEntity.action`。
 *
 * 为什么不让模型直接输出 JSON：JSON 更容易让模型说出
 * 「{"sticker": "xxx"}」这种带引号带括号的壳，反而更脏。
 * 方括号标记短、好剥、模型不容易加多余东西。
 *
 * 容错策略（模型不听话是常态）：
 * - 标记不合法 → 当普通文本处理，标记本身从正文里删掉
 * - id 不在清单里 → 丢弃，交给关键词兜底
 * - 模型没写标记 → 正文原样，不配图
 */
object StickerMarker {

    /**
     * 解析结果
     *
     * @param text 去掉标记后的正文
     * @param stickerId 模型选的表达式 id（清单外的会是 null）
     */
    data class Parsed(val text: String, val stickerId: String?)

    // 允许的写法：[表情:xxx] 【表情：xxx】 [sticker:xxx] {表情:xxx}
    private val PATTERN = Regex(
        """[\[【\{]\s*(?:表情|sticker|表情包)\s*[:：]\s*([A-Za-z0-9_\-]+)\s*[\]】\}]""",
        RegexOption.IGNORE_CASE
    )

    /**
     * 从模型回复里拆出表情标记
     *
     * @param raw 模型原始回复
     * @param catalog 用来校验 id 是否真实存在（默认用全量清单）
     */
    fun parse(raw: String, catalog: List<Sticker> = StickerCatalog.ALL): Parsed {
        if (raw.isBlank()) return Parsed("", null)

        var chosen: String? = null
        // 多个标记时取最后一个 —— 模型改了主意时以最后的为准
        for (m in PATTERN.findAll(raw)) {
            val id = m.groupValues[1]
            if (catalog.any { it.id == id }) {
                chosen = id
            }
        }

        // 无论标记合不合法，都把标记本身从正文里抹掉
        var text = PATTERN.replace(raw, "").trim()
        // 标记通常单独占一行，删掉它会留下空行 —— 顺手压掉，
        // 不然用户会看到正文中间莫名多一段空白。
        text = text.replace(Regex("\n{3,}"), "\n\n").trim()

        return Parsed(text, chosen)
    }

    /**
     * 只剥掉标记，不解析选中了哪张
     *
     * 用于流式渲染：模型正在打字时，正文里可能已经打出了
     * `[表情:st_xxx]` 的前半截或全部，直接显示会让用户看到这串符号。
     *
     * @param raw 原始文本
     */
    fun stripMarkers(raw: String): String {
        if (raw.isBlank()) return raw
        var text = PATTERN.replace(raw, "").trim()
        text = text.replace(Regex("\n{3,}"), "\n\n").trim()
        return text
    }

    /**
     * 取模型选中的表情包
     *
     * **模型没选就是没有表情包** —— 不再按关键词猜一张凑数。
     *
     * 2026-11-22 用户反馈：「感觉是随机固定发送的，
     * agent 没有阅读这个表情包本身是什么内容，就随便发一个糊弄的感觉」。
     * 关键词兜底正是这个「糊弄」的来源：不管模型说不说配图，
     * 总能按情绪词捞出一张，看起来就像每次都必发表情包。
     *
     * 正确做法（上游 dsh-pet 的 chatImageEnabled 就是这个口径）：
     * 把清单交给模型 → 它读懂每张图的内容 → 自己决定用不用。
     *
     * @param modelPicked 模型选中的 id
     */
    fun resolve(modelPicked: String?): Sticker? {
        modelPicked?.let { id -> StickerCatalog.byId(id)?.let { return it } }
        return null
    }
}
