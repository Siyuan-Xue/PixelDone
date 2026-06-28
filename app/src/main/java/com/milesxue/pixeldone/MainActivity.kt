package com.milesxue.pixeldone

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.verticalScroll
import com.milesxue.pixeldone.ui.theme.ClaudeCactus
import com.milesxue.pixeldone.ui.theme.ClaudeClay
import com.milesxue.pixeldone.ui.theme.ClaudeClayInteractive
import com.milesxue.pixeldone.ui.theme.ClaudeCoral
import com.milesxue.pixeldone.ui.theme.ClaudeFig
import com.milesxue.pixeldone.ui.theme.ClaudeGray100
import com.milesxue.pixeldone.ui.theme.ClaudeGray200
import com.milesxue.pixeldone.ui.theme.ClaudeGray300
import com.milesxue.pixeldone.ui.theme.ClaudeGray600
import com.milesxue.pixeldone.ui.theme.ClaudeIvory
import com.milesxue.pixeldone.ui.theme.ClaudeIvoryDark
import com.milesxue.pixeldone.ui.theme.ClaudeIvoryMedium
import com.milesxue.pixeldone.ui.theme.ClaudeMineral
import com.milesxue.pixeldone.ui.theme.ClaudeOat
import com.milesxue.pixeldone.ui.theme.ClaudeSky
import com.milesxue.pixeldone.ui.theme.ClaudeSlateDark
import com.milesxue.pixeldone.ui.theme.ClaudeSlateLight
import com.milesxue.pixeldone.ui.theme.PixelDoneTheme
import com.milesxue.pixeldone.ui.theme.PixelError
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val CompletionSortDelayMillis = 2_000L

private const val DeveloperCredit = "CODEX & XUE"
private val PixelReadTopBarContentHeight = 36.dp
private val PixelReadFrameInset = 8.dp
private val PixelDoneFooterHeight = 24.dp
private const val InitialUpdateCheckDelayMillis = 600L
private const val StartupUpdateRetryDelayMillis = 2_000L
private const val UpdateStatusVisibleMillis = 3_000L

private enum class UpdateUiStatus {
    Idle,
    Checking,
    Latest,
    Available,
    Offline,
}

private data class AppUpdateUiState(
    val status: UpdateUiStatus = UpdateUiStatus.Idle,
    val info: AppUpdateInfo? = null,
) {
    val label: String?
        get() = when (status) {
            UpdateUiStatus.Idle -> null
            UpdateUiStatus.Checking -> "CHECK"
            UpdateUiStatus.Latest -> "LATEST"
            UpdateUiStatus.Available -> "GET"
            UpdateUiStatus.Offline -> "OFFLINE"
        }

    val contentDescription: String
        get() = when (status) {
            UpdateUiStatus.Idle -> "CHECK UPDATE"
            UpdateUiStatus.Checking -> "CHECKING UPDATE"
            UpdateUiStatus.Latest -> "LATEST VERSION"
            UpdateUiStatus.Available -> "GET UPDATE"
            UpdateUiStatus.Offline -> "UPDATE CHECK UNAVAILABLE"
        }
}

private sealed interface DeleteConfirmation {
    data class SingleTodo(val id: String, val title: String) : DeleteConfirmation
    data class CompletedTodos(val count: Int) : DeleteConfirmation
    data class Checklist(val id: String, val name: String, val todoCount: Int) : DeleteConfirmation
}

private sealed interface TodoListScrollIntent {
    data object KeepPosition : TodoListScrollIntent
    data object ScrollToTop : TodoListScrollIntent
    data class RevealTodo(val id: String) : TodoListScrollIntent
}

private data class TodoListScrollRequest(
    val sequence: Int,
    val intent: TodoListScrollIntent,
)

private sealed interface EditorMode {
    data object None : EditorMode
    data object NewTask : EditorMode
    data class EditTask(val id: String) : EditorMode
    data object NewChecklist : EditorMode
    data class EditChecklist(val id: String) : EditorMode
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyPixelDoneSystemBars()
        setContent {
            PixelDoneTheme {
                PixelDoneApp()
            }
        }
    }
}

fun ComponentActivity.applyPixelDoneSystemBars() {
    val backgroundScrim = ClaudeIvory.toArgb()
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.light(
            scrim = android.graphics.Color.TRANSPARENT,
            darkScrim = android.graphics.Color.TRANSPARENT,
        ),
        navigationBarStyle = SystemBarStyle.light(
            scrim = backgroundScrim,
            darkScrim = backgroundScrim,
        ),
    )
}

