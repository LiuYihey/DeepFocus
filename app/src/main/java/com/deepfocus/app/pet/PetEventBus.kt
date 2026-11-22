package com.deepfocus.app.pet

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * 桌宠事件总线
 *
 * 桌宠是「跨组件的旁路」：专注页、聊天页、后台任务都在各自的界面上，
 * 但桌宠常驻在桌面上，它需要知道全局发生了什么。
 *
 * 用 SharedFlow 而不是直接互相引用，是为了避免：
 * - 桌宠服务去依赖 FocusViewModel / ChatViewModel（UI 层不该被服务持有）
 * - 事件在桌宠服务还没起来时丢失（SharedFlow 带 replay，新订阅者能拿到最近一条）
 *
 * 事件都定义在 data 层附近，发送方不需要知道桌宠是否存在。
 */
object PetEventBus {

    /** 桌宠应该显示的一句话 */
    data class Say(
        /** 要显示的文本 */
        val text: String,
        /** 说完后摆什么姿态，空表示沿用当前姿态 */
        val pose: PetSpriteLibrary.Pose? = null,
        /** 气泡停留时长（毫秒），0 表示用默认值 */
        val holdMillis: Long = 0L,
        /** 是否打断冷却期。主动提醒类事件为 true */
        val urgent: Boolean = false,
        /**
         * 要播的动作 id（来自全量动作库）
         *
         * 让 AI 的情绪和桌宠的动作对齐 ——
         * 比如它发了「害羞」的表情包，桌宠同时演一个害羞的动作。
         */
        val actionId: String? = null
    )

    /** 专注开始 */
    data class FocusStarted(val plannedMinutes: Int = 0)

    /** 专注结束，携带本次实际专注毫秒数 */
    data class FocusEnded(val durationMillis: Long)

    /** 专注达成里程碑（比如 25 分钟），让桌宠庆祝一下 */
    data class FocusMilestone(val totalMinutesToday: Int)

    private val _events = MutableSharedFlow<Any>(
        replay = 1,
        extraBufferCapacity = 32
    )

    /**
     * 全局事件流
     *
     * replay = 1：桌宠服务晚启动时能补到最后一条事件（比如专注刚结束）。
     */
    val events: SharedFlow<Any> = _events

    /** 发送事件（永不阻塞调用方） */
    fun emit(event: Any) {
        _events.tryEmit(event)
    }

    /** 说一句话 */
    fun say(text: String, pose: PetSpriteLibrary.Pose? = null, urgent: Boolean = false) {
        emit(Say(text, pose, urgent = urgent))
    }
}
