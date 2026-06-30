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
    val trashedFromChecklistId: String? = null,
    val trashedFromChecklistName: String? = null,
    val trashedAtMillis: Long? = null,
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

enum class ReminderScheduleMode {
    INEXACT_NOTIFICATION,
    SYSTEM_ALARM,
}

enum class ReminderAlertMode {
    SHORT_NOTIFICATION,
    FULLSCREEN_ALARM,
}

data class ReminderDispatchPlan(
    val fullscreenAlarmItems: List<TodoItem> = emptyList(),
    val shortNotificationItems: List<TodoItem> = emptyList(),
    val rescheduleItems: List<TodoItem> = emptyList(),
) {
    val isEmpty: Boolean
        get() = fullscreenAlarmItems.isEmpty() && shortNotificationItems.isEmpty()

    val signatureIds: List<String>
        get() = (fullscreenAlarmItems + shortNotificationItems)
            .map { it.id }
            .distinct()
            .sorted()
}

enum class ReminderCapability {
    NOTIFICATION_PERMISSION,
    EXACT_ALARM_ACCESS,
    FULL_SCREEN_INTENT_ACCESS,
}

object ReminderNotificationIds {
    const val XHIGH_ALARM = 0x30000001
    private const val SHORT_ITEM_NAMESPACE = 0x10000000
    private const val SHORT_BATCH_NAMESPACE = 0x20000000
    private const val SHORT_FOLLOW_UP_NAMESPACE = 0x28000000
    private const val PAYLOAD_MASK = 0x0FFFFFFF
    private const val SHORT_BATCH_PAYLOAD_MASK = 0x07FFFFFF

    fun shortItem(todoId: String): Int {
        return SHORT_ITEM_NAMESPACE or (todoId.hashCode() and PAYLOAD_MASK)
    }

    fun shortBatch(firedDueAtMillis: Long): Int {
        val foldedTime = (firedDueAtMillis xor (firedDueAtMillis ushr 32)).toInt()
        return SHORT_BATCH_NAMESPACE or (foldedTime and SHORT_BATCH_PAYLOAD_MASK)
    }

    fun shortFollowUp(firedDueAtMillis: Long): Int {
        val foldedTime = (firedDueAtMillis xor (firedDueAtMillis ushr 32)).toInt()
        return SHORT_FOLLOW_UP_NAMESPACE or (foldedTime and SHORT_BATCH_PAYLOAD_MASK)
    }
}

internal const val DailyReminderIntervalMillis = 24L * 60L * 60L * 1000L
internal const val WeeklyReminderIntervalMillis = 7L * DailyReminderIntervalMillis
internal const val DefaultSnoozeIntervalMillis = 10L * 60L * 1000L

const val DefaultChecklistId = "main"
const val DefaultChecklistName = "MAIN"
const val TrashChecklistId = "trash"
const val TrashChecklistName = "TRASH"

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
            TodoChecklist(
                id = TrashChecklistId,
                name = TrashChecklistName,
                items = emptyList(),
                createdAtMillis = createdAtMillis,
            ),
        ),
        selectedListId = DefaultChecklistId,
    )
}

fun selectedChecklistOf(state: TodoChecklistState): TodoChecklist {
    return state.lists.firstOrNull { it.id == state.selectedListId }
        ?: state.lists.firstOrNull { !isTrashChecklist(it) }
        ?: state.lists.first()
}

fun allTodos(state: TodoChecklistState): List<TodoItem> {
    return state.lists.flatMap { it.items }
}

fun normalTodos(state: TodoChecklistState): List<TodoItem> {
    return state.lists.filterNot(::isTrashChecklist).flatMap { it.items }
}

fun trashTodos(state: TodoChecklistState): List<TodoItem> {
    return state.lists.firstOrNull(::isTrashChecklist)?.items.orEmpty()
}

fun normalChecklistCount(state: TodoChecklistState): Int {
    return state.lists.count { !isTrashChecklist(it) }
}

