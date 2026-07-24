package com.milesxue.pixeldone.widget

import android.content.Context

internal object PixelDoneWidgetConfigStore {
    private const val PreferencesName = "pixeldone_widget_config"
    private const val ChecklistKeyPrefix = "checklist_"

    fun checklistId(context: Context, appWidgetId: Int): String? =
        preferences(context).getString("$ChecklistKeyPrefix$appWidgetId", null)

    fun saveChecklistId(context: Context, appWidgetId: Int, checklistId: String) {
        preferences(context)
            .edit()
            .putString("$ChecklistKeyPrefix$appWidgetId", checklistId)
            .apply()
    }

    fun remove(context: Context, appWidgetId: Int) {
        preferences(context)
            .edit()
            .remove("$ChecklistKeyPrefix$appWidgetId")
            .apply()
    }

    private fun preferences(context: Context) = context.applicationContext.getSharedPreferences(
        PreferencesName,
        Context.MODE_PRIVATE,
    )
}
