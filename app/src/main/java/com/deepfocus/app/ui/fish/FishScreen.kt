package com.deepfocus.app.ui.fish

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepfocus.app.ai.FishStateEngine
import com.deepfocus.app.data.local.entity.FishStateEntity
import com.deepfocus.app.data.local.entity.FoodInventoryEntity
import com.deepfocus.app.util.assetExists
import com.deepfocus.app.ui.components.AmbientHalo
import com.deepfocus.app.ui.components.AppCard
import com.deepfocus.app.ui.components.AppCardTokens
import com.deepfocus.app.ui.components.FishCanvas
import com.deepfocus.app.ui.components.FoodCanvas
import com.deepfocus.app.ui.components.InlineSectionLabel
import com.deepfocus.app.ui.components.PrimaryButton
import com.deepfocus.app.ui.components.StatusPill
import com.deepfocus.app.ui.components.iconBaselineOffset
import com.deepfocus.app.ui.theme.CuteTextFont
import com.deepfocus.app.ui.theme.DeepFocusMotion
import com.deepfocus.app.ui.theme.tabularNumbers
import com.deepfocus.app.ui.theme.themedWeight

/**
 * 首页舞台的素材短边（物理像素）
 *
 * 素材是「短边统一、长边按比例」的近景画布（角色已裁掉空白）。
 * 运行时容器**高度**按这个值反算 dp，保证「容器物理像素 == 素材短边」，
 * 也就是 1:1 显示 —— 零插值，边缘锐利，角色不被裁切。
 *
 * 宽度由 ImageView（adjustViewBounds）按各素材比例自适应，
 * 所以不同动作的角色高度完全一致。
 *
 * 改这个值必须同步改 assets/pet/anim/all/ 下的素材分辨率
 * （由 tools/media/build_hero_assets.py 的 SHORT_EDGE 决定）。
 */
private const val HERO_STAGE_PX = 512

/**
 * 大肥鱼养成界面（默认首页）
 * 展示大肥鱼形象、好感度、食物库存、投喂交互、成就，并提供开始专注入口
 */
