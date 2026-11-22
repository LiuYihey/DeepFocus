package com.deepfocus.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepfocus.app.ui.theme.DeepFocusMotion
import kotlin.math.roundToInt

/**
 * 高定输入框（Soft Text Field）
 *
 * ── 设计理念（严格执行）─────────────────────────────────────
 * 1. **聚焦禁描边**：聚焦时不画任何 border/underline，
 *    改用「加深双环阴影」表达焦点 —— 外环柔光 + 内环收紧，
 *    像光从输入框内部透出来，而不是像传统表单那样"画个框告诉你"。
 * 2. **未聚焦态**仅用 4 层背景体系中的浅色底 + 极淡描边（暖色，非黑）。
 * 3. **标签上浮**：聚焦时 label 缩小并上移到框外，
 *    这是 Material You 的经典手法，比"框内顶部小字"更透气。
 * 4. **无黑框**：所有描边使用暖色调（outlineVariant），绝不使用纯黑。
 *
 * @param value 输入值
 * @param onValueChange 值变化回调
 * @param label 标签文案；传 null 则不显示标签（聊天输入框这类自带占位符的场景）
 * @param modifier 布局修饰符
 * @param placeholder 占位符（聚焦且有值时才显示）
 * @param minLines 最小行数
 * @param maxLines 最大行数（多行输入时生效）
 * @param singleLine 是否单行
 * @param keyboardOptions 键盘配置
 * @param visualTransformation 视觉转换
 * @param trailingIcon 尾部图标
 * @param leadingIcon 头部图标（如搜索框的放大镜）
 */
@Composable
fun SoftTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String?,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    minLines: Int = 1,
    maxLines: Int = 6,
    singleLine: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    // 聚焦时背景提亮一档（用 4 层体系里的 overlay 层，不引入新色）
    val bgColor by animateColorAsState(
        targetValue = if (focused) scheme.surfaceContainerHighest else scheme.surfaceVariant,
        animationSpec = tween(200),
        label = "soft_field_bg"
    )

    // 双环阴影：聚焦时外环 + 内环同时出现
    val shadowAlpha by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(220),
        label = "soft_field_shadow"
    )

    // 标签上浮：聚焦或有值时缩小并上移
    val labelElevated = focused || value.isNotEmpty()
    val labelScale by animateFloatAsState(
        targetValue = if (labelElevated) 0.82f else 1f,
        // 零回弹（规范 9）：标签缩放干净到位，不来回弹跳
        animationSpec = DeepFocusMotion.ZeroRebound,
        label = "soft_field_label"
    )
    val labelColor by animateColorAsState(
        targetValue = if (focused) scheme.primary else scheme.onSurfaceVariant,
        animationSpec = tween(200),
        label = "soft_field_label_color"
    )

    Column(modifier = modifier) {
        // 标签（聚焦上浮到框外）
        // label 为 null 时不显示 —— 聊天输入框这类自带占位符的场景不需要二级标签
        if (label != null) {
            Box(
                modifier = Modifier
                    .padding(start = 4.dp, bottom = 6.dp)
                    .graphicsLayer {
                        scaleX = labelScale
                        scaleY = labelScale
                    }
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = labelColor
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(bgColor)
                // 聚焦禁描边：只在未聚焦时给一条极淡的暖色描边
                .then(
                    if (focused) Modifier
                    else Modifier.border(
                        width = 1.dp,
                        color = scheme.outlineVariant,
                        shape = RoundedCornerShape(16.dp)
                    )
                )
                // 聚焦双环阴影（规范 2）：外圈大而淡、内圈小而深
                // 两层都是"光晕"而不是"描边"，所以不画任何 border/outline。
                .drawBehind {
                    if (shadowAlpha > 0f) {
                        val radius = 16.dp.toPx()
                        // 外圈：大而淡的柔光晕，模拟大半径模糊
                        drawRoundRect(
                            color = scheme.primary.copy(alpha = 0.09f * shadowAlpha),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
                            size = size,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6.dp.toPx())
                        )
                        // 内圈：小而深的收紧环，模拟小半径模糊
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                listOf(
                                    scheme.primary.copy(alpha = 0.34f * shadowAlpha),
                                    scheme.primary.copy(alpha = 0.14f * shadowAlpha)
                                )
                            ),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
                            size = size,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                        )
                    }
                }
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(modifier = Modifier.width(10.dp))
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                textStyle = LocalTextStyle.current.merge(
                    MaterialTheme.typography.bodyLarge.copy(
                        color = scheme.onSurface,
                        letterSpacing = 0.2.sp
                    )
                ),
                singleLine = singleLine,
                minLines = minLines,
                maxLines = if (singleLine) 1 else maxLines,
                keyboardOptions = keyboardOptions,
                visualTransformation = visualTransformation,
                cursorBrush = SolidColor(scheme.primary),
                interactionSource = interactionSource,
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty() && placeholder != null && !focused) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                        innerTextField()
                    }
                }
            )
            if (trailingIcon != null) {
                Spacer(modifier = Modifier.width(8.dp))
                trailingIcon()
            }
            }
        }
    }
}

/**
 * 折叠区块（Grid 0fr → 1fr）
 *
 * CSS 里 `grid-template-rows: 0fr → 1fr` 是最优雅的折叠实现：
 * 内容始终存在于布局中，只有"轨道高度"在 0 与 1fr 之间动画，
 * 因此父容器高度与内容高度始终同步，不会出现"先淡出再收起"的错位。
 *
 * Compose 没有 Grid Track 概念，这里用等价手法实现：
 * 内容永远保持测量与布局（用无上限约束量出它的自然高度），
 * 只有外层容器的高度在 0 与自然高度之间动画 ——
 * 效果与 CSS 的 `grid-template-rows: 0fr → 1fr` 完全一致：
 * 父容器高度与内容高度始终同步，不会出现"先淡出再收起"的错位。
 *
 * @param expanded 是否展开
 * @param modifier 布局修饰符
 * @param content 内容
 */
@Composable
fun CollapsibleSection(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val progress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(durationMillis = 240),
        label = "collapsible_progress"
    )

    Box(
        modifier = modifier
            // 高度按 0→1 比例插值，撑开时裁掉多余部分
            .clipToBounds()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(
                    constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
                )
                val height = (placeable.height * progress).roundToInt()
                layout(placeable.width, height) {
                    placeable.place(0, 0)
                }
            }
    ) {
        content()
    }
}
