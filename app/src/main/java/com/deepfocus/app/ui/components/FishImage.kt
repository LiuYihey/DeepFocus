package com.deepfocus.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.deepfocus.app.data.FishAnimationCatalog
import com.deepfocus.app.data.FishOutfit
import com.deepfocus.app.data.FishOutfits
import com.deepfocus.app.pet.PetAction
import com.deepfocus.app.pet.PetActionLibrary
import com.deepfocus.app.pet.PetActionTag
import com.deepfocus.app.pet.PetPlayEngine
import com.deepfocus.app.pet.PetPlayEngineEntryPoint
import com.deepfocus.app.pet.StageCatalog
import com.deepfocus.app.util.assetExists
import dagger.hilt.android.EntryPointAccessors
import kotlin.random.Random

/**
 * 当前装扮
 *
 * 立绘按这个走 —— 换成冬装后，首页 / 聊天初始界面 / 头像会一起换。
 * 在 `MainTabScreen` 顶部用 `CompositionLocalProvider` 注入，
 * 各个 `FishCanvas` 调用点就不用逐个传参。
 */
val LocalFishOutfit = compositionLocalOf { FishOutfits.NORMAL }

/**
 * 大肥鱼展示组件
 *
 * 动画全部走 GIF 转码的 animated WebP（见 [FishAnimationCatalog]），
 * 视频制作环节已取消。
 * - eating 状态：播喂食动画（随机挑一个动作）
 * - idle / sleeping 状态：随机播一段「登场 + 退场」小剧场
 *
 * **冬装没有配套的桌宠动画素材** —— 此时只显示静态立绘，
 * 不去播常服的动作（穿冬装播常服动画会穿帮）。
 *
 * @param state 动画状态（idle/eating/happy/sleeping）
 * @param modifier 布局修饰符
 * @param interactive 是否启用触控视差倾斜
 * @param useVideo 是否启用动画模式（ eating 时播喂食动画）
 * @param edgeFade 是否启用边缘柔化过渡
 * @param outfit 覆盖当前装扮。首页传 [FishOutfits.NORMAL] ——
 *   首页是「大肥鱼的舞台」，动作库只有常服一套，冬装没有配套 GIF；
 *   用户的要求是「首页 GIF 不用变」，装扮只体现在聊天页（头像 + 欢迎图）。
 */
