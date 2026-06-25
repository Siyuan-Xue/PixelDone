package com.codexue.pixeldone

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object TodoAlarmScheduler {
    const val EXTRA_TODO_ID = "com.codexue.pixeldone.extra.TODO_ID"
    const val EXTRA_TODO_TITLE = "com.codexue.pixeldone.extra.TODO_TITLE"
    const val EXTRA_TODO_PRIORITY = "com.codexue.pixeldone.extra.TODO_PRIORITY"
    const val EXTRA_TODO_DUE_AT = "com.codexue.pixeldone.extra.TODO_DUE_AT"

    fun sync(context: Context, previousItems: List<TodoItem>, currentItems: List<TodoItem>) {
        val currentIds = currentItems.map { it.id }.toSet()
        previousItems
            .filterNot { it.id in currentIds }
            .forEach { cancel(context, it.id) }

        val nowMillis = System.currentTimeMillis()
        currentItems.forEach { item ->
            if (shouldScheduleTodoAlarm(item, nowMillis)) {
                schedule(context, item)
            } else {
                cancel(context, item.id)
            }
        }
    }

    fun schedule(context: Context, item: TodoItem) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val alarmIntent = pendingIntent(
            context = context,
            itemId = item.id,
            title = item.title,
            priority = item.priority,
            dueAtMillis = item.dueAtMillis,
            flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            item.dueAtMillis,
            alarmIntent,
        )
    }

    fun cancel(context: Context, itemId: String) {
        val pendingIntent = pendingIntent(
            context = context,
            itemId = itemId,
            title = "",
            priority = TodoPriority.MEDIUM,
            dueAtMillis = 0L,
            flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return

        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun pendingIntent(
        context: Context,
        itemId: String,
        title: String,
        priority: TodoPriority,
        dueAtMillis: Long,
        flags: Int,
    ): PendingIntent? {
        val intent = Intent(context, TodoAlarmReceiver::class.java).apply {
            putExtra(EXTRA_TODO_ID, itemId)
            putExtra(EXTRA_TODO_TITLE, title)
            putExtra(EXTRA_TODO_PRIORITY, priority.name)
            putExtra(EXTRA_TODO_DUE_AT, dueAtMillis)
        }
        return PendingIntent.getBroadcast(context, itemId.hashCode(), intent, flags)
    }
}
