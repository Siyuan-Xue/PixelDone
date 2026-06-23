package com.codexue.pixeldone

data class TodoItem(
    val id: String,
    val title: String,
    val priority: TodoPriority,
    val dueAtMillis: Long,
    val completed: Boolean,
    val createdAtMillis: Long,
)

enum class TodoPriority {
    HIGH,
    MEDIUM,
    LOW,
}

enum class SortMode {
    PRIORITY,
    TIME,
}

fun createTodoItem(
    id: String,
    titleInput: String,
    priority: TodoPriority,
    dueAtMillis: Long,
    createdAtMillis: Long,
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
            )
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

fun shouldScheduleTodoAlarm(item: TodoItem, nowMillis: Long): Boolean {
    return !item.completed && item.dueAtMillis > nowMillis
}

private fun todoComparator(sortMode: SortMode): Comparator<TodoItem> {
    return when (sortMode) {
        SortMode.PRIORITY -> compareBy<TodoItem> { it.completed }
            .thenBy { priorityRank(it.priority) }
            .thenBy { it.dueAtMillis }
            .thenBy { it.createdAtMillis }
        SortMode.TIME -> compareBy<TodoItem> { it.completed }
            .thenBy { it.dueAtMillis }
            .thenBy { priorityRank(it.priority) }
            .thenBy { it.createdAtMillis }
    }
}

private fun priorityRank(priority: TodoPriority): Int {
    return when (priority) {
        TodoPriority.HIGH -> 0
        TodoPriority.MEDIUM -> 1
        TodoPriority.LOW -> 2
    }
}
