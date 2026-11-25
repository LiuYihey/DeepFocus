package com.deepfocus.app.ai

import android.util.Log
import com.deepfocus.app.data.FishOutfit
import com.deepfocus.app.data.FishOutfitTemperature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 赛博旅游工作流
 *
 * 大肥鱼出门旅行的「导演组」—— 三个角色分工把一趟旅行攒出来。
 *
 * ## 为什么拆三个角色
 *
 * 一个 prompt 扛「去哪 + 发生什么 + 她怎么反应」三件事，质量会塌 ——
 * 出来的东西千篇一律，像填空题。拆开之后：
 * - **目的地 Agent** 只管挑地方
 * - **事件构造器** 只管把那个地方变成一件具体的事
 * - **角色 Agent** 只管以第一人称把这件事讲出来
 *
 * 三个都是「短 prompt + 短输出」，一次旅行的 token 花费是可控的。
 *
 * ## token 与真实性的平衡
 *
 * - 三次调用**全是短输出**（几十到一两百字），不是长篇大论
 * - 中间两步**不传历史**，只传上一步的结果 —— 上下文短，费用低
 * - 目的地由**模型自己选**（不是本地模板库），所以每趟都不一样
 * - 去过的地方会降低权重 —— 不会每次都去同一个地方
 * - 装扮牵引冷暖倾向 —— 冬装往北走，常服往南走
 */
