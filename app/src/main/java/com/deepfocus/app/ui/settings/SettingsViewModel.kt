package com.deepfocus.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deepfocus.app.ai.FishBrain
import com.deepfocus.app.ai.FishMoodResolver
import com.deepfocus.app.data.PersonaBase
import com.deepfocus.app.data.PersonaCardStore
import com.deepfocus.app.util.PermissionHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 权限状态数据类
 */
data class SettingsPermissionStatus(
    /** 使用情况访问权限是否已授权 */
    val usageStatsGranted: Boolean = false,
    /** 悬浮窗权限是否已授权 */
    val overlayGranted: Boolean = false,
    /** 无障碍服务权限是否已授权 */
    val accessibilityGranted: Boolean = false
)

/**
 * 设置界面 UI 状态
 */
data class SettingsUiState(
    /** 权限状态 */
    val permissionStatus: SettingsPermissionStatus = SettingsPermissionStatus(),
    /** 错误提示，UI 层据此展示反馈 */
    val errorMessage: String? = null,
    /** 一次性成功提示，UI 层据此展示 Snackbar */
    val successMessage: String? = null,
    /** 当前生效的人格卡 id（内置卡 id 或自定义卡 id） */
    val personaCardId: String = "",
    /** 当前生效的人格卡展示名 */
    val personaCardName: String = "",
    /** 当前底稿是不是用户自定义卡（false = 内置卡，随好感度自动切） */
    val personaIsCustom: Boolean = false,
    /** 当前好感度（人格卡说明里展示，不做解锁门槛） */
    val affinity: Int = 0,
    /** 当前装扮 id */
    val outfitId: String = com.deepfocus.app.data.FishOutfits.NORMAL_ID
)

/**
 * 设置 ViewModel
 * 负责管理权限状态、开关设置和数据导出
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userFishRepository: com.deepfocus.app.data.repository.FishRepository,
    private val personaCardStore: PersonaCardStore,
    private val fishBrain: FishBrain,
    private val outfitStore: com.deepfocus.app.data.FishOutfitStore,
    application: Application
) : AndroidViewModel(application) {

    /** 设置 UI 状态 */
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // 检查权限
        checkPermissions()
        // 加载人格卡状态
        loadCompanionState()
        // 订阅装扮变化
        viewModelScope.launch {
            outfitStore.outfitId.collect { id ->
                _uiState.update { it.copy(outfitId = id) }
            }
        }
    }

    /** 换装 */
    fun setOutfit(outfitId: String) {
        outfitStore.set(outfitId)
    }

    /**
     * 加载人格卡状态。
     *
     * 内置卡随好感度自动切，用户不干预；这里只读展示当前生效的那张。
     */
    fun loadCompanionState() {
        viewModelScope.launch {
            val fish = userFishRepository.getUserFish()
            val affinity = fish?.affinityLevel ?: 0
            // 跨档判定：好感度跨过档位门槛时强制切回内置卡。
            // 必须在这里也跑一遍 —— 否则设置页会显示已被切掉的旧卡名
            // （底稿切回是 FishBrain / PersonaViewModel 触发的，这页不经过它们）。
            personaCardStore.onAffinityChanged(affinity)
            val base = personaCardStore.resolveBase(affinity)
            _uiState.update {
                it.copy(
                    personaCardId = personaCardStore.baseCardId(affinity),
                    personaCardName = personaCardStore.displayNameOf(base),
                    personaIsCustom = base is PersonaBase.Custom,
                    affinity = affinity
                )
            }
        }
    }

    /**
     * 检查权限状态
     */
    fun checkPermissions() {
        val context = getApplication<Application>()
        val status = SettingsPermissionStatus(
            usageStatsGranted = PermissionHelper.checkUsageStatsPermission(context),
            overlayGranted = PermissionHelper.checkOverlayPermission(context),
            accessibilityGranted = PermissionHelper.checkAccessibilityPermission(context)
        )
        _uiState.update { it.copy(permissionStatus = status) }
    }

    /**
     * 页面回到前台时调用
     * 重新检查权限状态，确保用户从系统设置授权后状态能及时更新；
     * 同时刷新人格卡档位与好感度（在别的页面改过档位后回来要能看到）
     */
    fun onResume() {
        checkPermissions()
        loadCompanionState()
    }

    /**
     * 清除错误提示
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * 清除一次性成功提示（Snackbar 展示后调用）
     */
    fun clearSuccess() {
        _uiState.update { it.copy(successMessage = null) }
    }

    /**
     * 好感度称号（UI 用）
     *
     * 界面上不摆数字 —— 只说"她现在把我当什么"。
     */
    fun affinityTitle(affinity: Int): String = FishMoodResolver.affinityTitle(affinity)
}
