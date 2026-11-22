package com.deepfocus.app.ai

import android.content.Context
import com.deepfocus.app.data.repository.FishRepository
import com.deepfocus.app.data.repository.StatsRepositoryImpl
import com.deepfocus.app.data.repository.TodoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 大肥鱼的 agent 运行时
 *
 * 负责跑「模型 → 调工具 → 把结果喂回模型 → 直到模型给最终答案」这个循环。
 *
 * 为什么要有这一层
 * ----------------
 * 之前的 [FishBrain] 只会「一次性问一次模型」——
 * 世界信息全靠 [WorldContextProvider.describe] 拼在 prompt 里。
 *
 * 现在大肥鱼有了**自己的工具**（[FishToolkit]）：
 * - 自己决定要不要查「今天专注了多久」
 * - 自己决定要不要查「今天用了哪些 App」
 * - 自己决定要不要查「我今天吃了多少饭、饿不饿」
 * - 自己给自己定一个状态词（`set_mood`）
 *
 * 工具的**执行权在本地代码**，模型只能请求 —— 数据永远从本地出。
 */
class FishAgent(
    private val context: Context,
    private val aiConfigStore: AiConfigStore,
    private val fishRepository: FishRepository,
    private val statsRepository: StatsRepositoryImpl,
    private val worldContextProvider: WorldContextProvider,
    private val moodStore: MoodStore,
    private val todoRepository: TodoRepository
) {

    companion object {
        /** 最多跑几轮工具调用（防止模型无限循环调工具） */
        const val MAX_TOOL_ROUNDS = 4
    }

    /** 工具箱（构造时建一次，工具是纯函数式的） */
    private val tools: List<FishTool> by lazy {
        FishToolkit.build(
            context = context,
            statsRepository = statsRepository,
            fishRepository = fishRepository,
            worldProvider = worldContextProvider,
            todoRepository = todoRepository
        )
    }

    /**
     * 跑一次带工具的对话
     *
     * @param systemPrompt 角色设定
     * @param userPrompt 用户/系统给的一句话
     * @param extraContext 额外的事实（世界感知等），会拼进 system
     * @return 模型的最终文本 + 期间选定的状态词
     */
    suspend fun run(
        systemPrompt: String,
        userPrompt: String,
        extraContext: String = ""
    ): AgentResult = withContext(Dispatchers.IO) {
        val config = aiConfigStore.config.value
        if (!config.isValid) {
            return@withContext AgentResult("", null, "没有配置 AI")
        }

        val messages = mutableListOf<LlmMessage>()
        messages += LlmMessage("system", buildSystemPrompt(systemPrompt, extraContext))
        messages += LlmMessage("user", userPrompt)

        var chosenMood: String? = null
        var rounds = 0

        while (rounds < MAX_TOOL_ROUNDS) {
            rounds++
            val turn = LlmClient.chatOnceWithTools(config, messages, tools)

            when (turn) {
                is LlmTurn.Text -> {
                    return@withContext AgentResult(turn.content, chosenMood, null)
                }

                is LlmTurn.ToolCalls -> {
                    // 1) 先把模型的 tool_calls 记进 messages。
                    //    必须**带 tool_calls 数组本身**，只写一句文字模型认不出来，
                    //    后面的 tool 消息就会因为找不到 id 被拒（MiniMax 报 2013）。
                    messages += LlmMessage(
                        role = "assistant",
                        content = "",
                        toolCalls = turn.calls
                    )

                    // 2) 执行每个工具，把结果作为 tool 消息喂回去
                    //    （每条必须带 tool_call_id）
                    turn.calls.forEach { call ->
                        val tool = tools.firstOrNull { it.name == call.name }
                        val result = if (tool == null) {
                            "没有这个工具。"
                        } else {
                            runCatching { tool.executor(call.arguments) }
                                .getOrElse { "工具执行失败: ${it.message}" }
                        }

                        // set_mood 的参数就是状态词 —— 记下来，显示到状态胶囊
                        if (call.name == FishToolkit.TOOL_SET_MOOD) {
                            val mood = call.arguments.optString("mood").trim()
                            if (MoodStore.isAcceptableMood(mood)) {
                                chosenMood = mood
                                // 落盘 —— 首页胶囊读的就是它
                                moodStore.set(mood)
                            }
                        }

                        messages += LlmMessage(
                            role = "tool",
                            content = result,
                            toolCallId = call.id
                        )
                    }
                }
            }
        }

        // 跑满轮数还没拿到文本 —— 让模型用现有信息给个答复
        val final = runCatching {
            LlmClient.chatOnce(config, messages)
        }.getOrDefault("")
        AgentResult(final, chosenMood, null)
    }

    /**
     * 跑一次带工具的**流式**对话
     *
     * 聊天页要打字机效果，但也要能调工具 ——
     * 所以这里用 [LlmClient.chatStreamWithTools]：
     * 模型先要工具就跑工具（这轮没有正文），
     * 拿到结果再跑一轮，这轮才有正文，边收边吐给 UI。
     *
     * @param onChunk 正文增量回调（只在最终那轮触发）
     * @param configOverride 覆盖默认配置（聊天页要用人格卡的温度）
     */
    suspend fun runStreaming(
        systemPrompt: String,
        userPrompt: String,
        extraContext: String = "",
        configOverride: AiConfig? = null,
        imageBase64: String? = null,
        imageMime: String = "image/jpeg",
        onChunk: (String) -> Unit
    ): AgentResult = withContext(Dispatchers.IO) {
        val config = configOverride ?: aiConfigStore.config.value
        if (!config.isValid) {
            return@withContext AgentResult("", null, "没有配置 AI")
        }

        val messages = mutableListOf<LlmMessage>()
        messages += LlmMessage("system", buildSystemPrompt(systemPrompt, extraContext))
        messages += LlmMessage(
            role = "user",
            content = userPrompt,
            imageBase64 = imageBase64,
            imageMime = imageMime
        )

        var chosenMood: String? = null
        var rounds = 0

        while (rounds < MAX_TOOL_ROUNDS) {
            rounds++
            val turn = LlmClient.chatStreamWithTools(config, messages, tools) { delta ->
                onChunk(delta)
            }

            when (turn) {
                is StreamTurn.Text -> {
                    return@withContext AgentResult(turn.content, chosenMood, null)
                }

                is StreamTurn.ToolCalls -> {
                    // 同上：assistant 必须带 tool_calls 数组，tool 必须带 tool_call_id
                    messages += LlmMessage(
                        role = "assistant",
                        content = "",
                        toolCalls = turn.calls
                    )
                    turn.calls.forEach { call ->
                        val tool = tools.firstOrNull { it.name == call.name }
                        val result = if (tool == null) {
                            "没有这个工具。"
                        } else {
                            runCatching { tool.executor(call.arguments) }
                                .getOrElse { "工具执行失败: ${it.message}" }
                        }
                        if (call.name == FishToolkit.TOOL_SET_MOOD) {
                            val mood = call.arguments.optString("mood").trim()
                            if (MoodStore.isAcceptableMood(mood)) {
                                chosenMood = mood
                                moodStore.set(mood)
                            }
                        }
                        messages += LlmMessage(
                            role = "tool",
                            content = result,
                            toolCallId = call.id
                        )
                    }
                }
            }
        }

        // 跑满轮数还没拿到正文 —— 让模型用现有信息给个答复
        val final = runCatching { LlmClient.chatOnce(config, messages) }.getOrDefault("")
        AgentResult(final, chosenMood, null)
    }

    /**
     * system prompt 里额外挂一段「你可以用这些工具」
     */
    private fun buildSystemPrompt(base: String, extraContext: String): String {
        val toolLines = tools.joinToString("\n") { "- ${it.name}：${it.description}" }
        return buildString {
            append(base)
            if (extraContext.isNotBlank()) {
                append("\n\n")
                append(extraContext)
            }
            append("\n\n")
            append("你可以用下面这些工具了解情况（需要时才调，不用问用户）：\n")
            append(toolLines)
            append("\n\n想给自己定一个此刻的状态时，调用 set_mood，")
            append("参数 mood 填 2~6 个字，用你自己的话，别用系统词表。")
        }
    }
}

/**
 * 一次 agent 运行的结果
 *
 * @param text 模型的最终文本
 * @param mood 模型自己选的状态词（null 表示这次没选）
 * @param error 出错原因（成功时为 null）
 */
data class AgentResult(
    val text: String,
    val mood: String?,
    val error: String?
)
