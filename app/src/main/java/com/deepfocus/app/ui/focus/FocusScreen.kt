package com.deepfocus.app.ui.focus

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import com.deepfocus.app.MainActivity
import com.deepfocus.app.data.FishAnimationCatalog
import com.deepfocus.app.ui.components.FishVideo
import com.deepfocus.app.ui.components.GlassDialog
import com.deepfocus.app.ui.components.GlassDialogActions
import com.deepfocus.app.ui.components.iconBaselineOffset
import com.deepfocus.app.ui.theme.CuteChineseFont
import com.deepfocus.app.ui.theme.CuteRewardTextStyle
import com.deepfocus.app.ui.theme.PrimarySoft
import com.deepfocus.app.ui.theme.TimerNumberFont
import com.deepfocus.app.ui.theme.timerTextStyle
import com.deepfocus.app.ui.theme.themedWeight
import com.deepfocus.app.util.assetExists

/**
 * 专注模式全屏页面（v3 · 沉浸视频版）
 *
 * 设计要点：
 * - 全屏视频作背景，ZOOM 铺满，大肥鱼在画面中央，天空在上方
 * - 计时器直接浮在天空区域，无遮罩，保持画面沉浸
 * - 左上角返回按钮，右上角状态栏避让
 * - 保留退出确认对话框、奖励动画覆盖层
 * - 移除：音乐功能、白色半透明遮罩、"大肥鱼陪伴"文字
 *
 * 防分心机制：FocusSessionState 标志位 + DeepFocusService 轮询检测切屏 → 拉回本页
 */
