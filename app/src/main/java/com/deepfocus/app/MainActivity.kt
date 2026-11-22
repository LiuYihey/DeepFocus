package com.deepfocus.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.deepfocus.app.data.SeedData
import com.deepfocus.app.data.local.dao.AffinityAchievementDao
import com.deepfocus.app.data.local.dao.FishCatalogDao
import com.deepfocus.app.data.local.dao.FoodCatalogDao
import com.deepfocus.app.detection.AppDetectionManager
import com.deepfocus.app.service.DeepFocusService
import com.deepfocus.app.service.KeepAliveManager
import com.deepfocus.app.ui.MainScreen
import com.deepfocus.app.ui.theme.DeepFocusTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 主 Activity
 * 应用入口，使用 Compose 构建 UI，使用 Hilt 进行依赖注入
 * 负责初始化种子数据、启动前台保活服务
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        /** Intent extra：专注模式防分心拉回标记，DeepFocusService 检测到用户切屏时带上 */
        const val EXTRA_FOCUS_MODE_RECALL = "focus_mode_recall"

        /** Intent extra：桌宠点击后要打开聊天页 */
        const val EXTRA_OPEN_CHAT = "open_chat"

        /** Intent extra：桌宠双击后要打开投喂页 */
        const val EXTRA_OPEN_FEEDING = "open_feeding"

        /** 一次性导航事件流：「去投喂」时发射 true，MainScreen 收集后跳大肥鱼 Tab */
        private val _navigateToFishEvent = MutableStateFlow(false)
        val navigateToFishEvent = _navigateToFishEvent.asStateFlow()

        /** 触发跳转到大肥鱼 Tab */
        fun requestNavigateToFish() {
            _navigateToFishEvent.value = true
        }

        /** 供 MainScreen 消费后调用：重置标志，避免重复跳转 */
        fun requestNavigateToFishConsumed() {
            _navigateToFishEvent.value = false
        }

        /** 一次性导航事件流：DeepFocusService 专注模式拉回时发射 true，MainScreen 跳 Focus 路由 */
        private val _focusRecallEvent = MutableStateFlow(false)
        val focusRecallEvent = _focusRecallEvent.asStateFlow()

        /** 供 DeepFocusService 调用：触发跳转回专注页 */
        fun requestFocusRecall() {
            _focusRecallEvent.value = true
        }

        /** 供 MainScreen 消费后调用：重置标志 */
        fun requestFocusRecallConsumed() {
            _focusRecallEvent.value = false
        }

        /** 一次性导航事件流：桌宠点击后发射 true，MainScreen 跳 Chat 路由 */
        private val _navigateToChatEvent = MutableStateFlow(false)
        val navigateToChatEvent = _navigateToChatEvent.asStateFlow()

        /** 供 DesktopPetService 调用：打开聊天页 */
        fun requestNavigateToChat() {
            _navigateToChatEvent.value = true
        }

        /** 供 MainScreen 消费后调用：重置标志 */
        fun requestNavigateToChatConsumed() {
            _navigateToChatEvent.value = false
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // 专注模式防分心拉回：DeepFocusService 检测到用户切屏后通过 Intent extra 通知
        if (intent.getBooleanExtra(EXTRA_FOCUS_MODE_RECALL, false)) {
            requestFocusRecall()
        }
        // 桌宠点击 → 打开聊天页
        if (intent.getBooleanExtra(EXTRA_OPEN_CHAT, false)) {
            requestNavigateToChat()
        }
        // 桌宠双击 → 打开投喂页（大肥鱼 Tab）
        if (intent.getBooleanExtra(EXTRA_OPEN_FEEDING, false)) {
            requestNavigateToFish()
        }
    }

    /** 大肥鱼形态目录 DAO */
    @Inject
    lateinit var fishCatalogDao: FishCatalogDao

    /** 食物目录 DAO */
    @Inject
    lateinit var foodFishCatalogDao: FoodCatalogDao

    /** 好感度成就 DAO */
    @Inject
    lateinit var achievementDao: AffinityAchievementDao

    override fun onCreate(savedInstanceState: Bundle?) {
        // 安装 Splash Screen（必须在 super.onCreate 之前）
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // 启用 edge-to-edge 沉浸式显示
        enableEdgeToEdge()
        // 初始化守护开关状态（从 SharedPreferences 恢复）
        AppDetectionManager.init(this)
        setContent {
            DeepFocusTheme {
                MainScreen()
            }
        }
        // 初始化所有种子数据
        initSeedData()
        // 启动前台保活服务
        startDeepFocusService()
        // 处理冷启动时的专注模式拉回 Intent（进程被杀后重新拉起）
        if (intent?.getBooleanExtra(EXTRA_FOCUS_MODE_RECALL, false) == true) {
            requestFocusRecall()
        }
        // 处理冷启动时的「去投喂」Intent（桌宠双击，进程未在运行时）
        if (intent?.getBooleanExtra(EXTRA_OPEN_FEEDING, false) == true) {
            requestNavigateToFish()
        }
    }

    /**
     * 初始化种子数据
     * 首次启动时插入：大肥鱼形态、食物目录、成就、格言
     */
    private fun initSeedData() {
        lifecycleScope.launch(Dispatchers.IO) {
            // 大肥鱼形态目录
            if (fishCatalogDao.getAllBreeds().isEmpty()) {
                fishCatalogDao.insertAll(SeedData.getSeedFishBreeds())
            }
            // 食物目录（只有大白饭一种）
            // 按 foodId 逐条补齐：老库里可能还留着已下架的食物行，
            // 多余的行不删（用户背包里可能还有存货），但目录本身要跟种子一致。
            val existingFoodIds = foodFishCatalogDao.getAllFoods()
                .map { it.foodId }
                .toSet()
            SeedData.getSeedFoods().forEach { seed ->
                if (seed.foodId !in existingFoodIds) {
                    foodFishCatalogDao.insertAll(listOf(seed))
                }
            }
            // 好感度成就
            // 按 milestone 逐条补齐：不能只在表空时插一次。
            // 否则版本更新新增的里程碑（如 milestone=200）对老用户永远不存在，
            // 而 milestone 是主键，重复插会直接冲突。
            val existingMilestones = achievementDao.getAllAchievements()
                .map { it.milestone }
                .toSet()
            SeedData.getSeedAchievements().forEach { seed ->
                if (seed.milestone in existingMilestones) {
                    // 已有记录：只同步文案（描述随版本调整）
                    achievementDao.updateDescription(seed.milestone, seed.description)
                } else {
                    achievementDao.insertAll(listOf(seed))
                }
            }
        }
    }

    /**
     * 启动前台保活服务
     * 仅在守护开关开启时启动，避免关闭状态下仍常驻通知
     */
    private fun startDeepFocusService() {
        if (!AppDetectionManager.isProtectionEnabled) return
        try {
            val intent = Intent(this, DeepFocusService::class.java)
            startForegroundService(intent)
        } catch (e: Exception) {
            // Android 12+ 后台启动前台服务受限，忽略避免崩溃
        }
        // 需求 1+4：进入 App 时确认保活任务已注册
        // （JobScheduler 持久化任务在应用被系统清理后可能丢失，每次打开应用补一次保险）
        KeepAliveManager.schedule(this)
        KeepAliveManager.checkAccessibilityHealth(this)
    }
}
