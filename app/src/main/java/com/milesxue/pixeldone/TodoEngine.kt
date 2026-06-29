package com.milesxue.pixeldone

data class TodoItem(
    val id: String,
    val title: String,
    val priority: TodoPriority,
    val dueAtMillis: Long,
    val completed: Boolean,
    val createdAtMillis: Long,
    val reminderRepeat: ReminderRepeat = ReminderRepeat.NONE,
    val imageFileName: String? = null,
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
    XHIGH,
    HIGH,
    MEDIUM,
    LOW,
}

enum class ReminderRepeat {
    NONE,
    DAILY,
    WEEKLY,
}

enum class SortMode {
    PRIORITY,
    TIME,
}

internal const val DailyReminderIntervalMillis = 24L * 60L * 60L * 1000L
internal const val WeeklyReminderIntervalMillis = 7L * DailyReminderIntervalMillis

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

fun shouldScheduleTodoAlarm(item: TodoItem, nowMillis: Long): Boolean {
    return nextReminderAtMillis(item, nowMillis) != null
}

fun nextReminderAtMillis(item: TodoItem, nowMillis: Long): Long? {
    if (item.completed) return null
    return nextReminderAtMillis(
        dueAtMillis = item.dueAtMillis,
        reminderRepeat = item.reminderRepeat,
        nowMillis = nowMillis,
    )
}

fun nextReminderAtMillis(
    dueAtMillis: Long,
    reminderRepeat: ReminderRepeat,
    nowMillis: Long,
): Long? {
    if (dueAtMillis <= 0L) return null

    return when (reminderRepeat) {
        ReminderRepeat.NONE -> dueAtMillis.takeIf { it > nowMillis }
        ReminderRepeat.DAILY -> nextRepeatingReminderAtMillis(
            dueAtMillis = dueAtMillis,
            intervalMillis = DailyReminderIntervalMillis,
            nowMillis = nowMillis,
        )
        ReminderRepeat.WEEKLY -> nextRepeatingReminderAtMillis(
            dueAtMillis = dueAtMillis,
            intervalMillis = WeeklyReminderIntervalMillis,
            nowMillis = nowMillis,
        )
    }
}

fun advanceRepeatingTodoAfterReminder(
    item: TodoItem,
    nowMillis: Long,
): TodoItem? {
    if (item.completed || item.reminderRepeat == ReminderRepeat.NONE) return null

    val nextDueAtMillis = nextReminderAtMillis(
        dueAtMillis = item.dueAtMillis,
        reminderRepeat = item.reminderRepeat,
        nowMillis = nowMillis,
    ) ?: return null

    return if (nextDueAtMillis > item.dueAtMillis) {
        item.copy(dueAtMillis = nextDueAtMillis)
    } else {
        null
    }
}

fun advanceRepeatingTodoAfterReminder(
    state: TodoChecklistState,
    todoId: String,
    nowMillis: Long,
): TodoChecklistState? {
    var changed = false
    val updatedLists = state.lists.map { checklist ->
        val updatedItems = checklist.items.map { item ->
            if (item.id == todoId) {
                advanceRepeatingTodoAfterReminder(item, nowMillis)?.also {
                    changed = true
                } ?: item
            } else {
                item
            }
        }
        if (updatedItems == checklist.items) checklist else checklist.copy(items = updatedItems)
    }

    return if (changed) state.copy(lists = updatedLists) else null
}

private fun nextRepeatingReminderAtMillis(
    dueAtMillis: Long,
    intervalMillis: Long,
    nowMillis: Long,
): Long? {
    if (dueAtMillis > nowMillis) return dueAtMillis

    val elapsedMillis = nowMillis - dueAtMillis
    val elapsedIntervals = elapsedMillis / intervalMillis
    val nextAtMillis = dueAtMillis + ((elapsedIntervals + 1L) * intervalMillis)
    return nextAtMillis.takeIf { it > nowMillis }
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
        TodoPriority.XHIGH -> 0
        TodoPriority.HIGH -> 1
        TodoPriority.MEDIUM -> 2
        TodoPriority.LOW -> 3
    }
}
