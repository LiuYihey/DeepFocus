package com.deepfocus.app.pet

import kotlin.math.max

/**
 * 桌宠位置状态
 *
 * 刻意做得很简单：桌宠只负责"被拖到哪就待在哪"。
 *
 * 早期版本做过弹簧跟随 + 抛物线 + 撞墙/落地反弹，
 * 实测在悬浮窗上观感很差：弹簧让桌宠落后手指再追上来（像在抖），
 * 反弹又会把用户刚摆好的位置弹走。用户明确要求「我移动它，它就到哪就行」，
 * 所以这里只保留 1:1 跟手 + 屏幕内约束。
 */
class PetPhysics(
    /** 屏幕宽（px） */
    var screenWidth: Int = 1080,
    /** 屏幕高（px） */
    var screenHeight: Int = 1920,
    /** 桌宠宽度（px） */
    var petWidth: Int = 200,
    /** 桌宠高度（px） */
    var petHeight: Int = 200
) {

    /** 当前位置（桌宠左上角） */
    var x = 0f
    var y = 0f

    /** 是否正在被手指拖拽 */
    var dragging = false

    /** 拖拽目标位置 */
    var dragTargetX = 0f
    var dragTargetY = 0f

    /** 手指按下时相对桌宠左上角的偏移 */
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f

    /** 朝向：1 表示朝右，-1 表示朝左 */
    var facing = 1

    /**
     * 切换到拖拽状态
     */
    fun beginDrag(touchX: Float, touchY: Float) {
        dragging = true
        // 记录手指相对桌宠的偏移，拖动时不会跳到手指中心
        dragOffsetX = touchX - x
        dragOffsetY = touchY - y
        dragTargetX = x
        dragTargetY = y
    }

    /**
     * 更新拖拽目标
     */
    fun updateDrag(touchX: Float, touchY: Float) {
        dragTargetX = touchX - dragOffsetX
        dragTargetY = touchY - dragOffsetY
        // 立即生效：窗口位置由触摸事件驱动（最高 120Hz），
        // 不等渲染循环的 33ms tick，拖起来才真正跟手。
        x = dragTargetX
        y = dragTargetY
        clampToScreen()
    }

    /**
     * 结束拖拽
     *
     * 松手即停，不做任何惯性 / 反弹。
     */
    fun endDrag() {
        dragging = false
        clampToScreen()
    }

    /**
     * 每帧推进
     *
     * 拖拽时 1:1 贴着手指走（不做插值），其它时候位置由用户决定。
     */
    fun step() {
        if (!dragging) return
        x = dragTargetX
        y = dragTargetY
        clampToScreen()
    }

    /**
     * 限制在屏幕内
     */
    private fun clampToScreen() {
        x = x.coerceIn(0f, max(0f, (screenWidth - petWidth).toFloat()))
        y = y.coerceIn(0f, max(0f, (screenHeight - petHeight).toFloat()))
    }
}
