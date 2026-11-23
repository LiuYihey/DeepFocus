package com.deepfocus.app.pet

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.deepfocus.app.R
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 桌宠悬浮窗控制器
 *
 * 负责：
 * - 创建/销毁 WindowManager 悬浮窗
 * - 渲染桌宠本体 + 台词气泡
 * - 处理手势：点击 / 双击 / 长按 / 拖拽
 * - 维护位置（1:1 跟手，松手即停）
 * - 状态机：待机 → 散步 → 贴边 → 睡觉 → 拖拽
 *
 * 关键系统约束（踩过的坑）：
 * 1. 必须 `FLAG_NOT_FOCUSABLE`，否则会抢走输入法的键盘焦点
 * 2. Android 8.0+ 后台不能直接显示悬浮窗，必须挂在前台服务上
 * 3. Android 12+ 后台启动前台服务会抛 `ForegroundServiceStartNotAllowedException`，
 *    所以服务必须在用户可见时启动（见 DesktopPetService）
 */
class PetWindowController(
    private val context: Context,
    private val listener: Listener
) {

    interface Listener {
        /** 用户点击了桌宠（打开聊天） */
        fun onPetClicked()

        /** 用户双击了桌宠（投喂） */
        fun onPetDoubleClicked()

        /** 用户长按了桌宠（菜单） */
        fun onPetLongPressed()

        /** 用户拖动结束 */
        fun onPetDragged(x: Int, y: Int)

        /** 桌宠状态变化 */
        fun onPetStateChanged(state: PetState)
    }

    /** 桌宠状态 */
    enum class PetState {
        /** 待机 */
        IDLE,

        /** 睡觉 */
        SLEEPING,

        /** 被拖拽 */
        DRAGGING
    }

    companion object {
        private const val TAG = "PetWindowController"

        /** 默认桌宠宽度（dp） */
        const val DEFAULT_SIZE_DP = 160

        /** 可选尺寸档位（dp） */
        val SIZE_TIERS_DP = intArrayOf(96, 128, 160, 200)

        /** 最小桌宠尺寸（dp） */
        const val MIN_SIZE_DP = 96

        /** 最大桌宠尺寸（dp） */
        const val MAX_SIZE_DP = 200

        /** 取最接近的尺寸档位（兼容旧版本保存的连续值） */
        fun nearestTier(sizeDp: Int): Int =
            SIZE_TIERS_DP.minByOrNull { kotlin.math.abs(it - sizeDp) } ?: DEFAULT_SIZE_DP

        /** 右上角默认位置：距顶 */
        const val DEFAULT_TOP_OFFSET_DP = 120

        /** 右上角默认位置：距右 */
        const val DEFAULT_RIGHT_OFFSET_DP = 12

        /** 气泡显示时长（ms） */
        const val BUBBLE_DURATION_MS = 10_000L

        /** 双击判定间隔（ms） */
        const val DOUBLE_CLICK_MS = 280L

        /** 长按判定时间（ms） */
        const val LONG_PRESS_MS = 550L
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    /**
     * 主线程 Handler
     *
     * View 的属性改动和动画都要求 Looper 线程。
     * 桌宠的调用方很杂（Service 后台协程、用户点击、状态轮询），
     * 在这一层兜底最省事 —— 不用每个调用点都自己判断线程。
     */
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private var rootView: FrameLayout? = null
    private var petView: PetSpriteView? = null
    private var bubbleView: PetBubbleView? = null

    /** 物理引擎 */
    private val physics = PetPhysics()

    /** 当前状态 */
    var state: PetState = PetState.IDLE
        private set

    /** 是否已添加到窗口 */
    val isAttached: Boolean get() = rootView != null

    /** 桌宠尺寸（px） */
    private var petSizePx = 0

    /** 手势检测 */
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var lastTouchX = 0f
    private var lastTouchTime = 0L
    private var isDragging = false
    private var longPressTriggered = false

    /** 气泡隐藏计时 */
    private var bubbleHideAt = 0L

    /** 气泡占用的额外高度（px）—— 窗口变高、本体靠底，需要把窗口上移同样距离 */
    private var bubbleExtraTop = 0

    /**
     * 显示桌宠
     */
    @SuppressLint("ClickableViewAccessibility")
    fun show(sizeDp: Int = DEFAULT_SIZE_DP, savedX: Int? = null, savedY: Int? = null) {
        if (rootView != null) return

        val metrics = context.resources.displayMetrics
        val sizePx = (sizeDp * metrics.density).roundToInt().coerceAtLeast(1)
        petSizePx = sizePx

        val layout = LayoutInflater.from(context).inflate(R.layout.pet_overlay, null, false)
        rootView = layout as FrameLayout
        petView = layout.findViewById(R.id.petSprite)
        bubbleView = layout.findViewById(R.id.petBubble)

        // XML 里固定写了 160dp，这里按用户选的档位覆盖
        petView?.let { pet ->
            val lp = pet.layoutParams
            if (lp != null) {
                lp.width = sizePx
                lp.height = sizePx
                pet.layoutParams = lp
            }
        }

        physics.screenWidth = metrics.widthPixels
        physics.screenHeight = metrics.heightPixels
        physics.petWidth = sizePx
        physics.petHeight = sizePx

        // 位置：优先用保存的，否则默认右上角
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = savedX ?: (metrics.widthPixels - sizePx - (DEFAULT_RIGHT_OFFSET_DP * metrics.density).roundToInt())
        params.y = savedY ?: (DEFAULT_TOP_OFFSET_DP * metrics.density).roundToInt()

        physics.x = params.x.toFloat()
        physics.y = params.y.toFloat()

        setupTouch(layout)

        try {
            windowManager.addView(layout, params)
        } catch (e: Exception) {
            rootView = null
            petView = null
            bubbleView = null
            throw e
        }

        state = PetState.IDLE
        listener.onPetStateChanged(state)
    }

    /**
     * 隐藏桌宠
     */
    fun hide() {
        val view = rootView ?: return
        try {
            windowManager.removeView(view)
        } catch (e: Exception) {
            // 已经不在窗口里了，忽略
        }
        rootView = null
        petView = null
        bubbleView = null
        bubbleExtraTop = 0
        bubbleHideAt = 0L
        state = PetState.IDLE
    }

    /**
     * 悬浮窗类型
     *
     * Android 8.0+ 必须用 TYPE_APPLICATION_OVERLAY，
     * 旧的 TYPE_PHONE 已被废弃且在新版本上无法显示。
     */
    private fun overlayType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    /**
     * 设置手势
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouch(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    lastTouchX = event.rawX
                    lastTouchTime = System.currentTimeMillis()
                    isDragging = false
                    longPressTriggered = false

                    // 长按检测：用一个可取消的 Runnable，
                    // 松手时必须把它撤掉，否则会在松手后又触发一次
                    v.removeCallbacks(longPressRunnable)
                    v.postDelayed(longPressRunnable, LONG_PRESS_MS)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        isDragging = true
                        // 一旦开始拖动就取消长按检测
                        v.removeCallbacks(longPressRunnable)
                        physics.beginDrag(event.rawX, event.rawY)
                        setState(PetState.DRAGGING)
                        // 被拎起来时换姿态：被悬在半空的紧张感
                        petView?.pose = PetSpriteLibrary.Pose.DRAG
                    }
                    if (isDragging) {
                        physics.updateDrag(event.rawX, event.rawY)
                        // 朝向：跟着手指方向
                        val delta = event.rawX - lastTouchX
                        if (abs(delta) > 2f) {
                            physics.facing = if (delta > 0) 1 else -1
                            petView?.facing = physics.facing
                        }
                        lastTouchX = event.rawX
                        updatePosition()
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.removeCallbacks(longPressRunnable)
                    if (isDragging) {
                        physics.endDrag()
                        setState(PetState.IDLE)
                        listener.onPetDragged(physics.x.roundToInt(), physics.y.roundToInt())
                    } else if (!longPressTriggered) {
                        // 点击 / 双击
                        val now = System.currentTimeMillis()
                        val isDouble = lastClickTime > 0 && now - lastClickTime < DOUBLE_CLICK_MS
                        if (isDouble) {
                            lastClickTime = 0
                            listener.onPetDoubleClicked()
                        } else {
                            lastClickTime = now
                            // 延迟一点再响应单击，避免与双击冲突
                            v.postDelayed({
                                if (lastClickTime == now) {
                                    petView?.playSquash()
                                    listener.onPetClicked()
                                }
                            }, DOUBLE_CLICK_MS)
                        }
                    }
                    lastTouchTime = System.currentTimeMillis()
                    true
                }
                else -> false
            }
        }
    }

    /**
     * 长按检测 Runnable
     */
    private val longPressRunnable = Runnable {
        if (!isDragging && !longPressTriggered) {
            longPressTriggered = true
            listener.onPetLongPressed()
        }
    }

    /** 上次点击时间，用于双击判定 */
    private var lastClickTime = 0L

    /**
     * 切换状态
     */
    private fun setState(newState: PetState) {
        if (state == newState) return
        state = newState
        when (newState) {
            PetState.IDLE -> {
                petView?.sleeping = false
                // 从拖拽回到常态：把 DRAG 姿态换回待机，
                // 否则桌宠会一直保持被拎起来的姿势
                if (petView?.pose == PetSpriteLibrary.Pose.DRAG) {
                    petView?.pose = PetSpriteLibrary.Pose.IDLE
                }
            }
            PetState.SLEEPING -> {
                petView?.sleeping = true
            }
            PetState.DRAGGING -> {
            }
        }
        listener.onPetStateChanged(state)
    }

    /**
     * 每帧推进（由 Service 的渲染循环调用）
     *
     * **必须在主线程调用** —— 里面会改 View 属性、
     * 调 `WindowManager.updateViewLayout()`。
     */
    fun tick() {
        if (android.os.Looper.myLooper() != mainHandler.looper) {
            // 不在主线程就切过去 —— 后台线程调 View / WindowManager 会崩 App
            mainHandler.post { tick() }
            return
        }
        val now = System.currentTimeMillis()

        physics.step()

        // 气泡自动隐藏
        if (bubbleHideAt > 0 && now > bubbleHideAt) {
            hideBubble()
        }

        updatePosition()
    }

    /**
     * 更新悬浮窗位置
     */
    private fun updatePosition() {
        moveWindowTo(physics.x.roundToInt(), physics.y.roundToInt())
    }

    /**
     * 把悬浮窗挪到 (x, y)
     *
     * **必须在主线程调用** —— `WindowManager.updateViewLayout()`
     * 内部会 `checkThread()`，后台线程调会抛
     * `CalledFromWrongThreadException`（会直接崩 App）。
     *
     * 窗口已经被移除时（服务销毁的竞态）忽略就行。
     */
    private fun moveWindowTo(x: Int, y: Int) {
        val rv = rootView ?: return
        val lp = rv.layoutParams as? WindowManager.LayoutParams ?: return
        // 气泡显示时窗口比本体高，lp.y 要扣掉多出来的那截，本体才停在原处
        val targetY = y - bubbleExtraTop
        // 位置没变就别再 updateViewLayout —— 渲染循环每 33ms 调一次，
        // 重复 relayout 会让悬浮窗在合成器里闪。
        if (lp.x == x && lp.y == targetY) return
        lp.x = x
        lp.y = targetY
        try {
            windowManager.updateViewLayout(rv, lp)
        } catch (e: Exception) {
            // 窗口已被移除
        }
    }

    /**
     * 播一个一次性动作（互动玩法用）
     *
     * 播完自动回到当前姿态。
     *
     * **必须在主线程调用** —— 动画只能在 Looper 线程跑。
     * 这里统一兜底：不在主线程就 post 过去。
     * （调用方来自 Service 的后台协程 / 用户点击回调，线程不统一。）
     *
     * @param action 要播的动作
     * @param onEnd 播完回调
     */
    fun playAction(action: PetAction, onEnd: (() -> Unit)? = null) {
        onMain {
            petView?.playAction(action, onEnd)
        }
    }

    /**
     * 显示一条台词
     */
    fun showBubble(text: String) {
        onMain {
            val bubble = bubbleView ?: return@onMain
            val root = rootView ?: return@onMain
            // setText 会算好气泡高度。窗口按「本体 + 气泡」变高，
            // 本体钉在窗口底部，再从 lp.y 里扣掉多出来的高度 ——
            // 这样气泡在头顶长出来，本体在屏幕上不动。
            val bubbleHeight = bubble.setText(text)
            val gap = (petSizePx * 0.04f).toInt()
            bubbleExtraTop = bubbleHeight + gap
            bubble.visibility = View.VISIBLE
            bubble.alpha = 0f
            bubble.animate().alpha(1f).setDuration(180).start()
            root.minimumHeight = petSizePx + bubbleExtraTop
            root.requestLayout()
            bubbleHideAt = System.currentTimeMillis() + BUBBLE_DURATION_MS
            updatePosition()
        }
    }

    /**
     * 把一段 View 操作放到主线程执行
     *
     * View 的属性改动和动画都要求 Looper 线程。
     * 桌宠的调用方很杂（Service 后台协程、用户点击、状态轮询），
     * 在这一层兜底最省事 —— 不用每个调用点都自己判断线程。
     */
    private inline fun onMain(crossinline block: () -> Unit) {
        val handler = mainHandler
        if (handler.looper == android.os.Looper.myLooper()) {
            block()
        } else {
            handler.post { block() }
        }
    }

    /**
     * 隐藏气泡
     */
    fun hideBubble() {
        onMain {
            val bubble = bubbleView ?: return@onMain
            bubble.animate().alpha(0f).setDuration(220).withEndAction {
                bubble.visibility = View.GONE
                bubbleExtraTop = 0
                rootView?.minimumHeight = petSizePx
                rootView?.requestLayout()
                updatePosition()
            }.start()
            bubbleHideAt = 0L
        }
    }

    /**
     * 切换姿态
     */
    fun setPose(pose: PetSpriteLibrary.Pose) {
        onMain {
            petView?.pose = pose
        }
    }

    /**
     * 睡觉 / 醒来
     */
    fun setSleeping(sleeping: Boolean) {
        onMain {
            setState(if (sleeping) PetState.SLEEPING else PetState.IDLE)
            petView?.pose =
                if (sleeping) PetSpriteLibrary.Pose.SLEEPING else PetSpriteLibrary.Pose.IDLE
        }
    }
    /**
     * 当前窗口是否已经挂在 WindowManager 上
     */
    fun isShowing(): Boolean = rootView != null

    /**
     * 拿一个已挂在窗口上的 View 当 token
     *
     * 长按菜单是 PopupWindow，必须依附一个真实的窗口 token。
     * 服务里没有 Activity，只能拿桌宠自己的悬浮窗当锚点。
     */
    fun windowTokenView(): View? = rootView

    /**
     * 从任意线程安全地显示窗口
     *
     * `show()` / `hide()` 内部会动 `WindowManager`，
     * **只能在主线程调**（否则抛 `CalledFromWrongThreadException`，
     * 而这个异常如果被 try/catch 吞掉，表现就是「窗口没加上 / 没拿掉」）。
     * 桌宠的状态轮询跑在 `Dispatchers.Default` 上，所以统一走这两个壳。
     */
    fun showSafely(sizeDp: Int, x: Int?, y: Int?) {
        onMain {
            if (rootView != null) return@onMain
            try {
                show(sizeDp, x, y)
            } catch (e: Exception) {
                android.util.Log.w(TAG, "显示悬浮窗失败", e)
            }
        }
    }

    /** 从任意线程安全地移除窗口 */
    fun hideSafely() {
        onMain { hide() }
    }

    /**
     * 拖到某个位置（设置页用）
     */
    fun moveTo(x: Int, y: Int) {
        physics.x = x.toFloat()
        physics.y = y.toFloat()
        onMain {
            updatePosition()
        }
    }

    /**
     * 修改桌宠尺寸（设置页用）
     *
     * 只改 View 尺寸和窗口位置，不重建窗口 ——
     * 重建会闪一下，也会把正在播的动画打断。
     */
    fun resize(sizeDp: Int) {
        onMain {
            val px = (sizeDp * context.resources.displayMetrics.density)
                .roundToInt()
                .coerceAtLeast(1)
            petSizePx = px
            physics.petWidth = px
            physics.petHeight = px
            rootView?.minimumHeight = px + bubbleExtraTop
            rootView?.requestLayout()
            petView?.let { v ->
                val lp = v.layoutParams
                if (lp != null) {
                    lp.width = px
                    lp.height = px
                    v.layoutParams = lp
                } else {
                    v.layoutParams = FrameLayout.LayoutParams(px, px)
                }
            }
            // 变大后可能顶出屏幕，夹回来
            physics.x = physics.x.coerceIn(
                0f,
                (physics.screenWidth - px).coerceAtLeast(0).toFloat()
            )
            physics.y = physics.y.coerceIn(
                0f,
                (physics.screenHeight - px).coerceAtLeast(0).toFloat()
            )
            updatePosition()
        }
    }

    /**
     * 翻转朝向
     */
    fun flipFacing() {
        physics.facing = -physics.facing
        onMain {
            petView?.facing = physics.facing
        }
    }

    /**
     * 当前坐标
     */
    fun currentPosition(): Pair<Int, Int> = physics.x.roundToInt() to physics.y.roundToInt()
}
