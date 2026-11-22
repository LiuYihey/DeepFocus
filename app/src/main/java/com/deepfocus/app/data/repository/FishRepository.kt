package com.deepfocus.app.data.repository

import android.content.SharedPreferences
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.deepfocus.app.data.local.dao.AffinityAchievementDao
import com.deepfocus.app.data.local.dao.FishCatalogDao
import com.deepfocus.app.data.local.dao.FishStateDao
import com.deepfocus.app.data.local.dao.FoodInventoryDao
import com.deepfocus.app.data.local.dao.UserFishDao
import com.deepfocus.app.data.local.entity.AffinityAchievementEntity
import com.deepfocus.app.data.local.entity.FishCatalogEntity
import com.deepfocus.app.data.local.entity.UserFishEntity
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

/**
 * 大肥鱼仓库
 * 封装大肥鱼形态目录、用户大肥鱼、好感度成就的数据访问
 *
 * 喂食频率限制：每个自然日（0:00-24:00）最多 [MAX_FEED_PER_DAY] 次，
 * 超出后 [canFeedNow] 返回 false，由 UI 提示"大肥鱼已经吃饱啦，明天再喂吧~"。
 * 跨天自动重置（以本地时区 0 点为界）。
 *
 * 按大肥鱼独立计数：每只大肥鱼有独立的每日喂食次数上限，切换大肥鱼时互不影响。
 */
