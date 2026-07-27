package com.milesxue.pixeldone.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.milesxue.pixeldone.MainActivity
import com.milesxue.pixeldone.domain.todo.ReminderAlertMode
import com.milesxue.pixeldone.domain.todo.ReminderCapability
import com.milesxue.pixeldone.domain.todo.ReminderRepeat
import com.milesxue.pixeldone.domain.todo.ReminderScheduleMode
import com.milesxue.pixeldone.domain.todo.TodoItem
import com.milesxue.pixeldone.domain.todo.TodoPriority
import com.milesxue.pixeldone.domain.todo.canScheduleExactAlarmForSdk
import com.milesxue.pixeldone.domain.todo.effectiveReminderAlertMode
import com.milesxue.pixeldone.domain.todo.effectiveReminderScheduleMode
import com.milesxue.pixeldone.domain.todo.reminderAlarmTarget
import com.milesxue.pixeldone.domain.todo.reminderScheduleMode
import com.milesxue.pixeldone.domain.todo.shouldCancelUnschedulableTodoAlarm
import com.milesxue.pixeldone.domain.todo.shouldScheduleTodoAlarm

internal data class ReminderSyncPlan(
    val scheduleItems: List<TodoItem>,
    val cancelItemIds: Set<String>,
)

internal fun reminderSyncPlan(
    previousItems: List<TodoItem>,
    currentItems: List<TodoItem>,
    nowMillis: Long,
): ReminderSyncPlan {
    val currentIds = currentItems.mapTo(mutableSetOf()) { it.id }
    val previousById = previousItems.associateBy { it.id }
    val cancelItemIds = previousItems
        .asSequence()
        .filterNot { it.id in currentIds }
        .mapTo(mutableSetOf()) { it.id }
    val scheduleItems = mutableListOf<TodoItem>()

    currentItems.forEach { item ->
        val previousItem = previousById[item.id]
        if (previousItem == item) return@forEach

        if (shouldScheduleTodoAlarm(item, nowMillis)) {
            scheduleItems += item
        } else if (shouldCancelUnschedulableTodoAlarm(previousItem, item, nowMillis)) {
            cancelItemIds += item.id
        }
    }

    return ReminderSyncPlan(
        scheduleItems = scheduleItems,
        cancelItemIds = cancelItemIds,
    )
}

/**
 * Android AlarmManager adapter.
 *
 * Domain reminder rules decide when and how a reminder should fire. This adapter translates those
 * decisions into PendingIntent, AlarmClock, or inexact alarm calls at the Android boundary.
 */
object TodoAlarmScheduler {
    const val EXTRA_TODO_ID = "com.milesxue.pixeldone.extra.TODO_ID"
    const val EXTRA_TODO_TITLE = "com.milesxue.pixeldone.extra.TODO_TITLE"
    const val EXTRA_TODO_PRIORITY = "com.milesxue.pixeldone.extra.TODO_PRIORITY"
    const val EXTRA_TODO_DUE_AT = "com.milesxue.pixeldone.extra.TODO_DUE_AT"
    const val EXTRA_TODO_REPEAT = "com.milesxue.pixeldone.extra.TODO_REPEAT"
    const val EXTRA_ALERT_MODE = "com.milesxue.pixeldone.extra.ALERT_MODE"
    const val EXTRA_REMINDER_OCCURRENCE_AT =
        "com.milesxue.pixeldone.extra.REMINDER_OCCURRENCE_AT"

    fun sync(
        context: Context,
        previousItems: List<TodoItem>,
        currentItems: List<TodoItem>,
    ): Set<ReminderCapability> {
        val nowMillis = System.currentTimeMillis()
        val syncPlan = reminderSyncPlan(
            previousItems = previousItems,
            currentItems = currentItems,
            nowMillis = nowMillis,
        )
        syncPlan.cancelItemIds.forEach { itemId -> cancel(context, itemId) }

        val missingCapabilities = mutableSetOf<ReminderCapability>()
        syncPlan.scheduleItems.forEach { item ->
            missingCapabilities += schedule(context, item, nowMillis)
        }
        return missingCapabilities
    }

    fun rescheduleAll(
        context: Context,
        items: List<TodoItem>,
        nowMillis: Long = System.currentTimeMillis(),
    ): Set<ReminderCapability> {
        val missingCapabilities = mutableSetOf<ReminderCapability>()
        items.forEach { item ->
            missingCapabilities += schedule(context, item, nowMillis)
        }
        return missingCapabilities
    }

    fun reconcileOverdue(
        context: Context,
        items: List<TodoItem>,
        nowMillis: Long = System.currentTimeMillis(),
    ): Set<ReminderCapability> {
        val missingCapabilities = mutableSetOf<ReminderCapability>()
        items
            .filter { item ->
                !item.completed &&
                    item.reminderRepeat != ReminderRepeat.NONE &&
                    item.dueAtMillis in 1L..nowMillis
            }
            .forEach { item ->
                missingCapabilities += schedule(context, item, nowMillis)
            }
        return missingCapabilities
    }

