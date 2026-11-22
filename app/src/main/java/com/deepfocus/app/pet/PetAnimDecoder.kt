package com.deepfocus.app.pet

import android.content.Context
import android.graphics.ImageDecoder
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * animated WebP 解码器
 *
 * 桌宠的 P5 动画素材是 animated WebP（512 长边、20~30 帧、透明背景）。
 *
 * 兼容性说明：
 * - Android 9（API 28）起才有 [ImageDecoder]，才能解 animated WebP
 * - 本项目 minSdk = 26，所以 API 26/27 上退化为静态贴图（功能不崩，只是少动画）
 * - 所有 API 28 专属的类型都关在这个文件里，
 *   避免老设备在类加载阶段就因为找不到 `AnimatedImageDrawable` 而崩
 */
object PetAnimDecoder {

    /** 当前系统是否支持 animated WebP */
    val supported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    /**
     * 解码 assets 下的 animated WebP
     *
     * @param path assets 内路径，如 "pet/anim/idle.webp"
     * @param maxSize 解码后的长边上限（px），避免大图吃内存
     * @return 解码成功返回 AnimatedImageDrawable，失败返回 null
     */
    @RequiresApi(Build.VERSION_CODES.P)
    fun decode(context: Context, path: String, maxSize: Int = 640): android.graphics.drawable.Drawable? {
        return try {
            // 先读成字节再解码：ImageDecoder 对流的持有时机不好把握，读成字节最稳
            val bytes = context.assets.open(path).use { it.readBytes() }
            val source = ImageDecoder.createSource(bytes)
            ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
                // 1:1 采样，不做降采样。
                // 素材已是 512x512，桌宠窗口只有 160dp，
                // 让解码器按屏幕密度放大只会白白多占内存。
                decoder.setTargetSampleSize(1)
                // 超大素材再降一档，避免吃内存
                val w = info.size.width
                val h = info.size.height
                val longest = maxOf(w, h)
                if (longest > maxSize) {
                    val s = maxSize.toFloat() / longest
                    decoder.setTargetSize(
                        (w * s).toInt().coerceAtLeast(1),
                        (h * s).toInt().coerceAtLeast(1)
                    )
                }
            }
        } catch (e: Exception) {
            // 素材损坏 / 格式不支持 → 退化成静态贴图，不影响功能
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }
}
