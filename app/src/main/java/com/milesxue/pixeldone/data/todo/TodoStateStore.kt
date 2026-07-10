package com.milesxue.pixeldone.data.todo

import com.milesxue.pixeldone.domain.todo.TodoChecklistState

/**
 * Todo 本地存储接口。
 *
 * 教学说明：Repository 依赖接口而不是具体 SharedPreferences 实现，
 * 单元测试就可以换成内存实现，正式 App 则继续使用真实本地存储。
 */
interface TodoStateStore {
    fun loadTodoState(nowMillis: Long = System.currentTimeMillis()): TodoChecklistState
    fun saveTodoState(state: TodoChecklistState)
    fun updateTodoState(transform: (TodoChecklistState) -> TodoChecklistState): TodoChecklistState {
        return transform(loadTodoState()).also(::saveTodoState)
    }
    fun observeTodoState(onChange: () -> Unit): () -> Unit
}
