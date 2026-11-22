package com.deepfocus.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepfocus.app.data.BuiltinPersonaCard
import com.deepfocus.app.data.PersonaCardStore
import com.deepfocus.app.data.local.entity.PersonaCardEntity
import com.deepfocus.app.data.repository.FishRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 人格卡编辑 UI 状态
 *
 * 页面同时呈现**两套包**：
 * - [builtinCards] 内置人格卡包 —— 随好感度自动切换，用户不能手动挑
 * - [customCards] 我的自定义卡 —— 用户自己改出来的，可以主动切
 *
 * 下面的字段是**当前生效那张卡**的内容（底稿 + 覆盖层），
 * 只有当前生效的那张能改。
 */
data class PersonaUiState(
    /** 内置人格卡包，按好感度门槛升序 */
    val builtinCards: List<BuiltinPersonaCard> = emptyList(),
    /** 我的自定义人格卡包 */
    val customCards: List<PersonaCardEntity> = emptyList(),
    /** 当前底稿 id（内置卡 id 或 custom_xxx） */
    val currentBaseId: String = "",
    /** 当前底稿的展示名 */
    val currentBaseName: String = "",
    /** 当前底稿是不是内置卡 */
    val currentIsBuiltin: Boolean = true,
    /** 当前好感度 */
    val affinity: Int = 0,
    /** 当前生效的底稿来源署名（内置卡才有） */
    val currentSourceRepo: String = "",
    /** 当前生效的底稿许可 */
    val currentSourceLicense: String = "",
    /** 当前生效卡的简介（内置卡的 tagline / 自定义卡留空） */
    val currentTagline: String = "",

    // ── 当前生效卡的内容（可编辑） ──
    val identity: String = "",
    val traits: String = "",
    val speechStyle: String = "",
    val catchphrases: String = "",
    val backstory: String = "",
    val taboos: String = "",
    val proactiveMotivation: String = "",
    val temperature: Float = 0.9f,

    // ── 用户自有字段（换卡不冲掉） ──
    /** 当前激活的大肥鱼的名字（真源在 user_fish.name） */
    val fishName: String = "",
    /** 它对用户的称呼 */
    val userAlias: String = "",

    /** 当前卡上有没有用户改动（覆盖层非空） */
    val hasOverlay: Boolean = false,
    /** 另存为自定义卡时填的名字 */
    val newCardName: String = "",
    /** 一次性提示 */
    val savedMessage: String? = null,
    val errorMessage: String? = null
)

/**
 * 人格卡 ViewModel
 *
 * 管两套卡 + 一层覆盖层。规则见 docs/adr/0001：
 * - 内置卡随好感度自动切，用户不干预
 * - 跨档时强制切回内置卡，覆盖层继续跟着走（改动被继承）
 * - 只有当前生效的那张能改，改动存成覆盖层
 * - 「另存为自定义卡」快照进自定义包，可以主动切过去
 */
