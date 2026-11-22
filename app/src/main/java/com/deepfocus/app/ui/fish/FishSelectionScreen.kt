package com.deepfocus.app.ui.fish

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepfocus.app.data.FishOutfit
import com.deepfocus.app.ui.components.FishCanvas
import com.deepfocus.app.ui.components.LocalFishOutfit
import com.deepfocus.app.ui.components.PrimaryButton
import com.deepfocus.app.ui.components.SoftTextField
import com.deepfocus.app.ui.theme.DeepFocusMotion
import com.deepfocus.app.ui.theme.themedWeight

/**
 * 大肥鱼选择界面
 *
 * 只在**首次启动**时出现：选装扮（常服 / 冬装）+ 起名，创建这条鱼。
 * （「品种」已废弃 —— 只有一条大肥鱼；切换装扮在设置里随时能做。）
 */
@Composable
fun FishSelectionScreen(
    onFishCreated: () -> Unit,
    viewModel: FishSelectionViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // 创建完成后回调
    LaunchedEffect(state.isCreated) {
        if (state.isCreated) {
            onFishCreated()
        }
    }

    val accentColor = MaterialTheme.colorScheme.primary
    val hapticFeedback = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // 标题
        Text(
            text = "选择你的专注伙伴",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "挑一套她今天的样子，之后在设置里随时能换",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // === 装扮两卡 ===
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            state.outfits.forEach { outfit ->
                OutfitCard(
                    outfit = outfit,
                    isSelected = state.selectedOutfitId == outfit.id,
                    accentColor = accentColor,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.selectOutfit(outfit.id) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 命名输入框
        val nameIsError = state.errorMessage != null && state.fishName.isBlank()
        SoftTextField(
            value = state.fishName,
            onValueChange = { viewModel.updateName(it) },
            label = "给大肥鱼起个名字",
            placeholder = "例如：胖胖、鲸鲸...",
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(6.dp))

        // 字数 / 错误提示
        if (nameIsError) {
            Text(
                text = "请输入大肥鱼名字",
                color = com.deepfocus.app.ui.theme.ErrorText,
                fontSize = 13.sp
            )
        } else {
            Text(
                text = "${state.fishName.length}/12",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        state.errorMessage?.let { msg ->
            if (state.fishName.isNotBlank()) {
                Text(
                    text = msg,
                    color = com.deepfocus.app.ui.theme.ErrorText,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }

        // 确认选择按钮
        val canCreate = !state.isCreating && state.fishName.isNotBlank()
        PrimaryButton(
            text = "确认选择",
            onClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.createFish()
            },
            enabled = canCreate,
            loading = state.isCreating,
            containerColor = accentColor
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * 装扮卡片
 *
 * 立绘用 `LocalFishOutfit` 注入 —— 每张卡显示的是**它自己那套衣服**，
 * 而不是当前全局装扮。
 */
@Composable
private fun OutfitCard(
    outfit: FishOutfit,
    isSelected: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.03f else 1.0f,
        animationSpec = DeepFocusMotion.ZeroRebound,
        label = "outfit_scale"
    )
    val surfaceColor = MaterialTheme.colorScheme.surface
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) surfaceColor.copy(alpha = 0.8f) else surfaceColor,
        animationSpec = tween(durationMillis = 300),
        label = "outfit_container_color"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) accentColor else accentColor.copy(alpha = 0f),
        animationSpec = tween(durationMillis = 300),
        label = "outfit_border_color"
    )
    val hapticFeedback = LocalHapticFeedback.current

    Card(
        modifier = modifier
            .heightIn(min = 260.dp)
            .scale(scale)
            .clickable {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .border(2.dp, borderColor, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 立绘：方形裁切，占满卡片上半部分
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    CompositionLocalProvider(LocalFishOutfit provides outfit) {
                        FishCanvas(
                            state = "idle",
                            modifier = Modifier.fillMaxSize(),
                            useVideo = false
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = outfit.displayName,
                    style = MaterialTheme.typography.titleSmall
                        .copy(fontWeight = themedWeight(FontWeight.SemiBold))
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = outfit.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    lineHeight = 16.sp
                )
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
