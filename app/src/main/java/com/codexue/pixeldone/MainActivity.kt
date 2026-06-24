package com.codexue.pixeldone

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.verticalScroll
import com.codexue.pixeldone.ui.theme.ClaudeCactus
import com.codexue.pixeldone.ui.theme.ClaudeClay
import com.codexue.pixeldone.ui.theme.ClaudeClayInteractive
import com.codexue.pixeldone.ui.theme.ClaudeCoral
import com.codexue.pixeldone.ui.theme.ClaudeFig
import com.codexue.pixeldone.ui.theme.ClaudeGray100
import com.codexue.pixeldone.ui.theme.ClaudeGray200
import com.codexue.pixeldone.ui.theme.ClaudeGray300
import com.codexue.pixeldone.ui.theme.ClaudeGray600
import com.codexue.pixeldone.ui.theme.ClaudeIvory
import com.codexue.pixeldone.ui.theme.ClaudeIvoryDark
import com.codexue.pixeldone.ui.theme.ClaudeIvoryMedium
import com.codexue.pixeldone.ui.theme.ClaudeMineral
import com.codexue.pixeldone.ui.theme.ClaudeOat
import com.codexue.pixeldone.ui.theme.ClaudeSky
import com.codexue.pixeldone.ui.theme.ClaudeSlateDark
import com.codexue.pixeldone.ui.theme.ClaudeSlateLight
import com.codexue.pixeldone.ui.theme.PixelDoneTheme
import com.codexue.pixeldone.ui.theme.PixelError
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.delay

private const val CompletionSortDelayMillis = 1_500L

private sealed interface DeleteConfirmation {
    data class SingleTodo(val id: String, val title: String) : DeleteConfirmation
    data class CompletedTodos(val count: Int) : DeleteConfirmation
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PixelDoneTheme {
                PixelDoneApp()
            }
        }
    }
}