@Composable
private fun PixelDoneApp() {
    val context = LocalContext.current
    val updateScope = rememberCoroutineScope()
    val storage = remember { TodoPreferences.create(context) }
    var checklistState by remember { mutableStateOf(storage.loadTodoState()) }
    var titleInput by remember { mutableStateOf("") }
    var selectedPriority by remember { mutableStateOf(TodoPriority.MEDIUM) }
    var dueAtMillis by remember { mutableStateOf(0L) }
    var checklistNameInput by remember { mutableStateOf("") }
    var checklistEditorError by remember { mutableStateOf<String?>(null) }
    var headerExpanded by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf(SortMode.PRIORITY) }
    var hideCompleted by remember { mutableStateOf(false) }
    var editorMode by remember { mutableStateOf<EditorMode>(EditorMode.None) }
    var displayOrderIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var keepDisplayOrderDuringSortDelay by remember { mutableStateOf(false) }
    var sortDelayTick by remember { mutableStateOf(0) }
    var deleteConfirmation by remember { mutableStateOf<DeleteConfirmation?>(null) }
    var updateUiState by remember { mutableStateOf(AppUpdateUiState()) }
    var updatePromptInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var neverShowUpdateDialog by remember { mutableStateOf(storage.loadNeverShowUpdateDialog()) }
    var updatePromptDismissedThisSession by remember { mutableStateOf(false) }
    var scrollRequestSequence by remember { mutableStateOf(0) }
    var todoListScrollRequest by remember {
        mutableStateOf(
            TodoListScrollRequest(
                sequence = 0,
                intent = TodoListScrollIntent.KeepPosition,
            ),
        )
    }
    val selectedChecklist = selectedChecklistOf(checklistState)
    val todos = selectedChecklist.items
    val allCurrentTodos = allTodos(checklistState)
    val activeCount = activeTodoCount(selectedChecklist)
    val completedCount = completedTodoCount(selectedChecklist)
    val editingTaskId = (editorMode as? EditorMode.EditTask)?.id
    val editingChecklistId = (editorMode as? EditorMode.EditChecklist)?.id
    val taskEditorVisible = editorMode is EditorMode.NewTask || editorMode is EditorMode.EditTask
    val checklistEditorVisible =
        editorMode is EditorMode.NewChecklist || editorMode is EditorMode.EditChecklist

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            TodoAlarmScheduler.sync(context, emptyList(), allTodos(checklistState))
        }
    }

    LaunchedEffect(Unit) {
        TodoAlarmScheduler.sync(context, emptyList(), allCurrentTodos)
    }

    val sortedVisibleIds = visibleTodos(todos, sortMode, hideCompleted).map { it.id }

    LaunchedEffect(selectedChecklist.id, sortedVisibleIds, keepDisplayOrderDuringSortDelay) {
        if (!keepDisplayOrderDuringSortDelay) {
            displayOrderIds = sortedVisibleIds
        }
    }

    LaunchedEffect(sortDelayTick) {
        if (sortDelayTick > 0) {
            delay(CompletionSortDelayMillis)
            keepDisplayOrderDuringSortDelay = false
        }
    }

    LaunchedEffect(updateUiState.status, updateUiState.info?.version) {
        when (updateUiState.status) {
            UpdateUiStatus.Latest,
            UpdateUiStatus.Offline,
            -> {
                delay(UpdateStatusVisibleMillis)
                updateUiState = AppUpdateUiState()
            }
            UpdateUiStatus.Idle,
            UpdateUiStatus.Checking,
            UpdateUiStatus.Available,
            -> Unit
        }
    }

    fun updateChecklistState(updatedState: TodoChecklistState) {
        val previousTodos = allTodos(checklistState)
        val updatedTodos = allTodos(updatedState)
        checklistState = updatedState
        storage.saveTodoState(updatedState)
        TodoAlarmScheduler.sync(context, previousTodos, updatedTodos)
    }

    fun updateSelectedTodos(updatedTodos: List<TodoItem>): TodoChecklistState? {
        val updatedState = updateChecklistItems(
            state = checklistState,
            checklistId = selectedChecklist.id,
            items = updatedTodos,
        ) ?: return null
        updateChecklistState(updatedState)
        return updatedState
    }

    fun requestTodoListScroll(intent: TodoListScrollIntent) {
        scrollRequestSequence += 1
        todoListScrollRequest = TodoListScrollRequest(
            sequence = scrollRequestSequence,
            intent = intent,
        )
    }

    fun hasNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun requestNotificationPermissionIfNeeded(updatedTodos: List<TodoItem>) {
        if (hasNotificationPermission()) return
        val nowMillis = System.currentTimeMillis()
        val hasFutureAlarm = updatedTodos.any {
            shouldScheduleTodoAlarm(it, nowMillis)
        }
        if (hasFutureAlarm) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun clearEditor() {
        titleInput = ""
        selectedPriority = TodoPriority.MEDIUM
        checklistNameInput = ""
        checklistEditorError = null
    }

    fun openNewTaskEditor() {
        clearEditor()
        dueAtMillis = defaultDueAtMillis()
        editorMode = EditorMode.NewTask
    }

    fun openNewChecklistEditor() {
        clearEditor()
        headerExpanded = true
        editorMode = EditorMode.NewChecklist
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
        val editingId = editingTaskId
        var affectedTodoId = editingId
        val updatedTodos = if (editingId == null) {
            if (titleInput.isBlank()) {
                return false
            }
            val newTodoId = UUID.randomUUID().toString()
            affectedTodoId = newTodoId
            val item = createTodoItem(
                id = newTodoId,
                titleInput = titleInput,
                priority = selectedPriority,
                dueAtMillis = dueAtMillis,
                createdAtMillis = System.currentTimeMillis(),
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

        val updatedState = updateSelectedTodos(updatedTodos) ?: return false
        affectedTodoId?.let { id ->
            requestTodoListScroll(TodoListScrollIntent.RevealTodo(id))
        }
        requestNotificationPermissionIfNeeded(allTodos(updatedState))
        clearEditor()
        editorMode = EditorMode.None
        return true
    }

    fun startEditing(item: TodoItem) {
        editorMode = EditorMode.EditTask(item.id)
        titleInput = item.title
        selectedPriority = item.priority
        dueAtMillis = item.dueAtMillis
    }

    fun startEditingChecklist(checklist: TodoChecklist) {
        headerExpanded = true
        editorMode = EditorMode.EditChecklist(checklist.id)
        checklistNameInput = checklist.name
        checklistEditorError = null
    }

    fun submitChecklist(): Boolean {
        val editingId = editingChecklistId
        val trimmedName = checklistNameInput.trim()
        checklistEditorError = when {
            trimmedName.isEmpty() -> "Name is required."
            !isChecklistNameAvailable(checklistState, trimmedName, editingId) -> "Name already exists."
            else -> null
        }
        if (checklistEditorError != null) return false

        val updatedState = if (editingId == null) {
            createTodoChecklist(
                state = checklistState,
                id = UUID.randomUUID().toString(),
                nameInput = trimmedName,
                createdAtMillis = System.currentTimeMillis(),
            )
        } else {
            renameTodoChecklist(
                state = checklistState,
                id = editingId,
                nameInput = trimmedName,
            )
        } ?: run {
            checklistEditorError = "Could not save list."
            return false
        }

        updateChecklistState(updatedState)
        requestTodoListScroll(TodoListScrollIntent.ScrollToTop)
        keepDisplayOrderDuringSortDelay = false
        displayOrderIds = emptyList()
        clearEditor()
        editorMode = EditorMode.None
        return true
    }

    fun cancelEditing() {
        clearEditor()
        editorMode = EditorMode.None
    }

    fun selectChecklist(id: String) {
        val updatedState = selectTodoChecklist(checklistState, id) ?: return
        updateChecklistState(updatedState)
        clearEditor()
        editorMode = EditorMode.None
        keepDisplayOrderDuringSortDelay = false
        displayOrderIds = emptyList()
        requestTodoListScroll(TodoListScrollIntent.ScrollToTop)
        headerExpanded = false
    }

    fun confirmDelete() {
        when (val confirmation = deleteConfirmation) {
            is DeleteConfirmation.SingleTodo -> {
                requestTodoListScroll(TodoListScrollIntent.KeepPosition)
                updateSelectedTodos(deleteTodoItem(todos, confirmation.id))
                if (editingTaskId == confirmation.id) {
                    clearEditor()
                    editorMode = EditorMode.None
                }
            }
            is DeleteConfirmation.CompletedTodos -> {
                requestTodoListScroll(TodoListScrollIntent.KeepPosition)
                val editingItem = todos.firstOrNull { it.id == editingTaskId }
                updateSelectedTodos(deleteCompletedTodos(todos))
                if (editingItem?.completed == true) {
                    clearEditor()
                    editorMode = EditorMode.None
                }
            }
            is DeleteConfirmation.Checklist -> {
                val updatedState = deleteTodoChecklist(checklistState, confirmation.id)
                if (updatedState != null) {
                    updateChecklistState(updatedState)
                    requestTodoListScroll(TodoListScrollIntent.ScrollToTop)
                    keepDisplayOrderDuringSortDelay = false
                    displayOrderIds = emptyList()
                    if (editingChecklistId == confirmation.id) {
                        clearEditor()
                        editorMode = EditorMode.None
                    }
                }
            }
            null -> Unit
        }
        deleteConfirmation = null
    }

    fun openUpdateUrl(info: AppUpdateInfo) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(info.actionUrl)),
        )
    }

    fun showUpdatePromptIfAllowed(info: AppUpdateInfo) {
        if (!neverShowUpdateDialog && !updatePromptDismissedThisSession) {
            updatePromptInfo = info
        }
    }

    fun closeUpdatePrompt(neverShow: Boolean) {
        if (neverShow) {
            neverShowUpdateDialog = true
            storage.saveNeverShowUpdateDialog(true)
        }
        updatePromptDismissedThisSession = true
        updatePromptInfo = null
    }

    fun applyUpdateCheckResult(
        result: AppUpdateCheckResult,
        showCurrentOrOfflineStatus: Boolean,
    ) {
        when (result) {
            is AppUpdateCheckResult.Available -> {
                updateUiState = AppUpdateUiState(
                    status = UpdateUiStatus.Available,
                    info = result.info,
                )
                showUpdatePromptIfAllowed(result.info)
            }
            AppUpdateCheckResult.Current -> {
                updatePromptInfo = null
                if (showCurrentOrOfflineStatus) {
                    updateUiState = AppUpdateUiState(status = UpdateUiStatus.Latest)
                }
            }
            AppUpdateCheckResult.Unavailable -> {
                updatePromptInfo = null
                if (showCurrentOrOfflineStatus) {
                    updateUiState = AppUpdateUiState(status = UpdateUiStatus.Offline)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(InitialUpdateCheckDelayMillis)
        val firstResult = checkPixelDoneUpdate(BuildConfig.VERSION_NAME)
        if (firstResult == AppUpdateCheckResult.Unavailable) {
            delay(StartupUpdateRetryDelayMillis)
            applyUpdateCheckResult(
                result = checkPixelDoneUpdate(BuildConfig.VERSION_NAME),
                showCurrentOrOfflineStatus = false,
            )
        } else {
            applyUpdateCheckResult(
                result = firstResult,
                showCurrentOrOfflineStatus = false,
            )
        }
    }

    fun handleUpdateClick() {
        val availableInfo = updateUiState.info
        if (updateUiState.status == UpdateUiStatus.Available && availableInfo != null) {
            openUpdateUrl(availableInfo)
            return
        }
        if (updateUiState.status == UpdateUiStatus.Checking) return

        updateUiState = AppUpdateUiState(status = UpdateUiStatus.Checking)
        updateScope.launch {
            applyUpdateCheckResult(
                result = checkPixelDoneUpdate(BuildConfig.VERSION_NAME),
                showCurrentOrOfflineStatus = true,
            )
        }
    }

    PixelDoneScreen(
        checklists = checklistState.lists,
        selectedChecklistId = selectedChecklist.id,
        selectedChecklistName = selectedChecklist.name,
        headerExpanded = headerExpanded,
        onHeaderExpandedChange = { headerExpanded = it },
        onSelectChecklist = ::selectChecklist,
        onEditChecklist = { id ->
            checklistState.lists.firstOrNull { it.id == id }?.let(::startEditingChecklist)
        },
        todos = todos,
        activeCount = activeCount,
        completedCount = completedCount,
        titleInput = titleInput,
        onTitleInputChange = { titleInput = it },
        selectedPriority = selectedPriority,
        onPriorityChange = { selectedPriority = it },
        dueAtMillis = dueAtMillis,
        onPickDate = ::showDatePicker,
        onPickTime = ::showTimePicker,
        taskEditorVisible = taskEditorVisible,
        isEditingTask = editingTaskId != null,
        onOpenTaskEditor = ::openNewTaskEditor,
        onOpenChecklistEditor = ::openNewChecklistEditor,
        onSubmitTodo = ::submitTodo,
        onCancelEdit = ::cancelEditing,
        checklistNameInput = checklistNameInput,
        onChecklistNameInputChange = {
            checklistNameInput = it
            checklistEditorError = null
        },
        checklistEditorVisible = checklistEditorVisible,
        isEditingChecklist = editingChecklistId != null,
        checklistEditorError = checklistEditorError,
        canDeleteChecklist = checklistState.lists.size > 1,
        onSubmitChecklist = ::submitChecklist,
        onDeleteChecklist = {
            editingChecklistId?.let { id ->
                checklistState.lists.firstOrNull { it.id == id }?.let { checklist ->
                    deleteConfirmation = DeleteConfirmation.Checklist(
                        id = checklist.id,
                        name = checklist.name,
                        todoCount = checklist.items.size,
                    )
                }
            }
        },
        sortMode = sortMode,
        onSortModeChange = {
            requestTodoListScroll(TodoListScrollIntent.ScrollToTop)
            keepDisplayOrderDuringSortDelay = false
            sortMode = it
        },
        hideCompleted = hideCompleted,
        onHideCompletedChange = {
            requestTodoListScroll(TodoListScrollIntent.ScrollToTop)
            keepDisplayOrderDuringSortDelay = false
            hideCompleted = it
        },
        onToggleTodo = { id ->
            requestTodoListScroll(TodoListScrollIntent.KeepPosition)
            displayOrderIds = if (keepDisplayOrderDuringSortDelay && displayOrderIds.isNotEmpty()) {
                displayOrderIds
            } else {
                sortedVisibleIds
            }
            updateSelectedTodos(toggleTodoCompletion(todos, id))
            keepDisplayOrderDuringSortDelay = true
            sortDelayTick += 1
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
        keepDisplayOrder = keepDisplayOrderDuringSortDelay,
        todoListScrollRequest = todoListScrollRequest,
        updateUiState = updateUiState,
        onUpdateClick = ::handleUpdateClick,
    )
    DeleteConfirmationDialog(
        confirmation = deleteConfirmation,
        onConfirm = ::confirmDelete,
        onDismiss = { deleteConfirmation = null },
    )
    UpdateConfirmationDialog(
        info = updatePromptInfo,
        onUpdate = { info, neverShow ->
            closeUpdatePrompt(neverShow)
            openUpdateUrl(info)
        },
        onDismiss = ::closeUpdatePrompt,
    )
}

@Composable
private fun PixelDoneScreen(
    checklists: List<TodoChecklist>,
    selectedChecklistId: String,
    selectedChecklistName: String,
    headerExpanded: Boolean,
    onHeaderExpandedChange: (Boolean) -> Unit,
    onSelectChecklist: (String) -> Unit,
    onEditChecklist: (String) -> Unit,
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
    taskEditorVisible: Boolean,
    isEditingTask: Boolean,
    onOpenTaskEditor: () -> Unit,
    onOpenChecklistEditor: () -> Unit,
    onSubmitTodo: () -> Boolean,
    onCancelEdit: () -> Unit,
    checklistNameInput: String,
    onChecklistNameInputChange: (String) -> Unit,
    checklistEditorVisible: Boolean,
    isEditingChecklist: Boolean,
    checklistEditorError: String?,
    canDeleteChecklist: Boolean,
    onSubmitChecklist: () -> Boolean,
    onDeleteChecklist: () -> Unit,
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
    todoListScrollRequest: TodoListScrollRequest,
    updateUiState: AppUpdateUiState,
    onUpdateClick: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ClaudeIvory)
            .systemBarsPadding()
            .imePadding()
            .padding(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 0.dp),
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Header(
                checklists = checklists,
                selectedChecklistId = selectedChecklistId,
                selectedChecklistName = selectedChecklistName,
                activeCount = activeCount,
                completedCount = completedCount,
                expanded = headerExpanded,
                onExpandedChange = onHeaderExpandedChange,
                onSelectChecklist = onSelectChecklist,
                onEditChecklist = onEditChecklist,
            )
            TaskWorkspacePanel(
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
                todoListScrollRequest = todoListScrollRequest,
                taskEditorVisible = taskEditorVisible,
                isEditingTask = isEditingTask,
                onOpenTaskEditor = onOpenTaskEditor,
                onOpenChecklistEditor = onOpenChecklistEditor,
                titleInput = titleInput,
                onTitleInputChange = onTitleInputChange,
                selectedPriority = selectedPriority,
                onPriorityChange = onPriorityChange,
                dueAtMillis = dueAtMillis,
                onPickDate = onPickDate,
                onPickTime = onPickTime,
                onSubmitTodo = onSubmitTodo,
                onCancelEdit = onCancelEdit,
                checklistNameInput = checklistNameInput,
                onChecklistNameInputChange = onChecklistNameInputChange,
                checklistEditorVisible = checklistEditorVisible,
                isEditingChecklist = isEditingChecklist,
                checklistEditorError = checklistEditorError,
                canDeleteChecklist = canDeleteChecklist,
                onSubmitChecklist = onSubmitChecklist,
                onDeleteChecklist = onDeleteChecklist,
                editorMaxHeight = editorMaxHeight,
                compactForKeyboard = imeVisible,
                modifier = Modifier.weight(1f),
            )
            Footer(
                updateUiState = updateUiState,
                onUpdateClick = onUpdateClick,
            )
        }
    }
}

