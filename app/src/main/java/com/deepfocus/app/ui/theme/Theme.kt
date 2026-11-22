package com.deepfocus.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 浅色主题配色方案
 *
 * 设计语言：Deep Sea · 深海蓝 & 奶白（DeepFocus 专属）
 * - 纯白 canvas 与 AI 生成的纯白底大肥鱼图无缝融合，让大肥鱼像"住"在 app 里
 * - 4 层微明度背景分层（canvas → surface → raised → overlay），每层 1-3% 明度差
 * - 深海蓝主色（#2F5D8C），冷静可靠，呼应大肥鱼的深蓝发色与鱼尾
 * - 蜜桃强调色（#E8A87C）作为整屏唯一暖色点，用于小面积高光
 * - 错误色为柔暖珊瑚，提示但不"吓人"（用户偏好非红）
 */
private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE7F2), // 淡海蓝
    onPrimaryContainer = Color(0xFF1B3D60), // 深海墨
    secondary = Accent,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = SurfaceVariant, // L2 奶白
    onSecondaryContainer = Color(0xFF2C3E50), // 深海蓝墨
    tertiary = RarityRare,
    onTertiary = Color(0xFFFFFFFF),
    background = Background,
    onBackground = TextPrimary,
    surface = Surface, // L1
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariant, // L2
    onSurfaceVariant = TextSecondary,
    surfaceContainerHighest = SurfaceOverlay, // L3
    error = Error,
    onError = Color(0xFFFFFFFF),
    // 错误容器用柔珊瑚淡色，避免回落 M3 默认粉红/红色（用户偏好非红）
    errorContainer = Color(0xFFFCEAE2),
    onErrorContainer = Color(0xFF9C4526),
    // 描边用冷灰蓝且明度克制（禁黑框）
    outline = Color(0xFFD3DCE5),
    outlineVariant = Color(0xFFE4EAF0)
)

/**
 * 深色主题配色方案
 *
 * 延续 Deep Sea 设计语言：
 * - 深背景采用深海蓝墨（非 #000、非纯灰），逐层提亮 1-3%
 * - 主色提亮为雾蓝，保持品牌一致性且在深底上有足够对比
 * - 文本与表面分层通过冷色明度差实现，与浅色主题对称
 */
private val DarkColorScheme = darkColorScheme(
    primary = PrimaryLight,
    onPrimary = Color(0xFF0F2436),
    primaryContainer = Color(0xFF2A4C6E),
    onPrimaryContainer = Color(0xFFD6E6F4),
    secondary = Accent,
    onSecondary = Color(0xFF3A1E0E),
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = Color(0xFFE8EFF5),
    tertiary = RarityRare,
    onTertiary = Color(0xFF12232F),
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    surfaceContainerHighest = DarkSurfaceOverlay,
    error = Error,
    onError = Color(0xFF3A1E0E),
    errorContainer = Color(0xFF5C3524),
    onErrorContainer = Color(0xFFF6DCCF),
    outline = Color(0xFF4A5A6C),
    outlineVariant = Color(0xFF3A4856)
)

/**
 * 应用形状系统 —— 大圆角 + 柔和
 *
 * 圆润是"可爱感"的几何基础：圆角越大，亲和力越强。
 * 底部导航用 Capsule（胶囊），卡片用 20-24dp，弹窗用 28dp。
 */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * 液态玻璃配色 —— 随主题切换的高光参数
 *
 * 苹果液态玻璃的视觉本质是"边缘折射提亮 + 中心厚度加深"，
 * 所以高光颜色必须随明暗主题切换，否则深色下会变成刺眼白边。
 */
data class GlassColors(
    val highlightTop: Color,
    val highlightBottom: Color,
    val innerShadow: Color,
    val tint: Color,
    val indicatorActive: Color,
    val indicatorInactive: Color
)

private val LightGlassColors = GlassColors(
    highlightTop = GlassHighlightTop,
    highlightBottom = GlassHighlightBottom,
    innerShadow = GlassInnerShadow,
    tint = GlassTint,
    indicatorActive = Primary,
    indicatorInactive = Color(0xFF6B7A8B)
)

private val DarkGlassColors = GlassColors(
    highlightTop = GlassHighlightTopDark,
    highlightBottom = GlassHighlightBottomDark,
    innerShadow = Color(0x1A0E1620),
    tint = Color(0x1A5B87B5),
    indicatorActive = PrimaryLight,
    indicatorInactive = DarkTextSecondary
)

val LocalGlassColors = staticCompositionLocalOf { LightGlassColors }

/**
 * 应用主题 Composable
 *
 * DeepFocus 统一使用 Deep Sea 主题：
 * - 浅色模式：纯白 canvas 与 AI 生成的纯白底大肥鱼图无缝融合
 * - 深色模式：跟随系统设置，深海蓝墨 4 层背景，夜间使用不冷峻
 * - 系统字体 + Baloo 2 圆润数字字体，可爱但不幼稚
 *
 * @param darkTheme 是否使用深色主题，默认跟随系统
 * @param content 子内容
 */
@Composable
fun DeepFocusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val typography = if (darkTheme) DarkTypography else Typography
    val glassColors = if (darkTheme) DarkGlassColors else LightGlassColors

    CompositionLocalProvider(
        LocalGlassColors provides glassColors,
        LocalIsDarkTheme provides darkTheme
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = AppShapes,
            content = content
        )
    }
}
