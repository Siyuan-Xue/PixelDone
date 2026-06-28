package com.milesxue.pixeldone

data class TodoItem(
    val id: String,
    val title: String,
    val priority: TodoPriority,
    val dueAtMillis: Long,
    val completed: Boolean,
    val createdAtMillis: Long,
)

data class TodoChecklist(
    val id: String,
    val name: String,
    val items: List<TodoItem>,
    val createdAtMillis: Long,
)

data class TodoChecklistState(
    val lists: List<TodoChecklist>,
    val selectedListId: String,
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

const val DefaultChecklistId = "main"
const val DefaultChecklistName = "MAIN"

fun createInitialChecklistState(
    items: List<TodoItem>,
    createdAtMillis: Long,
): TodoChecklistState {
    return TodoChecklistState(
        lists = listOf(
            TodoChecklist(
                id = DefaultChecklistId,
                name = DefaultChecklistName,
                items = items,
                createdAtMillis = createdAtMillis,
            ),
        ),
        selectedListId = DefaultChecklistId,
    )
}

fun selectedChecklistOf(state: TodoChecklistState): TodoChecklist {
    return state.lists.firstOrNull { it.id == state.selectedListId } ?: state.lists.first()
}

fun allTodos(state: TodoChecklistState): List<TodoItem> {
    return state.lists.flatMap { it.items }
}

fun activeTodoCount(checklist: TodoChecklist): Int {
    return checklist.items.count { !it.completed }
}

fun completedTodoCount(checklist: TodoChecklist): Int {
    return checklist.items.count { it.completed }
}

fun isChecklistNameAvailable(
    state: TodoChecklistState,
    nameInput: String,
    editingId: String? = null,
): Boolean {
    val name = nameInput.trim()
    if (name.isEmpty()) return false

    return state.lists.none { checklist ->
        checklist.id != editingId && checklist.name.equals(name, ignoreCase = true)
    }
}

fun createTodoChecklist(
    state: TodoChecklistState,
    id: String,
    nameInput: String,
    createdAtMillis: Long,
): TodoChecklistState? {
    val name = nameInput.trim()
    if (!isChecklistNameAvailable(state, name)) return null

    val checklist = TodoChecklist(
        id = id,
        name = name,
        items = emptyList(),
        createdAtMillis = createdAtMillis,
    )
    return state.copy(
        lists = state.lists + checklist,
        selectedListId = checklist.id,
    )
}

fun renameTodoChecklist(
    state: TodoChecklistState,
    id: String,
    nameInput: String,
): TodoChecklistState? {
    val name = nameInput.trim()
    if (!isChecklistNameAvailable(state, name, editingId = id)) return null

    var found = false
    val updatedLists = state.lists.map { checklist ->
        if (checklist.id == id) {
            found = true
            checklist.copy(name = name)
        } else {
            checklist
        }
    }

    return if (found) state.copy(lists = updatedLists) else null
}

fun selectTodoChecklist(state: TodoChecklistState, id: String): TodoChecklistState? {
    if (state.lists.none { it.id == id }) return null
    return state.copy(selectedListId = id)
}

fun deleteTodoChecklist(state: TodoChecklistState, id: String): TodoChecklistState? {
    if (state.lists.size <= 1) return null

    val updatedLists = state.lists.filterNot { it.id == id }
    if (updatedLists.size == state.lists.size) return null

    val selectedListId = if (state.selectedListId == id) {
        updatedLists.first().id
    } else {
        state.selectedListId
    }
    return state.copy(
        lists = updatedLists,
        selectedListId = selectedListId,
    )
}

fun updateChecklistItems(
    state: TodoChecklistState,
    checklistId: String,
    items: List<TodoItem>,
): TodoChecklistState? {
    var found = false
    val updatedLists = state.lists.map { checklist ->
        if (checklist.id == checklistId) {
            found = true
            checklist.copy(items = items)
        } else {
            checklist
        }
    }

    return if (found) state.copy(lists = updatedLists) else null
}

fun normalizeChecklistState(
    state: TodoChecklistState,
    fallbackCreatedAtMillis: Long,
): TodoChecklistState {
    val validLists = state.lists
        .mapNotNull { checklist ->
            val name = checklist.name.trim()
            if (checklist.id.isBlank() || name.isEmpty()) {
                null
            } else {
                checklist.copy(name = name)
            }
        }

    if (validLists.isEmpty()) {
        return createInitialChecklistState(emptyList(), fallbackCreatedAtMillis)
    }

    val selectedId = if (validLists.any { it.id == state.selectedListId }) {
        state.selectedListId
    } else {
        validLists.first().id
    }
    return state.copy(
        lists = validLists,
        selectedListId = selectedId,
    )
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
