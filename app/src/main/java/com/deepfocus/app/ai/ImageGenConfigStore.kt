package com.deepfocus.app.ai

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Singleton

/**
 * 生图 API 配置存储
 *
 * 用户在「设置 → 生图 API」中自行填写：
 * - baseUrl：端点根地址，例如 https://api.minimax.cn/v1
 * - apiKey：密钥，长期保存在本机（Keystore 加密）
 * - model：模型名，例如 MiniMax-H3-Image
 *
 * **与聊天 API 完全独立** —— 聊天用 [AiConfigStore]，生图用这里。
 * 两者的额度、密钥、可用性互不影响：
 * 用户可以聊天用 DeepSeek、生图用 MiniMax，反之亦然。
 *
 * 长期存储说明：
 * - SharedPreferences 的 apply() 异步落盘，进程被杀不丢数据
 * - 单独文件 deep_focus_image_api，与聊天配置隔离
 * - 通过 StateFlow 对外暴露变更，配置后立即生效，无需重启
 */
@Singleton
class ImageGenConfigStore @javax.inject.Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext context: Context
) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(read())
    val config: StateFlow<ImageGenConfig> = _config.asStateFlow()

    /** 保存配置并立即广播变更 */
    fun save(config: ImageGenConfig) {
        prefs.edit()
            .putString(KEY_PROVIDER_ID, config.providerId)
            .putString(KEY_BASE_URL, config.baseUrl)
            .putString(KEY_API_KEY, SecureKeyStore.encrypt(config.apiKey))
            .putString(KEY_MODEL, config.model)
            .putBoolean(KEY_ENABLED, config.enabled)
            .apply()
        _config.value = config
    }

    /** 只更新开关，不动其他字段 */
    fun setEnabled(enabled: Boolean) {
        save(_config.value.copy(enabled = enabled))
    }

    /** 掩码显示的密钥，如 sk-abc...xyz */
    fun maskedApiKey(): String {
        val key = _config.value.apiKey
        if (key.isBlank()) return ""
        if (key.length <= 10) return "••••••"
        return key.take(4) + "••••••" + key.takeLast(4)
    }

    private fun read(): ImageGenConfig {
        val storedKey = prefs.getString(KEY_API_KEY, "") ?: ""
        val providerId = prefs.getString(KEY_PROVIDER_ID, DEFAULT_PROVIDER_ID) ?: DEFAULT_PROVIDER_ID
        val provider = ImageGenProviders.byId(providerId)
        return ImageGenConfig(
            providerId = providerId,
            baseUrl = prefs.getString(KEY_BASE_URL, provider.baseUrl)?.takeIf { it.isNotBlank() }
                ?: provider.baseUrl,
            apiKey = SecureKeyStore.decrypt(storedKey),
            model = prefs.getString(KEY_MODEL, provider.defaultModel)?.takeIf { it.isNotBlank() }
                ?: provider.defaultModel,
            enabled = prefs.getBoolean(KEY_ENABLED, true)
        )
    }

    companion object {
        private const val PREFS_NAME = "deep_focus_image_api"
        private const val KEY_PROVIDER_ID = "provider_id"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL = "model"
        private const val KEY_ENABLED = "enabled"

        /** 默认厂商 */
        const val DEFAULT_PROVIDER_ID = "minimax"

        /** 默认模型名（用户可在设置里改） */
        const val DEFAULT_MODEL = "MiniMax-H3-Image"

        /** MiniMax 端点根地址 */
        const val DEFAULT_BASE_URL = "https://api.minimax.cn/v1"
    }
}

/**
 * 生图配置数据类
 */
data class ImageGenConfig(
    /** 厂商预设 id，见 [ImageGenProviders] */
    val providerId: String = ImageGenConfigStore.DEFAULT_PROVIDER_ID,
    /** 端点根地址，如 https://api.minimax.cn/v1 */
    val baseUrl: String = ImageGenConfigStore.DEFAULT_BASE_URL,
    /** API Key */
    val apiKey: String = "",
    /** 模型名 */
    val model: String = ImageGenConfigStore.DEFAULT_MODEL,
    /** 是否启用 */
    val enabled: Boolean = true
) {
    /** 当前厂商预设 */
    val provider: ImageGenProvider get() = ImageGenProviders.byId(providerId)

    /** 配置是否完整可用 */
    val isValid: Boolean
        get() = baseUrl.isNotBlank() && model.isNotBlank() && apiKey.isNotBlank()

    /** 拼接完整的生图地址 */
    val imageEndpoint: String
        get() {
            val base = baseUrl.trimEnd('/')
            // 用户可能直接把完整地址粘进来了，此时不要再拼
            if (base.endsWith("/images/edits") || base.endsWith("/edits")) return base
            val path = provider.imagePath
            return if (base.endsWith(path)) base else "$base$path"
        }
}
