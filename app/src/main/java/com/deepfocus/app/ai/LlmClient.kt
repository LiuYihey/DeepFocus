package com.deepfocus.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OpenAI 兼容 LLM 客户端
 *
 * 用 [HttpURLConnection] 手写而非引入 Retrofit/OkHttp：
 * 请求形态固定（POST + JSON + SSE 流式），依赖越少 APK 越小、离线构建更稳。
 *
 * 支持两种模式：
 * - [chatStream]：SSE 流式，边收边回调，用于对话页的打字机效果
 * - [chatOnce]：一次性返回完整文本，用于主动行为等不需要打字的场景
 */
object LlmClient {

    /**
     * 流式对话
     *
     * @param config 用户配置
     * @param messages 消息列表
     * @param onChunk 每收到一段增量文本回调一次（在 IO 线程，调用方自行切回主线程）
     * @return 完整回复文本
     */
    suspend fun chatStream(
        config: AiConfig,
        messages: List<LlmMessage>,
        onChunk: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val connection = openConnection(config)
        try {
            connection.outputStream.use { out ->
                out.write(buildRequestBody(config, messages, stream = true).toByteArray())
                out.flush()
            }

            val status = connection.responseCode
            if (status !in 200..299) {
                val errorBody = connection.errorStream?.let { readAll(it) }.orEmpty()
                throw AiException("HTTP $status: ${errorBody.take(300)}")
            }

            val builder = StringBuilder()
            val reader = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8))
            while (true) {
                val line = reader.readLine() ?: break
                // SSE 格式：data: {json}\n，空行表示事件结束，[DONE] 表示流结束
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload == "[DONE]") break
                if (payload.isEmpty()) continue