@Composable
fun FishScreen(
    onNavigateToFocus: () -> Unit,
    viewModel: FishViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val hapticFeedback = LocalHapticFeedback.current

    // 舞台边长（dp）—— 按素材像素数与当前屏幕密度反算，保证 1:1 显示。
    //
    // 素材是 512x512，运行时只有「容器物理像素 == 512」才不产生任何插值。
    // 固定写 170.dp 只在 3x 屏上成立（170*3=510），在 2.75x / 4x 屏上就会偏。
    // 所以这里按实际 density 换算，跨机型都精确 1:1。
    val density = LocalDensity.current.density
    val stageSize = with(LocalDensity.current) {
        (HERO_STAGE_PX / density).dp
    }

    // 成就解锁 - 全屏庆祝弹窗
    if (state.newlyUnlockedAchievement != null) {
        AchievementCelebrationDialog(
            achievement = state.newlyUnlockedAchievement!!,
            onDismiss = { viewModel.clearAchievementPopup() }
        )
    }

    // 错误提示
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    val accentColor = MaterialTheme.colorScheme.primary
    val cardColor = MaterialTheme.colorScheme.surface
    val textColor = MaterialTheme.colorScheme.onBackground
    val dimColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val userFish = state.userFish
        val breed = state.breed

        if (state.isLoading) {
            // 正在加载大肥鱼数据 - 在 Column 外层显示，确保居中
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = accentColor)
            }
        } else if (userFish == null) {
            // 未选择大肥鱼 - 富空状态：沉睡大肥鱼插画 + 引导文案
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 沉睡大肥鱼矢量插画
                FishCanvas(
                    state = "sleeping",
                    modifier = Modifier.size(160.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "还没有领养大肥鱼",
                    color = textColor,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        } else {
            // 保存滚动状态
            val scrollState = rememberScrollState()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp)
                        .padding(top = 8.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // ── 身份 ──
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = userFish.name,
                            color = textColor,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = themedWeight(FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = viewModel.getAffinityTitle(userFish.affinityLevel),
                                color = dimColor,
                                style = MaterialTheme.typography.bodySmall
                            )
                            state.statusPill?.let { pill ->
                                StatusPill(text = pill, accent = accentColor)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 舞台区高度 = 角色高度 + 一点余量 + 头顶气泡槽。
                    //
                    // 角色钉在舞台底部，气泡槽固定高度在头顶 ——
                    // 换台词（一行/两行）时角色位置绝对稳定，不会上下漂。
                    // 余量只给 12dp：舞台区比角色大一圈就会在角色下面留出一条空白，
                    // 看着像「人物悬在上面，下面空一块」。
                    val stageArea = stageSize + 12.dp
                    val bubbleSlot = 84.dp
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(stageArea + bubbleSlot),
                contentAlignment = Alignment.BottomCenter
            ) {
                // ── 台词气泡：贴在 GIF 正上方 ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(bubbleSlot)
                        .align(Alignment.TopCenter),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    state.idleLine?.let { line ->
                        FishSpeechBubble(
                            text = line,
                            accent = accentColor,
                            modifier = Modifier.fillMaxWidth(0.86f),
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.refreshIdleLine()
                            }
                        )
                    }
                }

                // ── 角色舞台 ──
                // 大肥鱼是这一页的主角，放在视觉中心。
                //
                // 角色本身的高度按素材短边像素反算（stageSize），
                // 保证 1:1 显示：运行时零插值，边缘锐利，角色完整不被裁。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(stageArea),
                    contentAlignment = Alignment.Center
                ) {
                    // 氛围光晕：让大肥鱼从背景里"浮"出来。
                    // 直径要比角色的视觉尺寸更大 —— 光晕太小就藏在角色后面看不见了。
                    AmbientHalo(
                        modifier = Modifier.size(stageSize * 1.18f),
                        diameter = stageSize * 1.18f
                    )
                    // 星星点缀：两粒，克制
                    // 用比例定位（而不是固定 px），容器尺寸变了也不会跑偏
                    val twinkle by rememberInfiniteTransition(label = "twinkle").animateFloat(
                        initialValue = 0.25f,
                        targetValue = 0.85f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2400, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "twinkle_alpha"
                    )
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        listOf(0.16f to 0.18f, 0.74f to 0.66f).forEachIndexed { i, (fx, fy) ->
                            val a = if (i == 0) twinkle else 1f - twinkle
                            drawStar(
                                Offset(size.width * fx, size.height * fy),
                                3.5f,
                                com.deepfocus.app.ui.theme.StarGold.copy(alpha = a * 0.55f)
                            )
                        }
                    }
                    FishAnimationArea(
                        animationPhase = state.animationPhase,
                        accentColor = accentColor,
                        stageSize = stageSize,
                        onFeedingComplete = { viewModel.onFeedingVideoCompleted() }
                    )
                }
            }

                    Spacer(modifier = Modifier.height(20.dp))

                // ── 主行动：开始专注 ──
                // 放在角色正下方，而不是页面最底。
                //
                // 专注是这个 App 的核心功能 —— 首页第一眼就要能看到它、能点到它。
                // 之前放在最底部，用户得滚过档案/投喂/成就才能找到，
                // 核心入口被埋了（用户明确反馈过这点）。
                //
                // 拇指区原则仍然成立：它在屏幕下半部分，手指够得到。
                PrimaryButton(
                    text = "开始专注",
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNavigateToFocus()
                    },
                    enabled = true,
                    containerColor = accentColor,
                    height = 56.dp,
                    textStyle = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(24.dp))

                // ── 陪伴档案 ──
                // 好感度 + 进度合成一张卡，取代旧版的两张卡
                val nextMilestone = viewModel.getNextMilestone(userFish.affinityLevel)
                val isMax = viewModel.isMaxMilestone(userFish.affinityLevel)
                val progress = if (nextMilestone > 0) {
                    (userFish.affinityLevel.toFloat() / nextMilestone.toFloat()).coerceIn(0f, 1f)
                } else 1f

                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                    radius = AppCardTokens.RadiusLarge
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            InlineSectionLabel(text = "陪伴档案")
                            // 称号而不是数值 —— 进度条已经表达了"走到哪了"，
                            // 再甩个数字只是把内部计数器摆到台面上
                            Text(
                                text = viewModel.getAffinityTitle(userFish.affinityLevel),
                                color = accentColor,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            color = accentColor,
                            trackColor = dimColor.copy(alpha = 0.15f),
                            strokeCap = StrokeCap.Round
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (isMax) {
                                "已经离不开你了，累计投喂 ${userFish.totalFeedCount} 次"
                            } else {
                                "多陪陪它，就能换下一个称号"
                            },
                            color = dimColor,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ── 投喂 ──
                InlineSectionLabel(
                    text = "投喂",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))

                if (state.foodInventory.isEmpty()) {
                    // 空库存引导：专注是唯一获得大白饭的方式，说清楚这一点
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(AppCardTokens.RadiusLarge)
                            )
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "专注满 30 分钟，就能带一份大白饭回来",
                            color = dimColor,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    // 食物卡**居中**排 —— 唯一一种食物时，
                    // 靠左排会让它孤零零挂在屏幕左边，看着像没排完。
                    // 卡片宽度固定（112dp），不拉满屏宽。
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        state.foodInventory.forEach { item ->
                            val foodInfo = state.foodCatalog.find { it.foodId == item.foodId }
                            FoodItem(
                                item = item,
                                foodName = foodInfo?.displayName ?: item.foodId,
                                accentColor = accentColor,
                                textColor = textColor,
                                dimColor = dimColor,
                                isFeeding = state.isFeeding,
                                onFeed = { viewModel.feedFish(item.foodId) },
                                modifier = Modifier.width(112.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ── 成就（折叠）──
                // 6 张成就卡片平铺要占掉大半屏 —— 折叠起来，只在需要时展开
                var achievementsExpanded by remember { mutableStateOf(false) }
                val unlockedCount = state.achievements.count { it.unlockedAt != null }
                val totalCount = state.achievements.size

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            achievementsExpanded = !achievementsExpanded
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    InlineSectionLabel(text = "成就")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "$unlockedCount / $totalCount",
                            color = dimColor,
                            style = MaterialTheme.typography.bodySmall.tabularNumbers()
                        )
                        Icon(
                            imageVector = if (achievementsExpanded) {
                                Icons.Filled.ExpandLess
                            } else {
                                Icons.Filled.ExpandMore
                            },
                            contentDescription = if (achievementsExpanded) "收起成就" else "展开成就",
                            tint = dimColor,
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                AnimatedVisibility(
                    visible = achievementsExpanded,
                    enter = fadeIn(animationSpec = tween(220)) +
                        expandVertically(animationSpec = tween(220)),
                    exit = fadeOut(animationSpec = tween(180)) +
                        shrinkVertically(animationSpec = tween(180))
                ) {
                    Column {
                        state.achievements.forEach { achievement ->
                            val isUnlocked = achievement.unlockedAt != null
                            AppCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                containerColor = cardColor,
                                elevation = 0.dp,
                                radius = 14.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isUnlocked) {
                                            Icons.Filled.EmojiEvents
                                        } else {
                                            Icons.Filled.Lock
                                        },
                                        contentDescription = null,
                                        tint = if (isUnlocked) accentColor else dimColor,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .offset(y = iconBaselineOffset(MaterialTheme.typography.titleSmall))
                                    )
                                    Spacer(modifier = Modifier.size(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = achievement.title,
                                            color = if (isUnlocked) accentColor else dimColor,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = achievement.description,
                                            color = if (isUnlocked) textColor else dimColor,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    if (isUnlocked) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            tint = accentColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/**
 * 首页台词气泡
 *
 * 贴在 GIF 头顶，尾巴朝下指着她。点一下换一句。
 *
 * 高度交给外层固定槽位控制，气泡自身按内容伸展 ——
 * 一行/两行切换时，角色在屏幕上的位置不会跟着动。
 */
@Composable
private fun FishSpeechBubble(
    text: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bubbleColor = MaterialTheme.colorScheme.surface
    Surface(
        shape = SpeechBubbleShape(),
        color = bubbleColor,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f)),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            // 可爱字体：台词是她在说话，不是系统提示
            fontFamily = CuteTextFont,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                // 底部多留一个尾巴的高度：那一段不写字，只留给曲线尾巴
                .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 24.dp)
        )
    }
}

/**
 * 气泡轮廓：圆角矩形 + 一条平滑的曲线尾巴
 *
 * 之前的尾巴是单独画的小三角：直线边、没有阴影、边框也不跟着走，
 * 看起来像贴在气泡下面的一个纸片。现在把尾巴并进同一条轮廓，
 * 边框和投影会自然绕着尾巴走，收口处也用贝塞尔曲线过渡。
 */
private class SpeechBubbleShape(
    private val cornerRadiusDp: Float = 20f,
    private val tailWidthDp: Float = 22f,
    private val tailHeightDp: Float = 12f
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val tailWidth = with(density) { tailWidthDp.dp.toPx() }
        val tailHeight = with(density) { tailHeightDp.dp.toPx() }
        val width = size.width
        // 尾巴从「气泡主体」的底边往下伸，主体高度要扣掉尾巴
        val bodyHeight = (size.height - tailHeight).coerceAtLeast(0f)
        val radius = with(density) { cornerRadiusDp.dp.toPx() }
            .coerceAtMost(bodyHeight / 2f)
        val doubleRadius = radius * 2f
        val tailCenterX = width / 2f

        val path = Path().apply {
            moveTo(radius, 0f)
            lineTo(width - radius, 0f)
            arcTo(
                Rect(width - doubleRadius, 0f, width, doubleRadius),
                -90f, 90f, false
            )
            lineTo(width, bodyHeight - radius)
            arcTo(
                Rect(width - doubleRadius, bodyHeight - doubleRadius, width, bodyHeight),
                0f, 90f, false
            )
            // 底边走到尾巴右根，再沿着曲线收进尾尖、从左根回来
            lineTo(tailCenterX + tailWidth / 2f, bodyHeight)
            cubicTo(
                tailCenterX + tailWidth * 0.30f, bodyHeight + tailHeight * 0.45f,
                tailCenterX + tailWidth * 0.22f, bodyHeight + tailHeight,
                tailCenterX, bodyHeight + tailHeight
            )
            cubicTo(
                tailCenterX - tailWidth * 0.22f, bodyHeight + tailHeight,
                tailCenterX - tailWidth * 0.30f, bodyHeight + tailHeight * 0.45f,
                tailCenterX - tailWidth / 2f, bodyHeight
            )
            lineTo(radius, bodyHeight)
            arcTo(
                Rect(0f, bodyHeight - doubleRadius, doubleRadius, bodyHeight),
                90f, 90f, false
            )
            lineTo(0f, radius)
            arcTo(
                Rect(0f, 0f, doubleRadius, doubleRadius),
                180f, 90f, false
            )
            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * 成就解锁全屏庆祝弹窗 - 带粒子效果和弹性动画
 */
@Composable
private fun AchievementCelebrationDialog(
    achievement: com.deepfocus.app.data.local.entity.AffinityAchievementEntity,
    onDismiss: () -> Unit
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onBackground
    val dimColor = MaterialTheme.colorScheme.onSurfaceVariant
    val cardColor = MaterialTheme.colorScheme.surface
    val hapticFeedback = LocalHapticFeedback.current

    // 进场动画
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        kotlinx.coroutines.delay(100)
        visible = true
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            // 成就解锁是用户长期投入的高峰时刻，禁止点击 scrim 误关
            // 仅允许"太棒了！"按钮或返回键关闭，避免手抖错过庆祝
            dismissOnClickOutside = false,
            dismissOnBackPress = true,
            // 用满屏宽：平台默认宽度只有屏幕的 65%~80%，
            // 成就名 + 描述会被挤成两三行短句，读起来支离破碎。
            // 铺满后一行放得下，庆祝信息才是「一句话」。
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // 成就弹窗：不添加任何全屏背景层 / Lottie 粒子（用户硬约束）
            // 仅保留居中卡片浮于 Dialog 默认 scrim 之上，干净克制
            androidx.compose.animation.AnimatedVisibility(
                visible = visible,
                enter = androidx.compose.animation.fadeIn() +
                    androidx.compose.animation.scaleIn(
                        initialScale = 0.5f,
                        // 「点赞」式微弹（规范 9：380/18 → dampingRatio 0.46 / stiffness 380）
                        animationSpec = DeepFocusMotion.MicroBounce
                    )
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 32.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = cardColor
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 28.dp, vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 庆祝图标（矢量 Material Icon，替代 emoji）
                        Icon(
                            imageVector = Icons.Filled.Celebration,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "成就解锁",
                            color = accentColor,
                            style = MaterialTheme.typography.labelLarge,
                            letterSpacing = 2.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = achievement.title,
                            color = textColor,
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        // 描述单独一行，不再把「奖励」拼进同一串里折行
                        Text(
                            text = achievement.description,
                            color = dimColor,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(26.dp))
                        PrimaryButton(
                            text = "太棒了！",
                            onClick = {
                                hapticFeedback.performHapticFeedback(
                                    androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove
                                )
                                onDismiss()
                            },
                            enabled = true, containerColor = accentColor, height = 48.dp
                        )
                    }
                }
            }
        }
    }
}

/**
 * 大肥鱼动画展示区
 * - eating + 视频可用：全屏播放进食视频，不叠加装饰（真实场景已足够丰富）
 * - 其他状态：图片 + 光晕/星星装饰
 * 不显示任何状态文字
 */
@Composable
private fun FishAnimationArea(
    animationPhase: String,
    accentColor: Color,
    stageSize: Dp,
    onFeedingComplete: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // 喂食动作和登场短剧都在 FishCanvas 内部选（那边有统一的重抽逻辑），
    // 这里只需要判断「有没有登场短剧可用」来决定要不要进沉浸模式。
    //
    // 登场短剧：从「有出场感 / 有表现力」的动作里挑，
    // 并且**必须过 StageCatalog 白名单** ——
    // 白名单只保留「角色高度与其他动作一致」的，
    // 横向摊得太开的动作（躺着的、举风筝的）已隔离，
    // 否则上台时会出现「大一下小一下」。
    val appearanceScenes = remember {
        com.deepfocus.app.pet.PetActionLibrary.ALL
            .filter { action ->
                action.tags.any {
                    it == com.deepfocus.app.pet.PetActionTag.ENTER ||
                        it == com.deepfocus.app.pet.PetActionTag.TRICK ||
                        it == com.deepfocus.app.pet.PetActionTag.MOOD ||
                        it == com.deepfocus.app.pet.PetActionTag.PLAY
                }
            }
            .filter { it.id in com.deepfocus.app.pet.StageCatalog.STAGE_ACTIONS }
            .map { it to com.deepfocus.app.pet.PetActionLibrary.assetPath(it) }
            .filter { (_, path) -> context.assetExists(path) }
    }
    // eating 状态 → 纯动画模式，不画装饰。
    //
    // 不用判断「动作抽到了没」—— 动作在 FishCanvas 内部选，
    // 那一帧还没抽到时会先显示静态立绘兜底，
    // 这里如果跟着退化成装饰模式，会有一次明显的模式跳变。
    val isAnimEating = animationPhase == "eating"
    // 任何动画模式（eating 或登场短剧）都隐藏装饰层，避免光晕/星星叠加破坏沉浸感
    val isAnimMode = isAnimEating || (animationPhase == "idle" && appearanceScenes.isNotEmpty())
    val hideDecoration = isAnimMode

    val infiniteTransition = rememberInfiniteTransition(label = "glow")

    // 光晕呼吸效果（仅非视频模式使用）
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_scale"
    )
    val starTwinkle by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "star_twinkle"
    )

    // 容器：素材是「短边 = 512px、长边按比例」的画布（角色已裁掉空白）。
    // 容器**高度**由 stageSize 给出（按密度反算，物理像素正好 512）——
    // 1:1 显示，运行时零插值，边缘不糊。
    // 宽度交给内部的 ImageView（adjustViewBounds）按素材比例自适应。
    //
    // **不加背景色**：底层是氛围光晕，加了就把光晕盖死，
    // 角色会重新"贴"回纸面上（这是之前"贴纸感"的根源）。
    //
    // 旧版这里是 16:9 + scale(1.4f) + clipToBounds()，
    // 等效 2.1 倍运行时双线性插值（糊），而且放大的部分被裁掉（显示不全）。
    // 现在这些全部去掉 —— 放大交给离线转码，运行时只做 1:1。
    Box(
        modifier = if (isAnimMode) {
            Modifier
                .fillMaxWidth()
                .height(stageSize)
        } else {
            Modifier.size(280.dp)
        },
        contentAlignment = Alignment.Center
    ) {
        // 装饰层用 AnimatedVisibility 平滑淡入淡出，避免进食瞬间硬切
        // 视频模式（eating/idle）下隐藏装饰，保持纯白背景沉浸感
        AnimatedVisibility(
            visible = !hideDecoration,
            enter = fadeIn(animationSpec = tween(400)),
            exit = fadeOut(animationSpec = tween(400))
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Neo-minimalism：仅保留 3 颗点缀星星（从 6 颗精简），更克制
                // 位置用比例，换容器尺寸也不会跑偏
                Canvas(modifier = Modifier.size(stageSize)) {
                    listOf(0.18f to 0.2f, 0.8f to 0.62f, 0.42f to 0.86f)
                        .forEachIndexed { index, (fx, fy) ->
                            val alpha = (starTwinkle + index * 0.2f) % 1f
                            drawStar(
                                Offset(size.width * fx, size.height * fy),
                                3.5f,
                                com.deepfocus.app.ui.theme.StarGold.copy(alpha = alpha * 0.5f)
                            )
                        }
                }
                // 外层光晕（呼吸效果）
                Box(
                    modifier = Modifier
                        .size(stageSize * 0.86f * glowScale)
                        .background(color = accentColor.copy(alpha = glowAlpha), shape = CircleShape)
                )
                // 中层光晕
                Box(
                    modifier = Modifier
                        .size(stageSize * 0.95f)
                        .background(color = accentColor.copy(alpha = 0.08f), shape = CircleShape)
                )
            }
        }
        // 动画模式：填满容器；图片模式：固定尺寸 + 渐进过渡
        val targetFishSize = if (isAnimEating) 280.dp else 240.dp
        val catSize by animateDpAsState(
            targetValue = targetFishSize,
            animationSpec = tween(durationMillis = 400),
            label = "cat_size"
        )
        FishCanvas(
            state = animationPhase,
            modifier = if (isAnimMode) {
                // 高度锁死 = 素材短边像素（= stageSize），
                // 宽度按素材比例自适应。
                // 各动作的角色高度因此完全一致 —— 不会大一下小一下。
                Modifier.height(stageSize)
            } else {
                Modifier.size(catSize)
            },
            interactive = !hideDecoration,  // 动画模式禁用触控倾斜
            useVideo = true,
            fitWidth = isAnimMode,
            // 首页固定常服：动作库只有常服一套 GIF，冬装没有配套动画。
            // 用户要求「选冬装后首页 GIF 不用变」—— 装扮只体现在聊天页。
            outfit = com.deepfocus.app.data.FishOutfits.NORMAL,
            onVideoCompletion = if (animationPhase == "eating") onFeedingComplete else null
        )
    }
}

