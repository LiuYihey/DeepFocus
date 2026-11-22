package com.deepfocus.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.deepfocus.app.MainActivity
import com.deepfocus.app.R
import com.deepfocus.app.ui.fish.FishScreen
import com.deepfocus.app.ui.components.SimpleNavEntry
import com.deepfocus.app.ui.components.SimpleNavigationBar
import com.deepfocus.app.ui.fish.FishSelectionScreen
import com.deepfocus.app.ui.todo.TodoScreen
import com.deepfocus.app.ui.focus.FocusScreen
import com.deepfocus.app.ui.settings.SettingsScreen
import com.deepfocus.app.ui.chat.ChatScreen
import com.deepfocus.app.ui.settings.AiSettingsScreen
import com.deepfocus.app.ui.settings.PersonaScreen
import com.deepfocus.app.ui.settings.MemoryScreen
import com.deepfocus.app.ui.settings.ImageGenSettingsScreen
import com.deepfocus.app.ui.settings.TravelJournalScreen
import com.deepfocus.app.ui.settings.PetSettingsScreen
import com.deepfocus.app.ui.onboarding.OnboardingStore
import com.deepfocus.app.ui.onboarding.PermissionSetupScreen
import com.deepfocus.app.ui.theme.themedWeight

/**
 * 底部导航项数据类
 */
sealed class BottomNavItem(
    val route: String,
    val title: String
) {
    /** 大肥鱼（默认首页） */
    object Fish : BottomNavItem("fish", "大肥鱼")

    /** 与大肥鱼对话（取代原统计页） */
    object Chat : BottomNavItem("chat", "聊天")

    /** 今日待办（2026-11-18 用户要求新增） */
    object Todo : BottomNavItem("todo", "待办")

    /** 设置 */
    object Settings : BottomNavItem("settings", "设置")
}

/** 所有底部导航项列表 */
private val bottomNavItems = listOf(
    BottomNavItem.Fish,
    BottomNavItem.Chat,
    BottomNavItem.Todo,
    BottomNavItem.Settings
)

/**
 * 底部导航栏渲染用的数据（与路由表一一对应）
 *
 * 「大肥鱼」Tab 用 **DeepSeek 官方鲸鱼标志位图**（`ic_deepseek_whale.png`）——
 * 爪印是 FocusCat 的遗留，水波纹太抽象；这个 App 的一切思考都来自 DeepSeek，
 * 用它的标志既有辨识度又点题。
 *
 * 位图是**品牌渐变**，不能跟着主题色 tint —— 所以这两项的 tint 都是
 * `Color.Unspecified`（不加 ColorFilter，保留原色）。
 * 选中态靠下方的蜜糖色胶囊 + 标签色区分。
 */
@Composable
private fun bottomNavEntries(
    selectedTint: Color,
    unselectedTint: Color
): List<SimpleNavEntry> = listOf(
    SimpleNavEntry(
        route = BottomNavItem.Fish.route,
        label = BottomNavItem.Fish.title,
        icon = painterResource(R.drawable.ic_deepseek_whale),
        tintSelected = Color.Unspecified,
        tintUnselected = Color.Unspecified,
        // 位图比方形矢量窄，放大一点视觉重量才配得上旁边两个方块
        iconSize = 26.dp
    ),
    SimpleNavEntry(
        route = BottomNavItem.Chat.route,
        label = BottomNavItem.Chat.title,
        icon = rememberVectorPainter(Icons.Filled.Chat),
        tintSelected = selectedTint,
        tintUnselected = unselectedTint
    ),
    SimpleNavEntry(
        route = BottomNavItem.Todo.route,
        label = BottomNavItem.Todo.title,
        icon = rememberVectorPainter(Icons.Filled.EditNote),
        tintSelected = selectedTint,
        tintUnselected = unselectedTint
    ),
    SimpleNavEntry(
        route = BottomNavItem.Settings.route,
        label = BottomNavItem.Settings.title,
        icon = rememberVectorPainter(Icons.Filled.Settings),
        tintSelected = selectedTint,
        tintUnselected = unselectedTint
    )
)

/**
 * 主界面入口
 * 根据大肥鱼选择状态决定显示选择页还是主界面
 */
