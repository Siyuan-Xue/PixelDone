package com.milesxue.pixeldone

import android.content.Context
import android.content.SharedPreferences

class TodoPreferences(private val sharedPreferences: SharedPreferences) {
    fun loadTodoState(nowMillis: Long = System.currentTimeMillis()): TodoChecklistState {
        val stateJson = sharedPreferences.getString(KEY_CHECKLIST_STATE, null)
        if (stateJson != null) {
            return TodoJsonCodec.decodeState(
                json = stateJson,
                fallbackCreatedAtMillis = nowMillis,
            ) ?: createInitialChecklistState(emptyList(), nowMillis)
        }

        val migratedState = createInitialChecklistState(
            items = loadTodos(),
            createdAtMillis = nowMillis,
        )
        saveTodoState(migratedState)
        return migratedState
    }

    fun saveTodoState(state: TodoChecklistState) {
        sharedPreferences.edit()
            .putString(KEY_CHECKLIST_STATE, TodoJsonCodec.encodeState(state))
            .apply()
    }

    fun observeTodoState(onChange: () -> Unit): () -> Unit {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_CHECKLIST_STATE) {
                onChange()
            }
        }
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        return {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

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
        private const val KEY_CHECKLIST_STATE = "checklist_state"
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
