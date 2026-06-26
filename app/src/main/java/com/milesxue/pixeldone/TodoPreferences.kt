package com.milesxue.pixeldone

import android.content.Context
import android.content.SharedPreferences

class TodoPreferences(private val sharedPreferences: SharedPreferences) {
    fun loadTodos(): List<TodoItem> {
        val json = sharedPreferences.getString(KEY_TODOS, "[]") ?: "[]"
        return TodoJsonCodec.decode(json)
    }

    fun saveTodos(items: List<TodoItem>) {
        sharedPreferences.edit()
            .putString(KEY_TODOS, TodoJsonCodec.encode(items))
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "pixel_done_todos"
        private const val KEY_TODOS = "todos"

        fun create(context: Context): TodoPreferences {
            val prefs = context.applicationContext.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE,
            )
            return TodoPreferences(prefs)
        }
    }
}