@Composable
private fun PixelDoneApp() {
    val context = LocalContext.current
    val storage = remember { TodoPreferences.create(context) }
    var todos by remember { mutableStateOf(storage.loadTodos()) }
    var titleInput by remember { mutableStateOf("") }
    var selectedPriority by remember { mutableStateOf(TodoPriority.MEDIUM) }
    var dueAtMillis by remember { mutableStateOf(defaultDueAtMillis()) }
    var sortMode by remember { mutableStateOf(SortMode.PRIORITY) }
    var hideCompleted by remember { mutableStateOf(false) }
    var editorExpanded by remember { mutableStateOf(false) }
    var editingTodoId by remember { mutableStateOf<String?>(null) }
    var displayOrderIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var sortDelayUntilMillis by remember { mutableStateOf(0L) }
    var deleteConfirmation by remember { mutableStateOf<DeleteConfirmation?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            TodoAlarmScheduler.sync(context, emptyList(), todos)
        }
    }

    LaunchedEffect(Unit) {
        TodoAlarmScheduler.sync(context, emptyList(), todos)
    }

    val sortedVisibleIds = visibleTodos(todos, sortMode, hideCompleted).map { it.id }

    LaunchedEffect(sortedVisibleIds, sortDelayUntilMillis) {
        if (sortDelayUntilMillis == 0L) {
            displayOrderIds = sortedVisibleIds
        }
    }

    LaunchedEffect(sortDelayUntilMillis) {
        if (sortDelayUntilMillis > 0L) {
            val remainingDelay = sortDelayUntilMillis - System.currentTimeMillis()
            if (remainingDelay > 0L) {
                delay(remainingDelay)
            }
            sortDelayUntilMillis = 0L
        }
    }

    fun updateTodos(updatedTodos: List<TodoItem>) {
        val previousTodos = todos
        todos = updatedTodos
        storage.saveTodos(updatedTodos)
        TodoAlarmScheduler.sync(context, previousTodos, updatedTodos)
    }

    fun hasNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun requestNotificationPermissionIfNeeded(updatedTodos: List<TodoItem>) {
        if (hasNotificationPermission()) return
        val hasFutureAlarm = updatedTodos.any {
            shouldScheduleTodoAlarm(it, System.currentTimeMillis())
        }
        if (hasFutureAlarm) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun resetEditor(nowMillis: Long = System.currentTimeMillis()) {
        editingTodoId = null
        titleInput = ""
        selectedPriority = TodoPriority.MEDIUM
        dueAtMillis = defaultDueAtMillis(nowMillis)
    }

    fun showDatePicker() {
        val current = dueAtMillis.toLocalDateTime()
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                dueAtMillis = current
                    .withYear(year)
                    .withMonth(month + 1)
                    .withDayOfMonth(dayOfMonth)
                    .toEpochMillis()
            },
            current.year,
            current.monthValue - 1,
            current.dayOfMonth,
        ).show()
    }

    fun showTimePicker() {
        val current = dueAtMillis.toLocalDateTime()
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                dueAtMillis = current
                    .withHour(hourOfDay)
                    .withMinute(minute)
                    .withSecond(0)
                    .withNano(0)
                    .toEpochMillis()
            },
            current.hour,
            current.minute,
            true,
        ).show()
    }

    fun submitTodo(): Boolean {
        val now = System.currentTimeMillis()
        val editingId = editingTodoId
        val updatedTodos = if (editingId == null) {
            val item = createTodoItem(
                id = UUID.randomUUID().toString(),
                titleInput = titleInput,
                priority = selectedPriority,
                dueAtMillis = dueAtMillis,
                createdAtMillis = now,
            )

            if (item == null) {
                return false
            }
            todos + item
        } else {
            updateTodoItem(
                items = todos,
                id = editingId,
                titleInput = titleInput,
                priority = selectedPriority,
                dueAtMillis = dueAtMillis,
            ) ?: run {
                return false
            }
        }

        updateTodos(updatedTodos)
        requestNotificationPermissionIfNeeded(updatedTodos)
        resetEditor(now)
        editorExpanded = false
        return true
    }

    fun startEditing(item: TodoItem) {
        editingTodoId = item.id
        titleInput = item.title
        selectedPriority = item.priority
        dueAtMillis = item.dueAtMillis
        editorExpanded = true
    }

    fun cancelEditing() {
        resetEditor()
        editorExpanded = false
    }

    fun confirmDelete() {
        when (val confirmation = deleteConfirmation) {
            is DeleteConfirmation.SingleTodo -> {
                updateTodos(deleteTodoItem(todos, confirmation.id))
                if (editingTodoId == confirmation.id) {
                    resetEditor()
                    editorExpanded = false
                }
            }
            is DeleteConfirmation.CompletedTodos -> {
                val editingItem = todos.firstOrNull { it.id == editingTodoId }
                updateTodos(deleteCompletedTodos(todos))
                if (editingItem?.completed == true) {
                    resetEditor()
                    editorExpanded = false
                }
            }
            null -> Unit
        }
        deleteConfirmation = null
    }

    PixelDoneScreen(
        todos = todos,
        activeCount = todos.count { !it.completed },
        completedCount = todos.count { it.completed },
        titleInput = titleInput,
        onTitleInputChange = { titleInput = it },
        selectedPriority = selectedPriority,
        onPriorityChange = { selectedPriority = it },
        dueAtMillis = dueAtMillis,
        onPickDate = ::showDatePicker,
        onPickTime = ::showTimePicker,
        editorExpanded = editorExpanded,
        onEditorExpandedChange = { expanded ->
            editorExpanded = expanded
        },
        isEditing = editingTodoId != null,
        onSubmitTodo = ::submitTodo,
        onCancelEdit = ::cancelEditing,
        sortMode = sortMode,
        onSortModeChange = {
            sortDelayUntilMillis = 0L
            sortMode = it
        },
        hideCompleted = hideCompleted,
        onHideCompletedChange = {
            sortDelayUntilMillis = 0L
            hideCompleted = it
        },
        onToggleTodo = { id ->
            val now = System.currentTimeMillis()
            displayOrderIds = if (sortDelayUntilMillis > now && displayOrderIds.isNotEmpty()) {
                displayOrderIds
            } else {
                sortedVisibleIds
            }
            updateTodos(toggleTodoCompletion(todos, id))
            sortDelayUntilMillis = now + CompletionSortDelayMillis
        },
        onEditTodo = ::startEditing,
        onDeleteTodo = { id ->
            todos.firstOrNull { it.id == id }?.let { item ->
                deleteConfirmation = DeleteConfirmation.SingleTodo(item.id, item.title)
            }
        },
        onDeleteCompleted = {
            val completedCount = todos.count { it.completed }
            if (completedCount > 0) {
                deleteConfirmation = DeleteConfirmation.CompletedTodos(completedCount)
            }
        },
        displayOrderIds = displayOrderIds,
        keepDisplayOrder = sortDelayUntilMillis > 0L,
    )
    DeleteConfirmationDialog(
        confirmation = deleteConfirmation,
        onConfirm = ::confirmDelete,
        onDismiss = { deleteConfirmation = null },
    )
}

