package com.deepfocus.app.pet

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.deepfocus.app.DeepFocusApp
import com.deepfocus.app.MainActivity
import com.deepfocus.app.R
import com.deepfocus.app.ai.FishMoodResolver
import com.deepfocus.app.ai.FishStateEngine
import com.deepfocus.app.data.local.entity.FishStateEntity
import com.deepfocus.app.util.FocusSessionState
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 桌宠前台服务
 *
 * 为什么必须用前台服务：
 * Android 8.0+ 后台应用不能显示悬浮窗（`TYPE_APPLICATION_OVERLAY` 会抛
 * `WindowManager$BadTokenException`）。只有前台服务才有悬浮窗资格。
 *
 * 为什么用 `SPECIAL_USE` 类型：
 * Android 14+（API 34）起前台服务必须声明具体类型。
 * 桌宠既不是数据同步也不是位置服务，`SPECIAL_USE` 是正确的归类。
 *
 * Android 15 的额外限制：
 * 起前台服务前必须已经有一个可见的叠加层或 App 可见。
 * 所以本服务**只能从 Activity（用户可见）里启动**，
 * 不能靠 BootReceiver 后台拉起 —— 那会抛 `ForegroundServiceStartNotAllowedException`。
 * 开机自启改由 BootReceiver 拉起一个透明 Activity 来中转。
 */
class DesktopPetService : Service() {

