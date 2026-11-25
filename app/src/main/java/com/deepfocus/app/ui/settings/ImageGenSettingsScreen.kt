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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.text.KeyboardOptions
import com.deepfocus.app.ai.ImageGenConfig
import com.deepfocus.app.ai.ImageGenConfigStore
import com.deepfocus.app.ai.ImageGenProviders
import com.deepfocus.app.ui.components.PrimaryButton
import com.deepfocus.app.ui.components.SoftTextField
import com.deepfocus.app.ui.components.SubPageTopBar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 生图 API 配置页
 *
 * **与聊天 API 完全独立** —— 聊天用一家、生图可以用另一家。
 * 这里只填三样：base_url、api_key、模型名。
 *
 * 保存后游历本里就能给日记「画一张旅行相片」了。
 */
@Composable
fun ImageGenSettingsScreen(
    onBack: () -> Unit = {},
    viewModel: ImageGenSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.saved) {
        if (state.saved) {
            snackbarHostState.showSnackbar("已保存")
            viewModel.clearSaved()
        }
    }

    Scaffold(
        topBar = { SubPageTopBar(title = "生图 API", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "给旅行日记画相片用。和聊天 API 是两套独立的配置。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 预设
            Text(
                text = "服务商",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ImageGenProviders.ALL.forEach { provider ->
                    val selected = state.providerId == provider.id
                    PrimaryButton(
                        text = provider.displayName,
                        onClick = { viewModel.selectProvider(provider.id) },
                        modifier = Modifier.weight(1f),
                        height = 44.dp,
                        containerColor = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        contentColor = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            SoftTextField(
                value = state.baseUrl,
                onValueChange = viewModel::updateBaseUrl,
                label = "Base URL",
                placeholder = "https://api.minimax.cn/v1",
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            SoftTextField(
                value = state.apiKey,
                onValueChange = viewModel::updateApiKey,
                label = "API Key",
                placeholder = "sk-...",
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            SoftTextField(
                value = state.model,
                onValueChange = viewModel::updateModel,
                label = "模型",
                placeholder = "MiniMax-H3-Image",
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = state.providerNote,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            state.errorMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            PrimaryButton(
                text = "保存",
                onClick = { viewModel.save() },
                enabled = state.baseUrl.isNotBlank() &&
                    state.apiKey.isNotBlank() &&
                    state.model.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "提示：每次生图都会带上当前装扮的参考图，这样画出来的才是它本人。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 生图配置 UI 状态
 */
data class ImageGenSettingsUiState(
    val providerId: String = ImageGenConfigStore.DEFAULT_PROVIDER_ID,
    val baseUrl: String = ImageGenConfigStore.DEFAULT_BASE_URL,
    val apiKey: String = "",
    val model: String = ImageGenConfigStore.DEFAULT_MODEL,
    val errorMessage: String? = null,
    val saved: Boolean = false
) {
    val providerNote: String
        get() = ImageGenProviders.byId(providerId).note
}

/**
 * 生图配置 ViewModel
 */
@HiltViewModel
class ImageGenSettingsViewModel @Inject constructor(
    private val store: ImageGenConfigStore
) : ViewModel() {

    private val _state = MutableStateFlow(
        store.config.value.let {
            ImageGenSettingsUiState(
                providerId = it.providerId,
                baseUrl = it.baseUrl,
                apiKey = it.apiKey,
                model = it.model
            )
        }
    )
    val state: StateFlow<ImageGenSettingsUiState> = _state.asStateFlow()

    fun selectProvider(providerId: String) {
        val provider = ImageGenProviders.byId(providerId)
        _state.update {
            it.copy(
                providerId = provider.id,
                baseUrl = provider.baseUrl,
                model = provider.defaultModel
            )
        }
    }

    fun updateBaseUrl(value: String) {
        _state.update { it.copy(baseUrl = value.trim(), errorMessage = null) }
    }

    fun updateApiKey(value: String) {
        _state.update { it.copy(apiKey = value.trim(), errorMessage = null) }
    }

    fun updateModel(value: String) {
        _state.update { it.copy(model = value.trim(), errorMessage = null) }
    }

    fun save() {
        val s = _state.value
        if (s.baseUrl.isBlank() || s.apiKey.isBlank() || s.model.isBlank()) {
            _state.update { it.copy(errorMessage = "三项都要填") }
            return
        }
        viewModelScope.launch {
            store.save(
                ImageGenConfig(
                    providerId = s.providerId,
                    baseUrl = s.baseUrl,
                    apiKey = s.apiKey,
                    model = s.model,
                    enabled = true
                )
            )
            _state.update { it.copy(saved = true) }
        }
    }

    fun clearSaved() {
        _state.update { it.copy(saved = false) }
    }
}
