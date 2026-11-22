package com.deepfocus.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * 复制按钮（高定规范 10）
 *
 * ── 设计规格（严格执行）─────────────────────────────────────
 * - 点击后图标立即切成对勾（Check），而不是弹 Toast ——
 *   Toast 会打断视线，对勾在按钮原地完成"确认"的语义闭环。
 * - **2.5 秒自隐**：对勾停留 2.5s 后自动还原为复制图标，
 *   无需用户再次点击。对勾与原图标之间做一次轻微缩放过渡，
 *   让"状态切换"这件事有存在感但不掉档。
 *
 * @param text 要复制的文本
 * @param modifier 布局修饰符
 * @param contentDescription 无障碍描述
 */
@Composable
fun CopyIconButton(
    text: String,
    modifier: Modifier = Modifier,
    contentDescription: String = "复制"
) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2_500)
            copied = false
        }
    }

    // 对勾 / 复制图标之间的切换：缩放 + 透明度过渡
    val checkScale by animateFloatAsState(
        targetValue = if (copied) 1f else 0.6f,
        animationSpec = tween(160),
        label = "copy_check_scale"
    )
    val checkAlpha by animateFloatAsState(
        targetValue = if (copied) 1f else 0f,
        animationSpec = tween(160),
        label = "copy_check_alpha"
    )
    val copyAlpha by animateFloatAsState(
        targetValue = if (copied) 0f else 1f,
        animationSpec = tween(160),
        label = "copy_icon_alpha"
    )

    IconButton(
        onClick = {
            clipboardManager.setText(AnnotatedString(text))
            copied = true
        },
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Filled.ContentCopy,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer {
                    alpha = copyAlpha
                    scaleX = 1f - (1f - checkScale) * 0.4f
                    scaleY = 1f - (1f - checkScale) * 0.4f
                }
        )
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer {
                    alpha = checkAlpha
                    scaleX = checkScale
                    scaleY = checkScale
                }
        )
    }
}
