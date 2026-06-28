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

    fun loadNeverShowUpdateDialog(): Boolean =
        sharedPreferences.getBoolean(KEY_NEVER_SHOW_UPDATE_DIALOG, false)

    fun saveNeverShowUpdateDialog(neverShow: Boolean) {
        sharedPreferences.edit()
            .putBoolean(KEY_NEVER_SHOW_UPDATE_DIALOG, neverShow)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "pixel_done_todos"
        private const val KEY_TODOS = "todos"
        private const val KEY_NEVER_SHOW_UPDATE_DIALOG = "never_show_update_dialog"

        fun create(context: Context): TodoPreferences {
            val prefs = context.applicationContext.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE,
            )
            return TodoPreferences(prefs)
        }
    }
}
