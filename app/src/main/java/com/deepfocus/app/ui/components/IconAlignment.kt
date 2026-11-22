package com.deepfocus.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * 图标与文字基线对齐（高定规范 5）
 *
 * ── 设计规格（严格执行）─────────────────────────────────────
 * 图标默认按自身方形居中，与同行文字放在一起时会显得"浮"一点。
 * 规范要求图标**下移 0.125em** 与文字基线对齐 ——
 * 这是排版里图标与文字混排的经典微调：
 * 0.125em ≈ 字号的 12.5%，比 0.1em 更贴合中文小字号，
 * 视觉上图标"坐"在文字中线偏下的位置，与阅读基线咬合。
 *
 * Compose 没有 em 单位，这里按「字号的 12.5%」换算成 dp。
 *
 * @param textSize 与图标同行的文字字号
 * @return 需要加到图标上的垂直偏移量
 */
@Composable
@ReadOnlyComposable
fun iconBaselineOffset(textSize: TextUnit): Dp {
    val density = LocalDensity.current
    return with(density) { (textSize.value * 0.125f).dp }
}

/**
 * 图标与文字基线对齐（取自样式的字号）
 *
 * @param style 与图标同行的文字样式
 */
@Composable
@ReadOnlyComposable
fun iconBaselineOffset(style: TextStyle): Dp = iconBaselineOffset(style.fontSize)
