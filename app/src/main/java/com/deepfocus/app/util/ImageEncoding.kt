package com.deepfocus.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 聊天图片编码
 *
 * 用户从相册选一张图发给大肥鱼，要走两步：
 * 1. **存一份到 App 私有目录** —— 相册的 Uri 权限是临时的，
 *    重启后就读不到了，聊天记录里会变成一张裂图。
 * 2. **压小后转 base64** —— 原图动辄好几 MB，base64 后还要再涨三分之一，
 *    直接塞进请求体不仅慢，还可能被服务端拒掉。
 *
 * 压缩策略：长边压到 [MAX_EDGE] 以内，JPEG 质量 [QUALITY]。
 * 对「看懂图里是什么」这个需求来说，这个尺寸已经绰绰有余。
 */
object ImageEncoding {

    /** 长边上限（px） */
    private const val MAX_EDGE = 1024

    /** JPEG 压缩质量 */
    private const val QUALITY = 85

    /** 编码结果 */
    data class Encoded(
        /** base64（不含 data: 前缀），给模型看 */
        val base64: String,
        /** 本地文件绝对路径，给 UI 重画 */
        val localPath: String,
        /** MIME，固定 image/jpeg */
        val mime: String = "image/jpeg"
    )

    /**
     * 把用户选的图编码成「可发送」形态
     *
     * @param context 上下文
     * @param uri 相册 Uri（Photo Picker 返回的）
     * @return 编码结果；读不出来返回 null
     */
    fun encode(context: Context, uri: Uri): Encoded? {
        return try {
            // 1. 先读尺寸，决定采样率（避免把 4000px 的原图整张读进内存）
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val sample = calculateSampleSize(bounds.outWidth, bounds.outHeight)
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return null

            // 2. 再按长边缩到上限以内
            val scaled = scaleToMaxEdge(bitmap)
            if (scaled !== bitmap) bitmap.recycle()

            // 3. 压成 JPEG
            val bytes = ByteArrayOutputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
                out.toByteArray()
            }
            scaled.recycle()

            // 4. 落一份到私有目录（聊天记录长期可读）
            val dir = File(context.filesDir, "chat_images").apply { if (!exists()) mkdirs() }
            val file = File(dir, "chat_${System.currentTimeMillis()}.jpg")
            file.writeBytes(bytes)

            Encoded(
                base64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
                localPath = file.absolutePath
            )
        } catch (e: Exception) {
            null
        }
    }

    /** 按 2 的幂次采样，让解码后的图不超过长边上限的 2 倍 */
    private fun calculateSampleSize(width: Int, height: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (w / 2 >= MAX_EDGE && h / 2 >= MAX_EDGE) {
            w /= 2
            h /= 2
            sample *= 2
        }
        return sample
    }

    /** 把长边压到 [MAX_EDGE] 以内；已经够小就原样返回 */
    private fun scaleToMaxEdge(bitmap: Bitmap): Bitmap {
        val longEdge = maxOf(bitmap.width, bitmap.height)
        if (longEdge <= MAX_EDGE) return bitmap
        val ratio = MAX_EDGE.toFloat() / longEdge
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }
}
