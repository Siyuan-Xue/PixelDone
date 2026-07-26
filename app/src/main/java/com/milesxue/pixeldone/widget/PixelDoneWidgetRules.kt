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

internal fun widgetTodosWithFeedback(
    checklist: TodoChecklist,
    completingTodoIds: Set<String>,
): List<TodoItem> {
    if (completingTodoIds.isEmpty()) return widgetTodos(checklist)
    return visibleTodos(
        items = checklist.items.map { item ->
            if (item.completed && item.id in completingTodoIds) {
                item.copy(completed = false)
            } else {
                item
            }
        },
        sortMode = SortMode.PRIORITY,
        hideCompleted = true,
    )
}

internal fun widgetRemainingTodoCount(
    checklist: TodoChecklist,
    completingTodoIds: Set<String>,
): Int = widgetTodos(checklist).count { item -> item.id !in completingTodoIds }

internal data class WidgetTodoCompletion(
    val state: TodoChecklistState,
    val changed: Boolean,
)

internal fun completeWidgetTodo(
    state: TodoChecklistState,
    checklistId: String,
    todoId: String,
): WidgetTodoCompletion {
    val target = state.lists
        .firstOrNull { it.id == checklistId }
        ?.items
        ?.firstOrNull { it.id == todoId }
    if (target == null || target.completed) {
        return WidgetTodoCompletion(state = state, changed = false)
    }

    return WidgetTodoCompletion(
        state = state.copy(
            lists = state.lists.map { checklist ->
                if (checklist.id != checklistId) {
                    checklist
                } else {
                    checklist.copy(
                        items = checklist.items.map { item ->
                            if (item.id == todoId) item.copy(completed = true) else item
                        },
                    )
                }
            },
        ),
        changed = true,
    )
}

internal fun widgetItemId(todoId: String): Long {
    var hash = -3750763034362895579L
    todoId.forEach { character ->
        hash = hash xor character.code.toLong()
        hash *= 1099511628211L
    }
    // Glance reserves the lower quarter of the signed Long range for generated collection IDs.
    // Keep the FNV-1a value stable while mapping every app-owned ID into Glance's safe range.
    return hash and Long.MAX_VALUE
}