@Composable
fun FishCanvas(
    state: String = "idle",
    modifier: Modifier = Modifier,
    interactive: Boolean = false,
    useVideo: Boolean = true,
    edgeFade: Boolean = true,
    /** 宽度按素材比例自适应（高度由 modifier 锁死）—— 近景舞台用 */
    fitWidth: Boolean = false,
    /** 覆盖当前装扮（null = 跟随全局 LocalFishOutfit） */
    outfit: FishOutfit? = null,
    /** 动画（单次）播放完成回调，用于 eating 等单次播放场景 */
    onVideoCompletion: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val effectiveOutfit = outfit ?: LocalFishOutfit.current

    // 预加载静态图片 bitmap，作为动画播放器的兜底
    val assetPath = effectiveOutfit.portraitAssetPath
    val bitmap = remember(assetPath) { loadAssetBitmap(context, assetPath) }

    // 冬装没有动画素材 —— 只显示静态立绘，不播常服的动作
    val animationAllowed = useVideo && effectiveOutfit.hasAnimatedVariant

    // === 选动作统一走 PetPlayEngine ===
    // 首页和桌宠共用同一个引擎单例，「最近播过」的记忆是共享的 ——
    // 首页刚播过的动作，桌宠不会紧接着再播一遍。
    val playEngine = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            PetPlayEngineEntryPoint::class.java
        ).petPlayEngine()
    }

    // 喂食动作：每次喂都**重新抽**一个吃的动作。
    //
    // 2026-11-18 用户反馈：「点击大白饭后大肥鱼吃饭的表情包只有一个吗，
    // 不可以有多个吃东西的表情包随机出现吗」
    //
    // 之前这里是 `remember { playEngine.onFeed() }` —— **一次性抽取，永不改变**。
    // 这是「每次喂都是同一个表情包」的真正根因
    // （不是只播白饭类的问题 —— FEED 标签下现在有 17 个吃的动作）。
    //
    // 改用 LaunchedEffect(state)：state 每次进 "eating" 都会重新触发，
    // 于是每次喂都换一个吃的动作。
    //
    // 素材缺失时保留上一个（而不是变成 null），
    // 否则会闪回静态立绘。
    var eatScene by remember { mutableStateOf<Pair<PetAction, String>?>(null) }
    LaunchedEffect(state) {
        if (state == "eating") {
            playEngine.onFeed()
                ?.let { it to PetActionLibrary.assetPath(it) }
                ?.takeIf { (_, path) -> context.assetExists(path) }
                ?.let { eatScene = it }
        }
    }

    // 登场短剧：从「有出场感 / 有表现力」的动作里挑。
    //
    // 同时用 StageCatalog 过滤 —— 那份名单只保留「角色高度与其他动作一致」的，
    // 横向摊得太开的动作（躺着的、挥手的）已隔离，
    // 否则上台时会出现「大一下小一下」。
    val appearanceScenes = remember {
        PetActionLibrary.ALL
            .filter { action ->
                action.tags.any {
                    it == PetActionTag.ENTER ||
                        it == PetActionTag.TRICK ||
                        it == PetActionTag.MOOD ||
                        it == PetActionTag.PLAY
                }
            }
            .filter { it.id in StageCatalog.STAGE_ACTIONS }
            .map { it to PetActionLibrary.assetPath(it) }
            .filter { (_, path) -> context.assetExists(path) }
    }

    val isEating = animationAllowed && state == "eating" && eatScene != null
    val isAppearance = animationAllowed && state in listOf("idle", "sleeping") &&
        appearanceScenes.isNotEmpty()

    if (isEating || isAppearance) {
        // 当前应播放的动画与回调
        val currentAssetPath: String
        val currentOnCompletion: (() -> Unit)?

        if (isEating) {
            currentAssetPath = eatScene!!.second
            currentOnCompletion = onVideoCompletion
        } else {
            // 登场短剧：播完一段换下一段。
            //
            // 旧版 loop = true —— 同一段被无限循环播，onCompletion 永远不触发，
            // 用户看到的就是同一个动作反复放（用户明确反馈过"很没趣味"）。
            //
            // 现在改成「播完换段」，而且用**发牌堆**抽：
            // 全部候选洗牌成一叠，从上往下取，一轮之内每个只出现一次。
            // （只记住最近 3 段是不够的 —— 用户反馈「还是经常能看到重复的 GIF」。）
            //
            // 发牌堆放在共享的 [PetPlayEngine] 里 —— 首页和桌宠共用同一叠牌，
            // 首页刚播过的动作，桌宠不会紧接着再播一遍。
            val playEngine = remember(context) {
                EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    PetPlayEngineEntryPoint::class.java
                ).petPlayEngine()
            }
            val candidateIds = remember(appearanceScenes) { appearanceScenes.map { it.first.id } }
            val pathById = remember(appearanceScenes) {
                appearanceScenes.associate { it.first.id to it.second }
            }

            var currentSceneId by remember(candidateIds) {
                mutableStateOf(
                    playEngine.nextAppearance(candidateIds) ?: candidateIds.first()
                )
            }
            currentAssetPath = pathById[currentSceneId] ?: appearanceScenes.first().second
            currentOnCompletion = {
                currentSceneId = playEngine.nextAppearance(candidateIds) ?: currentSceneId
            }
        }

        FishAnimation(
            assetPath = currentAssetPath,
            modifier = modifier,
            // 登场短剧不循环：播完自动换下一段（见上面的 currentOnCompletion）
            loop = false,
            fitWidth = fitWidth,
            fallbackPath = assetPath,
            onCompletion = currentOnCompletion
        )
        return
    }
    // 动画不存在 → 继续走图片分支显示静态占位

    // 其他状态：图片 + 动画
    // bitmap 已在顶部预加载，此处直接使用
    val infiniteTransition = rememberInfiniteTransition(label = "cat_img")

    val (targetScaleX, targetScaleY, targetTranslateY, targetRotation) = when (state) {
        "idle" -> Quad(
            scaleX = infiniteTransition.animateFloat(
                initialValue = 0.98f, targetValue = 1.02f,
                animationSpec = infiniteRepeatable(
                    tween(2200, easing = LinearEasing), RepeatMode.Reverse
                ), label = "idle_scale"
            ).value,
            scaleY = infiniteTransition.animateFloat(
                initialValue = 0.98f, targetValue = 1.02f,
                animationSpec = infiniteRepeatable(
                    tween(2200, easing = LinearEasing), RepeatMode.Reverse
                ), label = "idle_scale_y"
            ).value,
            translateY = 0f,
            rotation = 0f
        )
        "eating" -> Quad(
            scaleX = 1f,
            scaleY = infiniteTransition.animateFloat(
                initialValue = 0.96f, targetValue = 1.04f,
                animationSpec = infiniteRepeatable(
                    tween(400, easing = LinearEasing), RepeatMode.Reverse
                ), label = "eat_scale"
            ).value,
            translateY = infiniteTransition.animateFloat(
                initialValue = -2f, targetValue = 2f,
                animationSpec = infiniteRepeatable(
                    tween(300, easing = LinearEasing), RepeatMode.Reverse
                ), label = "eat_y"
            ).value,
            rotation = 0f
        )
        "happy" -> Quad(
            scaleX = 1f,
            scaleY = 1f,
            translateY = infiniteTransition.animateFloat(
                initialValue = 0f, targetValue = -16f,
                animationSpec = infiniteRepeatable(
                    tween(600, easing = LinearEasing), RepeatMode.Reverse
                ), label = "happy_y"
            ).value,
            rotation = infiniteTransition.animateFloat(
                initialValue = -3f, targetValue = 3f,
                animationSpec = infiniteRepeatable(
                    tween(400, easing = LinearEasing), RepeatMode.Reverse
                ), label = "happy_rot"
            ).value
        )
        "sleeping" -> Quad(
            scaleX = infiniteTransition.animateFloat(
                initialValue = 0.99f, targetValue = 1.01f,
                animationSpec = infiniteRepeatable(
                    tween(3000, easing = LinearEasing), RepeatMode.Reverse
                ), label = "sleep_scale"
            ).value,
            scaleY = infiniteTransition.animateFloat(
                initialValue = 0.99f, targetValue = 1.01f,
                animationSpec = infiniteRepeatable(
                    tween(3000, easing = LinearEasing), RepeatMode.Reverse
                ), label = "sleep_scale_y"
            ).value,
            translateY = 0f,
            rotation = 0f
        )
        else -> Quad(1f, 1f, 0f, 0f)
    }

    var tiltX by remember { mutableStateOf(0f) }
    var tiltY by remember { mutableStateOf(0f) }
    val animatedTiltX by animateFloatAsState(targetValue = tiltX, label = "tilt_x")
    val animatedTiltY by animateFloatAsState(targetValue = tiltY, label = "tilt_y")

    Box(
        modifier = modifier
            .then(
                if (interactive) {
                    Modifier.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.firstOrNull()?.let { change ->
                                    val cx = size.width / 2f
                                    val cy = size.height / 2f
                                    tiltX = ((change.position.y - cy) / cy) * 8f
                                    tiltY = ((change.position.x - cx) / cx) * 8f
                                }
                            }
                        }
                    }
                } else Modifier
            )
            .graphicsLayer {
                scaleX = targetScaleX
                scaleY = targetScaleY
                translationY = targetTranslateY
                rotationZ = targetRotation
                rotationX = animatedTiltX
                rotationY = animatedTiltY
            },
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "大肥鱼",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        Color(0xFFE4EAF0),
                        androidx.compose.foundation.shape.CircleShape
                    )
            )
        }
    }
}

private data class Quad(
    val scaleX: Float,
    val scaleY: Float,
    val translateY: Float,
    val rotation: Float
)

/**
 * 在登场短剧列表里随机取一个索引。
 *
 * @param exclude 需要避开的索引（上一段刚播完的），避免连续重复
 */
private fun List<Pair<PetAction, String>>.randomIndex(exclude: Int = -1): Int {
    if (isEmpty()) return 0
    if (size == 1) return 0
    val candidates = indices.filter { it != exclude }
    return candidates[Random.nextInt(candidates.size)]
}

/**
 * 从 assets 加载 Bitmap，失败返回 null
 */
private fun loadAssetBitmap(context: Context, path: String): Bitmap? {
    return try {
        context.assets.open(path).use { input ->
            BitmapFactory.decodeStream(input)
        }
    } catch (e: Exception) {
        null
    }
}
