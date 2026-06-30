package com.milesxue.pixeldone.data.todo

import android.content.Context
import android.content.SharedPreferences
import com.milesxue.pixeldone.domain.todo.TodoChecklistState
import com.milesxue.pixeldone.domain.todo.TodoItem
import com.milesxue.pixeldone.domain.todo.createInitialChecklistState

/**
 * SharedPreferences 版本的本地 Todo 存储。
 *
 * 教学说明：Repository 依赖的是 [TodoStateStore] 接口，真正的 Android 存储细节藏在这里。
 * 本轮重构刻意不迁移 DataStore/Room，因为格式迁移会带来用户数据风险；先用边界隔离旧实现。
 *
 * 兼容规则：旧版本只保存 `todos` 数组，新版本保存 `checklist_state`。
 * 如果用户从旧 APK 升级，会在第一次读取时把旧 todos 包装进默认 MAIN 清单并立即保存新状态。
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

    companion object {
        private const val PREFS_NAME = "pixel_done_todos"
        private const val KEY_TODOS = "todos"
        private const val KEY_CHECKLIST_STATE = "checklist_state"
        private const val KEY_NEVER_SHOW_UPDATE_DIALOG = "never_show_update_dialog"
        private const val KEY_DARK_THEME = "dark_theme"

        fun create(context: Context): TodoPreferences {
            val prefs = context.applicationContext.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE,
            )
            return TodoPreferences(prefs)
        }
    }
}
