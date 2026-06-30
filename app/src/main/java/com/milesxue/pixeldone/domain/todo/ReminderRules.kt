package com.milesxue.pixeldone.domain.todo

/**
 * 提醒领域规则。
 * 这里决定“是否提醒、用全屏闹钟还是短通知、重复任务如何推进、批量提醒如何派发”。真正的 AlarmManager 调用放在 reminder package。
 */

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
        if (!isNormalChecklist(checklist)) return@map checklist
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
        if (!isNormalChecklist(checklist)) return@map checklist
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
