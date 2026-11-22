package com.deepfocus.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.deepfocus.app.ui.components.PermissionCard
import com.deepfocus.app.ui.components.PrimaryButton
import com.deepfocus.app.ui.theme.themedWeight
import com.deepfocus.app.util.PermissionHelper

/**
 * 首启权限引导页
 *
 * ## 为什么必须有
 *
 * 专注锁机依赖 `UsageStatsManager` 查询前台应用。这个权限是**特殊权限**，
 * 只能在系统设置里手动开，没有对应的运行时弹窗。
 *
 * 原先 App 没有任何引导，用户装完直接开专注 —— 结果 `queryEvents` 抛
 * SecurityException 被 catch 吞掉，前台包名永远拿不到，锁机完全不工作，
 * 而且**没有任何报错**。用户只会觉得"这 App 没用"。
 *
 * ## 交互
 *
 * - 三张权限卡：使用情况访问（必需，锁机靠它）/ 无障碍（必需，拉回靠它）/ 悬浮窗（可选，桌宠靠它）
 * - 点「去授权」跳系统设置，回来时自动刷新状态（ON_RESUME 监听）
 * - 必需项都拿到后才出现「开始专注」按钮；否则只提示还差什么
 */
@Composable
fun PermissionSetupScreen(
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 三项权限的授权状态
    var usageStatsGranted by remember { mutableStateOf(false) }
    var accessibilityGranted by remember { mutableStateOf(false) }
    var overlayGranted by remember { mutableStateOf(false) }

    // 首次进入 + 每次从系统设置返回都刷新
    DisposableEffect(lifecycleOwner) {
        fun refresh() {
            usageStatsGranted = PermissionHelper.checkUsageStatsPermission(context)
            accessibilityGranted = PermissionHelper.checkAccessibilityPermission(context)
            overlayGranted = PermissionHelper.checkOverlayPermission(context)
        }
        refresh()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val requiredReady = usageStatsGranted && accessibilityGranted

    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
    ) {
        // 滚动区（权限卡）
        //
        // 注意：这一层里不能用 Spacer(weight(1f)) 来把按钮压到底 ——
        // verticalScroll 的 Column 里 weight 不生效，按钮会掉回内容高度，
        // 停在页面中间而不是吸底。所以滚动区和底部操作区分成两层。
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "还差一步才能锁住你",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = themedWeight(FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "专注锁机需要两项系统权限。授权后你切到别的应用，它会把你拉回来。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            PermissionCard(
                title = "使用情况访问",
                description = "必需。用于识别你当前切到了哪个应用，判断有没有离开专注。",
                isGranted = usageStatsGranted,
                onRequestPermission = { PermissionHelper.requestUsageStatsPermission(context) }
            )

            PermissionCard(
                title = "无障碍服务",
                description = "必需。用于在 Android 10 之后把你从别的应用拉回专注页。",
                isGranted = accessibilityGranted,
                onRequestPermission = { PermissionHelper.requestAccessibilityPermission(context) }
            )

            PermissionCard(
                title = "悬浮窗",
                description = "可选。让大肥鱼住在桌面上，你专注时它陪着。",
                isGranted = overlayGranted,
                onRequestPermission = { PermissionHelper.requestOverlayPermission(context) }
            )
        }

        // 底部操作区：主行动永远在拇指够得到的地方
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 渐变托底：滚动内容滑到底时不会硬生生撞上按钮
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.background.copy(alpha = 0f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            if (requiredReady) {
                PrimaryButton(
                    text = "开始专注",
                    onClick = onDone,
                    enabled = true,
                    containerColor = MaterialTheme.colorScheme.primary,
                    height = 52.dp
                )
            } else {
                Text(
                    text = "还差：${if (!usageStatsGranted) "使用情况访问" else ""}" +
                        (if (!usageStatsGranted && !accessibilityGranted) "、" else "") +
                        (if (!accessibilityGranted) "无障碍服务" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            }
        }
    }
}
