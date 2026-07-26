package com.milesxue.pixeldone.widget

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

internal object PixelDoneWidgetConfigStore {
    private const val PreferencesName = "pixeldone_widget_config"
    private const val ChecklistKeyPrefix = "checklist_"

    fun checklistId(context: Context, appWidgetId: Int): String? =
        preferences(context).getString(checklistKey(appWidgetId), null)

    fun checklistIdFlow(context: Context, appWidgetId: Int): Flow<String?> {
        val preferences = preferences(context)
        val key = checklistKey(appWidgetId)
        return callbackFlow {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener {
                    changedPreferences,
                    changedKey,
                ->
                if (changedKey == key) {
                    trySend(changedPreferences.getString(key, null))
                }
            }
            preferences.registerOnSharedPreferenceChangeListener(listener)
            trySend(preferences.getString(key, null))
            awaitClose {
                preferences.unregisterOnSharedPreferenceChangeListener(listener)
            }
        }.distinctUntilChanged()
    }

    fun saveChecklistId(context: Context, appWidgetId: Int, checklistId: String): Boolean =
        preferences(context)
            .edit()
            .putString(checklistKey(appWidgetId), checklistId)
            .commit()

    fun remove(context: Context, appWidgetId: Int) {
        preferences(context)
            .edit()
            .remove(checklistKey(appWidgetId))
            .apply()
    }

    private fun checklistKey(appWidgetId: Int) = "$ChecklistKeyPrefix$appWidgetId"

    private fun preferences(context: Context) = context.applicationContext.getSharedPreferences(
        PreferencesName,
        Context.MODE_PRIVATE,
    )
}
