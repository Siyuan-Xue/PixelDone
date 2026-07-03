package com.milesxue.pixeldone.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.milesxue.pixeldone.di.pixelDoneAppContainer
import com.milesxue.pixeldone.domain.todo.normalTodos

class TodoBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val appContainer = context.pixelDoneAppContainer()
        appContainer.activeXHighAlarmStore.clear()
        val state = appContainer.todoRepository.loadTodoState()
        val currentTodos = normalTodos(state)
        appContainer.reminderScheduler.sync(currentTodos, currentTodos)
    }
}
