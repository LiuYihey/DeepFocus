package com.deepfocus.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepfocus.app.ai.StickerCatalog
import com.deepfocus.app.ai.StickerMarker
import com.deepfocus.app.data.local.entity.ChatConversationEntity
import com.deepfocus.app.data.local.entity.ChatMessageEntity
import com.deepfocus.app.ui.components.AmbientHalo
import com.deepfocus.app.ui.components.FishCanvas
import com.deepfocus.app.ui.components.SoftTextField
import com.deepfocus.app.ui.components.TypewriterText
import com.deepfocus.app.ui.theme.themedWeight

/**
 * 对话界面
 *
 * 取代原统计页：这里是大肥鱼与用户的聊天窗口。
 * 顶部展示大肥鱼当前状态（好感度 / 饱腹度 / 情绪），
 * 底部是输入框，右上角有「让大肥鱼说点什么」与「清空」两个操作。
 */
@Composable
fun ChatScreen(
    onOpenPersona: () -> Unit = {},
    onOpenAiSettings: () -> Unit = {},
    onOpenMemory: () -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val currentConversationId by viewModel.currentConversationId.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val hapticFeedback = LocalHapticFeedback.current

    // 对话记录抽屉：翻旧对话 / 删掉不要的
    var showHistory by remember { mutableStateOf(false) }

    // 系统照片选择器 —— 零运行时权限，选不到相册的其他内容
    val photoPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.sendImage(uri)
        }
    }

    // 新消息或流式文本变化时滚到底
    LaunchedEffect(messages.size, uiState.streamingText) {
        if (messages.isNotEmpty() || uiState.streamingText.isNotEmpty()) {
            listState.animateScrollToItem(
                (messages.size + if (uiState.streamingText.isNotEmpty()) 1 else 0).coerceAtLeast(0)
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // ── 顶部：大肥鱼状态条 ──
        ChatHeader(
            fishName = uiState.fishName,
            affinityTitle = uiState.affinityTitle,
            moodDescription = uiState.moodDescription,
            onOpenPersona = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onOpenPersona()
            },
            onNewConversation = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.startNewConversation()
            },
            onOpenHistory = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                showHistory = true
            }
        )

        // ── 消息列表 ──
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 未配置 API 时的引导
            if (!uiState.isConfigured) {
                item {
                    NotConfiguredCard(onOpenAiSettings = onOpenAiSettings)
                }
            }

            // 空对话时的欢迎语
            // 撑满一屏并居中 —— 大肥鱼落在页面正中，而不是贴着顶部
            if (messages.isEmpty()) {
                item {
                    WelcomeCard(
                        modifier = if (uiState.isConfigured) {
                            Modifier.fillParentMaxSize()
                        } else {
                            Modifier
                        }
                    )
                }
            }

            // 候选记忆提示：大肥鱼想记住某件事，等用户点头
            if (uiState.pendingMemoryCount > 0) {
                item {
                    PendingMemoryBanner(
                        count = uiState.pendingMemoryCount,
                        onClick = onOpenMemory
                    )
                }
            }

            items(messages, key = { it.id }) { message ->
                ChatBubble(message)
            }

            // 流式输出中的气泡
            if (uiState.streamingText.isNotEmpty()) {
                item {
                    ChatBubble(
                        ChatMessageEntity(
                            role = "FISH",
                            content = uiState.streamingText,
                            kind = "CHAT"
                        ),
                        isStreaming = true
                    )
                }
            }

            // 等待首个 token 时的加载态
            if (uiState.isReplying && uiState.streamingText.isEmpty()) {
                item { TypingIndicator() }
            }
        }

        // ── 错误提示 ──
        AnimatedVisibility(
            visible = uiState.errorMessage != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Text(
                text = uiState.errorMessage.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // ── 底部输入区 ──
        ChatInputBar(
            input = uiState.input,
            isReplying = uiState.isReplying,
            canSend = uiState.canSend,
            onInputChange = viewModel::updateInput,
            onSend = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.send()
            },
            onStop = viewModel::stopReplying,
            onTriggerProactive = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.triggerProactive()
            },
            onPickImage = {
                // 系统照片选择器：零运行时权限，用户只交出自己选中的那一张
                photoPicker.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                    )
                )
            }
        )
    }

    // ── 对话记录抽屉 ──
    if (showHistory) {
        ConversationHistorySheet(
            conversations = conversations,
            currentId = currentConversationId,
            onDismiss = { showHistory = false },
            onSelect = { id ->
                showHistory = false
                viewModel.switchConversation(id)
            },
            onDelete = { id -> viewModel.deleteConversation(id) }
        )
    }
}

/**
 * 顶部状态条
 *
 * 显示**名字**而不是称号 —— 名字是用户起的，是"我们之间"的凭证；
 * 称号是系统给的里程碑，放在档案卡里更合适。
 * 副标题给状态语义，不给「好感度 10 · 饱腹 70」这种内部数值。
 */
