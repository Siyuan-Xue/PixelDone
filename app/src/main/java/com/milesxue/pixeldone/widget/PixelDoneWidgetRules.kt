package com.milesxue.pixeldone.widget

import com.milesxue.pixeldone.domain.todo.SortMode
import com.milesxue.pixeldone.domain.todo.TodoChecklist
import com.milesxue.pixeldone.domain.todo.TodoChecklistState
import com.milesxue.pixeldone.domain.todo.TodoItem
import com.milesxue.pixeldone.domain.todo.isNormalChecklist
import com.milesxue.pixeldone.domain.todo.visibleTodos

internal fun widgetChecklists(state: TodoChecklistState): List<TodoChecklist> =
    state.lists.filter(::isNormalChecklist)

internal fun widgetTodos(checklist: TodoChecklist): List<TodoItem> =
    visibleTodos(
        items = checklist.items,
        sortMode = SortMode.PRIORITY,
        hideCompleted = true,
    )

internal fun widgetRowLimit(heightDp: Float): Int = when {
    heightDp < 140f -> 1
    heightDp < 200f -> 3
    else -> 5
}