@Composable
private fun PixelDoneScreen(
    todos: List<TodoItem>,
    activeCount: Int,
    completedCount: Int,
    titleInput: String,
    onTitleInputChange: (String) -> Unit,
    selectedPriority: TodoPriority,
    onPriorityChange: (TodoPriority) -> Unit,
    dueAtMillis: Long,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
    editorExpanded: Boolean,
    onEditorExpandedChange: (Boolean) -> Unit,
    isEditing: Boolean,
    onSubmitTodo: () -> Boolean,
    onCancelEdit: () -> Unit,
    sortMode: SortMode,
    onSortModeChange: (SortMode) -> Unit,
    hideCompleted: Boolean,
    onHideCompletedChange: (Boolean) -> Unit,
    onToggleTodo: (String) -> Unit,
    onEditTodo: (TodoItem) -> Unit,
    onDeleteTodo: (String) -> Unit,
    onDeleteCompleted: () -> Unit,
    displayOrderIds: List<String>,
    keepDisplayOrder: Boolean,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ClaudeIvory)
            .systemBarsPadding()
            .imePadding()
            .padding(16.dp),
    ) {
        val isTablet = maxWidth >= 720.dp
        val density = LocalDensity.current
        val imeVisible = WindowInsets.ime.getBottom(density) > 0
        val editorMaxHeight = when {
            isTablet -> 420.dp
            maxHeight < 620.dp -> 260.dp
            else -> 340.dp
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Header(
                activeCount = activeCount,
                completedCount = completedCount,
            )
            TodoListPanel(
                todos = todos,
                sortMode = sortMode,
                onSortModeChange = onSortModeChange,
                hideCompleted = hideCompleted,
                onHideCompletedChange = onHideCompletedChange,
                completedCount = completedCount,
                onToggleTodo = onToggleTodo,
                onEditTodo = onEditTodo,
                onDeleteTodo = onDeleteTodo,
                onDeleteCompleted = onDeleteCompleted,
                displayOrderIds = displayOrderIds,
                keepDisplayOrder = keepDisplayOrder,
                modifier = Modifier.weight(1f),
            )
            if (editorExpanded || isEditing) {
                TaskEditorPanel(
                    titleInput = titleInput,
                    onTitleInputChange = onTitleInputChange,
                    selectedPriority = selectedPriority,
                    onPriorityChange = onPriorityChange,
                    dueAtMillis = dueAtMillis,
                    onPickDate = onPickDate,
                    onPickTime = onPickTime,
                    isEditing = isEditing,
                    onSubmitTodo = onSubmitTodo,
                    onCancelEdit = onCancelEdit,
                    onCloseNewTask = { onEditorExpandedChange(false) },
                    editorMaxHeight = editorMaxHeight,
                    compactForKeyboard = imeVisible,
                )
            } else {
                NewTaskBar(onOpenEditor = { onEditorExpandedChange(true) })
            }
            Footer()
        }
    }
}