@Composable
private fun ChatHeader(
    fishName: String,
    affinityTitle: String,
    moodDescription: String,
    onOpenPersona: () -> Unit,
    onNewConversation: () -> Unit,
    onOpenHistory: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 大肥鱼头像（缩略）
            // 底衬用主色低透明度的圆，而不是灰块 —— 灰块让头像看起来像张贴纸。
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                FishCanvas(
                    state = "idle",
                    modifier = Modifier.fillMaxSize(),
                    useVideo = false
                )
            }

            Spacer(modifier = Modifier.size(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fishName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = themedWeight(FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = moodDescription.ifBlank { affinityTitle },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }

            IconButton(onClick = onOpenHistory) {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = "对话记录",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            // 「开启新对话」：旧对话归档，她还记得聊过什么
            IconButton(onClick = onNewConversation) {
                Icon(
                    imageVector = Icons.Filled.AddComment,
                    contentDescription = "开启新对话",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(onClick = onOpenPersona) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = "人格卡",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * 单条消息气泡
 */
@Composable
private fun ChatBubble(
    message: ChatMessageEntity,
    isStreaming: Boolean = false
) {
    val isUser = message.role == "USER"
    // 主动开口的消息用左侧色带标记，让用户一眼看出这是大肥鱼自己发起的
    val isProactive = message.kind == "PROACTIVE"
    val context = androidx.compose.ui.platform.LocalContext.current

    // 流式输出时，模型正在打的字里可能含表情标记
    // （[表情:st_xxx]）。直接显示会让用户看到这串符号被逐字打出来，
    // 所以渲染前先剥掉。等回复结束、消息落库后，
    // 正文里本来就没有标记了。
    val displayText = remember(message.content, isStreaming) {
        if (isStreaming) StickerMarker.stripMarkers(message.content) else message.content
    }

    // 表情包：用模型自己选的那张（存在 action 字段里）。
    // 模型没选就不显示 —— 她自己读懂清单后决定用不用。
    // seed 用消息 id，保证同一条消息每次重组都是同一张脸，不会闪。
    val sticker = remember(message.action, isUser) {
        if (isUser) null
        else StickerMarker.resolve(message.action)
    }
    val stickerBitmap = remember(sticker, message.id) {
        sticker?.let { StickerCatalog.load(context, it, message.id.toInt()) }
    }
    // 这张取不到时退到同情绪的无文字大立绘（st_{情绪}_big）——
    // 情绪对得上，比随便换一张别的情绪强。
    val fallbackBitmap = remember(sticker, stickerBitmap) {
        if (stickerBitmap != null) null
        else sticker?.let { s ->
            StickerCatalog.bigFallback(s)?.let { StickerCatalog.load(context, it) }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                FishCanvas(
                    state = "idle",
                    modifier = Modifier.fillMaxSize(),
                    useVideo = false
                )
            }
            Spacer(modifier = Modifier.size(8.dp))
        }

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 260.dp)
        ) {
            if (isProactive) {
                Text(
                    text = "大肥鱼主动说",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 4.dp, bottom = 3.dp)
                )
            }

            // 表情包：在气泡上方显示大肥鱼的表情
            // 优先用模型自己选的那张（stickerBitmap），
            // 没有才退到同情绪的无文字大表情（fallbackBitmap）
            //
            // 流式过程中不显示：此时正文还在打字，表情包会突然冒出来，
            // 而且模型可能选了几次才定稿。回复结束再一次性给出。
            val shownBitmap = if (isStreaming) null else (stickerBitmap ?: fallbackBitmap)
            if (shownBitmap != null) {
                Image(
                    bitmap = shownBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(120.dp)
                        .padding(bottom = 4.dp)
                )
            }

            // 用户发的图：气泡里重画一遍（文件在私有目录，长期可读）
            val imagePath = message.imageUri
            if (!imagePath.isNullOrBlank()) {
                val bitmap = remember(imagePath) {
                    runCatching { android.graphics.BitmapFactory.decodeFile(imagePath) }.getOrNull()
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "发给她看的图",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .widthIn(max = 200.dp)
                            .heightIn(max = 240.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .padding(bottom = 4.dp)
                    )
                }
            }

            Surface(
                color = if (isUser) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 5.dp,
                    bottomEnd = if (isUser) 5.dp else 16.dp
                )
            ) {
                // 流式回复用打字机效果（规范 4）：
                // 每个字符 blur(6px) 渐显，单字 120ms，字间错开 30ms
                if (isStreaming) {
                    TypewriterText(
                        text = displayText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                } else {
                    Text(
                        text = displayText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

/**
 * 等待首个 token 的加载态
 */
@Composable
private fun TypingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(modifier = Modifier.size(8.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 5.dp, bottomEnd = 16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * 未配置 API 的引导条
 *
 * 旧版是一整块灰底大卡片，视觉权重压过了大肥鱼本身 ——
 * 用户进来第一眼看到的是"你还没配 API"，而不是"我在这儿"。
 *
 * 改成一条轻量提示：一行话 + 一个文字链接，安静地待着。
 */
@Composable
private fun NotConfiguredCard(onOpenAiSettings: () -> Unit) {
    val hapticFeedback = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Key,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = "还没连上脑子",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "去配置",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onOpenAiSettings()
            }
        )
    }
}

/**
 * 候选记忆提示条
 *
 * 大肥鱼从你说的话里捡到了可能值得记的事，先放着。
 * 你点头它才真的记住 —— 假装记住了比没记住更伤。
 */
@Composable
private fun PendingMemoryBanner(count: Int, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "大肥鱼想记住 $count 件事",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "去看看",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * 空对话时的欢迎语
 *
 * 只有形象 + 一句话。不放副标题、不放合规说明 ——
 * 留白本身就把视线落到中间那条鱼上。
 * 合规提示已挪到设置页底部（见 `SettingsScreen`）。
 */
@Composable
private fun WelcomeCard(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 16.dp)
        ) {
            // 光晕托底：让大肥鱼从空白的对话区里"浮"出来
            Box(
                modifier = Modifier.size(200.dp),
                contentAlignment = Alignment.Center
            ) {
                AmbientHalo(
                    modifier = Modifier.size(190.dp),
                    diameter = 190.dp
                )
                FishCanvas(
                    state = "idle",
                    modifier = Modifier.size(160.dp),
                    useVideo = false
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "我是吃白饭的大肥鱼",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = themedWeight(FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

/**
 * 底部输入区
 */
@Composable
private fun ChatInputBar(
    input: String,
    isReplying: Boolean,
    canSend: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onTriggerProactive: () -> Unit,
    onPickImage: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // 让大肥鱼主动开口
            IconButton(onClick = onTriggerProactive, enabled = !isReplying) {
                Icon(
                    imageVector = Icons.Filled.Chat,
                    contentDescription = "让大肥鱼说点什么",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                    modifier = Modifier.size(22.dp)
                )
            }

            // 发图给她看（系统照片选择器，不申请任何权限）
            IconButton(onClick = onPickImage, enabled = !isReplying) {
                Icon(
                    imageVector = Icons.Filled.AddPhotoAlternate,
                    contentDescription = "发一张图给她看",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                    modifier = Modifier.size(22.dp)
                )
            }

            SoftTextField(
                value = input,
                onValueChange = onInputChange,
                label = null,
                modifier = Modifier.weight(1f),
                placeholder = "跟大肥鱼说点什么…",
                minLines = 1,
                maxLines = 4
            )

            Spacer(modifier = Modifier.size(6.dp))

            if (isReplying) {
                IconButton(onClick = onStop) {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = "停止",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            } else {
                IconButton(onClick = onSend, enabled = canSend) {
                    Icon(
                        imageVector = Icons.Filled.Send,
                        contentDescription = "发送",
                        tint = if (canSend) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

/**
 * 对话记录抽屉
 *
 * 用户的诉求：「可以开启新的对话，但大肥鱼记忆是连贯的，
 * 开启新对话只是让用户比较好的归档而已」。
 * 所以这里给三件事：
 * - 看旧对话（点一条切回去）
 * - 删掉不要的（连带这段对话里大肥鱼记住的事一起删）
 * - 明确告诉她「删了就是真删」
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationHistorySheet(
    conversations: List<ChatConversationEntity>,
    currentId: Long,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit,
    onDelete: (Long) -> Unit
) {
    var pendingDelete by remember { mutableStateOf<ChatConversationEntity?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "对话记录",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = themedWeight(FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "开新对话不会让她失忆 —— 旧对话只是归档，她还记得聊过什么。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (conversations.isEmpty()) {
                Text(
                    text = "还没有对话记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(conversations, key = { it.id }) { conversation ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelect(conversation.id) }
                                .padding(horizontal = 10.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = conversation.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (conversation.id == currentId) {
                                        themedWeight(FontWeight.SemiBold)
                                    } else {
                                        themedWeight(FontWeight.Normal)
                                    },
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                Text(
                                    text = if (conversation.id == currentId) {
                                        "当前对话"
                                    } else {
                                        conversation.summary?.take(40)?.ifBlank { "—" } ?: "—"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            IconButton(onClick = { pendingDelete = conversation }) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteOutline,
                                    contentDescription = "删除这段对话",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 二次确认：删了就是真删（消息 + 这段对话里抽出来的记忆一起消失）
    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删掉这段对话？") },
            text = {
                Text("对话记录和这段对话里大肥鱼记住的事会一起消失，没法恢复。")
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(target.id)
                    pendingDelete = null
                }) {
                    Text("删掉", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("算了")
                }
            }
        )
    }
}
