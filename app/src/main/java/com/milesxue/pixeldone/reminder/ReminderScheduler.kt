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
    fun recover(items: List<TodoItem>): Set<ReminderCapability> = sync(emptyList(), items)
    fun rescheduleAll(items: List<TodoItem>): Set<ReminderCapability> = sync(emptyList(), items)
    fun reconcileOverdue(items: List<TodoItem>): Set<ReminderCapability> = emptySet()
    fun canScheduleExactAlarms(): Boolean
    fun cancel(itemId: String)
}

internal enum class ReminderRecoveryMode {
    RESCHEDULE_ALL,
    RECONCILE_OVERDUE,
}

internal fun reminderRecoveryMode(
    previousExactAlarmAccess: Boolean?,
    currentExactAlarmAccess: Boolean,
): ReminderRecoveryMode {
    return if (
        previousExactAlarmAccess == null ||
        previousExactAlarmAccess != currentExactAlarmAccess
    ) {
        ReminderRecoveryMode.RESCHEDULE_ALL
    } else {
        ReminderRecoveryMode.RECONCILE_OVERDUE
    }
}

class AndroidReminderScheduler(private val context: Context) : ReminderScheduler {
    private val recoveryLock = Any()
    private var recoveredExactAlarmAccess: Boolean? = null

    override fun sync(
        previousItems: List<TodoItem>,
        currentItems: List<TodoItem>,
    ): Set<ReminderCapability> = TodoAlarmScheduler.sync(context, previousItems, currentItems)

    override fun schedule(item: TodoItem): Set<ReminderCapability> =
        TodoAlarmScheduler.schedule(context, item)

    override fun recover(items: List<TodoItem>): Set<ReminderCapability> {
        return synchronized(recoveryLock) {
            val currentExactAlarmAccess = TodoAlarmScheduler.canScheduleExactAlarms(context)
            val previousExactAlarmAccess = recoveredExactAlarmAccess
            recoveredExactAlarmAccess = currentExactAlarmAccess
            when (
                reminderRecoveryMode(
                    previousExactAlarmAccess = previousExactAlarmAccess,
                    currentExactAlarmAccess = currentExactAlarmAccess,
                )
            ) {
                ReminderRecoveryMode.RESCHEDULE_ALL ->
                    TodoAlarmScheduler.rescheduleAll(context, items)
                ReminderRecoveryMode.RECONCILE_OVERDUE ->
                    TodoAlarmScheduler.reconcileOverdue(context, items)
            }
        }
    }

    override fun rescheduleAll(items: List<TodoItem>): Set<ReminderCapability> {
        return synchronized(recoveryLock) {
            recoveredExactAlarmAccess = TodoAlarmScheduler.canScheduleExactAlarms(context)
            TodoAlarmScheduler.rescheduleAll(context, items)
        }
    }

    override fun reconcileOverdue(items: List<TodoItem>): Set<ReminderCapability> =
        TodoAlarmScheduler.reconcileOverdue(context, items)

    override fun canScheduleExactAlarms(): Boolean =
        TodoAlarmScheduler.canScheduleExactAlarms(context)

    override fun cancel(itemId: String) {
        TodoAlarmScheduler.cancel(context, itemId)
    }
}
