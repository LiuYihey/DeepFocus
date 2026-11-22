package com.deepfocus.app.util

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File

/**
 * 把图片存进系统相册
 *
 * 两个分支，因为 API 29 前后机制完全不同：
 * - **API 29+**：走 MediaStore + RELATIVE_PATH，**不需要任何存储权限**
 * - **API 26–28**：先写到公共 Pictures 目录，再让 MediaScanner 扫一遍
 *   （这一步需要 WRITE_EXTERNAL_STORAGE，由调用方先确认已授权）
 *
 * 返回值是**可读的失败原因**（成功返回 null）——
 * 相册写入失败的原因对用户没有意义，但对我们排查有用。
 */
object MediaStoreSaver {

    private const val TAG = "MediaStoreSaver"
    private const val ALBUM_NAME = "DeepFocus"

    /**
     * 保存图片到相册
     *
     * @param context 上下文
     * @param bytes 图片字节（JPEG / PNG）
     * @param fileName 文件名（不含目录），如 "travel_123.jpg"
     * @param mimeType MIME，如 image/jpeg
     * @return 失败原因，成功为 null
     */
    fun saveImage(
        context: Context,
        bytes: ByteArray,
        fileName: String,
        mimeType: String = "image/jpeg"
    ): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveViaMediaStore(context, bytes, fileName, mimeType)
            } else {
                saveViaLegacy(context, bytes, fileName, mimeType)
            }
        } catch (e: Exception) {
            Log.w(TAG, "存相册失败", e)
            e.message ?: "未知错误"
        }
    }

    /** API 29+：MediaStore 直写，零权限 */
    private fun saveViaMediaStore(
        context: Context,
        bytes: ByteArray,
        fileName: String,
        mimeType: String
    ): String? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/$ALBUM_NAME"
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return "系统相册不可写"

        return try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: return "打不开相册写入流"

            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            null
        } catch (e: Exception) {
            // 写失败要把半成品删掉，否则相册里会留一张 0 字节的坏图
            runCatching { resolver.delete(uri, null, null) }
            e.message ?: "写入失败"
        }
    }

    /**
     * API 26–28：写应用自己的外部目录 + 主动扫描
     *
     * 刻意**不**写公共 Pictures 目录 —— 那需要 WRITE_EXTERNAL_STORAGE，
     * 而这个 App 一直保持零运行时权限。写 `getExternalFilesDir(Pictures)` 不需要权限，
     * 扫进媒体库后相册里照样能看到（代价是卸载 App 时图片会跟着删）。
     */
    private fun saveViaLegacy(
        context: Context,
        bytes: ByteArray,
        fileName: String,
        mimeType: String
    ): String? {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: return "外部存储不可用"
        val dir = File(base, ALBUM_NAME)
        if (!dir.exists() && !dir.mkdirs()) {
            return "创建相册目录失败"
        }

        val file = File(dir, fileName)
        file.writeBytes(bytes)

        MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            arrayOf(mimeType),
            null
        )
        return null
    }
}
