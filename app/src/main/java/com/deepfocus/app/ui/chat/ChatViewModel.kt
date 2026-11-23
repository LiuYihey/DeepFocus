package com.deepfocus.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepfocus.app.ai.AiConfigStore
import com.deepfocus.app.ai.FishBrain
import com.deepfocus.app.ai.FishMood
import com.deepfocus.app.ai.FishMoodResolver
import com.deepfocus.app.ai.FishStateEngine
import com.deepfocus.app.ai.MemoryEngine
import com.deepfocus.app.ai.ProactiveAction
import com.deepfocus.app.ai.ProactiveMotive
import com.deepfocus.app.data.local.entity.ChatConversationEntity
import com.deepfocus.app.data.local.entity.ChatMessageEntity
import com.deepfocus.app.data.local.entity.FishStateEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 对话页 UI 状态
 */
data class ChatUiState(
    /** 当前输入框内容 */
    val input: String = "",
    /** 待发送的图片（从相册选好，还没配文 / 还没发） */
    val pendingImageUri: android.net.Uri? = null,
    /** 是否正在等待大肥鱼回复 */
    val isReplying: Boolean = false,
    /** 流式回复的实时文本（打字机效果） */
    val streamingText: String = "",
    /** 错误提示 */
    val errorMessage: String? = null,
    /** 是否已完成配置 API */
    val isConfigured: Boolean = false,
    /** 大肥鱼当前状态分档 */
    val moodTier: FishMood.Tier = FishMood.Tier.SHY,
    /** 当前状态描述 */
    val moodDescription: String = "",
    /** 大肥鱼的名字（用户起的） */
    val fishName: String = "大肥鱼",
    /** 好感度 */
    val affinity: Int = 0,
    /** 好感度档位标题 */
    val affinityTitle: String = "初次见面",
    /** 待用户确认的候选记忆条数（>0 时聊天页给个提示） */
    val pendingMemoryCount: Int = 0
) {
    /**
     * 是否可以发送
     *
     * 必须包含 [isConfigured]：没配 API Key 时点发送只会弹一句「请先配置 API」，
     * 用户白等一趟。直接不让点更干脆。
     */
    val canSend: Boolean
        get() = (input.isNotBlank() || pendingImageUri != null) && !isReplying && isConfigured
}