@Composable
@androidx.annotation.OptIn(UnstableApi::class)
fun FocusScreen(
    onExit: () -> Unit,
    viewModel: FocusViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val hapticFeedback = LocalHapticFeedback.current
    val context = LocalContext.current

    // 退出后才处理旅行日记：先给「点击查收」入口，不主动展开内容
    var showJournalContent by remember { mutableStateOf(false) }
    var exitHandled by remember { mutableStateOf(false) }
    // 首帧 isRunning 还是 false（startFocus 在 LaunchedEffect 里），
    // 必须等会话真的跑起来一次，才能把「isRunning=false」当成退出。
    var hasRun by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.isRunning) {
        if (uiState.isRunning) hasRun = true
    }

    // 进入页面自动开始计时
    LaunchedEffect(Unit) {
        viewModel.startFocus()
    }

    // 拦截系统返回键：与退出按钮走同一确认链路，避免误按丢失进度
    BackHandler {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        viewModel.requestExit()
    }

    // 收集 DeepFocusService 的拉回事件（用户切屏时被拉回）
    val focusRecall by MainActivity.focusRecallEvent.collectAsStateWithLifecycle()
    LaunchedEffect(focusRecall) {
        if (focusRecall) {
            MainActivity.requestFocusRecallConsumed()
        }
    }

    // 全屏沉浸式背景 + 计时器
    // 注意：根容器不加 systemBarsPadding —— 视频必须铺满到屏幕底部导航栏区域，
    // 否则底部会露出一条背景色白边（破坏沉浸感）。内容元素各自用 statusBarsPadding 避让。
    //
    // 底色用「深海墨蓝」而不是主题背景色：这一页的文字全是白色
    // （计时器 / 台词气泡 / 奖励横幅），白底上白字等于隐形。
    // 视频素材还没到位时，这一层就是唯一的背景，必须自己站得住。
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color(0xFF16283C),
                    0.45f to Color(0xFF0F1D2C),
                    1f to Color(0xFF0A141E)
                )
            )
    ) {
        // 视频全屏背景：focus 类别 9:16 竖屏，ZOOM 铺满
        val userFish = uiState.userFish
        if (userFish != null) {
            // 专注页书房视频（9:16 全屏 + 环境音）。
            // 这是**唯一**还用视频的地方 —— GIF 做不出竖屏书房和环境音。
            // 素材缺失时自动降级到纯背景色，不会黑屏。
            val focusPath = remember { FishAnimationCatalog.FOCUS_VIDEO_PATH }
            // 素材缺失时不要挂播放器：ExoPlayer 会报错并留下黑底，
            // 直接不渲染这一层，让下面的背景色露出来。
            val focusVideoExists = remember(focusPath) { context.assetExists(focusPath) }
            if (focusVideoExists) {
                FishVideo(
                    videoPath = focusPath,
                    modifier = Modifier.fillMaxSize(),
                    // 专注页是「上场陪看书 → 悄悄离开」的循环小剧场，
                    // 循环播放时观感是她离开一会儿又回来陪你
                    loop = true,
                    videoResizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                    edgeFade = false,  // 全屏背景视频无需边缘柔化
                    // 必须从头播，否则随机切入会截掉登场动作
                    randomStart = false,
                    // 静音：专注页不播任何声音（视频自带音轨也不放）
                    volume = 0f,
                    audioFocus = false
                )
            }
        }

        // === 顶部柔光 ===
        // 计时器悬在屏幕上方，给它一束从上方洒下的柔光，
        // 纯深色渐变背景会显得死板，这束光让画面有"呼吸"和纵深。
        // 用 drawBehind 而不是 background(Brush.radialGradient)，
        // 这样光心可以跟着实际宽度居中，换机型不会跑偏。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .align(Alignment.TopCenter)
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF5B87B5).copy(alpha = 0.18f),
                                Color(0xFF5B87B5).copy(alpha = 0.06f),
                                Color.Transparent
                            ),
                            center = Offset(size.width / 2f, size.height * 0.12f),
                            radius = size.width * 0.95f
                        )
                    )
                }
        )

        // === 顶部渐变遮罩 ===
        // 视频是亮的，计时器是白的 —— 不加遮罩会看不清。
        // 上深下浅的渐变给文字铺一层底，比给文字加黑框柔和得多。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0C1622).copy(alpha = 0.52f),
                            Color(0xFF0C1622).copy(alpha = 0.22f),
                            Color.Transparent
                        )
                    )
                )
        )

        // === 底部渐变遮罩 ===
        // 同理：底部的台词气泡和奖励横幅需要一层底
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFF0C1622).copy(alpha = 0.24f),
                            Color(0xFF0C1622).copy(alpha = 0.55f)
                        )
                    )
                )
        )

        // === 左上角返回按钮 ===
        // 毛玻璃圆钮，浮在视频之上；不再是突兀的白色实心圆。
        IconButton(
            onClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.requestExit()
            },
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 18.dp, top = 10.dp)
                .align(Alignment.TopStart)
                .size(38.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.16f))
                .border(1.dp, Color.White.copy(alpha = 0.30f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "退出专注",
                tint = Color.White.copy(alpha = 0.94f),
                modifier = Modifier.size(18.dp)
            )
        }

        // === 计时器 ===
        // 圆润数字（Baloo 2）+ 自动缩放字号保证单行，下方一句大白饭计数
        // 大白饭数取本次专注已获得的份数（每满 30 分钟 +1）。
        // 不能用 totalFeedCount —— 那是投喂次数，专注时不会变。
        FocusTimer(
            elapsedMillis = uiState.elapsedMillis,
            fishName = uiState.userFish?.name ?: "大肥鱼",
            foodCount = uiState.rewardsEarned,
            travelDestination = uiState.travelDestination,
            travelInProgress = uiState.travelInProgress,
            modifier = Modifier
                .statusBarsPadding()
                .padding(top = 56.dp)
                .align(Alignment.TopCenter)
        )

        // === 大肥鱼的碎碎念（本地台词，零 token 成本）===
        // 借鉴 dsh-whale-musume 的做法：日常陪伴的话不用 LLM 说，
        // 本地随机取一句就行 —— 省 token、省延迟，还更像"随时都在"。
        AnimatedVisibility(
            visible = uiState.companionLine != null,
            enter = fadeIn(tween(400)) + scaleIn(initialScale = 0.9f, animationSpec = tween(400)),
            exit = fadeOut(tween(600)),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 40.dp, vertical = 72.dp)
        ) {
            Surface(
                color = Color.White.copy(alpha = 0.14f),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, Color.White.copy(alpha = 0.24f)
                )
            ) {
                Text(
                    text = uiState.companionLine.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.94f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                )
            }
        }

        // === 奖励动画覆盖层（底部横幅）===
        // 放在底部而不是顶部：顶部被左上角返回按钮和计时器占据，
        // 横幅滑入会盖住返回按钮，导致按钮在该几秒内点不到。
        // 底部是 Toast/Snackbar 的惯例位置，也不会遮挡画面主体。
        AnimatedVisibility(
            visible = uiState.showRewardAnimation,
            enter = fadeIn(tween(300)) + scaleIn(initialScale = 0.8f, animationSpec = tween(400)),
            exit = fadeOut(tween(600)) + scaleOut(targetScale = 0.8f, animationSpec = tween(600)),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            RewardBannerOverlay(
                foodName = uiState.lastReward?.displayName ?: "大白饭"
            )
        }
    }

    // === 退出确认对话框 ===
    if (uiState.showExitConfirm) {
        ExitConfirmDialog(
            millisToNextReward = uiState.millisToNextReward,
            rewardsEarned = uiState.rewardsEarned,
            onConfirm = { viewModel.confirmExit() },
            onDismiss = { viewModel.cancelExit() }
        )
    }

    // === 退出专注后才处理旅行日记 ===
    // 专注中日记只写进游历本，不弹窗、不展开，避免把正在专注的人拽去看内容。
    // 退出时先给「点击查收」，点了才展开正文。
    val pendingJournal = uiState.finishedJournal
    if (hasRun && !uiState.isRunning) {
        if (pendingJournal != null) {
            if (showJournalContent) {
                TravelJournalDialog(
                    journal = pendingJournal,
                    confirmLabel = "收好，去休息",
                    onDismiss = {
                        viewModel.dismissJournal()
                        exitHandled = true
                        onExit()
                    }
                )
            } else {
                TravelJournalReadyDialog(
                    onClaim = { showJournalContent = true },
                    onLater = {
                        viewModel.dismissJournal()
                        exitHandled = true
                        onExit()
                    }
                )
            }
        } else if (!exitHandled) {
            // 这次没生成日记（没满 15 分钟 / 生成失败），直接回首页
            LaunchedEffect(Unit) {
                exitHandled = true
                onExit()
            }
        }
    }
}