@Composable
private fun Header(activeCount: Int, completedCount: Int) {
    PixelPanel(
        color = ClaudeGray100,
        borderWidth = 2.dp,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "PixelDone",
                style = MaterialTheme.typography.titleLarge,
                color = ClaudeSlateDark,
            )
            Text(
                text = "ACTIVE $activeCount  DONE $completedCount",
                style = MaterialTheme.typography.labelSmall,
                color = ClaudeSlateLight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TaskEditorPanel(
    titleInput: String,
    onTitleInputChange: (String) -> Unit,
    selectedPriority: TodoPriority,
    onPriorityChange: (TodoPriority) -> Unit,
    dueAtMillis: Long,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
    isEditing: Boolean,
    onSubmitTodo: () -> Boolean,
    onCancelEdit: () -> Unit,
    onCloseNewTask: () -> Unit,
    editorMaxHeight: Dp,
    compactForKeyboard: Boolean,
) {
    val focusManager = LocalFocusManager.current
    val titleFocusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()

    LaunchedEffect(isEditing) {
        if (!isEditing) {
            titleFocusRequester.requestFocus()
        }
    }

    PixelPanel {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (isEditing) {
                        onCancelEdit()
                    } else {
                        onCloseNewTask()
                    }
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (isEditing) "EDIT TASK" else "NEW TASK",
                style = MaterialTheme.typography.labelLarge,
                color = ClaudeSlateLight,
            )
            Text(
                text = if (isEditing) "CANCEL" else "CLOSE",
                style = MaterialTheme.typography.labelLarge,
                color = ClaudeClayInteractive,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = if (compactForKeyboard) 76.dp else editorMaxHeight)
                .then(if (compactForKeyboard) Modifier else Modifier.verticalScroll(scrollState)),
        ) {
            if (!compactForKeyboard) {
                Text(
                    text = "DETAILS",
                    style = MaterialTheme.typography.labelMedium,
                    color = ClaudeSlateLight,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            OutlinedTextField(
                value = titleInput,
                onValueChange = onTitleInputChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(titleFocusRequester),
                singleLine = true,
                shape = RectangleShape,
                textStyle = MaterialTheme.typography.bodyLarge,
                label = { Text("Name") },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                    },
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ClaudeClay,
                    unfocusedBorderColor = ClaudeGray300,
                    focusedContainerColor = ClaudeIvory,
                    unfocusedContainerColor = ClaudeIvory,
                    cursorColor = ClaudeClayInteractive,
                    focusedLabelColor = ClaudeClayInteractive,
                    unfocusedLabelColor = ClaudeGray600,
                ),
            )
            if (compactForKeyboard) {
                return@Column
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "PRIORITY",
                style = MaterialTheme.typography.labelMedium,
                color = ClaudeSlateLight,
            )
            Spacer(modifier = Modifier.height(6.dp))
            PixelSegmentedControl(
                options = TodoPriority.entries,
                selected = selectedPriority,
                label = { it.uiLabel() },
                onSelected = onPriorityChange,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "TIME / ALARM",
                style = MaterialTheme.typography.labelMedium,
                color = ClaudeSlateLight,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PixelButton(
                    text = dueAtMillis.formatDate(),
                    onClick = onPickDate,
                    modifier = Modifier.weight(1f),
                    primary = false,
                )
                PixelButton(
                    text = dueAtMillis.formatTime(),
                    onClick = onPickTime,
                    modifier = Modifier.weight(1f),
                    primary = false,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PixelButton(
                    text = if (isEditing) "SAVE" else "ADD",
                    onClick = {
                        if (onSubmitTodo()) {
                            focusManager.clearFocus()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    primary = true,
                )
                if (isEditing) {
                    PixelButton(
                        text = "CANCEL",
                        onClick = {
                            focusManager.clearFocus()
                            onCancelEdit()
                        },
                        modifier = Modifier.weight(1f),
                        primary = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun NewTaskBar(onOpenEditor: () -> Unit) {
    PixelPanel(
        color = ClaudeIvoryMedium,
        borderWidth = 1.dp,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 36.dp)
                .clickable(onClick = onOpenEditor),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "NEW TASK",
                style = MaterialTheme.typography.labelLarge,
                color = ClaudeSlateLight,
            )
            Text(
                text = "+",
                style = MaterialTheme.typography.titleLarge,
                color = ClaudeClayInteractive,
            )
        }
    }
}

@Composable
private fun TodoListPanel(
    todos: List<TodoItem>,
    sortMode: SortMode,
    onSortModeChange: (SortMode) -> Unit,
    hideCompleted: Boolean,
    onHideCompletedChange: (Boolean) -> Unit,
    completedCount: Int,
    onToggleTodo: (String) -> Unit,
    onEditTodo: (TodoItem) -> Unit,
    onDeleteTodo: (String) -> Unit,
    onDeleteCompleted: () -> Unit,
    displayOrderIds: List<String>,
    keepDisplayOrder: Boolean,
    modifier: Modifier = Modifier,
) {
    val sortedVisibleItems = visibleTodos(todos, sortMode, hideCompleted)
    val visibleItems = if (keepDisplayOrder) {
        orderedTodosForDisplay(
            todos = todos,
            sortedVisibleItems = sortedVisibleItems,
            displayOrderIds = displayOrderIds,
        )
    } else {
        sortedVisibleItems
    }

    PixelPanel(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PixelButton(
                text = if (sortMode == SortMode.PRIORITY) "PRI" else "TIME",
                onClick = {
                    onSortModeChange(
                        if (sortMode == SortMode.PRIORITY) SortMode.TIME else SortMode.PRIORITY,
                    )
                },
                modifier = Modifier.weight(1f),
                selected = sortMode == SortMode.TIME,
            )
            PixelButton(
                text = if (hideCompleted) "UNHIDE" else "HIDE",
                onClick = { onHideCompletedChange(!hideCompleted) },
                modifier = Modifier.weight(1f),
                selected = hideCompleted,
            )
            PixelBatchDeleteDoneButton(
                onClick = onDeleteCompleted,
                enabled = completedCount > 0,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (visibleItems.isEmpty()) {
            EmptyState(
                text = if (todos.isEmpty()) {
                    "Add a task to begin."
                } else {
                    "Done tasks are hidden."
                },
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 4.dp),
            ) {
                items(visibleItems, key = { it.id }) { item ->
                    TodoRow(
                        item = item,
                        onToggleTodo = onToggleTodo,
                        onEditTodo = onEditTodo,
                        onDeleteTodo = onDeleteTodo,
                    )
                }
            }
        }
    }
}

private fun orderedTodosForDisplay(
    todos: List<TodoItem>,
    sortedVisibleItems: List<TodoItem>,
    displayOrderIds: List<String>,
): List<TodoItem> {
    if (displayOrderIds.isEmpty()) return sortedVisibleItems

    val todosById = todos.associateBy { it.id }
    val orderedItems = displayOrderIds.mapNotNull { todosById[it] }
    val orderedIds = orderedItems.mapTo(mutableSetOf()) { it.id }
    val appendedItems = sortedVisibleItems.filterNot { it.id in orderedIds }
    return orderedItems + appendedItems
}

@Composable
private fun TodoRow(
    item: TodoItem,
    onToggleTodo: (String) -> Unit,
    onEditTodo: (TodoItem) -> Unit,
    onDeleteTodo: (String) -> Unit,
) {
    val itemBackground = if (item.completed) ClaudeCactus.copy(alpha = 0.35f) else ClaudeIvory
    val alarmText = if (shouldScheduleTodoAlarm(item, System.currentTimeMillis())) {
        "  ALARM"
    } else {
        ""
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ClaudeGray300, RectangleShape)
            .background(itemBackground)
            .clickable { onEditTodo(item) }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .width(5.dp)
                .height(48.dp)
                .background(item.priority.priorityColor()),
        )
        Checkbox(
            checked = item.completed,
            onCheckedChange = { onToggleTodo(item.id) },
            colors = CheckboxDefaults.colors(
                checkedColor = ClaudeMineral,
                uncheckedColor = ClaudeGray600,
                checkmarkColor = ClaudeIvory,
            ),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (item.completed) ClaudeGray600 else ClaudeSlateDark,
                textDecoration = if (item.completed) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${item.priority.uiLabel()}  ${item.dueAtMillis.formatDateTime()}$alarmText",
                style = MaterialTheme.typography.labelSmall,
                color = ClaudeSlateLight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PixelItemDeleteButton(onClick = { onDeleteTodo(item.id) })
    }
}

@Composable
private fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, ClaudeGray300, RectangleShape)
            .background(ClaudeIvoryMedium)
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = ClaudeSlateLight,
        )
    }
}

@Composable
private fun Footer() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "CODEX & XUE",
            style = MaterialTheme.typography.labelSmall,
            color = ClaudeSlateLight,
        )
    }
}

