package com.milesxue.pixeldone.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.milesxue.pixeldone.data.todo.TodoRepository
import com.milesxue.pixeldone.di.pixelDoneAppContainer
import com.milesxue.pixeldone.domain.todo.ReminderAlertMode
import com.milesxue.pixeldone.domain.todo.ReminderDispatchPlan
import com.milesxue.pixeldone.domain.todo.TodoChecklistState
import com.milesxue.pixeldone.domain.todo.TodoItem
import com.milesxue.pixeldone.domain.todo.advanceRepeatingTodosAfterReminder
import com.milesxue.pixeldone.domain.todo.normalTodos
import com.milesxue.pixeldone.domain.todo.reminderDispatchPlan

/**
 * 系统闹钟广播入口。
 *
 * 教学说明：Receiver 生命周期很短，只适合读取当前状态、计算本次派发计划、触发通知/服务。
 * 重复提醒推进、同一时间批量提醒、去重窗口都保留在这里串起来，但具体规则仍放在 domain 层。
 */
class TodoAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val firedDueAtMillis = intent.getLongExtra(TodoAlarmScheduler.EXTRA_TODO_DUE_AT, 0L)
        if (firedDueAtMillis <= 0L) return
        val triggerAlertMode = intent.getStringExtra(TodoAlarmScheduler.EXTRA_ALERT_MODE)
            ?.let { mode -> ReminderAlertMode.entries.firstOrNull { it.name == mode } }
            ?: ReminderAlertMode.SHORT_NOTIFICATION
        val triggerTodoId = intent.getStringExtra(TodoAlarmScheduler.EXTRA_TODO_ID)

        val appContainer = context.pixelDoneAppContainer()
        val storage = appContainer.todoRepository
        val state = storage.loadTodoState()
        val dispatchPlan = reminderDispatchPlan(
            items = normalTodos(state),
            firedDueAtMillis = firedDueAtMillis,
            triggerTodoId = triggerTodoId,
            triggerAlertMode = triggerAlertMode,
            canScheduleExactAlarms = appContainer.reminderScheduler.canScheduleExactAlarms(),
        )
        if (dispatchPlan.isEmpty) return
        if (wasRecentlyDispatched(context, dispatchSignature(firedDueAtMillis, triggerAlertMode, dispatchPlan))) {
            return
        }

        if (dispatchPlan.fullscreenAlarmItems.isNotEmpty()) {
            XHighAlarmService.start(
                context = context,
                items = dispatchPlan.fullscreenAlarmItems,
                firedDueAtMillis = firedDueAtMillis,
                companionShortItems = dispatchPlan.shortNotificationItems,
            )
        } else if (dispatchPlan.shortNotificationItems.isNotEmpty()) {
            TodoReminderNotifier.showShortNotificationBatch(
                context = context,
                items = dispatchPlan.shortNotificationItems,
                firedDueAtMillis = firedDueAtMillis,
            )
        }

        rescheduleDispatchedTodos(
            scheduler = appContainer.reminderScheduler,
            storage = storage,
            state = state,
            dueItems = dispatchPlan.rescheduleItems,
            firedDueAtMillis = firedDueAtMillis,
        )
    }

    private fun rescheduleDispatchedTodos(
        scheduler: ReminderScheduler,
        storage: TodoRepository,
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

        scheduler.sync(
            previousItems = normalTodos(state),
            currentItems = normalTodos(updatedState),
        )
    }

    private fun dispatchSignature(
        firedDueAtMillis: Long,
        triggerAlertMode: ReminderAlertMode,
        dispatchPlan: ReminderDispatchPlan,
    ): String {
        return "$triggerAlertMode:$firedDueAtMillis:${dispatchPlan.signatureIds.joinToString(",")}"
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
