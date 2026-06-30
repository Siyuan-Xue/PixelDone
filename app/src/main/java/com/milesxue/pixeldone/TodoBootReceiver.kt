package com.milesxue.pixeldone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TodoBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val state = TodoPreferences.create(context).loadTodoState()
        val currentTodos = normalTodos(state)
        TodoAlarmScheduler.sync(context, currentTodos, currentTodos)
    }
}
