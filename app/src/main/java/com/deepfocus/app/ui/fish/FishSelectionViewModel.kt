package com.deepfocus.app.ui.fish

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepfocus.app.data.FishOutfit
import com.deepfocus.app.data.FishOutfits
import com.deepfocus.app.data.FishOutfitStore
import com.deepfocus.app.data.repository.FishRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 大肥鱼选择界面的 UI 状态
 *
 * 现在选的不是「品种」（只有一条鱼），而是**装扮** —— 常服还是冬装。
 */
data class FishSelectionUiState(
    /** 可选装扮 */
    val outfits: List<FishOutfit> = FishOutfits.ALL,
    /** 当前选中的装扮 id */
    val selectedOutfitId: String = FishOutfits.NORMAL_ID,
    /** 用户输入的大肥鱼名 */
    val fishName: String = "",
    /** 是否正在创建 */
    val isCreating: Boolean = false,
    /** 是否创建完成 */
    val isCreated: Boolean = false,
    /** 错误提示 */
    val errorMessage: String? = null
)

/**
 * 大肥鱼选择 ViewModel
 *
 * 只在首次启动时用：选装扮 + 起名 → 创建第一条鱼。
 * （「品种」已废弃 —— App 里只有一条大肥鱼，不引入第二个角色。）
 */
@HiltViewModel
class FishSelectionViewModel @Inject constructor(
    private val fishRepository: FishRepository,
    private val outfitStore: FishOutfitStore
) : ViewModel() {

    private val _state = MutableStateFlow(FishSelectionUiState())
    val state: StateFlow<FishSelectionUiState> = _state.asStateFlow()

    /** 选择装扮 */
    fun selectOutfit(outfitId: String) {
        _state.update { it.copy(selectedOutfitId = outfitId, errorMessage = null) }
    }

    /** 更新大肥鱼名（限制最多 12 个字符） */
    fun updateName(name: String) {
        val trimmed = if (name.length > 12) name.take(12) else name
        _state.update { it.copy(fishName = trimmed, errorMessage = null) }
    }

    /**
     * 创建新大肥鱼（首次选择）
     *
     * 品种固定 `bigfatfish` —— 只有这一条鱼；
     * 装扮单独存进 [FishOutfitStore]，不进数据库。
     */
    fun createFish() {
        val currentState = _state.value
        if (currentState.fishName.isBlank()) {
            _state.update { it.copy(errorMessage = "请给大肥鱼起个名字") }
            return
        }

        _state.update { it.copy(isCreating = true) }
        viewModelScope.launch {
            try {
                // 品种固定 bigfatfish —— fish_catalog 里只有这一条；
                // 装扮是另一套概念，单独存在 FishOutfitStore，不进品种表。
                fishRepository.createFish(
                    breedId = "bigfatfish",
                    name = currentState.fishName.trim()
                )
                outfitStore.set(currentState.selectedOutfitId)
                _state.update { it.copy(isCreating = false, isCreated = true) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(isCreating = false, errorMessage = "创建失败，请重试")
                }
            }
        }
    }
}