/**
 * 对话 ViewModel
 *
 * 承接原统计页的位置：现在这一页是「大肥鱼与你聊天」。
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val fishBrain: FishBrain,
    private val aiConfigStore: AiConfigStore,
    private val memoryEngine: MemoryEngine,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context
) : ViewModel() {

    /** 全部消息 */
    val messages: StateFlow<List<ChatMessageEntity>> = fishBrain.observeMessages()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** 大肥鱼状态 */
    val fishState: StateFlow<FishStateEntity?> = fishBrain.observeState()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    /** 全部会话（翻旧对话 / 删掉不要的） */
    val conversations: StateFlow<List<ChatConversationEntity>> = fishBrain.observeConversations()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** 当前会话 id */
    val currentConversationId: StateFlow<Long> = fishBrain.currentConversationId

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    /** 当前流式请求的 Job，用于中途取消 */
    private var streamingJob: Job? = null

    init {
        viewModelScope.launch {
            aiConfigStore.config.collect { config ->
                _uiState.update { it.copy(isConfigured = config.isValid) }
            }
        }
        refreshMood()
    }

    /** 更新输入框 */
    fun updateInput(text: String) {
        _uiState.update { it.copy(input = text) }
    }

    /** 发送消息（纯文字，或「图片 + 可选配文」） */
    fun send() {
        val text = _uiState.value.input.trim()
        val image = _uiState.value.pendingImageUri
        if (_uiState.value.isReplying) return

        if (image != null) {
            sendImage(image, text)
            return
        }
        if (text.isEmpty()) return

        _uiState.update { it.copy(input = "", isReplying = true, streamingText = "", errorMessage = null) }

        streamingJob = viewModelScope.launch {
            try {
                fishBrain.chat(
                    userInput = text,
                    onChunk = { delta ->
                        _uiState.update { it.copy(streamingText = it.streamingText + delta) }
                    }
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = e.message ?: "和大肥鱼说话失败了，稍后再试试")
                }
            } finally {
                _uiState.update { it.copy(isReplying = false, streamingText = "") }
                refreshMood()
            }
        }
    }

    /** 停止生成 */
    fun stopReplying() {
        streamingJob?.cancel()
        streamingJob = null
        _uiState.update { it.copy(isReplying = false, streamingText = "") }
    }

    /**
     * 从相册选好一张图
     *
     * 只挂到输入框，不立即发送 —— 用户可以再输入配文，最后一起发。
     */
    fun attachImage(uri: android.net.Uri) {
        _uiState.update { it.copy(pendingImageUri = uri) }
    }

    /** 移除还没发出去的图片 */
    fun removePendingImage() {
        _uiState.update { it.copy(pendingImageUri = null) }
    }

    /**
     * 发一张图给她看
     *
     * 图片先压小、落一份到私有目录，再把 base64 随消息送进模型 ——
     * 模型是真的「看到」这张图，不是只收到一个文件路径。
     *
     * @param uri Photo Picker 返回的图片 Uri
     * @param text 可选的配文（为空就只发图）
     */
    fun sendImage(uri: android.net.Uri, text: String = "") {
        if (_uiState.value.isReplying) return
        _uiState.update {
            it.copy(
                input = "",
                pendingImageUri = null,
                isReplying = true,
                streamingText = "",
                errorMessage = null
            )
        }

        streamingJob = viewModelScope.launch {
            try {
                val encoded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.deepfocus.app.util.ImageEncoding.encode(appContext, uri)
                }
                if (encoded == null) {
                    _uiState.update {
                        it.copy(errorMessage = "这张图读不出来，换一张试试")
                    }
                    return@launch
                }

                val prompt = text.ifBlank { "看看这张图" }
                fishBrain.chat(
                    userInput = prompt,
                    onChunk = { delta ->
                        _uiState.update { it.copy(streamingText = it.streamingText + delta) }
                    },
                    imageBase64 = encoded.base64,
                    imageMime = encoded.mime,
                    imageLocalPath = encoded.localPath
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = e.message ?: "和大肥鱼说话失败了，稍后再试试")
                }
            } finally {
                _uiState.update { it.copy(isReplying = false, streamingText = "") }
                refreshMood()
            }
        }
    }

    /** 清空对话记录 */
    fun clearChat() {
        viewModelScope.launch {
            fishBrain.clearChat()
            _uiState.update { it.copy(errorMessage = null) }
        }
    }

    /**
     * 开启新对话
     *
     * 旧对话**归档**（不删），并压一张交接笔记 ——
     * 她还记得刚才聊过什么，但上下文不会越滚越大。
     */
    fun startNewConversation() {
        viewModelScope.launch {
            fishBrain.startNewConversation()
            _uiState.update { it.copy(streamingText = "", errorMessage = null) }
        }
    }

    /** 删除一段对话（连带这段对话里抽出来的记忆一起删） */
    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            fishBrain.deleteConversation(id)
            _uiState.update { it.copy(errorMessage = null) }
        }
    }

    /** 切回某段对话 */
    fun switchConversation(id: Long) {
        viewModelScope.launch {
            fishBrain.switchConversation(id)
            _uiState.update { it.copy(streamingText = "", errorMessage = null) }
        }
    }

    /**
     * 手动触发一次主动行为
     *
     * 用户主动点「让大肥鱼说点什么」时用，绕过概率判定直接触发。
     */
    fun triggerProactive(action: ProactiveAction = ProactiveAction.CHAT) {
        if (_uiState.value.isReplying) return
        viewModelScope.launch {
            _uiState.update { it.copy(isReplying = true, errorMessage = null) }
            // 手动触发时也给一个由头，让这次主动开口也有理由
            val result = fishBrain.runProactive(action, motive = ProactiveMotive.SILENCE)
            if (result == null) {
                _uiState.update { it.copy(errorMessage = "大肥鱼现在没什么想说的（可能还没配置 API）") }
            }
            _uiState.update { it.copy(isReplying = false) }
            refreshMood()
        }
    }

    /** 刷新状态分档 */
    private fun refreshMood() {
        viewModelScope.launch {
            val state = fishBrain.getState()
            val affinity = fishBrain.currentAffinity()
            val mood = FishMoodResolver.resolve(
                affinity = affinity,
                satiety = state.satiety,
                isHungry = FishStateEngine.isHungry(state, System.currentTimeMillis()),
                isSleeping = state.isSleeping
            )
            _uiState.update {
                it.copy(
                    moodTier = mood.tier,
                    moodDescription = mood.description,
                    fishName = fishBrain.currentFishName(),
                    affinity = affinity,
                    affinityTitle = FishMoodResolver.affinityTitle(affinity)
                )
            }
        }
        // 候选记忆条数：有新候选时在聊天页给个提示
        viewModelScope.launch {
            memoryEngine.observeCandidates().collect { list ->
                _uiState.update { it.copy(pendingMemoryCount = list.size) }
            }
        }
    }
}
