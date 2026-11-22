package com.deepfocus.app.pet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build

/**
 * 全量动作素材加载
 *
 * 素材路径规则见 [PetActionLibrary.assetPath]：
 *   assets/pet/anim/all/{序号}_{拼音名}.webp
 *
 * 与 [PetAnimDecoder] 的区别：
 * - `PetAnimDecoder` 服务于**姿态**（14 个固定姿态，路径短、复用率高）
 * - 这里服务**动作库**（109 个，路径长、加载频繁），所以带缓存
 *
 * 缓存策略：
 * - 动画（AnimatedImageDrawable）**不缓存** —— 它们有帧状态，
 *   同一实例不能被两处同时播。109 个动作但同时只播一个，
 *   每次新建的开销可以接受。
 * - 静态兜底图**缓存** —— Bitmap 大且复用频繁。
 */
object PetActionLoader {

    /** 静态图缓存。最多留 12 张，避免把内存吃满 */
    private const val STILL_CACHE_MAX = 12
    private val stillCache = object : LinkedHashMap<String, Bitmap>(
        STILL_CACHE_MAX, 0.75f, true
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, Bitmap>?
        ): Boolean = size > STILL_CACHE_MAX
    }

    /**
     * 加载动画
     *
     * @return AnimatedImageDrawable；失败返回 null（调用方退静态图）
     */
    fun loadAnimated(context: Context, action: PetAction): Drawable? {
        return loadAnimated(context, PetActionLibrary.assetPath(action))
    }

    /** 按路径加载动画 */
    fun loadAnimated(context: Context, assetPath: String): Drawable? {
        // ImageDecoder 是 API 28+；更低版本只能退静态图
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        return try {
            val bytes = context.assets.open(assetPath).use { it.readBytes() }
            val source = ImageDecoder.createSource(bytes)
            ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val longest = maxOf(w, h)
                // 桌宠窗口默认 160dp，素材 300px 足够；
                // 但有些设备密度高，放到 480 也不糊
                if (longest > 480) {
                    val s = 480f / longest
                    decoder.setTargetSize(
                        (w * s).toInt().coerceAtLeast(1),
                        (h * s).toInt().coerceAtLeast(1)
                    )
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 加载静态兜底图
     */
    fun loadStill(context: Context, action: PetAction): Bitmap? {
        val path = PetActionLibrary.stillPath(action)
        synchronized(stillCache) {
            stillCache[path]?.let { if (!it.isRecycled) return it }
        }
        return try {
            val bmp = context.assets.open(path).use {
                BitmapFactory.decodeStream(it)
            }
            synchronized(stillCache) {
                stillCache[path] = bmp
            }
            bmp
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 资源是否存在
     */
    fun exists(context: Context, action: PetAction): Boolean {
        return try {
            context.assets.open(PetActionLibrary.assetPath(action)).close()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** 清缓存（内存吃紧时调） */
    fun clearCache() {
        synchronized(stillCache) {
            stillCache.clear()
        }
    }
}
