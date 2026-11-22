package com.deepfocus.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontWeight

/**
 * 当前是否处于深色主题
 *
 * 由 [DeepFocusTheme] 提供，供需要"按主题微调"的组件读取。
 * 深色主题下所有字重需要降一档（规范 3），但 Typography 里
 * 已经处理好了样式级字重；这里的硬编码字重（如 `FontWeight.SemiBold`）
 * 仍需在深色下手动降档，所以要能读到主题状态。
 */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

/**
 * 字重降一档
 *
 * Bold 700 → SemiBold 600，SemiBold 600 → Medium 500，
 * Medium 500 → Normal 400，Normal 保持不变。
 *
 * 说明：规范原文写的是「降 40」，实际落地为「降一档」——
 * CSS 字重刻度（100~900）上降一档约等于 100 单位，
 * 在深色背景上视觉重量降得够、又不至于把标题压成正文。
 */
internal fun FontWeight.demoted(): FontWeight = when (this) {
    FontWeight.Bold -> FontWeight.SemiBold
    FontWeight.SemiBold -> FontWeight.Medium
    FontWeight.Medium -> FontWeight.Normal
    else -> this
}

/**
 * 按当前主题调整字重（高定规范 3：暗色字重全降 40）
 *
 * 用法：把写死的 `FontWeight.SemiBold` 换成 `themedWeight(FontWeight.SemiBold)`，
 * 浅色下保持原样，深色下自动降一档。
 *
 * @param weight 浅色主题下期望的字重
 */
@Composable
@ReadOnlyComposable
fun themedWeight(weight: FontWeight): FontWeight =
    if (LocalIsDarkTheme.current) weight.demoted() else weight