class TravelWorkflow @javax.inject.Inject constructor(
    private val aiConfigStore: AiConfigStore
) {

    companion object {
        private const val TAG = "TravelWorkflow"

        /** 目的地最多给几个候选让模型挑（多给几个更容易选出「她想去」的） */
        private const val DESTINATION_CANDIDATES = 6

        /** 标题最长几个字 —— 太长卡片里排不下 */
        private const val MAX_TITLE_LENGTH = 16

        /**
         * 旅行工作流单次调用的 token 上限
         *
         * 不能沿用聊天配置的 1024：MiniMax-M3.1-Flash 这类**推理模型**
         * 会先花几百到一两千 token 思考，1024 时 finish_reason=length，
         * content 直接是空串（实测「事件」步骤必空）。
         * 4096 给思考 + JSON 留足空间。
         */
        private const val JSON_MAX_TOKENS = 4096
    }

    /**
     * 跑一趟完整的旅行
     *
     * @param outfit 当前的装扮 —— 决定冷暖倾向和参考图
     * @param visited 已经去过的地方（去重后的目的地列表）
     * @param persona 她是谁（人格卡摘要）
     * @return 攒出来的旅行素材
     */
    suspend fun runTrip(
        outfit: FishOutfit,
        visited: List<String>,
        persona: String
    ): TravelStory = withContext(Dispatchers.IO) {
        val config = aiConfigStore.config.value
        if (!config.isValid) {
            throw AiException("没有配置 AI，无法开始旅行")
        }

        // ── ① 目的地 Agent：她自己挑地方 ──
        val destination = pickDestination(config, outfit, visited, persona)

        // ── ② 事件构造器：把地方变成一件具体的事 ──
        val event = buildEvent(config, destination, outfit, persona)

        // ── ③ 角色 Agent：用第一人称讲出来 ──
        val diary = writeDiary(config, destination, event, outfit, persona)

        TravelStory(
            title = diary.title.ifBlank { destination.name },
            destination = destination.name,
            content = diary.content,
            mood = diary.mood,
            people = event.people,
            photoIdea = event.photoIdea
        )
    }

    /**
     * ① 目的地 Agent
     *
     * 给它几个**冷暖倾向匹配**的候选地方，让它自己挑。
     *
     * 去过的地方会从候选里去掉 —— 已经去过的不重复去。
     * 如果全去过，就不排除，只是提示一句。
     */
    private suspend fun pickDestination(
        config: AiConfig,
        outfit: FishOutfit,
        visited: List<String>,
        persona: String
    ): TravelDestination {
        val temperatureWord = when (outfit.temperature) {
            FishOutfitTemperature.COLD -> "很冷的地方：冰岛、雪国、北欧、高山、极地"
            FishOutfitTemperature.WARM -> "很暖的地方：东南亚、地中海、海岛、阳光充足的地方"
        }
        val visitedHint = if (visited.isEmpty()) {
            "你还没去过任何地方。"
        } else {
            "你去过这些地方了：${visited.joinToString("、")}。"
        }

        val candidates = (if (outfit.temperature == FishOutfitTemperature.COLD) COLD_PLACES else WARM_PLACES)
            .filterNot { it in visited }
            .shuffled()
            .take(DESTINATION_CANDIDATES)
            .ifEmpty { (if (outfit.temperature == FishOutfitTemperature.COLD) COLD_PLACES else WARM_PLACES).shuffled().take(DESTINATION_CANDIDATES) }

        val prompt = """
你是一个角色扮演游戏的「地点策划」。请只输出 JSON，不要任何解释。

角色：$persona
$visitedHint
它现在穿着${outfit.displayName}，倾向于去$temperatureWord。

请从下面这些地方里挑**一个**它这次最想去的：
${candidates.joinToString("\n")}

输出格式（严格 JSON，一行）：
{"place":"地点名","reason":"一句话为什么想去这里"}
""".trimIndent()

        val raw = chatJson(config, "目的地", prompt)

        return parseDestination(raw, candidates)
    }

    /**
     * ② 事件构造器
     *
     * 把「冰岛」变成「她在冰岛的黑沙滩上遇到了一只把自己埋在沙里的海豹」。
     *
     * 要求里塞了当地的风土人情、历史、传说、景点 —— 事件才有血肉。
     */
    private suspend fun buildEvent(
        config: AiConfig,
        destination: TravelDestination,
        outfit: FishOutfit,
        persona: String
    ): TravelEvent {
        val prompt = """
你是一个旅行文学的「事件编剧」。请只输出 JSON，不要任何解释。

角色：$persona
地点：${destination.name}（${destination.reason}）
它穿着${outfit.displayName}。

请围绕这个地点，编一件它在那里**真实可能遇到**的小事。
要求：
1. 要有当地的血肉 —— 风土人情、历史背景、有趣的传说、具体的景点或当地人
2. 要小而具体，不要「看风景」这种空话，最好有一个人或一个意外
3. 要有人物 —— 它遇到了谁

输出格式（严格 JSON，一行）：
{"event":"一句话说发生了什么","people":"它遇到的人（逗号分隔，没有就写空）","photoIdea":"如果这一刻要拍一张相片，会拍什么？包括是自拍、别人拍它、还是画成漫画"}
""".trimIndent()

        val raw = chatJson(config, "事件", prompt)

        return parseEvent(raw)
    }

    /**
     * ③ 角色 Agent
     *
     * 用第一人称写成日记。
     *
     * 要求写得**短** —— 简洁才有日记感，长了就成作文了。
     * 同时把心情和遇到的人单独摘出来，供日记本展示。
     */
    private suspend fun writeDiary(
        config: AiConfig,
        destination: TravelDestination,
        event: TravelEvent,
        outfit: FishOutfit,
        persona: String
    ): TravelDiary {
        val prompt = """
你是下面这个角色，现在在写自己的旅行日记：

角色：$persona
地点：${destination.name}
刚发生的事：${event.event}
遇到的人：${event.people.ifBlank { "没遇到什么人" }}
心情参考：${event.moodHint.ifBlank { "你自己定" }}

请写这篇日记：
- **第一人称**（我 / 我们）
- **短**，3 到 5 句话就够，别写成散文
- 用你自己的语气，符合上面的人设
- 结尾提一句这趟的心情（开心 / 疲惫 / 惊喜 / 想家……）

输出格式（严格 JSON，一行）：
{"title":"日记标题，不超过${MAX_TITLE_LENGTH}字","content":"日记正文","mood":"心情词"}
""".trimIndent()

        val raw = chatJson(config, "日记", prompt)

        return parseDiary(raw, destination)
    }

    // ── 解析 ──

    /**
     * 跑一次「只输出 JSON」的短调用
     *
     * 模型偶尔会返回空内容（实测偶发一次，后面的日记就退化成通用模板）。
     * 空内容重试一次 —— 只在失败时多花一次 token，正常路径不加钱。
     */
    private suspend fun chatJson(config: AiConfig, step: String, prompt: String): String {
        // 推理模型要留够思考空间，否则 content 会是空串（见 JSON_MAX_TOKENS）
        val jsonConfig = if (config.maxTokens < JSON_MAX_TOKENS) {
            config.copy(maxTokens = JSON_MAX_TOKENS)
        } else {
            config
        }
        repeat(2) { attempt ->
            val raw = LlmClient.chatOnce(
                config = jsonConfig,
                messages = listOf(
                    LlmMessage("system", "你只输出 JSON，不要多余的话。"),
                    LlmMessage("user", prompt)
                )
            )
            if (raw.isNotBlank()) return raw
            Log.w(TAG, "$step 步骤返回空内容（第 ${attempt + 1} 次）")
        }
        return ""
    }

    private fun parseDestination(raw: String, candidates: List<String>): TravelDestination {
        val obj = extractJson(raw)
        val place = obj?.optString("place")?.trim().orEmpty()
        val reason = obj?.optString("reason")?.trim().orEmpty()
        return TravelDestination(
            name = place.ifBlank { candidates.firstOrNull().orEmpty() },
            reason = reason
        )
    }

    private fun parseEvent(raw: String): TravelEvent {
        val obj = extractJson(raw)
        return TravelEvent(
            event = obj?.optString("event")?.trim().orEmpty(),
            people = obj?.optString("people")?.trim().orEmpty(),
            moodHint = obj?.optString("mood")?.trim().orEmpty(),
            photoIdea = obj?.optString("photoIdea")?.trim().orEmpty()
        )
    }

    private fun parseDiary(raw: String, destination: TravelDestination): TravelDiary {
        val obj = extractJson(raw)
        val content = obj?.optString("content")?.trim().orEmpty()
        return TravelDiary(
            title = obj?.optString("title")?.trim().orEmpty().take(MAX_TITLE_LENGTH),
            content = content.ifBlank { "去了${destination.name}，没什么好说的。" },
            mood = obj?.optString("mood")?.trim().orEmpty()
        )
    }

    /**
     * 从模型输出里抠出 JSON
     *
     * 模型经常在 JSON 外面套一层 ```json 或者加两句解释，
     * 所以这里找第一个 `{` 和最后一个 `}` 之间的部分。
     */
    private fun extractJson(raw: String): JSONObject? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) {
            Log.w(TAG, "模型输出里没有 JSON：$raw")
            return null
        }
        return runCatching { JSONObject(raw.substring(start, end + 1)) }.getOrElse {
            Log.w(TAG, "JSON 解析失败：$raw")
            null
        }
    }
}

