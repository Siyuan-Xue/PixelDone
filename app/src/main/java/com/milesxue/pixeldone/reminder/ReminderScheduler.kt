package com.milesxue.pixeldone.reminder

import android.content.Context
import com.milesxue.pixeldone.domain.todo.ReminderCapability
import com.milesxue.pixeldone.domain.todo.TodoItem

/**
 * 提醒调度边界。
 *
 * 教学说明：领域层只决定“该不该提醒、用什么提醒方式”，这里才真正接触 Android AlarmManager。
 */
interface ReminderScheduler {
    fun sync(previousItems: List<TodoItem>, currentItems: List<TodoItem>): Set<ReminderCapability>
    fun schedule(item: TodoItem): Set<ReminderCapability>
    fun canScheduleExactAlarms(): Boolean
    fun cancel(itemId: String)
}

class AndroidReminderScheduler(private val context: Context) : ReminderScheduler {
    override fun sync(
        previousItems: List<TodoItem>,
        currentItems: List<TodoItem>,
    ): Set<ReminderCapability> = TodoAlarmScheduler.sync(context, previousItems, currentItems)

    override fun schedule(item: TodoItem): Set<ReminderCapability> =
        TodoAlarmScheduler.schedule(context, item)

    override fun canScheduleExactAlarms(): Boolean =
        TodoAlarmScheduler.canScheduleExactAlarms(context)

    override fun cancel(itemId: String) {
        TodoAlarmScheduler.cancel(context, itemId)
    }
}
