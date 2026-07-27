package com.milesxue.pixeldone.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.milesxue.pixeldone.di.pixelDoneAppContainer
import com.milesxue.pixeldone.domain.todo.ReminderAlertMode
import com.milesxue.pixeldone.domain.todo.ReminderDispatchPlan
import com.milesxue.pixeldone.domain.todo.normalTodos
import com.milesxue.pixeldone.domain.todo.reminderDispatchPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 系统闹钟广播入口。
 *
 * 教学说明：Receiver 生命周期很短，只适合读取当前状态、计算本次派发计划、触发通知/服务。
 * 重复提醒推进、同一时间批量提醒、去重窗口都保留在这里串起来，但具体规则仍放在 domain 层。
 */
class TodoAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val alarmIntent = Intent(intent)
        receiverScope.launch {
            try {
                dispatch(appContext, alarmIntent)
            } catch (error: Throwable) {
                Log.e(LOG_TAG, "Unable to dispatch todo reminder", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun dispatch(context: Context, intent: Intent) {
        synchronized(dispatchLock) {
            dispatchSerially(context, intent)
        }
    }

    private fun dispatchSerially(context: Context, intent: Intent) {
        val firedDueAtMillis = intent.getLongExtra(
            TodoAlarmScheduler.EXTRA_REMINDER_OCCURRENCE_AT,
            intent.getLongExtra(TodoAlarmScheduler.EXTRA_TODO_DUE_AT, 0L),
        )
        if (firedDueAtMillis <= 0L) return
        val triggerAlertMode = intent.getStringExtra(TodoAlarmScheduler.EXTRA_ALERT_MODE)
            ?.let { mode -> ReminderAlertMode.entries.firstOrNull { it.name == mode } }
            ?: ReminderAlertMode.SHORT_NOTIFICATION
        val triggerTodoId = intent.getStringExtra(TodoAlarmScheduler.EXTRA_TODO_ID)

        val appContainer = context.pixelDoneAppContainer()
        val occurrenceCoordinator = appContainer.reminderOccurrenceCoordinator
        val state = appContainer.todoRepository.loadTodoState()
        val allItems = normalTodos(state)
        val occurrencePartition = partitionReminderOccurrence(
            items = allItems,
            occurrenceAtMillis = firedDueAtMillis,
            wasDelivered = occurrenceCoordinator::wasDelivered,
        )
        val previouslyDeliveredIds = occurrencePartition.alreadyDeliveredTodoIds
        if (previouslyDeliveredIds.isNotEmpty()) {
            runCatching {
                occurrenceCoordinator.advanceDeliveredTodos(
                    todoIds = previouslyDeliveredIds,
                    occurrenceAtMillis = firedDueAtMillis,
                )
            }.onFailure { error ->
                Log.e(LOG_TAG, "Unable to reconcile an already delivered reminder", error)
            }
        }
        val hasExactAlarmAccess = appContainer.reminderScheduler.canScheduleExactAlarms()
        val dispatchPlan = reminderDispatchPlan(
            items = occurrencePartition.dispatchCandidateItems,
            firedDueAtMillis = firedDueAtMillis,
            triggerTodoId = triggerTodoId,
            triggerAlertMode = triggerAlertMode,
            canScheduleExactAlarms = hasExactAlarmAccess,
        )
        if (dispatchPlan.isEmpty) return

        val capabilities = TodoReminderNotifier.deliveryCapabilities(
            context = context,
            exactAlarmAccess = hasExactAlarmAccess,
        )
        val delivery = if (dispatchPlan.fullscreenAlarmItems.isNotEmpty()) {
            xhighReminderDelivery(capabilities)
        } else if (capabilities.canShowShortNotification) {
            XHighReminderDelivery.SHORT_NOTIFICATION
        } else {
            XHighReminderDelivery.SUPPRESSED
        }
        if (delivery == XHighReminderDelivery.SUPPRESSED) {
            // Keep the task due instead of starting sound or vibration without a visible stop path.
            return
        }
        if (
            wasRecentlyDispatched(
                context,
                dispatchSignature(firedDueAtMillis, delivery, dispatchPlan),
            )
        ) {
            return
        }

        val delivered = when (delivery) {
            XHighReminderDelivery.FULLSCREEN_LOOP -> runCatching {
                XHighAlarmService.start(
                    context = context,
                    items = dispatchPlan.fullscreenAlarmItems,
                    firedDueAtMillis = firedDueAtMillis,
                    companionShortItems = dispatchPlan.shortNotificationItems,
                )
            }.isSuccess
            XHighReminderDelivery.SHORT_NOTIFICATION -> {
                val notificationItems = (
                    dispatchPlan.fullscreenAlarmItems +
                        dispatchPlan.shortNotificationItems
                    ).distinctBy { it.id }
                TodoReminderNotifier.showShortNotificationBatch(
                    context = context,
                    items = notificationItems,
                    firedDueAtMillis = firedDueAtMillis,
                )
            }
            XHighReminderDelivery.SUPPRESSED -> false
        }
        if (!delivered) {
            return
        }

        if (delivery != XHighReminderDelivery.FULLSCREEN_LOOP) {
            runCatching {
                occurrenceCoordinator.recordDeliveredAndAdvance(
                    todoIds = dispatchPlan.rescheduleItems
                        .mapTo(mutableSetOf()) { item -> item.id },
                    occurrenceAtMillis = firedDueAtMillis,
                )
            }.onFailure { error ->
                Log.e(LOG_TAG, "Unable to advance delivered recurring reminders", error)
            }
        }
        recordDispatch(
            context,
            dispatchSignature(firedDueAtMillis, delivery, dispatchPlan),
        )
    }

    private fun dispatchSignature(
        firedDueAtMillis: Long,
        delivery: XHighReminderDelivery,
        dispatchPlan: ReminderDispatchPlan,
    ): String {
        return "$delivery:$firedDueAtMillis:${dispatchPlan.signatureIds.joinToString(",")}"
    }

    private fun wasRecentlyDispatched(context: Context, signature: String): Boolean {
        val nowMillis = System.currentTimeMillis()
        val preferences = context.getSharedPreferences(DISPATCH_PREFS_NAME, Context.MODE_PRIVATE)
        val lastSignature = preferences.getString(KEY_LAST_SIGNATURE, null)
        val lastDispatchedAt = preferences.getLong(KEY_LAST_DISPATCHED_AT, 0L)
        if (signature == lastSignature && nowMillis - lastDispatchedAt in 0L..DISPATCH_DEDUP_WINDOW_MILLIS) {
            return true
        }
        return false
    }

    private fun recordDispatch(context: Context, signature: String) {
        val nowMillis = System.currentTimeMillis()
        val preferences = context.getSharedPreferences(DISPATCH_PREFS_NAME, Context.MODE_PRIVATE)
        preferences.edit()
            .putString(KEY_LAST_SIGNATURE, signature)
            .putLong(KEY_LAST_DISPATCHED_AT, nowMillis)
            .apply()
    }

    private companion object {
        private const val LOG_TAG = "TodoAlarmReceiver"
        private const val DISPATCH_PREFS_NAME = "todo_alarm_dispatch"
        private const val KEY_LAST_SIGNATURE = "last_signature"
        private const val KEY_LAST_DISPATCHED_AT = "last_dispatched_at"
        private const val DISPATCH_DEDUP_WINDOW_MILLIS = 2L * 60L * 1000L
        private val dispatchLock = Any()
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
