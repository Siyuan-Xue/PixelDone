package com.milesxue.pixeldone.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.updateAll
import androidx.glance.appwidget.provideContent
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
import androidx.glance.text.TextStyle
import com.milesxue.pixeldone.MainActivity
import com.milesxue.pixeldone.R
import com.milesxue.pixeldone.di.pixelDoneAppContainer
import com.milesxue.pixeldone.domain.todo.TodoChecklist
import com.milesxue.pixeldone.domain.todo.TodoItem
import com.milesxue.pixeldone.domain.todo.normalTodos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
        val checklistId = PixelDoneWidgetConfigStore.checklistId(context, appWidgetId)
        val checklist = checklistId?.let { requestedId ->
            context.pixelDoneAppContainer()
                .todoRepository
                .loadTodoState()
                .lists
                .firstOrNull { it.id == requestedId }
        }

        provideContent {
            PixelDoneWidgetContent(
                context = context,
                appWidgetId = appWidgetId,
                checklist = checklist,
                configuredChecklistId = checklistId,
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

    fun requestUpdate(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            PixelDoneWidget().updateAll(appContext)
        }
    }
}

private val Background = ColorProvider(
    day = Color(0xFFD1CFC5),
    night = Color(0xFF5E5D59),
)
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
    val rowLimit = widgetRowLimit(size.height.value)
    val visibleTodos = todos.take(rowLimit)
    val remaining = todos.size - visibleTodos.size
    val reconfigureIntent = Intent(context, PixelDoneWidgetConfigurationActivity::class.java).apply {
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
    }

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Background)
            .padding(2.dp),
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Surface)
                .padding(10.dp),
        ) {
            if (checklist == null) {
                Text(
                    text = context.getString(
                        if (configuredChecklistId == null) {
                            R.string.widget_choose_list
                        } else {
                            R.string.widget_list_missing
                        },
                    ),
                    style = TextStyle(
                        color = PrimaryText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    ),
                    maxLines = 2,
                )
                Spacer(modifier = GlanceModifier.height(8.dp))
                Text(
                    text = context.getString(R.string.widget_reconfigure),
                    modifier = GlanceModifier
                        .background(RaisedSurface)
                        .padding(8.dp)
                        .clickable(actionStartActivity(reconfigureIntent)),
                    style = TextStyle(
                        color = Primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    ),
                    maxLines = 2,
                )
                return@Column
            }

            val openListAction = actionStartActivity(
                MainActivity.openChecklistIntent(context, checklist.id),
            )
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(openListAction),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
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
            }
            Spacer(modifier = GlanceModifier.height(8.dp))

            if (todos.isEmpty()) {
                Text(
                    text = context.getString(R.string.widget_all_done),
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .background(RaisedSurface)
                        .padding(10.dp)
                        .clickable(openListAction),
                    style = TextStyle(
                        color = SecondaryText,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                    ),
                )
            } else {
                visibleTodos.forEach { item ->
                    WidgetTodoRow(
                        context = context,
                        checklist = checklist,
                        item = item,
                    )
                    Spacer(modifier = GlanceModifier.height(4.dp))
                }
                if (remaining > 0) {
                    Text(
                        text = context.getString(R.string.widget_more_items, remaining),
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .clickable(openListAction),
                        style = TextStyle(
                            color = SecondaryText,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                        ),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@androidx.glance.GlanceComposable
@androidx.compose.runtime.Composable
private fun WidgetTodoRow(
    context: Context,
    checklist: TodoChecklist,
    item: TodoItem,
) {
    val openListAction = actionStartActivity(
        MainActivity.openChecklistIntent(context, checklist.id),
    )
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(32.dp)
            .background(RaisedSurface),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .size(32.dp)
                .background(PrimaryText)
                .padding(2.dp)
                .clickable(
                    actionRunCallback<CompleteTodoFromWidgetAction>(
                        actionParametersOf(
                            CompleteTodoFromWidgetAction.ChecklistIdKey to checklist.id,
                            CompleteTodoFromWidgetAction.TodoIdKey to item.id,
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = GlanceModifier
                    .size(28.dp)
                    .background(Surface),
            ) {}
        }
        Text(
            text = item.title,
            modifier = GlanceModifier
                .defaultWeight()
                .padding(horizontal = 8.dp)
                .clickable(openListAction),
            style = TextStyle(
                color = PrimaryText,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
            ),
            maxLines = 1,
        )
    }
}

class CompleteTodoFromWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val checklistId = parameters[ChecklistIdKey] ?: return
        val todoId = parameters[TodoIdKey] ?: return
        val container = context.pixelDoneAppContainer()
        val before = container.todoRepository.loadTodoState()
        var changed = false
        val after = container.todoRepository.updateTodoState { latest ->
            latest.copy(
                lists = latest.lists.map { checklist ->
                    if (checklist.id != checklistId) return@map checklist
                    checklist.copy(
                        items = checklist.items.map { item ->
                            if (item.id == todoId && !item.completed) {
                                changed = true
                                item.copy(completed = true)
                            } else {
                                item
                            }
                        },
                    )
                },
            )
        }
        if (changed) {
            container.reminderScheduler.sync(normalTodos(before), normalTodos(after))
            container.syncCoordinator.requestSync()
        }
        PixelDoneWidget().updateAll(context)
    }

    companion object {
        val ChecklistIdKey = ActionParameters.Key<String>("checklist_id")
        val TodoIdKey = ActionParameters.Key<String>("todo_id")
    }
}
