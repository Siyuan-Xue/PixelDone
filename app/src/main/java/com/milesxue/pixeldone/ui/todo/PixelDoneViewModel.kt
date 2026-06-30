package com.milesxue.pixeldone.ui.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.milesxue.pixeldone.data.todo.TodoRepository
import com.milesxue.pixeldone.domain.todo.ReminderCapability
import com.milesxue.pixeldone.domain.todo.TodoChecklistState
import com.milesxue.pixeldone.domain.todo.normalTodos
import com.milesxue.pixeldone.reminder.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * PixelDone 的屏幕级状态持有者。
 *
 * 教学说明：ViewModel 位于 UI 层，但不应该包含 Compose 代码。
 * 它负责把用户意图转换为 repository 更新和提醒调度，再把新的不可变 UI state 暴露给界面。
 */
class PixelDoneViewModel(
    private val todoRepository: TodoRepository,
    private val reminderScheduler: ReminderScheduler,
) : ViewModel() {
    private val unregisterTodoObserver: () -> Unit
    private val _uiState = MutableStateFlow(
        PixelDoneUiState(checklistState = todoRepository.loadTodoState()),
    )
    val uiState: StateFlow<PixelDoneUiState> = _uiState.asStateFlow()

    init {
        unregisterTodoObserver = todoRepository.observeTodoState {
            _uiState.value = _uiState.value.copy(checklistState = todoRepository.state.value)
        }
    }

    fun onAction(action: PixelDoneAction) {
        when (action) {
            is PixelDoneAction.ReplaceChecklistState -> replaceChecklistState(action.state)
            is PixelDoneAction.SetSortMode -> _uiState.value = _uiState.value.copy(sortMode = action.sortMode)
            is PixelDoneAction.SetHideCompleted -> {
                _uiState.value = _uiState.value.copy(hideCompleted = action.hideCompleted)
            }
            is PixelDoneAction.SetDeadlineCountdownVisible -> {
                _uiState.value = _uiState.value.copy(showDeadlineCountdown = action.visible)
            }
            PixelDoneAction.SystemActionConsumed -> {
                _uiState.value = _uiState.value.copy(pendingSystemAction = null)
            }
        }
    }

    fun replaceChecklistState(updatedState: TodoChecklistState): Set<ReminderCapability> {
        val previousTodos = normalTodos(_uiState.value.checklistState)
        val updatedTodos = normalTodos(updatedState)
        todoRepository.saveTodoState(updatedState)
        val missingCapabilities = reminderScheduler.sync(previousTodos, updatedTodos)
        _uiState.value = _uiState.value.copy(checklistState = updatedState)
        return missingCapabilities
    }

    override fun onCleared() {
        unregisterTodoObserver()
        super.onCleared()
    }

    companion object {
        fun factory(
            todoRepository: TodoRepository,
            reminderScheduler: ReminderScheduler,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(PixelDoneViewModel::class.java)) {
                    return PixelDoneViewModel(todoRepository, reminderScheduler) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    }
}
