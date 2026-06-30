package com.milesxue.pixeldone.domain.todo

/**
 * 单个 Todo 的创建、编辑、删除和图片字段规则。
 * UI 可以安全调用这些函数，因为它们没有副作用，适合用单元测试锁住行为。
 */

fun createTodoItem(
    id: String,
    titleInput: String,
    priority: TodoPriority,
    dueAtMillis: Long,
    createdAtMillis: Long,
    reminderRepeat: ReminderRepeat = ReminderRepeat.NONE,
): TodoItem? {
    val title = titleInput.trim()
    if (title.isEmpty()) return null

    return TodoItem(
        id = id,
        title = title,
        priority = priority,
        dueAtMillis = dueAtMillis,
        completed = false,
        createdAtMillis = createdAtMillis,
        reminderRepeat = reminderRepeat,
    )
}

fun visibleTodos(
    items: List<TodoItem>,
    sortMode: SortMode,
    hideCompleted: Boolean,
): List<TodoItem> {
    val filteredItems = if (hideCompleted) {
        items.filterNot { it.completed }
    } else {
        items
    }

    return filteredItems.sortedWith(todoComparator(sortMode))
}

fun toggleTodoCompletion(items: List<TodoItem>, id: String): List<TodoItem> {
    return items.map { item ->
        if (item.id == id) {
            item.copy(completed = !item.completed)
        } else {
            item
        }
    }
}

fun updateTodoItem(
    items: List<TodoItem>,
    id: String,
    titleInput: String,
    priority: TodoPriority,
    dueAtMillis: Long,
    reminderRepeat: ReminderRepeat = ReminderRepeat.NONE,
): List<TodoItem>? {
    val title = titleInput.trim()
    if (title.isEmpty()) return null

    var found = false
    val updatedItems = items.map { item ->
        if (item.id == id) {
            found = true
            item.copy(
                title = title,
                priority = priority,
                dueAtMillis = dueAtMillis,
                reminderRepeat = reminderRepeat,
            )
        } else {
            item
        }
    }

    return if (found) updatedItems else null
}

fun updateTodoImageFileName(
    items: List<TodoItem>,
    id: String,
    imageFileName: String?,
): List<TodoItem>? {
    val normalizedFileName = imageFileName?.takeIf { it.isNotBlank() }
    var found = false
    val updatedItems = items.map { item ->
        if (item.id == id) {
            found = true
            item.copy(imageFileName = normalizedFileName)
        } else {
            item
        }
    }

    return if (found) updatedItems else null
}

fun deleteTodoItem(items: List<TodoItem>, id: String): List<TodoItem> {
    return items.filterNot { it.id == id }
}

fun deleteCompletedTodos(items: List<TodoItem>): List<TodoItem> {
    return items.filterNot { it.completed }
}
