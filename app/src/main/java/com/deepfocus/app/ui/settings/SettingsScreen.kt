package com.deepfocus.app.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepfocus.app.BuildConfig
import com.deepfocus.app.R
import com.deepfocus.app.detection.AppDetectionManager
import com.deepfocus.app.ui.components.CollapsibleSection
import com.deepfocus.app.ui.components.FishCanvas
import com.deepfocus.app.ui.components.LocalFishOutfit
import com.deepfocus.app.ui.components.SoftTextField
import com.deepfocus.app.ui.components.iconBaselineOffset
import com.deepfocus.app.util.PermissionHelper
import com.deepfocus.app.util.RomUtils
import com.deepfocus.app.ui.theme.themedWeight
import java.io.File

/**
 * 设置界面 — iOS Settings 风格
 *
 * 设计语言：
 * - 分组列表（Grouped Inset List），每组用 Surface(surfaceVariant) + 14dp 圆角
 * - 组内行高 56dp，水平 padding 16dp
 * - 组间 24dp 间距
 * - 无阴影（iOS Settings 风格）
 * - 开关/箭头右对齐
 */
@Composable
fun SettingsScreen(
    onNavigateToAiSettings: () -> Unit = {},
    onNavigateToPersona: () -> Unit = {},
    onNavigateToJournals: () -> Unit = {},
    onNavigateToImageSettings: () -> Unit = {},
    onNavigateToMemory: () -> Unit = {},
    onNavigateToPet: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current

    // 监听生命周期，ON_RESUME 时刷新权限状态
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    // 导出成功的正向反馈（一次性 Snackbar）
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSuccess()
        }
    }

    // 当导出文件路径变化时，触发分享；成功后才提示用户
    LaunchedEffect(uiState.exportedFilePath) {
        uiState.exportedFilePath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                try {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "导出数据"))
                    snackbarHostState.showSnackbar("已打开分享窗口，请选择保存位置")
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(context, "未找到可分享的应用", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "分享失败：${e.message ?: "未知错误"}", Toast.LENGTH_SHORT).show()
                }
            }
            viewModel.clearExportedFile()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // ── 页面标题（headlineSmall，更宽裕的上边距） ──
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = "设置",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.size(24.dp))

            // ── 错误提示 ──
            uiState.errorMessage?.let { msg ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.clearError() }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            // 图标下移 0.125em 与文案基线对齐（规范 5）
                            modifier = Modifier
                                .size(20.dp)
                                .offset(y = iconBaselineOffset(MaterialTheme.typography.bodyMedium))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(modifier = Modifier.size(24.dp))
            }

            // ── 大肥鱼人格卡 ──
            // 内置卡随好感度自动切，用户不干预；这里只做只读展示。
            // 要改内容、去切自定义卡 → 点下面的「大肥鱼人格卡」进详情页。
            //
            // 放在最前面：这是这个 App 的**灵魂设置** ——
            // 她是谁、怎么说话、记住你什么，全在这里。
            SectionHeader(title = "大肥鱼人格卡")

            IosSettingsGroup {
                IosNavigationRow(
                    icon = if (uiState.personaIsCustom) Icons.Filled.Favorite else Icons.Filled.AutoAwesome,
                    title = uiState.personaCardName.ifBlank { "大肥鱼" },
                    subtitle = if (uiState.personaIsCustom) {
                        "我的自定义卡 · ${viewModel.affinityTitle(uiState.affinity)}"
                    } else {
                        "${viewModel.affinityTitle(uiState.affinity)} · 随好感度自动换卡"
                    },
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNavigateToPersona()
                    }
                )
            }

            Spacer(modifier = Modifier.size(24.dp))

            // ── 装扮 ──
            // 只有常服 / 冬装两套，一个开关。
            // 换装不只是换张图：它牵着旅行目的地的冷暖倾向，也决定生图参考图。
            SectionHeader(title = "装扮")

            IosSettingsGroup {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        com.deepfocus.app.data.FishOutfits.ALL.forEach { outfit ->
                            OutfitOption(
                                outfit = outfit,
                                isSelected = uiState.outfitId == outfit.id,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    hapticFeedback.performHapticFeedback(
                                        HapticFeedbackType.TextHandleMove
                                    )
                                    viewModel.setOutfit(outfit.id)
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "冬装爱去冷的地方，常服爱去暖的地方。换装也会换生图参考图。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.size(24.dp))

            // ── 大肥鱼 AI ──
            SectionHeader(title = "大肥鱼 AI")

            IosSettingsGroup {
                // API 配置（导航行）
                IosNavigationRow(
                    icon = Icons.Filled.CloudSync,
                    title = "聊天 API 配置",
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNavigateToAiSettings()
                    }
                )
                IosDivider()
                // 生图 API（独立一套配置）
                IosNavigationRow(
                    icon = Icons.Filled.PhotoCamera,
                    title = "生图 API 配置",
                    subtitle = "给旅行日记画相片用，与聊天 API 独立",
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNavigateToImageSettings()
                    }
                )
                IosDivider()
                // 游历本（导航行）
                IosNavigationRow(
                    icon = Icons.Filled.Map,
                    title = "游历本",
                    subtitle = "她去过的地方，都记在这里",
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNavigateToJournals()
                    }
                )
                IosDivider()
                // 记忆管理（导航行）
                IosNavigationRow(
                    icon = Icons.Filled.Psychology,
                    title = "大肥鱼的记忆",
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNavigateToMemory()
                    }
                )
                IosDivider()
                // 桌宠设置（导航行）
                IosNavigationRow(
                    icon = null,
                    iconPainter = painterResource(R.drawable.ic_deepseek_whale),
                    title = "桌面大肥鱼",
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNavigateToPet()
                    }
                )
            }

            Spacer(modifier = Modifier.size(24.dp))

            // ── 自定义与数据 ──
            SectionHeader(title = "自定义与数据")

            IosSettingsGroup {
                // 导出数据（导航行）
                IosNavigationRow(
                    icon = Icons.Filled.Upload,
                    title = "导出数据",
                    onClick = { viewModel.exportData() }
                )
            }

            Spacer(modifier = Modifier.size(24.dp))

            // ── 后台保活引导 ──
            // 放最后：这是**故障时的求助入口**，不是日常要用的功能。
            // 放在页首会让每次进来都先看到一坨告警，喧宾夺主。
            KeepAliveGuideCard()

            Spacer(modifier = Modifier.size(24.dp))

            // 合规提示（《人工智能拟人化互动服务管理暂行办法》要求显著提示）：
            // 放在设置页最底部 —— 聊天首页只留形象，不摆这段说明文字。
            Text(
                text = "大肥鱼是 AI，不是真人。它的记忆和心情都由程序模拟，聊天记录只存在你手机里。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
            )

            // 底部留白：别让最后一项被底部导航栏压住
            Spacer(modifier = Modifier.size(48.dp))
        }

        // 导出成功/失败的一次性反馈，置于页面底部
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// ──────────────────────────────────────────────
// iOS Style Shared Components
// ──────────────────────────────────────────────