@Composable
private fun Header(
    checklists: List<TodoChecklist>,
    selectedChecklistId: String,
    selectedChecklistName: String,
    activeCount: Int,
    completedCount: Int,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelectChecklist: (String) -> Unit,
    onEditChecklist: (String) -> Unit,
) {
    val scrollState = rememberScrollState()

    PixelPanel(
        color = ClaudeGray100,
        borderWidth = 2.dp,
        contentPadding = PaddingValues(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(PixelReadTopBarContentHeight)
                .clickable { onExpandedChange(!expanded) },
            horizontalArrangement = Arrangement.spacedBy(PixelReadFrameInset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selectedChecklistName,
                color = ClaudeSlateDark,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "ACTIVE $activeCount  DONE $completedCount",
                style = MaterialTheme.typography.labelSmall,
                color = ClaudeSlateLight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (expanded) {
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 184.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                checklists.forEach { checklist ->
                    ChecklistPickerRow(
                        checklist = checklist,
                        selected = checklist.id == selectedChecklistId,
                        onSelect = { onSelectChecklist(checklist.id) },
                        onEdit = { onEditChecklist(checklist.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChecklistPickerRow(
    checklist: TodoChecklist,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
) {
    val borderColor = if (selected) ClaudeClayInteractive else ClaudeGray300
    val backgroundColor = if (selected) ClaudeOat else ClaudeIvory

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(if (selected) 2.dp else 1.dp, borderColor, RectangleShape)
            .background(backgroundColor)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onSelect),
        ) {
            Text(
                text = checklist.name,
                color = ClaudeSlateDark,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "ACTIVE ${activeTodoCount(checklist)}  DONE ${completedTodoCount(checklist)}",
                style = MaterialTheme.typography.labelSmall,
                color = ClaudeSlateLight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PixelSettingsButton(onClick = onEdit)
    }
}

@Composable
private fun TaskWorkspacePanel(
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
    todoListScrollRequest: TodoListScrollRequest,
    taskEditorVisible: Boolean,
    isEditingTask: Boolean,
    onOpenTaskEditor: () -> Unit,
    onOpenChecklistEditor: () -> Unit,
    titleInput: String,
    onTitleInputChange: (String) -> Unit,
    selectedPriority: TodoPriority,
    onPriorityChange: (TodoPriority) -> Unit,
    dueAtMillis: Long,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
    onSubmitTodo: () -> Boolean,
    onCancelEdit: () -> Unit,
    checklistNameInput: String,
    onChecklistNameInputChange: (String) -> Unit,
    checklistEditorVisible: Boolean,
    isEditingChecklist: Boolean,
    checklistEditorError: String?,
    canDeleteChecklist: Boolean,
    onSubmitChecklist: () -> Boolean,
    onDeleteChecklist: () -> Unit,
    editorMaxHeight: Dp,
    compactForKeyboard: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PixelReadFrameInset),
    ) {
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
            todoListScrollRequest = todoListScrollRequest,
            showNewTaskButton = !taskEditorVisible && !checklistEditorVisible,
            onOpenTaskEditor = onOpenTaskEditor,
            onOpenChecklistEditor = onOpenChecklistEditor,
            modifier = Modifier.weight(1f),
        )
        if (taskEditorVisible) {
            TaskEditorPanel(
                titleInput = titleInput,
                onTitleInputChange = onTitleInputChange,
                selectedPriority = selectedPriority,
                onPriorityChange = onPriorityChange,
                dueAtMillis = dueAtMillis,
                onPickDate = onPickDate,
                onPickTime = onPickTime,
                isEditing = isEditingTask,
                onSubmitTodo = onSubmitTodo,
                onCancelEdit = onCancelEdit,
                onCloseNewTask = onCancelEdit,
                editorMaxHeight = editorMaxHeight,
                compactForKeyboard = compactForKeyboard,
            )
        } else if (checklistEditorVisible) {
            ChecklistEditorPanel(
                nameInput = checklistNameInput,
                onNameInputChange = onChecklistNameInputChange,
                isEditing = isEditingChecklist,
                errorText = checklistEditorError,
                canDeleteChecklist = canDeleteChecklist,
                onSubmitChecklist = onSubmitChecklist,
                onCancelEdit = onCancelEdit,
                onDeleteChecklist = onDeleteChecklist,
                editorMaxHeight = editorMaxHeight,
                compactForKeyboard = compactForKeyboard,
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
    val hapticFeedback = LocalHapticFeedback.current
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
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            focusManager.clearFocus()
                        } else {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Reject)
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
private fun ChecklistEditorPanel(
    nameInput: String,
    onNameInputChange: (String) -> Unit,
    isEditing: Boolean,
    errorText: String?,
    canDeleteChecklist: Boolean,
    onSubmitChecklist: () -> Boolean,
    onCancelEdit: () -> Unit,
    onDeleteChecklist: () -> Unit,
    editorMaxHeight: Dp,
    compactForKeyboard: Boolean,
) {
    val focusManager = LocalFocusManager.current
    val hapticFeedback = LocalHapticFeedback.current
    val nameFocusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()

    LaunchedEffect(isEditing) {
        nameFocusRequester.requestFocus()
    }

    PixelPanel {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCancelEdit() },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (isEditing) "EDIT LIST" else "NEW LIST",
                style = MaterialTheme.typography.labelLarge,
                color = ClaudeSlateLight,
            )
            Text(
                text = "CANCEL",
                style = MaterialTheme.typography.labelLarge,
                color = ClaudeClayInteractive,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = if (compactForKeyboard) 96.dp else editorMaxHeight)
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
                value = nameInput,
                onValueChange = onNameInputChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(nameFocusRequester),
                singleLine = true,
                isError = errorText != null,
                shape = RectangleShape,
                textStyle = MaterialTheme.typography.bodyLarge,
                label = { Text("List name") },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
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
                    errorBorderColor = PixelError,
                    focusedContainerColor = ClaudeIvory,
                    unfocusedContainerColor = ClaudeIvory,
                    errorContainerColor = ClaudeIvory,
                    cursorColor = ClaudeClayInteractive,
                    focusedLabelColor = ClaudeClayInteractive,
                    unfocusedLabelColor = ClaudeGray600,
                    errorLabelColor = PixelError,
                ),
            )
            errorText?.let { text ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = PixelError,
                )
            }
            if (compactForKeyboard) {
                return@Column
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PixelButton(
                    text = "SAVE",
                    onClick = {
                        if (onSubmitChecklist()) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            focusManager.clearFocus()
                        } else {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Reject)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    primary = true,
                )
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
            if (isEditing) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "DELETE",
                    style = MaterialTheme.typography.labelMedium,
                    color = PixelError,
                )
                Spacer(modifier = Modifier.height(6.dp))
                PixelButton(
                    text = if (canDeleteChecklist) "DELETE LIST" else "KEEP ONE LIST",
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        onDeleteChecklist()
                    },
                    enabled = canDeleteChecklist,
                    modifier = Modifier.fillMaxWidth(),
                    destructive = true,
                )
            }
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
    todoListScrollRequest: TodoListScrollRequest,
    showNewTaskButton: Boolean,
    onOpenTaskEditor: () -> Unit,
    onOpenChecklistEditor: () -> Unit,
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
    val visibleItemIds = visibleItems.map { it.id }
    val listState = rememberLazyListState()
    val hapticFeedback = LocalHapticFeedback.current

    LaunchedEffect(todoListScrollRequest.sequence, visibleItemIds, keepDisplayOrder) {
        when (val intent = todoListScrollRequest.intent) {
            TodoListScrollIntent.KeepPosition -> Unit
            TodoListScrollIntent.ScrollToTop -> {
                if (visibleItems.isNotEmpty()) {
                    listState.scrollToItem(0)
                }
            }
            is TodoListScrollIntent.RevealTodo -> {
                val itemIndex = visibleItemIds.indexOf(intent.id)
                if (itemIndex >= 0) {
                    listState.scrollToItem(itemIndex)
                }
            }
        }
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
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onSortModeChange(
                        if (sortMode == SortMode.PRIORITY) SortMode.TIME else SortMode.PRIORITY,
                    )
                },
                modifier = Modifier.weight(1f),
                selected = sortMode == SortMode.TIME,
            )
            PixelButton(
                text = if (hideCompleted) "UNHIDE" else "HIDE",
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onHideCompletedChange(!hideCompleted)
                },
                modifier = Modifier.weight(1f),
                selected = hideCompleted,
            )
            PixelBatchDeleteDoneButton(
                onClick = onDeleteCompleted,
                enabled = completedCount > 0,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            if (visibleItems.isEmpty()) {
                EmptyState(
                    text = if (todos.isEmpty()) {
                        "Add a task to begin."
                    } else {
                        "Done tasks are hidden."
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                val alarmNowMillis = System.currentTimeMillis()
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 4.dp),
                ) {
                    items(visibleItems, key = { it.id }) { item ->
                        TodoRow(
                            item = item,
                            onToggleTodo = onToggleTodo,
                            onEditTodo = onEditTodo,
                            onDeleteTodo = onDeleteTodo,
                            nowMillis = alarmNowMillis,
                        )
                    }
                }
            }
            if (showNewTaskButton) {
                FloatingNewTaskButton(
                    onClick = onOpenTaskEditor,
                    onLongClick = onOpenChecklistEditor,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp),
                )
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
    nowMillis: Long,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val itemBackground = if (item.completed) ClaudeCactus.copy(alpha = 0.35f) else ClaudeIvory
    val alarmText = if (shouldScheduleTodoAlarm(item, nowMillis)) {
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
            onCheckedChange = { checked ->
                if (checked) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                }
                onToggleTodo(item.id)
            },
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
private fun Footer(
    updateUiState: AppUpdateUiState,
    onUpdateClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(PixelDoneFooterHeight),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "PIXELDONE",
            color = ClaudeSlateDark,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
            maxLines = 1,
        )
        Text(
            text = DeveloperCredit,
            color = ClaudeClay,
            fontFamily = FontFamily.Monospace,
            fontSize = 8.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
        )
        UpdateMark(
            state = updateUiState,
            onClick = onUpdateClick,
            idleColor = ClaudeSlateLight,
            activeColor = ClaudeClay,
            errorColor = PixelError,
        )
    }
}