    fun schedule(
        context: Context,
        item: TodoItem,
        nowMillis: Long = System.currentTimeMillis(),
    ): Set<ReminderCapability> {
        val target = reminderAlarmTarget(item, nowMillis) ?: run {
            cancel(context, item.id)
            return emptySet()
        }
        // The data-URI PendingIntent is the stable alarm identity. Scheduling it again replaces
        // the existing alarm atomically; only the pre-data-URI legacy identity needs cancellation.
        cancelLegacyAlarm(context, item.id)
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val hasExactAlarmAccess = canScheduleExactAlarms(context)
        val scheduleMode = effectiveReminderScheduleMode(
            item = item,
            nowMillis = nowMillis,
            canScheduleExactAlarms = hasExactAlarmAccess,
        )
        val alertMode = effectiveReminderAlertMode(
            item = item,
            nowMillis = nowMillis,
            canScheduleExactAlarms = hasExactAlarmAccess,
        )
        if (scheduleMode == null || alertMode == null) {
            cancel(context, item.id)
            return emptySet()
        }

        val alarmIntent = pendingIntent(
            context = context,
            itemId = item.id,
            title = item.title,
            priority = item.priority,
            occurrenceAtMillis = target.occurrenceAtMillis,
            reminderRepeat = item.reminderRepeat,
            alertMode = alertMode,
            flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            includeIdentityData = true,
        ) ?: return emptySet()

        val missingCapabilities = mutableSetOf<ReminderCapability>()
        if (
            reminderScheduleMode(item, nowMillis) in setOf(
                ReminderScheduleMode.SYSTEM_ALARM,
                ReminderScheduleMode.EXACT_NOTIFICATION,
            ) &&
            !hasExactAlarmAccess
        ) {
            missingCapabilities += ReminderCapability.EXACT_ALARM_ACCESS
        }

        return when (scheduleMode) {
            ReminderScheduleMode.SYSTEM_ALARM -> {
                if (!hasExactAlarmAccess) {
                    cancel(context, item.id)
                    setOf(ReminderCapability.EXACT_ALARM_ACCESS)
                } else {
                    try {
                        alarmManager.setAlarmClock(
                            AlarmManager.AlarmClockInfo(
                                target.triggerAtMillis,
                                showIntent(context, item.id),
                            ),
                            alarmIntent,
                        )
                        missingCapabilities
                    } catch (_: SecurityException) {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            target.triggerAtMillis,
                            alarmIntent,
                        )
                        missingCapabilities + ReminderCapability.EXACT_ALARM_ACCESS
                    }
                }
            }
            ReminderScheduleMode.EXACT_NOTIFICATION -> {
                if (!hasExactAlarmAccess) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        target.triggerAtMillis,
                        alarmIntent,
                    )
                    setOf(ReminderCapability.EXACT_ALARM_ACCESS)
                } else {
                    try {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            target.triggerAtMillis,
                            alarmIntent,
                        )
                        missingCapabilities
                    } catch (_: SecurityException) {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            target.triggerAtMillis,
                            alarmIntent,
                        )
                        missingCapabilities + ReminderCapability.EXACT_ALARM_ACCESS
                    }
                }
            }
            ReminderScheduleMode.INEXACT_NOTIFICATION -> {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    target.triggerAtMillis,
                    alarmIntent,
                )
                missingCapabilities
            }
        }
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        return canScheduleExactAlarmForSdk(
            sdkInt = Build.VERSION.SDK_INT,
            canScheduleExactAlarms = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms(),
        )
    }

    fun cancel(context: Context, itemId: String) {
        listOfNotNull(
            pendingIntent(
                context = context,
                itemId = itemId,
                title = "",
                priority = TodoPriority.MEDIUM,
                occurrenceAtMillis = 0L,
                reminderRepeat = ReminderRepeat.NONE,
                alertMode = ReminderAlertMode.SHORT_NOTIFICATION,
                flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
                includeIdentityData = true,
            ),
            pendingIntent(
                context = context,
                itemId = itemId,
                title = "",
                priority = TodoPriority.MEDIUM,
                occurrenceAtMillis = 0L,
                reminderRepeat = ReminderRepeat.NONE,
                alertMode = ReminderAlertMode.SHORT_NOTIFICATION,
                flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
                includeIdentityData = false,
            ),
        ).forEach { pendingIntent ->
            context.getSystemService(AlarmManager::class.java).cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun cancelLegacyAlarm(context: Context, itemId: String) {
        pendingIntent(
            context = context,
            itemId = itemId,
            title = "",
            priority = TodoPriority.MEDIUM,
            occurrenceAtMillis = 0L,
            reminderRepeat = ReminderRepeat.NONE,
            alertMode = ReminderAlertMode.SHORT_NOTIFICATION,
            flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            includeIdentityData = false,
        )?.let { pendingIntent ->
            context.getSystemService(AlarmManager::class.java).cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun pendingIntent(
        context: Context,
        itemId: String,
        title: String,
        priority: TodoPriority,
        occurrenceAtMillis: Long,
        reminderRepeat: ReminderRepeat,
        alertMode: ReminderAlertMode,
        flags: Int,
        includeIdentityData: Boolean,
    ): PendingIntent? {
        val intent = Intent(context, TodoAlarmReceiver::class.java).apply {
            if (includeIdentityData) {
                data = Uri.parse("pixeldone://todo-alarm/${Uri.encode(itemId)}")
            }
            putExtra(EXTRA_TODO_ID, itemId)
            putExtra(EXTRA_TODO_TITLE, title)
            putExtra(EXTRA_TODO_PRIORITY, priority.name)
            putExtra(EXTRA_TODO_DUE_AT, occurrenceAtMillis)
            putExtra(EXTRA_REMINDER_OCCURRENCE_AT, occurrenceAtMillis)
            putExtra(EXTRA_TODO_REPEAT, reminderRepeat.name)
            putExtra(EXTRA_ALERT_MODE, alertMode.name)
        }
        return PendingIntent.getBroadcast(context, itemId.hashCode(), intent, flags)
    }

    private fun showIntent(context: Context, itemId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            itemId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
