package com.deepfocus.app.ui.fish

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepfocus.app.ai.FishStateEngine
import com.deepfocus.app.ai.LocalLineLibrary
import com.deepfocus.app.data.local.entity.AffinityAchievementEntity
import com.deepfocus.app.data.local.entity.FishCatalogEntity
import com.deepfocus.app.data.local.entity.FoodCatalogEntity
import com.deepfocus.app.data.local.entity.FoodInventoryEntity
import com.deepfocus.app.data.local.entity.FishStateEntity
import com.deepfocus.app.data.local.entity.UserFishEntity
import com.deepfocus.app.data.repository.FishRepository
import com.deepfocus.app.data.repository.FoodRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * 大肥鱼养成界面的 UI 状态
 */
data class FishUiState(
    /** 用户大肥鱼（null 表示尚未选择或正在加载） */
    val userFish: UserFishEntity? = null,
    /** 大肥鱼形态信息 */
    val breed: FishCatalogEntity? = null,
    /** 食物库存列表 */
    val foodInventory: List<FoodInventoryEntity> = emptyList(),
    /** 食物目录（用于显示名称和图标） */
    val foodCatalog: List<FoodCatalogEntity> = emptyList(),
    /** 成就列表 */
    val achievements: List<AffinityAchievementEntity> = emptyList(),
    /** 是否正在投喂动画中 */
    val isFeeding: Boolean = false,
    /** 投喂动画阶段：idle / eating / happy */
    val animationPhase: String = "idle",
    /** 最近解锁的成就（用于弹窗提示） */
    val newlyUnlockedAchievement: AffinityAchievementEntity? = null,
    /** 错误提示 */
    val errorMessage: String? = null,
    /** 是否正在加载初始数据 */
    val isLoading: Boolean = true,
    /** 投喂反馈触发序号（每次投喂 +1，UI 监听变化触发动画） */
    val feedBackTrigger: Int = 0,
    /** 大肥鱼生理状态：饱腹度 / 精力 / 睡眠 / 上次喂食时间 */
    val fishState: com.deepfocus.app.data.local.entity.FishStateEntity? = null,
    /** 大肥鱼的碎碎念（本地台词，零 token 成本） */
    val idleLine: String? = null,
    /**
     * 大肥鱼当前状态的**语义**描述（一枚小胶囊）。
     *
     * 只说人话（"饿了" / "睡得很香"），不暴露饱腹度这类内部数值 ——
     * 用户既看不懂也改不了，摆出来只是噪音。
     */
    val statusPill: String? = null
)

/**
 * 大肥鱼养成 ViewModel
 * 管理大肥鱼展示、投喂交互、好感度和成就
 */