/**
 * iOS Settings 分组标题（titleSmall，灰字）
 */
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

/**
 * 装扮选项（设置页里的两张小卡）
 *
 * 立绘用 `LocalFishOutfit` 注入 —— 这张卡显示的是**它自己那套衣服**，
 * 而不是当前全局装扮。
 */
@Composable
private fun OutfitOption(
    outfit: com.deepfocus.app.data.FishOutfit,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    }
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = borderColor
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp, max = 110.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                CompositionLocalProvider(LocalFishOutfit provides outfit) {
                    FishCanvas(
                        state = "idle",
                        modifier = Modifier.fillMaxSize(),
                        useVideo = false
                    )
                }
            }
            Spacer(modifier = Modifier.size(6.dp))
            Text(
                text = outfit.displayName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = themedWeight(if (isSelected) FontWeight.SemiBold else FontWeight.Normal),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

/**
 * iOS Settings 分组容器
 * - surfaceVariant 背景
 * - 14dp 圆角
 * - 无阴影
 */
@Composable
private fun IosSettingsGroup(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 0.dp
    ) {
        // Surface 内部为 Box 布局，多个子项会堆叠；用 Column 包裹确保垂直排列
        Column {
            content()
        }
    }
}

/**
 * iOS 风格分割线 — 0.5dp，alpha 0.1
 */
@Composable
private fun IosDivider() {
    HorizontalDivider(
        modifier = Modifier.alpha(0.1f),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.onSurface
    )
}

/**
 * iOS 导航行
 * - 左侧图标 (20dp, primary alpha 0.7)
 * - 中间 bodyLarge 标题
 * - 右侧 chevron (14dp, onSurfaceVariant)
 * - 行高 56dp，水平 padding 16dp
 */