@Composable
private fun DeleteConfirmationDialog(
    confirmation: DeleteConfirmation?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (confirmation == null) return

    val titleText = when (confirmation) {
        is DeleteConfirmation.SingleTodo -> "Delete task?"
        is DeleteConfirmation.CompletedTodos -> "Delete done tasks?"
    }
    val bodyText = when (confirmation) {
        is DeleteConfirmation.SingleTodo -> "This will remove \"${confirmation.title}\"."
        is DeleteConfirmation.CompletedTodos -> "This will remove ${confirmation.count} completed task(s)."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = titleText,
                style = MaterialTheme.typography.titleMedium,
                color = ClaudeSlateDark,
            )
        },
        text = {
            Text(
                text = bodyText,
                style = MaterialTheme.typography.bodyMedium,
                color = ClaudeSlateLight,
            )
        },
        confirmButton = {
            PixelButton(
                text = "DELETE",
                onClick = onConfirm,
                destructive = true,
            )
        },
        dismissButton = {
            PixelButton(
                text = "CANCEL",
                onClick = onDismiss,
                primary = false,
            )
        },
        shape = RectangleShape,
        containerColor = ClaudeGray100,
        tonalElevation = 0.dp,
    )
}

@Composable
private fun PixelBatchDeleteDoneButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .border(1.dp, if (enabled) PixelError else ClaudeGray300, RectangleShape)
            .background(if (enabled) ClaudeCoral else ClaudeGray200)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PixelTrashIcon(color = if (enabled) ClaudeSlateDark else ClaudeGray600)
        PixelDoneBadge(
            enabled = enabled,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp),
        )
    }
}

