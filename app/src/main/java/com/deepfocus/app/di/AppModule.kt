package com.deepfocus.app.di

import android.content.Context
import com.deepfocus.app.ai.AiConfigStore
import com.deepfocus.app.ai.FishAgent
import com.deepfocus.app.ai.MoodStore
import com.deepfocus.app.ai.WorldContextProvider
import com.deepfocus.app.data.local.AppDatabase
import com.deepfocus.app.data.local.dao.AffinityAchievementDao
import com.deepfocus.app.data.local.dao.ChatConversationDao
import com.deepfocus.app.data.local.dao.ChatDao
import com.deepfocus.app.data.local.dao.FishCatalogDao
import com.deepfocus.app.data.local.dao.FishMemoryDao
import com.deepfocus.app.data.local.dao.FishStateDao
import com.deepfocus.app.data.local.dao.FoodCatalogDao
import com.deepfocus.app.data.local.dao.FoodInventoryDao
import com.deepfocus.app.data.local.dao.FocusSessionDao
import com.deepfocus.app.data.local.dao.PersonaCardDao
import com.deepfocus.app.data.local.dao.PersonaStateDao
import com.deepfocus.app.data.local.dao.TodoDao
import com.deepfocus.app.data.local.dao.TravelJournalDao
import com.deepfocus.app.data.local.dao.UserFishDao
import com.deepfocus.app.data.repository.FishRepository
import com.deepfocus.app.data.repository.FoodRepository
import com.deepfocus.app.data.repository.StatsRepositoryImpl
import com.deepfocus.app.data.repository.TodoRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt 依赖注入模块
 * 提供数据库实例、各表 DAO 和各 Repository 的依赖
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * 提供 AppDatabase 单例
     */
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.getInstance(context)

    // ===== 原有 DAO =====

    @Provides
    fun provideFocusSessionDao(db: AppDatabase): FocusSessionDao = db.focusSessionDao()

    // ===== DeepFocus 新增 DAO =====

    @Provides
    fun provideFishCatalogDao(db: AppDatabase): FishCatalogDao = db.fishCatalogDao()

    @Provides
    fun provideUserFishDao(db: AppDatabase): UserFishDao = db.userFishDao()

    @Provides
    fun provideFoodCatalogDao(db: AppDatabase): FoodCatalogDao = db.foodCatalogDao()

    @Provides
    fun provideFoodInventoryDao(db: AppDatabase): FoodInventoryDao = db.foodInventoryDao()

    @Provides
    fun provideAffinityAchievementDao(db: AppDatabase): AffinityAchievementDao =
        db.affinityAchievementDao()

    // ===== AI 对话相关 DAO =====

    @Provides
    fun providePersonaStateDao(db: AppDatabase): PersonaStateDao = db.personaStateDao()

    @Provides
    fun providePersonaCardDao(db: AppDatabase): PersonaCardDao = db.personaCardDao()

    @Provides
    fun provideChatDao(db: AppDatabase): ChatDao = db.chatDao()

    /** 会话 DAO（开启新对话 = 归档旧会话） */
    @Provides
    fun provideChatConversationDao(db: AppDatabase): ChatConversationDao = db.chatConversationDao()

    @Provides
    fun provideFishStateDao(db: AppDatabase): FishStateDao = db.fishStateDao()

    @Provides
    fun provideFishMemoryDao(db: AppDatabase): FishMemoryDao = db.fishMemoryDao()

    /** 旅行日记 DAO */
    @Provides
    fun provideTravelJournalDao(db: AppDatabase): TravelJournalDao = db.travelJournalDao()

    // ===== AI 引擎组件 =====

    /**
     * AI 配置存储：用户自填的 API 地址 / 密钥 / 模型，长期保存在本机
     */
    @Provides
    @Singleton
    fun provideAiConfigStore(@ApplicationContext context: Context): AiConfigStore =
        AiConfigStore(context)

    /**
     * 世界感知提供器：把应用使用时长、专注时长、状态汇总给大肥鱼
     */
    @Provides
    @Singleton
    fun provideWorldContextProvider(@ApplicationContext context: Context): WorldContextProvider =
        WorldContextProvider(context)

    // ===== 原有 Repository =====

    @Provides
    @Singleton
    fun provideStatsRepository(dao: FocusSessionDao): StatsRepositoryImpl = StatsRepositoryImpl(dao)

    // ===== DeepFocus 新增 Repository =====

    /**
     * 提供 FishRepository，注入喂食频率限制用的 SharedPreferences（独立文件名，避免与设置项混淆）
     */
    @Provides
    @Singleton
    fun provideFishRepository(
        @ApplicationContext context: Context,
        db: AppDatabase,
        fishCatalogDao: FishCatalogDao,
        userFishDao: UserFishDao,
        achievementDao: AffinityAchievementDao,
        foodInventoryDao: FoodInventoryDao,
        fishStateDao: FishStateDao
    ): FishRepository = FishRepository(
        db = db,
        fishCatalogDao = fishCatalogDao,
        userFishDao = userFishDao,
        achievementDao = achievementDao,
        foodInventoryDao = foodInventoryDao,
        fishStateDao = fishStateDao,
        feedLimitPrefs = context.getSharedPreferences("deep_focus_feed_limit", Context.MODE_PRIVATE)
    )

    @Provides
    @Singleton
    fun provideFoodRepository(
        foodCatalogDao: FoodCatalogDao,
        foodInventoryDao: FoodInventoryDao
    ): FoodRepository = FoodRepository(foodCatalogDao, foodInventoryDao)

    /** 今日待办 DAO */
    @Provides
    fun provideTodoDao(db: AppDatabase): TodoDao = db.todoDao()

    /** 今日待办仓库 */
    @Provides
    @Singleton
    fun provideTodoRepository(todoDao: TodoDao): TodoRepository = TodoRepository(todoDao)

    /**
     * 大肥鱼的 agent 运行时
     *
     * 她靠这个跑「模型 → 调工具 → 回喂结果 → 最终答复」的循环。
     */
    @Provides
    @Singleton
    fun provideFishAgent(
        @ApplicationContext context: Context,
        aiConfigStore: AiConfigStore,
        fishRepository: FishRepository,
        statsRepository: StatsRepositoryImpl,
        worldContextProvider: WorldContextProvider,
        moodStore: MoodStore,
        todoRepository: TodoRepository
    ): FishAgent = FishAgent(
        context = context,
        aiConfigStore = aiConfigStore,
        fishRepository = fishRepository,
        statsRepository = statsRepository,
        worldContextProvider = worldContextProvider,
        moodStore = moodStore,
        todoRepository = todoRepository
    )
}
