package com.milesxue.pixeldone.widget

import android.content.Context

internal object PixelDoneWidgetConfigStore {
    private const val PreferencesName = "pixeldone_widget_config"
    private const val ChecklistKeyPrefix = "checklist_"

    fun legacyChecklistId(context: Context, appWidgetId: Int): String? =
        preferences(context).getString(checklistKey(appWidgetId), null)

    fun clearLegacy(context: Context, appWidgetId: Int): Boolean =
        preferences(context)
            .edit()
            .remove(checklistKey(appWidgetId))
            .commit()

    private fun checklistKey(appWidgetId: Int) = "$ChecklistKeyPrefix$appWidgetId"

    private fun preferences(context: Context) = context.applicationContext.getSharedPreferences(
        PreferencesName,
        Context.MODE_PRIVATE,
    )
}
