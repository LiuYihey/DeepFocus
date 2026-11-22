package com.deepfocus.app.ui.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepfocus.app.data.local.entity.TodoEntity
import com.deepfocus.app.data.repository.TodoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 今日待办 ViewModel
 */
@HiltViewModel
class TodoViewModel @Inject constructor(
    private val todoRepository: TodoRepository
) : ViewModel() {

    data class UiState(
        val input: String = "",
        val todos: List<TodoEntity> = emptyList()
    ) {
        /** 已完成条数 */
        val doneCount: Int get() = todos.count { it.done }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            todoRepository.observeToday().collect { list ->
                _state.update { it.copy(todos = list) }
            }
        }
    }

    /** 输入框内容变化 */
    fun setInput(value: String) {
        _state.update { it.copy(input = value) }
    }

    /** 加一条待办 */
    fun add() {
        val text = _state.value.input.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            todoRepository.add(text)
            _state.update { it.copy(input = "") }
        }
    }

    /** 勾选 / 取消 */
    fun toggle(todo: TodoEntity) {
        viewModelScope.launch {
            todoRepository.setDone(todo.id, !todo.done)
        }
    }

    /** 删除 */
    fun delete(todo: TodoEntity) {
        viewModelScope.launch {
            todoRepository.delete(todo.id)
        }
    }
}
