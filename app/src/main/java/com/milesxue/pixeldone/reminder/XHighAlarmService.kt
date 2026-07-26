package com.milesxue.pixeldone.reminder

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Vibrator
import android.os.VibratorManager
import com.milesxue.pixeldone.R
import com.milesxue.pixeldone.di.pixelDoneAppContainer
import com.milesxue.pixeldone.domain.todo.DefaultSnoozeIntervalMillis
import com.milesxue.pixeldone.domain.todo.ReminderRepeat
import com.milesxue.pixeldone.domain.todo.TodoItem
import com.milesxue.pixeldone.domain.todo.TodoPriority
import com.milesxue.pixeldone.domain.todo.advanceRepeatingTodosAfterReminder
import com.milesxue.pixeldone.domain.todo.normalTodos
import com.milesxue.pixeldone.domain.todo.snoozeTodosAfterReminder

class XHighAlarmService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var playbackController: AlarmPlaybackController
    private var foregroundStarted = false
    private var activeTodoIds: List<String> = emptyList()
    private var activeCompanionShortItems: List<TodoItem> = emptyList()
    private var activeFiredDueAtMillis: Long = 0L
    private var activeAlarmSession = 0L

    override fun onCreate() {
        super.onCreate()
        playbackController = AlarmPlaybackController(
            playerFactory = {
                TodoReminderNotifier.alarmSoundUri()?.let { soundUri ->
                    AndroidAlarmAudioPlayer(this, soundUri)
                }
            },
            vibrationController = AndroidAlarmVibrationController(
                vibrator = alarmVibrator(),
                pattern = TodoReminderNotifier.XHighVibrationPattern,
            ),
            timeoutScheduler = WatchdogAlarmTimeoutScheduler,
            hardTimeoutMillis = MAX_ALARM_DURATION_MILLIS,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_ALARM -> startAlarm(intent, startId)
            ACTION_STOP_ALARM -> {
                restoreCompanionShortItems(intent)
                stopAlarm(showCompanionShortReminder = true)
            }
            ACTION_SNOOZE_ALARM -> {
                restoreCompanionShortItems(intent)
                val todoIds = intent.todoIds().ifEmpty { activeTodoIds }
                playbackController.stop()
                if (todoIds.isNotEmpty()) snoozeAlarms(todoIds)
                stopAlarm(showCompanionShortReminder = true)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        activeAlarmSession += 1L
        mainHandler.removeCallbacksAndMessages(null)
        playbackController.stop()
        clearActiveAlarmState()
        signalAlarmFinished()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAlarm(intent: Intent, startId: Int) {
        val items = intent.toDisplayTodoItems()
        if (items.isEmpty()) {
            stopSelfResult(startId)
            return
        }
        val session = activeAlarmSession + 1L
        activeAlarmSession = session
        // A newer START must retire the previous loop before notification or storage Binder work.
        playbackController.stop()
        activeTodoIds = items.map { it.id }
        val firedDueAtMillis = intent.getLongExtra(TodoAlarmScheduler.EXTRA_TODO_DUE_AT, items.first().dueAtMillis)
        val companionShortItems = intent.toCompanionShortItems(firedDueAtMillis)
        activeCompanionShortItems = companionShortItems
        activeFiredDueAtMillis = firedDueAtMillis

        val delivery = runCatching {
            xhighReminderDelivery(
                TodoReminderNotifier.deliveryCapabilities(
                    context = this,
                    exactAlarmAccess = pixelDoneAppContainer()
                        .reminderScheduler
                        .canScheduleExactAlarms(),
                ),
            )
        }.getOrDefault(XHighReminderDelivery.SUPPRESSED)
        if (delivery != XHighReminderDelivery.FULLSCREEN_LOOP) {
            // This instance was launched with startForegroundService(). End that startup
            // obligation before notification/storage Binder work in the downgrade path.
            stopSelfResult(startId)
            try {
                if (delivery == XHighReminderDelivery.SHORT_NOTIFICATION) {
                    val delivered = TodoReminderNotifier.showShortNotificationBatch(
                        context = this,
                        items = (items + companionShortItems).distinctBy { it.id },
                        firedDueAtMillis = firedDueAtMillis,
                    )
                    if (delivered) {
                        rescheduleDeliveredTodos(
                            todoIds = (items + companionShortItems).mapTo(mutableSetOf()) { it.id },
                            firedDueAtMillis = firedDueAtMillis,
                        )
                    }
                }
            } finally {
                stopAlarm(
                    showCompanionShortReminder = false,
                    expectedSession = session,
                    stopStartId = startId,
                )
            }
            return
        }

        val foregroundStartedSuccessfully = runCatching {
            startForeground(
                TodoReminderNotifier.XHIGH_NOTIFICATION_ID,
                TodoReminderNotifier.buildXHighAlarmNotification(
                    context = this,
                    items = items,
                    firedDueAtMillis = firedDueAtMillis,
                    companionShortItems = companionShortItems,
                ),
            )
        }.isSuccess
        if (!foregroundStartedSuccessfully) {
            // startForeground() failed, so release the startup deadline immediately before
            // performing the best-effort short-notification fallback.
            stopSelfResult(startId)
            try {
                val delivered = TodoReminderNotifier.showShortNotificationBatch(
                    context = this,
                    items = (items + companionShortItems).distinctBy { it.id },
                    firedDueAtMillis = firedDueAtMillis,
                )
                if (delivered) {
                    rescheduleDeliveredTodos(
                        todoIds = (items + companionShortItems).mapTo(mutableSetOf()) { it.id },
                        firedDueAtMillis = firedDueAtMillis,
                    )
                }
            } finally {
                stopAlarm(
                    showCompanionShortReminder = false,
                    expectedSession = session,
                    stopStartId = startId,
                )
            }
            return
        }
        foregroundStarted = true
        runCatching {
            activeXHighAlarmFrom(
                items = items,
                firedDueAtMillis = firedDueAtMillis,
                companionShortItems = companionShortItems,
                updatedAtMillis = System.currentTimeMillis(),
            )?.let { activeAlarm ->
                pixelDoneAppContainer().activeXHighAlarmStore.save(activeAlarm)
            }
        }
        playbackController.start {
            mainHandler.post {
                stopAlarm(
                    // The XHIGH loop has timed out, but ordinary tasks that shared its due time
                    // still deserve their one-shot short notification.
                    showCompanionShortReminder = true,
                    expectedSession = session,
                )
            }
        }
        runCatching {
            rescheduleDeliveredTodos(
                // Companion reminders are not visible until the alarm is acknowledged.
                // Keep them due until their short notification is actually posted.
                todoIds = items.mapTo(mutableSetOf()) { it.id },
                firedDueAtMillis = firedDueAtMillis,
            )
        }
    }

    private fun restoreCompanionShortItems(intent: Intent) {
        if (activeCompanionShortItems.isNotEmpty()) return
        val firedDueAtMillis = intent.getLongExtra(TodoAlarmScheduler.EXTRA_TODO_DUE_AT, 0L)
        activeCompanionShortItems = intent.toCompanionShortItems(firedDueAtMillis)
        activeFiredDueAtMillis = firedDueAtMillis
    }

    private fun stopAlarm(
        showCompanionShortReminder: Boolean,
        expectedSession: Long? = null,
        stopStartId: Int? = null,
    ) {
        if (expectedSession != null && expectedSession != activeAlarmSession) return
        activeAlarmSession += 1L
        playbackController.stop()
        if (foregroundStarted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            foregroundStarted = false
        }
        clearActiveAlarmState()
        if (showCompanionShortReminder) {
            showCompanionShortReminderAfterAcknowledgement()
        }
        activeTodoIds = emptyList()
        activeCompanionShortItems = emptyList()
        activeFiredDueAtMillis = 0L
        signalAlarmFinished()
        if (stopStartId != null) {
            stopSelfResult(stopStartId)
        } else {
            stopSelf()
        }
    }

    private fun clearActiveAlarmState() {
        pixelDoneAppContainer().activeXHighAlarmStore.clear()
    }

    private fun showCompanionShortReminderAfterAcknowledgement() {
        val companionItems = activeCompanionShortItems
        if (companionItems.isEmpty()) return

        val delivered = TodoReminderNotifier.showShortNotificationBatch(
            context = this,
            items = companionItems,
            firedDueAtMillis = activeFiredDueAtMillis.takeIf { it > 0L } ?: System.currentTimeMillis(),
            replaceExisting = true,
        )
        if (delivered) {
            runCatching {
                rescheduleDeliveredTodos(
                    todoIds = companionItems.mapTo(mutableSetOf()) { it.id },
                    firedDueAtMillis = activeFiredDueAtMillis
                        .takeIf { it > 0L }
                        ?: System.currentTimeMillis(),
                )
            }
        }
    }

    private fun snoozeAlarms(todoIds: List<String>) {
        val appContainer = pixelDoneAppContainer()
        val storage = appContainer.todoRepository
        val state = storage.loadTodoState()
        val updatedState = snoozeTodosAfterReminder(
            state = state,
            todoIds = todoIds.toSet(),
            nowMillis = System.currentTimeMillis(),
        ) ?: return
        storage.saveTodoState(updatedState)
        appContainer.reminderScheduler.sync(normalTodos(state), normalTodos(updatedState))
    }

    private fun rescheduleDeliveredTodos(
        todoIds: Set<String>,
        firedDueAtMillis: Long,
    ) {
        if (todoIds.isEmpty()) return
        val appContainer = pixelDoneAppContainer()
        val storage = appContainer.todoRepository
        val state = storage.loadTodoState()
        val updatedState = advanceRepeatingTodosAfterReminder(
            state = state,
            todoIds = todoIds,
            nowMillis = maxOf(System.currentTimeMillis(), firedDueAtMillis),
        ) ?: state
        if (updatedState != state) {
            storage.saveTodoState(updatedState)
        }
        appContainer.reminderScheduler.sync(
            previousItems = normalTodos(state),
            currentItems = normalTodos(updatedState),
        )
    }

    private fun signalAlarmFinished() {
        sendBroadcast(
            Intent(ACTION_ALARM_FINISHED)
                .setPackage(packageName),
        )
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
                title = titles.getOrNull(index)?.takeIf { it.isNotBlank() } ?: getString(R.string.todo_due),
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
                title = titles.getOrNull(index)?.takeIf { it.isNotBlank() } ?: getString(R.string.todo_due),
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
        const val ACTION_ALARM_FINISHED = "com.milesxue.pixeldone.action.XHIGH_ALARM_FINISHED"
        const val EXTRA_BATCH_TODO_IDS = "com.milesxue.pixeldone.extra.BATCH_TODO_IDS"
        const val EXTRA_BATCH_TODO_TITLES = "com.milesxue.pixeldone.extra.BATCH_TODO_TITLES"
        const val EXTRA_COMPANION_TODO_IDS = "com.milesxue.pixeldone.extra.COMPANION_TODO_IDS"
        const val EXTRA_COMPANION_TODO_TITLES = "com.milesxue.pixeldone.extra.COMPANION_TODO_TITLES"
        const val EXTRA_COMPANION_TODO_PRIORITIES = "com.milesxue.pixeldone.extra.COMPANION_TODO_PRIORITIES"
        const val EXTRA_COMPANION_TODO_REPEATS = "com.milesxue.pixeldone.extra.COMPANION_TODO_REPEATS"
        internal const val MAX_ALARM_DURATION_MILLIS = 10L * 60L * 1000L

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
            val intent = actionIntent(
                context = context,
                todoIds = todoIds,
                action = action,
                companionShortItems = companionShortItems,
                firedDueAtMillis = firedDueAtMillis,
            )
            return PendingIntent.getService(
                context,
                batchRequestCode(todoIds, action),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        fun actionIntent(
            context: Context,
            todoIds: List<String>,
            action: String,
            companionShortItems: List<TodoItem> = emptyList(),
            firedDueAtMillis: Long = 0L,
        ): Intent {
            return Intent(context, XHighAlarmService::class.java).apply {
                this.action = action
                putStringArrayListExtra(EXTRA_BATCH_TODO_IDS, ArrayList(todoIds))
                todoIds.firstOrNull()?.let { putExtra(TodoAlarmScheduler.EXTRA_TODO_ID, it) }
                putCompanionShortItems(companionShortItems)
                if (firedDueAtMillis > 0L) {
                    putExtra(TodoAlarmScheduler.EXTRA_TODO_DUE_AT, firedDueAtMillis)
                }
            }
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
