package com.milesxue.pixeldone.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.appcompat.app.AppCompatActivity
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import com.milesxue.pixeldone.R
import com.milesxue.pixeldone.applyPixelDoneLanguage
import com.milesxue.pixeldone.applyPixelDoneSystemBars
import com.milesxue.pixeldone.di.pixelDoneAppContainer
import com.milesxue.pixeldone.domain.todo.TodoChecklist
import com.milesxue.pixeldone.ui.theme.PixelDoneColors
import com.milesxue.pixeldone.ui.theme.PixelDoneTheme
import com.milesxue.pixeldone.ui.theme.PixelTextRole
import com.milesxue.pixeldone.ui.theme.scriptAwareText
import com.milesxue.pixeldone.ui.todo.components.PixelButton
import kotlinx.coroutines.launch

class PixelDoneWidgetConfigurationActivity : AppCompatActivity() {
    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private var glanceId: GlanceId? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        applyPixelDoneLanguage(pixelDoneAppContainer().settingsStore.loadSettings().languageMode)
        super.onCreate(savedInstanceState)

        appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setResult(
            Activity.RESULT_CANCELED,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
        )

        val container = pixelDoneAppContainer()
        val checklists = widgetChecklists(container.todoRepository.loadTodoState())
        val darkTheme = container.settingsStore.loadSettings().darkTheme
        applyPixelDoneSystemBars(darkTheme)

        lifecycleScope.launch {
            val resolvedGlanceId = GlanceAppWidgetManager(
                this@PixelDoneWidgetConfigurationActivity,
            ).getGlanceIdBy(intent)
            if (resolvedGlanceId == null) {
                Log.e(
                    WidgetConfigurationLogTag,
                    "Unable to resolve Glance ID for appWidgetId=$appWidgetId",
                )
                finish()
                return@launch
            }
            glanceId = resolvedGlanceId
            val configuredChecklistId = runCatching {
                val preferences = PixelDoneWidgetStateStore.readPreferences(
                    context = this@PixelDoneWidgetConfigurationActivity,
                    glanceId = resolvedGlanceId,
                )
                PixelDoneWidgetStateStore.selectedChecklistId(
                    context = this@PixelDoneWidgetConfigurationActivity,
                    appWidgetId = appWidgetId,
                    preferences = preferences,
                )
            }.onFailure { error ->
                Log.e(
                    WidgetConfigurationLogTag,
                    "Unable to read widget configuration for appWidgetId=$appWidgetId",
                    error,
                )
            }.getOrElse {
                PixelDoneWidgetConfigStore.legacyChecklistId(
                    context = this@PixelDoneWidgetConfigurationActivity,
                    appWidgetId = appWidgetId,
                )
            }
            val initialChecklistId = configuredWidgetChecklistId(
                checklists = checklists,
                configuredChecklistId = configuredChecklistId,
            )

            setContent {
                PixelDoneTheme(darkTheme = darkTheme) {
                    WidgetConfigurationScreen(
                        checklists = checklists,
                        initialChecklistId = initialChecklistId,
                        onSave = ::saveConfiguration,
                    )
                }
            }
        }
    }

    private suspend fun saveConfiguration(checklistId: String): Boolean {
        val targetGlanceId = glanceId ?: return false
        var previousState: PixelDoneWidgetStateSnapshot? = null
        return try {
            previousState = PixelDoneWidgetStateStore.replaceConfiguration(
                context = this@PixelDoneWidgetConfigurationActivity,
                glanceId = targetGlanceId,
                checklistId = checklistId,
            )
            PixelDoneWidget().update(
                context = this@PixelDoneWidgetConfigurationActivity,
                id = targetGlanceId,
            )
            if (
                !PixelDoneWidgetConfigStore.clearLegacy(
                    context = this@PixelDoneWidgetConfigurationActivity,
                    appWidgetId = appWidgetId,
                )
            ) {
                Log.w(
                    WidgetConfigurationLogTag,
                    "Unable to clear legacy configuration for appWidgetId=$appWidgetId",
                )
            }
            setResult(
                Activity.RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
            )
            finish()
            true
        } catch (error: Exception) {
            previousState?.let { snapshot ->
                runCatching {
                    PixelDoneWidgetStateStore.restore(
                        context = this@PixelDoneWidgetConfigurationActivity,
                        glanceId = targetGlanceId,
                        snapshot = snapshot,
                    )
                    PixelDoneWidget().update(
                        context = this@PixelDoneWidgetConfigurationActivity,
                        id = targetGlanceId,
                    )
                }.onFailure { rollbackError ->
                    Log.e(
                        WidgetConfigurationLogTag,
                        "Unable to roll back widget configuration for appWidgetId=$appWidgetId",
                        rollbackError,
                    )
                }
            }
            Log.e(
                WidgetConfigurationLogTag,
                "Unable to save widget configuration for appWidgetId=$appWidgetId",
                error,
            )
            false
        }
    }

    private companion object {
        const val WidgetConfigurationLogTag = "PixelDoneWidgetConfig"
    }
}

internal fun configuredWidgetChecklistId(
    checklists: List<TodoChecklist>,
    configuredChecklistId: String?,
): String? = configuredChecklistId?.takeIf { configuredId ->
    checklists.any { it.id == configuredId }
}

@Composable
private fun WidgetConfigurationScreen(
    checklists: List<TodoChecklist>,
    initialChecklistId: String?,
    onSave: suspend (String) -> Boolean,
) {
    val colors = PixelDoneColors.current
    var selectedChecklistId by remember(initialChecklistId) { mutableStateOf(initialChecklistId) }
    var isSaving by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.widget_choose_list),
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary,
        )
        Text(
            text = if (checklists.isEmpty()) {
                stringResource(R.string.widget_no_lists)
            } else {
                stringResource(R.string.widget_choose_list_detail)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(checklists, key = { it.id }) { checklist ->
                val selected = checklist.id == selectedChecklistId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (selected) colors.selectedSurface else colors.surface,
                            RectangleShape,
                        )
                        .border(
                            width = 2.dp,
                            color = if (selected) colors.primary else colors.border,
                            shape = RectangleShape,
                        )
                        .clickable(enabled = !isSaving) {
                            selectedChecklistId = checklist.id
                            saveFailed = false
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = scriptAwareText(checklist.name, PixelTextRole.SERIF),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = checklist.items.count { !it.completed }.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) colors.primary else colors.textSecondary,
                    )
                }
            }
        }
        if (saveFailed) {
            Text(
                text = stringResource(R.string.widget_save_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.error,
            )
        }
        PixelButton(
            text = stringResource(
                if (isSaving) R.string.widget_saving else R.string.widget_show_list,
            ),
            onClick = {
                val checklistId = selectedChecklistId ?: return@PixelButton
                isSaving = true
                saveFailed = false
                scope.launch {
                    if (!onSave(checklistId)) {
                        isSaving = false
                        saveFailed = true
                    }
                }
            },
            enabled = selectedChecklistId != null && !isSaving,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
