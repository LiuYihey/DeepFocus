package com.deepfocus.app.ui.components

import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.deepfocus.app.util.assetExists

/**
 * 动画素材播放器（animated WebP / GIF）
 *
 * 为什么用这个而不是 [FishVideo]：
 * - 视频是 15 秒大文件，一个 App 装几十 MB；GIF/WebP 动画几百 KB
 * - dsh-pet 那套动作库有一百多个透明 GIF，转码成 animated WebP 后
 *   既有动作又有体积优势 —— 所以首页的登场、喂食、换装全部改用动画
 * - 唯一还需要视频的是**专注页书房**（9:16 全屏 + 环境音），那个继续用 ExoPlayer
 *
 * 兼容性：
 * - [ImageDecoder] 是 API 28+，minSdk 26 上更低版本退化为静态图
 * - 静态兜底图由调用方提供（`fallbackPath`），所以老设备也有画面
 *
 * @param assetPath assets 内路径，如 "fish/anim/eating.webp"
 * @param modifier 布局修饰符
 * @param loop 是否循环播放
 * @param contentScale 缩放方式（默认 Fit，保持比例不裁切）
 * @param fitWidth true = 宽度按素材比例自适应、高度由 modifier 锁死。
 *   近景素材的画布比例各不相同（横向伸展的动作更宽），
 *   用这个模式才能让各动作的**角色高度完全一致**，
 *   避免出现「有的 GIF 大、有的 GIF 小」。
 * @param fallbackPath 动画不可用时显示的静态图路径
 * @param onCompletion 播完一轮的回调（loop=false 时用）
 */
@Composable
fun FishAnimation(
    assetPath: String,
    modifier: Modifier = Modifier,
    loop: Boolean = true,
    contentScale: ContentScale = ContentScale.Fit,
    fitWidth: Boolean = false,
    fallbackPath: String? = null,
    onCompletion: (() -> Unit)? = null
) {
    val context = LocalContext.current

    // 动画是否可用（API 28+ 且文件存在）
    val supported = remember(assetPath) {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && context.assetExists(assetPath)
    }

    // 静态兜底图
    val fallbackBitmap = remember(fallbackPath) {
        fallbackPath?.let { path ->
            if (context.assetExists(path)) {
                runCatching {
                    context.assets.open(path).use {
                        android.graphics.BitmapFactory.decodeStream(it)
                    }
                }.getOrNull()
            } else null
        }
    }

    if (!supported) {
        // 老设备或文件缺失：显示静态图
        Box(modifier = modifier) {
            fallbackBitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        return
    }

    // 解码动画。失败就退静态图。
    var animated by remember(assetPath) { mutableStateOf<Drawable?>(null) }
    var decodeFailed by remember(assetPath) { mutableStateOf(false) }

    DisposableEffect(assetPath) {
        val drawable = runCatching {
            val bytes = context.assets.open(assetPath).use { it.readBytes() }
            val source = ImageDecoder.createSource(bytes)
            ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
                // 必须显式指定解码目标尺寸 = 素材原始像素。
                //
                // ImageDecoder 默认会按**屏幕密度**放大解码结果：
                // 512px 的素材在 3x 屏上会被解成 1536px，
                // 再被 ImageView 缩回容器 —— 一来一回细节全丢，
                // 看起来就是"糊"。（这是之前 GIF 变糊的真正原因，跟容器尺寸无关。）
                //
                // setTargetSize 到原始尺寸 + sampleSize=1 = 严格 1:1 像素，
                // 运行时零重采样。
                decoder.setTargetSampleSize(1)
                decoder.setTargetSize(info.size.width, info.size.height)
            }
        }.getOrNull()

        if (drawable == null) {
            decodeFailed = true
        } else {
            animated = drawable
            if (drawable is AnimatedImageDrawable) {
                // repeatCount: -1 = 无限循环，0 = 播一遍就停
                drawable.repeatCount = if (loop) -1 else 0
                // 播完一轮的回调。
                // AnimatedImageDrawable 实现了 Animatable2，
                // 它的 AnimationCallback 直接给 onAnimationEnd —— 比轮询帧号可靠。
                if (!loop) {
                    drawable.registerAnimationCallback(
                        object : Animatable2.AnimationCallback() {
                            override fun onAnimationEnd(d: Drawable) {
                                onCompletion?.invoke()
                            }
                        }
                    )
                }
                drawable.start()
            }
        }
        onDispose {
            (animated as? AnimatedImageDrawable)?.stop()
            animated = null
        }
    }

    val drawable = animated
    if (drawable == null || decodeFailed) {
        Box(modifier = modifier) {
            fallbackBitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        return
    }

    AndroidView(
        factory = { ctx ->
            ImageView(ctx).apply {
                this.setImageDrawable(drawable)
                scaleType = when (contentScale) {
                    ContentScale.Crop -> ImageView.ScaleType.CENTER_CROP
                    ContentScale.FillBounds -> ImageView.ScaleType.FIT_XY
                    else -> ImageView.ScaleType.FIT_CENTER
                }
                // 素材是「短边统一、长边按比例」的画布（不是正方形），
                // 让 ImageView 按比例自适应宽度，高度由外层容器锁死。
                // 这样各动作的角色高度完全一致 —— 不会有的大一下小一下。
                adjustViewBounds = fitWidth
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        update = { view ->
            view.setImageDrawable(drawable)
            (drawable as? AnimatedImageDrawable)?.let {
                if (!it.isRunning) it.start()
            }
        },
        modifier = if (fitWidth) {
            // 宽度按素材比例自适应（wrap content），
            // 高度沿用调用方给的高度 —— 角色高度因此完全一致。
            modifier.wrapContentWidth()
        } else {
            modifier
        }
    )
}
