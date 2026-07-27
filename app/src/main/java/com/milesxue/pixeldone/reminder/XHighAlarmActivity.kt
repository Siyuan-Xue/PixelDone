package com.milesxue.pixeldone.reminder

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
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
import androidx.core.content.ContextCompat
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
    private val alarmPayload = mutableStateOf(AlarmScreenPayload())
    private var alarmFinishedReceiverRegistered = false
    private var actionDispatched = false
    private val alarmFinishedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == XHighAlarmService.ACTION_ALARM_FINISHED) {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAlarmWindow()
        updateAlarmPayload(intent)
        onBackPressedDispatcher.addCallback(this) {
            dispatchAlarmAction(ACTION_SNOOZE_ALARM)
        }

        setContent {
            val payload = alarmPayload.value
            PixelDoneTheme {
                XHighAlarmScreen(
                    titles = payload.titles,
                    dueTime = payload.firedDueAtMillis.formatAlarmScreenTime(),
                    onStop = {
                        dispatchAlarmAction(ACTION_STOP_ALARM)
                    },
                    onSnooze = {
                        dispatchAlarmAction(ACTION_SNOOZE_ALARM)
                    },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!alarmFinishedReceiverRegistered) {
            ContextCompat.registerReceiver(
                this,
                alarmFinishedReceiver,
                IntentFilter(XHighAlarmService.ACTION_ALARM_FINISHED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            alarmFinishedReceiverRegistered = true
        }
    }

    override fun onStop() {
        if (alarmFinishedReceiverRegistered) {
            unregisterReceiver(alarmFinishedReceiver)
            alarmFinishedReceiverRegistered = false
        }
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        actionDispatched = false
        updateAlarmPayload(intent)
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
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun updateAlarmPayload(sourceIntent: Intent) {
        val todoIds = sourceIntent.todoIds()
        alarmPayload.value = AlarmScreenPayload(
            todoIds = todoIds,
            titles = sourceIntent.todoTitles(todoIds.size),
            firedDueAtMillis = sourceIntent.getLongExtra(
                TodoAlarmScheduler.EXTRA_REMINDER_OCCURRENCE_AT,
                sourceIntent.getLongExtra(
                    TodoAlarmScheduler.EXTRA_TODO_DUE_AT,
                    System.currentTimeMillis(),
                ),
            ),
        )
    }

    private fun dispatchAlarmAction(action: String) {
        if (actionDispatched) return
        actionDispatched = true
        startService(actionIntent(action))
        finish()
    }

    private fun actionIntent(action: String): Intent {
        val sourceIntent = intent
        val todoIds = alarmPayload.value.todoIds
        return Intent(this, XHighAlarmService::class.java).apply {
            this.action = action
            putStringArrayListExtra(XHighAlarmService.EXTRA_BATCH_TODO_IDS, ArrayList(todoIds))
            todoIds.firstOrNull()?.let { putExtra(TodoAlarmScheduler.EXTRA_TODO_ID, it) }
            putExtra(
                TodoAlarmScheduler.EXTRA_TODO_DUE_AT,
                alarmPayload.value.firedDueAtMillis,
            )
            putExtra(
                TodoAlarmScheduler.EXTRA_REMINDER_OCCURRENCE_AT,
                alarmPayload.value.firedDueAtMillis,
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
                putExtra(TodoAlarmScheduler.EXTRA_REMINDER_OCCURRENCE_AT, firedDueAtMillis)
            }
            val creatorOptions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                val backgroundStartMode = if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA
                ) {
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                } else {
                    @Suppress("DEPRECATION")
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                }
                ActivityOptions.makeBasic()
                    .setPendingIntentCreatorBackgroundActivityStartMode(
                        backgroundStartMode,
                    )
                    .toBundle()
            } else {
                null
            }
            return PendingIntent.getActivity(
                context,
                batchRequestCode(safeItems, firedDueAtMillis),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                creatorOptions,
            )
        }

        private fun batchRequestCode(items: List<TodoItem>, firedDueAtMillis: Long): Int {
            return items.map { it.id }.sorted().joinToString("|").hashCode() xor firedDueAtMillis.hashCode()
        }
    }
}

private data class AlarmScreenPayload(
    val todoIds: List<String> = emptyList(),
    val titles: List<String> = emptyList(),
    val firedDueAtMillis: Long = System.currentTimeMillis(),
)

@Composable
private fun XHighAlarmScreen(
    titles: List<String>,
    dueTime: String,
    onStop: () -> Unit,
    onSnooze: () -> Unit,
) {
    val safeTitles = titles.ifEmpty { listOf(stringResource(R.string.todo_due)) }
    val selectedIndexState = remember(safeTitles) { mutableStateOf(0) }
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