/**
 * 目的地
 */
data class TravelDestination(
    /** 地点名 */
    val name: String,
    /** 为什么想去 */
    val reason: String
)

/**
 * 旅行中发生的事件
 */
data class TravelEvent(
    /** 发生了什么 */
    val event: String,
    /** 遇到了谁 */
    val people: String,
    /** 心情提示 */
    val moodHint: String,
    /** 如果这一刻要拍照，会拍什么 */
    val photoIdea: String
)

/**
 * 角色写的日记
 */
data class TravelDiary(
    /** 标题 */
    val title: String,
    /** 正文 */
    val content: String,
    /** 心情 */
    val mood: String
)

/**
 * 一趟旅行攒出来的素材
 */
data class TravelStory(
    /** 日记标题 */
    val title: String,
    /** 目的地 */
    val destination: String,
    /** 日记正文（第一人称） */
    val content: String,
    /** 她的心情 */
    val mood: String,
    /** 遇到的人 */
    val people: String,
    /** 生图的画面构想（自拍 / 他拍 / 漫画） */
    val photoIdea: String
)

/** 冷的地方 —— 冬装时从这里挑 */
private val COLD_PLACES = listOf(
    "冰岛", "挪威的峡湾", "芬兰的雪原", "北海道", "苏格兰高地",
    "阿尔卑斯山", "格陵兰", "俄罗斯的贝加尔湖", "捷克的布拉格老城", "瑞士的雪峰"
)

/** 暖的地方 —— 常服时从这里挑 */
private val WARM_PLACES = listOf(
    "京都", "巴厘岛", "里斯本", "希腊的圣托里尼", "越南的会安",
    "摩洛哥", "西班牙的安达卢西亚", "泰国清迈", "墨西哥的 Oaxaca", "意大利的 Amalfi"
)
