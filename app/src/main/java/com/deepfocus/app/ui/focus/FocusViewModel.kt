package com.deepfocus.app.ui.focus

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deepfocus.app.data.local.entity.FishCatalogEntity
import com.deepfocus.app.data.local.entity.FoodCatalogEntity
import com.deepfocus.app.data.local.entity.UserFishEntity
import com.deepfocus.app.ai.FishBrain
import com.deepfocus.app.ai.LocalLineLibrary
import com.deepfocus.app.ai.TravelService
import com.deepfocus.app.data.local.entity.TravelJournalEntity
import com.deepfocus.app.data.repository.FishRepository
import com.deepfocus.app.data.repository.FoodRepository
import com.deepfocus.app.data.repository.StatsRepositoryImpl
import com.deepfocus.app.pet.PetEventBus
import com.deepfocus.app.util.FocusSessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 专注会话 UI 状态
 */
data class FocusUiState(
    /** 计时是否正在运行 */
    val isRunning: Boolean = false,
    /** 已专注时长（毫秒） */
    val elapsedMillis: Long = 0L,
    /** 距离下次大白饭奖励的剩余毫秒（0 表示已达成待领取） */
    val millisToNextReward: Long = REWARD_INTERVAL_MS,
    /** 本次专注会话已获得的大白饭数量 */
    val rewardsEarned: Int = 0,
    /** 最近一次获得的大白饭（用于奖励动画），null 表示无新奖励 */
    val lastReward: FoodCatalogEntity? = null,
    /** 是否显示退出确认对话框 */
    val showExitConfirm: Boolean = false,
    /** 是否显示奖励动画 */
    val showRewardAnimation: Boolean = false,
    /** 用户大肥鱼（用于陪伴展示） */
    val userFish: UserFishEntity? = null,
    /** 大肥鱼形态信息 */
    val breed: FishCatalogEntity? = null,
    /** 她这趟去了哪（旅行中/已完成时非空） */
    val travelDestination: String? = null,
    /** 是否正在旅行 */
    val travelInProgress: Boolean = false,
    /** 刚写完的旅行日记（非空时弹出卡片） */
    val finishedJournal: TravelJournalEntity? = null,
    /** 大肥鱼在专注开始/结束时冒泡的一句话（本地台词，零 token 成本） */
    val companionLine: String? = null,
    /** 错误提示 */
    val errorMessage: String? = null
) {
    companion object {
        /** 每专注 30 分钟获得一份大白饭 */
        const val REWARD_INTERVAL_MS = 30 * 60 * 1000L
    }
}

/**
 * 专注模式 ViewModel
 *
 * 核心职责：
 * 1. 启动/停止正计时（每秒更新 elapsedMillis）
 * 2. 每满 30 分钟自动调用 FoodRepository.grantBigRice() 发放一份大白饭，触发奖励动画
 * 3. 调用 StatsRepositoryImpl 持久化 FocusSession（开始时 recordFocusSession，结束时 updateFocusSession）
 * 4. 通过 FocusSessionState 全局标志通知 DeepFocusService 进行防分心检测
 */