@Composable
fun MainScreen(
    mainViewModel: MainViewModel = hiltViewModel()
) {
    val state by mainViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 当前装扮：换装后所有 FishCanvas 一起换，不用重启
    val outfit by mainViewModel.outfit.collectAsStateWithLifecycle(
        initialValue = com.deepfocus.app.data.FishOutfits.NORMAL
    )

    // 状态切换使用 AnimatedContent 淡入淡出，避免 Loading → Ready 的硬切
    AnimatedContent(
        targetState = state,
        transitionSpec = {
            fadeIn(tween(400)) togetherWith fadeOut(tween(300))
        },
        label = "main_state"
    ) { currentState ->
        when (currentState) {
            MainUiState.Loading -> {
                // 入场淡入，避免突兀闪现
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { visible = true }
                val alpha by animateFloatAsState(
                    targetValue = if (visible) 1f else 0f,
                    animationSpec = tween(300),
                    label = "loading_fade"
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(alpha),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_deepseek_whale),
                        contentDescription = null,
                        // 品牌渐变图标不挂 ColorFilter
                        tint = Color.Unspecified,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "DeepFocus",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = themedWeight(FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            MainUiState.NeedFishSelection -> {
                FishSelectionScreen(
                    onFishCreated = { mainViewModel.checkFishExistence() }
                )
            }

            MainUiState.NeedPermissionSetup -> {
                PermissionSetupScreen(
                    onDone = {
                        OnboardingStore.markGuideSeen(context)
                        mainViewModel.checkFishExistence()
                    }
                )
            }

            MainUiState.Ready -> {
                // 装扮从这里注入：首页 / 聊天 / 头像 / 生图预览都读同一个 LocalFishOutfit
                androidx.compose.runtime.CompositionLocalProvider(
                    com.deepfocus.app.ui.components.LocalFishOutfit provides outfit
                ) {
                    MainTabScreen()
                }
            }        }
    }
}

/**
 * 主界面（底部导航 + 各页面）
 */
@Composable
private fun MainTabScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val hapticFeedback = LocalHapticFeedback.current

    // 统一的 Tab 切换导航：底部导航与首页卡片入口使用同一套逻辑，
    // 保证从首页进入大肥鱼后，底部点首页能正常返回（行为一致）
    // 硬约束：所有 Tab 导航必须经此 helper，禁止直接 navController.navigate 切 Tab
    val navigateToTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    // 收集「去投喂」导航事件：StateFlow 持续收集，即使 MainActivity 已在前台也能响应
    val navigateToFish by com.deepfocus.app.MainActivity.navigateToFishEvent.collectAsStateWithLifecycle()
    LaunchedEffect(navigateToFish) {
        if (navigateToFish) {
            // 消费事件：重置标志并跳转到大肥鱼 Tab（经 navigateToTab 统一导航）
            com.deepfocus.app.MainActivity.requestNavigateToFishConsumed()
            navigateToTab(BottomNavItem.Fish.route)
        }
    }

    // 收集「专注模式拉回」事件：DeepFocusService 检测到用户切屏时触发
    // 用户切到其他应用 → 服务拉回 MainActivity → MainScreen 跳到 Focus 路由
    val focusRecall by com.deepfocus.app.MainActivity.focusRecallEvent.collectAsStateWithLifecycle()
    LaunchedEffect(focusRecall) {
        if (focusRecall) {
            com.deepfocus.app.MainActivity.requestFocusRecallConsumed()
            // 跳到 Focus 路由（全屏页，非 Tab；若已在 Focus 页则 launchSingleTop 保证不重复创建）
            navController.navigate("focus") {
                launchSingleTop = true
            }
        }
    }

    // 收集「桌宠点击」事件：DesktopPetService 发射 → 跳到 Chat 路由
    val navigateToChat by com.deepfocus.app.MainActivity.navigateToChatEvent.collectAsStateWithLifecycle()
    LaunchedEffect(navigateToChat) {
        if (navigateToChat) {
            com.deepfocus.app.MainActivity.requestNavigateToChatConsumed()
            navController.navigate("chat") {
                launchSingleTop = true
            }
        }
    }

    // 当前路由是否为全屏页（专注模式）：全屏时隐藏底部导航栏
    val currentRoute = currentDestination?.route
    val isFullScreen = currentRoute == "focus"

    // 二级页（设置子页）也是沉浸的：它们有自己的返回栏，
    // 再挂一层底部 Tab 会让内容被压住半截，也让人误以为还能在 Tab 间跳。
    val subPageRoutes = setOf("ai_settings", "persona", "journals", "image_settings", "memory", "pet")
    val isSubPage = currentRoute in subPageRoutes

    // 底栏数据要在组合期构造（位图要走 painterResource）
    val navScheme = MaterialTheme.colorScheme
    val navEntries = bottomNavEntries(
        selectedTint = navScheme.primary,
        unselectedTint = navScheme.onSurfaceVariant
    )

    Scaffold(
        bottomBar = {
            // 全屏页（专注模式）不显示底部导航，避免用户切到其他 Tab 导致 bug
            if (!isFullScreen && !isSubPage) {
                // 简洁底栏：自绘（非 M3 NavigationBar）
                // M3 的 NavigationBar 会把底部系统手势 inset 挤进固定高度，
                // 导致图标上方留白被压没、选中胶囊被裁切，故改为自绘以精确控制留白。
                // 尝试过液态玻璃（多层 Canvas 模拟折射），在纯色背景上只呈现"脏边缘"，
                // 反而破坏了简洁感，故回退到此方案。
                SimpleNavigationBar(
                    items = navEntries,
                    selectedRoute = currentDestination?.hierarchy
                        ?.firstOrNull { dest -> navEntries.any { it.route == dest.route } }
                        ?.route,
                    onSelect = { route ->
                        // Tab 切换是高频轻操作，按项目触觉规范使用 TextHandleMove（轻震）
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        navigateToTab(route)
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomNavItem.Fish.route,
            // 全屏页不应用 bottom padding，让视频真正铺满全屏。
            // 二级页同理：它有自己的 Scaffold，自己管系统栏 inset，
            // 这里再补一层会变成双倍底部留白。
            modifier = if (isFullScreen || isSubPage) {
                Modifier.fillMaxSize()
            } else {
                Modifier.padding(innerPadding)
            },
            enterTransition = { fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.96f, animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(200)) + scaleOut(targetScale = 1.02f, animationSpec = tween(200)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.96f, animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(200)) + scaleOut(targetScale = 1.02f, animationSpec = tween(200)) }
        ) {
            // 大肥鱼养成（默认首页）
            composable(BottomNavItem.Fish.route) {
                FishScreen(
                    onNavigateToFocus = { navController.navigate("focus") }
                )
            }
            // 与大肥鱼对话（取代原统计页）
            composable(BottomNavItem.Chat.route) {
                ChatScreen(
                    onOpenPersona = { navController.navigate("persona") },
                    onOpenAiSettings = { navController.navigate("ai_settings") },
                    onOpenMemory = { navController.navigate("memory") }
                )
            }
            // 今日待办（2026-11-18 用户要求新增）
            composable(BottomNavItem.Todo.route) {
                TodoScreen()
            }
            // 设置
            composable(BottomNavItem.Settings.route) {
                SettingsScreen(
                    onNavigateToAiSettings = {
                        navController.navigate("ai_settings")
                    },
                    onNavigateToPersona = {
                        navController.navigate("persona")
                    },
                    onNavigateToJournals = {
                        navController.navigate("journals")
                    },
                    onNavigateToImageSettings = {
                        navController.navigate("image_settings")
                    },
                    onNavigateToMemory = {
                        navController.navigate("memory")
                    },
                    onNavigateToPet = {
                        navController.navigate("pet")
                    }
                )
            }
            // 专注模式（全屏，无底部导航）
            composable(
                route = "focus",
                enterTransition = { fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.92f, animationSpec = tween(300)) },
                exitTransition = { fadeOut(animationSpec = tween(200)) + scaleOut(targetScale = 1.05f, animationSpec = tween(200)) },
                popEnterTransition = { fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.92f, animationSpec = tween(300)) },
                popExitTransition = { fadeOut(animationSpec = tween(200)) + scaleOut(targetScale = 1.05f, animationSpec = tween(200)) }
            ) {
                FocusScreen(
                    onExit = { navController.popBackStack() }
                )
            }
            // API 配置
            composable(route = "ai_settings") {
                AiSettingsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            // 大肥鱼人格卡
            composable(route = "persona") {
                PersonaScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            // 游历本（大肥鱼的旅行日记）
            composable(route = "journals") {
                TravelJournalScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            // 生图 API 配置（独立于聊天）
            composable(route = "image_settings") {
                ImageGenSettingsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            // 大肥鱼的记忆
            composable(route = "memory") {
                MemoryScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            // 桌面大肥鱼（桌宠）
            composable(route = "pet") {
                PetSettingsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
