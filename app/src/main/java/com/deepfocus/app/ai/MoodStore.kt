package com.deepfocus.app.ai

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 大肥鱼自己选的状态词
 *
 * 用户要求（2026-11-18）：
 * > 「图3中吃的很饱的状态变更成大肥鱼自己选择的状态词汇，
 * > 大肥鱼可以选择自己的情绪和状态」
 *
 * 之前状态胶囊是**机器词表**四选一：
 * 「睡得很香 / 饿了 / 吃得很饱 / 精神不错」——
 * 那不是她在表达状态，是程序在报状态。
 *
 * 现在改成：她想说什么就说什么（「吃撑了」「有点想你」「困得睁不开眼」）。
 * 她通过 `set_mood` 工具自己定，定完存在这里，首页胶囊显示它。
 *
 * **没有她的选择时回落到机器词表** ——
 * 没配 AI、AI 调用失败、或者她还没开口说过话的时候，
 * 胶囊不能是空的，得有个兜底。
 */
@Singleton
class MoodStore @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext context: Context
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("deep_focus_mood", Context.MODE_PRIVATE)

    private val _mood = MutableStateFlow(prefs.getString(KEY_MOOD, null))
    val mood: StateFlow<String?> = _mood.asStateFlow()

    /**
     * 记下她自选的状态词
     *
     * @param value 状态词，空的话忽略
     */
    fun set(value: String?) {
        val v = value?.trim().orEmpty()
        if (!isAcceptableMood(v)) return
        prefs.edit().putString(KEY_MOOD, v).apply()
        _mood.value = v
    }

    /** 清掉（状态变化大的时候别留着旧的） */
    fun clear() {
        prefs.edit().remove(KEY_MOOD).apply()
        _mood.value = null
    }

    companion object {
        private const val KEY_MOOD = "self_mood"

        /** 状态词最长几个字 —— 再长就不是状态词，是句子 */
        internal const val MAX_MOOD_LENGTH = 12

        /**
         * 这个状态词收不收（纯函数，单元测试直接锁它）
         *
         * 空的不要；超过 [MAX_MOOD_LENGTH] 字的也不要 ——
         * 「我今天有点想你因为早上那碗粥太好喝了」不是状态词，是句子，
         * 塞进胶囊会把首页挤爆。
         */
        internal fun isAcceptableMood(value: String?): Boolean {
            val v = value?.trim().orEmpty()
            return v.isNotEmpty() && v.length <= MAX_MOOD_LENGTH
        }
    }
}
