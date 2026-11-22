package com.deepfocus.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.deepfocus.app.ui.theme.CuteTextFont
import com.deepfocus.app.ui.theme.GlassCardDark
import com.deepfocus.app.ui.theme.GlassCardLight
import com.deepfocus.app.ui.theme.GlassStrokeDark
import com.deepfocus.app.ui.theme.GlassStrokeLight
import com.deepfocus.app.ui.theme.HaloDark
import com.deepfocus.app.ui.theme.HaloDarkEdge
import com.deepfocus.app.ui.theme.HaloLight
import com.deepfocus.app.ui.theme.HaloLightEdge
import com.deepfocus.app.ui.theme.LocalIsDarkTheme

/**
 * 氛围舞台 —— 角色背后的柔光
 *
 * ── 为什么需要 ──────────────────────────────────────────────
 * 纯白画布上的角色是「贴」在纸上的：没有前后关系，
 * 看起来像一张截图而不像一个站在你面前的人。
 * 一圈极淡的径向光晕给出「空间」——
 * 角色从背景里浮出来，这就是"高级感"最省力的一步。
 *
 * 光晕用主色 7% 的极低透明度做中心、向外衰减到全透明，
 * 既不脏、也不抢戏，只是给角色一个"位置"。
 *
 * @param modifier 布局修饰符
 * @param diameter 光晕直径（通常大于角色本身，让边缘柔化）
 * @param breathe 是否做呼吸动画（有生命感的暗示）
 */
@Composable
fun AmbientHalo(
    modifier: Modifier = Modifier,
    diameter: Dp = 280.dp,
    breathe: Boolean = true
) {
    val dark = LocalIsDarkTheme.current
    val center = if (dark) HaloDark else HaloLight
    val edge = if (dark) HaloDarkEdge else HaloLightEdge

    val scale by if (breathe) {
        val transition = rememberInfiniteTransition(label = "halo")
        transition.animateFloat(
            initialValue = 0.94f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(
                animation = tween(4200, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "halo_scale"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    }

    Box(
        modifier = modifier
            .drawBehind {
                val radius = size.minDimension / 2f * scale
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(center, edge),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = radius
                    ),
                    radius = radius,
                    center = Offset(size.width / 2f, size.height / 2f)
                )
            }
    )
}

/**
 * 毛玻璃卡片 —— 叠在图片/动画/视频上的半透明容器
 *
 * 与 [AppCard] 的区别：AppCard 是不透明的实体卡（用于页面区块），
 * 这个是**浮层**容器 —— 文字压在大肥鱼或专注视频上时，
 * 靠半透明填充保证可读性，而不是硬邦邦的实色块。
 *
 * **不描边**（2026-11-17 用户要求：「图中红框中的文字不需要边框，去掉边框」）。
 * 描边用主色低透明度而不是黑框（规范 1），但这里干脆连描边都不要 ——
 * 半透明填充本身就够托住文字，加一圈线反而像「输入框」。
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    content: @Composable () -> Unit
) {
    val dark = LocalIsDarkTheme.current
    Box(
        modifier = modifier
            .background(
                color = if (dark) GlassCardDark else GlassCardLight,
                shape = RoundedCornerShape(cornerRadius)
            )
    ) {
        content()
    }
}

/**
 * 状态胶囊 —— 一枚小圆角标签
 *
 * 用途：把「她现在什么状态」这种**语义**说清楚，
 * 而不是甩一个用户看不懂也改不了的内部数值。
 *
 * **不描边**（2026-11-17 用户要求：「图中红框中的文字不需要边框，去掉边框」）——
 * 只留一层低透明度的填充色块，字用可爱字体（[CuteTextFont]）。
 *
 * @param text 胶囊文字
 * @param modifier 布局修饰符
 * @param accent 强调色（默认主色）
 */
@Composable
fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    Box(
        modifier = modifier
            .background(
                color = accent.copy(alpha = 0.12f),
                shape = RoundedCornerShape(50)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            fontFamily = CuteTextFont
        )
    }
}

/**
 * 分区小标题 —— 带一根细竖线的轻量标题
 *
 * 比 [SectionHeader]（设置页用的）更安静：
 * 首页这种"陪伴展示"页面不该有强烈的分区感，
 * 一根 2dp 的竖线就够把内容分组。
 */
@Composable
fun InlineSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(14.dp)
                .background(accent, RoundedCornerShape(1.dp))
        )
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

/**
 * 空状态 —— 让"还没有内容"变成一个邀请，而不是一片虚无
 *
 * 旧版的空列表只有两行灰字飘在屏幕中间，看起来像页面加载失败。
 * 空状态该做三件事：
 * 1. 告诉用户这里将来会有什么（图标 + 标题）
 * 2. 说清怎么让它发生（副标题）
 * 3. 给一个能立刻做的动作（按钮）
 *
 * @param icon 状态图标
 * @param title 一句话说清这里将来有什么
 * @param description 怎么做
 * @param actionLabel 主行动文案（null 则不显示按钮）
 * @param onAction 主行动回调
 */
@Composable
fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 圆形浅底 + 图标：给空状态一个"落点"，视觉上不悬空
        Box(
            modifier = Modifier.size(72.dp),
            contentAlignment = Alignment.Center
        ) {
            AmbientHalo(
                modifier = Modifier.size(72.dp),
                diameter = 72.dp,
                breathe = false
            )
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(24.dp))
            PrimaryButton(
                text = actionLabel,
                onClick = onAction,
                height = 48.dp,
                modifier = Modifier.widthIn(min = 160.dp)
            )
        }
    }
}

/**
 * 二级页统一顶栏
 *
 * 五个设置子页（API 配置 / 人格卡 / 游历本 / 记忆 / 桌宠）原来是各写一份
 * Scaffold(topBar = { Row { IconButton(ArrowBack) + Text } })，
 * 五份长得一模一样，又都没做状态栏避让 —— 返回按钮会顶到状态栏里。
 *
 * 统一到这一个组件：标题用 headlineSmall（大字，与主页面的标题层级一致），
 * 返回按钮在左，右侧留一个 actions 槽位。
 *
 * @param title 页面标题
 * @param onBack 返回回调
 * @param modifier 布局修饰符
 * @param actions 右侧操作区（可选）
 */
@Composable
fun SubPageTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {}
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.IconButton(onClick = onBack) {
            androidx.compose.material3.Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp)
        )
        actions()
    }
}
