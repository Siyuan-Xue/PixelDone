package com.milesxue.pixeldone.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
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
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PixelDoneWidget : GlanceAppWidget() {
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
        val initialChecklistId = PixelDoneWidgetConfigStore.checklistId(context, appWidgetId)

        provideContent {
            val checklistIdFlow = remember(appWidgetId) {
                PixelDoneWidgetConfigStore.checklistIdFlow(context, appWidgetId)
            }
            val selection = observeWidgetChecklist(
                todoStateFlow = repository.state,
                checklistIdFlow = checklistIdFlow,
                initialChecklistId = initialChecklistId,
            )
            PixelDoneWidgetContent(
                context = context,
                appWidgetId = appWidgetId,
                checklist = selection.checklist,
                configuredChecklistId = selection.configuredChecklistId,
            )
        }
    }
}

class PixelDoneWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PixelDoneWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { PixelDoneWidgetConfigStore.remove(context, it) }
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
private val RaisedSurface = ColorProvider(
    day = Color(0xFFF0EEE6),
    night = Color(0xFF30302E),
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
) {
    val size = LocalSize.current
    val todos = checklist?.let(::widgetTodos).orEmpty()
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
                    text = todos.size.toString(),
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
                        .background(RaisedSurface)
                        .padding(10.dp),
                    style = TextStyle(
                        color = SecondaryText,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                    ),
                )
            } else {
                LazyColumn(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight(),
                ) {
                    items(
                        items = todos,
                        itemId = { item -> widgetItemId(item.id) },
                    ) { item ->
                        WidgetTodoRow(
                            checklist = checklist,
                            item = item,
                        )
                    }
                }
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
internal fun WidgetTodoRow(
    checklist: TodoChecklist,
    item: TodoItem,
) {
    CheckBox(
        checked = false,
        onCheckedChange = actionRunCallback<CompleteTodoFromWidgetAction>(
            widgetCompletionParameters(
                checklistId = checklist.id,
                todoId = item.id,
            ),
        ),
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(bottom = 4.dp)
            .background(RaisedSurface),
        text = item.title,
        style = TextStyle(
            color = PrimaryText,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
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
    checklistIdFlow: Flow<String?>,
    initialChecklistId: String?,
): ObservedWidgetChecklist {
    val todoState by todoStateFlow.collectAsState()
    val configuredChecklistId by checklistIdFlow.collectAsState(initial = initialChecklistId)
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
        val checklistId = parameters[ChecklistIdKey] ?: return
        val todoId = parameters[TodoIdKey] ?: return
        try {
            val container = context.pixelDoneAppContainer()
            var before = container.todoRepository.loadTodoState()
            var changed = false
            val after = container.todoRepository.updateTodoState { latest ->
                before = latest
                completeWidgetTodo(
                    state = latest,
                    checklistId = checklistId,
                    todoId = todoId,
                ).also { result ->
                    changed = result.changed
                }.state
            }
            if (changed) {
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
        } finally {
            try {
                PixelDoneWidget().update(context, glanceId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(LogTag, "Unable to refresh widget after completion action", error)
            }
        }
    }

    companion object {
        val ChecklistIdKey = ActionParameters.Key<String>("checklist_id")
        val TodoIdKey = ActionParameters.Key<String>("todo_id")
        private const val LogTag = "PixelDoneWidget"
    }
}
