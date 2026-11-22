package com.deepfocus.app.ai

import android.content.Context
import com.deepfocus.app.data.repository.FishRepository
import com.deepfocus.app.data.repository.StatsRepositoryImpl
import com.deepfocus.app.data.repository.TodoRepository
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 大肥鱼可以自己调用的工具
 *
 * 用户要求（2026-11-18）：
 * > 「图3中吃的很饱的状态变更成大肥鱼自己选择的状态词汇，
 * > 大肥鱼可以选择自己的情绪和状态，这是一个大肥鱼可以使用的agent tool，
 * > 大肥鱼还有tool可以知道用户今天专注了多久，
 * > 同时使用本机哪些软件多长的时间，帮助用户复盘今日手机使用。
 * > 大肥鱼今天吃了多少饭也会记在心里，太久没吃饭了就会饿饿。」
 *
 * 为什么要做成 tool 而不是直接把数据塞进 prompt
 * ------------------------------------------------
 * 之前的做法（[WorldContextProvider.describe]）是把所有世界信息**一次性**
 * 拼成一段话塞进 system prompt。那是「我告诉你这些事实」，
 * 模型只能被动接收，状态词也是从固定四选一里挑。
 *
 * 做成 tool 之后：
 * - **模型自己决定要看什么**。问「我今天用手机多久」时它去调
 *   `app_usage`，问「我饿了没」时它去调 `meal_status`。
 *   不相关的工具不调，省 token 也省模型注意力。
 * - **状态词是模型自己写的**。`set_mood` 工具的参数就是状态词本身，
 *   模型根据它看到的事实挑一个合适的说法 ——
 *   不再是「吃得很饱 / 饿了 / 精神不错」这种机器词表。
 *
 * 工具调用的执行方是**本地代码**，不是模型 ——
 * 模型只能「请求」查数据，数据始终从本地 Room / UsageStats 出。
 */
data class FishTool(
    /** 工具名（英文，模型用） */
    val name: String,
    /** 给模型看的中文说明 */
    val description: String,
    /** 参数的 JSON Schema（OpenAI 兼容格式） */
    val parameters: JSONObject,
    /** 真正执行 */
    val executor: suspend (JSONObject) -> String
)

/**
 * 工具调用轮次里的一次调用
 */
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: JSONObject
)

/**
 * 一次模型回复的结果
 *
 * 模型可能只回文本，也可能回 tool_calls（要继续调工具）。
 */
sealed interface LlmTurn {
    /** 模型回了一段文本，没有要调工具 */
    data class Text(val content: String) : LlmTurn

    /** 模型要调工具 */
    data class ToolCalls(val calls: List<ToolCall>) : LlmTurn
}

/**
 * 大肥鱼的工具箱
 *
 * 目前提供四个工具：
 * - [TOOL_TODAY_FOCUS] 今天专注了多久
 * - [TOOL_APP_USAGE] 今天用了哪些 App、多久（手机使用复盘）
 * - [TOOL_MEAL_STATUS] 今天吃了多少饭、饿不饿
 * - [TOOL_SET_MOOD] 自己选一个状态词（写进状态胶囊）
 */
object FishToolkit {

    /** 今天专注了多久 */
    const val TOOL_TODAY_FOCUS = "today_focus"

    /** 今天的 App 使用情况 */
    const val TOOL_APP_USAGE = "app_usage"

    /** 今天的饭量 / 饿不饿 */
    const val TOOL_MEAL_STATUS = "meal_status"

    /** 自己选状态词 */
    const val TOOL_SET_MOOD = "set_mood"

    /** 今天还有什么没做完 */
    const val TOOL_TODO_STATUS = "todo_status"

    /** 空 schema（无参数工具用） */
    private fun emptySchema(): JSONObject = JSONObject()
        .put("type", "object")
        .put("properties", JSONObject())
        .put("required", JSONObject())

