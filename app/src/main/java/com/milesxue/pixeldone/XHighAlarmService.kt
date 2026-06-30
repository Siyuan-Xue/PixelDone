package com.milesxue.pixeldone

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class XHighAlarmService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var activeTodoIds: List<String> = emptyList()
    private var activeCompanionShortItems: List<TodoItem> = emptyList()
    private var activeFiredDueAtMillis: Long = 0L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_ALARM -> startAlarm(intent)
            ACTION_STOP_ALARM -> {
                restoreCompanionShortItems(intent)
                stopAlarm(showCompanionShortReminder = true)
            }
            ACTION_SNOOZE_ALARM -> {
                restoreCompanionShortItems(intent)
                val todoIds = intent.todoIds().ifEmpty { activeTodoIds }
                if (todoIds.isNotEmpty()) snoozeAlarms(todoIds)
                stopAlarm(showCompanionShortReminder = true)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopPlayback()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAlarm(intent: Intent) {
        val items = intent.toDisplayTodoItems()
        if (items.isEmpty()) return
        activeTodoIds = items.map { it.id }
        val firedDueAtMillis = intent.getLongExtra(TodoAlarmScheduler.EXTRA_TODO_DUE_AT, items.first().dueAtMillis)
        val companionShortItems = intent.toCompanionShortItems(firedDueAtMillis)
        activeCompanionShortItems = companionShortItems
        activeFiredDueAtMillis = firedDueAtMillis

        startForeground(
            TodoReminderNotifier.XHIGH_NOTIFICATION_ID,
            TodoReminderNotifier.buildXHighAlarmNotification(
                context = this,
                items = items,
                firedDueAtMillis = firedDueAtMillis,
                companionShortItems = companionShortItems,
            ),
        )
        startPlayback()
    }

    private fun restoreCompanionShortItems(intent: Intent) {
        if (activeCompanionShortItems.isNotEmpty()) return
        val firedDueAtMillis = intent.getLongExtra(TodoAlarmScheduler.EXTRA_TODO_DUE_AT, 0L)
        activeCompanionShortItems = intent.toCompanionShortItems(firedDueAtMillis)
        activeFiredDueAtMillis = firedDueAtMillis
    }

    private fun stopAlarm(showCompanionShortReminder: Boolean) {
        stopPlayback()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        if (showCompanionShortReminder) {
            showCompanionShortReminderAfterAcknowledgement()
        }
        activeTodoIds = emptyList()
        activeCompanionShortItems = emptyList()
        activeFiredDueAtMillis = 0L
        stopSelf()
    }

    private fun showCompanionShortReminderAfterAcknowledgement() {
        val companionItems = activeCompanionShortItems
        if (companionItems.isEmpty()) return

        TodoReminderNotifier.showShortNotificationBatch(
            context = this,
            items = companionItems,
            firedDueAtMillis = activeFiredDueAtMillis.takeIf { it > 0L } ?: System.currentTimeMillis(),
            replaceExisting = true,
        )
    }

    private fun snoozeAlarms(todoIds: List<String>) {
        val storage = TodoPreferences.create(this)
        val state = storage.loadTodoState()
        val updatedState = snoozeTodosAfterReminder(
            state = state,
            todoIds = todoIds.toSet(),
            nowMillis = System.currentTimeMillis(),
        ) ?: return
        storage.saveTodoState(updatedState)
        TodoAlarmScheduler.sync(this, normalTodos(state), normalTodos(updatedState))
    }

    private fun startPlayback() {
        stopPlayback()
        val alarmSoundUri = TodoReminderNotifier.alarmSoundUri() ?: return

        mediaPlayer = runCatching {
            MediaPlayer().apply {
                setDataSource(this@XHighAlarmService, alarmSoundUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()
        vibrator = alarmVibrator().also { alarmVibrator ->
            alarmVibrator.vibrate(
                VibrationEffect.createWaveform(TodoReminderNotifier.XHighVibrationPattern, 0),
            )
        }
    }

    private fun stopPlayback() {
        mediaPlayer?.run {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        vibrator?.cancel()
        vibrator = null
    }

    private fun alarmVibrator(): Vibrator {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
    }

    private fun Intent.toDisplayTodoItems(): List<TodoItem> {
        val todoIds = todoIds()
        if (todoIds.isEmpty()) return emptyList()
        val titles = getStringArrayListExtra(EXTRA_BATCH_TODO_TITLES).orEmpty()
        val dueAtMillis = getLongExtra(TodoAlarmScheduler.EXTRA_TODO_DUE_AT, System.currentTimeMillis())

        return todoIds.mapIndexed { index, todoId ->
            TodoItem(
                id = todoId,
                title = titles.getOrNull(index)?.takeIf { it.isNotBlank() } ?: "Todo due",
                priority = TodoPriority.XHIGH,
                dueAtMillis = dueAtMillis,
                completed = false,
                createdAtMillis = index.toLong(),
            )
        }
    }

    private fun Intent.toCompanionShortItems(firedDueAtMillis: Long): List<TodoItem> {
        val todoIds = getStringArrayListExtra(EXTRA_COMPANION_TODO_IDS)
            ?.filter { it.isNotBlank() }
            .orEmpty()
        if (todoIds.isEmpty()) return emptyList()

        val titles = getStringArrayListExtra(EXTRA_COMPANION_TODO_TITLES).orEmpty()
        val priorities = getStringArrayListExtra(EXTRA_COMPANION_TODO_PRIORITIES).orEmpty()
        val repeats = getStringArrayListExtra(EXTRA_COMPANION_TODO_REPEATS).orEmpty()

        return todoIds.mapIndexed { index, todoId ->
            TodoItem(
                id = todoId,
                title = titles.getOrNull(index)?.takeIf { it.isNotBlank() } ?: "Todo due",
                priority = priorities.getOrNull(index)
                    ?.let { name -> TodoPriority.entries.firstOrNull { it.name == name } }
                    ?: TodoPriority.MEDIUM,
                dueAtMillis = firedDueAtMillis,
                completed = false,
                createdAtMillis = index.toLong(),
                reminderRepeat = repeats.getOrNull(index)
                    ?.let { name -> ReminderRepeat.entries.firstOrNull { it.name == name } }
                    ?: ReminderRepeat.NONE,
            )
        }
    }

    private fun Intent.todoIds(): List<String> {
        val batchIds = getStringArrayListExtra(EXTRA_BATCH_TODO_IDS)
            ?.filter { it.isNotBlank() }
            .orEmpty()
        if (batchIds.isNotEmpty()) return batchIds

        return getStringExtra(TodoAlarmScheduler.EXTRA_TODO_ID)
            ?.takeIf { it.isNotBlank() }
            ?.let(::listOf)
            .orEmpty()
    }

    companion object {
        const val ACTION_START_ALARM = "com.milesxue.pixeldone.action.START_XHIGH_ALARM"
        const val ACTION_STOP_ALARM = "com.milesxue.pixeldone.action.STOP_XHIGH_ALARM"
        const val ACTION_SNOOZE_ALARM = "com.milesxue.pixeldone.action.SNOOZE_XHIGH_ALARM"
        const val EXTRA_BATCH_TODO_IDS = "com.milesxue.pixeldone.extra.BATCH_TODO_IDS"
        const val EXTRA_BATCH_TODO_TITLES = "com.milesxue.pixeldone.extra.BATCH_TODO_TITLES"
        const val EXTRA_COMPANION_TODO_IDS = "com.milesxue.pixeldone.extra.COMPANION_TODO_IDS"
        const val EXTRA_COMPANION_TODO_TITLES = "com.milesxue.pixeldone.extra.COMPANION_TODO_TITLES"
        const val EXTRA_COMPANION_TODO_PRIORITIES = "com.milesxue.pixeldone.extra.COMPANION_TODO_PRIORITIES"
        const val EXTRA_COMPANION_TODO_REPEATS = "com.milesxue.pixeldone.extra.COMPANION_TODO_REPEATS"

        fun start(context: Context, item: TodoItem, firedDueAtMillis: Long) {
            start(context, listOf(item), firedDueAtMillis)
        }

        fun start(
            context: Context,
            items: List<TodoItem>,
            firedDueAtMillis: Long,
            companionShortItems: List<TodoItem> = emptyList(),
        ) {
            if (items.isEmpty()) return
            val intent = Intent(context, XHighAlarmService::class.java).apply {
                action = ACTION_START_ALARM
                putStringArrayListExtra(EXTRA_BATCH_TODO_IDS, ArrayList(items.map { it.id }))
                putStringArrayListExtra(EXTRA_BATCH_TODO_TITLES, ArrayList(items.map { it.title }))
                putCompanionShortItems(companionShortItems)
                putExtra(TodoAlarmScheduler.EXTRA_TODO_ID, items.first().id)
                putExtra(TodoAlarmScheduler.EXTRA_TODO_TITLE, items.first().title)
                putExtra(TodoAlarmScheduler.EXTRA_TODO_PRIORITY, TodoPriority.XHIGH.name)
                putExtra(TodoAlarmScheduler.EXTRA_TODO_DUE_AT, firedDueAtMillis)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun actionPendingIntent(
            context: Context,
            todoId: String,
            action: String,
        ): PendingIntent = actionPendingIntent(context, listOf(todoId), action)

        fun actionPendingIntent(
            context: Context,
            todoIds: List<String>,
            action: String,
            companionShortItems: List<TodoItem> = emptyList(),
            firedDueAtMillis: Long = 0L,
        ): PendingIntent {
            val intent = Intent(context, XHighAlarmService::class.java).apply {
                this.action = action
                putStringArrayListExtra(EXTRA_BATCH_TODO_IDS, ArrayList(todoIds))
                todoIds.firstOrNull()?.let { putExtra(TodoAlarmScheduler.EXTRA_TODO_ID, it) }
                putCompanionShortItems(companionShortItems)
                if (firedDueAtMillis > 0L) {
                    putExtra(TodoAlarmScheduler.EXTRA_TODO_DUE_AT, firedDueAtMillis)
                }
            }
            return PendingIntent.getService(
                context,
                batchRequestCode(todoIds, action),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun batchRequestCode(todoIds: List<String>, action: String): Int {
            return todoIds.sorted().joinToString("|").hashCode() xor action.hashCode()
        }
    }
}

fun Intent.putCompanionShortItems(items: List<TodoItem>) {
    putStringArrayListExtra(XHighAlarmService.EXTRA_COMPANION_TODO_IDS, ArrayList(items.map { it.id }))
    putStringArrayListExtra(XHighAlarmService.EXTRA_COMPANION_TODO_TITLES, ArrayList(items.map { it.title }))
    putStringArrayListExtra(
        XHighAlarmService.EXTRA_COMPANION_TODO_PRIORITIES,
        ArrayList(items.map { it.priority.name }),
    )
    putStringArrayListExtra(
        XHighAlarmService.EXTRA_COMPANION_TODO_REPEATS,
        ArrayList(items.map { it.reminderRepeat.name }),
    )
}
