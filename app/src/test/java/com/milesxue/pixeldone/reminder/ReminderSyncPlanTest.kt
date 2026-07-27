package com.milesxue.pixeldone.reminder

import com.milesxue.pixeldone.domain.todo.ReminderRepeat
import com.milesxue.pixeldone.domain.todo.TodoItem
import com.milesxue.pixeldone.domain.todo.TodoPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderSyncPlanTest {
    @Test
    fun firstRecoveryAndExactPermissionChangesRescheduleEveryReminder() {
        assertEquals(
            ReminderRecoveryMode.RESCHEDULE_ALL,
            reminderRecoveryMode(
                previousExactAlarmAccess = null,
                currentExactAlarmAccess = true,
            ),
        )
        assertEquals(
            ReminderRecoveryMode.RESCHEDULE_ALL,
            reminderRecoveryMode(
                previousExactAlarmAccess = true,
                currentExactAlarmAccess = false,
            ),
        )
        assertEquals(
            ReminderRecoveryMode.RESCHEDULE_ALL,
            reminderRecoveryMode(
                previousExactAlarmAccess = false,
                currentExactAlarmAccess = true,
            ),
        )
    }

    @Test
    fun unchangedExactPermissionOnlyReconcilesOverdueRecurrences() {
        assertEquals(
            ReminderRecoveryMode.RECONCILE_OVERDUE,
            reminderRecoveryMode(
                previousExactAlarmAccess = true,
                currentExactAlarmAccess = true,
            ),
        )
        assertEquals(
            ReminderRecoveryMode.RECONCILE_OVERDUE,
            reminderRecoveryMode(
                previousExactAlarmAccess = false,
                currentExactAlarmAccess = false,
            ),
        )
    }

    @Test
    fun unchangedOverdueRecurrenceIsNotCancelledOrRescheduled() {
        val overdue = item(
            id = "unchanged-daily",
            dueAtMillis = 1_000L,
            reminderRepeat = ReminderRepeat.DAILY,
        )

        val plan = reminderSyncPlan(
            previousItems = listOf(overdue),
            currentItems = listOf(overdue),
            nowMillis = 2_000L,
        )

        assertTrue(plan.scheduleItems.isEmpty())
        assertTrue(plan.cancelItemIds.isEmpty())
    }

    @Test
    fun changingAnotherTodoDoesNotRescheduleUnchangedOverdueRecurrence() {
        val unchangedOverdue = item(
            id = "unchanged-daily",
            dueAtMillis = 1_000L,
            reminderRepeat = ReminderRepeat.DAILY,
        )
        val previousChangedTodo = item(
            id = "changed",
            dueAtMillis = 5_000L,
            reminderRepeat = ReminderRepeat.NONE,
        )
        val currentChangedTodo = previousChangedTodo.copy(title = "Updated title")

        val plan = reminderSyncPlan(
            previousItems = listOf(unchangedOverdue, previousChangedTodo),
            currentItems = listOf(unchangedOverdue, currentChangedTodo),
            nowMillis = 2_000L,
        )

        assertEquals(listOf("changed"), plan.scheduleItems.map { it.id })
        assertTrue(plan.cancelItemIds.isEmpty())
    }

    @Test
    fun newOverdueRecurrenceIsScheduledForImmediateReconciliation() {
        val overdue = item(
            id = "new-weekly",
            dueAtMillis = 1_000L,
            reminderRepeat = ReminderRepeat.WEEKLY,
        )

        val plan = reminderSyncPlan(
            previousItems = emptyList(),
            currentItems = listOf(overdue),
            nowMillis = 2_000L,
        )

        assertEquals(listOf(overdue), plan.scheduleItems)
        assertTrue(plan.cancelItemIds.isEmpty())
    }

    @Test
    fun removedTodoIsCancelledWithoutSchedulingOtherUnchangedTodos() {
        val removed = item(
            id = "removed",
            dueAtMillis = 5_000L,
            reminderRepeat = ReminderRepeat.NONE,
        )
        val unchanged = item(
            id = "unchanged",
            dueAtMillis = 1_000L,
            reminderRepeat = ReminderRepeat.DAILY,
        )

        val plan = reminderSyncPlan(
            previousItems = listOf(removed, unchanged),
            currentItems = listOf(unchanged),
            nowMillis = 2_000L,
        )

        assertTrue(plan.scheduleItems.isEmpty())
        assertEquals(setOf("removed"), plan.cancelItemIds)
    }

    @Test
    fun newlyCompletedTodoCancelsItsExistingAlarm() {
        val previous = item(
            id = "completed",
            dueAtMillis = 5_000L,
            reminderRepeat = ReminderRepeat.DAILY,
        )

        val plan = reminderSyncPlan(
            previousItems = listOf(previous),
            currentItems = listOf(previous.copy(completed = true)),
            nowMillis = 2_000L,
        )

        assertTrue(plan.scheduleItems.isEmpty())
        assertEquals(setOf("completed"), plan.cancelItemIds)
    }

    private fun item(
        id: String,
        dueAtMillis: Long,
        reminderRepeat: ReminderRepeat,
    ): TodoItem {
        return TodoItem(
            id = id,
            title = id,
            priority = TodoPriority.MEDIUM,
            dueAtMillis = dueAtMillis,
            completed = false,
            createdAtMillis = 1L,
            reminderRepeat = reminderRepeat,
        )
    }
}
