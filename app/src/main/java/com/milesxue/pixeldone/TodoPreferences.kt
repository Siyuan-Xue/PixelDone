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

    fun loadHandledUpdateVersion(): String? =
        sharedPreferences.getString(KEY_HANDLED_UPDATE_VERSION, null)

    fun saveHandledUpdateVersion(version: String) {
        sharedPreferences.edit()
            .putString(KEY_HANDLED_UPDATE_VERSION, version)
            .apply()
    }

    internal fun loadPendingUpdateDownload(): PendingUpdateDownload? {
        val version = sharedPreferences.getString(KEY_PENDING_UPDATE_DOWNLOAD_VERSION, null)
            ?: return null
        val downloadId = sharedPreferences.getLong(KEY_PENDING_UPDATE_DOWNLOAD_ID, -1L)
        if (downloadId < 0L) return null
        return PendingUpdateDownload(version = version, downloadId = downloadId)
    }

    internal fun savePendingUpdateDownload(download: PendingUpdateDownload) {
        sharedPreferences.edit()
            .putString(KEY_PENDING_UPDATE_DOWNLOAD_VERSION, download.version)
            .putLong(KEY_PENDING_UPDATE_DOWNLOAD_ID, download.downloadId)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "pixel_done_todos"
        private const val KEY_TODOS = "todos"
        private const val KEY_CHECKLIST_STATE = "checklist_state"
        private const val KEY_NEVER_SHOW_UPDATE_DIALOG = "never_show_update_dialog"
        private const val KEY_HANDLED_UPDATE_VERSION = "handled_update_version"
        private const val KEY_PENDING_UPDATE_DOWNLOAD_VERSION = "pending_update_download_version"
        private const val KEY_PENDING_UPDATE_DOWNLOAD_ID = "pending_update_download_id"

        fun create(context: Context): TodoPreferences {
            val prefs = context.applicationContext.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE,
            )
            return TodoPreferences(prefs)
        }
    }
}
