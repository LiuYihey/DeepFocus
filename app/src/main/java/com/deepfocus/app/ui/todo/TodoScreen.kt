package com.deepfocus.app.ui.todo

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepfocus.app.data.local.entity.TodoEntity
import com.deepfocus.app.ui.components.AppCard
import com.deepfocus.app.ui.components.InlineSectionLabel
import com.deepfocus.app.ui.components.SoftTextField
import com.deepfocus.app.ui.theme.CuteTextFont

/**
 * 今日待办
 *
 * 用户要求（2026-11-18）：
 * > 「增加一个 todo 菜单栏选项，这里可以添加今日 todo list，
 * > 大肥鱼会知道用户今天的计划。并且唤醒时督促用户完成，
 * > 大肥鱼的话可以通过通知栏让用户看到。」
 *
 * 设计要点：
 * - 待办是**今日**的，不是无限 backlog（跨天就不算了）
 * - 大肥鱼会通过 `todo_status` 工具知道这些事，唤醒时主动催
 * - 空状态要说人话，不是「暂无数据」
 */
@Composable
fun TodoScreen(
    modifier: Modifier = Modifier,
    viewModel: TodoViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val hapticFeedback = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                InlineSectionLabel("今日待办")
            }

            item {
                // 输入框 + 添加按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SoftTextField(
                        value = state.input,
                        onValueChange = viewModel::setInput,
                        label = "今天要做的一件事",
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    // 添加按钮：圆形，跟输入框一样高
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                if (state.input.isNotBlank()) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                            .clickable(enabled = state.input.isNotBlank()) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.add()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "添加",
                            tint = if (state.input.isNotBlank()) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            if (state.todos.isEmpty()) {
                item {
                    EmptyTodoState()
                }
            } else {
                items(state.todos, key = { it.id }) { todo ->
                    TodoRow(
                        todo = todo,
                        onToggle = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.toggle(todo)
                        },
                        onDelete = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.delete(todo)
                        }
                    )
                }
            }

            if (state.doneCount > 0) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "完成 ${state.doneCount} / ${state.todos.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 单条待办
 */
@Composable
private fun TodoRow(
    todo: TodoEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 勾选圈
            val circleColor by animateColorAsState(
                targetValue = if (todo.done) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                label = "todo_circle"
            )
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(circleColor)
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center
            ) {
                if (todo.done) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "取消完成",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = todo.content,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = CuteTextFont,
                color = if (todo.done) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                textDecoration = if (todo.done) TextDecoration.LineThrough else null,
                modifier = Modifier.weight(1f)
            )

            // 删除：平时淡出，点到才明显
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(32.dp)
                    .alpha(0.45f)
                    .clip(CircleShape)
                    .clickable(onClick = onDelete)
                    .padding(7.dp)
            )
        }
    }
}

/**
 * 空状态
 *
 * 规范：空状态要有邀请感 —— 图标 + 一句说明 + 一个能立刻做的动作。
 */
@Composable
private fun EmptyTodoState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.EditNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "今天还没安排什么事",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "写下来，大肥鱼就记住了 —— 到点会来催你",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
