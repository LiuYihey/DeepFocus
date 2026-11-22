package com.deepfocus.app.ai

import com.deepfocus.app.data.local.dao.FishStateDao
import com.deepfocus.app.data.local.entity.FishStateEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 大肥鱼状态引擎
 *
 * 负责饱腹度随时间衰减、睡眠判定。
 * 这是「它是个活物」的基础：你不理它，它会饿。
 */
@Singleton
class FishStateEngine @Inject constructor(
    private val fishStateDao: FishStateDao
) {

    companion object {
        /** 饱腹度每小时衰减多少点（约 4 天从满到空） */
        const val SATIETY_DECAY_PER_HOUR = 1

        /** 连续无互动多少小时后开始睡觉 */
        const val SLEEP_AFTER_IDLE_HOURS = 2

        /** 每次喂食增加的饱腹度 */
        const val SATIETY_PER_FEED = 15

        /** 大白饭额外增加的饱腹度（最爱的东西，多一点） */
        const val SATIETY_PER_BIG_RICE = 25

        /** 距上次喂食超过 24 小时就算饿 */
        const val HUNGRY_AFTER_MILLIS = 24 * 60 * 60 * 1000L

        /**
         * 是否饿了（纯判定，所有调用点共用这一份）
         *
         * **只看距上次喂食是否超过 24 小时**，与饱腹度数值无关。
         * 从没喂过（lastFedAt = 0）算作饿了 ——
         * 新用户看到的第一句话就该是「大白饭」。
         *
         * @param state 当前状态
         * @param now 当前时间戳
         */
        fun isHungry(state: FishStateEntity, now: Long): Boolean {
            if (state.lastFedAt <= 0L) return true
            return now - state.lastFedAt > HUNGRY_AFTER_MILLIS
        }
    }

    /**
     * 刷新状态：按经过的时间衰减饱腹度
     */
    suspend fun tick(): FishStateEntity {
        val state = fishStateDao.get() ?: FishStateEntity().also { fishStateDao.upsert(it) }
        val now = System.currentTimeMillis()
        val elapsedHours = (now - state.updatedAt).coerceAtLeast(0) / 3_600_000.0

        if (elapsedHours < 0.01) return state

        val satietyDecay = (SATIETY_DECAY_PER_HOUR * elapsedHours).toInt()

        val updated = state.copy(
            satiety = (state.satiety - satietyDecay).coerceIn(0, 100),
            updatedAt = now
        )
        fishStateDao.upsert(updated)
        return updated
    }

    /**
     * 喂食：增加饱腹度并记下喂食时间
     *
     * 这里只管**生理状态**（饱腹度 + 上次喂食时间），
     * 好感度在 [com.deepfocus.app.data.repository.FishRepository.feedFishWithFood] 里加。
     *
     * @param isBigRice 是否是大白饭（大肥鱼最爱，额外加成）
     */
    suspend fun onFed(isBigRice: Boolean) {
        val state = fishStateDao.get() ?: FishStateEntity().also { fishStateDao.upsert(it) }
        val satietyGain = if (isBigRice) SATIETY_PER_BIG_RICE else SATIETY_PER_FEED
        val now = System.currentTimeMillis()
        val updated = state.copy(
            satiety = (state.satiety + satietyGain).coerceIn(0, 100),
            lastFedAt = now,
            updatedAt = now
        )
        fishStateDao.upsert(updated)
    }

    /**
     * 判断是否该睡觉
     *
     * 条件：超过 [SLEEP_AFTER_IDLE_HOURS] 小时没互动。
     * （原判据还有 `energy < 60` —— 精力维度已删除，这个数值用户看不见也设不了。）
     */
    suspend fun shouldSleep(): Boolean {
        val state = tick()
        if (state.isSleeping) return true
        val idleMillis = System.currentTimeMillis() - state.updatedAt
        return idleMillis > SLEEP_AFTER_IDLE_HOURS * 3_600_000L
    }

    /**
     * 入睡 */
    suspend fun sleep() {
        fishStateDao.markSleeping()
    }

    /**
     * 醒来：累计睡眠时长
     */
    suspend fun wake(): Long {
        val state = fishStateDao.get() ?: return 0L
        if (!state.isSleeping) return 0L
        val slept = System.currentTimeMillis() - state.sleepStartedAt
        fishStateDao.markAwake(slept.coerceAtLeast(0L))
        return slept.coerceAtLeast(0L)
    }
}
