package com.deepfocus.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepfocus.app.ai.FishMoodResolver
import com.deepfocus.app.data.BuiltinPersonaCard
import com.deepfocus.app.data.local.entity.PersonaCardEntity
import com.deepfocus.app.ui.components.SoftTextField
import com.deepfocus.app.ui.components.SubPageTopBar
import com.deepfocus.app.ui.components.iconBaselineOffset
import com.deepfocus.app.ui.theme.themedWeight

/**
 * 人格卡页
 *
 * 三块内容：
 * 1. **当前生效卡** —— 谁在说话，一眼看清
 * 2. **内置人格卡包** —— 6 张，随好感度自动切换，用户不干预
 * 3. **我的自定义卡** —— 用户改出来的，可主动切
 *
 * 最下面是当前生效卡的编辑区（改动写进覆盖层，跨档仍然跟着走）。
 */
@Composable
fun PersonaScreen(
    onBack: () -> Unit = {},
    viewModel: PersonaViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 回到前台时重新载入，保证屏幕上是当前生效的人格。
    // 否则在别的页面改了档位再切回来，保存会把旧文案写回去，把档位回滚掉。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.reload()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            SubPageTopBar(title = "大肥鱼人格卡", onBack = onBack) {
                if (uiState.hasOverlay) {
                    TextButton(onClick = viewModel::resetToBase) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "恢复底稿原样",
                            modifier = Modifier
                                .size(18.dp)
                                .offset(y = iconBaselineOffset(MaterialTheme.typography.labelLarge))
                        )
                        Spacer(modifier = Modifier.size(4.dp))
                        Text("恢复原样")
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            CurrentPersonaCard(
                name = uiState.currentBaseName,
                affinity = uiState.affinity,
                isBuiltin = uiState.currentIsBuiltin,
                sourceRepo = uiState.currentSourceRepo,
                sourceLicense = uiState.currentSourceLicense
            )

            // ── 内置人格卡包 ──
            PersonaSectionHeader("内置人格卡包")
            BuiltinCardList(
                cards = uiState.builtinCards,
                currentBaseId = uiState.currentBaseId,
                currentIsBuiltin = uiState.currentIsBuiltin,
                affinity = uiState.affinity,
                onPickBuiltin = { viewModel.useBuiltinPack() },
                onSelectCard = viewModel::selectBuiltinCard
            )

            // ── 我的自定义卡 ──
            PersonaSectionHeader("我的自定义卡")
            CustomCardList(
                cards = uiState.customCards,
                currentBaseId = uiState.currentBaseId,
                onSelect = viewModel::selectCustomCard,
                onDelete = viewModel::deleteCustomCard
            )

            // ── 编辑当前人格卡 ──
            PersonaSectionHeader("编辑当前人格卡")

            PersonaField(
                label = "它的名字",
                value = uiState.fishName,
                onValueChange = viewModel::updateFishName,
                placeholder = "大肥鱼",
                singleLine = true,
                helper = "改的是当前这只大肥鱼的名字，多只鱼各改各的。"
            )

            PersonaField(
                label = "你是谁",
                value = uiState.identity,
                onValueChange = viewModel::updateIdentity,
                placeholder = "我是吃白饭的大肥鱼。住在你的手机里……",
                minLines = 3
            )

            PersonaField(
                label = "性格标签（逗号分隔）",
                value = uiState.traits,
                onValueChange = viewModel::updateTraits,
                placeholder = "摸鱼,傲娇,话痨,贪吃",
                singleLine = true
            )

            PersonaField(
                label = "说话的样子",
                value = uiState.speechStyle,
                onValueChange = viewModel::updateSpeechStyle,
                placeholder = "短句为主，语气随意，被说胖会炸毛。",
                minLines = 4
            )

            PersonaField(
                label = "口头禅（逗号分隔）",
                value = uiState.catchphrases,
                onValueChange = viewModel::updateCatchphrases,
                placeholder = "呜哇,好耶,我去吃饭了",
                singleLine = true
            )

            PersonaField(
                label = "来历",
                value = uiState.backstory,
                onValueChange = viewModel::updateBackstory,
                placeholder = "住在你的手机里，靠吃大白饭长大……",
                minLines = 4
            )

            PersonaField(
                label = "禁忌话题（逗号分隔）",
                value = uiState.taboos,
                onValueChange = viewModel::updateTaboos,
                placeholder = "承认自己是AI,说它瘦",
                singleLine = true
            )

            PersonaField(
                label = "它怎么称呼你",
                value = uiState.userAlias,
                onValueChange = viewModel::updateUserAlias,
                placeholder = "你",
                singleLine = true
            )

            PersonaField(
                label = "它主动开口的理由",
                value = uiState.proactiveMotivation,
                onValueChange = viewModel::updateProactiveMotivation,
                placeholder = "你注意到他今天摸鱼摸得有点久了，想吐槽他一句",
                minLines = 4,
                helper = "这一段决定大肥鱼「为什么想主动跟你说话」。写得有血有肉一点，它说话才不像通知。留空则用内置的几个理由。"
            )

            // 温度
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "温度",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = String.format("%.1f", uiState.temperature),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Slider(
                        value = uiState.temperature,
                        onValueChange = viewModel::updateTemperature,
                        valueRange = 0f..2f,
                        steps = 19
                    )
                }
            }

            uiState.errorMessage?.let { msg ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            uiState.savedMessage?.let { msg ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(
                    onClick = { viewModel.save() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("保存")
                }
            }

            // ── 另存为自定义卡 ──
            PersonaField(
                label = "另存为自定义卡",
                value = uiState.newCardName,
                onValueChange = viewModel::updateNewCardName,
                placeholder = "给这张卡起个名字",
                singleLine = true
            )

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(
                    onClick = { viewModel.saveAsCustomCard() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("存为我的卡")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * 当前生效人格卡的头部展示
 *
 * 一眼看清「现在是谁在说话」——名字、风格、当前好感度、来源署名。
 */
@Composable
private fun CurrentPersonaCard(
    name: String,
    affinity: Int,
    isBuiltin: Boolean,
    sourceRepo: String,
    sourceLicense: String
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isBuiltin) Icons.Filled.Star else Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = name.ifBlank { "大肥鱼" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = themedWeight(FontWeight.SemiBold),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    // 用称号而不是数字 —— 与首页「陪伴档案」口径一致。
                    // 内部数值不该出现在界面上，用户要的是"她现在把我当什么"。
                    text = FishMoodResolver.affinityTitle(affinity),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (sourceRepo.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "正文来自 $sourceRepo（$sourceLicense）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }
        }
    }
}

/**
 * 内置人格卡包
 *
 * 卡按好感度门槛升序排列 —— 越往后门槛越高，解锁得越晚。
 *
 * **已解锁的卡可以点回去用**（2026-11-18 用户要求：
 * 「好感度是解锁新的人格卡，但是也可以切换回之前解锁过的」）。
 * 没解锁的卡点不动 —— 门槛没到就是没到。
 */
@Composable
private fun BuiltinCardList(
    cards: List<BuiltinPersonaCard>,
    currentBaseId: String,
    currentIsBuiltin: Boolean,
    affinity: Int,
    onPickBuiltin: () -> Unit,
    onSelectCard: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 0.dp
    ) {
        Column {
            cards.forEachIndexed { index, card ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.alpha(0.1f),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                val active = currentIsBuiltin && card.id == currentBaseId
                val unlocked = affinity >= card.affinityThreshold
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        // 已解锁的卡可以点回去
                        .clickable(enabled = unlocked && !active) { onSelectCard(card.id) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = builtinIcon(index),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(
                            alpha = if (unlocked) 0.7f else 0.25f
                        ),
                        modifier = Modifier
                            .size(20.dp)
                            .offset(y = iconBaselineOffset(MaterialTheme.typography.bodyLarge))
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = card.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (unlocked) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (active) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "当前生效",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    } else if (!unlocked) {
                        // 没解锁的卡给个锁的提示，一眼看得出还没到
                        Spacer(modifier = Modifier.width(12.dp))
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "还没解锁",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // 正处在自定义卡上时，给一条切回内置包的路
            if (!currentIsBuiltin) {
                HorizontalDivider(
                    modifier = Modifier.alpha(0.1f),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onPickBuiltin)
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier
                            .size(20.dp)
                            .offset(y = iconBaselineOffset(MaterialTheme.typography.bodyLarge))
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = "切回内置卡（跟随好感度）",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * 我的自定义人格卡
 *
 * 可以主动切过去（直到下次跨档被强制切回），也可以删。
 */
@Composable
private fun CustomCardList(
    cards: List<PersonaCardEntity>,
    currentBaseId: String,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    if (cards.isEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 0.dp
        ) {
            Text(
                text = "还没有自定义卡。在下面改完当前人格卡，存一张自己的。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
        return
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 0.dp
    ) {
        Column {
            cards.forEachIndexed { index, card ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.alpha(0.1f),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                val active = card.id == currentBaseId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(card.id) }
                        .heightIn(min = 60.dp)
                        .padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier
                            .size(20.dp)
                            .offset(y = iconBaselineOffset(MaterialTheme.typography.bodyLarge))
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = card.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (card.tagline.isNotBlank()) {
                            Text(
                                text = card.tagline,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (active) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "当前生效",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        IconButton(onClick = { onDelete(card.id) }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 分区标题
 */
@Composable
private fun PersonaSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp)
    )
}

/**
 * 人格卡字段输入框
 */
@Composable
private fun PersonaField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    singleLine: Boolean = false,
    minLines: Int = 1,
    helper: String? = null
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        SoftTextField(
            value = value,
            onValueChange = onValueChange,
            label = null,
            modifier = Modifier.fillMaxWidth(),
            placeholder = placeholder.ifBlank { null },
            minLines = minLines,
            singleLine = singleLine
        )
        if (helper != null) {
            Text(
                text = helper,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                modifier = Modifier.padding(start = 4.dp, top = 6.dp)
            )
        }
    }
}

/**
 * 内置卡的图标（按顺序轮换，不给每张卡单独指定）
 */
private fun builtinIcon(index: Int): ImageVector = when (index % 3) {
    0 -> Icons.Filled.AutoAwesome
    1 -> Icons.Filled.Psychology
    else -> Icons.Filled.Favorite
}
