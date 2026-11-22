package com.deepfocus.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deepfocus.app.data.repository.FishRepository
import com.deepfocus.app.ui.onboarding.OnboardingStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 主界面初始状态
 */
sealed class MainUiState {
    /** 加载中 */
    object Loading : MainUiState()
    /** 未选择大肥鱼，需显示选择页 */
    object NeedFishSelection : MainUiState()
    /** 缺必需权限，需显示首启权限引导页 */
    object NeedPermissionSetup : MainUiState()
    /** 已有大肥鱼，显示主界面 */
    object Ready : MainUiState()
}

/**
 * 主界面 ViewModel
 * 检查用户是否已选择大肥鱼 + 是否已授予必需权限，决定显示哪个页面
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val fishRepository: FishRepository,
    outfitStore: com.deepfocus.app.data.FishOutfitStore,
    application: Application
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<MainUiState>(MainUiState.Loading)
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    /** 当前装扮 —— 注入给所有 FishCanvas，换装后全局立绘一起换 */
    val outfit: kotlinx.coroutines.flow.Flow<com.deepfocus.app.data.FishOutfit> = outfitStore.outfit

    init {
        checkFishExistence()
    }

    /**
     * 检查用户是否已选择大肥鱼
     *
     * 首次启动时保证 Loading 界面至少展示 [MIN_LOADING_DURATION_MS]，
     * 让用户能看到"DeepFocus 专注每一刻，陪伴每一天"的品牌标语，
     * 避免异步检查过快导致开屏一闪而过。
     * 用 async 并行执行大肥鱼检查 + delay，两者取最大值，不阻塞业务。
     * 后续由 FishSelectionScreen 创建大肥鱼后回调触发时，无需再等待。
     *
     * 顺序：先选大肥鱼（建立感情），再引导权限（锁机才有效）。
     * 反过来的话，用户还没认识大肥鱼就被一屏系统权限拦住，体验很差。
     */
    fun checkFishExistence() {
        viewModelScope.launch {
            val isFirstCheck = _state.value is MainUiState.Loading
            if (!isFirstCheck) {
                _state.value = MainUiState.Loading
            }
            coroutineScope {
                val catCheck = async { fishRepository.hasFish() }
                if (isFirstCheck) {
                    // 并行等待最小展示时长，与大肥鱼检查取最大值
                    delay(MIN_LOADING_DURATION_MS)
                }
                val hasFish = catCheck.await()
                _state.value = when {
                    !hasFish -> MainUiState.NeedFishSelection
                    OnboardingStore.needsPermissionGuide(getApplication()) ->
                        MainUiState.NeedPermissionSetup
                    else -> MainUiState.Ready
                }
            }
        }
    }

    companion object {
        /** 开屏 Loading 界面最小展示时长（毫秒） */
        private const val MIN_LOADING_DURATION_MS = 1000L
    }
}