                val delta = parseDelta(payload) ?: continue
                if (delta.isNotEmpty()) {
                    builder.append(delta)
                    onChunk(delta)
                }
            }
            builder.toString()
        } finally {
            connection.disconnect()
        }
    }

    /**
     * 一次性对话（非流式）
     */
    suspend fun chatOnce(
        config: AiConfig,
        messages: List<LlmMessage>
    ): String = withContext(Dispatchers.IO) {
        val connection = openConnection(config)
        try {
            connection.outputStream.use { out ->
                out.write(buildRequestBody(config, messages, stream = false).toByteArray())
                out.flush()
            }

            val status = connection.responseCode
            if (status !in 200..299) {
                val errorBody = connection.errorStream?.let { readAll(it) }.orEmpty()
                throw AiException("HTTP $status: ${errorBody.take(300)}")
            }

            val text = readAll(connection.inputStream)
            // 非流式响应：choices[0].message.content
            return@withContext runCatching {
                val json = JSONObject(text)
                val choices = json.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    choices.getJSONObject(0).optJSONObject("message")
                        ?.optString("content").orEmpty()
                } else {
                    ""
                }
            }.getOrElse { throw AiException("响应解析失败: ${it.message}") }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * 流式对话 + 工具调用
     *
     * 为什么要有这个版本：
     * 聊天页要的是**打字机效果**（边收边显示），
     * 但大肥鱼还得能自己查事实、自己定状态词 —— 这需要 tools。
     * [chatStream] 不带 tools，[chatOnceWithTools] 不流式，
     * 所以这里把两件事合起来：流式收正文，同时把 tool_calls 分片攒起来。
     *
     * SSE 里 tool_calls 是**分片**的：第一个分片带 id/name，
     * 后面几个分片只带 arguments 的片段，按 `index` 归位。
     *
     * @return 若是纯文本回复返回 [StreamTurn.Text]；若是工具调用返回 [StreamTurn.ToolCalls]
     */
    suspend fun chatStreamWithTools(
        config: AiConfig,
        messages: List<LlmMessage>,
        tools: List<FishTool>,
        onChunk: (String) -> Unit
    ): StreamTurn = withContext(Dispatchers.IO) {
        val connection = openConnection(config)
        try {
            connection.outputStream.use { out ->
                out.write(
                    buildRequestBody(config, messages, stream = true, tools = tools).toByteArray()
                )
                out.flush()
            }

            val status = connection.responseCode
            if (status !in 200..299) {
                val errorBody = connection.errorStream?.let { readAll(it) }.orEmpty()
                throw AiException("HTTP $status: ${errorBody.take(300)}")
            }

            val builder = StringBuilder()
            // index -> (id, name, argsBuilder)
            val toolAcc = LinkedHashMap<Int, Triple<String, String, StringBuilder>>()

            val reader = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8))
            while (true) {
                val line = reader.readLine() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload == "[DONE]") break
                if (payload.isEmpty()) continue

                val json = runCatching { JSONObject(payload) }.getOrNull() ?: continue
                val choices = json.optJSONArray("choices") ?: continue
                if (choices.length() == 0) continue
                val delta = choices.getJSONObject(0).optJSONObject("delta") ?: continue

                // 正文增量
                delta.optString("content").takeIf { it.isNotEmpty() }?.let {
                    builder.append(it)
                    onChunk(it)
                }

                // 工具调用分片
                val calls = delta.optJSONArray("tool_calls") ?: continue
                for (i in 0 until calls.length()) {
                    val c = calls.getJSONObject(i)
                    val index = c.optInt("index", i)
                    val existing = toolAcc[index]
                    val id = c.optString("id").ifEmpty { existing?.first ?: "" }
                    val fn = c.optJSONObject("function")
                    val name = fn?.optString("name")?.ifEmpty { existing?.second } ?: existing?.second ?: ""
                    val argsFragment = fn?.optString("arguments").orEmpty()
                    val argsBuilder = existing?.third ?: StringBuilder()
                    argsBuilder.append(argsFragment)
                    toolAcc[index] = Triple(id, name, argsBuilder)
                }
            }

            if (toolAcc.isNotEmpty()) {
                val parsed = toolAcc.map { (_, acc) ->
                    val args = runCatching { JSONObject(acc.third.toString()) }
                        .getOrElse { JSONObject() }
                    ToolCall(id = acc.first, name = acc.second, arguments = args)
                }
                return@withContext if (parsed.isNotEmpty()) {
                    StreamTurn.ToolCalls(parsed)
                } else {
                    StreamTurn.Text(builder.toString())
                }
            }
            StreamTurn.Text(builder.toString())
        } finally {
            connection.disconnect()
        }
    }

    /**
     * 带工具的一次对话（非流式）
     *
     * 模型要么回一段文本（[LlmTurn.Text]），
     * 要么回若干个 tool_calls（[LlmTurn.ToolCalls]）——
     * 调用方执行完工具后，把结果塞回 messages 再调一轮。
     */
    suspend fun chatOnceWithTools(
        config: AiConfig,
        messages: List<LlmMessage>,
        tools: List<FishTool>
    ): LlmTurn = withContext(Dispatchers.IO) {
        val connection = openConnection(config)
        try {
            connection.outputStream.use { out ->
                out.write(
                    buildRequestBody(config, messages, stream = false, tools = tools).toByteArray()
                )
                out.flush()
            }

            val status = connection.responseCode
            if (status !in 200..299) {
                val errorBody = connection.errorStream?.let { readAll(it) }.orEmpty()
                throw AiException("HTTP $status: ${errorBody.take(300)}")
            }

            val text = readAll(connection.inputStream)
            return@withContext runCatching {
                val json = JSONObject(text)
                val choices = json.optJSONArray("choices")
                    ?: return@runCatching LlmTurn.Text("")
                val message = choices.getJSONObject(0).optJSONObject("message")
                    ?: return@runCatching LlmTurn.Text("")

                val calls = message.optJSONArray("tool_calls")
                if (calls != null && calls.length() > 0) {
                    val parsed = buildList {
                        for (i in 0 until calls.length()) {
                            val c = calls.getJSONObject(i)
                            val fn = c.optJSONObject("function") ?: continue
                            val argsText = fn.optString("arguments")
                            val args = runCatching { JSONObject(argsText) }.getOrElse { JSONObject() }
                            add(
                                ToolCall(
                                    id = c.optString("id"),
                                    name = fn.optString("name"),
                                    arguments = args
                                )
                            )
                        }
                    }
                    if (parsed.isNotEmpty()) LlmTurn.ToolCalls(parsed)
                    else LlmTurn.Text(message.optString("content").orEmpty())
                } else {
                    LlmTurn.Text(message.optString("content").orEmpty())
                }
            }.getOrElse { throw AiException("响应解析失败: ${it.message}") }
        } finally {
            connection.disconnect()
        }
    }

    /** 解析 SSE 单条增量，返回文本增量 */
    private fun parseDelta(payload: String): String? {
        return runCatching {
            val json = JSONObject(payload)
            val choices = json.optJSONArray("choices")
            if (choices == null || choices.length() == 0) return null
            val delta = choices.getJSONObject(0).optJSONObject("delta") ?: return null
            delta.optString("content").takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    private fun openConnection(config: AiConfig): HttpURLConnection {
        val url = URL(config.chatEndpoint)
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            setRequestProperty("Accept", "application/json, text/event-stream")
        }
    }

    private fun buildRequestBody(
        config: AiConfig,
        messages: List<LlmMessage>,
        stream: Boolean,
        tools: List<FishTool> = emptyList()
    ): String {
        val messagesArray = JSONArray()
        messages.forEach { msg ->
            val obj = JSONObject().put("role", msg.role)
            if (msg.role == "assistant" && !msg.toolCalls.isNullOrEmpty()) {
                // assistant 回传工具调用：必须带 tool_calls 数组，
                // 否则下一条 tool 消息找不到对应的 id（MiniMax 报 2013）
                obj.put("content", msg.content)
                val calls = JSONArray()
                msg.toolCalls.forEach { call ->
                    calls.put(
                        JSONObject()
                            .put("id", call.id)
                            .put("type", "function")
                            .put(
                                "function",
                                JSONObject()
                                    .put("name", call.name)
                                    .put("arguments", call.arguments.toString())
                            )
                    )
                }
                obj.put("tool_calls", calls)
            } else if (msg.role == "tool") {
                // tool 消息必须带 tool_call_id，指向它回应的那次调用
                obj.put("tool_call_id", msg.toolCallId.orEmpty())
                obj.put("content", msg.content)
            } else if (msg.imageBase64 != null) {
                // 多模态：content 是 parts 数组，而不是字符串。
                // 顺序固定「先文字后图」—— 模型先读到问题，再看图。
                val parts = JSONArray()
                if (msg.content.isNotBlank()) {
                    parts.put(
                        JSONObject().put("type", "text").put("text", msg.content)
                    )
                }
                parts.put(
                    JSONObject()
                        .put("type", "image_url")
                        .put(
                            "image_url",
                            JSONObject().put(
                                "url",
                                "data:${msg.imageMime};base64,${msg.imageBase64}"
                            )
                        )
                )
                obj.put("content", parts)
            } else {
                obj.put("content", msg.content)
            }
            messagesArray.put(obj)
        }
        val body = JSONObject()
            .put("model", config.model)
            .put("messages", messagesArray)
            .put("stream", stream)

        // token 上限参数名：老模型 max_tokens，o 系列 / gpt-5 用 max_completion_tokens
        body.put(tokenParamOf(config), config.maxTokens)

        // temperature：推理模型（o 系列 / gpt-5 / deepseek-reasoner）只接受默认值，
        // 显式传 temperature 会被 400 拒绝，所以这类模型干脆不传这个字段。
        if (supportsTemperature(config)) {
            body.put("temperature", config.temperature)
        }

        // 工具清单。非空才传 —— 有些 provider 不接受空 tools 数组。
        if (tools.isNotEmpty()) {
            body.put("tools", FishToolkit.toJsonSchema(tools))
            body.put("tool_choice", "auto")
        }

        return body.toString()
    }

    /**
     * token 上限参数名。
     *
     * 除了厂商预设外，还要看模型名 —— 同一个厂商下既有老模型也有 o 系列，
     * 只按厂商判断会漏。
     */
    private fun tokenParamOf(config: AiConfig): String {
        if (isReasoningModel(config.model)) return "max_completion_tokens"
        return config.provider.tokenParam
    }

    /** 是否可以传 temperature */
    private fun supportsTemperature(config: AiConfig): Boolean {
        if (!config.provider.supportsTemperature) return false
        return !isReasoningModel(config.model)
    }

    /** 判断是否是只接受默认 temperature 的推理模型 */
    private fun isReasoningModel(model: String): Boolean {
        val m = model.lowercase()
        // o1/o3/o4 系列、gpt-5 系列
        if (Regex("^(o1|o3|o4)[-/]").containsMatchIn(m)) return true
        if (m.startsWith("gpt-5")) return true
        // deepseek-reasoner 及其变体
        if (m.contains("deepseek-reasoner")) return true
        return false
    }

    private fun readAll(stream: java.io.InputStream): String {
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}

/**
 * 流式对话的结果
 *
 * 与非流式的 [LlmTurn] 同构，只是工具调用是从 SSE 分片里攒出来的。
 */
sealed interface StreamTurn {
    /** 模型回了一段正文 */
    data class Text(val content: String) : StreamTurn

    /** 模型要调工具 */
    data class ToolCalls(val calls: List<ToolCall>) : StreamTurn
}

/**
 * 一条对话消息
 *
 * 为什么要专门建个类型，不能用 `Pair<String, String>`：
 * OpenAI 兼容协议的**工具回传**有两个硬要求，
 * 光靠「角色 + 文本」表达不了 ——
 *
 * 1. 模型回了 tool_calls 之后，下一条 assistant 消息必须**原样带上**
 *    那个 `tool_calls` 数组（含 id、name、arguments）。
 * 2. 每条 tool 消息必须带 `tool_call_id`，
 *    指向它回应的是哪一次调用。
 *
 * 不带这两个字段，MiniMax 会直接 400：
 * `invalid params, tool result's tool id() not found (2013)`。
 *
 * @param role system / user / assistant / tool
 * @param content 正文
 * @param toolCalls assistant 消息里回传给模型的工具调用（其它角色为 null）
 * @param toolCallId tool 消息对应的调用 id
 * @param imageBase64 随消息一起发给模型的图片（base64，不含 data: 前缀）。
 *   非空时 content 会变成 OpenAI 兼容的 content-parts 数组。
 * @param imageMime 图片 MIME，如 image/jpeg
 */
data class LlmMessage(
    val role: String,
    val content: String,
    val toolCalls: List<ToolCall>? = null,
    val toolCallId: String? = null,
    val imageBase64: String? = null,
    val imageMime: String = "image/jpeg"
)

/**
 * AI 调用异常
 */
class AiException(message: String) : Exception(message)