    companion object {
        private const val TAG = "DesktopPetService"

        /** 前台通知 ID */
        const val NOTIFICATION_ID = 1002

        /** SharedPreferences 名 */
        private const val PREFS = "deep_focus_pet"

        /** 是否启用桌宠 */
        private const val KEY_ENABLED = "enabled"

        /** 桌宠尺寸（dp） */
        private const val KEY_SIZE_DP = "size_dp"

        /** 桌宠 X 坐标 */
        private const val KEY_POS_X = "pos_x"

        /** 桌宠 Y 坐标 */
        private const val KEY_POS_Y = "pos_y"

        /** 渲染帧间隔（ms）—— 约 30fps，桌宠不需要 60fps */
        private const val FRAME_INTERVAL_MS = 33L

        /** 状态轮询间隔（ms）—— 每 3 秒看一眼大肥鱼的状态 */
        private const val STATE_POLL_INTERVAL_MS = 3_000L

        /** AI 台词最短长度：太短不像人话 */
        private const val MIN_SPEECH_LEN = 2

        /** AI 台词最长长度：气泡装不下更长的话 */
        private const val MAX_SPEECH_LEN = 120

        /** Intent Action */
        const val ACTION_TOGGLE = "com.deepfocus.app.action.TOGGLE_PET"

        /** 修改桌宠尺寸（设置页用，不重建窗口） */
        const val ACTION_SET_SIZE = "com.deepfocus.app.action.SET_PET_SIZE"

        /** 尺寸档位（dp） */
        const val EXTRA_SIZE_DP = "size_dp"

        /**
         * 启动桌宠服务
         *
         * 注意：必须在用户可见的上下文中调用（Activity），
         * 否则 Android 12+ 会拒绝启动前台服务。
         */
        fun start(context: Context) {
            try {
                val intent = Intent(context, DesktopPetService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "启动桌宠服务失败", e)
            }
        }

        /** 停止桌宠服务 */
        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, DesktopPetService::class.java))
            } catch (e: Exception) {
                // 忽略
            }
        }

        /**
         * 实时修改桌宠尺寸
         *
         * 服务没在跑时什么都不做 —— 用户下次开桌宠会从 prefs 读新尺寸。
         */
        fun setSize(context: Context, sizeDp: Int) {
            try {
                val intent = Intent(context, DesktopPetService::class.java)
                    .setAction(ACTION_SET_SIZE)
                    .putExtra(EXTRA_SIZE_DP, sizeDp)
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "修改桌宠尺寸失败", e)
            }
        }
    }

    /**
     * 服务自己的协程作用域
     *
     * 用 [Dispatchers.Default]（后台线程）：轮询状态、解码动画这些活儿
     * 不该占着主线程。
     *
     * **碰 View / 动画的调用不用自己切线程** ——
     * `PetWindowController` 内部统一兜底切回主线程
     * （`View.animate()` 只能在 Looper 线程跑，
     * 后台线程调它会抛 `Animators may only be run on Looper threads`）。
     */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * 渲染循环专用作用域 —— **必须跑在主线程**
     *
     * 桌宠的每一帧都要动 View 和悬浮窗：
     * - `WindowManager.updateViewLayout()` 只能在主线程调，
     *   后台线程调会抛 `CalledFromWrongThreadException`
     * - `View` 的属性改动、`Drawable.setCallback()`、`Animator` 的启停
     *   也都要求 Looper 线程
     *
     * 之前这个循环挂在 [serviceScope]（`Dispatchers.Default`）上，
     * 后果有两个：
     * 1. 贴边静置 12 秒后进入隐藏态，那里的 `updateViewLayout` 没有兜底
     *    → **直接崩 App**
     * 2. 每帧的 `updatePosition()` 里同样的调用被 `catch` 吞掉
     *    → **桌宠在屏幕上不动**（物理在跑，窗口不跟）
     *
     * 状态轮询、解码那些活儿仍然留在后台的 [serviceScope]。
     */
    private val renderScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: PetWindowController? = null
    private var menuController: PetMenuController? = null
    private var renderJob: Job? = null
    private var stateJob: Job? = null

    /** 订阅全局事件（专注开始/结束、AI 台词） */
    private var eventJob: Job? = null

    /** 订阅最新一条大肥鱼台词，把 AI 说的话顶到气泡上 */
    private var aiSpeechJob: Job? = null

    /** 桌宠是否被用户藏起来了 */
    private var petHidden = false

    /** 上一轮判定「当前在系统桌面」的结果 */
    private var homeScreenAllowed = true

    /**
     * 最近一次已知的窗口位置
     *
     * 窗口被整块移除后 `physics` 还在，但重建时用这个更稳
     * （也避免依赖 controller 内部状态）。
     */
    private var lastKnownPosition: Pair<Int, Int>? = null

    /** 上次显示的姿态，避免每 3 秒重复切贴图 */
    private var currentPose: PetSpriteLibrary.Pose? = null

    /** 已经播报过的 AI 消息 id，避免同一条反复弹气泡 */
    private var lastSpokenMessageId: Long = -1L

    /**
     * 互动引擎
     *
     * 把 109 个动作按「玩法」组织起来：
     * 喂食、摸头、逗猫棒、甩飞、送礼、思考、陪伴、节日、恶作剧。
     *
     * 取的是**全局共享单例** —— 首页大肥鱼和桌宠共用一份「最近播过」记忆。
     */
    private val playEngine: PetPlayEngine by lazy {
        EntryPointAccessors.fromApplication(
            applicationContext,
            PetPlayEngineEntryPoint::class.java
        ).petPlayEngine()
    }

    /** 是否处于睡觉状态 */
    private var sleeping = false

    /** 桌宠尺寸（dp）—— 窗口被移除后重建要用 */
    private var petSizeDp = PetWindowController.DEFAULT_SIZE_DP

    /** 是否由用户主动开启（决定要不要显示通知） */
    private var userEnabled = true

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification()

        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        userEnabled = prefs.getBoolean(KEY_ENABLED, false)
        val sizeDp = prefs.getInt(KEY_SIZE_DP, PetWindowController.DEFAULT_SIZE_DP)
        petSizeDp = sizeDp
        val savedX = prefs.getInt(KEY_POS_X, -1).takeIf { it >= 0 }
        val savedY = prefs.getInt(KEY_POS_Y, -1).takeIf { it >= 0 }

        val ctl = PetWindowController(this, object : PetWindowController.Listener {
            override fun onPetClicked() {
                // 摸头：只给动作反馈，不冒随机台词、也不打开 App
                // （用户 2026-11-22：点击误触进 App 太频繁）
                playEngine.onUserInteraction()
                playEngine.onPet()?.let { playAction(it) }
            }

            override fun onPetDoubleClicked() {
                // 投喂：吃的动作全在动作库里，随机挑一个
                playEngine.onUserInteraction()
                playEngine.onFeed()?.let { playAction(it) }
            }

            override fun onPetLongPressed() {
                showMenu()
            }

            override fun onPetDragged(x: Int, y: Int) {
                savePosition(x, y)
            }

            override fun onPetStateChanged(state: PetWindowController.PetState) {
                Log.d(TAG, "状态切换：$state")
            }
        })

        controller = ctl
        menuController = PetMenuController(this, object : PetMenuController.Listener {
            override fun onMenuToggleSleep() {
                sleeping = !sleeping
                controller?.setSleeping(sleeping)
                currentPose = if (sleeping) {
                    PetSpriteLibrary.Pose.SLEEPING
                } else {
                    PetSpriteLibrary.Pose.IDLE
                }
                // 睡/醒都用动作库里的对应动作
                val action = if (sleeping) playEngine.onSleep() else playEngine.onWake()
                action?.let { playAction(it) }
            }

            override fun onMenuPlay() {
                // 「逗它玩」：随机挑一个动作。
                // 优先挑「有表现力」的 —— 魔术、烟花、玩耍这些，
                // 观感上才像被逗到了。
                playEngine.onUserInteraction()
                val action = playEngine.pick(
                    setOf(
                        PetActionTag.PLAY,
                        PetActionTag.TRICK,
                        PetActionTag.MOOD,
                        PetActionTag.FEED
                    )
                )
                // 动作库为空时不冒台词 —— 桌宠只在 agent 主动输出时说话。
                if (action == null) return
                playAction(action)
            }

            override fun onMenuGoHome() {
                val metrics = resources.displayMetrics
                controller?.moveTo(
                    metrics.widthPixels - petSizePx() - dpToPx(12),
                    dpToPx(120)
                )
                savePosition(
                    metrics.widthPixels - petSizePx() - dpToPx(12),
                    dpToPx(120)
                )
            }

            override fun onMenuFlip() {
                val ctl = controller ?: return
                ctl.flipFacing()
            }

            override fun onMenuToggleVisible() {
                petHidden = !petHidden
                // 不能只是 View.GONE —— 悬浮窗还挂在 WindowManager 上，
                // 仍然占着一层 overlay。用户要求「关掉就真的不显示」，
                // 所以要整块移除 / 重新加回窗口。
                applyPetWindowVisibility()
            }

            override fun onMenuQuit() {
                getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putBoolean(KEY_ENABLED, false)
                    .apply()
                stopSelf()
            }
        })

        // 只在系统桌面时才把窗口挂上去。
        // 用户通常是在设置页里打开桌宠的 —— 那时前台是我们自己的 App，
        // 直接挂窗口会「刚开就盖在自己脸上」。
        // 这里先不算，等下一次轮询（≤3 秒）回到桌面再出现。
        homeScreenAllowed = HomeScreenGate.isOnHomeScreen(this) ?: true
        if (homeScreenAllowed) {
            try {
                ctl.show(sizeDp, savedX, savedY)
            } catch (e: Exception) {
                Log.e(TAG, "显示悬浮窗失败", e)
                stopSelf()
                return
            }
            lastKnownPosition = ctl.currentPosition()
        }

        startRenderLoop()
        startStateWatch()
        startEventWatch()
        startAiSpeechWatch()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SET_SIZE -> {
                val dp = intent.getIntExtra(EXTRA_SIZE_DP, petSizeDp)
                    .coerceIn(PetWindowController.MIN_SIZE_DP, PetWindowController.MAX_SIZE_DP)
                petSizeDp = dp
                controller?.resize(dp)
                return START_STICKY
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        renderJob?.cancel()
        stateJob?.cancel()
        eventJob?.cancel()
        aiSpeechJob?.cancel()
        serviceScope.cancel()
        renderScope.cancel()
        controller?.hide()
        controller = null
        PetSpriteLibrary.clear()
        super.onDestroy()
    }

    /**
     * 渲染循环
     */
    private fun startRenderLoop() {
        renderJob?.cancel()
        renderJob = renderScope.launch {
            while (isActive) {
                controller?.let { ctl ->
                    // 窗口没挂着（在别的应用里 / 用户隐藏了）就别算了，
                    // 省点电。重新显示时位置从 lastKnownPosition 复位。
                    if (!ctl.isShowing()) return@let
                    ctl.tick()
                }
                delay(FRAME_INTERVAL_MS)
            }
        }
    }

    /**
     * 状态监听：让桌宠跟着大肥鱼的状态变化
     */
    private fun startStateWatch() {
        stateJob?.cancel()
        stateJob = serviceScope.launch {
            while (isActive) {
                try {
                    // 先决定「这一轮该不该有窗口」——
                    // 只在系统桌面时显示，进 App / 别的应用立刻收起来
                    evaluateHomeScreenGate()
                    val state = loadFishState()
                    stateJobAffinity = currentAffinity()
                    applyState(state)
                    checkAmbientPlay()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 忽略单次失败
                }
                delay(STATE_POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * 判断当前要不要让桌宠露脸，并真的把窗口加进来 / 拿下去
     *
     * 规则（用户 2026-11-19 明确要求）：
     * 1. 用户手动隐藏（菜单里的「隐藏」）→ 不显示
     * 2. 当前前台不是系统桌面（进了我们的 App、微信、设置……）→ 不显示
     * 3. 只在系统桌面 → 显示
     *
     * 窗口是**整块移除**而不是 `View.GONE` —— 只有这样才真的
     * 「不干扰其它应用」。位置和尺寸都记着，回到桌面时原地出现。
     */
    private fun evaluateHomeScreenGate() {
        val onHome = HomeScreenGate.isOnHomeScreen(this)
        // null = 没有 UsageStats 权限，判断不了。
        // 这时退回「保守显示」——否则用户明明开了桌宠却什么都看不见，
        // 比偶尔盖住其它应用更让人困惑。
        val allowed = onHome ?: true
        homeScreenAllowed = allowed
        applyPetWindowVisibility()
    }

    /**
     * 把「想不想显示」落到窗口上
     *
     * 幂等：已经在这个状态就什么都不做
     * （`show()` 在 rootView 非空时直接 return，`hide()` 在空时直接 return）。
     */
    private fun applyPetWindowVisibility() {
        val ctl = controller ?: return
        val shouldShow = homeScreenAllowed && !petHidden && userEnabled
        if (!shouldShow) {
            // 收起来前记下当前位置 —— 桌宠散步是直接改坐标的，
            // 不记的话下次现身会跳回上一次拖拽的位置。
            if (ctl.isShowing()) lastKnownPosition = ctl.currentPosition()
            ctl.hideSafely()
            return
        }
        if (ctl.isShowing()) return

        val saved = lastKnownPosition
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val sx = saved?.first
            ?: prefs.getInt(KEY_POS_X, -1).takeIf { it >= 0 }
        val sy = saved?.second
            ?: prefs.getInt(KEY_POS_Y, -1).takeIf { it >= 0 }
        // 这里跑在 Dispatchers.Default 上，不能直接动 WindowManager
        ctl.showSafely(petSizeDp, sx, sy)
        // 窗口被整块移除后重建的是一个全新的 PetSpriteView（默认空白）。
        // 服务层的 currentPose 没变，setPose 的「同姿态跳过」会挡住下发，
        // 结果就是回到桌面时窗口在、但里面什么都没有 —— 这里强制补一次姿态。
        currentPose?.let { ctl.setPose(it) }
    }

    /**
     * 订阅全局事件
     *
     * 桌宠不主动 polling 谁在做什么，而是被事件驱动：
     * - 专注开始 → 挺身而出，摆出认真工作的姿态
     * - 专注结束 → 总结一句，达成里程碑就庆祝
     * - 有人说句话 → 直接显示
     */
    private fun startEventWatch() {
        eventJob?.cancel()
        eventJob = serviceScope.launch {
            PetEventBus.events.collectLatest { event ->
                try {
                    when (event) {
                        is PetEventBus.FocusStarted -> onFocusStarted(event)
                        is PetEventBus.FocusEnded -> onFocusEnded(event)
                        is PetEventBus.FocusMilestone -> onFocusMilestone(event)
                        is PetEventBus.Say -> onSay(event)
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "处理桌宠事件失败", e)
                }
            }
        }
    }

    /**
     * 订阅最新一条大肥鱼台词
     *
     * 用户在 App 内跟大肥鱼聊天、或它主动说话时，
     * 桌宠气泡会同步显示 —— 不用打开 App 也能看到它说了什么。
     */
    private fun startAiSpeechWatch() {
        aiSpeechJob?.cancel()
        aiSpeechJob = serviceScope.launch {
            try {
                com.deepfocus.app.data.local.AppDatabase.getInstance(this@DesktopPetService)
                    .chatDao()
                    .observeLatestFishMessage()
                    .collectLatest { msg ->
                        if (msg == null) return@collectLatest
                        if (msg.id == lastSpokenMessageId) return@collectLatest
                        lastSpokenMessageId = msg.id
                        val text = msg.content.trim()
                        if (text.length < MIN_SPEECH_LEN) return@collectLatest
                        showAiSpeech(text)
                        // 桌宠配合演一个动作 —— 说的话和样子是一回事
                        playActionForSpeech(text)
                    }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "订阅 AI 台词失败", e)
            }
        }
    }

    /** 显示 AI 的台词（超长截断） */
    private fun showAiSpeech(text: String) {
        val ctl = controller ?: return
        val trimmed = if (text.length > MAX_SPEECH_LEN) {
            text.take(MAX_SPEECH_LEN - 1) + "…"
        } else {
            text
        }
        // controller 内部会把 View 操作切回主线程
        ctl.showBubble(trimmed)
    }

    /**
     * AI 说话时，桌宠配合演一个动作
     *
     * 让「说的话」和「桌宠的样子」是一回事：
     * AI 撒了个娇 → 桌宠也害羞；
     * AI 在认真思考 → 桌宠跟着托腮冒泡。
     *
     * @param text AI 说的话（用来判断情绪）
     * @param actionId 指定的动作（可选，优先用）
     */
    private fun playActionForSpeech(text: String, actionId: String? = null) {
        // 指定的动作优先
        val action = actionId?.let { PetActionLibrary.byId(it) }
            ?: pickActionByMood(text)
        action ?: return
        // controller 内部会把 View 操作切回主线程
        playAction(action)
    }

    /**
     * 根据话里的情绪词挑一个动作
     *
     * 词表刻意短 —— 命中不了就交给随机，不硬凑。
     */
    private fun pickActionByMood(text: String): PetAction? {
        val rules: List<Pair<String, Set<String>>> = listOf(
            "害羞" to setOf(PetActionTag.MOOD),
            "开心" to setOf(PetActionTag.MOOD),
            "生气" to setOf(PetActionTag.MOOD),
            "困" to setOf(PetActionTag.SLEEP)
        )
        for ((keyword, tags) in rules) {
            if (text.contains(keyword)) {
                return playEngine.pick(tags)
            }
        }
        // 提到「思考」「想一想」这类 → 思考动作
        if (text.contains("想") || text.contains("思考")) {
            return playEngine.pick(setOf(PetActionTag.THINK))
        }
        return null
    }

    /** 专注开始：摆出认真干活的姿态 */
    private fun onFocusStarted(event: PetEventBus.FocusStarted) {
        val ctl = controller ?: return
        sleeping = false
        ctl.setSleeping(false)
        setPose(PetSpriteLibrary.Pose.WORKING)
        // 换上工作的动作（写代码 / 归档 / 忙个不停）
        playEngine.onWorking()?.let { playAction(it) }
        val line = if (event.plannedMinutes > 0) {
            "好，${event.plannedMinutes} 分钟，我们开始。"
        } else {
            "好，开始吧，我陪着你。"
        }
        ctl.showBubble(line)
    }

    /** 专注结束：报一下这次的时长 */
    private fun onFocusEnded(event: PetEventBus.FocusEnded) {
        val ctl = controller ?: return
        val mins = event.durationMillis / 60_000
        val line = if (mins >= 1) {
            "这趟专注了 $mins 分钟，辛苦啦。"
        } else {
            "回来啦，坐下歇会儿。"
        }
        ctl.showBubble(line)
        setPose(PetSpriteLibrary.Pose.IDLE)
        // 伸个懒腰 —— 专注结束的动作反馈
        playEngine.onPet()?.let { playAction(it) }
    }

    /** 专注里程碑：庆祝一下 */
    private fun onFocusMilestone(event: PetEventBus.FocusMilestone) {
        val ctl = controller ?: return
        setPose(PetSpriteLibrary.Pose.CELEBRATE)
        // 放烟花 / 雀跃 —— 动作库里现成的庆祝动作
        playEngine.pick(setOf(PetActionTag.MOOD, PetActionTag.TRICK))
            ?.let { playAction(it) }
        ctl.showBubble("今天已经专注 $event.totalMinutesToday 分钟啦，厉害！")
        // 庆祝完回到正常姿态
        serviceScope.launch {
            delay(6_000)
            if (currentPose == PetSpriteLibrary.Pose.CELEBRATE) {
                setPose(PetSpriteLibrary.Pose.IDLE)
            }
        }
    }

    /** 处理「说句话」事件 */
    private fun onSay(event: PetEventBus.Say) {
        val ctl = controller ?: return
        val text = event.text.trim()
        if (text.isEmpty()) return
        ctl.showBubble(text)
        event.pose?.let { setPose(it) }
        event.actionId?.let { id ->
            PetActionLibrary.byId(id)?.let { playAction(it) }
        }
    }

    /** 统一切姿态（避免与状态轮询打架） */
    private fun setPose(pose: PetSpriteLibrary.Pose) {
        if (pose == currentPose) return
        currentPose = pose
        controller?.setPose(pose)
    }

    /**
     * 播一个一次性动作
     *
     * 动作播完自动回到当前姿态 —— 互动不应该改变桌宠的常态。
     */
    private fun playAction(action: PetAction) {
        controller?.playAction(action)
    }

    /**
     * 读取大肥鱼状态
     */
    private suspend fun loadFishState(): FishStateEntity? {
        return try {
            com.deepfocus.app.data.local.AppDatabase.getInstance(this)
                .fishStateDao().get()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 根据状态切换桌宠姿态
     *
     * 这是 DeepFocus 相对 dsh-pet 的独有价值：
     * 桌宠读的是同一份 FishStateEntity，
     * 所以大肥鱼在 App 里挨饿，桌宠也会露出饿相。
     */
    private fun applyState(state: FishStateEntity?) {
        val ctl = controller ?: return
        if (state == null) return

        // 睡眠状态优先
        val isSleeping = state.isSleeping
        if (isSleeping != sleeping) {
            sleeping = isSleeping
            ctl.setSleeping(isSleeping)
            currentPose = if (isSleeping) PetSpriteLibrary.Pose.SLEEPING else PetSpriteLibrary.Pose.IDLE
            return
        }
        if (isSleeping) return

        val affinity = stateJobAffinity
        // 饿的判定只看距上次喂食超过 24 小时，与饱腹度数值无关
        val hungry = FishStateEngine.isHungry(state, System.currentTimeMillis())
        val mood = FishMoodResolver.resolve(
            affinity = affinity,
            satiety = state.satiety,
            isHungry = hungry,
            isSleeping = false
        )

        val pose = when {
            hungry -> PetSpriteLibrary.Pose.HUNGRY
            mood.tier == com.deepfocus.app.ai.FishMood.Tier.WANT_FOCUS -> PetSpriteLibrary.Pose.SLACKING
            affinity >= FishMoodResolver.AFFINITY_CLOSE -> PetSpriteLibrary.Pose.PROUD
            else -> PetSpriteLibrary.Pose.IDLE
        }

        if (pose != currentPose) {
            setPose(pose)
        }
    }

    /**
     * 周期性玩法检查
     *
     * 这是「不用用户动手」时桌宠自己的生活：
     * - 专注中 → 换上工作的动作（写代码、归档、忙个不停）
     * - 长时间没人理 → 自己找点事做（看书、发呆、碎碎念）
     * - 深夜 → 去睡觉
     * - 节日 → 放个烟花、堆个雪人
     * - 极低概率 → 来个惊喜（孔明灯、魔术、凭空生花）
     *
     * 频率：每 30 秒查一次，靠引擎内部的冷却控制实际触发。
     */
    private suspend fun checkAmbientPlay() {
        val ctl = controller ?: return
        if (sleeping) return

        // 专注中：换上工作的动作
        if (FocusSessionState.isActive) {
            if (playEngine.currentAction?.tags?.contains(PetActionTag.WORK) != true) {
                playEngine.onWorking()?.let { playAction(it) }
            }
            return
        }

        // 深夜：该睡了
        if (playEngine.isNightTime()) {
            playEngine.onSleep()?.let { action ->
                playAction(action)
                sleeping = true
                ctl.setSleeping(true)
            }
            return
        }

        // 节日：应景动作，一天一次
        playEngine.onFestival()?.let { playAction(it) }

        // 长时间没人理：自己找点事做
        val idle = playEngine.idleMillis()
        if (idle > 5 * 60 * 1000L) {
            playEngine.onIdle(idle)?.let { playAction(it) }
            return
        }

        // 惊喜：极低概率
        playEngine.onTrick()?.let { playAction(it) }
    }

    /** 缓存的好感度（在状态轮询里刷新，避免在非协程里调 suspend 函数） */
    private var stateJobAffinity = 0

    /**
     * 读取当前好感度
     */
    private suspend fun currentAffinity(): Int {
        return try {
            val db = com.deepfocus.app.data.local.AppDatabase.getInstance(this)
            db.userFishDao().getUserFish()?.affinityLevel ?: 0
        } catch (e: Exception) {
            0
        }
    }

    /**
     * 显示长按菜单
     */
    private fun showMenu() {
        val ctl = controller ?: return
        val menu = menuController ?: return
        val anchor = ctl.windowTokenView() ?: return
        val (x, y) = ctl.currentPosition()
        menu.show(anchor, x + dpToPx(40), y)
    }

    /**
     * dp → px
     */
    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    /**
     * 桌宠尺寸（px）
     */
    private fun petSizePx(): Int {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val sizeDp = prefs.getInt(KEY_SIZE_DP, PetWindowController.DEFAULT_SIZE_DP)
        return dpToPx(sizeDp)
    }

    /**
     * 保存位置
     */
    private fun savePosition(x: Int, y: Int) {
        lastKnownPosition = x to y
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_POS_X, x)
            .putInt(KEY_POS_Y, y)
            .apply()
    }

    /**
     * 前台通知
     */
    private fun startForegroundWithNotification() {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动前台服务失败", e)
        }
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, DeepFocusApp.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("大肥鱼在桌面上")
            .setContentText("有消息时，它会冒个泡泡告诉你")
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }
}