@Composable
private fun PixelItemDeleteButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .border(1.dp, ClaudeGray300, RectangleShape)
            .background(ClaudeIvoryMedium)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PixelTrashIcon(color = PixelError)
    }
}

@Composable
private fun PixelDoneBadge(enabled: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(14.dp)
            .border(1.dp, if (enabled) ClaudeSlateDark else ClaudeGray300, RectangleShape)
            .background(if (enabled) ClaudeCactus else ClaudeGray200),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(8.dp)) {
            val strokeWidth = 1.5.dp.toPx()
            drawLine(
                color = if (enabled) ClaudeSlateDark else ClaudeGray600,
                start = Offset(1.dp.toPx(), 4.dp.toPx()),
                end = Offset(3.dp.toPx(), 6.dp.toPx()),
                strokeWidth = strokeWidth,
            )
            drawLine(
                color = if (enabled) ClaudeSlateDark else ClaudeGray600,
                start = Offset(3.dp.toPx(), 6.dp.toPx()),
                end = Offset(7.dp.toPx(), 1.dp.toPx()),
                strokeWidth = strokeWidth,
            )
        }
    }
}

@Composable
private fun PixelTrashIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(18.dp)) {
        val strokeWidth = 2.dp.toPx()
        val thinStrokeWidth = 1.5.dp.toPx()
        drawLine(
            color = color,
            start = Offset(4.dp.toPx(), 5.dp.toPx()),
            end = Offset(14.dp.toPx(), 5.dp.toPx()),
            strokeWidth = strokeWidth,
        )
        drawLine(
            color = color,
            start = Offset(7.dp.toPx(), 3.dp.toPx()),
            end = Offset(11.dp.toPx(), 3.dp.toPx()),
            strokeWidth = strokeWidth,
        )
        drawRect(
            color = color,
            topLeft = Offset(5.dp.toPx(), 7.dp.toPx()),
            size = Size(8.dp.toPx(), 9.dp.toPx()),
            style = Stroke(width = strokeWidth),
        )
        drawLine(
            color = color,
            start = Offset(8.dp.toPx(), 9.dp.toPx()),
            end = Offset(8.dp.toPx(), 14.dp.toPx()),
            strokeWidth = thinStrokeWidth,
        )
        drawLine(
            color = color,
            start = Offset(11.dp.toPx(), 9.dp.toPx()),
            end = Offset(11.dp.toPx(), 14.dp.toPx()),
            strokeWidth = thinStrokeWidth,
        )
    }
}

@Composable
private fun PixelPanel(
    modifier: Modifier = Modifier,
    color: Color = ClaudeGray100,
    borderColor: Color = ClaudeGray600,
    borderWidth: Dp = 2.dp,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = color,
        shape = RectangleShape,
        border = BorderStroke(borderWidth, borderColor),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content,
        )
    }
}

