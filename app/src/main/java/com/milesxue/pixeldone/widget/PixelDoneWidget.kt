package com.milesxue.pixeldone.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.CheckboxDefaults
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.ToggleableStateKey
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import com.milesxue.pixeldone.R
import com.milesxue.pixeldone.di.pixelDoneAppContainer
import com.milesxue.pixeldone.domain.todo.TodoChecklist
import com.milesxue.pixeldone.domain.todo.TodoChecklistState
import com.milesxue.pixeldone.domain.todo.TodoItem
import com.milesxue.pixeldone.domain.todo.normalTodos
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class PixelDoneWidget : GlanceAppWidget() {
    override val stateDefinition = PixelDoneWidgetStateStore.definition

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(110.dp, 110.dp),
            DpSize(180.dp, 180.dp),
            DpSize(250.dp, 250.dp),
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val repository = context.pixelDoneAppContainer().todoRepository
        repository.loadTodoState()

        provideContent {
            val preferences = currentState<Preferences>()
            val configuredChecklistId = PixelDoneWidgetStateStore.selectedChecklistId(
                context = context,
                appWidgetId = appWidgetId,
                preferences = preferences,
            )
            val selection = observeWidgetChecklist(
                todoStateFlow = repository.state,
                configuredChecklistId = configuredChecklistId,
            )
            PixelDoneWidgetContent(
                context = context,
                appWidgetId = appWidgetId,
                checklist = selection.checklist,
                configuredChecklistId = selection.configuredChecklistId,
                completionFeedback = PixelDoneWidgetStateStore.completionFeedback(preferences),
            )
        }
    }
}

class PixelDoneWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PixelDoneWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { PixelDoneWidgetConfigStore.clearLegacy(context, it) }
        super.onDeleted(context, appWidgetIds)
    }
}

object PixelDoneWidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun requestUpdateAll(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            PixelDoneWidget().updateAll(appContext)
        }
    }
}

private val Surface = ColorProvider(
    day = Color(0xFFFAF9F5),
    night = Color(0xFF1F1E1D),
)
private val Primary = ColorProvider(
    day = Color(0xFFD97757),
    night = Color(0xFFD97757),
)
private val PrimaryText = ColorProvider(
    day = Color(0xFF141413),
    night = Color(0xFFFAF9F5),
)
private val SecondaryText = ColorProvider(
    day = Color(0xFF5E5D59),
    night = Color(0xFFC2C0B6),
)
private val Success = ColorProvider(
    day = Color(0xFF629987),
    night = Color(0xFF629987),
)
private val OnPrimary = ColorProvider(
    day = Color(0xFF141413),
    night = Color(0xFF141413),
)

@androidx.glance.GlanceComposable
@androidx.compose.runtime.Composable
private fun PixelDoneWidgetContent(
    context: Context,
    appWidgetId: Int,
    checklist: TodoChecklist?,
    configuredChecklistId: String?,
    completionFeedback: List<WidgetCompletionFeedback>,
) {
    val size = LocalSize.current
    val completingTodoIds = checklist
        ?.let { selectedChecklist ->
            completionFeedback
                .asSequence()
                .filter { feedback -> feedback.checklistId == selectedChecklist.id }
                .mapTo(mutableSetOf()) { feedback -> feedback.todoId }
        }
        .orEmpty()
    val todos = checklist
        ?.let { selectedChecklist ->
            widgetTodosWithFeedback(
                checklist = selectedChecklist,
                completingTodoIds = completingTodoIds,
            )
        }
        .orEmpty()
    val remainingTodoCount = checklist
        ?.let { selectedChecklist ->
            widgetRemainingTodoCount(
                checklist = selectedChecklist,
                completingTodoIds = completingTodoIds,
            )
        }
        ?: 0
    val reconfigureAction = actionStartActivity(
        widgetConfigurationIntent(context, appWidgetId),
    )

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Surface),
    ) {
        if (checklist == null) {
            EmptyWidgetContent(
                context = context,
                missingList = configuredChecklistId != null,
                compact = size.height.value < 220f || size.width.value < 180f,
                modifier = GlanceModifier.clickable(reconfigureAction),
            )
            return@Box
        }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(10.dp),
        ) {
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clickable(reconfigureAction),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.pixeldone_widget_list_icon),
                    contentDescription = null,
                    modifier = GlanceModifier.size(20.dp),
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
                Text(
                    text = checklist.name,
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(
                        color = PrimaryText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    ),
                    maxLines = 1,
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
                Text(
                    text = remainingTodoCount.toString(),
                    style = TextStyle(
                        color = Primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    ),
                    maxLines = 1,
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
                Image(
                    provider = ImageProvider(R.drawable.pixeldone_widget_switch_icon),
                    contentDescription = context.getString(R.string.widget_reconfigure),
                    modifier = GlanceModifier.size(20.dp),
                )
            }
            Spacer(modifier = GlanceModifier.height(4.dp))

            if (todos.isEmpty()) {
                Text(
                    text = context.getString(R.string.widget_all_done),
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    style = TextStyle(
                        color = SecondaryText,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                    ),
                )
            } else {
                WidgetTodoList(
                    checklist = checklist,
                    todos = todos,
                    completingTodoIds = completingTodoIds,
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight(),
                )
            }
        }
    }
}