/**
 * 状态条：一条细进度条 + 左侧标签 + 右侧数值
 */
@Composable
private fun StatusBar(
    label: String,
    value: Int,
    display: String,
    color: Color,
    textColor: Color,
    dimColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = dimColor,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(36.dp)
        )
        LinearProgressIndicator(
            progress = { (value / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .weight(1f)
                .height(5.dp),
            color = color,
            trackColor = dimColor.copy(alpha = 0.12f),
            strokeCap = StrokeCap.Round
        )
        Text(
            text = display,
            color = textColor,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .width(52.dp)
                .padding(start = 8.dp),
            textAlign = TextAlign.End
        )
    }
}

/**
 * 是否饿了
 *
 * **只看距上次喂食是否超过 24 小时**，与饱腹度数值无关。
 */
private fun isHungry(state: FishStateEntity): Boolean =
    FishStateEngine.isHungry(state, System.currentTimeMillis())

/**
 * 绘制小星星装饰
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStar(
    center: Offset,
    radius: Float,
    color: Color
) {
    val path = Path().apply {
        for (i in 0..4) {
            val angle = (i * 144 - 90).toDouble() * Math.PI / 180
            val x = center.x + (radius * Math.cos(angle)).toFloat()
            val y = center.y + (radius * Math.sin(angle)).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    drawPath(path = path, color = color)
}

/**
 * 食物卡片 - 带按压反馈
 * 使用奶油白(SurfaceVariant)背景 + 圆角裁剪食物图，避免"套娃"式嵌套框
 *
 * @param modifier 布局修饰符（首页用 weight 均分宽度）
 */