@HiltViewModel
class PersonaViewModel @Inject constructor(
    private val personaCardStore: PersonaCardStore,
    private val fishRepository: FishRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PersonaUiState())
    val uiState: StateFlow<PersonaUiState> = _uiState.asStateFlow()

    init {
        reload()
    }

    /**
     * 重新载入两套包与当前生效卡
     *
     * 页面每次回到前台都要调一次：底部导航有 saveState/restoreState，
     * 别的页面改了好感度（跨档 → 强制切回内置卡）之后，
     * 这张编辑页还攥着旧数据，点保存会把旧文案写回库。
     */
    fun reload() {
        viewModelScope.launch {
            val affinity = fishRepository.getAffinityLevel()
            // 先跑一次跨档判定，跟 FishBrain.getPersona 保持同一条路径
            personaCardStore.onAffinityChanged(affinity)
            val state = personaCardStore.state()
            val base = personaCardStore.resolveBase(affinity)
            val content = personaCardStore.resolve(base, state)

            _uiState.update {
                it.copy(
                    builtinCards = personaCardStore.builtinCards(),
                    customCards = personaCardStore.customCards(),
                    currentBaseId = when (base) {
                        is com.deepfocus.app.data.PersonaBase.Builtin -> base.card.id
                        is com.deepfocus.app.data.PersonaBase.Custom -> base.card.id
                    },
                    currentBaseName = personaCardStore.displayNameOf(base),
                    currentIsBuiltin = base is com.deepfocus.app.data.PersonaBase.Builtin,
                    currentSourceRepo = (base as? com.deepfocus.app.data.PersonaBase.Builtin)?.card?.sourceRepo.orEmpty(),
                    currentSourceLicense = (base as? com.deepfocus.app.data.PersonaBase.Builtin)?.card?.sourceLicense.orEmpty(),
                    currentTagline = (base as? com.deepfocus.app.data.PersonaBase.Builtin)?.card?.tagline.orEmpty(),
                    affinity = affinity,
                    identity = content.identity,
                    traits = content.traits,
                    speechStyle = content.speechStyle,
                    catchphrases = content.catchphrases,
                    backstory = content.backstory,
                    taboos = content.taboos,
                    proactiveMotivation = content.proactiveMotivation,
                    temperature = content.temperature,
                    fishName = fishRepository.getUserFish()?.name.orEmpty(),
                    userAlias = state.userAlias,
                    hasOverlay = hasOverlay(state),
                    errorMessage = null
                )
            }
        }
    }

    /** 覆盖层里只要有一个字段非 null，就说明用户改过当前卡 */
    private fun hasOverlay(state: com.deepfocus.app.data.local.entity.PersonaStateEntity): Boolean =
        state.identity != null || state.traits != null || state.speechStyle != null ||
            state.catchphrases != null || state.backstory != null || state.taboos != null ||
            state.proactiveMotivation != null || state.temperature != null

    fun updateIdentity(v: String) = _uiState.update { it.copy(identity = v) }
    fun updateTraits(v: String) = _uiState.update { it.copy(traits = v) }
    fun updateSpeechStyle(v: String) = _uiState.update { it.copy(speechStyle = v) }
    fun updateCatchphrases(v: String) = _uiState.update { it.copy(catchphrases = v) }
    fun updateBackstory(v: String) = _uiState.update { it.copy(backstory = v) }
    fun updateTaboos(v: String) = _uiState.update { it.copy(taboos = v) }
    fun updateProactiveMotivation(v: String) = _uiState.update { it.copy(proactiveMotivation = v) }
    fun updateTemperature(v: Float) = _uiState.update { it.copy(temperature = v) }
    fun updateFishName(v: String) = _uiState.update { it.copy(fishName = v) }
    fun updateUserAlias(v: String) = _uiState.update { it.copy(userAlias = v) }
    fun updateNewCardName(v: String) = _uiState.update { it.copy(newCardName = v) }

    /**
     * 保存改动
     *
     * 内容写进**覆盖层**（只存跟底稿不同的字段）；
     * 名字写回当前大肥鱼（user_fish.name），称呼写进用户自有字段。
     * 覆盖层跟着底稿走 —— 跨档换内置卡时改动仍然生效。
     */
    fun save() {
        val s = _uiState.value
        val newName = s.fishName.trim()
        if (newName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "总得给它起个名字吧") }
            return
        }
        viewModelScope.launch {
            val affinity = fishRepository.getAffinityLevel()
            val base = personaCardStore.resolveBase(affinity)
            personaCardStore.saveOverlay(
                base = base,
                identity = s.identity.trim(),
                traits = s.traits.trim(),
                speechStyle = s.speechStyle.trim(),
                catchphrases = s.catchphrases.trim(),
                backstory = s.backstory.trim(),
                taboos = s.taboos.trim(),
                proactiveMotivation = s.proactiveMotivation.trim(),
                temperature = s.temperature
            )
            personaCardStore.saveUserAlias(s.userAlias.trim().ifBlank { "你" })
            fishRepository.renameActiveFish(newName)
            _uiState.update { it.copy(savedMessage = "已保存", errorMessage = null) }
            reload()
        }
    }

    /**
     * 另存为自定义人格卡
     *
     * 把当前生效内容（底稿 + 覆盖层）快照进自定义包，并切过去。
     * 切过去后它就是新的底稿，与内置包互不影响。
     */
    fun saveAsCustomCard() {
        val s = _uiState.value
        val name = s.newCardName.trim()
        if (name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "给这张卡起个名字") }
            return
        }
        viewModelScope.launch {
            val affinity = fishRepository.getAffinityLevel()
            val base = personaCardStore.resolveBase(affinity)
            val state = personaCardStore.state()
            val id = personaCardStore.saveAsCustomCard(
                base = base,
                state = state,
                displayName = name,
                tagline = s.currentTagline
            )
            personaCardStore.selectCustomCard(id, affinity)
            _uiState.update {
                it.copy(newCardName = "", savedMessage = "已存为「$name」", errorMessage = null)
            }
            reload()
        }
    }

    /** 切到某张自定义卡 */
    fun selectCustomCard(id: String) {
        viewModelScope.launch {
            val affinity = fishRepository.getAffinityLevel()
            personaCardStore.selectCustomCard(id, affinity)
            val card = personaCardStore.customCard(id)
            _uiState.update {
                it.copy(
                    savedMessage = card?.let { c -> "已切到「${c.displayName}」" },
                    errorMessage = null
                )
            }
            reload()
        }
    }

    /** 切回内置卡（跟随好感度自动切） */
    fun useBuiltinPack() {
        viewModelScope.launch {
            val affinity = fishRepository.getAffinityLevel()
            personaCardStore.selectBuiltin(affinity)
            _uiState.update { it.copy(savedMessage = "已切回内置人格卡", errorMessage = null) }
            reload()
        }
    }

    /**
     * 切到某张**已解锁的内置卡**
     *
     * 2026-11-18 用户要求：「好感度是解锁新的人格卡，
     * 但是也可以切换回之前解锁过的」。
     *
     * 没解锁的卡不让切 —— 门槛没到就是没到。
     */
    fun selectBuiltinCard(id: String) {
        viewModelScope.launch {
            val affinity = fishRepository.getAffinityLevel()
            val ok = personaCardStore.selectBuiltinCard(id, affinity)
            _uiState.update {
                it.copy(
                    savedMessage = if (ok) "已切到这张人格卡" else null,
                    errorMessage = if (ok) null else "这张还没解锁呢",
                )
            }
            reload()
        }
    }

    /** 删掉一张自定义卡；若删的是当前底稿，回落到内置卡 */
    fun deleteCustomCard(id: String) {
        viewModelScope.launch {
            val affinity = fishRepository.getAffinityLevel()
            personaCardStore.deleteCustomCard(id, affinity)
            _uiState.update { it.copy(savedMessage = "已删除", errorMessage = null) }
            reload()
        }
    }

    /**
     * 恢复底稿原样
     *
     * 清掉覆盖层 —— 当前卡回到内置卡（或自定义卡）的原文。
     * 注意只清覆盖层，用户自己起的名字和称呼保留。
     */
    fun resetToBase() {
        viewModelScope.launch {
            personaCardStore.clearOverlay()
            _uiState.update { it.copy(savedMessage = "已恢复底稿原样", errorMessage = null) }
            reload()
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(savedMessage = null, errorMessage = null) }
    }
}
