package com.deepfocus.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import com.deepfocus.app.pet.DesktopPetService
import com.deepfocus.app.pet.PetWindowController
import com.deepfocus.app.util.PermissionHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * 桌宠设置 UI 状态
 */
data class PetSettingsUiState(
    /** 桌宠是否已开启 */
    val enabled: Boolean = false,
    /** 是否有悬浮窗权限 */
    val hasOverlayPermission: Boolean = false,
    /** 桌宠尺寸（dp） */
    val sizeDp: Int = PetWindowController.DEFAULT_SIZE_DP,
    /** 是否已授予电池优化白名单 */
    val hasBatteryExemption: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null
)

/**
 * 桌宠设置 ViewModel
 *
 * 桌宠的开启/关闭走「用户主动操作」路径 ——
 * Android 15 起后台启动前台服务受限，必须在 App 可见时启动。
 */
@HiltViewModel
class PetSettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    companion object {
        private const val PREFS = "deep_focus_pet"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_SIZE_DP = "size_dp"
        private const val KEY_POS_X = "pos_x"
        private const val KEY_POS_Y = "pos_y"

        /** 用户拨了开关但卡在悬浮窗权限上 —— 授权返回后自动启动 */
        private const val KEY_PENDING_ENABLE = "pending_enable"
    }

    private val _uiState = MutableStateFlow(PetSettingsUiState())
    val uiState: StateFlow<PetSettingsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /**
     * 刷新状态
     *
     * 从系统设置返回时也要调 —— 悬浮窗权限是在系统设置里授的，
     * 不刷新的话开关还是关的，用户以为没生效。
     */
    fun refresh() {
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val overlayGranted = PermissionHelper.checkOverlayPermission(appContext)
        _uiState.update {
            it.copy(
                enabled = prefs.getBoolean(KEY_ENABLED, false),
                hasOverlayPermission = overlayGranted,
                sizeDp = PetWindowController.nearestTier(
                    prefs.getInt(KEY_SIZE_DP, PetWindowController.DEFAULT_SIZE_DP)
                ),
                hasBatteryExemption = PermissionHelper.checkBatteryOptimization(appContext)
            )
        }
        // 用户之前拨了开关但卡在权限上，现在权限到手了 → 直接把桌宠放出来。
        // 否则用户得「去设置 → 授权 → 回到 App → 再拨一次开关」，多绕一圈。
        if (overlayGranted && prefs.getBoolean(KEY_PENDING_ENABLE, false)) {
            prefs.edit().putBoolean(KEY_PENDING_ENABLE, false).apply()
            launchPet()
        }
    }

    /**
     * 开启桌宠
     *
     * 前置条件：悬浮窗权限。没有权限时先引导用户去系统设置开启，
     * 并记下「用户想开」—— 授权返回后自动启动（见 [refresh]）。
     */
    fun enablePet() {
        if (!PermissionHelper.checkOverlayPermission(appContext)) {
            appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_PENDING_ENABLE, true)
                .apply()
            PermissionHelper.requestOverlayPermission(appContext)
            _uiState.update {
                it.copy(errorMessage = "需要「显示在其他应用上层」权限才能显示桌宠，去系统设置开启吧")
            }
            return
        }
        launchPet()
    }

    /** 真正把桌宠放出来 */
    private fun launchPet() {
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, true)
            .putBoolean(KEY_PENDING_ENABLE, false)
            .apply()
        DesktopPetService.start(appContext)
        _uiState.update { it.copy(enabled = true, errorMessage = null, message = "大肥鱼上桌了") }
    }

    /**
     * 关闭桌宠
     */
    fun disablePet() {
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, false)
            .apply()
        DesktopPetService.stop(appContext)
        _uiState.update { it.copy(enabled = false, message = "大肥鱼下班了") }
    }

    /**
     * 切换桌宠开关
     */
    fun togglePet(enabled: Boolean) {
        if (enabled) enablePet() else disablePet()
    }

    /**
     * 修改桌宠尺寸
     */
    fun setSize(sizeDp: Int) {
        // 只认档位值，避免旧版本保存的连续值把选中态卡在档位之间
        val tier = PetWindowController.nearestTier(sizeDp)
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_SIZE_DP, tier)
            .apply()
        _uiState.update { it.copy(sizeDp = tier) }
        // 服务在跑就实时改窗口尺寸 —— 不重启，重启会闪一下
        if (_uiState.value.enabled) {
            DesktopPetService.setSize(appContext, tier)
        }
    }

    /**
     * 请求悬浮窗权限
     */
    fun requestOverlayPermission() {
        PermissionHelper.requestOverlayPermission(appContext)
    }

    /**
     * 请求电池优化白名单
     *
     * 不加白名单的话，国产 ROM 会在几分钟内把桌宠服务杀掉，
     * 用户看到的就是「桌宠莫名消失」—— 第一大差评来源。
     */
    fun requestBatteryExemption() {
        PermissionHelper.requestBatteryOptimization(appContext)
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
}