@Composable
private fun UpdateMark(
    state: AppUpdateUiState,
    onClick: () -> Unit,
    idleColor: Color,
    activeColor: Color,
    errorColor: Color,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val color = when (state.status) {
        UpdateUiStatus.Available -> activeColor
        UpdateUiStatus.Offline -> errorColor
        else -> idleColor
    }
    val displayColor = if (pressed && state.status != UpdateUiStatus.Checking) activeColor else color

    Row(
        modifier = modifier.height(PixelDoneFooterHeight),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clickable(
                    enabled = state.status != UpdateUiStatus.Checking,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .semantics { contentDescription = state.contentDescription },
            contentAlignment = Alignment.Center,
        ) {
            UpdateGlyph(
                color = displayColor,
                modifier = Modifier.size(14.dp),
            )
            if (state.status == UpdateUiStatus.Available) {
                Canvas(
                    modifier = Modifier
                        .size(6.dp)
                        .align(Alignment.TopEnd),
                ) {
                    drawCircle(color = activeColor, radius = size.minDimension / 2f)
                }
            }
        }
        state.label?.let { label ->
            Text(
                text = label,
                color = displayColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun UpdateGlyph(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 1.5.dp.toPx()
        val centerX = size.width / 2f
        drawLine(
            color = color,
            start = Offset(centerX, 1.dp.toPx()),
            end = Offset(centerX, 9.dp.toPx()),
            strokeWidth = strokeWidth,
        )
        drawLine(
            color = color,
            start = Offset(centerX, 9.dp.toPx()),
            end = Offset(3.dp.toPx(), 5.dp.toPx()),
            strokeWidth = strokeWidth,
        )
        drawLine(
            color = color,
            start = Offset(centerX, 9.dp.toPx()),
            end = Offset(11.dp.toPx(), 5.dp.toPx()),
            strokeWidth = strokeWidth,
        )
        drawLine(
            color = color,
            start = Offset(2.dp.toPx(), 12.dp.toPx()),
            end = Offset(12.dp.toPx(), 12.dp.toPx()),
            strokeWidth = strokeWidth,
        )
    }
}

@Composable
private fun UpdateConfirmationDialog(
    info: AppUpdateInfo?,
    onUpdate: (AppUpdateInfo, Boolean) -> Unit,
    onDismiss: (Boolean) -> Unit,
) {
    if (info == null) return

    var dontShowAgain by remember(info.version) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { onDismiss(dontShowAgain) },
        title = {
            Text(
                text = "Update available",
                style = MaterialTheme.typography.titleMedium,
                color = ClaudeSlateDark,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "PixelDone ${info.version} is available.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ClaudeSlateLight,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { dontShowAgain = !dontShowAgain },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Checkbox(
                        checked = dontShowAgain,
                        onCheckedChange = { dontShowAgain = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = ClaudeClay,
                            uncheckedColor = ClaudeGray600,
                            checkmarkColor = ClaudeIvory,
                        ),
                    )
                    Text(
                        text = "Don't show again",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ClaudeSlateDark,
                    )
                }
            }
        },
        confirmButton = {
            PixelButton(
                text = "UPDATE",
                onClick = { onUpdate(info, dontShowAgain) },
                primary = true,
            )
        },
        dismissButton = {
            PixelButton(
                text = "NOT NOW",
                onClick = { onDismiss(dontShowAgain) },
                primary = false,
            )
        },
        shape = RectangleShape,
        containerColor = ClaudeGray100,
        tonalElevation = 0.dp,
    )
}

