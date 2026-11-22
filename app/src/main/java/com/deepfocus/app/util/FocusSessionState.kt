package com.deepfocus.app.util

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 专注会话全局状态（进程内单例）
 *
 * 用于跨组件通信：FocusScreen 进入专注模式时置位，DeepFocusService 检测到
 * 用户切换到其他应用时若发现此标志为 true，则将 DeepFocus 拉回前台（覆盖其他应用）。
 *
 * 仅内存态：进程被杀后状态丢失，专注会话也随之结束（符合用户预期：退出即结束）。
 *
 * ## 会话代号（generation）
 *
 * 专注页被拉回时系统可能新建 Activity 实例 → 新建 FocusViewModel →
 * 新的计时循环和新的 ViewModel 同时存在。两边都跑计时循环会重复发奖励、
 * 还会互相覆盖全局累计时长。
 *
 * 所以每次「开启」或「接管」会话都会生成一个递增代号：
 * - 计时循环只在代号仍是当前代号时继续跑，旧实例的循环自动退出
 * - 只有代号匹配的实例能结束全局会话，旧实例销毁时不会误杀新会话
 */
object FocusSessionState {
    /** 专注模式是否激活 */
    private val active = AtomicBoolean(false)

    /** 专注开始时间戳（毫秒），用于服务端校验和恢复 UI */
    private val startedAt = AtomicLong(0L)

    /** 当前专注会话已积累的毫秒数（用于服务端推送奖励时判断是否满 30 分钟） */
    private val accumulatedMs = AtomicLong(0L)

    /**
     * 当前会话在 focus_sessions 表里的行 id（0 表示还没写入）
     *
     * 用途：Activity 被系统回收后重建 → 新 FocusViewModel 接管旧会话，
     * 需要拿到原行 id 才能在结束时正确回填时长，避免留下 endTime=null 的孤儿行。
     */
    private val sessionRowId = AtomicLong(0L)

    /** 会话代号，每次开启或接管都会变化 */
    private val generation = AtomicLong(0L)

    val isActive: Boolean get() = active.get()
    val startedAtMillis: Long get() = startedAt.get()
    val accumulatedMillis: Long get() = accumulatedMs.get()
    val sessionRowIdValue: Long get() = sessionRowId.get()

    /** 当前会话代号 */
    val currentGeneration: Long get() = generation.get()

    /**
     * 开启新会话
     *
     * @return 本次会话的代号
     */
    fun start(): Long {
        active.set(true)
        startedAt.set(System.currentTimeMillis())
        accumulatedMs.set(0L)
        sessionRowId.set(0L)
        return generation.incrementAndGet()
    }

    /**
     * 接管进行中的会话
     *
     * 场景：专注中用户切到别的 App 被拉回，系统重建了 Activity 和 ViewModel。
     * 此时不能重新计时（那会让用户白专注），只能接着原会话跑。
     *
     * 开始时间、行 id、累计时长都保持不变，只有代号更新 ——
     * 旧 ViewModel 的计时循环因此自动失效，不会重复发奖励。
     *
     * @return 本次接管后的会话代号
     */
    fun claim(): Long {
        active.set(true)
        return generation.incrementAndGet()
    }

    /** 数据库插入完成后回填行 id */
    fun setSessionRowId(id: Long) {
        if (id > 0) sessionRowId.set(id)
    }

    /** 更新累计时长（FocusScreen 每秒调用一次） */
    fun updateAccumulated(ms: Long) {
        if (active.get()) accumulatedMs.set(ms)
    }

    /** 退出专注模式 */
    fun stop() {
        active.set(false)
        startedAt.set(0L)
        accumulatedMs.set(0L)
        sessionRowId.set(0L)
    }

    /**
     * 仅当代号仍是自己持有的那个时才结束全局会话
     *
     * 用途：旧 ViewModel 被销毁时不能把新实例的会话一起停掉。
     *
     * @param generation 本实例持有的会话代号
     * @return 是否真的结束了会话
     */
    fun stopIfCurrent(generation: Long): Boolean {
        if (this.generation.get() != generation) return false
        stop()
        return true
    }
}
