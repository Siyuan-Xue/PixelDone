package com.milesxue.pixeldone.reminder

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import com.milesxue.pixeldone.MainActivity
import com.milesxue.pixeldone.R
import com.milesxue.pixeldone.domain.todo.ReminderNotificationIds
import com.milesxue.pixeldone.domain.todo.ReminderRepeat
import com.milesxue.pixeldone.domain.todo.TodoItem
import com.milesxue.pixeldone.domain.todo.TodoPriority

object TodoReminderNotifier {
    const val SHORT_CHANNEL_ID = "todo_due_short_v1"
    const val XHIGH_CHANNEL_ID = "xhigh_alarm_fullscreen_v1"
    const val XHIGH_NOTIFICATION_ID = ReminderNotificationIds.XHIGH_ALARM

    private const val SHORT_GROUP_KEY = "pixeldone.reminder.short"
    private const val XHIGH_GROUP_KEY = "pixeldone.reminder.xhigh"
    private val ShortVibrationPattern = longArrayOf(0L, 140L, 90L, 180L)
    val XHighVibrationPattern = longArrayOf(0L, 600L, 220L, 600L, 300L, 900L)

    fun showShortNotification(
        context: Context,
        item: TodoItem,
        firedDueAtMillis: Long,
    ) {
        showShortNotificationBatch(context, listOf(item), firedDueAtMillis)
    }