@Composable
private fun <T> PixelSegmentedControl(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Button(
                onClick = { onSelected(option) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 42.dp),
                shape = RectangleShape,
                border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) ClaudeClayInteractive else ClaudeGray300,
                ),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) ClaudeOat else ClaudeIvoryMedium,
                    contentColor = ClaudeSlateDark,
                ),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
            ) {
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PixelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    destructive: Boolean = false,
    selected: Boolean = false,
) {
    val containerColor = when {
        destructive -> ClaudeCoral
        primary -> ClaudeClay
        selected -> ClaudeOat
        else -> ClaudeIvoryMedium
    }
    val borderColor = when {
        destructive -> PixelError
        primary -> ClaudeClayInteractive
        selected -> ClaudeClayInteractive
        else -> ClaudeGray300
    }

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 44.dp),
        shape = RectangleShape,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (enabled) borderColor else ClaudeGray300),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = ClaudeSlateDark,
            disabledContainerColor = ClaudeGray200,
            disabledContentColor = ClaudeGray600,
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun TodoPriority.uiLabel(): String {
    return when (this) {
        TodoPriority.HIGH -> "HIGH"
        TodoPriority.MEDIUM -> "MID"
        TodoPriority.LOW -> "LOW"
    }
}

private fun TodoPriority.priorityColor(): Color {
    return when (this) {
        TodoPriority.HIGH -> ClaudeFig
        TodoPriority.MEDIUM -> ClaudeClay
        TodoPriority.LOW -> ClaudeSky
    }
}

private fun SortMode.uiLabel(): String {
    return when (this) {
        SortMode.PRIORITY -> "Priority"
        SortMode.TIME -> "Time"
    }
}

private val DateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val TimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DateTimeUiFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private fun defaultDueAtMillis(nowMillis: Long = System.currentTimeMillis()): Long {
    return nowMillis.toLocalDateTime()
        .withSecond(0)
        .withNano(0)
        .toEpochMillis()
}

private fun Long.toLocalDateTime(): LocalDateTime {
    return Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
}

private fun LocalDateTime.toEpochMillis(): Long {
    return atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

private fun Long.formatDate(): String {
    return toLocalDateTime().format(DateFormatter)
}

private fun Long.formatTime(): String {
    return toLocalDateTime().format(TimeFormatter)
}

private fun Long.formatDateTime(): String {
    return toLocalDateTime().format(DateTimeUiFormatter)
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun PhonePreview() {
    PixelDoneTheme {
        PixelDoneScreen(
            todos = previewTodos(),
            activeCount = 2,
            completedCount = 1,
            titleInput = "",
            onTitleInputChange = {},
            selectedPriority = TodoPriority.MEDIUM,
            onPriorityChange = {},
            dueAtMillis = defaultDueAtMillis(),
            onPickDate = {},
            onPickTime = {},
            editorExpanded = false,
            onEditorExpandedChange = {},
            isEditing = false,
            onSubmitTodo = { true },
            onCancelEdit = {},
            sortMode = SortMode.PRIORITY,
            onSortModeChange = {},
            hideCompleted = false,
            onHideCompletedChange = {},
            onToggleTodo = {},
            onEditTodo = {},
            onDeleteTodo = {},
            onDeleteCompleted = {},
            displayOrderIds = emptyList(),
            keepDisplayOrder = false,
        )
    }
}

@Preview(showBackground = true, widthDp = 900, heightDp = 600)
@Composable
private fun TabletPreview() {
    PixelDoneTheme {
        PixelDoneScreen(
            todos = previewTodos(),
            activeCount = 2,
            completedCount = 1,
            titleInput = "Draft project notes",
            onTitleInputChange = {},
            selectedPriority = TodoPriority.HIGH,
            onPriorityChange = {},
            dueAtMillis = defaultDueAtMillis(),
            onPickDate = {},
            onPickTime = {},
            editorExpanded = true,
            onEditorExpandedChange = {},
            isEditing = true,
            onSubmitTodo = { true },
            onCancelEdit = {},
            sortMode = SortMode.TIME,
            onSortModeChange = {},
            hideCompleted = false,
            onHideCompletedChange = {},
            onToggleTodo = {},
            onEditTodo = {},
            onDeleteTodo = {},
            onDeleteCompleted = {},
            displayOrderIds = emptyList(),
            keepDisplayOrder = false,
        )
    }
}

private fun previewTodos(): List<TodoItem> {
    val now = defaultDueAtMillis()
    return listOf(
        TodoItem("1", "Send the build to the test phone", TodoPriority.HIGH, now, false, 1L),
        TodoItem("2", "Plan tomorrow's tiny wins", TodoPriority.MEDIUM, now + 3_600_000L, false, 2L),
        TodoItem("3", "Archive finished notes", TodoPriority.LOW, now - 3_600_000L, true, 3L),
    )
}
