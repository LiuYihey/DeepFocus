package com.deepfocus.app.ai

import android.content.Context
import android.util.Log
import com.deepfocus.app.data.local.dao.ChatConversationDao
import com.deepfocus.app.data.local.dao.ChatDao
import com.deepfocus.app.data.local.dao.FishMemoryDao
import com.deepfocus.app.data.local.entity.ChatConversationEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 会话管理
 *
 * **用户可以开启新对话，但大肥鱼的记忆是连贯的。**
 * 新对话不是「把她忘掉」，只是把上一段**归档** —— 归档的那一刻，
 * 这段对话会被压成一张**交接笔记**（handoff note），下一段对话开局时
 * 把它读回来。她还记得刚才聊过什么，但不用把原文再喂一遍。
 *
 * 做法参考 DeepSeek Harness 的开源记忆插件
 * [dsh-memory](https://github.com/haitang1/dsh-memory)（MIT）：
 * 每个 session 结束时蒸馏出一份 rollout summary，
 * 下一段对话把它注入 prompt；全局长期记忆另有自己的一本账。
 * 本项目对应：
 * - **交接笔记** = [ChatConversationEntity.summary]
 * - **长期记忆** = `fish_memories` 表（跨会话，删会话时一起删）
 *
 * 交接笔记的四个小节对齐
 * [dsh-auto-memory](https://github.com/Aik358/dsh-auto-memory) 的 handoff 结构：
 * 聊了什么 / 用户在意什么 / 约定了什么 / 还能接着说什么。
 */
@Singleton
class ConversationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val conversationDao: ChatConversationDao,
    private val chatDao: ChatDao,
    private val memoryDao: FishMemoryDao,
    private val aiConfigStore: AiConfigStore
) {

    companion object {
        private const val TAG = "ConversationManager"

        /** 当前会话 id 存哪 —— 不能用包名（见项目规则 §4.2） */
        private const val PREFS = "deep_focus_chat"
        private const val KEY_CURRENT_ID = "current_conversation_id"

        /** 新对话的默认标题 */
        const val DEFAULT_TITLE = "新的对话"

        /**
         * 摘要的 token 上限
         *
         * 推理模型（MiniMax-M3.1-Flash）会先烧一段 token 思考，
         * max_tokens 给小了正文就是空串 —— 实测 1024 时 completion_tokens
         * 全被 reasoning 吃掉，content 长度为 0。4096 才稳。
         */
        private const val SUMMARY_MAX_TOKENS = 4096

        /** 交接笔记的字数上限（超了模型会自己收） */
        private const val SUMMARY_MAX_CHARS = 240

        /**
         * 上下文压缩阈值
         *
         * 一段对话超过这么多条，就把前面的一部分压进摘要，
         * 只留最近 [KEEP_RECENT_RAW] 条原文在上下文窗口里。
         * 这样「上下文不过载」是**结构保证**，不靠模型自觉。
         */
        private const val COMPACT_THRESHOLD = 40

        /** 压缩时保留的原文条数 */
        private const val KEEP_RECENT_RAW = 20

        /**
         * 新会话第一句话等交接笔记的上限
         *
         * 交接笔记在后台压，通常用户打完招呼就压好了。
         * 真压不出来也不能让人干等 —— 超过这个时长就当没有。
         */
        private const val HANDOFF_WAIT_MS = 45_000L
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * 后台压缩用的作用域
     *
     * 单例活到进程结束 —— 用户切走页面也别把正在压的交接笔记掐掉。
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 正在后台压的交接笔记：会话 id → 结果 */
    private val pendingSummaries = mutableMapOf<Long, Deferred<String?>>()

    private val _currentId = MutableStateFlow(prefs.getLong(KEY_CURRENT_ID, 0L))

    /** 当前会话 id（0 = 还没建过） */
    val currentId: StateFlow<Long> = _currentId.asStateFlow()

    /** 全部会话（按最近活动倒序）—— 用户用来翻旧对话、删掉不要的 */
    fun observeConversations(): Flow<List<ChatConversationEntity>> =
        conversationDao.observeAll()

    /**
     * 拿到当前会话，没有就建一个
     *
     * 老的聊天记录（迁移前的那批，conversationId 为 NULL）
     * 在迁移里被归到 id = 1 的「最早的那段对话」，所以这里不会漏。
     */
    suspend fun ensureCurrent(): Long {
        val id = _currentId.value
        if (id != 0L && conversationDao.getById(id) != null) return id
        val created = conversationDao.insert(
            ChatConversationEntity(
                title = DEFAULT_TITLE,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
        setCurrent(created)
        return created
    }

    /** 切换当前会话 */
    suspend fun switchTo(id: Long) {
        if (conversationDao.getById(id) == null) return
        setCurrent(id)
        conversationDao.touch(id)
    }

    /**
     * 开启新对话
     *
     * 旧会话**归档**（不删），并给它压一张交接笔记；
     * 新会话从这张笔记接着聊 —— 记忆连贯，但上下文不会越滚越大。
     *
     * @param title 新对话标题
     * @return 新会话 id
     */
    suspend fun startNew(title: String = DEFAULT_TITLE): Long {
        val previous = _currentId.value.takeIf { it != 0L }

        // 先把界面切过去 —— 交接笔记在后台压。
        // 压一次要走模型（推理模型要几十秒），同步等会让「新对话」按钮
        // 点下去半天没反应 —— 用户反馈过「点击新对话，反应有些迟钝」。
        val created = conversationDao.insert(
            ChatConversationEntity(
                title = title,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
        setCurrent(created)

        if (previous != null) {
            pendingSummaries[previous] = scope.async {
                runCatching { summarize(previous) }
                    .onFailure { Log.w(TAG, "生成交接笔记失败：${it.message}") }
                    .getOrNull()
            }
        }
        return created
    }

    /**
     * 删除会话
     *
     * 连带删掉：这段对话的消息、这段对话里抽出来的记忆。
     * 不删记忆的话，她会一边说「不记得了」一边又提起被删掉的事。
     *
     * @param id 要删的会话 id
     * @return 删除后新的当前会话 id（删的就是当前会话时，会开一段新的）
     */
    suspend fun delete(id: Long): Long {
        val wasCurrent = _currentId.value == id
        chatDao.deleteByConversation(id)
        memoryDao.deleteByConversation(id)
        conversationDao.deleteById(id)
        if (wasCurrent) {
            _currentId.value = 0L
            prefs.edit().remove(KEY_CURRENT_ID).apply()
        }
        return ensureCurrent()
    }

    /**
     * 给会话补个标题
     *
     * 拿用户说的第一句话当标题 —— 比「新的对话」有辨识度，
     * 归档列表里一眼能认出是哪一段。
     */
    suspend fun ensureTitle(id: Long, fromUserMessage: String) {
        val conversation = conversationDao.getById(id) ?: return
        if (conversation.title != DEFAULT_TITLE) return
        val title = fromUserMessage.trim().take(16).replace('\n', ' ').ifBlank { DEFAULT_TITLE }
        conversationDao.saveTitle(id, title)
    }

    /** 刷新会话的活跃时间 */
    suspend fun touch(id: Long) = conversationDao.touch(id)

    /**
     * 当前会话自己的摘要（上下文压缩产物）
     *
     * 一段对话太长时，早期内容被压进这里 —— 原文不再进上下文窗口，
     * 但信息还在（压成几百字）。
     */
    suspend fun currentSummary(): String =
        conversationDao.getById(_currentId.value)?.summary?.takeIf { it.isNotBlank() } ?: ""

    /**
     * 上一段对话的交接笔记
     *
     * 新会话开局时注入 —— 这就是「开新对话但记忆连贯」的那根线。
     *
     * 取的是**最近一个别的会话**的摘要：归档时摘要写在旧会话身上，
     * 新会话只是把它读回来。
     */
    suspend fun handoffNote(): String {
        val current = _currentId.value

        // 最近的那一段旧对话优先
        val latest = conversationDao.getRecent(excludeId = current, limit = 1).firstOrNull()
        if (latest != null) {
            if (!latest.summary.isNullOrBlank()) return latest.summary.orEmpty()

            // 摘要还在后台压 —— 等一下再走，别让她在新对话里失忆。
            // 给个上限：真压不出来就当没有，不能让用户干等。
            pendingSummaries[latest.id]?.let { deferred ->
                val note = withTimeoutOrNull(HANDOFF_WAIT_MS) { deferred.await() }
                if (!note.isNullOrBlank()) return note
                // 压完了但还是空的（没配 API / 空会话）→ 往前找
                pendingSummaries.remove(latest.id)
                return conversationDao.getRecent(excludeId = current, limit = 3)
                    .firstOrNull { !it.summary.isNullOrBlank() }
                    ?.summary
                    .orEmpty()
            }
        }

        // 再往前找：有些会话没压成功，但更早的有
        return conversationDao.getRecent(excludeId = current, limit = 3)
            .firstOrNull { !it.summary.isNullOrBlank() }
            ?.summary
            .orEmpty()
    }

    /**
     * 上下文压缩：对话太长时，把早期内容压进摘要
     *
     * 只保留最近 [KEEP_RECENT_RAW] 条原文在窗口里 ——
     * 这样即使聊了一整天，喂给模型的上下文也是有界的。
     *
     * 压缩失败不影响聊天（只是上下文会长一点）。
     */
    suspend fun compactIfNeeded(conversationId: Long) {
        val conversation = conversationDao.getById(conversationId) ?: return
        val messages = chatDao.getByConversationForSummary(conversationId)
        if (messages.size < COMPACT_THRESHOLD) return

        // 只压「已经落在旧水位之后、且离尾部还很远」的那部分
        val cutoff = messages.size - KEEP_RECENT_RAW
        if (cutoff <= 0) return
        val toCompact = messages.take(cutoff)
        if (toCompact.isEmpty()) return
        // 已经压过的就别再压一遍
        if (toCompact.last().id <= conversation.compactedUpToId) return

        runCatching { summarize(conversationId, toCompact) }
            .onFailure { Log.w(TAG, "压缩上下文失败：${it.message}") }
    }

    /**
     * 压交接笔记
     *
     * 让模型把这段对话压成几百字 —— 聊了什么、用户在意什么、
     * 约定了什么、还能接着说什么。
     *
     * 压不出来就返回 null（**绝不影响开新对话**）。
     *
     * @param messages 要压的消息（null = 压整段会话）
     */
    suspend fun summarize(
        conversationId: Long,
        messages: List<com.deepfocus.app.data.local.entity.ChatMessageEntity>? = null
    ): String? {
        val config = aiConfigStore.config.value
        if (!config.isValid) return null

        val target = messages ?: chatDao.getByConversationForSummary(conversationId)
        if (target.isEmpty()) return null

        val conversation = conversationDao.getById(conversationId) ?: return null
        val transcript = target.joinToString("\n") { msg ->
            val who = when (msg.role) {
                "USER" -> "用户"
                "FISH" -> "大肥鱼"
                else -> "（发生的事）"
            }
            "$who：${msg.content.take(120)}"
        }

        val prompt = """
把下面这段对话压成一张**交接笔记**，用于下一段对话开头让她想起来。
要求：
1. 最多 ${SUMMARY_MAX_CHARS} 字，几句话写完
2. 只写事实，不写感想，不写「我们聊了聊」这种废话
3. 四件事各一句：刚才聊了什么 / 用户在意什么 / 约定了什么 / 还能接着说什么
4. 不写标题、不用列表符号、不加引号，直接写正文

对话记录：
$transcript
""".trimIndent()

        val raw = LlmClient.chatOnce(
            config = if (config.maxTokens < SUMMARY_MAX_TOKENS) {
                config.copy(maxTokens = SUMMARY_MAX_TOKENS)
            } else {
                config
            },
            messages = listOf(
                LlmMessage("system", "你只输出正文，不要多余的话。"),
                LlmMessage("user", prompt)
            )
        ).trim()

        if (raw.isBlank()) return null

        val note = raw
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
            .take(SUMMARY_MAX_CHARS * 2)

        // 旧的摘要压掉，避免越攒越长
        val merged = listOfNotNull(conversation.summary, note)
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
            .takeLast(SUMMARY_MAX_CHARS * 3)

        conversationDao.saveSummary(
            id = conversationId,
            summary = merged,
            upToId = target.maxOf { it.id },
            now = System.currentTimeMillis()
        )
        return merged
    }

    private fun setCurrent(id: Long) {
        _currentId.value = id
        prefs.edit().putLong(KEY_CURRENT_ID, id).apply()
    }
}