@Composable
private fun IosNavigationRow(
    icon: ImageVector? = null,
    title: String,
    onClick: () -> Unit,
    /** 位图图标（品牌图标）。与 [icon] 二选一 —— 位图不能 tint，会抹掉渐变。 */
    iconPainter: Painter? = null,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    showChevron: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = if (subtitle != null) 10.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconPainter != null) {
            // 位图品牌图标：保留原色，不挂 ColorFilter
            Icon(
                painter = iconPainter,
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier
                    .size(22.dp)
                    .offset(y = iconBaselineOffset(MaterialTheme.typography.bodyLarge))
            )
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                // 图标下移 0.125em 与标题基线对齐（规范 5）
                modifier = Modifier
                    .size(20.dp)
                    .offset(y = iconBaselineOffset(MaterialTheme.typography.bodyLarge))
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(12.dp))
            trailing()
        } else if (showChevron) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/**
 * 后台保活引导 — 可折叠卡片
 *
 * 指导用户完成电池优化白名单、自启动、锁定后台等设置，确保守护服务稳定运行
 *
 * 需求 1+4 增强：
 * - 步骤文案按 ROM 品牌动态生成（ColorOS 单独给出「允许完全后台行为」等关键项）
 * - 提供一键跳转按钮，直接进入系统对应的保活设置页
 * - 顶部展示无障碍守护的实时状态，系统把无障碍开关关掉时给出明确警示与恢复入口
 */
@Composable
private fun KeepAliveGuideCard() {
    var expanded by remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current
    val context = LocalContext.current
    val accentColor = Color(0xFFF59E0B)
    val guide = remember { RomUtils.getKeepAliveGuide() }

    // 无障碍守护实时状态：连接中 / 被系统关闭
    val accessibilityConnected by AppDetectionManager.isAccessibilityConnected.collectAsStateWithLifecycle()
    val accessibilityDisabled by AppDetectionManager.isAccessibilityDisabled.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                expanded = !expanded
            }
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.BatteryFull,
                        contentDescription = null,
                        tint = accentColor,
                        // 图标下移 0.125em 与标题基线对齐（规范 5）
                        modifier = Modifier
                            .size(22.dp)
                            .offset(y = iconBaselineOffset(MaterialTheme.typography.bodyLarge))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "后台保活引导",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = themedWeight(FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = guide.brandName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }

            // 守护状态实时提示（需求 1+4）
            if (accessibilityDisabled) {
                Spacer(modifier = Modifier.size(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = accentColor.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { PermissionHelper.requestAccessibilityPermission(context) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = accentColor,
                            // 图标下移 0.125em 与文案基线对齐（规范 5）
                            modifier = Modifier
                                .size(18.dp)
                                .offset(y = iconBaselineOffset(MaterialTheme.typography.bodySmall))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "无障碍服务已被系统关闭，点击重新开启守护",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else if (!accessibilityConnected) {
                Spacer(modifier = Modifier.size(12.dp))
                Text(
                    text = "无障碍服务正在恢复中（系统会在数秒内自动重连）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 折叠用 Grid 0fr→1fr（规范 7）——
            // AnimatedVisibility+expandVertically 会先淡出再收起，收起时下方内容会跳一下。
            CollapsibleSection(expanded = expanded) {
                Column {
                    Spacer(modifier = Modifier.size(16.dp))
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    )
                    Spacer(modifier = Modifier.size(12.dp))

                    // 一键跳转：直接进入系统保活设置页
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KeepAliveActionButton(
                            text = "电池白名单",
                            modifier = Modifier.weight(1f),
                            onClick = { PermissionHelper.requestBatteryOptimization(context) }
                        )
                        KeepAliveActionButton(
                            text = "允许后台运行",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (RomUtils.detectBrand() == RomUtils.RomBrand.COLOROS) {
                                    PermissionHelper.requestColorOsBackgroundRun(context)
                                } else {
                                    PermissionHelper.requestAutoStart(context)
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.size(16.dp))

                    guide.steps.forEachIndexed { index, step ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = accentColor.copy(alpha = 0.12f),
                                modifier = Modifier.size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = themedWeight(FontWeight.SemiBold),
                                        color = accentColor
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = step,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 保活引导里的快捷跳转按钮（暖琥珀色描边，16dp 圆角）
 */
@Composable
private fun KeepAliveActionButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    val accentColor = Color(0xFFF59E0B)
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        shape = RoundedCornerShape(16.dp),
        color = accentColor.copy(alpha = 0.10f),
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier.padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = themedWeight(FontWeight.SemiBold),
                color = accentColor
            )
        }
    }
}
