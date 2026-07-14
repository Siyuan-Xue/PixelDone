package com.milesxue.pixeldone.reminder

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.milesxue.pixeldone.R
import com.milesxue.pixeldone.domain.todo.TodoItem
import com.milesxue.pixeldone.domain.todo.TodoPriority
import com.milesxue.pixeldone.ui.theme.ClaudeClayInteractive
import com.milesxue.pixeldone.ui.theme.ClaudeCoral
import com.milesxue.pixeldone.ui.theme.ClaudeGray300
import com.milesxue.pixeldone.ui.theme.ClaudeIvory
import com.milesxue.pixeldone.ui.theme.ClaudeOat
import com.milesxue.pixeldone.ui.theme.ClaudeSlateDark
import com.milesxue.pixeldone.ui.theme.ClaudeSlateLight
import com.milesxue.pixeldone.ui.theme.PixelDoneTheme
import com.milesxue.pixeldone.ui.theme.PixelError
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class XHighAlarmActivity : ComponentActivity() {
    private val todoIds: List<String> by lazy {
        intent.todoIds()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAlarmWindow()

        val titles = intent.todoTitles(todoIds.size)
        val firedDueAtMillis = intent.getLongExtra(
            TodoAlarmScheduler.EXTRA_TODO_DUE_AT,
            System.currentTimeMillis(),
        )

        setContent {
            PixelDoneTheme {
                XHighAlarmScreen(
                    titles = titles,
                    dueTime = firedDueAtMillis.formatAlarmScreenTime(),
                    onStop = {
                        startService(actionIntent(ACTION_STOP_ALARM))
                        finish()
                    },
                    onSnooze = {
                        startService(actionIntent(ACTION_SNOOZE_ALARM))
                        finish()
                    },
                )
            }
        }
    }

    private fun configureAlarmWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON,
        )
    }

    private fun actionIntent(action: String): Intent {
        val sourceIntent = intent
        return Intent(this, XHighAlarmService::class.java).apply {
            this.action = action
            putStringArrayListExtra(XHighAlarmService.EXTRA_BATCH_TODO_IDS, ArrayList(todoIds))
            todoIds.firstOrNull()?.let { putExtra(TodoAlarmScheduler.EXTRA_TODO_ID, it) }
            putExtra(
                TodoAlarmScheduler.EXTRA_TODO_DUE_AT,
                sourceIntent.getLongExtra(TodoAlarmScheduler.EXTRA_TODO_DUE_AT, 0L),
            )
            copyStringArrayListExtraFrom(sourceIntent, XHighAlarmService.EXTRA_COMPANION_TODO_IDS)
            copyStringArrayListExtraFrom(sourceIntent, XHighAlarmService.EXTRA_COMPANION_TODO_TITLES)
            copyStringArrayListExtraFrom(sourceIntent, XHighAlarmService.EXTRA_COMPANION_TODO_PRIORITIES)
            copyStringArrayListExtraFrom(sourceIntent, XHighAlarmService.EXTRA_COMPANION_TODO_REPEATS)
        }
    }

    private fun Intent.copyStringArrayListExtraFrom(source: Intent, key: String) {
        source.getStringArrayListExtra(key)?.let { values ->
            putStringArrayListExtra(key, values)
        }
    }

    private fun Intent.todoIds(): List<String> {
        val batchIds = getStringArrayListExtra(XHighAlarmService.EXTRA_BATCH_TODO_IDS)
            ?.filter { it.isNotBlank() }
            .orEmpty()
        if (batchIds.isNotEmpty()) return batchIds

        return getStringExtra(TodoAlarmScheduler.EXTRA_TODO_ID)
            ?.takeIf { it.isNotBlank() }
            ?.let(::listOf)
            .orEmpty()
    }

    private fun Intent.todoTitles(count: Int): List<String> {
        val batchTitles = getStringArrayListExtra(XHighAlarmService.EXTRA_BATCH_TODO_TITLES)
            ?.map { title -> title.takeIf { it.isNotBlank() } ?: getString(R.string.todo_due) }
            .orEmpty()
        val fallbackTitle = getStringExtra(TodoAlarmScheduler.EXTRA_TODO_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: getString(R.string.todo_due)
        val titles = if (batchTitles.isNotEmpty()) batchTitles else listOf(fallbackTitle)
        return List(maxOf(count, titles.size, 1)) { index -> titles.getOrNull(index) ?: getString(R.string.todo_due) }
    }

    companion object {
        private const val ACTION_STOP_ALARM = XHighAlarmService.ACTION_STOP_ALARM
        private const val ACTION_SNOOZE_ALARM = XHighAlarmService.ACTION_SNOOZE_ALARM

        fun pendingIntent(
            context: Context,
            item: TodoItem,
            firedDueAtMillis: Long,
        ): PendingIntent = pendingIntent(context, listOf(item), firedDueAtMillis)

        fun pendingIntent(
            context: Context,
            items: List<TodoItem>,
            firedDueAtMillis: Long,
            companionShortItems: List<TodoItem> = emptyList(),
        ): PendingIntent {
            val safeItems = items.takeIf { it.isNotEmpty() }
                ?: listOf(
                    TodoItem(
                        id = "xhigh-alarm",
                        title = context.getString(R.string.todo_due),
                        priority = TodoPriority.XHIGH,
                        dueAtMillis = firedDueAtMillis,
                        completed = false,
                        createdAtMillis = firedDueAtMillis,
                    ),
                )
            val intent = Intent(context, XHighAlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putStringArrayListExtra(XHighAlarmService.EXTRA_BATCH_TODO_IDS, ArrayList(safeItems.map { it.id }))
                putStringArrayListExtra(XHighAlarmService.EXTRA_BATCH_TODO_TITLES, ArrayList(safeItems.map { it.title }))
                putCompanionShortItems(companionShortItems)
                putExtra(TodoAlarmScheduler.EXTRA_TODO_ID, safeItems.first().id)
                putExtra(TodoAlarmScheduler.EXTRA_TODO_TITLE, safeItems.first().title)
                putExtra(TodoAlarmScheduler.EXTRA_TODO_DUE_AT, firedDueAtMillis)
            }
            return PendingIntent.getActivity(
                context,
                batchRequestCode(safeItems, firedDueAtMillis),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun batchRequestCode(items: List<TodoItem>, firedDueAtMillis: Long): Int {
            return items.map { it.id }.sorted().joinToString("|").hashCode() xor firedDueAtMillis.hashCode()
        }
    }
}

@Composable
private fun XHighAlarmScreen(
    titles: List<String>,
    dueTime: String,
    onStop: () -> Unit,
    onSnooze: () -> Unit,
) {
    val safeTitles = titles.ifEmpty { listOf(stringResource(R.string.todo_due)) }
    val selectedIndexState = remember { mutableStateOf(0) }
    val selectedIndex = selectedIndexState.value.coerceIn(0, safeTitles.lastIndex)
    val title = safeTitles[selectedIndex]
    val count = safeTitles.size

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = ClaudeSlateDark,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, ClaudeClayInteractive, RectangleShape)
                    .background(ClaudeIvory)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = pluralStringResource(R.plurals.xhigh_tasks_due, count, count),
                    color = PixelError,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = title,
                    color = ClaudeSlateDark,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                if (count > 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.alarm_task_position, selectedIndex + 1, count),
                        color = ClaudeSlateLight,
                        fontSize = 12.sp,
                        letterSpacing = 0.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AlarmActionButton(
                            text = "<",
                            onClick = {
                                selectedIndexState.value = (selectedIndex - 1).coerceAtLeast(0)
                            },
                            modifier = Modifier.weight(1f),
                            primary = false,
                            enabled = selectedIndex > 0,
                        )
                        AlarmActionButton(
                            text = ">",
                            onClick = {
                                selectedIndexState.value = (selectedIndex + 1).coerceAtMost(safeTitles.lastIndex)
                            },
                            modifier = Modifier.weight(1f),
                            primary = false,
                            enabled = selectedIndex < safeTitles.lastIndex,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = dueTime,
                    color = ClaudeSlateLight,
                    fontSize = 13.sp,
                    letterSpacing = 0.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AlarmActionButton(
                        text = stringResource(if (count == 1) R.string.snooze_10 else R.string.snooze_all_10),
                        onClick = onSnooze,
                        modifier = Modifier.weight(1f),
                        primary = false,
                    )
                    AlarmActionButton(
                        text = stringResource(if (count == 1) R.string.stop else R.string.stop_all),
                        onClick = onStop,
                        modifier = Modifier.weight(1f),
                        primary = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun AlarmActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled,
        shape = RectangleShape,
        border = BorderStroke(1.dp, if (primary) PixelError else ClaudeGray300),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) ClaudeCoral else ClaudeOat,
            contentColor = ClaudeSlateDark,
        ),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            letterSpacing = 0.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun Long.formatAlarmScreenTime(): String {
    return Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
}
