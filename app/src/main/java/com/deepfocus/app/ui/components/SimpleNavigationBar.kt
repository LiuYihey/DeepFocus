package com.deepfocus.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.deepfocus.app.ui.theme.DeepFocusMotion

/**
 * 底部导航项
 *
 * @param icon 图标。用 [Painter] 而不是 [ImageVector] ——
 *   有些图标是**位图**（DeepSeek 鲸鱼标志是带渐变的位图，
 *   矢量化会把渐变抹平）。矢量图标用 `rememberVectorPainter()` 转。
 * @param tintSelected 选中时的着色。
 * @param tintUnselected 未选中时的着色。
 *   两者都给 [Color.Unspecified] → **保留原色**，
 *   不挂 ColorFilter（品牌图标要保住自己的渐变）。
 */
data class SimpleNavEntry(
    val route: String,
    val label: String,
    val icon: Painter,
    val tintSelected: Color = Color.Unspecified,
    val tintUnselected: Color = Color.Unspecified,
    /** 图标边长。品牌图标比方形图标窄，按比例放大一点视觉重量才配得上。 */
    val iconSize: Dp = 22.dp
)

/**
 * 简洁底部导航栏
 *
 * ── 为什么不用 Material3 的 NavigationBar ─────────────────────
 * M3 的 NavigationBar 会把窗口底部 inset 挤进固定高度里，
 * 结果是图标上方的留白被压没、选中胶囊被裁切（实测截图里"设置"的
 * 胶囊顶部被切掉一角）。这里改为完全自绘：
 *
 *  - 顶部一条 1dp 发丝分隔线，不使用阴影（Apple 风）
 *  - 图标区 30dp 高，选中时用蜜糖色胶囊做指示器
 *  - **图标上方固定 12dp、下方 10dp**，四周留白对称、视觉重心居中
 *  - 标签字号固定 12sp（选中/未选中只换颜色，不换字号），
 *    避免切换 Tab 时文字高度跳动
 *  - 底部系统手势区用 navigationBars inset 补足，背景铺到屏幕最底边
 */
@Composable
fun SimpleNavigationBar(
    items: List<SimpleNavEntry>,
    selectedRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val borderColor = scheme.outlineVariant.copy(alpha = 0.35f)
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = scheme.surface,
        tonalElevation = 0.dp
    ) {
        Column {
            HorizontalDivider(
                thickness = 1.dp,
                color = borderColor
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // 关键：图标上方 12dp 留白，让图标不贴顶
                    .padding(top = 12.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Top
            ) {
                items.forEach { entry ->
                    val selected = entry.route == selectedRoute
                    val iconScale by animateFloatAsState(
                        targetValue = if (selected) 1.06f else 1f,
                        // 零回弹（规范 9）：图标缩放干脆到位，不来回晃
                        animationSpec = DeepFocusMotion.ZeroRebound,
                        label = "nav_icon_scale"
                    )

                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSelect(entry.route) }
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 选中指示器：蜜糖色胶囊，未选中时透明（保留占位，切换不跳动）
                        Box(
                            modifier = Modifier
                                .size(width = 56.dp, height = 30.dp)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (selected) scheme.primaryContainer.copy(alpha = 0.6f)
                                    else Color.Transparent
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = entry.icon,
                                contentDescription = entry.label,
                                // Color.Unspecified = 不加任何 ColorFilter →
                                // 保留品牌图标自己的渐变（DeepSeek 鲸鱼）
                                tint = if (selected) entry.tintSelected else entry.tintUnselected,
                                modifier = Modifier
                                    .size(entry.iconSize)
                                    .scale(iconScale)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = entry.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) scheme.primary else scheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
            // 底部手势区：背景延伸到屏幕最底边，避免出现白边
            Spacer(modifier = Modifier.height(bottomInset))
        }
    }
}
