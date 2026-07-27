package com.milesxue.pixeldone.reminder

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.milesxue.pixeldone.di.pixelDoneAppContainer
import com.milesxue.pixeldone.domain.todo.normalTodos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TodoBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in RECOVERY_ACTIONS) return

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        recoveryScope.launch {
            try {
                val appContainer = appContext.pixelDoneAppContainer()
                if (
                    action == Intent.ACTION_BOOT_COMPLETED ||
                    action == Intent.ACTION_MY_PACKAGE_REPLACED
                ) {
                    appContainer.activeXHighAlarmStore.clear()
                }
                val currentTodos = normalTodos(appContainer.todoRepository.loadTodoState())
                appContainer.reminderScheduler.rescheduleAll(currentTodos)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        val RECOVERY_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
        val recoveryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
