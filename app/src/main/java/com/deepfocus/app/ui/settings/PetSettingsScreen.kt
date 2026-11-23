package com.deepfocus.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepfocus.app.pet.PetWindowController
import com.deepfocus.app.ui.components.SubPageTopBar
import com.deepfocus.app.ui.theme.themedWeight

/**
 * 桌宠设置页
 *
 * 桌宠是「桌面大肥鱼」的总开关。
 *
 * 关键设计：把「为什么大肥鱼会消失」这件事讲清楚。
 * 桌宠最常见的差评是「它莫名就没了」——
 * 但真实原因几乎都是被系统杀了后台，不是 App 的 bug。
 * 所以这里明确告诉用户：需要开哪些权限、怎么防止被杀。
 */
@Composable
fun PetSettingsScreen(
    onBack: () -> Unit = {},
    viewModel: PetSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 从系统设置返回时刷新权限状态。
    // 用 ON_RESUME 而不是 DisposableEffect(Unit) —— 后者只在首次进入时跑，
    // 用户去系统设置授权再回来时状态不会更新。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            SubPageTopBar(title = "桌面大肥鱼", onBack = onBack)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // ── 总开关 ──
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "启用桌宠",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = uiState.enabled,
                        onCheckedChange = { viewModel.togglePet(it) }
                    )
                }
            }

            // ── 悬浮窗权限 ──
            if (!uiState.hasOverlayPermission) {
                PermissionCard(
                    title = "需要「显示在其他应用上层」",
                    description = "没有这个权限，Android 不允许任何应用在桌面上画画。这是系统硬性限制。",
                    actionText = "去授权",
                    onAction = { viewModel.requestOverlayPermission() }
                )
            }

            // ── 电池优化白名单 ──
            if (!uiState.hasBatteryExemption) {
                PermissionCard(
                    title = "建议加入电池优化白名单",
                    description = "不加的话，系统会在几分钟内把大肥鱼杀掉，你看到的就会是「它莫名消失了」—— 这是被系统杀的，不是 App 的问题。",
                    actionText = "去设置",
                    onAction = { viewModel.requestBatteryExemption() }
                )
            }

            // ── 尺寸 ──
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "桌宠大小",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    val labels = listOf("小", "中", "大", "超大")
                    val selected = PetWindowController.nearestTier(uiState.sizeDp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PetWindowController.SIZE_TIERS_DP.forEachIndexed { index, dp ->
                            FilterChip(
                                selected = dp == selected,
                                onClick = { viewModel.setSize(dp) },
                                label = { Text(labels.getOrElse(index) { "$dp" }) }
                            )
                        }
                    }
                }
            }

            // ── 玩法说明 ──
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "怎么玩",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = themedWeight(FontWeight.SemiBold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    listOf(
                        "点它 → 逗它一下（不会打开 App）",
                        "双击 → 投喂大白饭",
                        "按住拖动 → 把它挪到别的地方",
                        "不碰它就不会乱跑，停在原地等你",
                        "它想说话时，会冒个泡泡告诉你",
                        "它饿了会露出饿相，困了会睡着"
                    ).forEach { line ->
                        Row(
                            modifier = Modifier.padding(vertical = 3.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "·",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.padding(horizontal = 6.dp))
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // ── 提示 ──
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "桌宠在别的 ROM 上可能需要多点设置",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = themedWeight(FontWeight.SemiBold)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "小米/红米：设置 → 应用设置 → 应用管理 → DeepFocus → 自启动 + 省电策略选「无限制」\n" +
                            "OPPO/一加/realme：设置 → 电池 → 应用耗电管理 → DeepFocus → 允许后台运行 + 允许自启动\n" +
                            "荣耀：设置 → 应用和 services → 应用启动管理 → DeepFocus → 改为手动管理，全部打开\n" +
                            "vivo：设置 → 应用与权限 → 权限管理 → 其它权限 → 悬浮窗（如果被关掉的话）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            uiState.errorMessage?.let { msg ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            uiState.message?.let { msg ->
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * 权限引导卡片
 */
@Composable
private fun PermissionCard(
    title: String,
    description: String,
    actionText: String,
    onAction: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = themedWeight(FontWeight.SemiBold)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onAction) { Text(actionText) }
        }
    }
}