@HiltViewModel
class FishViewModel @Inject constructor(
    private val fishRepository: FishRepository,
    private val foodRepository: FoodRepository,
    private val fishStateEngine: FishStateEngine,
    private val fishStateDao: com.deepfocus.app.data.local.dao.FishStateDao,
    private val moodStore: com.deepfocus.app.ai.MoodStore
) : ViewModel() {

    private val _state = MutableStateFlow(FishUiState())
    val state: StateFlow<FishUiState> = _state.asStateFlow()

    /** 数据收集协程引用，便于在重新加载时取消旧任务，避免重复收集器累积 */
    private var dataCollectionJob: Job? = null

    /**
     * 投喂原子锁，防止双击重复消耗食物
     * 修复 CV-1：原 isFeeding 在协程内（suspend 之后）才设置，双击会通过校验
     */
    private val feedingLock = AtomicBoolean(false)

    /** 等待进食视频播放完成后展示的解锁成就 */
    private var pendingUnlockedAchievements: List<AffinityAchievementEntity>? = null

    /** 进食动画安全超时任务，防止视频异常时动画永远卡住（20 秒安全网，正常视频 15 秒） */
    private var feedingTimeoutJob: Job? = null

    init {
        loadFishData()
        refreshIdleLine()
    }

    /**
     * 换一句碎碎念
     *
     * 大肥鱼在首页没事干时会发呆、想事情 —— 这时候说句话，
     * 用户就觉得"她一直在这儿"。台词走本地库，不花 token。
     */
    fun refreshIdleLine() {
        val fs = _state.value.fishState
        val scene = when {
            fs == null -> LocalLineLibrary.Scene.IDLE
            fs.isSleeping -> LocalLineLibrary.Scene.JUST_WOKE
            fs.satiety < 25 -> LocalLineLibrary.Scene.HUNGRY
            else -> LocalLineLibrary.Scene.IDLE
        }
        _state.update { it.copy(idleLine = LocalLineLibrary.line(scene)) }
    }

    /**
     * 把生理状态翻译成一句人话，给首页的状态胶囊用。
     *
     * **优先用她自己选的状态词**（2026-11-18 用户要求：
     * 「图3中吃的很饱的状态变更成大肥鱼自己选择的状态词汇」）。
     * 她通过 `set_mood` 工具自己定 —— 「吃撑了」「有点想你」这种话，
     * 比机器词表的「吃得很饱」有人味。
     *
     * 她还没开过口（没配 AI / 没调用过 / 调用失败）时，
     * 回落到机器词表 —— 胶囊不能是空的。
     */
    private fun statusPillOf(fs: FishStateEntity?): String? {
        if (fs == null) return null
        // 她自己说的优先
        moodStore.mood.value?.takeIf { it.isNotBlank() }?.let { return it }
        return when {
            fs.isSleeping -> "睡得很香"
            FishStateEngine.isHungry(fs, System.currentTimeMillis()) -> "饿了"
            fs.satiety > 85 -> "吃得很饱"
            else -> "精神不错"
        }
    }

    /**
     * 加载大肥鱼相关数据：用户大肥鱼、品种、食物库存、成就
     * 重新调用时会取消上一次的收集任务，防止累积多个并行 Flow 收集器
     */
    fun loadFishData() {
        dataCollectionJob?.cancel()
        dataCollectionJob = viewModelScope.launch {
            // 观察用户大肥鱼
            launch {
                fishRepository.observeUserFish().collect { fish ->
                    val breed = fish?.let { fishRepository.getBreedById(it.breedId) }
                    _state.update {
                        it.copy(
                            userFish = fish,
                            breed = breed,
                            isLoading = false
                        )
                    }
                }
            }
            // 观察食物库存
            launch {
                foodRepository.observeInventory().collect { inventory ->
                    _state.update { it.copy(foodInventory = inventory) }
                }
            }
            // 加载食物目录
            launch {
                val catalog = foodRepository.getAllFoods()
                _state.update { it.copy(foodCatalog = catalog) }
            }
            // 观察成就
            launch {
                fishRepository.observeAchievements().collect { achievements ->
                    _state.update { it.copy(achievements = achievements) }
                }
            }
            // 观察大肥鱼生理状态（饱腹度 / 睡眠）
            launch {
                fishStateDao.observe().collect { fs ->
                    _state.update {
                        it.copy(fishState = fs, statusPill = statusPillOf(fs))
                    }
                    // 状态变了，碎碎念也该换一句（饿了就说饿的）
                    refreshIdleLine()
                }
            }
            // 观察她自己选的状态词 —— 她一改，胶囊立刻跟着变
            launch {
                moodStore.mood.collect {
                    _state.update { s -> s.copy(statusPill = statusPillOf(s.fishState)) }
                }
            }
        }
    }

    /**
     * 投喂大肥鱼
     * 消耗一个食物，播放进食视频，增加好感度，检查成就
     * 视频播放完成后由 UI 回调结束动画，不再插入 happy 抖动阶段
     * @param foodId 食物 ID
     */
    fun feedFish(foodId: String) {
        // 修复 CV-1：用 AtomicBoolean 原子守门，防止双击在协程启动前通过 isFeeding 校验
        if (!feedingLock.compareAndSet(false, true)) return
        val currentState = _state.value
        // 防止动画期间重复触发
        if (currentState.isFeeding) {
            feedingLock.set(false)
            return
        }
        // 每日喂食限制：按大肥鱼独立计数，每只大肥鱼每日最多 5 次，超限提示"大肥鱼已经吃饱啦"
        if (!fishRepository.canFeedNow(currentState.userFish?.id)) {
            feedingLock.set(false)
            _state.update { it.copy(errorMessage = "大肥鱼已经吃饱啦，明天再喂吧~") }
            return
        }
        // 检查是否有该食物
        val inventoryItem = currentState.foodInventory.find { it.foodId == foodId }
        if (inventoryItem == null || inventoryItem.count <= 0) {
            feedingLock.set(false)
            _state.update { it.copy(errorMessage = "没有这种食物了") }
            return
        }
        // 检查是否有大肥鱼
        if (currentState.userFish == null) {
            feedingLock.set(false)
            _state.update { it.copy(errorMessage = "请先选择一条大肥鱼") }
            return
        }

        viewModelScope.launch {
            try {
                // 修复迭代5 Bug #3：消耗食物 + 投喂 + 解锁成就合并为单事务，
                // 任一步失败整体回滚，避免食物被消耗但投喂次数未增加的数据不一致
                val newlyUnlocked = fishRepository.feedFishWithFood(foodId, currentState.userFish?.id)

                // 联动大肥鱼生理状态：喂食提升饱腹度并记下喂食时间（决定它什么时候会喊饿）
                fishStateEngine.onFed(isBigRice = foodId == "大白饭")

                // 数据已持久化，开始进食动画
                // 视频播放完成后由 UI 调用 onFeedingVideoCompleted() 结束动画
                pendingUnlockedAchievements = newlyUnlocked
                _state.update {
                    it.copy(
                        isFeeding = true,
                        animationPhase = "eating",
                        errorMessage = null,
                        feedBackTrigger = it.feedBackTrigger + 1
                    )
                }
                // 安全超时：无论视频是否存在，最长 20 秒后自动结束动画
                // 留足时间让 eating 视频完整播放（视频实际 15 秒，20 秒作为安全网，5 秒余量）
                feedingTimeoutJob?.cancel()
                feedingTimeoutJob = viewModelScope.launch {
                    kotlinx.coroutines.delay(20000)
                    onFeedingVideoCompleted()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 协程取消必须重新抛出，保证结构化并发正确传播
                throw e
            } catch (e: Exception) {
                feedingTimeoutJob?.cancel()
                feedingTimeoutJob = null
                feedingLock.set(false)
                pendingUnlockedAchievements = null
                _state.update {
                    it.copy(
                        isFeeding = false,
                        animationPhase = "idle",
                        errorMessage = "投喂失败"
                    )
                }
            }
        }
    }

    /**
     * 进食视频播放完成后调用
     * 结束投喂动画、展示解锁成就、释放投喂锁
     */
    fun onFeedingVideoCompleted() {
        feedingTimeoutJob?.cancel()
        feedingTimeoutJob = null
        // 用锁做守卫，避免超时任务与视频完成回调重复执行
        if (!feedingLock.compareAndSet(true, false)) return
        val newlyUnlocked = pendingUnlockedAchievements
        pendingUnlockedAchievements = null
        _state.update {
            it.copy(
                isFeeding = false,
                animationPhase = "idle",
                newlyUnlockedAchievement = newlyUnlocked?.firstOrNull()
            )
        }
    }

    /**
     * 清除成就弹窗
     */
    fun clearAchievementPopup() {
        _state.update { it.copy(newlyUnlockedAchievement = null) }
    }

    /**
     * 清除错误提示
     */
    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    /**
     * 获取当前好感度阶段称号
     *
     * 统一使用「好感度点数」（affinityLevel）而非投喂次数 ——
     * 因为不同食物加的好感度不同，大白饭 +1、冻干 +3、罐头 +5，
     * 用投喂次数衡量关系会失真。
     * 档位与 [com.deepfocus.app.ai.FishMoodResolver.affinityTitle] 保持一致。
     */
    fun getAffinityTitle(affinity: Int): String {
        return com.deepfocus.app.ai.FishMoodResolver.affinityTitle(affinity)
    }

    /**
     * 获取下一个里程碑
     * 达到最高里程碑后固定返回该值，避免里程碑随投喂次数漂移
     * （否则会显示 "投喂 101/101 次" 这种无意义文案）
     */
    fun getNextMilestone(affinity: Int): Int {
        return com.deepfocus.app.ai.FishMoodResolver.nextMilestone(affinity)
    }

    /** 是否已达成最高里程碑（用于 UI 显示 "已满级"） */
    fun isMaxMilestone(affinity: Int): Boolean = affinity >= 200
}