/**
 * 「旅行日记已送达」入口
 *
 * 不直接展开正文 —— 先问一句要不要看。
 */
@Composable
private fun TravelJournalReadyDialog(
    onClaim: () -> Unit,
    onLater: () -> Unit
) {
    GlassDialog(
        onDismissRequest = onLater,
        title = "她回来啦",
        actions = {
            GlassDialogActions(
                dismissLabel = "等会儿看",
                onDismiss = onLater,
                confirmLabel = "点击查收",
                onConfirm = onClaim
            )
        }
    ) {
        Text(
            text = "趁你专注，她出门转了一圈，写下一篇游历日记。要先看看，还是先去休息？",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 旅行日记卡片
 *
 * 她趁你专注的时候出了趟门，回来把这件事写成了日记。
 * 只展示，不打断计时 —— 关掉就继续专注。
 */
@Composable
private fun TravelJournalDialog(
    journal: com.deepfocus.app.data.local.entity.TravelJournalEntity,
    confirmLabel: String = "收进游历本",
    onDismiss: () -> Unit
) {
    GlassDialog(
        onDismissRequest = onDismiss,
        title = journal.title,
        actions = {
            GlassDialogActions(
                confirmLabel = confirmLabel,
                onConfirm = onDismiss
            )
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "去了 ${journal.destination} · 心情 ${journal.mood}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = journal.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * 专注计时器
 *
 * ── 设计意图 ────────────────────────────────────────────────
 *  - **不要进度圈**：外圈圆环会被误读成 24 小时时钟表盘，
 *    且在明亮天空上留下一圈生硬描边。
 *  - **不要动画进度条**：跑动的大肥鱼动画与整体风格不搭，
 *    已整体移除。进度信息改由下方一句文案承载。
 *  - **圆润数字**：使用 Baloo 2 圆体（`TimerTextStyle`），
 *    字符本身有柔和的圆弧收笔，比系统黑体更有"可爱"感。
 *    该字体**只作用于计时数字**，不进入全局 Typography。
 *  - **绝不分行**：时长超过 1 小时后格式从 `MM:SS` 变成 `HH:MM:SS`，
 *    字符数从 5 变 8，固定字号会把数字挤到两行。用
 *    `BoxWithConstraints` + 文本测量，按可用宽度自动缩放字号，
 *    保证任何时长都是单行。
 *  - **不抖动的秒数**：字体自带 tnum（tabular figures），
 *    每个数字占位相同，跳秒时整串宽度不变。
 */
@Composable
private fun FocusTimer(
    elapsedMillis: Long,
    fishName: String,
    foodCount: Int,
    travelDestination: String? = null,
    travelInProgress: Boolean = false,
    modifier: Modifier = Modifier
) {
    val timeText = formatElapsedTime(elapsedMillis)

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        val baseStyle = timerTextStyle().copy(
            color = Color.White,
            shadow = Shadow(
                color = Color(0xFF0E2036).copy(alpha = 0.52f),
                offset = Offset(0f, 2f),
                blurRadius = 10f
            )
        )
        // 量出当前字符串在基准字号下的宽度，再按可用宽度反推能单行放下的字号
        val textMeasurer = rememberTextMeasurer()
        val estimatedWidthPx = remember(timeText) {
            textMeasurer.measure(
                text = timeText,
                style = baseStyle,
                constraints = Constraints()
            ).size.width
        }
        val availableWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val fittedFontSize = remember(estimatedWidthPx, availableWidthPx) {
            if (estimatedWidthPx <= 0f || availableWidthPx <= 0f) {
                baseStyle.fontSize
            } else {
                // 留 12% 余量，避免数字贴边
                val ratio = (availableWidthPx * 0.88f) / estimatedWidthPx
                (baseStyle.fontSize.value * ratio).coerceIn(26f, baseStyle.fontSize.value).sp
            }
        }

        // 计时数字下方的空白（来自 Baloo 2 巨大的下伸部）要靠负偏移抵消，
        // 偏移量与计时字号成正比，并按 px 计算，这样跟随系统字体缩放。
        val timerFontPx = with(LocalDensity.current) { fittedFontSize.toPx() }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Text(
                text = timeText,
                style = baseStyle.copy(
                    fontSize = fittedFontSize,
                    // 行高贴近字号：多余的 leading 会在数字下方留白，
                    // 让下方那行大白饭计数显得离计时数字很远，这里压缩掉。
                    lineHeight = fittedFontSize * 1.05f,
                    letterSpacing = (fittedFontSize.value * 0.02f).sp
                ),
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center
            )

            // 大白饭计数：站酷快乐体 + 浅海蓝，一句话说明这只大肥鱼攒了多少大白饭
            // 紧贴计时数字下方：Baloo 2 的下伸部（descent 0.524em）会在数字下方
            // 留出一大片空白，所以这里用与计时字号成正比的负偏移把两行真正贴在一起。
            Text(
                text = buildCuteRewardText(fishName, foodCount, themedWeight(FontWeight.Bold)),
                style = CuteRewardTextStyle.copy(
                    color = PrimarySoft,
                    lineHeight = 22.sp,
                    shadow = Shadow(
                        color = Color(0xFF0E2036).copy(alpha = 0.44f),
                        offset = Offset(0f, 2f),
                        blurRadius = 8f
                    )
                ),
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.offset {
                    IntOffset(0, -(timerFontPx * 0.32f).roundToInt())
                }
            )

            // 赛博旅游状态：她趁你专注的时候出门了，这里告诉你她到哪了
            if (travelInProgress) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "大肥鱼出门旅行去了…",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.88f),
                        shadow = Shadow(
                            color = Color(0xFF0E2036).copy(alpha = 0.46f),
                            offset = Offset(0f, 1f),
                            blurRadius = 6f
                        )
                    ),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            } else if (!travelDestination.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "大肥鱼去了$travelDestination，回来给你看日记",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.88f),
                        shadow = Shadow(
                            color = Color(0xFF0E2036).copy(alpha = 0.46f),
                            offset = Offset(0f, 1f),
                            blurRadius = 6f
                        )
                    ),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}
/**
 * 构造「XX已获得 N 份大白饭」文案
 *
 * 按字符集分派字体：
 *  - 汉字 → 站酷快乐体（手绘活泼感）
 *  - 数字与拉丁字母 → Baloo 2 圆体（圆润，tnum 等宽）
 * 两者都是圆润手绘风，混排不突兀。
 *
 * 数字两侧额外加宽间距 + 略微放大字号，让"份数"从整句里独立出来、
 * 更容易被一眼读到。
 */
private fun buildCuteRewardText(
    fishName: String,
    foodCount: Int,
    boldWeight: FontWeight
): AnnotatedString =
    buildAnnotatedString {
        appendCute(fishName, boldWeight)
        appendCute("已获得", boldWeight)
        // 数字两侧各留两个空格，让份数从整句里独立出来
        appendCute("  ", boldWeight)
        withStyle(
            SpanStyle(
                fontFamily = TimerNumberFont,
                fontWeight = boldWeight,
                fontSize = 21.sp,
                // 等宽数字（规范 6）：份数跳动时整串宽度不变，不抖
                fontFeatureSettings = "tnum"
            )
        ) {
            append(foodCount.toString())
        }
        appendCute("  ", boldWeight)
        appendCute("份大白饭", boldWeight)
    }

/** 按字符集分段追加：汉字走 ZCOOL，其余走 Baloo 2 */
private fun AnnotatedString.Builder.appendCute(text: String, boldWeight: FontWeight) {
    var buffer = StringBuilder()
    var bufferIsCjk: Boolean? = null
    fun flush() {
        val s = buffer.toString()
        if (s.isNotEmpty()) {
            withStyle(
                SpanStyle(
                    fontFamily = if (bufferIsCjk == true) CuteChineseFont else TimerNumberFont,
                    fontWeight = if (bufferIsCjk == true) FontWeight.Normal else boldWeight
                )
            ) {
                append(s)
            }
        }
        buffer = StringBuilder()
    }
    text.forEach { ch ->
        val isCjk = ch.code in 0x4E00..0x9FFF || ch.code in 0x3000..0x303F || ch.code in 0xFF00..0xFFEF
        if (bufferIsCjk != null && bufferIsCjk != isCjk) flush()
        bufferIsCjk = isCjk
        buffer.append(ch)
    }
    flush()
}

/**
 * Apple 风格奖励横幅通知
 * 底部浮出的细长横幅，带脉冲动画
 */
@Composable
private fun RewardBannerOverlay(foodName: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "reward_pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // 玻璃底 + 蜜桃描边：在视频上不跳，同时保持"奖励"的暖色存在感。
    // 实心主色块会在明亮的书房画面上撕出一个生硬的洞。
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF14263A).copy(alpha = 0.78f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        ),
        shadowElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .scale(pulse)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.EmojiEvents,
                contentDescription = null,
                tint = com.deepfocus.app.ui.theme.StarGold,
                // 图标下移 0.125em 与文案基线对齐（规范 5）
                modifier = Modifier
                    .size(22.dp)
                    .offset(y = iconBaselineOffset(MaterialTheme.typography.labelLarge))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "为大肥鱼赢得 $foodName x1",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = themedWeight(FontWeight.SemiBold),
                color = Color.White.copy(alpha = 0.96f)
            )
        }
    }
}

