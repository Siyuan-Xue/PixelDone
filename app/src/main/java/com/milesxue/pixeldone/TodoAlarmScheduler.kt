package com.milesxue.pixeldone

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

object TodoAlarmScheduler {
    const val EXTRA_TODO_ID = "com.milesxue.pixeldone.extra.TODO_ID"
    const val EXTRA_TODO_TITLE = "com.milesxue.pixeldone.extra.TODO_TITLE"
    const val EXTRA_TODO_PRIORITY = "com.milesxue.pixeldone.extra.TODO_PRIORITY"
    const val EXTRA_TODO_DUE_AT = "com.milesxue.pixeldone.extra.TODO_DUE_AT"
    const val EXTRA_TODO_REPEAT = "com.milesxue.pixeldone.extra.TODO_REPEAT"

    fun sync(
        context: Context,
        previousItems: List<TodoItem>,
        currentItems: List<TodoItem>,
    ): Set<ReminderCapability> {
        val currentIds = currentItems.map { it.id }.toSet()
        val previousById = previousItems.associateBy { it.id }
        previousItems
            .filterNot { it.id in currentIds }
            .forEach { cancel(context, it.id) }

        val nowMillis = System.currentTimeMillis()
        val missingCapabilities = mutableSetOf<ReminderCapability>()
        currentItems.forEach { item ->
            if (shouldScheduleTodoAlarm(item, nowMillis)) {
                missingCapabilities += schedule(context, item)
            } else if (shouldCancelUnschedulableTodoAlarm(previousById[item.id], item, nowMillis)) {
                cancel(context, item.id)
            }
        }
        return missingCapabilities
    }

    fun schedule(
        context: Context,
        item: TodoItem,
        nowMillis: Long = System.currentTimeMillis(),
    ): Set<ReminderCapability> {
        val triggerAtMillis = nextReminderAtMillis(item, nowMillis) ?: run {
            cancel(context, item.id)
            return emptySet()
        }
        cancel(context, item.id)
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val alarmIntent = pendingIntent(
            context = context,
            itemId = item.id,
            title = item.title,
            priority = item.priority,
            dueAtMillis = triggerAtMillis,
            reminderRepeat = item.reminderRepeat,
            flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            includeIdentityData = true,
        ) ?: return emptySet()

        return when (reminderScheduleMode(item, nowMillis)) {
            ReminderScheduleMode.SYSTEM_ALARM -> {
                if (!canScheduleExactAlarms(context)) {
                    cancel(context, item.id)
                    setOf(ReminderCapability.EXACT_ALARM_ACCESS)
                } else {
                    alarmManager.setAlarmClock(
                        AlarmManager.AlarmClockInfo(
                            triggerAtMillis,
                            showIntent(context, item.id),
                        ),
                        alarmIntent,
                    )
                    emptySet()
                }
            }
            ReminderScheduleMode.INEXACT_NOTIFICATION -> {
                if (!canScheduleExactAlarms(context)) {
                    cancel(context, item.id)
                    setOf(ReminderCapability.EXACT_ALARM_ACCESS)
                } else {
                    alarmManager.setAlarmClock(
                        AlarmManager.AlarmClockInfo(
                            triggerAtMillis,
                            showIntent(context, item.id),
                        ),
                        alarmIntent,
                    )
                    emptySet()
                }
            }
            null -> {
                cancel(context, item.id)
                emptySet()
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
                dueAtMillis = 0L,
                reminderRepeat = ReminderRepeat.NONE,
                flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
                includeIdentityData = true,
            ),
            pendingIntent(
                context = context,
                itemId = itemId,
                title = "",
                priority = TodoPriority.MEDIUM,
                dueAtMillis = 0L,
                reminderRepeat = ReminderRepeat.NONE,
                flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
                includeIdentityData = false,
            ),
        ).forEach { pendingIntent ->
            context.getSystemService(AlarmManager::class.java).cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun pendingIntent(
        context: Context,
        itemId: String,
        title: String,
        priority: TodoPriority,
        dueAtMillis: Long,
        reminderRepeat: ReminderRepeat,
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
            putExtra(EXTRA_TODO_DUE_AT, dueAtMillis)
            putExtra(EXTRA_TODO_REPEAT, reminderRepeat.name)
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
