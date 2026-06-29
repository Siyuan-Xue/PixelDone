package com.milesxue.pixeldone

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

class TodoAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!canPostNotifications(context)) return

        val title = intent.getStringExtra(TodoAlarmScheduler.EXTRA_TODO_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: "Todo due"
        val priority = intent.getStringExtra(TodoAlarmScheduler.EXTRA_TODO_PRIORITY)
            ?.lowercase()
            ?: "task"
        val dueAtMillis = intent.getLongExtra(TodoAlarmScheduler.EXTRA_TODO_DUE_AT, 0L)
        val reminderRepeat = intent.getStringExtra(TodoAlarmScheduler.EXTRA_TODO_REPEAT)
            ?.let { name -> ReminderRepeat.entries.firstOrNull { it.name == name } }
            ?: ReminderRepeat.NONE
        val todoId = intent.getStringExtra(TodoAlarmScheduler.EXTRA_TODO_ID) ?: title

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )

        val openAppIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText("${priority.uppercase()}  ${dueAtMillis.formatAlarmTime()}${reminderRepeat.notificationSuffix()}")
            .setContentIntent(openAppIntent)
            .setAutoCancel(true)
            .setShowWhen(true)
            .setWhen(dueAtMillis)
            .build()

        notificationManager.notify(todoId.hashCode(), notification)
        rescheduleRepeatingTodo(context, todoId, dueAtMillis)
    }

    private fun canPostNotifications(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun Long.formatAlarmTime(): String {
        return if (this > 0L) {
            java.time.Instant.ofEpochMilli(this)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDateTime()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        } else {
            "Now"
        }
    }

    private fun ReminderRepeat.notificationSuffix(): String {
        return when (this) {
            ReminderRepeat.NONE -> ""
            ReminderRepeat.DAILY -> "  DAILY"
            ReminderRepeat.WEEKLY -> "  WEEKLY"
        }
    }

    private fun rescheduleRepeatingTodo(
        context: Context,
        todoId: String,
        triggerAtMillis: Long,
    ) {
        val storage = TodoPreferences.create(context)
        val nowMillis = maxOf(System.currentTimeMillis(), triggerAtMillis)
        val updatedState = advanceRepeatingTodoAfterReminder(
            state = storage.loadTodoState(),
            todoId = todoId,
            nowMillis = nowMillis,
        ) ?: return
        storage.saveTodoState(updatedState)

        val updatedItem = normalTodos(updatedState).firstOrNull { it.id == todoId } ?: return
        TodoAlarmScheduler.schedule(
            context = context,
            item = updatedItem,
            nowMillis = nowMillis,
        )
    }

    companion object {
        private const val CHANNEL_ID = "todo_alarms"
        private const val CHANNEL_NAME = "Todo alarms"
    }
}
