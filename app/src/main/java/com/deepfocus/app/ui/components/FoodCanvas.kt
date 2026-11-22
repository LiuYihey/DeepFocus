package com.deepfocus.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AI 生成图片食物展示组件
 *
 * 使用 mmx image 生成的 3D 卡通食物 PNG 替代旧的 SVG 矢量拼凑绘制。
 * 接口与旧 FoodCanvas 完全一致（foodId / modifier / size），调用方零改动。
 *
 * 支持食物类型：大白饭 / 冻干 / 酸奶 / 罐头
 *
 * 素材已经是**透明背景**（见 tools/media/make_food_transparent.py），
 * 所以这里不再叠径向渐变遮罩 —— 那是给「白底方图」做柔化用的，
 * 素材透明后再叠就会在四周糊出一圈脏边。
 */
@Composable
fun FoodCanvas(
    foodId: String,
    modifier: Modifier = Modifier,
    size: Int = 120
) {
    val context = LocalContext.current
    val assetPath = remember(foodId) { "foods/$foodId.png" }

    val bitmapState = produceState<Bitmap?>(initialValue = null, assetPath) {
        value = withContext(Dispatchers.IO) {
            loadFoodAssetBitmap(context, assetPath)
        }
    }

    val bitmap = bitmapState.value

    Box(
        modifier = modifier.size(size.dp),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "食物 $foodId",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}

private fun loadFoodAssetBitmap(context: Context, path: String): Bitmap? {
    return try {
        context.assets.open(path).use { input ->
            BitmapFactory.decodeStream(input)
        }
    } catch (e: Exception) {
        null
    }
}
