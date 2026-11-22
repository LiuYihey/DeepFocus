package com.deepfocus.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepfocus.app.ai.AiProviders
import com.deepfocus.app.ui.components.CopyIconButton
import com.deepfocus.app.ui.components.SoftTextField
import com.deepfocus.app.ui.components.SubPageTopBar
import com.deepfocus.app.ui.theme.tabularNumbers
import com.deepfocus.app.ui.theme.themedWeight

/**
 * API 配置页
 *
 * 用户在这里选厂商、填自己的 API 密钥。
 * 密钥经 Android Keystore 加密后长期保存在本机，不上传任何服务器。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    onBack: () -> Unit = {},
    viewModel: AiSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showKey by remember { mutableStateOf(false) }
    var providerExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            SubPageTopBar(title = "API 配置", onBack = onBack)
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

            // ── 启用开关 ──
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
                        text = "启用大肥鱼对话",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = uiState.enabled,
                        onCheckedChange = viewModel::toggleEnabled
                    )
                }
            }

            // ── 厂商选择 ──
            ExposedDropdownMenuBox(
                expanded = providerExpanded,
                onExpandedChange = { providerExpanded = it }
            ) {
                // 服务商下拉的锚点必须用 OutlinedTextField（ExposedDropdownMenuBox 的限制），
                // 但描边要拿掉 —— 规范 2 禁描边，聚焦只有双环阴影。
                // 否则全 App 就剩这一个输入框带框，视觉上很突兀。
                OutlinedTextField(
                    value = uiState.provider.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("服务商") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent
                    )
                )
                ExposedDropdownMenu(
                    expanded = providerExpanded,
                    onDismissRequest = { providerExpanded = false }
                ) {
                    AiProviders.ALL.forEach { provider ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = provider.displayName,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (provider.note.isNotBlank()) {
                                        Text(
                                            text = provider.note,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            onClick = {
                                viewModel.selectProvider(provider.id)
                                providerExpanded = false
                            }
                        )
                    }
                }
            }

            // ── 端点 ──
            SettingsTextField(
                label = "API 地址",
                value = uiState.baseUrl,
                onValueChange = viewModel::updateBaseUrl,
                placeholder = "https://api.deepseek.com",
                trailingIcon = {
                    if (uiState.baseUrl.isNotBlank()) {
                        CopyIconButton(
                            text = uiState.baseUrl,
                            contentDescription = "复制 API 地址"
                        )
                    }
                }
            )

            // ── 模型 ──
            SettingsTextField(
                label = "模型名",
                value = uiState.model,
                onValueChange = viewModel::updateModel,
                placeholder = uiState.provider.defaultModel
            )

            // 备选模型快捷切换
            if (uiState.provider.modelHints.isNotEmpty()) {
                Text(
                    text = "常用模型",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    uiState.provider.modelHints.take(4).forEach { hint ->
                        Surface(
                            color = if (hint == uiState.model) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            TextButton(
                                onClick = { viewModel.updateModel(hint) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = hint,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // ── 密钥 ──
            SettingsTextField(
                label = "API 密钥",
                value = uiState.apiKey,
                onValueChange = viewModel::updateApiKey,
                placeholder = when {
                    !uiState.provider.requiresKey -> "本地部署可不填"
                    uiState.hasSavedKey -> "已保存，留空则不修改"
                    else -> "sk-..."
                },
                visualTransformation = if (showKey) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            imageVector = if (showKey) {
                                Icons.Filled.VisibilityOff
                            } else {
                                Icons.Filled.Visibility
                            },
                            contentDescription = if (showKey) "隐藏" else "显示",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            )

            // ── 温度 ──
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "温度",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = String.format("%.1f", uiState.temperature),
                            style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Slider(
                        value = uiState.temperature,
                        onValueChange = viewModel::updateTemperature,
                        valueRange = 0f..2f,
                        steps = 19
                    )
                }
            }

            // ── 错误 / 成功 ──
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

            uiState.testResult?.let { msg ->
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

            uiState.savedMessage?.let { msg ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            // ── 测试连接 ──
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(
                    onClick = { viewModel.testConnection() },
                    enabled = !uiState.isTesting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState.isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("测试中…")
                    } else {
                        Text("测试连接")
                    }
                }
            }

            // ── 保存 ──
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(
                    onClick = { viewModel.save() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("保存配置")
                }
            }

            Text(
                text = "密钥用系统 Keystore 加密后只存在这台设备上，App 不会把它发到任何地方 —— 只有你调用大模型时才会带上它。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * 设置页风格的输入框
 */
@Composable
private fun SettingsTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        SoftTextField(
            value = value,
            onValueChange = onValueChange,
            label = null,
            modifier = Modifier.fillMaxWidth(),
            placeholder = placeholder.ifBlank { null },
            singleLine = true,
            visualTransformation = visualTransformation,
            trailingIcon = trailingIcon
        )
    }
}
