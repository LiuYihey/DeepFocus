package com.deepfocus.app.ui.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepfocus.app.data.local.entity.TravelJournalEntity
import com.deepfocus.app.ui.components.EmptyState
import com.deepfocus.app.ui.components.GlassDialog
import com.deepfocus.app.ui.components.SubPageTopBar
import com.deepfocus.app.ui.theme.themedWeight
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.absoluteValue

/**
 * 游历本
 *
 * 她所有旅行日记的合集 —— 常服和冬装的都进同一本。
 *
 * 卡片优先展示相片；没相片时用目的地氛围色块 + 文字排版撑住，
 * 不会因为缺图就变成一条干巴巴的列表项。
 */
@Composable
fun TravelJournalScreen(
    onBack: () -> Unit = {},
    viewModel: TravelJournalViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val dateFormat = SimpleDateFormat("yyyy年M月d日", Locale.CHINA)
    val scope = rememberCoroutineScope()

    // 待确认删除的日记 —— 删了不可恢复，所以要点两次
    var pendingDelete by remember { mutableStateOf<TravelJournalEntity?>(null) }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            SubPageTopBar(title = "游历本", onBack = onBack)
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            if (uiState.journals.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.Palette,
                        title = "她还没出过远门",
                        description = "专注满 15 分钟，她会自己挑个地方去一趟，回来写日记。"
                    )
                }
            }

            items(uiState.journals, key = { it.id }) { journal ->
                JournalCard(
                    journal = journal,
                    dateText = dateFormat.format(Date(journal.createdAt)),
                    isPainting = uiState.paintingId == journal.id,
                    imageApiReady = uiState.imageApiReady,
                    onPaint = { viewModel.paint(journal) },
                    onSave = { viewModel.saveToAlbum(journal) },
                    onToggleFavorite = { viewModel.toggleFavorite(journal) },
                    onDelete = { pendingDelete = journal }
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            // 调试入口：不用真等 15 分钟就能验证「旅行 → 日记 → 生图」整条链路
            if (com.deepfocus.app.BuildConfig.DEBUG) {
                item {
                    TextButton(
                        onClick = { viewModel.travelNowForDebug() },
                        enabled = !uiState.isTraveling,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (uiState.isTraveling) "她正在路上…" else "（调试）现在让她去旅行")
                    }
                }
            }
        }
    }

    // 二次确认 —— 删除不可恢复，日记和配图都会没
    pendingDelete?.let { target ->
        DeleteJournalDialog(
            destination = target.destination,
            onDismiss = { pendingDelete = null },
            onConfirm = {
                pendingDelete = null
                scope.launch { viewModel.deleteJournal(target) }
            }
        )
    }
}

/**
 * 删除日记的确认弹窗
 *
 * 删了就没了 —— 日记正文和磁盘上的配图一起清掉，所以要再确认一次。
 */
@Composable
private fun DeleteJournalDialog(
    destination: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    GlassDialog(
        onDismissRequest = onDismiss,
        title = "删掉这趟旅行？",
        actions = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) { Text("再想想") }
                TextButton(onClick = onConfirm) {
                    Text("删掉", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    ) {
        Text(
            text = "「$destination」的日记和配图都会一起删掉，删了就找不回来了。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 一篇日记的卡片
 */
@Composable
private fun JournalCard(
    journal: TravelJournalEntity,
    dateText: String,
    isPainting: Boolean,
    imageApiReady: Boolean,
    onPaint: () -> Unit,
    onSave: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column {
            // 配图区：有图显示图，没图显示氛围色块
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
            ) {
                val bitmap = remember(journal.imagePath) {
                    TravelJournalViewModel.decodeImage(journal.imagePath)
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "${journal.destination}的旅行相片",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    DestinationBackdrop(destination = journal.destination)
                    if (isPainting) {
                        PaintingIndicator()
                    }
                }

                // 收藏
                Icon(
                    imageVector = if (journal.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = "收藏",
                    tint = if (journal.isFavorite) Color(0xFFFFC24B) else Color.White.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(24.dp)
                        .clickable { onToggleFavorite() }
                )
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = journal.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = themedWeight(FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "去了 ${journal.destination} · 心情 ${journal.mood} · $dateText",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = journal.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 删除 —— 放在操作排里，点开有二次确认
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "删掉这篇日记",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    if (journal.imagePath == null) {
                        TextButton(
                            onClick = onPaint,
                            enabled = !isPainting
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Palette,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(if (isPainting) "正在画…" else "画一张旅行相片")
                        }
                    } else {
                        TextButton(onClick = onSave) {
                            Icon(
                                imageVector = Icons.Filled.SaveAlt,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("保存到相册")
                        }
                    }
                }

                if (journal.imagePath == null && !imageApiReady) {
                    Text(
                        text = "她还不会画画 —— 去设置里填一个生图 API 就能补相片。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 没有相片时的目的地氛围色块
 *
 * 按目的地名字取一个稳定的色相，同一个地方每次颜色一致 ——
 * 这样「冰岛」看起来永远是冷色的，不会今天蓝明天粉。
 */
@Composable
private fun DestinationBackdrop(destination: String) {
    val hue = (destination.hashCode().absoluteValue % 360).toFloat()
    val top = Color.hsl(hue, 0.32f, 0.72f)
    val bottom = Color.hsl((hue + 28f) % 360f, 0.38f, 0.52f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(top, bottom)))
    ) {
        Text(
            text = destination,
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White.copy(alpha = 0.92f),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        )
    }
}

/**
 * 「正在画」的呼吸提示
 *
 * 对齐 ChatGPT 生成图片时的等待观感：一条缓慢流动的光带。
 */
@Composable
private fun PaintingIndicator() {
    val transition = rememberInfiniteTransition(label = "painting")
    val shift by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "painting_shift"
    )
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.0f),
                            Color.White.copy(alpha = 0.28f),
                            Color.White.copy(alpha = 0.0f)
                        ),
                        startX = shift * 900f,
                        endX = shift * 900f + 700f
                    )
                )
        )
        Text(
            text = "正在画…",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(8.dp)
        )
    }
}
