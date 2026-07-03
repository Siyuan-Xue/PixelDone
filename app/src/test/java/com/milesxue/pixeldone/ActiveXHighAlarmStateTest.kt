package com.milesxue.pixeldone

import com.milesxue.pixeldone.domain.todo.ReminderRepeat
import com.milesxue.pixeldone.domain.todo.TodoItem
import com.milesxue.pixeldone.domain.todo.TodoPriority
import com.milesxue.pixeldone.reminder.ActiveXHighAlarm
import com.milesxue.pixeldone.reminder.ActiveXHighAlarmCodec
import com.milesxue.pixeldone.reminder.ActiveXHighAlarmMaxAgeMillis
import com.milesxue.pixeldone.reminder.activeXHighAlarmFrom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveXHighAlarmStateTest {
    @Test
    fun activeAlarmCodecRoundTripsDisplayAndCompanionItems() {
        val alarm = ActiveXHighAlarm(
            todoIds = listOf("first", "second"),
            todoTitles = listOf("Take meds\nnow", "Call \"home\""),
            firedDueAtMillis = 2_000L,
            companionTodoIds = listOf("high"),
            companionTodoTitles = listOf("Companion / high"),
            companionTodoPriorities = listOf(TodoPriority.HIGH.name),
            companionTodoRepeats = listOf(ReminderRepeat.DAILY.name),
            updatedAtMillis = 2_100L,
        )

        val decoded = ActiveXHighAlarmCodec.decode(ActiveXHighAlarmCodec.encode(alarm))

        assertEquals(alarm, decoded)
        assertEquals("Take meds\nnow", decoded?.primaryTitle())
        assertEquals(
            listOf(
                TodoItem(
                    id = "high",
                    title = "Companion / high",
                    priority = TodoPriority.HIGH,
                    dueAtMillis = 2_000L,
                    completed = false,
                    createdAtMillis = 0L,
                    reminderRepeat = ReminderRepeat.DAILY,
                ),
            ),
            decoded?.companionShortItems(),
        )
    }

    @Test
    fun activeAlarmCodecRejectsInvalidOrEmptyPayloads() {
        assertNull(ActiveXHighAlarmCodec.decode(""))
        assertNull(ActiveXHighAlarmCodec.decode("1\n0\n1\nid\ntitle\n\n\n\n"))
        assertNull(
            ActiveXHighAlarmCodec.decode(
                ActiveXHighAlarmCodec.encode(
                    ActiveXHighAlarm(
                        todoIds = emptyList(),
                        todoTitles = emptyList(),
                        firedDueAtMillis = 2_000L,
                        updatedAtMillis = 2_100L,
                    ),
                ),
            ),
        )
    }

    @Test
    fun activeAlarmFromNormalizesBlankTitlesAndTracksStaleness() {
        val alarm = activeXHighAlarmFrom(
            items = listOf(item(id = "xhigh", title = "")),
            firedDueAtMillis = 2_000L,
            companionShortItems = emptyList(),
            updatedAtMillis = 3_000L,
        )

        assertEquals("Todo due", alarm?.primaryTitle())
        assertFalse(alarm?.isStale(3_000L + ActiveXHighAlarmMaxAgeMillis) ?: true)
        assertTrue(alarm?.isStale(3_001L + ActiveXHighAlarmMaxAgeMillis) ?: false)
    }

    private fun item(
        id: String,
        title: String,
        priority: TodoPriority = TodoPriority.XHIGH,
        repeat: ReminderRepeat = ReminderRepeat.NONE,
    ): TodoItem {
        return TodoItem(
            id = id,
            title = title,
            priority = priority,
            dueAtMillis = 2_000L,
            completed = false,
            createdAtMillis = 1L,
            reminderRepeat = repeat,
        )
    }
}