@Composable
private fun DeleteConfirmationDialog(
    confirmation: DeleteConfirmation?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (confirmation == null) return

    val hapticFeedback = LocalHapticFeedback.current
    val titleText = when (confirmation) {
        is DeleteConfirmation.SingleTodo -> "Delete task?"
        is DeleteConfirmation.CompletedTodos -> "Delete done tasks?"
        is DeleteConfirmation.Checklist -> "Delete list?"
    }
    val bodyText = when (confirmation) {
        is DeleteConfirmation.SingleTodo -> "This will remove \"${confirmation.title}\"."
        is DeleteConfirmation.CompletedTodos -> "This will remove ${confirmation.count} completed task(s)."
        is DeleteConfirmation.Checklist ->
            "This will remove \"${confirmation.name}\" and ${confirmation.todoCount} task(s)."
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
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    onConfirm()
                },
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
private fun PixelSettingsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .border(1.dp, ClaudeGray300, RectangleShape)
            .background(ClaudeIvoryMedium)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "EDIT LIST" },
        contentAlignment = Alignment.Center,
    ) {
        PixelSettingsIcon(color = ClaudeSlateDark)
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
private fun PixelSettingsIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(18.dp)) {
        val strokeWidth = 1.8.dp.toPx()
        drawLine(
            color = color,
            start = Offset(3.dp.toPx(), 5.dp.toPx()),
            end = Offset(15.dp.toPx(), 5.dp.toPx()),
            strokeWidth = strokeWidth,
        )
        drawRect(
            color = color,
            topLeft = Offset(6.dp.toPx(), 3.dp.toPx()),
            size = Size(3.dp.toPx(), 4.dp.toPx()),
            style = Stroke(width = strokeWidth),
        )
        drawLine(
            color = color,
            start = Offset(3.dp.toPx(), 13.dp.toPx()),
            end = Offset(15.dp.toPx(), 13.dp.toPx()),
            strokeWidth = strokeWidth,
        )
        drawRect(
            color = color,
            topLeft = Offset(10.dp.toPx(), 11.dp.toPx()),
            size = Size(3.dp.toPx(), 4.dp.toPx()),
            style = Stroke(width = strokeWidth),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FloatingNewTaskButton(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val hapticFeedback = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .size(56.dp)
            .background(if (pressed) ClaudeClayInteractive else ClaudeClay, RectangleShape)
            .border(2.dp, ClaudeSlateDark, RectangleShape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            )
            .semantics { contentDescription = "NEW TASK OR LIST" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "+",
            color = ClaudeSlateDark,
            fontFamily = FontFamily.Monospace,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
        )
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

internal fun defaultDueAtMillis(nowMillis: Long = System.currentTimeMillis()): Long {
    return nowMillis.toLocalDateTime()
        .plusDays(1)
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
    val checklists = previewChecklists()
    PixelDoneTheme {
        PixelDoneScreen(
            checklists = checklists,
            selectedChecklistId = checklists.first().id,
            selectedChecklistName = checklists.first().name,
            headerExpanded = false,
            onHeaderExpandedChange = {},
            onSelectChecklist = {},
            onEditChecklist = {},
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
            taskEditorVisible = false,
            isEditingTask = false,
            onOpenTaskEditor = {},
            onOpenChecklistEditor = {},
            onSubmitTodo = { true },
            onCancelEdit = {},
            checklistNameInput = "",
            onChecklistNameInputChange = {},
            checklistEditorVisible = false,
            isEditingChecklist = false,
            checklistEditorError = null,
            canDeleteChecklist = true,
            onSubmitChecklist = { true },
            onDeleteChecklist = {},
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
            todoListScrollRequest = previewScrollRequest(),
            updateUiState = AppUpdateUiState(),
            onUpdateClick = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 900, heightDp = 600)
@Composable
private fun TabletPreview() {
    val checklists = previewChecklists()
    PixelDoneTheme {
        PixelDoneScreen(
            checklists = checklists,
            selectedChecklistId = checklists.first().id,
            selectedChecklistName = checklists.first().name,
            headerExpanded = true,
            onHeaderExpandedChange = {},
            onSelectChecklist = {},
            onEditChecklist = {},
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
            taskEditorVisible = true,
            isEditingTask = true,
            onOpenTaskEditor = {},
            onOpenChecklistEditor = {},
            onSubmitTodo = { true },
            onCancelEdit = {},
            checklistNameInput = "WORK",
            onChecklistNameInputChange = {},
            checklistEditorVisible = false,
            isEditingChecklist = false,
            checklistEditorError = null,
            canDeleteChecklist = true,
            onSubmitChecklist = { true },
            onDeleteChecklist = {},
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
            todoListScrollRequest = previewScrollRequest(),
            updateUiState = AppUpdateUiState(status = UpdateUiStatus.Available),
            onUpdateClick = {},
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

private fun previewChecklists(): List<TodoChecklist> {
    val todos = previewTodos()
    return listOf(
        TodoChecklist(
            id = DefaultChecklistId,
            name = DefaultChecklistName,
            items = todos,
            createdAtMillis = 1L,
        ),
        TodoChecklist(
            id = "work",
            name = "WORK",
            items = todos.take(1),
            createdAtMillis = 2L,
        ),
    )
}

private fun previewScrollRequest(): TodoListScrollRequest {
    return TodoListScrollRequest(
        sequence = 0,
        intent = TodoListScrollIntent.KeepPosition,
    )
}