class FishRepository(
    private val db: RoomDatabase,
    private val fishCatalogDao: FishCatalogDao,
    private val userFishDao: UserFishDao,
    private val achievementDao: AffinityAchievementDao,
    private val foodInventoryDao: FoodInventoryDao,
    private val fishStateDao: FishStateDao,
    private val feedLimitPrefs: SharedPreferences
) {

    companion object {
        /** 每个自然日最多喂食次数 */
        internal const val MAX_FEED_PER_DAY = 5
        /** SharedPreferences key 前缀：存储"当天喂食次数"（按 fishId 命名空间分隔） */
        private const val KEY_FEED_COUNT_TODAY_PREFIX = "feed_count_today_"
        /** SharedPreferences key 前缀：存储"上次喂食所属的自然日（yyyyMMdd）" */
        private const val KEY_FEED_DAY_KEY_PREFIX = "feed_day_key_"

        /** 专注每多少分钟涨 1 点好感度 */
        internal const val FOCUS_AFFINITY_PER_MINUTE = 10

        /**
         * 喂一次饭涨几点好感度
         *
         * 2026-11-18 用户决定：「点击大白饭后饱腹度增加，同时好感度也可以慢慢增加」，
         * 并且好感度要能解锁人格卡。
         *
         * 定 1 点 —— 专注 10 分钟也才 +1，
         * 所以喂饭是「顺手也表示亲近」，不能替代专注。
         * 想靠喂饭刷满 350 点解锁全部人格卡，得喂 350 次（每天上限 5 次 = 70 天）。
         */
        internal const val AFFINITY_PER_FEED = 1

        /** 专注少于这么多秒不给好感度（秒） */
        private const val MIN_FOCUS_AFFINITY_SECONDS = 60

        /**
         * 算这次专注该涨几点好感度（纯函数，单元测试直接锁它）
         *
         * 每 [FOCUS_AFFINITY_PER_MINUTE] 分钟 +1，不足一个间隔也按 1 分钟算，
         * 免得「专注了 3 分钟」一点反馈都没有。
         */
        internal fun focusAffinityBonus(durationSeconds: Int): Int {
            if (durationSeconds < MIN_FOCUS_AFFINITY_SECONDS) return 0
            val minutes = (durationSeconds / 60).coerceAtLeast(1)
            return (minutes / FOCUS_AFFINITY_PER_MINUTE).coerceAtLeast(1)
        }
    }

    /**
     * 是否还能继续喂食（今日未超过 [MAX_FEED_PER_DAY] 次）
     * 按大肥鱼独立计数：不同大肥鱼有独立的每日喂食额度。
     *
     * @param fishId 当前大肥鱼 ID，null 时返回 false
     */
    fun canFeedNow(fishId: Int?): Boolean {
        if (fishId == null) return false
        return getFeedCountToday(fishId) < MAX_FEED_PER_DAY
    }

    /**
     * 今日剩余可喂食次数（0 表示已达上限）
     * 按大肥鱼独立计数。
     *
     * @param fishId 当前大肥鱼 ID，null 时返回 0
     */
    fun getRemainingFeedCountToday(fishId: Int?): Int {
        if (fishId == null) return 0
        return (MAX_FEED_PER_DAY - getFeedCountToday(fishId)).coerceAtLeast(0)
    }

    /**
     * 获取今日已喂食次数，跨天时自动归零
     * 按大肥鱼独立计数：每只大肥鱼的次数存储在独立的 SharedPreferences key 中。
     *
     * @param fishId 当前大肥鱼 ID
     */
    suspend fun getTodayFeedCount(): Int {
        val fishId = userFishDao.getUserFish()?.id ?: return 0
        return getFeedCountToday(fishId)
    }

    /**
     * 当前大肥鱼的状态（饱腹度 / 上次喂食时间 / 睡没睡）
     *
     * 给 agent tool 用 —— 「我今天吃了多少饭」「我饿了没」要查这个。
     */
    suspend fun getCurrentFishState(): com.deepfocus.app.data.local.entity.FishStateEntity? =
        fishStateDao.get()

    /**
     * 获取今日已喂食次数，跨天时自动归零
     * 按大肥鱼独立计数：每只大肥鱼的次数存储在独立的 SharedPreferences key 中。
     *
     * @param fishId 当前大肥鱼 ID
     */
    private fun getFeedCountToday(fishId: Int): Int {
        val todayKey = todayKey()
        val dayKeyKey = KEY_FEED_DAY_KEY_PREFIX + fishId
        val countKey = KEY_FEED_COUNT_TODAY_PREFIX + fishId
        val savedDayKey = feedLimitPrefs.getString(dayKeyKey, null)
        // 跨天：次数归零，更新日期 key
        if (savedDayKey != todayKey) {
            feedLimitPrefs.edit()
                .putString(dayKeyKey, todayKey)
                .putInt(countKey, 0)
                .apply()
            return 0
        }
        return feedLimitPrefs.getInt(countKey, 0)
    }

    /**
     * 记录一次喂食，今日计数 +1（跨天则从 1 开始）
     * 按大肥鱼独立计数。
     *
     * @param fishId 当前大肥鱼 ID
     */
    private fun recordFeedTimestamp(fishId: Int) {
        val todayKey = todayKey()
        val dayKeyKey = KEY_FEED_DAY_KEY_PREFIX + fishId
        val countKey = KEY_FEED_COUNT_TODAY_PREFIX + fishId
        val savedDayKey = feedLimitPrefs.getString(dayKeyKey, null)
        val newCount = if (savedDayKey == todayKey) {
            feedLimitPrefs.getInt(countKey, 0) + 1
        } else {
            1
        }
        feedLimitPrefs.edit()
            .putString(dayKeyKey, todayKey)
            .putInt(countKey, newCount)
            .apply()
    }

    /** 今日日期 key（yyyyMMdd，本地时区） */
    private fun todayKey(): String {
        val c = Calendar.getInstance()
        return String.format(
            "%04d%02d%02d",
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH) + 1,
            c.get(Calendar.DAY_OF_MONTH)
        )
    }

    /** 获取所有大肥鱼形态 */
    suspend fun getAllBreeds(): List<FishCatalogEntity> = fishCatalogDao.getAllBreeds()

    /** 观察所有大肥鱼形态（Flow），种子数据插入后自动通知 UI */
    fun observeAllBreeds(): Flow<List<FishCatalogEntity>> = fishCatalogDao.observeAllBreeds()

    /** 根据品种 ID 获取品种 */
    suspend fun getBreedById(breedId: String): FishCatalogEntity? = fishCatalogDao.getById(breedId)

    /** 观察用户大肥鱼（Flow） */
    fun observeUserFish(): Flow<UserFishEntity?> = userFishDao.observeUserFish()

    /** 获取用户大肥鱼 */
    suspend fun getUserFish(): UserFishEntity? = userFishDao.getUserFish()

    /** 用户是否已创建过任何大肥鱼 */
    suspend fun hasFish(): Boolean = userFishDao.hasAnyFish()

    /**
     * 专注完成后增加好感度
     *
     * 养成主线只有好感度，靠「喂食 / 专注 / 聊天」三条路增长。
     * 专注这条路的权重刻意压得比喂食低：喂食是主动照料，专注是「你俩一起熬过的一段」。
     *
     * 规则：每 [FOCUS_AFFINITY_PER_MINUTE] 分钟 +1，不足一个间隔也按 1 分钟算，
     * 免得「专注了 3 分钟」一点反馈都没有。
     *
     * 只涨 affinityLevel，不涨 totalFeedCount —— 后者是投喂次数，
     * 混在一起会让「投喂 N 次解锁」这类文案对不上。
     *
     * @param durationSeconds 本次专注时长（秒）
     */
    suspend fun addAffinityByFocus(durationSeconds: Int) {
        val bonus = focusAffinityBonus(durationSeconds)
        if (bonus <= 0) return
        userFishDao.addAffinity(bonus)
    }

    /** 当前激活大肥鱼的好感度点数 */
    suspend fun getAffinityLevel(): Int = userFishDao.getUserFish()?.affinityLevel ?: 0

    /**
     * 检查并解锁达标的好感度成就
     *
     * 判定基准是**好感度**而非投喂次数 ——
     * 养成主线只有好感度这一条线，好感度由「喂食 / 专注 / 聊天」共同推高，
     * 用投喂次数当门槛会让「光靠专注也能变亲近」的设计自相矛盾。
     * 里程碑 10 / 50 / 100 / 200 / 350 正好对应 [FishMoodResolver] 的五档称号边界
     * 与内置人格卡的 6 个档位门槛。
     *
     * @return 本次新解锁的成就列表
     */
    suspend fun checkAndUnlockAchievements(): List<AffinityAchievementEntity> {
        val fish = userFishDao.getUserFish() ?: return emptyList()
        val locked = achievementDao.getLockedAchievements()
        if (locked.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val newly = mutableListOf<AffinityAchievementEntity>()
        for (achievement in locked) {
            if (fish.affinityLevel >= achievement.milestone) {
                achievementDao.unlock(achievement.milestone, now)
                newly.add(achievement.copy(unlockedAt = now))
            }
        }
        return newly
    }

    /** 观察所有已创建的大肥鱼（用于切换大肥鱼列表） */
    fun observeAllFish(): Flow<List<UserFishEntity>> = userFishDao.observeAllFish()

    /** 获取所有已创建的大肥鱼 */
    suspend fun getAllFish(): List<UserFishEntity> = userFishDao.getAllFish()

    /**
     * 创建新大肥鱼（首次选择或新增大肥鱼）
     *
     * 多实例化设计：每只大肥鱼是独立记录，有独立的 affinityLevel/totalFeedCount。
     * 创建时自动将旧大肥鱼（若有）设为 inactive，新大肥鱼设为 active。
     *
     * @param breedId 品种 ID
     * @param name 大肥鱼名字
     */
    suspend fun createFish(breedId: String, name: String) {
        db.withTransaction {
            userFishDao.deactivateAll()
            userFishDao.insert(
                UserFishEntity(
                    breedId = breedId,
                    name = name,
                    isActive = true
                )
            )
        }
    }

    /**
     * 改当前激活大肥鱼的名字
     *
     * 名字的真源在这张表里 —— 人格卡页编辑的也是它。
     */
    suspend fun renameActiveFish(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        userFishDao.renameActiveFish(trimmed)
    }

    /**
     * 切换到指定大肥鱼（实例化切换，恢复该大肥鱼的独立数据）
     *
     * 每只大肥鱼有独立的 name/affinityLevel/totalFeedCount，切换即恢复该大肥鱼的完整状态。
     * 类似"切换账号"：不同大肥鱼的养成进度独立保留。
     *
     * @param fishId 目标大肥鱼 ID
     */
    suspend fun switchToFish(fishId: Int) {
        db.withTransaction {
            userFishDao.deactivateAll()
            userFishDao.activateFish(fishId)
        }
    }

    /** 删除指定大肥鱼（不可删除当前激活的大肥鱼） */
    suspend fun deleteFish(fishId: Int) {
        userFishDao.deleteFishById(fishId)
    }

    /**
     * 投喂大肥鱼：增加投喂次数，并检查成就解锁
     *
     * ⚠️ 这是**不消耗食物**的计数路径（不发好感度）。
     * 真正喂大白饭走 [feedFishWithFood] —— 那里才会 +1 好感度。
     *
     * 修复 B3：用 @Transaction 包裹多步写操作，确保 totalFeedCount+1 与成就解锁原子完成。
     * 原代码无事务，进程在 incrementFeed 后被杀会导致 totalFeedCount 已+1 但成就未解锁，
     * 下次投喂才解锁本应本次解锁的成就，进度错位。
     *
     * 投喂频率限制：事务成功后记录时间戳，[canFeedNow] 据此判断是否已超 24h/5 次上限。
     * 调用方应在调用前先调用 [canFeedNow]，超限时给出"大肥鱼已经吃饱啦"提示。
     *
     * @param fishId 当前大肥鱼 ID，用于按大肥鱼独立计数每日喂食次数
     * @return 新解锁的成就列表（可能为空）
     */
    suspend fun feedFish(
        fishId: Int?
    ): List<AffinityAchievementEntity> {
        if (fishId == null) return emptyList()
        val newlyUnlocked = db.withTransaction {
            userFishDao.incrementFeed()
            unlockAchievementsInTx()
        }
        // 事务成功后才记录喂食时间戳，失败回滚则不占用每日额度
        recordFeedTimestamp(fishId)
        return newlyUnlocked
    }

    /**
     * 事务内解锁达标成就（判定基准：好感度）
     *
     * 单独抽出来给 feedFish / feedFishWithFood 复用。
     */
    private suspend fun unlockAchievementsInTx(): List<AffinityAchievementEntity> {
        val fish = userFishDao.getUserFish() ?: return emptyList()
        val locked = achievementDao.getLockedAchievements()
        if (locked.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val newly = mutableListOf<AffinityAchievementEntity>()
        for (achievement in locked) {
            if (fish.affinityLevel >= achievement.milestone) {
                achievementDao.unlock(achievement.milestone, now)
                newly.add(achievement.copy(unlockedAt = now))
            }
        }
        return newly
    }

    /**
     * 投喂大肥鱼（含消耗食物）：单事务包裹"消耗食物 + 增加投喂次数 + 解锁成就"
     *
     * 修复迭代5 Bug #3：原 FishViewModel 分两步调用
     * foodRepository.consumeOne(foodId) + fishRepository.feedFish(affinityBonus)，
     * 两个独立写操作无外层事务。若 consumeOne 成功后 feedFish 抛异常，食物被消耗但
     * totalFeedCount/affinityLevel 未增加，成就未解锁，数据不一致。
     *
     * 此方法将三步合并为一个 Room 事务，任一步失败整体回滚。
     *
     * 投喂频率限制：事务成功后记录时间戳，[canFeedNow] 据此判断是否已超 24h/5 次上限。
     * 调用方应在调用前先调用 [canFeedNow]，超限时给出"大肥鱼已经吃饱啦"提示。
     *
     * @param foodId 要消耗的食物 ID
     * @param fishId 当前大肥鱼 ID，用于按大肥鱼独立计数每日喂食次数
     * @return 新解锁的成就列表（可能为空）
     */
    suspend fun feedFishWithFood(
        foodId: String,
        fishId: Int?
    ): List<AffinityAchievementEntity> {
        if (fishId == null) return emptyList()
        val newlyUnlocked = db.withTransaction {
            foodInventoryDao.consumeOne(foodId, System.currentTimeMillis())
            userFishDao.incrementFeed()
            // 喂食也涨好感度（2026-11-18 用户决定：涨好感度，且能解锁人格卡）。
            //
            // 额度刻意压得很低 —— 专注才是主线，喂饭只是「顺手也表示一下亲近」。
            // 一次喂饭 +1，而专注每 [FOCUS_AFFINITY_PER_MINUTE] 分钟就 +1，
            // 所以光靠喂饭是解锁不了人格卡的，必须真的专注。
            userFishDao.addAffinity(AFFINITY_PER_FEED)
            unlockAchievementsInTx()
        }
        // 事务成功后才记录喂食时间戳，失败回滚则不占用每日额度
        recordFeedTimestamp(fishId)
        return newlyUnlocked
    }

    /** 观察所有成就 */
    fun observeAchievements(): Flow<List<AffinityAchievementEntity>> =
        achievementDao.observeAllAchievements()

    /** 获取所有成就 */
    suspend fun getAllAchievements(): List<AffinityAchievementEntity> =
        achievementDao.getAllAchievements()
}
