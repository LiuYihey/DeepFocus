package com.deepfocus.app.ai

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 生图客户端
 *
 * 干一件事：**带参考图**生成一张旅行相片。
 *
 * ## 为什么参考图是强制的
 *
 * 没有参考图，模型就不知道画的是谁 —— 每次旅行出来的她会变成另一个人。
 * 所以每次调用都必须带参考图（当前装扮的立绘），
 * 这是「生图加进日记本」这件事能不能成立的前提。
 *
 * ## 两种协议
 *
 * 参考图怎么带，两家不一样，必须在客户端层区分：
 * - MiniMax：请求体是 JSON，参考图是 base64 data URI 塞在 `images[].image_url`
 * - OpenAI 原生：请求体是 multipart/form-data，参考图作为文件流传
 *
 * 走哪个由厂商预设决定（[ImageGenProvider.referenceMode]），
 * 用户在设置里换预设就自动切协议。
 */
class ImageGenClient @javax.inject.Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) {

    /**
     * 生成一张图
     *
     * @param config 生图配置
     * @param prompt 生图提示词（决定拍什么、什么风格）
     * @param referenceImage 参考图字节（PNG / JPEG）
     * @param referenceMime 参考图 MIME，如 image/png
     * @param aspectRatio 画幅比例，如 "3:4" —— MiniMax 专用
     * @param area 清晰度档位，如 "1K" —— MiniMax 专用
     * @return 生成的图片字节
     */
    suspend fun generate(
        config: ImageGenConfig,
        prompt: String,
        referenceImage: ByteArray,
        referenceMime: String,
        aspectRatio: String = "3:4",
        area: String = "1K"
    ): ByteArray = withContext(Dispatchers.IO) {
        if (!config.isValid) {
            throw AiException("生图 API 未配置完整")
        }

        val connection = (URL(config.imageEndpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            // 生图比聊天慢得多（MiniMax 实测要几十秒）
            readTimeout = 180_000
            doOutput = true
            useCaches = false
            setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            setRequestProperty("Accept", "application/json")
        }

        try {
            when (config.provider.referenceMode) {
                ImageGenReferenceMode.JSON_BASE64 -> {
                    connection.setRequestProperty("Content-Type", "application/json")
                    val body = buildJsonBody(
                        config = config,
                        prompt = prompt,
                        referenceImage = referenceImage,
                        referenceMime = referenceMime,
                        aspectRatio = aspectRatio,
                        area = area
                    )
                    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }

                ImageGenReferenceMode.MULTIPART -> {
                    connection.setRequestProperty("Content-Type", "multipart/form-data")
                    writeMultipart(
                        connection = connection,
                        config = config,
                        prompt = prompt,
                        referenceImage = referenceImage,
                        referenceMime = referenceMime
                    )
                }
            }

            val status = connection.responseCode
            val text = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (status !in 200..299) {
                throw AiException("生图失败（HTTP $status）：${extractError(text)}")
            }

            parseImageBytes(text)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * 构造 JSON 请求体（MiniMax 协议）
     *
     * 参考图走 `images[].image_url`，是完整的 data URI。
     */
    private fun buildJsonBody(
        config: ImageGenConfig,
        prompt: String,
        referenceImage: ByteArray,
        referenceMime: String,
        aspectRatio: String,
        area: String
    ): String {
        val dataUri = "data:$referenceMime;base64," +
            Base64.encodeToString(referenceImage, Base64.NO_WRAP)

        val image = JSONObject()
            .put("image_url", dataUri)

        val images = org.json.JSONArray().put(image)

        return JSONObject()
            .put("model", config.model)
            .put("prompt", prompt)
            .put("images", images)
            // aspect_ratio 和 area 是 MiniMax 专用；
            // 和 size 互斥（同时传会 400），所以这里只传这两个。
            .put("aspect_ratio", aspectRatio)
            .put("area", area)
            .put("n", 1)
            .put("response_format", "url")
            .toString()
    }

    /**
     * 写 multipart 请求体（OpenAI 原生协议）
     *
     * 参考图作为文件流上传，字段名 `image`。
     */
    private fun writeMultipart(
        connection: HttpURLConnection,
        config: ImageGenConfig,
        prompt: String,
        referenceImage: ByteArray,
        referenceMime: String
    ) {
        val boundary = "----DeepFocus${System.currentTimeMillis()}"
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

        DataOutputStream(connection.outputStream).use { out ->
            out.writeBytes("--$boundary\r\n")
            out.writeBytes("Content-Disposition: form-data; name=\"model\"\r\n\r\n")
            out.writeBytes("${config.model}\r\n")

            out.writeBytes("--$boundary\r\n")
            out.writeBytes("Content-Disposition: form-data; name=\"prompt\"\r\n\r\n")
            out.writeBytes(prompt)

            out.writeBytes("--$boundary\r\n")
            out.writeBytes(
                "Content-Disposition: form-data; name=\"image\"; filename=\"reference.${extensionOf(referenceMime)}\"\r\n"
            )
            out.writeBytes("Content-Type: $referenceMime\r\n\r\n")
            out.write(referenceImage)
            out.writeBytes("\r\n")

            out.writeBytes("--$boundary--\r\n")
        }
    }

    /**
     * 从响应里取出图片字节
     *
     * 两种返回形式都认：
     * - `data[0].url` —— MiniMax 返回临时链接，要再下载一次
     * - `data[0].b64_json` —— 直接是 base64
     */
    private fun parseImageBytes(responseText: String): ByteArray {
        val root = runCatching { JSONObject(responseText) }.getOrElse {
            throw AiException("生图响应解析失败：${it.message}")
        }
        val first = root.optJSONArray("data")?.optJSONObject(0)
            ?: throw AiException("生图响应里没有图片：${root.optString("error")}")

        first.optString("b64_json").takeIf { it.isNotBlank() }?.let { b64 ->
            return runCatching { Base64.decode(b64, Base64.DEFAULT) }
                .getOrElse { throw AiException("生图 base64 解码失败：${it.message}") }
        }

        val url = first.optString("url")
        if (url.isNotBlank()) {
            return downloadImage(url)
        }

        throw AiException("生图响应里既没有 url 也没有 b64_json")
    }

    /** 下载图片字节 */
    private fun downloadImage(url: String): ByteArray {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 30_000
            readTimeout = 120_000
        }
        return try {
            val status = connection.responseCode
            if (status !in 200..299) {
                throw AiException("下载生图结果失败（HTTP $status）")
            }
            val buffer = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                input.copyTo(buffer)
            }
            buffer.toByteArray()
        } finally {
            connection.disconnect()
        }
    }

    /** 从错误响应里挖出可读信息 */
    private fun extractError(text: String): String {
        return runCatching {
            val root = JSONObject(text)
            root.optJSONObject("error")?.optString("message")
                ?: root.optString("message")
                ?: root.optString("detail")
                ?: text.take(200)
        }.getOrElse { text.take(200) }
    }

    /** MIME → 扩展名 */
    private fun extensionOf(mime: String): String =
        if (mime.contains("png")) "png" else "jpg"

    /**
     * 读 assets 里的参考图
     *
     * @param assetPath assets 下的相对路径，如 fish/bigfatfish.png
     */
    fun readAssetBytes(assetPath: String): ByteArray {
        return try {
            context.assets.open(assetPath).use { it.readBytes() }
        } catch (e: Exception) {
            throw AiException("读不到参考图 $assetPath：${e.message}")
        }
    }
}