fun isTrashChecklist(checklist: TodoChecklist): Boolean {
    return checklist.id == TrashChecklistId
}

fun isTrashChecklistId(id: String): Boolean {
    return id == TrashChecklistId
}

fun isTrashedTodo(item: TodoItem): Boolean {
    return item.trashedAtMillis != null || item.trashedFromChecklistId != null
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
    if (name.equals(TrashChecklistName, ignoreCase = true)) return false

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
    if (isTrashChecklistId(id)) return null

    val name = nameInput.trim()
    if (!isChecklistNameAvailable(state, name)) return null

    val checklist = TodoChecklist(
        id = id,
        name = name,
        items = emptyList(),
        createdAtMillis = createdAtMillis,
    )
    return state.copy(
        lists = state.lists.filterNot(::isTrashChecklist) +
            checklist +
            state.lists.filter(::isTrashChecklist),
        selectedListId = checklist.id,
    )
}

fun renameTodoChecklist(
    state: TodoChecklistState,
    id: String,
    nameInput: String,
): TodoChecklistState? {
    if (isTrashChecklistId(id)) return null

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

fun deleteTodoChecklist(
    state: TodoChecklistState,
    id: String,
    trashedAtMillis: Long,
): TodoChecklistState? {
    if (isTrashChecklistId(id)) return null
    if (normalChecklistCount(state) <= 1) return null

    val checklistToDelete = state.lists.firstOrNull { it.id == id } ?: return null
    val trash = state.lists.firstOrNull(::isTrashChecklist) ?: return null
    val movedItems = checklistToDelete.items.map { item ->
        item.toTrashItem(
            sourceChecklist = checklistToDelete,
            trashedAtMillis = trashedAtMillis,
        )
    }
    val updatedLists = state.lists
        .filterNot { it.id == id }
        .map { checklist ->
            if (isTrashChecklist(checklist)) {
                trash.copy(items = trash.items + movedItems)
            } else {
                checklist
            }
        }

    val selectedListId = if (state.selectedListId == id) {
        updatedLists.first { !isTrashChecklist(it) }.id
    } else {
        state.selectedListId
    }
    return state.copy(
        lists = updatedLists,
        selectedListId = selectedListId,
    )
}

fun moveTodoItemToTrash(
    state: TodoChecklistState,
    checklistId: String,
    todoId: String,
    trashedAtMillis: Long,
): TodoChecklistState? {
    if (isTrashChecklistId(checklistId)) return null
    val sourceChecklist = state.lists.firstOrNull { it.id == checklistId } ?: return null
    val itemToMove = sourceChecklist.items.firstOrNull { it.id == todoId } ?: return null

    return moveTodosToTrash(
        state = state,
        sourceChecklist = sourceChecklist,
        todoIds = setOf(itemToMove.id),
        trashedAtMillis = trashedAtMillis,
    )
}

fun moveCompletedTodosToTrash(
    state: TodoChecklistState,
    checklistId: String,
    trashedAtMillis: Long,
): TodoChecklistState? {
    if (isTrashChecklistId(checklistId)) return null
    val sourceChecklist = state.lists.firstOrNull { it.id == checklistId } ?: return null
    val completedIds = sourceChecklist.items
        .filter { it.completed }
        .mapTo(mutableSetOf()) { it.id }
    if (completedIds.isEmpty()) return null

    return moveTodosToTrash(
        state = state,
        sourceChecklist = sourceChecklist,
        todoIds = completedIds,
        trashedAtMillis = trashedAtMillis,
    )
}

fun restoreTodoFromTrash(
    state: TodoChecklistState,
    todoId: String,
    restoredAtMillis: Long,
): TodoChecklistState? {
    val trash = state.lists.firstOrNull(::isTrashChecklist) ?: return null
    val trashedItem = trash.items.firstOrNull { it.id == todoId } ?: return null
    val originalChecklistId = trashedItem.trashedFromChecklistId
        ?.takeIf { it.isNotBlank() && !isTrashChecklistId(it) }
    val originalChecklist = originalChecklistId?.let { id ->
        state.lists.firstOrNull { it.id == id && !isTrashChecklist(it) }
    }
    val fallbackChecklist = state.lists.firstOrNull { !isTrashChecklist(it) }
    val targetChecklistId = originalChecklist?.id
        ?: originalChecklistId
        ?: fallbackChecklist?.id
        ?: DefaultChecklistId

    val listsWithTarget = when {
        originalChecklist != null -> state.lists
        originalChecklistId != null -> {
            val restoredChecklist = TodoChecklist(
                id = originalChecklistId,
                name = restoredChecklistName(trashedItem),
                items = emptyList(),
                createdAtMillis = restoredAtMillis,
            )
            state.lists.filterNot(::isTrashChecklist) + restoredChecklist + trash
        }
        fallbackChecklist != null -> state.lists
        else -> {
            val fallbackMain = TodoChecklist(
                id = DefaultChecklistId,
                name = DefaultChecklistName,
                items = emptyList(),
                createdAtMillis = restoredAtMillis,
            )
            state.lists.filterNot(::isTrashChecklist) + fallbackMain + trash
        }
    }

    val selectedListId = if (state.lists.any { it.id == state.selectedListId }) {
        state.selectedListId
    } else {
        targetChecklistId
    }

    val restoredItem = trashedItem.copy(
        trashedFromChecklistId = null,
        trashedFromChecklistName = null,
        trashedAtMillis = null,
    )
    val updatedLists = listsWithTarget.map { checklist ->
        when {
            isTrashChecklist(checklist) -> {
                checklist.copy(items = checklist.items.filterNot { it.id == todoId })
            }
            checklist.id == targetChecklistId -> {
                checklist.copy(items = checklist.items + restoredItem)
            }
            else -> checklist
        }
    }

    return state.copy(
        lists = updatedLists,
        selectedListId = selectedListId,
    )
}

fun deleteAllTrashTodos(state: TodoChecklistState): TodoChecklistState {
    return state.copy(
        lists = state.lists.map { checklist ->
            if (isTrashChecklist(checklist)) {
                checklist.copy(items = emptyList())
            } else {
                checklist
            }
        },
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
    val trashItems = mutableListOf<TodoItem>()
    var trashCreatedAtMillis: Long? = null
    val validNormalLists = state.lists.mapNotNull { checklist ->
        val name = checklist.name.trim()
        if (checklist.id.isBlank() || name.isEmpty()) {
            return@mapNotNull null
        }

        if (isTrashChecklist(checklist)) {
            trashCreatedAtMillis = trashCreatedAtMillis ?: checklist.createdAtMillis
            trashItems += checklist.items.map { item ->
                if (isTrashedTodo(item)) {
                    item
                } else {
                    item.copy(
                        trashedFromChecklistId = DefaultChecklistId,
                        trashedFromChecklistName = DefaultChecklistName,
                        trashedAtMillis = fallbackCreatedAtMillis,
                    )
                }
            }
            return@mapNotNull null
        }

        val normalName = if (name.equals(TrashChecklistName, ignoreCase = true)) {
            "$TrashChecklistName LIST"
        } else {
            name
        }
        val normalItems = checklist.items.filterNot(::isTrashedTodo)
        trashItems += checklist.items.filter(::isTrashedTodo)
        checklist.copy(
            name = normalName,
            items = normalItems,
        )
    }
    val normalLists = validNormalLists.ifEmpty {
        listOf(
            TodoChecklist(
                id = DefaultChecklistId,
                name = DefaultChecklistName,
                items = emptyList(),
                createdAtMillis = fallbackCreatedAtMillis,
            ),
        )
    }
    val trashChecklist = TodoChecklist(
        id = TrashChecklistId,
        name = TrashChecklistName,
        items = trashItems,
        createdAtMillis = trashCreatedAtMillis ?: fallbackCreatedAtMillis,
    )
    val validLists = normalLists + trashChecklist

    val selectedId = if (validLists.any { it.id == state.selectedListId }) {
        state.selectedListId
    } else {
        normalLists.first().id
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
    if (isTrashedTodo(item)) return false
    return nextReminderAtMillis(item, nowMillis) != null
}

fun reminderScheduleMode(item: TodoItem, nowMillis: Long): ReminderScheduleMode? {
    if (!shouldScheduleTodoAlarm(item, nowMillis)) return null
    return when (item.priority) {
        TodoPriority.XHIGH -> ReminderScheduleMode.SYSTEM_ALARM
        TodoPriority.HIGH,
        TodoPriority.MEDIUM,
        TodoPriority.LOW,
        -> ReminderScheduleMode.INEXACT_NOTIFICATION
    }
}

fun reminderAlertMode(item: TodoItem, nowMillis: Long): ReminderAlertMode? {
    if (!shouldScheduleTodoAlarm(item, nowMillis)) return null
    return when (item.priority) {
        TodoPriority.XHIGH -> ReminderAlertMode.FULLSCREEN_ALARM
        TodoPriority.HIGH,
        TodoPriority.MEDIUM,
        TodoPriority.LOW,
        -> ReminderAlertMode.SHORT_NOTIFICATION
    }
}

fun requiredReminderCapabilities(item: TodoItem, nowMillis: Long): Set<ReminderCapability> {
    return when (reminderAlertMode(item, nowMillis)) {
        ReminderAlertMode.FULLSCREEN_ALARM -> setOf(
            ReminderCapability.NOTIFICATION_PERMISSION,
            ReminderCapability.EXACT_ALARM_ACCESS,
            ReminderCapability.FULL_SCREEN_INTENT_ACCESS,
        )
        ReminderAlertMode.SHORT_NOTIFICATION -> setOf(
            ReminderCapability.NOTIFICATION_PERMISSION,
        )
        null -> emptySet()
    }
}

fun effectiveReminderScheduleMode(
    item: TodoItem,
    nowMillis: Long,
    canScheduleExactAlarms: Boolean,
): ReminderScheduleMode? {
    val idealMode = reminderScheduleMode(item, nowMillis) ?: return null
    return if (idealMode == ReminderScheduleMode.SYSTEM_ALARM && !canScheduleExactAlarms) {
        ReminderScheduleMode.INEXACT_NOTIFICATION
    } else {
        idealMode
    }
}

fun effectiveReminderAlertMode(
    item: TodoItem,
    nowMillis: Long,
    canScheduleExactAlarms: Boolean,
): ReminderAlertMode? {
    val idealMode = reminderAlertMode(item, nowMillis) ?: return null
    return if (idealMode == ReminderAlertMode.FULLSCREEN_ALARM && !canScheduleExactAlarms) {
        ReminderAlertMode.SHORT_NOTIFICATION
    } else {
        idealMode
    }
}

fun canScheduleExactAlarmForSdk(sdkInt: Int, canScheduleExactAlarms: Boolean): Boolean {
    return sdkInt < 31 || canScheduleExactAlarms
}

fun shouldDispatchTodoReminder(item: TodoItem, firedDueAtMillis: Long): Boolean {
    if (isTrashedTodo(item) || item.completed || firedDueAtMillis <= 0L) return false
    if (item.dueAtMillis <= 0L || firedDueAtMillis < item.dueAtMillis) return false

    return when (item.reminderRepeat) {
        ReminderRepeat.NONE -> item.dueAtMillis == firedDueAtMillis
        ReminderRepeat.DAILY -> isAlignedRepeatingReminder(
            dueAtMillis = item.dueAtMillis,
            firedDueAtMillis = firedDueAtMillis,
            intervalMillis = DailyReminderIntervalMillis,
        )
        ReminderRepeat.WEEKLY -> isAlignedRepeatingReminder(
            dueAtMillis = item.dueAtMillis,
            firedDueAtMillis = firedDueAtMillis,
            intervalMillis = WeeklyReminderIntervalMillis,
        )
    }
}

fun shouldCancelUnschedulableTodoAlarm(
    previousItem: TodoItem?,
    item: TodoItem,
    nowMillis: Long,
): Boolean {
    if (shouldScheduleTodoAlarm(item, nowMillis)) return false
    val unchangedActivePastDueOneShot = previousItem == item &&
        !item.completed &&
        !isTrashedTodo(item) &&
        item.reminderRepeat == ReminderRepeat.NONE &&
        item.dueAtMillis in 1L..nowMillis
    return !unchangedActivePastDueOneShot
}

fun todosDueForReminder(
    items: List<TodoItem>,
    firedDueAtMillis: Long,
): List<TodoItem> {
    return items
        .filter { item -> shouldDispatchTodoReminder(item, firedDueAtMillis) }
        .sortedWith(
            compareBy<TodoItem> { priorityRank(it.priority) }
                .thenBy { it.createdAtMillis }
                .thenBy { it.id },
        )
}

fun reminderDispatchPlan(
    items: List<TodoItem>,
    firedDueAtMillis: Long,
    triggerTodoId: String?,
    triggerAlertMode: ReminderAlertMode,
    canScheduleExactAlarms: Boolean,
): ReminderDispatchPlan {
    val dueItems = todosDueForReminder(items, firedDueAtMillis)
    if (dueItems.isEmpty()) return ReminderDispatchPlan()

    val triggerItem = triggerTodoId?.let { id -> dueItems.firstOrNull { it.id == id } }
    val xhighItems = dueItems.filter { it.priority == TodoPriority.XHIGH }
    val shortItems = dueItems.filter { it.priority != TodoPriority.XHIGH }

    if (triggerAlertMode == ReminderAlertMode.FULLSCREEN_ALARM) {
        if (!canScheduleExactAlarms) {
            return ReminderDispatchPlan(
                shortNotificationItems = dueItems,
                rescheduleItems = dueItems,
            )
        }
        return if (xhighItems.isNotEmpty()) {
            ReminderDispatchPlan(
                fullscreenAlarmItems = xhighItems,
                shortNotificationItems = shortItems,
                rescheduleItems = dueItems,
            )
        } else {
            ReminderDispatchPlan(
                shortNotificationItems = shortItems,
                rescheduleItems = shortItems,
            )
        }
    }

    val hasSystemXhighAtSameTime = xhighItems.isNotEmpty() && canScheduleExactAlarms
    if (hasSystemXhighAtSameTime && triggerItem?.priority != TodoPriority.XHIGH) {
        return ReminderDispatchPlan()
    }

    val dueShortItems = if (xhighItems.isNotEmpty() && !canScheduleExactAlarms) {
        dueItems
    } else if (triggerItem?.priority == TodoPriority.XHIGH) {
        dueItems
    } else {
        shortItems
    }
    return ReminderDispatchPlan(
        shortNotificationItems = dueShortItems,
        rescheduleItems = dueShortItems,
    )
}

fun snoozeTodoAfterReminder(
    item: TodoItem,
    nowMillis: Long,
    snoozeIntervalMillis: Long = DefaultSnoozeIntervalMillis,
): TodoItem? {
    if (isTrashedTodo(item) || item.completed) return null
    return item.copy(dueAtMillis = nowMillis + snoozeIntervalMillis)
}

fun snoozeTodoAfterReminder(
    state: TodoChecklistState,
    todoId: String,
    nowMillis: Long,
    snoozeIntervalMillis: Long = DefaultSnoozeIntervalMillis,
): TodoChecklistState? {
    return snoozeTodosAfterReminder(
        state = state,
        todoIds = setOf(todoId),
        nowMillis = nowMillis,
        snoozeIntervalMillis = snoozeIntervalMillis,
    )
}

fun snoozeTodosAfterReminder(
    state: TodoChecklistState,
    todoIds: Set<String>,
    nowMillis: Long,
    snoozeIntervalMillis: Long = DefaultSnoozeIntervalMillis,
): TodoChecklistState? {
    if (todoIds.isEmpty()) return null

    var changed = false
    val updatedLists = state.lists.map { checklist ->
        if (isTrashChecklist(checklist)) return@map checklist
        val updatedItems = checklist.items.map { item ->
            if (item.id in todoIds) {
                snoozeTodoAfterReminder(item, nowMillis, snoozeIntervalMillis)?.also {
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

fun nextReminderAtMillis(item: TodoItem, nowMillis: Long): Long? {
    if (isTrashedTodo(item)) return null
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

fun normalizeRepeatingDueAtMillis(
    dueAtMillis: Long,
    reminderRepeat: ReminderRepeat,
    nowMillis: Long,
): Long {
    if (reminderRepeat == ReminderRepeat.NONE) return dueAtMillis
    return nextReminderAtMillis(
        dueAtMillis = dueAtMillis,
        reminderRepeat = reminderRepeat,
        nowMillis = nowMillis,
    ) ?: dueAtMillis
}

fun advanceRepeatingTodoAfterReminder(
    item: TodoItem,
    nowMillis: Long,
): TodoItem? {
    if (isTrashedTodo(item)) return null
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
    return advanceRepeatingTodosAfterReminder(
        state = state,
        todoIds = setOf(todoId),
        nowMillis = nowMillis,
    )
}

fun advanceRepeatingTodosAfterReminder(
    state: TodoChecklistState,
    todoIds: Set<String>,
    nowMillis: Long,
): TodoChecklistState? {
    if (todoIds.isEmpty()) return null

    var changed = false
    val updatedLists = state.lists.map { checklist ->
        if (isTrashChecklist(checklist)) return@map checklist
        val updatedItems = checklist.items.map { item ->
            if (item.id in todoIds) {
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

private fun isAlignedRepeatingReminder(
    dueAtMillis: Long,
    firedDueAtMillis: Long,
    intervalMillis: Long,
): Boolean {
    return (firedDueAtMillis - dueAtMillis) % intervalMillis == 0L
}

private fun moveTodosToTrash(
    state: TodoChecklistState,
    sourceChecklist: TodoChecklist,
    todoIds: Set<String>,
    trashedAtMillis: Long,
): TodoChecklistState? {
    val trash = state.lists.firstOrNull(::isTrashChecklist) ?: return null
    val movedItems = sourceChecklist.items
        .filter { it.id in todoIds }
        .map { item ->
            item.toTrashItem(
                sourceChecklist = sourceChecklist,
                trashedAtMillis = trashedAtMillis,
            )
        }
    if (movedItems.isEmpty()) return null

    val updatedLists = state.lists.map { checklist ->
        when {
            checklist.id == sourceChecklist.id -> {
                checklist.copy(items = checklist.items.filterNot { it.id in todoIds })
            }
            isTrashChecklist(checklist) -> {
                trash.copy(items = trash.items + movedItems)
            }
            else -> checklist
        }
    }

    return state.copy(lists = updatedLists)
}

private fun TodoItem.toTrashItem(
    sourceChecklist: TodoChecklist,
    trashedAtMillis: Long,
): TodoItem {
    return copy(
        trashedFromChecklistId = sourceChecklist.id,
        trashedFromChecklistName = sourceChecklist.name,
        trashedAtMillis = trashedAtMillis,
    )
}

private fun restoredChecklistName(item: TodoItem): String {
    val sourceName = item.trashedFromChecklistName
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: return DefaultChecklistName

    return if (sourceName.equals(TrashChecklistName, ignoreCase = true)) {
        DefaultChecklistName
    } else {
        sourceName
    }
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