@HiltViewModel
class FocusViewModel @Inject constructor(
    private val foodRepository: FoodRepository,
    private val fishRepository: FishRepository,
    private val statsRepository: StatsRepositoryImpl,
    private val travelService: TravelService,
    private val fishBrain: FishBrain,
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(FocusUiState())
    val uiState: StateFlow<FocusUiState> = _uiState.asStateFlow()

    /** 计时循环 Job */
    private var timerJob: Job? = null
    /** 奖励动画关闭协程引用，新奖励触发前取消旧的，避免旧协程提前关闭新动画 */
    private var rewardAnimationJob: Job? = null
    /** recordFocusSession 协程引用，stopFocus 时 join 等待 sessionId 写入，避免孤儿记录 */
    private var recordSessionJob: Job? = null
    /** 赛博旅游协程：专注满 15 分钟后跑一趟 */
    private var travelJob: Job? = null

    /** 当前专注会话的数据库 ID（持久化用） */
    private var sessionId: Long = -1L

    /** 本次会话开始时间戳 */
    private var sessionStartTime: Long = 0L

    /**
     * 本实例持有的会话代号
     *
     * 用途：Activity 被重建后新旧两个 ViewModel 可能短暂共存，
     * 旧实例销毁时不能把新实例的会话停掉 —— 只有代号仍是自己那个才有权结束会话。
     */
    private var sessionGeneration: Long = 0L

    init {
        // 加载用户大肥鱼用于陪伴展示
        viewModelScope.launch {
            fishRepository.observeUserFish().collect { fish ->
                val breed = fish?.let { fishRepository.getBreedById(it.breedId) }
                _uiState.update {
                    it.copy(
                        userFish = fish,
                        breed = breed
                    )
                }
            }
        }
    }

    /**
     * 开始专注计时
     * - 持久化一条 FocusSession 到数据库
     * - 设置 FocusSessionState 全局标志（DeepFocusService 据此检测切屏）
     * - 启动每秒计时循环
     *
     * ## 会话接管
     *
     * 用户切到别的 App 被拉回时，系统可能重建 Activity → 新建 ViewModel →
     * 新的 `startFocus()` 会被调用。若无条件重开一段，用户的专注时长就被清零了。
     *
     * 所以：进程级 `FocusSessionState` 里已有活跃会话时，这里**接管**而不是重开 ——
     * 开始时间、数据库行 id、已发奖励数都沿用旧的，计时接着跑。
     */
    fun startFocus() {
        if (_uiState.value.isRunning) return

        // 接管已有会话（进程内 FocusSessionState 还活跃）
        if (FocusSessionState.isActive && FocusSessionState.startedAtMillis > 0L) {
            adoptRunningSession()
            return
        }

        sessionStartTime = System.currentTimeMillis()
        sessionGeneration = FocusSessionState.start()

        // 通知桌宠：专注开始，它该摆出认真干活的姿态并冒个泡
        PetEventBus.emit(PetEventBus.FocusStarted())

        // 大肥鱼本地冒泡一句（零 token 成本，不调模型）
        _uiState.update {
            it.copy(companionLine = LocalLineLibrary.line(LocalLineLibrary.Scene.FOCUS_START))
        }

        // 启动赛博旅游：专注满 15 分钟，她自己出门一趟
        scheduleTravel()

        // 持久化 FocusSession 到数据库（保存 Job 引用，stopFocus 时 join 等待写入完成）
        recordSessionJob = viewModelScope.launch {
            try {
                // 先把上次进程被杀留下的孤儿行补齐（endTime=null 会让时长统计失准）
                statsRepository.closeOrphanSessions()
                sessionId = statsRepository.recordFocusSession(
                    targetApp = "focus_mode", // 专注模式标记，区别于被拦截 APP
                    startTime = sessionStartTime,
                    endTime = null,
                    quizPassed = false,
                    durationSeconds = 0
                )
                FocusSessionState.setSessionRowId(sessionId)
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 协程取消必须重新抛出，保证结构化并发正确传播
                throw e
            } catch (e: Exception) {
                // 持久化失败不影响计时功能
            }
        }

        _uiState.update { it.copy(isRunning = true) }
        startTimerLoop()
    }

    /**
     * 接管进程内已有的专注会话
     *
     * 场景：专注中被拉回（或 Activity 被系统回收后重建），新 ViewModel 接手旧会话。
     *
     * 要点：
     * - 开始时间取全局状态，计时不归零
     * - 数据库行 id 取全局状态（拿不到就查未结束的行），结束时能正确回填
     * - 已发奖励数按已专注时长推算，不会因为接管而重复发大白饭
     * - 不再发 FocusStarted 事件、也不再冒泡 —— 桌宠已经知道你在专注了
     */
    private fun adoptRunningSession() {
        sessionStartTime = FocusSessionState.startedAtMillis
        sessionGeneration = FocusSessionState.claim()
        sessionId = FocusSessionState.sessionRowIdValue

        val elapsed = System.currentTimeMillis() - sessionStartTime
        val earned = (elapsed / FocusUiState.REWARD_INTERVAL_MS).toInt()

        _uiState.update {
            it.copy(
                isRunning = true,
                elapsedMillis = elapsed,
                rewardsEarned = earned,
                millisToNextReward = FocusUiState.REWARD_INTERVAL_MS -
                    (elapsed % FocusUiState.REWARD_INTERVAL_MS)
            )
        }
        startTimerLoop()

        // 全局状态里的行 id 可能还没写进去（插入协程尚未完成），补查一次
        if (sessionId <= 0L) {
            viewModelScope.launch {
                try {
                    val open = statsRepository.getOpenSession()
                    if (open != null && open.startTime == sessionStartTime) {
                        sessionId = open.id
                        FocusSessionState.setSessionRowId(open.id)
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 查不到就退化为「结束时无法回填」，不影响计时
                }
            }
        }

        // 旅行继续（新的 ViewModel 里没有旅行的协程）
        scheduleTravel()
    }

    /**
     * 计时循环：每秒更新 elapsedMillis 和 millisToNextReward
     * 每满 30 分钟自动发放一份大白饭
     */
    private fun startTimerLoop() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_uiState.value.isRunning) {
                // 会话已被别的实例接管 → 本循环立刻退出。
                // 否则旧实例会继续每秒覆盖全局累计时长，两个循环互相踩。
                if (FocusSessionState.currentGeneration != sessionGeneration) break

                val elapsed = System.currentTimeMillis() - sessionStartTime

                // 每秒更新计时显示和累计时长（奖励发放时也不中断计时更新，避免 1 秒冻结）
                _uiState.update {
                    it.copy(
                        elapsedMillis = elapsed,
                        millisToNextReward = FocusUiState.REWARD_INTERVAL_MS -
                            (elapsed % FocusUiState.REWARD_INTERVAL_MS)
                    )
                }
                FocusSessionState.updateAccumulated(elapsed)

                // 检查是否达成新奖励（每 30 分钟一次）
                val totalRewardsShouldHave = (elapsed / FocusUiState.REWARD_INTERVAL_MS).toInt()
                if (totalRewardsShouldHave > _uiState.value.rewardsEarned) {
                    grantReward()
                }

                delay(1000L)
            }
        }
    }

    /**
     * 发放一份大白饭并触发奖励动画
     */
    private suspend fun grantReward() {
        try {
            val food = foodRepository.grantBigRice()
            _uiState.update {
                it.copy(
                    rewardsEarned = it.rewardsEarned + 1,
                    lastReward = food,
                    showRewardAnimation = true
                )
            }
            // 桌宠庆祝一下：告诉它今天一共专注了多久，它自己会放到气泡上
            reportMilestoneToPet()
            // 3 秒后关闭奖励动画
            // 修复 B7：保存 Job 引用，新奖励触发前取消旧的，避免旧协程提前关闭新动画
            rewardAnimationJob?.cancel()
            rewardAnimationJob = viewModelScope.launch {
                delay(3000L)
                _uiState.update { it.copy(showRewardAnimation = false) }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 协程取消必须重新抛出，保证结构化并发正确传播
            throw e
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = "大白饭发放失败：${e.message}") }
        }
    }

    /**
     * 用户点击退出按钮 → 显示退出确认对话框
     * 对话框中显示距离下一份大白饭的剩余分钟数
     */
    fun requestExit() {
        _uiState.update { it.copy(showExitConfirm = true) }
    }
    /**
     * 通知桌宠：达成里程碑，庆祝一下
     *
     * 桌宠会自己去读今日总时长，所以这里只发事件，不传具体数字 ——
     * 免得两处各算一遍，对不上。
     */
    private suspend fun reportMilestoneToPet() {
        try {
            val todayMillis = statsRepository.getTodayFocusDurationMillis()
            val minutes = (todayMillis / 60_000L).toInt()
            if (minutes > 0) {
                PetEventBus.emit(PetEventBus.FocusMilestone(minutes))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // 庆祝失败不影响专注
        }
    }

    /**
     * 取消退出，继续专注
     */
    fun cancelExit() {
        _uiState.update { it.copy(showExitConfirm = false) }
    }

    /**
     * 确认退出，结束专注会话
     * - 停止计时
     * - 更新数据库 FocusSession 的结束时间和时长
     * - 清除 FocusSessionState 标志
     */
    fun confirmExit() {
        stopFocus()
        _uiState.update { it.copy(showExitConfirm = false) }
    }

    /**
     * 专注结束后给大肥鱼涨好感度
     *
     * 设计初衷：好感度是唯一养成主线，喂食 / 专注 / 聊天三条路都能涨。
     * 原来只有喂食能涨 —— 只专注不喂食的话好感度纹丝不动，专注毫无正反馈。
     */
    private fun grantFocusAffinity(durationSeconds: Int) {
        viewModelScope.launch {
            try {
                fishRepository.addAffinityByFocus(durationSeconds)
                // 专注也涨好感度，所以成就解锁判定也得在这里跑一次
                fishRepository.checkAndUnlockAchievements()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // 涨好感度失败不影响专注结算
            }
        }
    }

    /**
     * 把这次专注记进大肥鱼的记忆
     *
     * 设计初衷：「AI 能知道今天专注了多久」不只是回答当下的问题，
     * 还要让大肥鱼**记得**你们一起熬过的时间。
     */
    private fun recordFocusToMemory(durationSeconds: Int) {
        viewModelScope.launch {
            try {
                fishBrain.rememberFocusSession(durationSeconds)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // 记记忆失败不影响专注结算
            }
        }
    }

    /**
     * 停止专注计时（内部调用）
     */
    private fun stopFocus() {
        timerJob?.cancel()
        timerJob = null
        // 停止计时；旅行协程一并取消 —— 没跑完就不跑了（省 token）
        travelJob?.cancel()
        travelJob = null
        FocusSessionState.stopIfCurrent(sessionGeneration)

        val endTime = System.currentTimeMillis()
        val durationSeconds = ((endTime - sessionStartTime) / 1000).toInt()

        // 通知桌宠：这一趟结束了，它该报一下时长、回到常态
        PetEventBus.emit(PetEventBus.FocusEnded(durationSeconds * 1000L))

        // 专注涨好感度 + 记进大肥鱼的记忆
        // 设计初衷：好感度是唯一养成主线，靠「喂食、专注、聊天」三条路增长。
        // 原来只有喂食能涨 —— 光专注不涨，专注就失去了正反馈。
        grantFocusAffinity(durationSeconds)
        recordFocusToMemory(durationSeconds)

        // 更新数据库 FocusSession
        // 修复 FV-2：原代码在协程内才重置 sessionId，若用户快速"退出→再开始"，
        // startFocus 的协程会先设置新 sessionId，然后本协程的 sessionId=-1L 会覆盖它，
        // 导致新会话无法被关闭更新，统计数据丢失。改为立即重置成员变量。
        //
        // 修复迭代5 Bug #5：原代码若用户在 recordFocusSession 协程完成前调用 stopFocus，
        // sessionId 仍是 -1L，updateFocusSession 不会被调用，但 recordSessionJob 随后
        // 完成会写入新 sessionId，形成孤儿记录（endTime=null, duration=0）。
        // 改为 join 等待 recordSessionJob 完成后再判断 sessionId。
        val pendingJob = recordSessionJob
        recordSessionJob = null
        viewModelScope.launch {
            // 等待 recordFocusSession 完成，确保 sessionId 已写入（或失败保持 -1L）
            pendingJob?.join()
            if (sessionId > 0) {
                val sessionIdToClose = sessionId
                sessionId = -1L  // 立即重置，防止被 startFocus 的新值覆盖
                try {
                    statsRepository.updateFocusSession(
                        id = sessionIdToClose,
                        endTime = endTime,
                        duration = durationSeconds
                    )
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // 协程取消必须重新抛出，保证结构化并发正确传播
                    throw e
                } catch (e: Exception) {
                    // 持久化失败不影响 UI
                }
            }
        }

        _uiState.update {
            it.copy(
                isRunning = false,
                elapsedMillis = 0L,
                millisToNextReward = FocusUiState.REWARD_INTERVAL_MS,
                rewardsEarned = 0,
                travelInProgress = false,
                travelDestination = null,
                companionLine = LocalLineLibrary.line(LocalLineLibrary.Scene.FOCUS_END)
            )
        }
    }

    /**
     * 安排一次赛博旅游
     *
     * ## 为什么是 15 分钟门槛
     *
     * 一次旅行要跑三次 LLM（选地方 / 编故事 / 写日记），
     * 十几分钟的专注不值得为它花这笔钱，也还没到「值得纪念」的长度。
     *
     * ## 为什么一次专注只走一趟
     *
     * 60 分钟的专注也只去一个地方 —— 一个完整的故事，
     * 比三次走马观花更像「她真的去了」。
     *
     * 防重复：本段专注的起始时间写进 prefs，
     * Activity 重建后新 ViewModel 接管，不会因为「已经过了 15 分钟」
     * 就再跑一趟（那会白烧一次 token）。
     */
    private fun scheduleTravel() {
        if (sessionStartTime <= 0L) return
        // 已经为这一段专注跑过了 → 不再重复
        if (travelDoneFor(sessionStartTime)) {
            return
        }
        // 已经在跑 → 不重入
        if (travelJob?.isActive == true) return

        travelJob = viewModelScope.launch {
            val elapsed = System.currentTimeMillis() - sessionStartTime
            val remaining = TRAVEL_THRESHOLD_MS - elapsed
            if (remaining > 0) {
                delay(remaining)
            }
            // 到点再确认一次：会话还活着吗？没被停掉才出发
            if (!_uiState.value.isRunning) return@launch

            runTravel()
        }
    }

    /**
     * 真的跑一趟旅行
     *
     * 三次短调用跑完，把日记写进游历本，然后弹卡片。
     * 失败**静默** —— 网络不通、没配 API、模型抽风，
     * 都不该影响用户专注（更不能打断计时）。
     */
    private suspend fun runTravel() {
        // 先上锁：即使这次失败也不再重试，避免反复烧 token
        markTravelDone(sessionStartTime)

        _uiState.update { it.copy(travelInProgress = true) }

        try {
            val journal = travelService.travelNow()

            _uiState.update {
                it.copy(
                    travelInProgress = false,
                    travelDestination = journal.destination,
                    finishedJournal = journal
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.update { it.copy(travelInProgress = false) }
        }
    }

    /** 关掉旅行日记卡片 */
    fun dismissJournal() {
        _uiState.update { it.copy(finishedJournal = null) }
    }

    private fun travelPrefs() = getApplication<Application>()
        .getSharedPreferences("deep_focus_travel", android.content.Context.MODE_PRIVATE)

    private fun travelDoneFor(sessionStart: Long): Boolean =
        travelPrefs().getLong(KEY_LAST_TRAVEL_SESSION, 0L) == sessionStart

    private fun markTravelDone(sessionStart: Long) {
        travelPrefs().edit().putLong(KEY_LAST_TRAVEL_SESSION, sessionStart).apply()
    }

    companion object {
        /** 旅行触发门槛：专注满 15 分钟才出发 */
        const val TRAVEL_THRESHOLD_MS = 15 * 60 * 1000L

        /** 记录「这段专注已经旅行过了」的键（防重建后重复触发） */
        private const val KEY_LAST_TRAVEL_SESSION = "last_travel_session"
    }

    /** 清除错误提示 */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * ViewModel 销毁时确保停止计时和清除全局标志
     * 防止用户直接杀进程导致 FocusSessionState 残留 true
     */
    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        rewardAnimationJob?.cancel()
        travelJob?.cancel()
        // 只有代号仍是自己那个才结束全局会话。
        // 场景：专注中被拉回 → 系统重建 Activity → 新 ViewModel 接管并换了代号 →
        // 旧 ViewModel 此时被销毁，若无脑 stop() 会把新会话的全局标志一起清掉，
        // 导致锁机拉回失效、计时累加归零。
        FocusSessionState.stopIfCurrent(sessionGeneration)
    }
}
