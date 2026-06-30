package com.milesxue.pixeldone.domain.todo

/**
 * 排序规则。
 * 优先级排序和时间排序集中放在这里，避免列表 UI 自己隐藏业务判断。
 */

internal fun todoComparator(sortMode: SortMode): Comparator<TodoItem> {
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

internal fun priorityRank(priority: TodoPriority): Int {
    return when (priority) {
        TodoPriority.XHIGH -> 0
        TodoPriority.HIGH -> 1
        TodoPriority.MEDIUM -> 2
        TodoPriority.LOW -> 3
    }
}