@androidx.glance.GlanceComposable
@androidx.compose.runtime.Composable
private fun EmptyWidgetContent(
    context: Context,
    missingList: Boolean,
    compact: Boolean,
    modifier: GlanceModifier = GlanceModifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        ) {
            Image(
                provider = ImageProvider(R.drawable.pixeldone_widget_list_icon),
                contentDescription = context.getString(R.string.widget_list_icon_description),
                modifier = GlanceModifier.size(if (compact) 32.dp else 48.dp),
            )
            Spacer(modifier = GlanceModifier.height(if (compact) 8.dp else 12.dp))
            Text(
                text = context.getString(
                    if (missingList) {
                        R.string.widget_list_missing
                    } else {
                        R.string.widget_choose_list
                    },
                ),
                modifier = GlanceModifier.fillMaxWidth(),
                style = TextStyle(
                    color = PrimaryText,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (compact) 14.sp else 16.sp,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 2,
            )
            if (!compact) {
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = context.getString(
                        if (missingList) {
                            R.string.widget_list_missing_detail
                        } else {
                            R.string.widget_empty_detail
                        },
                    ),
                    modifier = GlanceModifier.fillMaxWidth(),
                    style = TextStyle(
                        color = SecondaryText,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 2,
                )
                Spacer(modifier = GlanceModifier.height(12.dp))
                Row(
                    modifier = GlanceModifier
                        .background(Primary)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Vertical.CenterVertically,
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.pixeldone_widget_arrow_icon),
                        contentDescription = null,
                        modifier = GlanceModifier.size(16.dp),
                    )
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Text(
                        text = context.getString(
                            if (missingList) {
                                R.string.widget_reconfigure
                            } else {
                                R.string.widget_select_list
                            },
                        ),
                        style = TextStyle(
                            color = OnPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        ),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

internal fun widgetConfigurationIntent(context: Context, appWidgetId: Int): Intent =
    Intent(context, PixelDoneWidgetConfigurationActivity::class.java).apply {
        action = AppWidgetManager.ACTION_APPWIDGET_CONFIGURE
        data = Uri.Builder()
            .scheme("pixeldone")
            .authority("widget")
            .appendPath("configure")
            .appendPath(appWidgetId.toString())
            .build()
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
        )
    }

@androidx.glance.GlanceComposable
@androidx.compose.runtime.Composable
internal fun WidgetTodoList(
    checklist: TodoChecklist,
    todos: List<TodoItem>,
    completingTodoIds: Set<String> = emptySet(),
    modifier: GlanceModifier = GlanceModifier,
) {
    LazyColumn(modifier = modifier) {
        items(
            items = todos,
            itemId = { item -> widgetItemId(item.id) },
        ) { item ->
            WidgetTodoRow(
                checklist = checklist,
                item = item,
                completing = item.id in completingTodoIds,
            )
        }
    }
}

@androidx.glance.GlanceComposable
@androidx.compose.runtime.Composable
internal fun WidgetTodoRow(
    checklist: TodoChecklist,
    item: TodoItem,
    completing: Boolean = false,
) {
    val completionAction: Action? = if (completing) {
        null
    } else {
        actionRunCallback<CompleteTodoFromWidgetAction>(
            widgetCompletionParameters(
                checklistId = checklist.id,
                todoId = item.id,
            ),
        )
    }
    CheckBox(
        checked = completing,
        onCheckedChange = completionAction,
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(bottom = 4.dp),
        text = item.title,
        style = TextStyle(
            color = if (completing) SecondaryText else PrimaryText,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            textDecoration = if (completing) TextDecoration.LineThrough else null,
        ),
        colors = CheckboxDefaults.colors(
            checkedColor = Success,
            uncheckedColor = SecondaryText,
        ),
        maxLines = 1,
    )
}

internal fun widgetCompletionParameters(
    checklistId: String,
    todoId: String,
): ActionParameters = actionParametersOf(
    CompleteTodoFromWidgetAction.ChecklistIdKey to checklistId,
    CompleteTodoFromWidgetAction.TodoIdKey to todoId,
)

internal data class ObservedWidgetChecklist(
    val configuredChecklistId: String?,
    val checklist: TodoChecklist?,
)

@Composable
internal fun observeWidgetChecklist(
    todoStateFlow: StateFlow<TodoChecklistState>,
    configuredChecklistId: String?,
): ObservedWidgetChecklist {
    val todoState by todoStateFlow.collectAsState()
    return ObservedWidgetChecklist(
        configuredChecklistId = configuredChecklistId,
        checklist = configuredChecklistId?.let { requestedId ->
            todoState.lists.firstOrNull { it.id == requestedId }
        },
    )
}

class CompleteTodoFromWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        if (parameters[ToggleableStateKey] == false) return
        val checklistId = parameters[ChecklistIdKey] ?: return
        val todoId = parameters[TodoIdKey] ?: return
        val appContext = context.applicationContext
        val container = appContext.pixelDoneAppContainer()
        val initialState = try {
            container.todoRepository.loadTodoState()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(LogTag, "Unable to read todo before widget completion", error)
            return
        }
        val target = initialState.lists
            .firstOrNull { checklist -> checklist.id == checklistId }
            ?.items
            ?.firstOrNull { item -> item.id == todoId }
        if (target == null || target.completed) return

        val startedAtMillis = System.currentTimeMillis()
        val feedback = WidgetCompletionFeedback(
            checklistId = checklistId,
            todoId = todoId,
            token = UUID.randomUUID().toString(),
            expiresAtMillis = startedAtMillis + FeedbackSetupTimeoutMillis,
        )
        val feedbackAdded = try {
            PixelDoneWidgetStateStore.addCompletionFeedback(
                context = appContext,
                glanceId = glanceId,
                feedback = feedback,
                nowMillis = startedAtMillis,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(LogTag, "Unable to persist widget completion feedback", error)
            false
        }
        if (!feedbackAdded) return

        val appWidgetId = runCatching {
            GlanceAppWidgetManager(appContext).getAppWidgetId(glanceId)
        }.onFailure { error ->
            Log.e(LogTag, "Unable to resolve widget completion cleanup target", error)
        }.getOrNull()
        appWidgetId?.let { resolvedAppWidgetId ->
            runCatching {
                WidgetCompletionFeedbackCleanupScheduler(appContext).schedule(
                    appWidgetId = resolvedAppWidgetId,
                    feedback = feedback,
                    nowMillis = startedAtMillis,
                )
            }.onFailure { error ->
                Log.e(LogTag, "Unable to schedule provisional widget feedback cleanup", error)
            }
        }
        refreshWidget(appContext, glanceId, "show completion feedback")
        val visibleUntilMillis = System.currentTimeMillis() + CompletionFeedbackDurationMillis

        var before = initialState
        var changed = false
        val after = try {
            container.todoRepository.updateTodoState { latest ->
                before = latest
                completeWidgetTodo(
                    state = latest,
                    checklistId = checklistId,
                    todoId = todoId,
                ).also { result ->
                    changed = result.changed
                }.state
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(LogTag, "Unable to complete todo from widget", error)
            rollbackFeedback(appContext, glanceId, feedback)
            return
        }

        if (changed) {
            WidgetCompletionHaptics(appContext).perform()
            runCatching {
                container.reminderScheduler.sync(normalTodos(before), normalTodos(after))
            }.onFailure { error ->
                Log.e(LogTag, "Unable to reschedule reminders after widget completion", error)
            }
            runCatching {
                container.syncCoordinator.requestSync()
            }.onFailure { error ->
                Log.e(LogTag, "Unable to request sync after widget completion", error)
            }
        }

        val completionFinishedAtMillis = System.currentTimeMillis()
        val finalExpiryMillis = widgetCompletionFeedbackFinalExpiryMillis(
            visibleUntilMillis = visibleUntilMillis,
            completionFinishedAtMillis = completionFinishedAtMillis,
        )
        val finalFeedback = try {
            PixelDoneWidgetStateStore.updateCompletionFeedbackExpiry(
                context = appContext,
                glanceId = glanceId,
                feedback = feedback,
                expiresAtMillis = finalExpiryMillis,
                nowMillis = completionFinishedAtMillis,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(LogTag, "Unable to finalize widget completion feedback expiry", error)
            null
        }
        if (finalFeedback == null) {
            rollbackFeedback(appContext, glanceId, feedback)
            return
        }
        appWidgetId?.let { resolvedAppWidgetId ->
            runCatching {
                WidgetCompletionFeedbackCleanupScheduler(appContext).schedule(
                    appWidgetId = resolvedAppWidgetId,
                    feedback = finalFeedback,
                    nowMillis = completionFinishedAtMillis,
                )
            }.onFailure { error ->
                Log.e(LogTag, "Unable to schedule final widget feedback cleanup", error)
            }
        }

        try {
            val remainingMillis = widgetCompletionFeedbackDelayMillis(
                expiresAtMillis = finalFeedback.expiresAtMillis,
                nowMillis = System.currentTimeMillis(),
            )
            if (remainingMillis > 0L) {
                delay(remainingMillis)
            }
            val cleared = PixelDoneWidgetStateStore.removeExpiredCompletionFeedback(
                context = appContext,
                glanceId = glanceId,
                feedback = finalFeedback,
            )
            if (cleared) {
                refreshWidget(appContext, glanceId, "remove completion feedback")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // The persistent cleanup worker remains responsible if this in-process cleanup fails.
            Log.e(LogTag, "Unable to clear widget completion feedback on time", error)
        }
    }

    companion object {
        val ChecklistIdKey = ActionParameters.Key<String>("checklist_id")
        val TodoIdKey = ActionParameters.Key<String>("todo_id")
        private const val CompletionFeedbackDurationMillis = 2_000L
        private const val FeedbackSetupTimeoutMillis = 30_000L
        private const val LogTag = "PixelDoneWidget"
    }

    private suspend fun rollbackFeedback(
        context: Context,
        glanceId: GlanceId,
        feedback: WidgetCompletionFeedback,
    ) {
        runCatching {
            PixelDoneWidgetStateStore.removeCompletionFeedback(
                context = context,
                glanceId = glanceId,
                checklistId = feedback.checklistId,
                todoId = feedback.todoId,
                token = feedback.token,
            )
            refreshWidget(context, glanceId, "roll back completion feedback")
        }.onFailure { error ->
            Log.e(LogTag, "Unable to roll back widget completion feedback", error)
        }
    }

    private suspend fun refreshWidget(
        context: Context,
        glanceId: GlanceId,
        reason: String,
    ) {
        try {
            PixelDoneWidget().update(context, glanceId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(LogTag, "Unable to $reason", error)
        }
    }
}