    /**
     * 构造工具箱
     *
     * @param context 用于读 UsageStats
     * @param statsRepository 读专注时长
     * @param fishRepository 读饭量 / 好感度
     * @param worldProvider 采集 App 使用情况
     */
    fun build(
        context: Context,
        statsRepository: StatsRepositoryImpl,
        fishRepository: FishRepository,
        worldProvider: WorldContextProvider,
        todoRepository: TodoRepository
    ): List<FishTool> = listOf(
        FishTool(
            name = TOOL_TODAY_FOCUS,
            description = "查用户今天总共专注了多久。返回分钟数、几段、以及最长的一段。",
            parameters = emptySchema(),
            executor = {
                val millis = statsRepository.getTodayFocusDurationMillis()
                val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
                buildString {
                    append("今天一共专注了 ")
                    if (minutes == 0L) append("还没满一分钟")
                    else append("$minutes 分钟")
                    append("。")
                }
            }
        ),
        FishTool(
            name = TOOL_APP_USAGE,
            description = "查用户今天用了哪些 App、各用了多久。用来帮用户复盘手机使用情况。",
            parameters = emptySchema(),
            executor = {
                val usage = worldProvider.collectAppUsage()
                if (usage.isEmpty()) {
                    "今天还没查到 App 使用记录（可能没给「使用情况访问」权限）。"
                } else {
                    val total = usage.sumOf { it.durationMillis }
                    val totalMin = TimeUnit.MILLISECONDS.toMinutes(total)
                    buildString {
                        append("今天总共用了手机 $totalMin 分钟，前几名是：")
                        usage.take(5).forEach {
                            append(" ${it.appName} ${TimeUnit.MILLISECONDS.toMinutes(it.durationMillis)} 分钟")
                        }
                        append("。")
                    }
                }
            }
        ),
        FishTool(
            name = TOOL_MEAL_STATUS,
            description = "查大肥鱼自己今天吃了多少饭、距上次吃饭多久了、饿不饿。",
            parameters = emptySchema(),
            executor = {
                val todayFeeds = fishRepository.getTodayFeedCount()
                val state = fishRepository.getCurrentFishState()
                val lastFed = state?.lastFedAt ?: 0L
                val hours = if (lastFed <= 0L) {
                    -1L
                } else {
                    TimeUnit.MILLISECONDS.toHours(System.currentTimeMillis() - lastFed)
                }
                buildString {
                    append("今天吃了 $todayFeeds 顿。")
                    if (hours < 0) {
                        append("你从来没吃过东西，饿坏了。")
                    } else {
                        append("距上次吃饭过了 $hours 小时，")
                        append(if (state?.let { FishStateEngine.isHungry(it, System.currentTimeMillis()) } == true) {
                            "你已经饿了。"
                        } else {
                            "还不饿。"
                        })
                    }
                }
            }
        ),
        FishTool(
            name = TOOL_SET_MOOD,
            description = "给自己定一个此刻的状态词，会显示在首页的状态胶囊上。" +
                "用你自己的话，比如「吃撑了」「有点想你」「困得睁不开眼」。",
            parameters = JSONObject()
                .put("type", "object")
                .put(
                    "properties",
                    JSONObject()
                        .put(
                            "mood",
                            JSONObject()
                                .put("type", "string")
                                .put("description", "此刻的状态词，2~6 个字")
                        )
                )
                .put("required", org.json.JSONArray().put("mood")),
            executor = { args ->
                val mood = args.optString("mood").trim()
                when {
                    mood.isEmpty() -> "没给状态词。"
                    !MoodStore.isAcceptableMood(mood) ->
                        "这个状态词太长了，缩到 ${MoodStore.MAX_MOOD_LENGTH} 个字以内再给我。"
                    else -> "好，就叫「$mood」。"
                }
            }
        ),
        FishTool(
            name = TOOL_TODO_STATUS,
            description = "查用户今天的待办：哪些还没做完、哪些做完了。" +
                "催用户之前先查一下，别催错事。",
            parameters = emptySchema(),
            executor = {
                val undone = todoRepository.getTodayUndone()
                val all = todoRepository.getToday()
                if (all.isEmpty()) {
                    "用户今天没写任何待办。"
                } else {
                    buildString {
                        append("今天一共 ${all.size} 件事，做完了 ${all.size - undone.size} 件。")
                        if (undone.isNotEmpty()) {
                            append("还没做完的是：")
                            undone.take(5).forEach { append(" ${it.content}") }
                            append("。")
                        }
                    }
                }
            }
        )
    )

    /** 工具清单转成 OpenAI 兼容的 tools 数组 */
    fun toJsonSchema(tools: List<FishTool>): org.json.JSONArray {
        val array = org.json.JSONArray()
        tools.forEach { tool ->
            array.put(
                JSONObject()
                    .put("type", "function")
                    .put(
                        "function",
                        JSONObject()
                            .put("name", tool.name)
                            .put("description", tool.description)
                            .put("parameters", tool.parameters)
                    )
            )
        }
        return array
    }
}
