package com.milesxue.pixeldone.data.todo

import android.content.Context
import android.content.SharedPreferences
import com.milesxue.pixeldone.domain.todo.DefaultDockActions
import com.milesxue.pixeldone.domain.todo.DockAction
import com.milesxue.pixeldone.domain.todo.DockConfig
import com.milesxue.pixeldone.domain.todo.DockPlusPlacement
import com.milesxue.pixeldone.domain.todo.TodoChecklistState
import com.milesxue.pixeldone.domain.todo.TodoItem
import com.milesxue.pixeldone.domain.todo.createInitialChecklistState

/**
 * SharedPreferences-backed local todo store.
 *
 * The repository depends on [TodoStateStore]; Android storage details stay inside this adapter.
 * This iteration keeps the existing format to avoid migration risk while preserving a clear boundary.
 *
 * Compatibility rule: older builds stored only a `todos` array. Newer builds store `checklist_state`.
 * On first read after an upgrade, legacy todos are wrapped into the default MAIN checklist.
 */
class TodoPreferences(private val sharedPreferences: SharedPreferences) : TodoStateStore {
    override fun loadTodoState(nowMillis: Long): TodoChecklistState {
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

    override fun saveTodoState(state: TodoChecklistState) {
        sharedPreferences.edit()
            .putString(KEY_CHECKLIST_STATE, TodoJsonCodec.encodeState(state))
            .apply()
    }

    override fun observeTodoState(onChange: () -> Unit): () -> Unit {
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

    fun loadDarkTheme(): Boolean =
        sharedPreferences.getBoolean(KEY_DARK_THEME, false)

    fun saveDarkTheme(darkTheme: Boolean) {
        sharedPreferences.edit()
            .putBoolean(KEY_DARK_THEME, darkTheme)
            .apply()
    }

    fun loadDockConfig(): DockConfig {
        val placement = sharedPreferences.getString(KEY_DOCK_PLUS_PLACEMENT, null)
            ?.let { value -> runCatching { DockPlusPlacement.valueOf(value) }.getOrNull() }
            ?: DockPlusPlacement.CENTER
        val actions = sharedPreferences.getString(KEY_DOCK_ACTIONS, null)
            ?.split(DOCK_ACTION_SEPARATOR)
            ?.mapNotNull { value -> runCatching { DockAction.valueOf(value) }.getOrNull() }
            ?: DefaultDockActions
        return DockConfig(placement, actions).normalized()
    }

    fun saveDockConfig(config: DockConfig) {
        val normalizedConfig = config.normalized()
        sharedPreferences.edit()
            .putString(KEY_DOCK_PLUS_PLACEMENT, normalizedConfig.plusPlacement.name)
            .putString(KEY_DOCK_ACTIONS, normalizedConfig.actions.joinToString(DOCK_ACTION_SEPARATOR) { it.name })
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "pixel_done_todos"
        private const val KEY_TODOS = "todos"
        private const val KEY_CHECKLIST_STATE = "checklist_state"
        private const val KEY_NEVER_SHOW_UPDATE_DIALOG = "never_show_update_dialog"
        private const val KEY_DARK_THEME = "dark_theme"
        private const val KEY_DOCK_PLUS_PLACEMENT = "dock_plus_placement"
        private const val KEY_DOCK_ACTIONS = "dock_actions"
        private const val DOCK_ACTION_SEPARATOR = ","

        fun create(context: Context): TodoPreferences {
            val prefs = context.applicationContext.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE,
            )
            return TodoPreferences(prefs)
        }
    }
}
