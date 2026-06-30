package com.milesxue.pixeldone

import com.milesxue.pixeldone.data.todo.TodoJsonCodec
import com.milesxue.pixeldone.domain.todo.*
import com.milesxue.pixeldone.ui.todo.CompletionSortDelayMillis
import com.milesxue.pixeldone.ui.todo.PendingTodoToggleFeedback
import com.milesxue.pixeldone.ui.todo.TodoRowClickAction
import com.milesxue.pixeldone.ui.todo.defaultDueAtMillis
import com.milesxue.pixeldone.ui.todo.firstRevealTargetIndex
import com.milesxue.pixeldone.ui.todo.formatDeadlineCountdown
import com.milesxue.pixeldone.ui.todo.isDueExpired
import com.milesxue.pixeldone.ui.todo.nextTodoListClockRefreshDelayMillis
import com.milesxue.pixeldone.ui.todo.recordTodoToggle
import com.milesxue.pixeldone.ui.todo.todoRowClickAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class TodoEngineTest {
    @Test
    fun createTodoItemRejectsBlankNamesAndTrimsValidNames() {
        assertNull(
            createTodoItem(
                id = "blank",
                titleInput = "   ",
                priority = TodoPriority.MEDIUM,
                dueAtMillis = 1_000L,
                createdAtMillis = 1L,
            ),
        )

        val item = createTodoItem(
            id = "task",
            titleInput = "  Ship build  ",
            priority = TodoPriority.HIGH,
            dueAtMillis = 1_000L,
            createdAtMillis = 1L,
        )

        assertEquals("Ship build", item?.title)
        assertFalse(item?.completed ?: true)
        assertEquals(ReminderRepeat.NONE, item?.reminderRepeat)
    }

    @Test
    fun prioritySortKeepsActiveFirstThenPriorityThenTime() {
        val items = listOf(
            item("done-high", TodoPriority.HIGH, due = 100L, completed = true, created = 1L),
            item("low", TodoPriority.LOW, due = 10L, created = 2L),
            item("high-late", TodoPriority.HIGH, due = 30L, created = 3L),
            item("high-early", TodoPriority.HIGH, due = 20L, created = 4L),
            item("xhigh", TodoPriority.XHIGH, due = 40L, created = 6L),
            item("mid", TodoPriority.MEDIUM, due = 5L, created = 5L),
        )

        val sortedIds = visibleTodos(items, SortMode.PRIORITY, hideCompleted = false)
            .map { it.id }

        assertEquals(
            listOf("xhigh", "high-early", "high-late", "mid", "low", "done-high"),
            sortedIds,
        )
    }

    @Test
    fun xhighPrioritySortsAboveHigh() {
        val items = listOf(
            item("high", TodoPriority.HIGH, due = 1L),
            item("xhigh", TodoPriority.XHIGH, due = 99L),
        )

        val sortedIds = visibleTodos(items, SortMode.PRIORITY, hideCompleted = false)
            .map { it.id }

        assertEquals(listOf("xhigh", "high"), sortedIds)
    }

    @Test
    fun timeSortKeepsActiveFirstThenTimeThenPriorityThenCreation() {
        val items = listOf(
            item("done-early", TodoPriority.HIGH, due = 1L, completed = true, created = 1L),
            item("late", TodoPriority.HIGH, due = 20L, created = 2L),
            item("same-time-low", TodoPriority.LOW, due = 10L, created = 3L),
            item("same-time-high", TodoPriority.HIGH, due = 10L, created = 4L),
            item("same-time-high-old", TodoPriority.HIGH, due = 10L, created = 1L),
        )

        val sortedIds = visibleTodos(items, SortMode.TIME, hideCompleted = false)
            .map { it.id }

        assertEquals(
            listOf("same-time-high-old", "same-time-high", "same-time-low", "late", "done-early"),
            sortedIds,
        )
    }

    @Test
    fun hideCompletedFiltersCompletedItems() {
        val items = listOf(
            item("active", TodoPriority.MEDIUM, due = 1L),
            item("done", TodoPriority.HIGH, due = 2L, completed = true),
        )

        val visibleIds = visibleTodos(items, SortMode.PRIORITY, hideCompleted = true)
            .map { it.id }

        assertEquals(listOf("active"), visibleIds)
    }

    @Test
    fun toggleCompletionChangesOnlyTheMatchingTodo() {
        val items = listOf(
            item("one", TodoPriority.MEDIUM, due = 1L),
            item("two", TodoPriority.MEDIUM, due = 2L),
        )

        val updated = toggleTodoCompletion(items, "two")

        assertFalse(updated.first { it.id == "one" }.completed)
        assertTrue(updated.first { it.id == "two" }.completed)
    }

    @Test
    fun updateTodoItemChangesEditableFieldsAndKeepsExistingState() {
        val items = listOf(
            item("one", TodoPriority.MEDIUM, due = 1L, completed = true, created = 10L),
            item("two", TodoPriority.LOW, due = 2L, created = 20L),
        )

        val updated = updateTodoItem(
            items = items,
            id = "one",
            titleInput = "  Updated task  ",
            priority = TodoPriority.HIGH,
            dueAtMillis = 99L,
            reminderRepeat = ReminderRepeat.WEEKLY,
        )

        val changed = updated?.first { it.id == "one" }
        assertEquals("Updated task", changed?.title)
        assertEquals(TodoPriority.HIGH, changed?.priority)
        assertEquals(99L, changed?.dueAtMillis)
        assertEquals(ReminderRepeat.WEEKLY, changed?.reminderRepeat)
        assertNull(changed?.imageFileName)
        assertEquals(10L, changed?.createdAtMillis)
        assertTrue(changed?.completed ?: false)
        assertEquals(items.first { it.id == "two" }, updated?.first { it.id == "two" })
    }

    @Test
    fun updateTodoItemRejectsBlankNamesAndMissingIds() {
        val items = listOf(item("one", TodoPriority.MEDIUM, due = 1L))

        assertNull(
            updateTodoItem(
                items = items,
                id = "one",
                titleInput = " ",
                priority = TodoPriority.HIGH,
                dueAtMillis = 2L,
            ),
        )
        assertNull(
            updateTodoItem(
                items = items,
                id = "missing",
                titleInput = "Valid",
                priority = TodoPriority.HIGH,
                dueAtMillis = 2L,
            ),
        )
    }

    @Test
    fun updateTodoImageFileNameChangesOnlyTheMatchingTodo() {
        val items = listOf(
            item("one", TodoPriority.MEDIUM, due = 1L, imageFileName = "old.img"),
            item("two", TodoPriority.HIGH, due = 2L),
        )

        val withImage = updateTodoImageFileName(items, "two", "new.img")
        assertEquals("new.img", withImage?.first { it.id == "two" }?.imageFileName)
        assertEquals("old.img", withImage?.first { it.id == "one" }?.imageFileName)

        val withoutImage = updateTodoImageFileName(withImage.orEmpty(), "two", null)
        assertNull(withoutImage?.first { it.id == "two" }?.imageFileName)
        assertNull(updateTodoImageFileName(items, "missing", "new.img"))
    }

    @Test
    fun deleteCompletedRemovesOnlyCompletedTodos() {
        val items = listOf(
            item("active", TodoPriority.MEDIUM, due = 1L),
            item("done", TodoPriority.MEDIUM, due = 2L, completed = true),
        )

        assertEquals(listOf("active"), deleteCompletedTodos(items).map { it.id })
    }

    @Test
    fun deleteTodoItemRemovesOnlyMatchingTodo() {
        val items = listOf(
            item("one", TodoPriority.MEDIUM, due = 1L),
            item("two", TodoPriority.HIGH, due = 2L, completed = true),
            item("three", TodoPriority.LOW, due = 3L),
        )

        assertEquals(listOf("one", "three"), deleteTodoItem(items, "two").map { it.id })
        assertEquals(items, deleteTodoItem(items, "missing"))
    }

    @Test
    fun alarmSchedulingRequiresActiveFutureTodos() {
        assertTrue(
            shouldScheduleTodoAlarm(
                item("future", TodoPriority.MEDIUM, due = 2_000L),
                nowMillis = 1_000L,
            ),
        )
        assertFalse(
            shouldScheduleTodoAlarm(
                item("done", TodoPriority.MEDIUM, due = 2_000L, completed = true),
                nowMillis = 1_000L,
            ),
        )
        assertFalse(
            shouldScheduleTodoAlarm(
                item("past", TodoPriority.MEDIUM, due = 1_000L),
                nowMillis = 1_000L,
            ),
        )
        assertTrue(
            shouldScheduleTodoAlarm(
                item(
                    id = "past-daily",
                    priority = TodoPriority.MEDIUM,
                    due = 1_000L,
                    repeat = ReminderRepeat.DAILY,
                ),
                nowMillis = 2_000L,
            ),
        )
        assertFalse(
            shouldScheduleTodoAlarm(
                item(
                    id = "trash",
                    priority = TodoPriority.MEDIUM,
                    due = 2_000L,
                    trashedFromChecklistId = DefaultChecklistId,
                    trashedFromChecklistName = DefaultChecklistName,
                    trashedAtMillis = 1_500L,
                ),
                nowMillis = 1_000L,
            ),
        )
    }

    @Test
    fun reminderModesMapXhighToSystemAlarmAndOtherPrioritiesToShortNotifications() {
        assertEquals(
            ReminderScheduleMode.SYSTEM_ALARM,
            reminderScheduleMode(
                item("xhigh", TodoPriority.XHIGH, due = 2_000L),
                nowMillis = 1_000L,
            ),
        )
        assertEquals(
            ReminderAlertMode.FULLSCREEN_ALARM,
            reminderAlertMode(
                item("xhigh", TodoPriority.XHIGH, due = 2_000L),
                nowMillis = 1_000L,
            ),
        )
        assertEquals(
            ReminderScheduleMode.INEXACT_NOTIFICATION,
            reminderScheduleMode(
                item("high", TodoPriority.HIGH, due = 2_000L),
                nowMillis = 1_000L,
            ),
        )
        assertEquals(
            ReminderAlertMode.SHORT_NOTIFICATION,
            reminderAlertMode(
                item("high", TodoPriority.HIGH, due = 2_000L),
                nowMillis = 1_000L,
            ),
        )
        assertEquals(
            ReminderScheduleMode.INEXACT_NOTIFICATION,
            reminderScheduleMode(
                item("mid", TodoPriority.MEDIUM, due = 2_000L),
                nowMillis = 1_000L,
            ),
        )
        assertEquals(
            ReminderScheduleMode.INEXACT_NOTIFICATION,
            reminderScheduleMode(
                item("low", TodoPriority.LOW, due = 2_000L),
                nowMillis = 1_000L,
            ),
        )
        assertNull(
            reminderScheduleMode(
                item("done-xhigh", TodoPriority.XHIGH, due = 2_000L, completed = true),
                nowMillis = 1_000L,
            ),
        )
    }

    @Test
    fun requiredReminderCapabilitiesDependOnAffectedTodoOnly() {
        assertEquals(
            setOf(
                ReminderCapability.NOTIFICATION_PERMISSION,
                ReminderCapability.EXACT_ALARM_ACCESS,
                ReminderCapability.FULL_SCREEN_INTENT_ACCESS,
            ),
            requiredReminderCapabilities(
                item("xhigh", TodoPriority.XHIGH, due = 2_000L),
                nowMillis = 1_000L,
            ),
        )
        assertEquals(
            setOf(
                ReminderCapability.NOTIFICATION_PERMISSION,
            ),
            requiredReminderCapabilities(
                item("high", TodoPriority.HIGH, due = 2_000L),
                nowMillis = 1_000L,
            ),
        )
        assertEquals(
            emptySet<ReminderCapability>(),
            requiredReminderCapabilities(
                item("past", TodoPriority.XHIGH, due = 1_000L),
                nowMillis = 1_000L,
            ),
        )
    }

    @Test
    fun reminderNotificationIdsUseSeparateNamespaces() {
        val shortItemId = ReminderNotificationIds.shortItem("normal")
        val shortBatchId = ReminderNotificationIds.shortBatch(2_000L)
        val xhighId = ReminderNotificationIds.XHIGH_ALARM

        assertTrue(shortItemId in 0x10000000..0x1FFFFFFF)
        assertTrue(shortBatchId in 0x20000000..0x2FFFFFFF)
        assertTrue(xhighId in 0x30000000..0x3FFFFFFF)
        assertNotEquals(shortItemId, shortBatchId)
        assertNotEquals(shortItemId, xhighId)
        assertNotEquals(shortBatchId, xhighId)
    }

    @Test
    fun exactAlarmAccessRequiresPermissionOnlyOnAndroid12AndLater() {
        assertTrue(canScheduleExactAlarmForSdk(sdkInt = 30, canScheduleExactAlarms = false))
        assertTrue(canScheduleExactAlarmForSdk(sdkInt = 31, canScheduleExactAlarms = true))
        assertFalse(canScheduleExactAlarmForSdk(sdkInt = 31, canScheduleExactAlarms = false))
        assertFalse(canScheduleExactAlarmForSdk(sdkInt = 37, canScheduleExactAlarms = false))
    }

    @Test
    fun xhighFallsBackToShortNotificationWhenExactAlarmAccessIsMissing() {
        val xhigh = item("xhigh", TodoPriority.XHIGH, due = 2_000L)
        val high = item("high", TodoPriority.HIGH, due = 2_000L)

        assertEquals(
            ReminderScheduleMode.SYSTEM_ALARM,
            effectiveReminderScheduleMode(
                item = xhigh,
                nowMillis = 1_000L,
                canScheduleExactAlarms = true,
            ),
        )
        assertEquals(
            ReminderAlertMode.FULLSCREEN_ALARM,
            effectiveReminderAlertMode(
                item = xhigh,
                nowMillis = 1_000L,
                canScheduleExactAlarms = true,
            ),
        )
        assertEquals(
            ReminderScheduleMode.INEXACT_NOTIFICATION,
            effectiveReminderScheduleMode(
                item = xhigh,
                nowMillis = 1_000L,
                canScheduleExactAlarms = false,
            ),
        )
        assertEquals(
            ReminderAlertMode.SHORT_NOTIFICATION,
            effectiveReminderAlertMode(
                item = xhigh,
                nowMillis = 1_000L,
                canScheduleExactAlarms = false,
            ),
        )
        assertEquals(
            ReminderScheduleMode.INEXACT_NOTIFICATION,
            effectiveReminderScheduleMode(
                item = high,
                nowMillis = 1_000L,
                canScheduleExactAlarms = false,
            ),
        )
    }

    @Test
    fun reminderDispatchRequiresCurrentActiveTodo() {
        assertTrue(
            shouldDispatchTodoReminder(
                item("one-shot", TodoPriority.MEDIUM, due = 2_000L),
                firedDueAtMillis = 2_000L,
            ),
        )
        assertFalse(
            shouldDispatchTodoReminder(
                item("edited", TodoPriority.MEDIUM, due = 3_000L),
                firedDueAtMillis = 2_000L,
            ),
        )
        assertFalse(
            shouldDispatchTodoReminder(
                item("done", TodoPriority.MEDIUM, due = 2_000L, completed = true),
                firedDueAtMillis = 2_000L,
            ),
        )
        assertTrue(
            shouldDispatchTodoReminder(
                item("daily", TodoPriority.XHIGH, due = 1_000L, repeat = ReminderRepeat.DAILY),
                firedDueAtMillis = 1_000L + DailyReminderIntervalMillis,
            ),
        )
    }

    @Test
    fun completedTodoDoesNotScheduleDispatchOrAppearInDueReminderSet() {
        val completed = item(
            id = "done",
            priority = TodoPriority.XHIGH,
            due = 2_000L,
            completed = true,
            repeat = ReminderRepeat.DAILY,
        )

        assertNull(nextReminderAtMillis(completed, nowMillis = 1_000L))
        assertFalse(shouldScheduleTodoAlarm(completed, nowMillis = 1_000L))
        assertFalse(shouldDispatchTodoReminder(completed, firedDueAtMillis = 2_000L))
        assertEquals(emptyList<TodoItem>(), todosDueForReminder(listOf(completed), firedDueAtMillis = 2_000L))
    }

    @Test
    fun unchangedPastDueOneShotAlarmIsPreservedDuringFullSync() {
        val unchangedPastDue = item("late", TodoPriority.HIGH, due = 2_000L)
        assertFalse(
            shouldCancelUnschedulableTodoAlarm(
                previousItem = unchangedPastDue,
                item = unchangedPastDue,
                nowMillis = 3_000L,
            ),
        )

        assertTrue(
            shouldCancelUnschedulableTodoAlarm(
                previousItem = unchangedPastDue.copy(dueAtMillis = 4_000L),
                item = unchangedPastDue,
                nowMillis = 3_000L,
            ),
        )
        assertTrue(
            shouldCancelUnschedulableTodoAlarm(
                previousItem = unchangedPastDue,
                item = unchangedPastDue.copy(completed = true),
                nowMillis = 3_000L,
            ),
        )
    }

    @Test
    fun todosDueForReminderCollectsSameTimeActiveTodosOnly() {
        val dueItems = todosDueForReminder(
            items = listOf(
                item("low", TodoPriority.LOW, due = 2_000L, created = 4L),
                item("xhigh", TodoPriority.XHIGH, due = 2_000L, created = 3L),
                item("high", TodoPriority.HIGH, due = 2_000L, created = 2L),
                item("done", TodoPriority.XHIGH, due = 2_000L, completed = true),
                item("edited", TodoPriority.MEDIUM, due = 3_000L),
                item(
                    id = "trash",
                    priority = TodoPriority.XHIGH,
                    due = 2_000L,
                    trashedFromChecklistId = DefaultChecklistId,
                    trashedFromChecklistName = DefaultChecklistName,
                    trashedAtMillis = 1_500L,
                ),
            ),
            firedDueAtMillis = 2_000L,
        )

        assertEquals(listOf("xhigh", "high", "low"), dueItems.map { it.id })
    }

    @Test
    fun dispatchPlanStartsFullscreenForSystemXhighAndKeepsShortItemsAsCompanions() {
        val items = listOf(
            item("low", TodoPriority.LOW, due = 2_000L, created = 4L),
            item("xhigh", TodoPriority.XHIGH, due = 2_000L, created = 3L),
            item("high", TodoPriority.HIGH, due = 2_000L, created = 2L),
        )

        val plan = reminderDispatchPlan(
            items = items,
            firedDueAtMillis = 2_000L,
            triggerTodoId = "xhigh",
            triggerAlertMode = ReminderAlertMode.FULLSCREEN_ALARM,
            canScheduleExactAlarms = true,
        )

        assertEquals(listOf("xhigh"), plan.fullscreenAlarmItems.map { it.id })
        assertEquals(listOf("high", "low"), plan.shortNotificationItems.map { it.id })
        assertEquals(listOf("xhigh", "high", "low"), plan.rescheduleItems.map { it.id })
    }

    @Test
    fun dispatchPlanSuppressesNormalShortNotificationWhenSystemXhighOwnsTheSameTime() {
        val items = listOf(
            item("low", TodoPriority.LOW, due = 2_000L, created = 4L),
            item("xhigh", TodoPriority.XHIGH, due = 2_000L, created = 3L),
            item("high", TodoPriority.HIGH, due = 2_000L, created = 2L),
        )

        val plan = reminderDispatchPlan(
            items = items,
            firedDueAtMillis = 2_000L,
            triggerTodoId = "high",
            triggerAlertMode = ReminderAlertMode.SHORT_NOTIFICATION,
            canScheduleExactAlarms = true,
        )

        assertTrue(plan.isEmpty)
        assertEquals(emptyList<TodoItem>(), plan.rescheduleItems)
    }

    @Test
    fun dispatchPlanUsesShortNotificationForXhighFallbackWhenExactAccessIsMissing() {
        val items = listOf(
            item("low", TodoPriority.LOW, due = 2_000L, created = 4L),
            item("xhigh", TodoPriority.XHIGH, due = 2_000L, created = 3L),
            item("high", TodoPriority.HIGH, due = 2_000L, created = 2L),
        )

        val plan = reminderDispatchPlan(
            items = items,
            firedDueAtMillis = 2_000L,
            triggerTodoId = "xhigh",
            triggerAlertMode = ReminderAlertMode.SHORT_NOTIFICATION,
            canScheduleExactAlarms = false,
        )

        assertEquals(emptyList<TodoItem>(), plan.fullscreenAlarmItems)
        assertEquals(listOf("xhigh", "high", "low"), plan.shortNotificationItems.map { it.id })
        assertEquals(listOf("xhigh", "high", "low"), plan.rescheduleItems.map { it.id })
    }

    @Test
    fun dispatchPlanTreatsStaleFullscreenTriggerAsShortWhenExactAccessWasRevoked() {
        val items = listOf(
            item("xhigh", TodoPriority.XHIGH, due = 2_000L, created = 1L),
            item("high", TodoPriority.HIGH, due = 2_000L, created = 2L),
        )

        val plan = reminderDispatchPlan(
            items = items,
            firedDueAtMillis = 2_000L,
            triggerTodoId = "xhigh",
            triggerAlertMode = ReminderAlertMode.FULLSCREEN_ALARM,
            canScheduleExactAlarms = false,
        )

        assertEquals(emptyList<TodoItem>(), plan.fullscreenAlarmItems)
        assertEquals(listOf("xhigh", "high"), plan.shortNotificationItems.map { it.id })
        assertEquals(listOf("xhigh", "high"), plan.rescheduleItems.map { it.id })
    }

    @Test
    fun snoozeReminderMovesDueTimeWithoutCompletingTodo() {
        val todo = item("xhigh", TodoPriority.XHIGH, due = 2_000L)

        val snoozed = snoozeTodoAfterReminder(
            item = todo,
            nowMillis = 5_000L,
            snoozeIntervalMillis = 600L,
        )

        assertEquals(todo.copy(dueAtMillis = 5_600L), snoozed)
        assertFalse(snoozed?.completed ?: true)
        assertNull(
            snoozeTodoAfterReminder(
                item = todo.copy(completed = true),
                nowMillis = 5_000L,
            ),
        )
    }

    @Test
    fun snoozeReminderUpdatesNestedChecklistState() {
        val todo = item("xhigh", TodoPriority.XHIGH, due = 2_000L)
        val state = TodoChecklistState(
            lists = listOf(
                TodoChecklist(
                    id = DefaultChecklistId,
                    name = DefaultChecklistName,
                    items = listOf(todo),
                    createdAtMillis = 1L,
                ),
                TodoChecklist(
                    id = TrashChecklistId,
                    name = TrashChecklistName,
                    items = emptyList(),
                    createdAtMillis = 2L,
                ),
            ),
            selectedListId = DefaultChecklistId,
        )

        val snoozed = snoozeTodoAfterReminder(
            state = state,
            todoId = "xhigh",
            nowMillis = 5_000L,
            snoozeIntervalMillis = 600L,
        )

        assertEquals(
            todo.copy(dueAtMillis = 5_600L),
            snoozed?.lists?.first { it.id == DefaultChecklistId }?.items?.single(),
        )
        assertNull(snoozeTodoAfterReminder(state, "missing", nowMillis = 5_000L))
    }

    @Test
    fun snoozeTodosAfterReminderUpdatesBatchWithoutTouchingCompletedOrTrash() {
        val first = item("first", TodoPriority.XHIGH, due = 2_000L)
        val second = item("second", TodoPriority.XHIGH, due = 2_000L)
        val completed = item("completed", TodoPriority.XHIGH, due = 2_000L, completed = true)
        val trashed = item(
            id = "trashed",
            priority = TodoPriority.XHIGH,
            due = 2_000L,
            trashedFromChecklistId = DefaultChecklistId,
            trashedFromChecklistName = DefaultChecklistName,
            trashedAtMillis = 1_000L,
        )
        val state = TodoChecklistState(
            lists = listOf(
                TodoChecklist(
                    id = DefaultChecklistId,
                    name = DefaultChecklistName,
                    items = listOf(first, second, completed),
                    createdAtMillis = 1L,
                ),
                TodoChecklist(
                    id = TrashChecklistId,
                    name = TrashChecklistName,
                    items = listOf(trashed),
                    createdAtMillis = 2L,
                ),
            ),
            selectedListId = DefaultChecklistId,
        )

        val snoozed = snoozeTodosAfterReminder(
            state = state,
            todoIds = setOf("first", "second", "completed", "trashed"),
            nowMillis = 5_000L,
            snoozeIntervalMillis = 600L,
        )!!
        val mainItems = snoozed.lists.first { it.id == DefaultChecklistId }.items

        assertEquals(5_600L, mainItems.first { it.id == "first" }.dueAtMillis)
        assertEquals(5_600L, mainItems.first { it.id == "second" }.dueAtMillis)
        assertEquals(2_000L, mainItems.first { it.id == "completed" }.dueAtMillis)
        assertEquals(listOf(trashed), trashTodos(snoozed))
    }

    @Test
    fun nextReminderAtMillisSupportsOneShotDailyAndWeeklyRules() {
        assertEquals(
            2_000L,
            nextReminderAtMillis(
                dueAtMillis = 2_000L,
                reminderRepeat = ReminderRepeat.NONE,
                nowMillis = 1_000L,
            ),
        )
        assertNull(
            nextReminderAtMillis(
                dueAtMillis = 1_000L,
                reminderRepeat = ReminderRepeat.NONE,
                nowMillis = 1_000L,
            ),
        )
        assertEquals(
            1_000L + DailyReminderIntervalMillis,
            nextReminderAtMillis(
                dueAtMillis = 1_000L,
                reminderRepeat = ReminderRepeat.DAILY,
                nowMillis = 1_000L,
            ),
        )
        assertEquals(
            1_000L + WeeklyReminderIntervalMillis,
            nextReminderAtMillis(
                dueAtMillis = 1_000L,
                reminderRepeat = ReminderRepeat.WEEKLY,
                nowMillis = 1_000L + DailyReminderIntervalMillis,
            ),
        )
    }

    @Test
    fun normalizeRepeatingDueAtMillisAdvancesExpiredDailyAndWeeklyRules() {
        assertEquals(
            1_000L + DailyReminderIntervalMillis,
            normalizeRepeatingDueAtMillis(
                dueAtMillis = 1_000L,
                reminderRepeat = ReminderRepeat.DAILY,
                nowMillis = 1_000L,
            ),
        )
        assertEquals(
            2_000L + WeeklyReminderIntervalMillis,
            normalizeRepeatingDueAtMillis(
                dueAtMillis = 2_000L,
                reminderRepeat = ReminderRepeat.WEEKLY,
                nowMillis = 2_000L + DailyReminderIntervalMillis,
            ),
        )
    }

    @Test
    fun normalizeRepeatingDueAtMillisKeepsFutureAndNonRepeatingDueTimes() {
        assertEquals(
            3_000L,
            normalizeRepeatingDueAtMillis(
                dueAtMillis = 3_000L,
                reminderRepeat = ReminderRepeat.DAILY,
                nowMillis = 2_000L,
            ),
        )
        assertEquals(
            1_000L,
            normalizeRepeatingDueAtMillis(
                dueAtMillis = 1_000L,
                reminderRepeat = ReminderRepeat.NONE,
                nowMillis = 2_000L,
            ),
        )
    }

    @Test
    fun advanceRepeatingTodoAfterReminderMovesDailyAndWeeklyDueTimesForward() {
        val daily = item(
            id = "daily",
            priority = TodoPriority.MEDIUM,
            due = 1_000L,
            repeat = ReminderRepeat.DAILY,
        )
        val weekly = item(
            id = "weekly",
            priority = TodoPriority.HIGH,
            due = 2_000L,
            repeat = ReminderRepeat.WEEKLY,
        )

        val advancedDaily = advanceRepeatingTodoAfterReminder(
            item = daily,
            nowMillis = 1_000L,
        )
        val advancedWeekly = advanceRepeatingTodoAfterReminder(
            item = weekly,
            nowMillis = 2_000L + DailyReminderIntervalMillis,
        )

        assertEquals(1_000L + DailyReminderIntervalMillis, advancedDaily?.dueAtMillis)
        assertEquals(2_000L + WeeklyReminderIntervalMillis, advancedWeekly?.dueAtMillis)
        assertEquals(daily.copy(dueAtMillis = 1_000L + DailyReminderIntervalMillis), advancedDaily)
        assertEquals(weekly.copy(dueAtMillis = 2_000L + WeeklyReminderIntervalMillis), advancedWeekly)
    }

    @Test
    fun advanceRepeatingTodoAfterReminderSkipsNonRepeatingCompletedAndFutureItems() {
        assertNull(
            advanceRepeatingTodoAfterReminder(
                item("one-shot", TodoPriority.MEDIUM, due = 1_000L),
                nowMillis = 1_000L,
            ),
        )
        assertNull(
            advanceRepeatingTodoAfterReminder(
                item(
                    id = "completed",
                    priority = TodoPriority.MEDIUM,
                    due = 1_000L,
                    completed = true,
                    repeat = ReminderRepeat.DAILY,
                ),
                nowMillis = 1_000L,
            ),
        )
        assertNull(
            advanceRepeatingTodoAfterReminder(
                item(
                    id = "future",
                    priority = TodoPriority.MEDIUM,
                    due = 2_000L,
                    repeat = ReminderRepeat.DAILY,
                ),
                nowMillis = 1_000L,
            ),
        )
    }

    @Test
    fun advanceRepeatingTodoAfterReminderUpdatesNestedChecklistState() {
        val mainTodo = item("main", TodoPriority.MEDIUM, due = 1_000L)
        val repeatingTodo = item(
            id = "repeat",
            priority = TodoPriority.HIGH,
            due = 2_000L,
            repeat = ReminderRepeat.DAILY,
        )
        val state = TodoChecklistState(
            lists = listOf(
                TodoChecklist(
                    id = DefaultChecklistId,
                    name = DefaultChecklistName,
                    items = listOf(mainTodo),
                    createdAtMillis = 1L,
                ),
                TodoChecklist(
                    id = "work",
                    name = "Work",
                    items = listOf(repeatingTodo),
                    createdAtMillis = 2L,
                ),
            ),
            selectedListId = "work",
        )

        val updatedState = advanceRepeatingTodoAfterReminder(
            state = state,
            todoId = "repeat",
            nowMillis = 2_000L,
        )

        assertEquals("work", updatedState?.selectedListId)
        assertEquals(listOf(mainTodo), updatedState?.lists?.first { it.id == DefaultChecklistId }?.items)
        assertEquals(
            repeatingTodo.copy(dueAtMillis = 2_000L + DailyReminderIntervalMillis),
            updatedState?.lists?.first { it.id == "work" }?.items?.single(),
        )
        assertNull(advanceRepeatingTodoAfterReminder(state, "missing", nowMillis = 2_000L))
    }

    @Test
    fun jsonCodecRoundTripsTodosWithEscapedText() {
        val items = listOf(
            item(
                id = "quoted",
                priority = TodoPriority.XHIGH,
                due = 1_700_000_000_000L,
                completed = false,
                created = 1L,
                title = "Review \"PixelDone\"\\notes\nnow",
                repeat = ReminderRepeat.DAILY,
                imageFileName = "quoted.img",
            ),
            item(
                id = "done",
                priority = TodoPriority.LOW,
                due = 1_700_000_003_000L,
                completed = true,
                created = 2L,
                title = "Archive",
                trashedFromChecklistId = "work",
                trashedFromChecklistName = "Work",
                trashedAtMillis = 3L,
            ),
        )

        val decoded = TodoJsonCodec.decode(TodoJsonCodec.encode(items))

        assertEquals(items, decoded)
    }

    @Test
    fun jsonCodecDefaultsMissingReminderRepeatToNone() {
        val decoded = TodoJsonCodec.decode(
            """
            [
              {
                "id": "legacy",
                "title": "Legacy task",
                "priority": "HIGH",
                "dueAtMillis": 1700000000000,
                "completed": false,
                "createdAtMillis": 1
              }
            ]
            """.trimIndent(),
        )

        assertEquals(ReminderRepeat.NONE, decoded.single().reminderRepeat)
        assertNull(decoded.single().imageFileName)
        assertNull(decoded.single().trashedFromChecklistId)
        assertNull(decoded.single().trashedFromChecklistName)
        assertNull(decoded.single().trashedAtMillis)
    }

    @Test
    fun initialChecklistStateWrapsLegacyTodosInMainList() {
        val legacyTodos = listOf(item("legacy", TodoPriority.MEDIUM, due = 1L))

        val state = createInitialChecklistState(legacyTodos, createdAtMillis = 42L)

        assertEquals(DefaultChecklistId, state.selectedListId)
        assertEquals(listOf(DefaultChecklistId, TrashChecklistId), state.lists.map { it.id })
        assertEquals(DefaultChecklistName, state.lists.first { it.id == DefaultChecklistId }.name)
        assertEquals(42L, state.lists.first { it.id == DefaultChecklistId }.createdAtMillis)
        assertEquals(legacyTodos, state.lists.first { it.id == DefaultChecklistId }.items)
        assertEquals(TrashChecklistName, state.lists.first { it.id == TrashChecklistId }.name)
        assertEquals(emptyList<TodoItem>(), state.lists.first { it.id == TrashChecklistId }.items)
    }

    @Test
    fun createChecklistTrimsNamesSelectsNewListAndRejectsInvalidNames() {
        val state = createInitialChecklistState(emptyList(), createdAtMillis = 1L)

        val withWork = createTodoChecklist(
            state = state,
            id = "work",
            nameInput = "  Work  ",
            createdAtMillis = 2L,
        )!!

        assertEquals("work", withWork.selectedListId)
        assertEquals(listOf(DefaultChecklistId, "work", TrashChecklistId), withWork.lists.map { it.id })
        assertEquals("Work", withWork.lists.first { it.id == "work" }.name)
        assertNull(createTodoChecklist(withWork, "blank", " ", createdAtMillis = 3L))
        assertNull(createTodoChecklist(withWork, "duplicate", "work", createdAtMillis = 3L))
        assertNull(createTodoChecklist(withWork, "trash-name", "TRASH", createdAtMillis = 3L))
        assertNull(createTodoChecklist(withWork, TrashChecklistId, "Other", createdAtMillis = 3L))
    }

    @Test
    fun renameChecklistKeepsNamesUniqueCaseInsensitively() {
        val state = createTodoChecklist(
            state = createInitialChecklistState(emptyList(), createdAtMillis = 1L),
            id = "work",
            nameInput = "Work",
            createdAtMillis = 2L,
        )!!

        val renamed = renameTodoChecklist(state, DefaultChecklistId, "  Home  ")!!

        assertEquals("Home", renamed.lists.first { it.id == DefaultChecklistId }.name)
        assertNull(renameTodoChecklist(renamed, DefaultChecklistId, "WORK"))
        assertNull(renameTodoChecklist(renamed, DefaultChecklistId, "TRASH"))
        assertNull(renameTodoChecklist(renamed, TrashChecklistId, "Archive"))
        assertNull(renameTodoChecklist(renamed, "missing", "Other"))
    }

    @Test
    fun deleteChecklistMovesItemsToTrashKeepsAtLeastOneNormalListAndFallsBackSelection() {
        val workDone = item("work-done", TodoPriority.HIGH, due = 2L, completed = true)
        val workActive = item("work-active", TodoPriority.MEDIUM, due = 3L)
        val state = createTodoChecklist(
            state = createInitialChecklistState(emptyList(), createdAtMillis = 1L),
            id = "work",
            nameInput = "Work",
            createdAtMillis = 2L,
        )!!.let { created ->
            updateChecklistItems(created, "work", listOf(workDone, workActive))!!
        }

        val deletedSelected = deleteTodoChecklist(state, "work", trashedAtMillis = 99L)!!

        assertEquals(listOf(DefaultChecklistId, TrashChecklistId), deletedSelected.lists.map { it.id })
        assertEquals(DefaultChecklistId, deletedSelected.selectedListId)
        assertEquals(emptyList<TodoItem>(), deletedSelected.lists.first { it.id == DefaultChecklistId }.items)
        val trashItems = trashTodos(deletedSelected)
        assertEquals(listOf("work-done", "work-active"), trashItems.map { it.id })
        assertTrue(trashItems.first { it.id == "work-done" }.completed)
        assertEquals("work", trashItems.first().trashedFromChecklistId)
        assertEquals("Work", trashItems.first().trashedFromChecklistName)
        assertEquals(99L, trashItems.first().trashedAtMillis)
        assertNull(deleteTodoChecklist(deletedSelected, DefaultChecklistId, trashedAtMillis = 100L))
        assertNull(deleteTodoChecklist(state, TrashChecklistId, trashedAtMillis = 100L))
    }

    @Test
    fun updateChecklistItemsChangesOnlyTheMatchingListAndAllTodosFlattensLists() {
        val mainTodo = item("main", TodoPriority.HIGH, due = 1L)
        val workTodo = item("work-task", TodoPriority.LOW, due = 2L)
        val state = createTodoChecklist(
            state = createInitialChecklistState(listOf(mainTodo), createdAtMillis = 1L),
            id = "work",
            nameInput = "Work",
            createdAtMillis = 2L,
        )!!

        val updated = updateChecklistItems(state, "work", listOf(workTodo))!!

        assertEquals(listOf(mainTodo), updated.lists.first { it.id == DefaultChecklistId }.items)
        assertEquals(listOf(workTodo), updated.lists.first { it.id == "work" }.items)
        assertEquals(listOf(mainTodo, workTodo), allTodos(updated))
    }

    @Test
    fun normalizeChecklistStateAddsFixedTrashAndKeepsOneNormalList() {
        val trashed = item(
            id = "trashed",
            priority = TodoPriority.LOW,
            due = 1L,
            trashedFromChecklistId = "old",
            trashedFromChecklistName = "Old",
            trashedAtMillis = 7L,
        )
        val state = TodoChecklistState(
            lists = listOf(
                TodoChecklist(
                    id = TrashChecklistId,
                    name = "Editable trash",
                    items = listOf(trashed),
                    createdAtMillis = 2L,
                ),
            ),
            selectedListId = "missing",
        )

        val normalized = normalizeChecklistState(state, fallbackCreatedAtMillis = 10L)

        assertEquals(listOf(DefaultChecklistId, TrashChecklistId), normalized.lists.map { it.id })
        assertEquals(DefaultChecklistId, normalized.selectedListId)
        assertEquals(TrashChecklistName, normalized.lists.first { it.id == TrashChecklistId }.name)
        assertEquals(listOf(trashed), trashTodos(normalized))
        assertEquals(1, normalChecklistCount(normalized))
    }

    @Test
    fun moveSingleTodoToTrashPreservesCompletedStateAndMetadata() {
        val completed = item("done", TodoPriority.HIGH, due = 1L, completed = true)
        val active = item("active", TodoPriority.LOW, due = 2L)
        val state = createInitialChecklistState(listOf(completed, active), createdAtMillis = 1L)

        val updated = moveTodoItemToTrash(
            state = state,
            checklistId = DefaultChecklistId,
            todoId = "done",
            trashedAtMillis = 99L,
        )!!

        assertEquals(listOf("active"), updated.lists.first { it.id == DefaultChecklistId }.items.map { it.id })
        val trashed = trashTodos(updated).single()
        assertEquals("done", trashed.id)
        assertTrue(trashed.completed)
        assertEquals(DefaultChecklistId, trashed.trashedFromChecklistId)
        assertEquals(DefaultChecklistName, trashed.trashedFromChecklistName)
        assertEquals(99L, trashed.trashedAtMillis)
    }

    @Test
    fun moveCompletedTodosToTrashPreservesActiveTodosAndCompletedState() {
        val state = createInitialChecklistState(
            items = listOf(
                item("active", TodoPriority.MEDIUM, due = 1L),
                item("done-one", TodoPriority.HIGH, due = 2L, completed = true),
                item("done-two", TodoPriority.LOW, due = 3L, completed = true),
            ),
            createdAtMillis = 1L,
        )

        val updated = moveCompletedTodosToTrash(
            state = state,
            checklistId = DefaultChecklistId,
            trashedAtMillis = 50L,
        )!!

        assertEquals(listOf("active"), updated.lists.first { it.id == DefaultChecklistId }.items.map { it.id })
        assertEquals(listOf("done-one", "done-two"), trashTodos(updated).map { it.id })
        assertTrue(trashTodos(updated).all { it.completed })
        assertEquals(50L, trashTodos(updated).first().trashedAtMillis)
        assertNull(moveCompletedTodosToTrash(updated, DefaultChecklistId, trashedAtMillis = 51L))
    }

    @Test
    fun restoreTrashTodoReturnsToOriginalListAndKeepsCompletedState() {
        val work = createTodoChecklist(
            state = createInitialChecklistState(emptyList(), createdAtMillis = 1L),
            id = "work",
            nameInput = "Work",
            createdAtMillis = 2L,
        )!!
        val withWorkTodo = updateChecklistItems(
            state = work,
            checklistId = "work",
            items = listOf(item("done", TodoPriority.HIGH, due = 3L, completed = true)),
        )!!
        val trashed = moveTodoItemToTrash(
            state = withWorkTodo,
            checklistId = "work",
            todoId = "done",
            trashedAtMillis = 4L,
        )!!

        val restored = restoreTodoFromTrash(
            state = trashed,
            todoId = "done",
            restoredAtMillis = 5L,
        )!!

        assertEquals(emptyList<TodoItem>(), trashTodos(restored))
        val restoredItem = restored.lists.first { it.id == "work" }.items.single()
        assertEquals("done", restoredItem.id)
        assertTrue(restoredItem.completed)
        assertNull(restoredItem.trashedFromChecklistId)
        assertNull(restoredItem.trashedFromChecklistName)
        assertNull(restoredItem.trashedAtMillis)
    }

    @Test
    fun restoreTrashTodoRecreatesOriginalListWhenOriginalListIsGone() {
        val trashedItem = item(
            id = "old",
            priority = TodoPriority.MEDIUM,
            due = 1L,
            completed = true,
            trashedFromChecklistId = "deleted",
            trashedFromChecklistName = "Deleted",
            trashedAtMillis = 2L,
        )
        val state = TodoChecklistState(
            lists = listOf(
                TodoChecklist(
                    id = "other",
                    name = "Other",
                    items = emptyList(),
                    createdAtMillis = 1L,
                ),
                TodoChecklist(
                    id = TrashChecklistId,
                    name = TrashChecklistName,
                    items = listOf(trashedItem),
                    createdAtMillis = 1L,
                ),
            ),
            selectedListId = TrashChecklistId,
        )

        val restored = restoreTodoFromTrash(state, "old", restoredAtMillis = 9L)!!

        assertEquals(listOf("other", "deleted", TrashChecklistId), restored.lists.map { it.id })
        val recreatedList = restored.lists.first { it.id == "deleted" }
        assertEquals("Deleted", recreatedList.name)
        assertEquals(9L, recreatedList.createdAtMillis)
        val restoredItem = recreatedList.items.single()
        assertEquals("old", restoredItem.id)
        assertTrue(restoredItem.completed)
        assertEquals(emptyList<TodoItem>(), trashTodos(restored))
    }

    @Test
    fun restoreTrashTodoFallsBackToExistingNormalListWhenOriginalMetadataIsMissing() {
        val trashedItem = item(
            id = "old",
            priority = TodoPriority.MEDIUM,
            due = 1L,
            completed = true,
            trashedAtMillis = 2L,
        )
        val state = TodoChecklistState(
            lists = listOf(
                TodoChecklist(
                    id = "other",
                    name = "Other",
                    items = emptyList(),
                    createdAtMillis = 1L,
                ),
                TodoChecklist(
                    id = TrashChecklistId,
                    name = TrashChecklistName,
                    items = listOf(trashedItem),
                    createdAtMillis = 1L,
                ),
            ),
            selectedListId = TrashChecklistId,
        )

        val restored = restoreTodoFromTrash(state, "old", restoredAtMillis = 9L)!!

        assertEquals(listOf("other", TrashChecklistId), restored.lists.map { it.id })
        val restoredItem = restored.lists.first { it.id == "other" }.items.single()
        assertEquals("old", restoredItem.id)
        assertTrue(restoredItem.completed)
        assertEquals(emptyList<TodoItem>(), trashTodos(restored))
    }

    @Test
    fun deleteAllTrashTodosClearsOnlyTrashItems() {
        val main = item("main", TodoPriority.HIGH, due = 1L)
        val trashed = item(
            id = "trash",
            priority = TodoPriority.LOW,
            due = 2L,
            trashedFromChecklistId = DefaultChecklistId,
            trashedFromChecklistName = DefaultChecklistName,
            trashedAtMillis = 3L,
        )
        val state = TodoChecklistState(
            lists = listOf(
                TodoChecklist(DefaultChecklistId, DefaultChecklistName, listOf(main), 1L),
                TodoChecklist(TrashChecklistId, TrashChecklistName, listOf(trashed), 1L),
            ),
            selectedListId = TrashChecklistId,
        )

        val updated = deleteAllTrashTodos(state)

        assertEquals(listOf(main), updated.lists.first { it.id == DefaultChecklistId }.items)
        assertEquals(emptyList<TodoItem>(), trashTodos(updated))
    }

    @Test
    fun checklistJsonCodecRoundTripsNestedState() {
        val state = TodoChecklistState(
            lists = listOf(
                TodoChecklist(
                    id = DefaultChecklistId,
                    name = DefaultChecklistName,
                    items = listOf(item("one", TodoPriority.HIGH, due = 1L)),
                    createdAtMillis = 1L,
                ),
                TodoChecklist(
                    id = "work",
                    name = "Work \"Escaped\"",
                    items = listOf(item("two", TodoPriority.LOW, due = 2L, completed = true)),
                    createdAtMillis = 2L,
                ),
                TodoChecklist(
                    id = TrashChecklistId,
                    name = TrashChecklistName,
                    items = listOf(
                        item(
                            id = "trash",
                            priority = TodoPriority.MEDIUM,
                            due = 3L,
                            completed = true,
                            imageFileName = "trash.img",
                            trashedFromChecklistId = "work",
                            trashedFromChecklistName = "Work \"Escaped\"",
                            trashedAtMillis = 4L,
                        ),
                    ),
                    createdAtMillis = 3L,
                ),
            ),
            selectedListId = "work",
        )

        val decoded = TodoJsonCodec.decodeState(
            json = TodoJsonCodec.encodeState(state),
            fallbackCreatedAtMillis = 99L,
        )

        assertEquals(state, decoded)
    }

    @Test
    fun defaultDueAtMillisUsesSameTimeTomorrowRoundedToMinute() {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.of(2026, 6, 25, 9, 30, 42, 123_000_000)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        val expected = LocalDateTime.of(2026, 6, 26, 9, 30)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

        assertEquals(expected, defaultDueAtMillis(now))
    }

    @Test
    fun formatDeadlineCountdownShowsRemainingDaysHoursAndMinutes() {
        val now = 1_000_000L
        val due = now + (3L * 24L * 60L * 60L * 1000L) +
            (4L * 60L * 60L * 1000L) +
            (5L * 60L * 1000L)

        assertEquals("DDL 3D 04H 05M", formatDeadlineCountdown(due, now))
    }

    @Test
    fun formatDeadlineCountdownMarksOverdueItems() {
        val now = 200_000_000L
        val due = now - (1L * 24L * 60L * 60L * 1000L) -
            (2L * 60L * 60L * 1000L) -
            (3L * 60L * 1000L)

        assertEquals("DDL OVERDUE 1D 02H 03M", formatDeadlineCountdown(due, now))
    }

    @Test
    fun dueExpirationTreatsExactDueTimeAsExpired() {
        assertFalse(isDueExpired(dueAtMillis = 1_000L, nowMillis = 999L))
        assertTrue(isDueExpired(dueAtMillis = 1_000L, nowMillis = 1_000L))
        assertTrue(isDueExpired(dueAtMillis = 1_000L, nowMillis = 1_001L))
        assertFalse(isDueExpired(dueAtMillis = 0L, nowMillis = 1_000L))
    }

    @Test
    fun todoListClockRefreshSkipsWhenNoDeadlineCanChangeDisplay() {
        assertNull(
            nextTodoListClockRefreshDelayMillis(
                nowMillis = 1_000L,
                dueAtMillis = emptyList(),
                showDeadlineCountdown = false,
            ),
        )
        assertNull(
            nextTodoListClockRefreshDelayMillis(
                nowMillis = 1_000L,
                dueAtMillis = listOf(0L, 999L),
                showDeadlineCountdown = false,
            ),
        )
    }

    @Test
    fun todoListClockRefreshUsesNearestFutureDeadlineWhenCountdownIsHidden() {
        assertEquals(
            2_000L,
            nextTodoListClockRefreshDelayMillis(
                nowMillis = 1_000L,
                dueAtMillis = listOf(8_000L, 3_000L),
                showDeadlineCountdown = false,
            ),
        )
    }

    @Test
    fun todoListClockRefreshUsesMinuteBoundaryWhenCountdownIsVisible() {
        assertEquals(
            59_000L,
            nextTodoListClockRefreshDelayMillis(
                nowMillis = 61_000L,
                dueAtMillis = listOf(1_000L),
                showDeadlineCountdown = true,
            ),
        )
        assertEquals(
            60_000L,
            nextTodoListClockRefreshDelayMillis(
                nowMillis = 120_000L,
                dueAtMillis = listOf(1_000L),
                showDeadlineCountdown = true,
            ),
        )
    }

    @Test
    fun todoListClockRefreshPrioritizesDeadlineBeforeMinuteBoundary() {
        assertEquals(
            2_000L,
            nextTodoListClockRefreshDelayMillis(
                nowMillis = 61_000L,
                dueAtMillis = listOf(63_000L),
                showDeadlineCountdown = true,
            ),
        )
    }

    @Test
    fun completionSortDelayIsTwoSeconds() {
        assertEquals(2_000L, CompletionSortDelayMillis)
    }

    @Test
    fun firstRevealTargetIndexChoosesHighestVisibleTarget() {
        val visibleIds = listOf("one", "two", "three", "four")

        assertEquals(1, firstRevealTargetIndex(visibleIds, setOf("four", "two")))
    }

    @Test
    fun firstRevealTargetIndexReturnsNullWhenTargetsAreMissing() {
        val visibleIds = listOf("one", "two", "three")

        assertNull(firstRevealTargetIndex(visibleIds, setOf("missing", "other")))
        assertNull(firstRevealTargetIndex(visibleIds, emptySet()))
    }

    @Test
    fun todoRowClickActionEditsDifferentTodoAndCancelsCurrentTodo() {
        assertEquals(TodoRowClickAction.Edit, todoRowClickAction(itemId = "one", editingTaskId = null))
        assertEquals(TodoRowClickAction.Edit, todoRowClickAction(itemId = "one", editingTaskId = "two"))
        assertEquals(TodoRowClickAction.CancelEdit, todoRowClickAction(itemId = "one", editingTaskId = "one"))
    }

    @Test
    fun todoToggleFeedbackRecordsCompletedTodosWithoutHighlight() {
        val feedback = PendingTodoToggleFeedback()
            .recordTodoToggle(id = "done", wasCompleted = false, checked = true)

        assertEquals(setOf("done"), feedback.completedIds)
        assertEquals(emptySet<String>(), feedback.undoneIds)
        assertEquals(emptySet<String>(), feedback.highlightIds)
    }

    @Test
    fun todoToggleFeedbackRecordsUndoneTodosOnly() {
        val feedback = PendingTodoToggleFeedback()
            .recordTodoToggle(id = "undone", wasCompleted = true, checked = false)

        assertEquals(emptySet<String>(), feedback.completedIds)
        assertEquals(setOf("undone"), feedback.undoneIds)
        assertEquals(setOf("undone"), feedback.highlightIds)
    }

    @Test
    fun todoToggleFeedbackHighlightsUndoneTodosOnly() {
        val feedback = PendingTodoToggleFeedback()
            .recordTodoToggle(id = "done", wasCompleted = false, checked = true)
            .recordTodoToggle(id = "undone", wasCompleted = true, checked = false)

        assertEquals(setOf("done"), feedback.completedIds)
        assertEquals(setOf("undone"), feedback.undoneIds)
        assertEquals(setOf("undone"), feedback.highlightIds)
    }

    @Test
    fun todoToggleFeedbackUsesLatestStateForRepeatedTodo() {
        val feedback = PendingTodoToggleFeedback()
            .recordTodoToggle(id = "task", wasCompleted = true, checked = true)
            .recordTodoToggle(id = "task", wasCompleted = true, checked = false)
            .recordTodoToggle(id = "task", wasCompleted = false, checked = true)

        assertEquals(emptySet<String>(), feedback.completedIds)
        assertEquals(emptySet<String>(), feedback.undoneIds)
        assertEquals(emptySet<String>(), feedback.highlightIds)
    }

    @Test
    fun todoToggleFeedbackIgnoresIncompleteTaskThatReturnsToInitialState() {
        val feedback = PendingTodoToggleFeedback()
            .recordTodoToggle(id = "task", wasCompleted = false, checked = true)
            .recordTodoToggle(id = "task", wasCompleted = true, checked = false)

        assertEquals(emptySet<String>(), feedback.completedIds)
        assertEquals(emptySet<String>(), feedback.undoneIds)
        assertEquals(emptySet<String>(), feedback.highlightIds)
    }

    @Test
    fun todoToggleFeedbackIgnoresCompletedTaskThatReturnsToInitialState() {
        val feedback = PendingTodoToggleFeedback()
            .recordTodoToggle(id = "task", wasCompleted = true, checked = false)
            .recordTodoToggle(id = "task", wasCompleted = false, checked = true)

        assertEquals(emptySet<String>(), feedback.completedIds)
        assertEquals(emptySet<String>(), feedback.undoneIds)
        assertEquals(emptySet<String>(), feedback.highlightIds)
    }

    private fun item(
        id: String,
        priority: TodoPriority,
        due: Long,
        completed: Boolean = false,
        created: Long = due,
        title: String = id,
        repeat: ReminderRepeat = ReminderRepeat.NONE,
        imageFileName: String? = null,
        trashedFromChecklistId: String? = null,
        trashedFromChecklistName: String? = null,
        trashedAtMillis: Long? = null,
    ): TodoItem {
        return TodoItem(
            id = id,
            title = title,
            priority = priority,
            dueAtMillis = due,
            completed = completed,
            createdAtMillis = created,
            reminderRepeat = repeat,
            imageFileName = imageFileName,
            trashedFromChecklistId = trashedFromChecklistId,
            trashedFromChecklistName = trashedFromChecklistName,
            trashedAtMillis = trashedAtMillis,
        )
    }
}
