package com.deepfocus.app.ui.components

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 流式打字机文本（高定规范 4）
 *
 * ── 设计规格（严格执行）─────────────────────────────────────
 * - **blur(6px) 渐显**：新字符先以模糊态亮相，随进度推进逐渐变清晰，
 *   像"字从水里浮出来"，而不是硬切入。
 * - **120ms 单字时长**：一个字符从完全透明到完全清晰用 120ms。
 * - **字间错开 30ms**：相邻字符的启动时刻至少间隔 30ms，
 *   形成逐字点亮的节奏感，而不是整段一起淡入。
 *
 * ── 实现方式 ────────────────────────────────────────────────
 * 单个 `Text` 内用 `AnnotatedString` 的 `SpanStyle` 逐字控制透明度，
 * 保证换行 / 软断行仍由 Compose 文本引擎负责（拆成多个 Text 会破坏排版）。
 * 模糊效果用上下两层叠加：
 *  - **锐利层**：字符透明度 = 渐显进度 p
 *  - **柔光层**：字符透明度 = 4p(1-p)（只在过渡中段最亮），整层带 6px 模糊
 * 两层文本与样式完全相同，字形位置天然对齐；
 * 柔光层里只有"刚到的字符"可见，所以模糊只作用在它们身上。
 *
 * 注：`Modifier.blur` 底层走 `RenderEffect`（Android 12+），
 * Android 12 以下不画柔光层，只靠透明度渐显 —— 避免两层同位叠画产生重影。
 *
 * @param text 当前已到达的文本（流式场景下会不断变长）
 * @param modifier 布局修饰符
 * @param style 文字样式
 * @param color 文字颜色，Unspecified 时取 style / 主题色
 * @param charDurationMillis 单字渐显时长
 * @param charStaggerMillis 相邻字符启动间隔
 * @param blurRadius 柔光层模糊半径
 */
@Composable
fun TypewriterText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    charDurationMillis: Int = 120,
    charStaggerMillis: Int = 30,
    blurRadius: Dp = 6.dp
) {
    val resolvedColor = when {
        color != Color.Unspecified -> color
        style.color != Color.Unspecified -> style.color
        else -> MaterialTheme.colorScheme.onBackground
    }

    val staggerNanos = charStaggerMillis * 1_000_000L
    val durationNanos = charDurationMillis * 1_000_000L

    // 每个字符的"启动时刻"。新字符到达时追加，
    // 且与前一个字符至少间隔 30ms（快速到达时自然排队，不会糊成一片）。
    val charStarts = remember { mutableStateListOf<Long>() }
    if (charStarts.size < text.length) {
        val now = System.nanoTime()
        val base = maxOf(now, (charStarts.lastOrNull() ?: now) + staggerNanos)
        repeat(text.length - charStarts.size) { offset ->
            charStarts.add(base + offset * staggerNanos)
        }
    }

    // 逐帧推进时钟，直到所有字符都完成渐显
    var nowNanos by remember { mutableLongStateOf(System.nanoTime()) }
    LaunchedEffect(text.length) {
        while (true) {
            withFrameNanos { }
            val current = System.nanoTime()
            nowNanos = current
            val lastStart = charStarts.lastOrNull() ?: break
            if (current >= lastStart + durationNanos) break
        }
    }

    Box(modifier = modifier) {
        // 柔光层：只在字符过渡中段可见，整层 6px 模糊
        //
        // 只有 Android 12+ 才画这一层 —— `Modifier.blur` 依赖 RenderEffect，
        // API 31 以下是静默 no-op，模糊不生效但两层文字仍在同一位置叠画，
        // 结果是字形描边加重的重影（"糊"）。低版本只留锐利层，靠透明度渐显。
        if (supportsRenderEffectBlur) {
            Text(
                text = buildTypewriterString(text, resolvedColor, charStarts, nowNanos, durationNanos) { p ->
                    4f * p * (1f - p)
                },
                style = style,
                modifier = Modifier.blur(blurRadius)
            )
        }
        // 锐利层：真正承载内容
        Text(
            text = buildTypewriterString(text, resolvedColor, charStarts, nowNanos, durationNanos) { p -> p },
            style = style
        )
    }
}

/** `Modifier.blur` 是否真的能模糊（RenderEffect 需要 Android 12 / API 31） */
private val supportsRenderEffectBlur: Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * 构造逐字透明的 AnnotatedString
 *
 * @param alphaOf 由渐显进度推导出的该字符透明度
 */
private fun buildTypewriterString(
    text: String,
    color: Color,
    charStarts: List<Long>,
    nowNanos: Long,
    durationNanos: Long,
    alphaOf: (Float) -> Float
): AnnotatedString = buildAnnotatedString {
    text.forEachIndexed { index, ch ->
        val start = charStarts.getOrNull(index) ?: Long.MAX_VALUE
        val progress = if (nowNanos <= start) {
            0f
        } else {
            ((nowNanos - start).toFloat() / durationNanos).coerceIn(0f, 1f)
        }
        val alpha = alphaOf(progress).coerceIn(0f, 1f)
        if (alpha <= 0f) {
            append(ch)
        } else {
            withStyle(SpanStyle(color = color.copy(alpha = alpha))) {
                append(ch)
            }
        }
    }
}