/**
 * 退出确认对话框
 */
@Composable
private fun ExitConfirmDialog(
    millisToNextReward: Long,
    rewardsEarned: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val intervalMs = FocusUiState.REWARD_INTERVAL_MS
    val minutesLeft = (millisToNextReward / 60000).toInt()

    GlassDialog(
        onDismissRequest = onDismiss,
        title = "确定要退出专注吗？",
        actions = {
            GlassDialogActions(
                dismissLabel = "退出",
                confirmLabel = "继续专注",
                onDismiss = onConfirm,
                onConfirm = onDismiss
            )
        }
    ) {
        val msg = if (millisToNextReward >= intervalMs) {
            "本次专注已为大肥鱼赢得 $rewardsEarned 份大白饭，确定退出吗？"
        } else if (minutesLeft > 0) {
            "还有 $minutesLeft 分钟就可以为大肥鱼获得下一份大白饭奖励，确定要退出吗？"
        } else {
            "马上就能为大肥鱼获得下一份大白饭奖励，再坚持一下！确定要退出吗？"
        }
        Text(
            text = msg,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 格式化已专注时长为 HH:MM:SS
 *
 * millis 可能为负（系统时间被往回调），此时钳制为 0，
 * 否则会显示成 "-2:-37" 这种负数时间。
 */
private fun formatElapsedTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
