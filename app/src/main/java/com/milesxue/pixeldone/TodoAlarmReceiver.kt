package com.milesxue.pixeldone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TodoAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val firedDueAtMillis = intent.getLongExtra(TodoAlarmScheduler.EXTRA_TODO_DUE_AT, 0L)
        if (firedDueAtMillis <= 0L) return

        val storage = TodoPreferences.create(context)
        val state = storage.loadTodoState()
        val dueItems = todosDueForReminder(normalTodos(state), firedDueAtMillis)
        if (dueItems.isEmpty()) return
        if (wasRecentlyDispatched(context, dispatchSignature(firedDueAtMillis, dueItems))) return

        val xhighItems = dueItems.filter { it.priority == TodoPriority.XHIGH }
        val shortItems = dueItems.filter { it.priority != TodoPriority.XHIGH }

        if (xhighItems.isNotEmpty()) {
            XHighAlarmService.start(
                context = context,
                items = xhighItems,
                firedDueAtMillis = firedDueAtMillis,
                companionShortItems = shortItems,
            )
        } else if (shortItems.isNotEmpty()) {
            TodoReminderNotifier.showShortNotificationBatch(context, shortItems, firedDueAtMillis)
        }

        rescheduleDispatchedTodos(context, storage, state, dueItems, firedDueAtMillis)
    }

    private fun rescheduleDispatchedTodos(
        context: Context,
        storage: TodoPreferences,
        state: TodoChecklistState,
        dueItems: List<TodoItem>,
        firedDueAtMillis: Long,
    ) {
        val updatedState = advanceRepeatingTodosAfterReminder(
            state = state,
            todoIds = dueItems.mapTo(mutableSetOf()) { it.id },
            nowMillis = maxOf(System.currentTimeMillis(), firedDueAtMillis),
        ) ?: state
        if (updatedState != state) {
            storage.saveTodoState(updatedState)
        }

        TodoAlarmScheduler.sync(
            context = context,
            previousItems = normalTodos(state),
            currentItems = normalTodos(updatedState),
        )
    }

    private fun dispatchSignature(firedDueAtMillis: Long, items: List<TodoItem>): String {
        return "$firedDueAtMillis:${items.map { it.id }.sorted().joinToString(",")}"
    }

    private fun wasRecentlyDispatched(context: Context, signature: String): Boolean {
        val nowMillis = System.currentTimeMillis()
        val preferences = context.getSharedPreferences(DISPATCH_PREFS_NAME, Context.MODE_PRIVATE)
        val lastSignature = preferences.getString(KEY_LAST_SIGNATURE, null)
        val lastDispatchedAt = preferences.getLong(KEY_LAST_DISPATCHED_AT, 0L)
        if (signature == lastSignature && nowMillis - lastDispatchedAt in 0L..DISPATCH_DEDUP_WINDOW_MILLIS) {
            return true
        }
        preferences.edit()
            .putString(KEY_LAST_SIGNATURE, signature)
            .putLong(KEY_LAST_DISPATCHED_AT, nowMillis)
            .apply()
        return false
    }

    private companion object {
        private const val DISPATCH_PREFS_NAME = "todo_alarm_dispatch"
        private const val KEY_LAST_SIGNATURE = "last_signature"
        private const val KEY_LAST_DISPATCHED_AT = "last_dispatched_at"
        private const val DISPATCH_DEDUP_WINDOW_MILLIS = 2L * 60L * 1000L
    }
}
