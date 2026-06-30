package com.milesxue.pixeldone.data.todo

import com.milesxue.pixeldone.domain.todo.TodoChecklistState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Todo 数据层入口。
 *
 * 教学说明：Repository 是 UI/领域逻辑与本地存储之间的边界。
 * 这一轮不迁移 DataStore，仍然使用 SharedPreferences JSON，避免改变用户已有数据格式。
 */
class TodoRepository(private val store: TodoStateStore) {
    private val _state = MutableStateFlow(store.loadTodoState())
    val state: StateFlow<TodoChecklistState> = _state.asStateFlow()

    fun loadTodoState(): TodoChecklistState {
        return store.loadTodoState().also { _state.value = it }
    }

    fun saveTodoState(state: TodoChecklistState) {
        store.saveTodoState(state)
        _state.value = state
    }

    fun observeTodoState(onChange: () -> Unit): () -> Unit {
        return store.observeTodoState {
            _state.value = store.loadTodoState()
            onChange()
        }
    }

    fun updateTodoState(transform: (TodoChecklistState) -> TodoChecklistState): TodoChecklistState {
        val updated = transform(loadTodoState())
        saveTodoState(updated)
        return updated
    }
}