@Composable
private fun FoodItem(
    item: FoodInventoryEntity,
    foodName: String,
    accentColor: Color,
    textColor: Color,
    dimColor: Color,
    isFeeding: Boolean,
    onFeed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    // 按下回弹用「微弹」（规范 9：380/18 → dampingRatio 0.46 / stiffness 380），
    // 轻轻过冲一下就稳住，像被戳了一下
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = DeepFocusMotion.MicroBounce,
        label = "food_press_scale"
    )
    val isEmpty = item.count <= 0
    val hapticFeedback = LocalHapticFeedback.current
    // 奶油白卡片背景，与 App 其他卡片层级一致（SurfaceVariant = #FFF8E7）
    val foodCardColor = MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = modifier
            // 只固定高度，宽度交给调用方（首页用 weight 均分）——
            // size() 会覆盖 weight，两者不能共存
            .height(130.dp)
            .scale(pressScale)
            .clickable(
                enabled = !isFeeding && !isEmpty,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = {
                    hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    onFeed()
                }
            ),
        shape = RoundedCornerShape(AppCardTokens.RadiusLarge),
        colors = CardDefaults.cardColors(
            containerColor = if (isEmpty) foodCardColor.copy(alpha = 0.5f) else foodCardColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = AppCardTokens.ElevationSoft,
            pressedElevation = 0.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 食物图：FoodCanvas 内置径向渐变边缘柔化（与视频窗口一致），无需额外裁剪
            FoodCanvas(
                foodId = item.foodId,
                modifier = Modifier
                    .size(64.dp)
                    .scale(if (isPressed && !isEmpty) 1.15f else 1f),
                size = 64
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = foodName,
                color = if (isEmpty) dimColor else textColor,
                fontSize = 13.sp,
                fontWeight = themedWeight(FontWeight.Medium)
            )
            Text(
                text = "x${item.count}",
                color = if (isEmpty) dimColor else accentColor,
                style = MaterialTheme.typography.titleMedium.tabularNumbers(),
                fontWeight = themedWeight(FontWeight.SemiBold)
            )
        }
    }
}