    fun showShortNotificationBatch(
        context: Context,
        items: List<TodoItem>,
        firedDueAtMillis: Long,
        replaceExisting: Boolean = false,
    ) {
        if (!canPostNotifications(context)) return
        val dueItems = items.takeIf { it.isNotEmpty() } ?: return
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(shortNotificationChannel(context))

        if (dueItems.size == 1) {
            val item = dueItems.single()
            val notificationId = ReminderNotificationIds.shortItem(item.id)
            if (replaceExisting) {
                notificationManager.cancel(notificationId)
            }
            notificationManager.notify(
                notificationId,
                Notification.Builder(context, SHORT_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(item.title)
                    .setContentText(item.notificationContentText(context, firedDueAtMillis))
                    .setContentIntent(openAppIntent(context, notificationId))
                    .setAutoCancel(true)
                    .setShowWhen(true)
                    .setWhen(firedDueAtMillis)
                    .setCategory(Notification.CATEGORY_REMINDER)
                    .setGroup(SHORT_GROUP_KEY)
                    .setGroupAlertBehavior(Notification.GROUP_ALERT_ALL)
                    .setOnlyAlertOnce(false)
                    .build(),
            )
            return
        }

        val notificationId = ReminderNotificationIds.shortBatch(firedDueAtMillis)
        if (replaceExisting) {
            notificationManager.cancel(notificationId)
        }
        val batchTitle = context.resources.getQuantityString(R.plurals.tasks_due, dueItems.size, dueItems.size)
        val inboxStyle = Notification.InboxStyle().setBigContentTitle(batchTitle)
        dueItems.take(6).forEach { item ->
            inboxStyle.addLine(item.title)
        }
        notificationManager.notify(
            notificationId,
            Notification.Builder(context, SHORT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(batchTitle)
                .setContentText(dueItems.previewTitles())
                .setStyle(inboxStyle)
                .setNumber(dueItems.size)
                .setContentIntent(openAppIntent(context, notificationId))
                .setAutoCancel(true)
                .setShowWhen(true)
                .setWhen(firedDueAtMillis)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setGroup(SHORT_GROUP_KEY)
                .setGroupAlertBehavior(Notification.GROUP_ALERT_ALL)
                .setOnlyAlertOnce(false)
                .build(),
        )
    }

    fun buildXHighAlarmNotification(
        context: Context,
        item: TodoItem,
        firedDueAtMillis: Long,
    ): Notification = buildXHighAlarmNotification(context, listOf(item), firedDueAtMillis)

    fun buildXHighAlarmNotification(
        context: Context,
        items: List<TodoItem>,
        firedDueAtMillis: Long,
        companionShortItems: List<TodoItem> = emptyList(),
    ): Notification {
        val dueItems = items.takeIf { it.isNotEmpty() }
            ?: listOf(
                TodoItem(
                    id = "xhigh-alarm",
                    title = context.getString(R.string.xhigh_task_due),
                    priority = TodoPriority.XHIGH,
                    dueAtMillis = firedDueAtMillis,
                    completed = false,
                    createdAtMillis = firedDueAtMillis,
                ),
            )
        val activityIntent = XHighAlarmActivity.pendingIntent(
            context = context,
            items = dueItems,
            firedDueAtMillis = firedDueAtMillis,
            companionShortItems = companionShortItems,
        )
        val actionIds = dueItems.map { it.id }
        val stopLabel = context.getString(if (dueItems.size > 1) R.string.stop_all else R.string.stop)
        val snoozeLabel = context.getString(if (dueItems.size > 1) R.string.snooze_all_10 else R.string.snooze_10)
        val stopAlarmIntent = XHighAlarmService.actionPendingIntent(
            context = context,
            todoIds = actionIds,
            action = XHighAlarmService.ACTION_STOP_ALARM,
            companionShortItems = companionShortItems,
            firedDueAtMillis = firedDueAtMillis,
        )
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(xhighNotificationChannel(context))

        return Notification.Builder(context, XHIGH_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(dueItems.xhighNotificationTitle(context))
            .setContentText(dueItems.xhighNotificationText(context, firedDueAtMillis))
            .setContentIntent(activityIntent)
            .setFullScreenIntent(activityIntent, true)
            .setDeleteIntent(stopAlarmIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setShowWhen(true)
            .setWhen(firedDueAtMillis)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setNumber(dueItems.size)
            .setGroup(XHIGH_GROUP_KEY)
            .setGroupAlertBehavior(Notification.GROUP_ALERT_ALL)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.ic_notification),
                    stopLabel,
                    stopAlarmIntent,
                ).build(),
            )
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.ic_notification),
                    snoozeLabel,
                    XHighAlarmService.actionPendingIntent(
                        context = context,
                        todoIds = actionIds,
                        action = XHighAlarmService.ACTION_SNOOZE_ALARM,
                        companionShortItems = companionShortItems,
                        firedDueAtMillis = firedDueAtMillis,
                    ),
                ).build(),
            )
            .build()
    }

    fun alarmSoundUri() =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    fun canPostNotifications(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun shortNotificationChannel(context: Context): NotificationChannel {
        return NotificationChannel(
            SHORT_CHANNEL_ID,
            context.getString(R.string.notification_short_channel),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notification_short_channel_description)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            enableVibration(true)
            vibrationPattern = ShortVibrationPattern
        }
    }

    private fun xhighNotificationChannel(context: Context): NotificationChannel {
        return NotificationChannel(
            XHIGH_CHANNEL_ID,
            context.getString(R.string.notification_alarm_channel),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notification_alarm_channel_description)
            setSound(
                alarmSoundUri(),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            enableVibration(true)
            vibrationPattern = XHighVibrationPattern
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
    }

    private fun openAppIntent(context: Context, requestCode: Int): PendingIntent {
        return PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun TodoItem.notificationContentText(context: Context, firedDueAtMillis: Long): String {
        return "${priority.name}  ${firedDueAtMillis.formatAlarmTime(context)}${reminderRepeat.notificationSuffix(context)}"
    }

    private fun List<TodoItem>.previewTitles(): String {
        val visibleTitles = take(3).joinToString(" / ") { it.title }
        return if (size > 3) "$visibleTitles / +${size - 3}" else visibleTitles
    }

    private fun List<TodoItem>.xhighNotificationTitle(context: Context): String {
        return if (size == 1) first().title else context.resources.getQuantityString(
            R.plurals.xhigh_tasks_due,
            size,
            size,
        )
    }

    private fun List<TodoItem>.xhighNotificationText(context: Context, firedDueAtMillis: Long): String {
        return if (size == 1) first().notificationContentText(context, firedDueAtMillis) else previewTitles()
    }

    private fun Long.formatAlarmTime(context: Context): String {
        return if (this > 0L) {
            java.time.Instant.ofEpochMilli(this)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDateTime()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        } else {
            context.getString(R.string.now)
        }
    }

    private fun ReminderRepeat.notificationSuffix(context: Context): String {
        return when (this) {
            ReminderRepeat.NONE -> ""
            ReminderRepeat.DAILY -> "  ${context.getString(R.string.repeat_daily)}"
            ReminderRepeat.WEEKLY -> "  ${context.getString(R.string.repeat_weekly)}"
        }
    }
}
