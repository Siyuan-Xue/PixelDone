package com.milesxue.pixeldone.domain.todo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderRecurrenceRulesTest {
    @Test
    fun futureRepeatingReminderKeepsItsPersistedOccurrenceAndTriggerTime() {
        val dueAtMillis = 10_000L

        assertEquals(
            ReminderAlarmTarget(
                triggerAtMillis = dueAtMillis,
                occurrenceAtMillis = dueAtMillis,
            ),
            reminderAlarmTarget(
                item = item(
                    id = "future-daily",
                    dueAtMillis = dueAtMillis,
                    reminderRepeat = ReminderRepeat.DAILY,
                ),
                nowMillis = 9_000L,
            ),
        )
    }

    @Test
    fun overdueDailyReminderTargetsLatestOccurrenceAndTriggersImmediately() {
        val dueAtMillis = 10_000L
        val nowMillis = dueAtMillis + (2L * DailyReminderIntervalMillis) + 321L

        assertEquals(
            ReminderAlarmTarget(
                triggerAtMillis = nowMillis,
                occurrenceAtMillis = dueAtMillis + (2L * DailyReminderIntervalMillis),
            ),
            reminderAlarmTarget(
                item = item(
                    id = "overdue-daily",
                    dueAtMillis = dueAtMillis,
                    reminderRepeat = ReminderRepeat.DAILY,
                ),
                nowMillis = nowMillis,
            ),
        )
    }

    @Test
    fun overdueWeeklyReminderTargetsLatestOccurrenceAndTriggersImmediately() {
        val dueAtMillis = 20_000L
        val nowMillis = dueAtMillis + WeeklyReminderIntervalMillis + 999L

        assertEquals(
            ReminderAlarmTarget(
                triggerAtMillis = nowMillis,
                occurrenceAtMillis = dueAtMillis + WeeklyReminderIntervalMillis,
            ),
            reminderAlarmTarget(
                item = item(
                    id = "overdue-weekly",
                    dueAtMillis = dueAtMillis,
                    reminderRepeat = ReminderRepeat.WEEKLY,
                ),
                nowMillis = nowMillis,
            ),
        )
    }

    @Test
    fun overdueOneShotReminderIsNotRecreatedByRecovery() {
        assertNull(
            reminderAlarmTarget(
                item = item(
                    id = "overdue-one-shot",
                    dueAtMillis = 10_000L,
                    reminderRepeat = ReminderRepeat.NONE,
                ),
                nowMillis = 10_001L,
            ),
        )
    }

    @Test
    fun repeatingDispatchAcceptsOneCrossDeviceOccurrenceBehind() {
        val persistedDueAtMillis = 10_000L + DailyReminderIntervalMillis
        val item = item(
            id = "cross-device-daily",
            dueAtMillis = persistedDueAtMillis,
            reminderRepeat = ReminderRepeat.DAILY,
        )

        assertTrue(
            shouldDispatchTodoReminder(
                item = item,
                firedDueAtMillis = persistedDueAtMillis - DailyReminderIntervalMillis,
            ),
        )
    }

    @Test
    fun repeatingDispatchRejectsTwoCrossDeviceOccurrencesBehind() {
        val persistedDueAtMillis = 10_000L + (2L * WeeklyReminderIntervalMillis)
        val item = item(
            id = "stale-cross-device-weekly",
            dueAtMillis = persistedDueAtMillis,
            reminderRepeat = ReminderRepeat.WEEKLY,
        )

        assertFalse(
            shouldDispatchTodoReminder(
                item = item,
                firedDueAtMillis = persistedDueAtMillis - (2L * WeeklyReminderIntervalMillis),
            ),
        )
    }

    @Test
    fun crossDeviceToleranceAppliesOnlyToTheAlarmTriggerNotFutureCompanions() {
        val firedDueAtMillis = 10_000L
        val trigger = item(
            id = "synced-trigger",
            dueAtMillis = firedDueAtMillis + DailyReminderIntervalMillis,
            reminderRepeat = ReminderRepeat.DAILY,
        )
        val futureCompanion = item(
            id = "future-companion",
            dueAtMillis = firedDueAtMillis + DailyReminderIntervalMillis,
            reminderRepeat = ReminderRepeat.DAILY,
        )

        val plan = reminderDispatchPlan(
            items = listOf(trigger, futureCompanion),
            firedDueAtMillis = firedDueAtMillis,
            triggerTodoId = trigger.id,
            triggerAlertMode = ReminderAlertMode.SHORT_NOTIFICATION,
            canScheduleExactAlarms = true,
        )

        assertEquals(listOf(trigger.id), plan.shortNotificationItems.map { it.id })
    }

    @Test
    fun oneShotDispatchNeverAcceptsAnOlderOccurrence() {
        assertFalse(
            shouldDispatchTodoReminder(
                item = item(
                    id = "edited-one-shot",
                    dueAtMillis = 20_000L,
                    reminderRepeat = ReminderRepeat.NONE,
                ),
                firedDueAtMillis = 10_000L,
            ),
        )
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
