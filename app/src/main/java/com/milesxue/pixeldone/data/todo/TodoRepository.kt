package com.milesxue.pixeldone.data.todo

import com.milesxue.pixeldone.domain.todo.TodoChecklistState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Todo data-layer entry point.
 *
 * The repository keeps UI/domain code behind [TodoStateStore]. Production uses Room through
 * that interface; tests can keep using the in-memory implementation.
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
