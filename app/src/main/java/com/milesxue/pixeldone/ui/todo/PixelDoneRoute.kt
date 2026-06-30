package com.milesxue.pixeldone.ui.todo

import android.Manifest
import android.app.NotificationManager
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.verticalScroll
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.milesxue.pixeldone.ui.theme.ClaudeCactus
import com.milesxue.pixeldone.ui.theme.ClaudeClay
import com.milesxue.pixeldone.ui.theme.ClaudeClayInteractive
import com.milesxue.pixeldone.ui.theme.ClaudeGray100
import com.milesxue.pixeldone.ui.theme.ClaudeGray300
import com.milesxue.pixeldone.ui.theme.ClaudeGray600
import com.milesxue.pixeldone.ui.theme.ClaudeIvory
import com.milesxue.pixeldone.ui.theme.ClaudeIvoryMedium
import com.milesxue.pixeldone.ui.theme.ClaudeMineral
import com.milesxue.pixeldone.ui.theme.ClaudeOat
import com.milesxue.pixeldone.ui.theme.ClaudeSlateDark
import com.milesxue.pixeldone.ui.theme.ClaudeSlateLight
import com.milesxue.pixeldone.ui.theme.PixelDoneTheme
import com.milesxue.pixeldone.ui.theme.PixelError
import com.milesxue.pixeldone.ui.todo.components.FloatingNewTaskButton
import com.milesxue.pixeldone.ui.todo.components.PixelAlarmIcon
import com.milesxue.pixeldone.ui.todo.components.PixelBatchDeleteDoneButton
import com.milesxue.pixeldone.ui.todo.components.PixelButton
import com.milesxue.pixeldone.ui.todo.components.PixelItemImageButton
import com.milesxue.pixeldone.ui.todo.components.PixelPanel
import com.milesxue.pixeldone.ui.todo.components.PixelRestoreButton
import com.milesxue.pixeldone.ui.todo.components.PixelSegmentedControl
import com.milesxue.pixeldone.ui.todo.components.PixelSettingsButton
import com.milesxue.pixeldone.ui.todo.components.PrioritySlider
import com.milesxue.pixeldone.ui.todo.components.priorityColor
import com.milesxue.pixeldone.ui.todo.components.uiLabel
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.milesxue.pixeldone.BuildConfig
import com.milesxue.pixeldone.data.image.TodoImageStore
import com.milesxue.pixeldone.di.pixelDoneAppContainer
import com.milesxue.pixeldone.data.update.AppUpdateDownload
import com.milesxue.pixeldone.data.update.AppUpdateDownloadCompletion
import com.milesxue.pixeldone.data.update.AppUpdateDownloadResult
import com.milesxue.pixeldone.data.update.AppUpdateCheckResult
import com.milesxue.pixeldone.data.update.AppUpdateInfo
import com.milesxue.pixeldone.domain.todo.*
internal const val CompletionSortDelayMillis = 2_000L
private const val MinuteMillis = 60_000L
private const val TodoHighlightFadeMillis = 180

private const val DeveloperCredit = "CODEX & XUE"
private val PixelReadTopBarContentHeight = 36.dp
private val PixelReadFrameInset = 8.dp
private val PixelDoneFooterHeight = 24.dp
private const val InitialUpdateCheckDelayMillis = 600L
private const val UpdateStatusVisibleMillis = 3_000L
private const val MinPreviewScale = 1f
private const val MaxPreviewScale = 6f

private enum class UpdateUiStatus {
    Idle,
    Checking,
    Latest,
    Available,
    Offline,
    Downloading,
    Installing,
}

private data class AppUpdateUiState(
    val status: UpdateUiStatus = UpdateUiStatus.Idle,
    val info: AppUpdateInfo? = null,
    val message: String? = null,
) {
    val contentDescription: String
        get() = when (status) {
            UpdateUiStatus.Idle -> "CHECK UPDATE"
            UpdateUiStatus.Checking -> "CHECKING UPDATE"
            UpdateUiStatus.Latest -> "LATEST VERSION"
            UpdateUiStatus.Available -> "GET UPDATE"
            UpdateUiStatus.Offline -> "UPDATE CHECK UNAVAILABLE"
            UpdateUiStatus.Downloading -> "DOWNLOADING UPDATE"
            UpdateUiStatus.Installing -> "INSTALLING UPDATE"
        }

    val shouldAutoRestore: Boolean
        get() = status == UpdateUiStatus.Latest ||
            status == UpdateUiStatus.Available ||
            status == UpdateUiStatus.Offline ||
            status == UpdateUiStatus.Installing
}

private sealed interface DeleteConfirmation {
    data class SingleTodo(val id: String, val title: String) : DeleteConfirmation
    data class CompletedTodos(val count: Int) : DeleteConfirmation
    data class Checklist(val id: String, val name: String, val todoCount: Int) : DeleteConfirmation
    data class TrashTodos(val count: Int) : DeleteConfirmation
}

private sealed interface TodoImagePreviewLoadState {
    data object Loading : TodoImagePreviewLoadState
    data class Ready(val bitmap: ImageBitmap) : TodoImagePreviewLoadState
    data object Unavailable : TodoImagePreviewLoadState
}

private sealed interface TodoListScrollIntent {
    data object KeepPosition : TodoListScrollIntent
    data object PreserveViewport : TodoListScrollIntent
    data object ScrollToTop : TodoListScrollIntent
    data class RevealTodos(val ids: Set<String>) : TodoListScrollIntent
}

private data class TodoListScrollRequest(
    val sequence: Int,
    val intent: TodoListScrollIntent,
)

private data class TodoListHighlightRequest(
    val sequence: Int,
    val ids: Set<String>,
)

internal data class PendingTodoToggleFeedback(
    val initialCompletedById: Map<String, Boolean> = emptyMap(),
    val latestCompletedById: Map<String, Boolean> = emptyMap(),
) {
    val completedIds: Set<String>
        get() = changedIds(finalCompleted = true)

    val undoneIds: Set<String>
        get() = changedIds(finalCompleted = false)

    val highlightIds: Set<String>
        get() = undoneIds

    private fun changedIds(finalCompleted: Boolean): Set<String> {
        return latestCompletedById
            .filter { (id, latestCompleted) ->
                latestCompleted == finalCompleted &&
                    initialCompletedById[id] != null &&
                    initialCompletedById[id] != latestCompleted
            }
            .keys
    }
}

private sealed interface EditorMode {
    data object None : EditorMode
    data object NewTask : EditorMode
    data class EditTask(val id: String) : EditorMode
    data object NewChecklist : EditorMode
    data class EditChecklist(val id: String) : EditorMode
}

@Composable
internal fun PixelDoneApp() {
    val context = LocalContext.current
    val updateScope = rememberCoroutineScope()
    val appContainer = remember(context) { context.pixelDoneAppContainer() }
    val todoRepository = remember(appContainer) { appContainer.todoRepository }
    val imageStore = remember(appContainer) { appContainer.todoImageStore }
    val updateService = remember(appContainer) { appContainer.updateService }
    val reminderScheduler = remember(appContainer) { appContainer.reminderScheduler }
    val viewModelFactory = remember(appContainer) {
        PixelDoneViewModel.factory(
            todoRepository = todoRepository,
            reminderScheduler = reminderScheduler,
        )
    }
    val viewModel: PixelDoneViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val checklistState = uiState.checklistState
    var titleInput by remember { mutableStateOf("") }
    var selectedPriority by remember { mutableStateOf(TodoPriority.MEDIUM) }
    var selectedReminderRepeat by remember { mutableStateOf(ReminderRepeat.NONE) }
    var dueAtMillis by remember { mutableStateOf(0L) }
    var checklistNameInput by remember { mutableStateOf("") }
    var checklistEditorError by remember { mutableStateOf<String?>(null) }
    var headerExpanded by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf(SortMode.PRIORITY) }
    var hideCompleted by remember { mutableStateOf(false) }
    var showDeadlineCountdown by remember { mutableStateOf(false) }
    var editorMode by remember { mutableStateOf<EditorMode>(EditorMode.None) }
    var displayOrderIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var keepDisplayOrderDuringSortDelay by remember { mutableStateOf(false) }
    var sortDelayTick by remember { mutableStateOf(0) }
    var pendingTodoToggleFeedback by remember { mutableStateOf(PendingTodoToggleFeedback()) }
    var pendingReminderPermissionTodoId by remember { mutableStateOf<String?>(null) }
    var pendingFullScreenPermissionTodoId by remember { mutableStateOf<String?>(null) }
    var deleteConfirmation by remember { mutableStateOf<DeleteConfirmation?>(null) }
    var imagePreviewTodoId by remember { mutableStateOf<String?>(null) }
    var pendingImageTodoId by remember { mutableStateOf<String?>(null) }
    var updateUiState by remember { mutableStateOf(AppUpdateUiState()) }
    var updateCheckInFlight by remember { mutableStateOf(false) }
    var activeUpdateDownload by remember { mutableStateOf<AppUpdateDownload?>(null) }
    var scrollRequestSequence by remember { mutableStateOf(0) }
    var highlightRequestSequence by remember { mutableStateOf(0) }
    var todoListScrollRequest by remember {
        mutableStateOf(
            TodoListScrollRequest(
                sequence = 0,
                intent = TodoListScrollIntent.KeepPosition,
            ),
        )
    }
    var todoListHighlightRequest by remember {
        mutableStateOf(
            TodoListHighlightRequest(
                sequence = 0,
                ids = emptySet(),
            ),
        )
    }
    val selectedChecklist = selectedChecklistOf(checklistState)
    val currentChecklistState by rememberUpdatedState(checklistState)
    val isTrashSelected = isTrashChecklist(selectedChecklist)
    val todos = selectedChecklist.items
    val activeCount = activeTodoCount(selectedChecklist)
    val completedCount = completedTodoCount(selectedChecklist)
    val editingTaskId = (editorMode as? EditorMode.EditTask)?.id
    val editingChecklistId = (editorMode as? EditorMode.EditChecklist)?.id
    val taskEditorVisible = editorMode is EditorMode.NewTask || editorMode is EditorMode.EditTask
    val checklistEditorVisible =
        editorMode is EditorMode.NewChecklist || editorMode is EditorMode.EditChecklist
    val imagePreviewItem = todos.firstOrNull { it.id == imagePreviewTodoId }

    LaunchedEffect(Unit) {
        val currentTodos = normalTodos(checklistState)
        reminderScheduler.sync(currentTodos, currentTodos)
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
            val feedback = pendingTodoToggleFeedback
            keepDisplayOrderDuringSortDelay = false
            pendingTodoToggleFeedback = PendingTodoToggleFeedback()
            if (feedback.undoneIds.isNotEmpty()) {
                scrollRequestSequence += 1
                todoListScrollRequest = TodoListScrollRequest(
                    sequence = scrollRequestSequence,
                    intent = TodoListScrollIntent.RevealTodos(feedback.undoneIds),
                )
            }
            val highlightIds = feedback.highlightIds
            if (highlightIds.isNotEmpty()) {
                highlightRequestSequence += 1
                todoListHighlightRequest = TodoListHighlightRequest(
                    sequence = highlightRequestSequence,
                    ids = highlightIds,
                )
            }
        }
    }

    LaunchedEffect(updateUiState.status, updateUiState.info?.version, updateUiState.message) {
        if (updateUiState.shouldAutoRestore) {
            delay(UpdateStatusVisibleMillis)
            updateUiState = AppUpdateUiState()
        }
    }

    fun updateChecklistState(updatedState: TodoChecklistState): Set<ReminderCapability> {
        return viewModel.replaceChecklistState(updatedState)
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

    fun requestTodoListHighlight(ids: Set<String>) {
        if (ids.isEmpty()) return
        highlightRequestSequence += 1
        todoListHighlightRequest = TodoListHighlightRequest(
            sequence = highlightRequestSequence,
            ids = ids,
        )
    }

    fun revealAndHighlightTodos(ids: Set<String>) {
        if (ids.isEmpty()) return
        requestTodoListScroll(TodoListScrollIntent.RevealTodos(ids))
        requestTodoListHighlight(ids)
    }

    fun hasNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun hasFullScreenIntentAccess(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    }

    fun missingReminderCapabilities(item: TodoItem): Set<ReminderCapability> {
        val nowMillis = System.currentTimeMillis()
        return requiredReminderCapabilities(item, nowMillis).filterTo(mutableSetOf()) { capability ->
            when (capability) {
                ReminderCapability.NOTIFICATION_PERMISSION -> !hasNotificationPermission()
                ReminderCapability.EXACT_ALARM_ACCESS -> !reminderScheduler.canScheduleExactAlarms()
                ReminderCapability.FULL_SCREEN_INTENT_ACCESS -> !hasFullScreenIntentAccess()
            }
        }
    }

    fun requestSystemReminderPermissionIfNeeded(item: TodoItem) {
        val missingCapabilities = missingReminderCapabilities(item)
        val action = when {
            ReminderCapability.EXACT_ALARM_ACCESS in missingCapabilities -> {
                if (ReminderCapability.FULL_SCREEN_INTENT_ACCESS in missingCapabilities) {
                    pendingFullScreenPermissionTodoId = item.id
                }
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
            }
            ReminderCapability.FULL_SCREEN_INTENT_ACCESS in missingCapabilities ->
                Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT
            else -> return
        }
        try {
            context.startActivity(
                Intent(action).apply {
                    data = Uri.parse("package:${context.packageName}")
                },
            )
        } catch (_: ActivityNotFoundException) {
            // Some Android builds omit these settings panels; leave the reminder degraded.
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            val currentTodos = normalTodos(currentChecklistState)
            reminderScheduler.sync(currentTodos, currentTodos)
            pendingReminderPermissionTodoId
                ?.let { id -> normalTodos(currentChecklistState).firstOrNull { it.id == id } }
                ?.let(::requestSystemReminderPermissionIfNeeded)
        }
        pendingReminderPermissionTodoId = null
    }

    fun requestReminderPermissionsIfNeeded(item: TodoItem) {
        val missingCapabilities = missingReminderCapabilities(item)
        if (ReminderCapability.NOTIFICATION_PERMISSION in missingCapabilities) {
            pendingReminderPermissionTodoId = item.id
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        requestSystemReminderPermissionIfNeeded(item)
    }

    fun setTodoImageFileName(
        todoId: String,
        imageFileName: String?,
        revealAfterUpdate: Boolean = false,
    ): TodoItem? {
        val updatedTodos = updateTodoImageFileName(
            items = todos,
            id = todoId,
            imageFileName = imageFileName,
        ) ?: return null
        updateSelectedTodos(updatedTodos)
        if (revealAfterUpdate) {
            revealAndHighlightTodos(setOf(todoId))
        } else {
            requestTodoListScroll(TodoListScrollIntent.KeepPosition)
        }
        return updatedTodos.firstOrNull { it.id == todoId }
    }

    fun attachPickedImage(todoId: String, uri: Uri) {
        val previousImageFileName = todos.firstOrNull { it.id == todoId }?.imageFileName
        val newImageFileName = imageStore.copyImage(uri) ?: return
        val updatedItem = setTodoImageFileName(
            todoId = todoId,
            imageFileName = newImageFileName,
            revealAfterUpdate = true,
        )
        if (updatedItem == null) {
            imageStore.deleteImage(newImageFileName)
            return
        }
        imageStore.deleteImage(previousImageFileName)
        imagePreviewTodoId = null
    }

    fun removeTodoImage(todoId: String) {
        val previousImageFileName = todos.firstOrNull { it.id == todoId }?.imageFileName
        val updatedItem = setTodoImageFileName(todoId, null)
        if (updatedItem != null) {
            imageStore.deleteImage(previousImageFileName)
            imagePreviewTodoId = null
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val todoId = pendingImageTodoId
        pendingImageTodoId = null
        if (uri != null && todoId != null) {
            attachPickedImage(todoId, uri)
        }
    }

    fun openTodoImagePicker(todoId: String) {
        pendingImageTodoId = todoId
        imagePickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    fun clearEditor() {
        titleInput = ""
        selectedPriority = TodoPriority.MEDIUM
        selectedReminderRepeat = ReminderRepeat.NONE
        checklistNameInput = ""
        checklistEditorError = null
    }

    fun openNewTaskEditor() {
        if (isTrashSelected) return
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
        if (isTrashSelected) return false
        val editingId = editingTaskId
        var affectedTodoId = editingId
        val nowMillis = System.currentTimeMillis()
        val normalizedDueAtMillis = normalizeRepeatingDueAtMillis(
            dueAtMillis = dueAtMillis,
            reminderRepeat = selectedReminderRepeat,
            nowMillis = nowMillis,
        )
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
                dueAtMillis = normalizedDueAtMillis,
                createdAtMillis = nowMillis,
                reminderRepeat = selectedReminderRepeat,
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
                dueAtMillis = normalizedDueAtMillis,
                reminderRepeat = selectedReminderRepeat,
            ) ?: run {
                return false
            }
        }

        val updatedState = updateSelectedTodos(updatedTodos) ?: return false
        affectedTodoId?.let { id ->
            revealAndHighlightTodos(setOf(id))
        }
        affectedTodoId
            ?.let { id -> normalTodos(updatedState).firstOrNull { it.id == id } }
            ?.let(::requestReminderPermissionsIfNeeded)
        clearEditor()
        editorMode = EditorMode.None
        return true
    }

    fun startEditing(item: TodoItem) {
        if (isTrashSelected) return
        editorMode = EditorMode.EditTask(item.id)
        titleInput = item.title
        selectedPriority = item.priority
        selectedReminderRepeat = item.reminderRepeat
        dueAtMillis = item.dueAtMillis
    }

    fun startEditingChecklist(checklist: TodoChecklist) {
        if (isTrashChecklist(checklist)) return
        headerExpanded = true
        editorMode = EditorMode.EditChecklist(checklist.id)
        checklistNameInput = checklist.name
        checklistEditorError = null
    }

    fun submitChecklist(): Boolean {
        val editingId = editingChecklistId
        if (editingId == TrashChecklistId) {
            checklistEditorError = "TRASH is locked."
            return false
        }
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
        pendingTodoToggleFeedback = PendingTodoToggleFeedback()
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
        pendingTodoToggleFeedback = PendingTodoToggleFeedback()
        displayOrderIds = emptyList()
        requestTodoListScroll(TodoListScrollIntent.ScrollToTop)
        headerExpanded = false
    }

    fun confirmDelete() {
        when (val confirmation = deleteConfirmation) {
            is DeleteConfirmation.SingleTodo -> {
                requestTodoListScroll(TodoListScrollIntent.KeepPosition)
                moveTodoItemToTrash(
                    state = checklistState,
                    checklistId = selectedChecklist.id,
                    todoId = confirmation.id,
                    trashedAtMillis = System.currentTimeMillis(),
                )?.let(::updateChecklistState)
                if (imagePreviewTodoId == confirmation.id) {
                    imagePreviewTodoId = null
                }
                if (editingTaskId == confirmation.id) {
                    clearEditor()
                    editorMode = EditorMode.None
                }
            }
            is DeleteConfirmation.CompletedTodos -> {
                requestTodoListScroll(TodoListScrollIntent.KeepPosition)
                val editingItem = todos.firstOrNull { it.id == editingTaskId }
                moveCompletedTodosToTrash(
                    state = checklistState,
                    checklistId = selectedChecklist.id,
                    trashedAtMillis = System.currentTimeMillis(),
                )?.let(::updateChecklistState)
                if (imagePreviewTodoId != null && todos.any { it.id == imagePreviewTodoId && it.completed }) {
                    imagePreviewTodoId = null
                }
                if (editingItem?.completed == true) {
                    clearEditor()
                    editorMode = EditorMode.None
                }
            }
            is DeleteConfirmation.Checklist -> {
                val updatedState = deleteTodoChecklist(
                    state = checklistState,
                    id = confirmation.id,
                    trashedAtMillis = System.currentTimeMillis(),
                )
                if (updatedState != null) {
                    updateChecklistState(updatedState)
                    imagePreviewTodoId = null
                    requestTodoListScroll(TodoListScrollIntent.ScrollToTop)
                    keepDisplayOrderDuringSortDelay = false
                    pendingTodoToggleFeedback = PendingTodoToggleFeedback()
                    displayOrderIds = emptyList()
                    if (editingChecklistId == confirmation.id) {
                        clearEditor()
                        editorMode = EditorMode.None
                    }
                }
            }
            is DeleteConfirmation.TrashTodos -> {
                val deletedImageFileNames = trashTodos(checklistState).mapNotNull { it.imageFileName }
                updateChecklistState(deleteAllTrashTodos(checklistState))
                deletedImageFileNames.forEach(imageStore::deleteImage)
                imagePreviewTodoId = null
                requestTodoListScroll(TodoListScrollIntent.ScrollToTop)
            }
            null -> Unit
        }
        deleteConfirmation = null
    }

    fun restoreTrashTodo(todoId: String) {
        restoreTodoFromTrash(
            state = checklistState,
            todoId = todoId,
            restoredAtMillis = System.currentTimeMillis(),
        )?.let { updatedState ->
            updateChecklistState(updatedState)
            requestTodoListScroll(TodoListScrollIntent.KeepPosition)
        }
    }

    fun versionLabel(version: String): String = "v$version"

    fun latestUpdateMessage(): String =
        "latest: ${versionLabel(BuildConfig.VERSION_NAME)}"

    fun availableUpdateMessage(info: AppUpdateInfo): String =
        "get: ${versionLabel(BuildConfig.VERSION_NAME)} -> ${versionLabel(info.version)}"

    fun startUpdateDownload(info: AppUpdateInfo) {
        if (activeUpdateDownload?.version == info.version) return
        when (val result = updateService.enqueue(info)) {
            is AppUpdateDownloadResult.Started -> {
                activeUpdateDownload = result.download
                updateUiState = AppUpdateUiState(
                    status = UpdateUiStatus.Downloading,
                    info = info,
                    message = "download: ${versionLabel(info.version)}",
                )
                updateScope.launch {
                    val completion = updateService.awaitCompletion(result.download)
                    if (activeUpdateDownload?.downloadId != result.download.downloadId) {
                        return@launch
                    }
                    activeUpdateDownload = null
                    if (
                        completion == AppUpdateDownloadCompletion.Success &&
                        updateService.openInstallPrompt(result.download)
                    ) {
                        updateUiState = AppUpdateUiState(
                            status = UpdateUiStatus.Installing,
                            message = "install: ${versionLabel(info.version)}",
                        )
                    } else {
                        updateUiState = AppUpdateUiState(
                            status = UpdateUiStatus.Offline,
                            message = "update failed",
                        )
                    }
                }
            }
            AppUpdateDownloadResult.Failed -> {
                updateUiState = AppUpdateUiState(
                    status = UpdateUiStatus.Offline,
                    message = "update failed",
                )
            }
        }
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
                    message = availableUpdateMessage(result.info),
                )
            }
            AppUpdateCheckResult.Current -> {
                if (showCurrentOrOfflineStatus) {
                    updateUiState = AppUpdateUiState(
                        status = UpdateUiStatus.Latest,
                        message = latestUpdateMessage(),
                    )
                }
            }
            AppUpdateCheckResult.Unavailable -> {
                if (showCurrentOrOfflineStatus) {
                    updateUiState = AppUpdateUiState(
                        status = UpdateUiStatus.Offline,
                        message = "update failed",
                    )
                }
            }
        }
    }

    fun checkForUpdateSilently() {
        if (updateCheckInFlight || activeUpdateDownload != null) return
        updateCheckInFlight = true
        updateScope.launch {
            delay(InitialUpdateCheckDelayMillis)
            try {
                applyUpdateCheckResult(
                    result = updateService.check(BuildConfig.VERSION_NAME),
                    showCurrentOrOfflineStatus = false,
                )
            } finally {
                updateCheckInFlight = false
            }
        }
    }

    DisposableEffect(context) {
        val activity = context as? ComponentActivity
        if (activity == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START) {
                    checkForUpdateSilently()
                }
                if (event == Lifecycle.Event.ON_RESUME) {
                    val currentTodos = normalTodos(currentChecklistState)
                    reminderScheduler.sync(currentTodos, currentTodos)
                    pendingFullScreenPermissionTodoId?.let { id ->
                        val item = normalTodos(currentChecklistState).firstOrNull { it.id == id }
                        pendingFullScreenPermissionTodoId = null
                        if (item != null && reminderScheduler.canScheduleExactAlarms()) {
                            requestSystemReminderPermissionIfNeeded(item)
                        }
                    }
                }
            }
            activity.lifecycle.addObserver(observer)
            onDispose {
                activity.lifecycle.removeObserver(observer)
            }
        }
    }

    fun handleUpdateClick() {
        val availableInfo = updateUiState.info
        if (updateUiState.status == UpdateUiStatus.Available && availableInfo != null) {
            startUpdateDownload(availableInfo)
            return
        }
        if (updateCheckInFlight || activeUpdateDownload != null) return

        updateCheckInFlight = true
        updateUiState = AppUpdateUiState(
            status = UpdateUiStatus.Checking,
            message = "checking...",
        )
        updateScope.launch {
            try {
                applyUpdateCheckResult(
                    result = updateService.check(BuildConfig.VERSION_NAME),
                    showCurrentOrOfflineStatus = true,
                )
            } finally {
                updateCheckInFlight = false
            }
        }
    }

    PixelDoneScreen(
        checklists = checklistState.lists,
        selectedChecklistId = selectedChecklist.id,
        selectedChecklistName = selectedChecklist.name,
        isTrashSelected = isTrashSelected,
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
        selectedReminderRepeat = selectedReminderRepeat,
        onReminderRepeatChange = { selectedReminderRepeat = it },
        dueAtMillis = dueAtMillis,
        onPickDate = ::showDatePicker,
        onPickTime = ::showTimePicker,
        taskEditorVisible = taskEditorVisible,
        editingTaskId = editingTaskId,
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
        canDeleteChecklist = !isTrashSelected && normalChecklistCount(checklistState) > 1,
        onSubmitChecklist = ::submitChecklist,
        onDeleteChecklist = {
            editingChecklistId?.let { id ->
                checklistState.lists
                    .firstOrNull { it.id == id && !isTrashChecklist(it) }
                    ?.let { checklist ->
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
            pendingTodoToggleFeedback = PendingTodoToggleFeedback()
            sortMode = it
        },
        hideCompleted = hideCompleted,
        onHideCompletedChange = {
            requestTodoListScroll(TodoListScrollIntent.ScrollToTop)
            keepDisplayOrderDuringSortDelay = false
            pendingTodoToggleFeedback = PendingTodoToggleFeedback()
            hideCompleted = it
        },
        showDeadlineCountdown = showDeadlineCountdown,
        onDeadlineCountdownChange = { showDeadlineCountdown = it },
        onToggleTodo = { id, checked ->
            val itemBeforeToggle = todos.firstOrNull { it.id == id }
            if (itemBeforeToggle != null) {
                displayOrderIds = if (keepDisplayOrderDuringSortDelay && displayOrderIds.isNotEmpty()) {
                    displayOrderIds
                } else {
                    sortedVisibleIds
                }
                updateSelectedTodos(toggleTodoCompletion(todos, id))
                pendingTodoToggleFeedback = pendingTodoToggleFeedback.recordTodoToggle(
                    id = id,
                    wasCompleted = itemBeforeToggle.completed,
                    checked = checked,
                )
                keepDisplayOrderDuringSortDelay = true
                sortDelayTick += 1
            }
        },
        onEditTodo = ::startEditing,
        onOpenTodoImage = { item ->
            if (!isTrashSelected) {
                if (item.imageFileName == null) {
                    openTodoImagePicker(item.id)
                } else {
                    imagePreviewTodoId = item.id
                }
            }
        },
        onDeleteEditingTodo = {
            editingTaskId?.let { id ->
                todos.firstOrNull { it.id == id }?.let { item ->
                    deleteConfirmation = DeleteConfirmation.SingleTodo(item.id, item.title)
                }
            }
        },
        onDeleteCompleted = {
            if (!isTrashSelected) {
                val completedCount = todos.count { it.completed }
                if (completedCount > 0) {
                    deleteConfirmation = DeleteConfirmation.CompletedTodos(completedCount)
                }
            }
        },
        onRestoreTodo = ::restoreTrashTodo,
        onDeleteAllTrash = {
            val trashCount = trashTodos(checklistState).size
            if (trashCount > 0) {
                deleteConfirmation = DeleteConfirmation.TrashTodos(trashCount)
            }
        },
        displayOrderIds = displayOrderIds,
        keepDisplayOrder = keepDisplayOrderDuringSortDelay,
        todoListScrollRequest = todoListScrollRequest,
        todoListHighlightRequest = todoListHighlightRequest,
        updateUiState = updateUiState,
        onUpdateClick = ::handleUpdateClick,
    )
    DeleteConfirmationDialog(
        confirmation = deleteConfirmation,
        onConfirm = ::confirmDelete,
        onDismiss = { deleteConfirmation = null },
    )
    TodoImagePreviewDialog(
        item = imagePreviewItem,
        imageStore = imageStore,
        onChange = { item -> openTodoImagePicker(item.id) },
        onRemove = { item -> removeTodoImage(item.id) },
        onDismiss = { imagePreviewTodoId = null },
    )
}

@Composable
private fun PixelDoneScreen(
    checklists: List<TodoChecklist>,
    selectedChecklistId: String,
    selectedChecklistName: String,
    isTrashSelected: Boolean,
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
    selectedReminderRepeat: ReminderRepeat,
    onReminderRepeatChange: (ReminderRepeat) -> Unit,
    dueAtMillis: Long,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
    taskEditorVisible: Boolean,
    editingTaskId: String?,
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
    showDeadlineCountdown: Boolean,
    onDeadlineCountdownChange: (Boolean) -> Unit,
    onToggleTodo: (String, Boolean) -> Unit,
    onEditTodo: (TodoItem) -> Unit,
    onOpenTodoImage: (TodoItem) -> Unit,
    onDeleteEditingTodo: () -> Unit,
    onDeleteCompleted: () -> Unit,
    onRestoreTodo: (String) -> Unit,
    onDeleteAllTrash: () -> Unit,
    displayOrderIds: List<String>,
    keepDisplayOrder: Boolean,
    todoListScrollRequest: TodoListScrollRequest,
    todoListHighlightRequest: TodoListHighlightRequest,
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
        val density = LocalDensity.current
        val imeVisible = WindowInsets.ime.getBottom(density) > 0

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Header(
                checklists = checklists,
                selectedChecklistId = selectedChecklistId,
                selectedChecklistName = selectedChecklistName,
                isTrashSelected = isTrashSelected,
                activeCount = activeCount,
                completedCount = completedCount,
                expanded = headerExpanded,
                onExpandedChange = onHeaderExpandedChange,
                onSelectChecklist = onSelectChecklist,
                onEditChecklist = onEditChecklist,
            )
            TaskWorkspacePanel(
                todos = todos,
                isTrashSelected = isTrashSelected,
                sortMode = sortMode,
                onSortModeChange = onSortModeChange,
                hideCompleted = hideCompleted,
                onHideCompletedChange = onHideCompletedChange,
                showDeadlineCountdown = showDeadlineCountdown,
                onDeadlineCountdownChange = onDeadlineCountdownChange,
                completedCount = completedCount,
                onToggleTodo = onToggleTodo,
                onEditTodo = onEditTodo,
                onOpenTodoImage = onOpenTodoImage,
                onDeleteEditingTodo = onDeleteEditingTodo,
                onDeleteCompleted = onDeleteCompleted,
                onRestoreTodo = onRestoreTodo,
                onDeleteAllTrash = onDeleteAllTrash,
                displayOrderIds = displayOrderIds,
                keepDisplayOrder = keepDisplayOrder,
                todoListScrollRequest = todoListScrollRequest,
                todoListHighlightRequest = todoListHighlightRequest,
                taskEditorVisible = taskEditorVisible,
                editingTaskId = editingTaskId,
                isEditingTask = isEditingTask,
                onOpenTaskEditor = onOpenTaskEditor,
                onOpenChecklistEditor = onOpenChecklistEditor,
                titleInput = titleInput,
                onTitleInputChange = onTitleInputChange,
                selectedPriority = selectedPriority,
                onPriorityChange = onPriorityChange,
                selectedReminderRepeat = selectedReminderRepeat,
                onReminderRepeatChange = onReminderRepeatChange,
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
    isTrashSelected: Boolean,
    activeCount: Int,
    completedCount: Int,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelectChecklist: (String) -> Unit,
    onEditChecklist: (String) -> Unit,
) {
    val scrollState = rememberScrollState()
    val statusText = if (isTrashSelected) {
        "ITEMS ${activeCount + completedCount}"
    } else {
        "ACTIVE $activeCount  DONE $completedCount"
    }

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
                text = statusText,
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
                        canEdit = !isTrashChecklist(checklist),
                        onSelect = { onSelectChecklist(checklist.id) },
                        onEdit = { onEditChecklist(checklist.id) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "LONG PRESS \"+\" TO CREATE LIST",
                style = MaterialTheme.typography.labelSmall,
                color = ClaudeSlateLight,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ChecklistPickerRow(
    checklist: TodoChecklist,
    selected: Boolean,
    canEdit: Boolean,
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
                text = if (isTrashChecklist(checklist)) {
                    "ITEMS ${checklist.items.size}"
                } else {
                    "ACTIVE ${activeTodoCount(checklist)}  DONE ${completedTodoCount(checklist)}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = ClaudeSlateLight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (canEdit) {
            PixelSettingsButton(onClick = onEdit)
        }
    }
}

@Composable
private fun TaskWorkspacePanel(
    todos: List<TodoItem>,
    isTrashSelected: Boolean,
    sortMode: SortMode,
    onSortModeChange: (SortMode) -> Unit,
    hideCompleted: Boolean,
    onHideCompletedChange: (Boolean) -> Unit,
    showDeadlineCountdown: Boolean,
    onDeadlineCountdownChange: (Boolean) -> Unit,
    completedCount: Int,
    onToggleTodo: (String, Boolean) -> Unit,
    onEditTodo: (TodoItem) -> Unit,
    onOpenTodoImage: (TodoItem) -> Unit,
    onDeleteEditingTodo: () -> Unit,
    onDeleteCompleted: () -> Unit,
    onRestoreTodo: (String) -> Unit,
    onDeleteAllTrash: () -> Unit,
    displayOrderIds: List<String>,
    keepDisplayOrder: Boolean,
    todoListScrollRequest: TodoListScrollRequest,
    todoListHighlightRequest: TodoListHighlightRequest,
    taskEditorVisible: Boolean,
    editingTaskId: String?,
    isEditingTask: Boolean,
    onOpenTaskEditor: () -> Unit,
    onOpenChecklistEditor: () -> Unit,
    titleInput: String,
    onTitleInputChange: (String) -> Unit,
    selectedPriority: TodoPriority,
    onPriorityChange: (TodoPriority) -> Unit,
    selectedReminderRepeat: ReminderRepeat,
    onReminderRepeatChange: (ReminderRepeat) -> Unit,
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
    compactForKeyboard: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PixelReadFrameInset),
    ) {
        TodoListPanel(
            todos = todos,
            isTrashSelected = isTrashSelected,
            sortMode = sortMode,
            onSortModeChange = onSortModeChange,
            hideCompleted = hideCompleted,
            onHideCompletedChange = onHideCompletedChange,
            showDeadlineCountdown = showDeadlineCountdown,
            onDeadlineCountdownChange = onDeadlineCountdownChange,
            completedCount = completedCount,
            onToggleTodo = onToggleTodo,
            onEditTodo = onEditTodo,
            onOpenTodoImage = onOpenTodoImage,
            onDeleteCompleted = onDeleteCompleted,
            onRestoreTodo = onRestoreTodo,
            onDeleteAllTrash = onDeleteAllTrash,
            displayOrderIds = displayOrderIds,
            keepDisplayOrder = keepDisplayOrder,
            todoListScrollRequest = todoListScrollRequest,
            todoListHighlightRequest = todoListHighlightRequest,
            showNewTaskButton = !isTrashSelected && !taskEditorVisible && !checklistEditorVisible,
            onOpenTaskEditor = onOpenTaskEditor,
            onOpenChecklistEditor = onOpenChecklistEditor,
            editingTaskId = editingTaskId,
            onCancelEdit = onCancelEdit,
            modifier = Modifier.weight(1f),
        )
        if (!isTrashSelected && taskEditorVisible) {
            TaskEditorPanel(
                titleInput = titleInput,
                onTitleInputChange = onTitleInputChange,
                selectedPriority = selectedPriority,
                onPriorityChange = onPriorityChange,
                selectedReminderRepeat = selectedReminderRepeat,
                onReminderRepeatChange = onReminderRepeatChange,
                dueAtMillis = dueAtMillis,
                onPickDate = onPickDate,
                onPickTime = onPickTime,
                isEditing = isEditingTask,
                onSubmitTodo = onSubmitTodo,
                onCancelEdit = onCancelEdit,
                onCloseNewTask = onCancelEdit,
                onDeleteTodo = onDeleteEditingTodo,
                compactForKeyboard = compactForKeyboard,
            )
        } else if (!isTrashSelected && checklistEditorVisible) {
            ChecklistEditorPanel(
                nameInput = checklistNameInput,
                onNameInputChange = onChecklistNameInputChange,
                isEditing = isEditingChecklist,
                errorText = checklistEditorError,
                canDeleteChecklist = canDeleteChecklist,
                onSubmitChecklist = onSubmitChecklist,
                onCancelEdit = onCancelEdit,
                onDeleteChecklist = onDeleteChecklist,
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
    selectedReminderRepeat: ReminderRepeat,
    onReminderRepeatChange: (ReminderRepeat) -> Unit,
    dueAtMillis: Long,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
    isEditing: Boolean,
    onSubmitTodo: () -> Boolean,
    onCancelEdit: () -> Unit,
    onCloseNewTask: () -> Unit,
    onDeleteTodo: () -> Unit,
    compactForKeyboard: Boolean,
) {
    val focusManager = LocalFocusManager.current
    val hapticFeedback = LocalHapticFeedback.current
    val titleFocusRequester = remember { FocusRequester() }

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
            modifier = Modifier.fillMaxWidth(),
        ) {
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
            PrioritySlider(
                selected = selectedPriority,
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
            Text(
                text = "REPEAT",
                style = MaterialTheme.typography.labelMedium,
                color = ClaudeSlateLight,
            )
            Spacer(modifier = Modifier.height(6.dp))
            PixelSegmentedControl(
                options = ReminderRepeat.entries,
                selected = selectedReminderRepeat,
                label = { it.uiLabel() },
                onSelected = onReminderRepeatChange,
            )
            if (isEditing) {
                Spacer(modifier = Modifier.height(12.dp))
                PixelButton(
                    text = "DELETE TASK",
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        onDeleteTodo()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    destructive = true,
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
    compactForKeyboard: Boolean,
) {
    val focusManager = LocalFocusManager.current
    val hapticFeedback = LocalHapticFeedback.current
    val nameFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isEditing) {
        if (!isEditing) {
            nameFocusRequester.requestFocus()
        }
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
            modifier = Modifier.fillMaxWidth(),
        ) {
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
            if (isEditing) {
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
                Spacer(modifier = Modifier.height(12.dp))
            }
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
        }
    }
}

@Composable
private fun TodoListPanel(
    todos: List<TodoItem>,
    isTrashSelected: Boolean,
    sortMode: SortMode,
    onSortModeChange: (SortMode) -> Unit,
    hideCompleted: Boolean,
    onHideCompletedChange: (Boolean) -> Unit,
    showDeadlineCountdown: Boolean,
    onDeadlineCountdownChange: (Boolean) -> Unit,
    completedCount: Int,
    onToggleTodo: (String, Boolean) -> Unit,
    onEditTodo: (TodoItem) -> Unit,
    onOpenTodoImage: (TodoItem) -> Unit,
    onDeleteCompleted: () -> Unit,
    onRestoreTodo: (String) -> Unit,
    onDeleteAllTrash: () -> Unit,
    displayOrderIds: List<String>,
    keepDisplayOrder: Boolean,
    todoListScrollRequest: TodoListScrollRequest,
    todoListHighlightRequest: TodoListHighlightRequest,
    showNewTaskButton: Boolean,
    onOpenTaskEditor: () -> Unit,
    onOpenChecklistEditor: () -> Unit,
    editingTaskId: String?,
    onCancelEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sortedVisibleItems = if (isTrashSelected) {
        todos
    } else {
        visibleTodos(todos, sortMode, hideCompleted)
    }
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
    val context = LocalContext.current
    var listNowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var listClockActive by remember { mutableStateOf(true) }
    var activeHighlightedTodoIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var handledPreserveViewportSequence by remember { mutableStateOf(0) }
    val visibleDueAtMillis = if (isTrashSelected) {
        emptyList()
    } else {
        visibleItems.map { it.dueAtMillis }
    }
    val shouldPreserveViewport =
        todoListScrollRequest.intent is TodoListScrollIntent.PreserveViewport &&
            todoListScrollRequest.sequence != handledPreserveViewportSequence &&
            visibleItems.isNotEmpty()

    DisposableEffect(context) {
        val lifecycle = (context as? ComponentActivity)?.lifecycle
        if (lifecycle == null) {
            onDispose {}
        } else {
            listClockActive = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (listClockActive) {
                listNowMillis = System.currentTimeMillis()
            }
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        listNowMillis = System.currentTimeMillis()
                        listClockActive = true
                    }
                    Lifecycle.Event.ON_PAUSE -> {
                        listClockActive = false
                    }
                    else -> Unit
                }
            }
            lifecycle.addObserver(observer)
            onDispose {
                lifecycle.removeObserver(observer)
            }
        }
    }

    LaunchedEffect(listClockActive, visibleDueAtMillis, showDeadlineCountdown) {
        if (listClockActive) {
            while (true) {
                val nowMillis = System.currentTimeMillis()
                listNowMillis = nowMillis
                val delayMillis = nextTodoListClockRefreshDelayMillis(
                    nowMillis = nowMillis,
                    dueAtMillis = visibleDueAtMillis,
                    showDeadlineCountdown = showDeadlineCountdown,
                ) ?: break
                delay(delayMillis)
            }
        }
    }

    LaunchedEffect(todoListHighlightRequest.sequence) {
        activeHighlightedTodoIds = if (todoListHighlightRequest.sequence > 0) {
            todoListHighlightRequest.ids
        } else {
            emptySet()
        }
        if (activeHighlightedTodoIds.isNotEmpty()) {
            delay(CompletionSortDelayMillis)
            activeHighlightedTodoIds = emptySet()
        }
    }

    SideEffect {
        if (shouldPreserveViewport) {
            listState.requestScrollToItem(
                index = listState.firstVisibleItemIndex.coerceIn(0, visibleItems.lastIndex),
                scrollOffset = listState.firstVisibleItemScrollOffset,
            )
            handledPreserveViewportSequence = todoListScrollRequest.sequence
        }
    }

    LaunchedEffect(todoListScrollRequest.sequence) {
        when (val intent = todoListScrollRequest.intent) {
            TodoListScrollIntent.KeepPosition -> Unit
            TodoListScrollIntent.PreserveViewport -> Unit
            TodoListScrollIntent.ScrollToTop -> {
                if (visibleItems.isNotEmpty()) {
                    listState.scrollToItem(0)
                }
            }
            is TodoListScrollIntent.RevealTodos -> {
                firstRevealTargetIndex(
                    visibleItemIds = visibleItemIds,
                    targetIds = intent.ids,
                )?.let { itemIndex ->
                    if (itemIndex >= 0) {
                        listState.scrollToItem(itemIndex)
                    }
                }
            }
        }
    }

    PixelPanel(modifier = modifier) {
        if (isTrashSelected) {
            PixelButton(
                text = "DELETE ALL",
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    onDeleteAllTrash()
                },
                enabled = todos.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                destructive = true,
            )
        } else {
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
                PixelButton(
                    text = "DDL",
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onDeadlineCountdownChange(!showDeadlineCountdown)
                    },
                    modifier = Modifier.weight(1f),
                    selected = showDeadlineCountdown,
                )
                PixelBatchDeleteDoneButton(
                    onClick = onDeleteCompleted,
                    enabled = completedCount > 0,
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            if (visibleItems.isEmpty()) {
                EmptyState(
                    text = if (isTrashSelected) {
                        "Trash is empty."
                    } else if (todos.isEmpty()) {
                        "Add a task to begin."
                    } else {
                        "Done tasks are hidden."
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 4.dp),
                ) {
                    items(visibleItems, key = { it.id }) { item ->
                        if (isTrashSelected) {
                            TrashTodoRow(
                                item = item,
                                onRestoreTodo = onRestoreTodo,
                            )
                        } else {
                            TodoRow(
                                item = item,
                                onToggleTodo = onToggleTodo,
                                onEditTodo = onEditTodo,
                                editingTaskId = editingTaskId,
                                onCancelEdit = onCancelEdit,
                                onOpenTodoImage = onOpenTodoImage,
                                nowMillis = listNowMillis,
                                showDeadlineCountdown = showDeadlineCountdown,
                                highlighted = item.id in activeHighlightedTodoIds,
                            )
                        }
                    }
                }
            }
            if (!isTrashSelected && showNewTaskButton) {
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

internal fun firstRevealTargetIndex(
    visibleItemIds: List<String>,
    targetIds: Set<String>,
): Int? {
    if (targetIds.isEmpty()) return null
    val targetSet = targetIds.toSet()
    return visibleItemIds.indexOfFirst { it in targetSet }
        .takeIf { it >= 0 }
}

internal fun nextTodoListClockRefreshDelayMillis(
    nowMillis: Long,
    dueAtMillis: List<Long>,
    showDeadlineCountdown: Boolean,
): Long? {
    val activeDueAtMillis = dueAtMillis.filter { it > 0L }
    if (activeDueAtMillis.isEmpty()) return null

    val nextDueDelayMillis = activeDueAtMillis
        .filter { it > nowMillis }
        .minOfOrNull { it - nowMillis }

    if (!showDeadlineCountdown) {
        return nextDueDelayMillis?.coerceAtLeast(1L)
    }

    val nextMinuteDelayMillis = millisUntilNextMinuteBoundary(nowMillis)
    return listOfNotNull(nextDueDelayMillis, nextMinuteDelayMillis)
        .minOrNull()
        ?.coerceAtLeast(1L)
}

private fun millisUntilNextMinuteBoundary(nowMillis: Long): Long {
    val remainder = ((nowMillis % MinuteMillis) + MinuteMillis) % MinuteMillis
    return if (remainder == 0L) MinuteMillis else MinuteMillis - remainder
}

internal enum class TodoRowClickAction {
    Edit,
    CancelEdit,
}

internal fun todoRowClickAction(
    itemId: String,
    editingTaskId: String?,
): TodoRowClickAction {
    return if (itemId == editingTaskId) {
        TodoRowClickAction.CancelEdit
    } else {
        TodoRowClickAction.Edit
    }
}

internal fun PendingTodoToggleFeedback.recordTodoToggle(
    id: String,
    wasCompleted: Boolean,
    checked: Boolean,
): PendingTodoToggleFeedback {
    val initialStates = if (id in initialCompletedById) {
        initialCompletedById
    } else {
        initialCompletedById + (id to wasCompleted)
    }
    return copy(
        initialCompletedById = initialStates,
        latestCompletedById = latestCompletedById + (id to checked),
    )
}

@Composable
private fun TodoRow(
    item: TodoItem,
    onToggleTodo: (String, Boolean) -> Unit,
    onEditTodo: (TodoItem) -> Unit,
    editingTaskId: String?,
    onCancelEdit: () -> Unit,
    onOpenTodoImage: (TodoItem) -> Unit,
    nowMillis: Long,
    showDeadlineCountdown: Boolean,
    highlighted: Boolean,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val itemBackground = if (item.completed) ClaudeCactus.copy(alpha = 0.35f) else ClaudeIvory
    val borderColor by animateColorAsState(
        targetValue = if (highlighted) ClaudeClayInteractive else ClaudeGray300,
        animationSpec = tween(durationMillis = TodoHighlightFadeMillis),
        label = "todoRowHighlightBorder",
    )
    val borderWidth = if (highlighted) 2.dp else 1.dp
    val dueDateTime = item.dueAtMillis.formatDateTime()
    val dueDateTimeColor = if (item.dueAtMillis.isExpired(nowMillis)) PixelError else ClaudeSlateLight
    val repeatText = if (item.reminderRepeat != ReminderRepeat.NONE) {
        "  ${item.reminderRepeat.uiLabel()}"
    } else {
        ""
    }
    val showXHighAlarmIcon = item.priority == TodoPriority.XHIGH && !item.completed

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(borderWidth, borderColor, RectangleShape)
            .background(itemBackground)
            .clickable {
                when (todoRowClickAction(item.id, editingTaskId)) {
                    TodoRowClickAction.Edit -> onEditTodo(item)
                    TodoRowClickAction.CancelEdit -> onCancelEdit()
                }
            }
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
                onToggleTodo(item.id, checked)
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
            Row(
                modifier = Modifier.heightIn(min = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    modifier = if (showXHighAlarmIcon) Modifier.weight(1f, fill = false) else Modifier,
                    text = item.subtitleText(
                        nowMillis = nowMillis,
                        dueDateTime = dueDateTime,
                        dueDateTimeColor = dueDateTimeColor,
                        repeatText = repeatText,
                        showDeadlineCountdown = showDeadlineCountdown,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = ClaudeSlateLight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (showXHighAlarmIcon) {
                    Spacer(modifier = Modifier.width(4.dp))
                    PixelAlarmIcon(
                        color = ClaudeSlateLight,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        PixelItemImageButton(
            hasImage = item.imageFileName != null,
            onClick = { onOpenTodoImage(item) },
        )
    }
}

@Composable
private fun TrashTodoRow(
    item: TodoItem,
    onRestoreTodo: (String) -> Unit,
) {
    val itemBackground = if (item.completed) ClaudeCactus.copy(alpha = 0.35f) else ClaudeIvory
    val sourceName = item.trashedFromChecklistName?.takeIf { it.isNotBlank() }
        ?: DefaultChecklistName

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ClaudeGray300, RectangleShape)
            .background(itemBackground)
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
            onCheckedChange = null,
            enabled = false,
            colors = CheckboxDefaults.colors(
                checkedColor = ClaudeMineral,
                uncheckedColor = ClaudeGray600,
                disabledCheckedColor = ClaudeMineral.copy(alpha = 0.45f),
                disabledUncheckedColor = ClaudeGray300,
                disabledIndeterminateColor = ClaudeGray300,
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
                text = "FROM $sourceName",
                style = MaterialTheme.typography.labelSmall,
                color = ClaudeSlateLight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PixelRestoreButton(
            onClick = { onRestoreTodo(item.id) },
        )
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
    val message = updateUiState.message
    if (message != null) {
        val messageColor = when (updateUiState.status) {
            UpdateUiStatus.Offline -> PixelError
            UpdateUiStatus.Available,
            UpdateUiStatus.Downloading,
            UpdateUiStatus.Installing,
            -> ClaudeClay
            else -> ClaudeSlateDark
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(PixelDoneFooterHeight)
                .clickable(
                    enabled = updateUiState.status == UpdateUiStatus.Available,
                    onClick = onUpdateClick,
                )
                .semantics { contentDescription = updateUiState.contentDescription },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = message,
                color = messageColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

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
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clickable(
                    enabled = state.status != UpdateUiStatus.Checking &&
                        state.status != UpdateUiStatus.Downloading &&
                        state.status != UpdateUiStatus.Installing,
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
private fun TodoImagePreviewDialog(
    item: TodoItem?,
    imageStore: TodoImageStore,
    onChange: (TodoItem) -> Unit,
    onRemove: (TodoItem) -> Unit,
    onDismiss: () -> Unit,
) {
    if (item == null) return

    val imageFile = remember(item.imageFileName) { imageStore.imageFile(item.imageFileName) }
    val imageLastModified = remember(imageFile?.absolutePath) { imageFile?.lastModified() ?: 0L }
    val imageLoadState by produceState<TodoImagePreviewLoadState>(
        initialValue = TodoImagePreviewLoadState.Loading,
        item.id,
        item.imageFileName,
        imageLastModified,
    ) {
        value = TodoImagePreviewLoadState.Loading
        val bitmap = withContext(Dispatchers.IO) {
            imageStore.loadPreviewBitmap(item.imageFileName)
        }
        value = bitmap
            ?.let { TodoImagePreviewLoadState.Ready(it.asImageBitmap()) }
            ?: TodoImagePreviewLoadState.Unavailable
    }
    val imageBitmap = (imageLoadState as? TodoImagePreviewLoadState.Ready)?.bitmap
    var previewScale by remember(item.id, item.imageFileName) { mutableStateOf(1f) }
    var previewOffset by remember(item.id, item.imageFileName) { mutableStateOf(Offset.Zero) }
    var previewViewportSize by remember(item.id, item.imageFileName) { mutableStateOf(Size.Zero) }
    val previewGestureModifier = imageBitmap?.let { bitmap ->
        Modifier.pointerInput(item.id, item.imageFileName, bitmap, previewViewportSize) {
            detectTransformGestures(panZoomLock = true) { centroid, panChange, zoomChange, _ ->
                val transform = calculatePreviewTransform(
                    currentScale = previewScale,
                    currentOffset = previewOffset,
                    zoomChange = zoomChange,
                    panChange = panChange,
                    centroid = centroid,
                    viewportSize = previewViewportSize,
                    imageWidth = bitmap.width,
                    imageHeight = bitmap.height,
                )
                previewScale = transform.scale
                previewOffset = transform.offset
            }
        }
    } ?: Modifier

    LaunchedEffect(item.id, item.imageFileName, imageBitmap, previewScale, previewViewportSize) {
        previewOffset = imageBitmap?.let { bitmap ->
            clampPreviewOffset(
                offset = previewOffset,
                scale = previewScale,
                imageWidth = bitmap.width,
                imageHeight = bitmap.height,
                viewportSize = previewViewportSize,
            )
        } ?: Offset.Zero
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = "Task image",
                    style = MaterialTheme.typography.titleMedium,
                    color = ClaudeSlateDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                PixelDialogCloseButton(onClick = onDismiss)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ClaudeSlateLight,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 320.dp)
                        .border(1.dp, ClaudeGray300, RectangleShape)
                        .background(ClaudeIvory)
                        .clipToBounds()
                        .onSizeChanged { size ->
                            previewViewportSize = Size(
                                width = size.width.toFloat(),
                                height = size.height.toFloat(),
                            )
                        }
                        .then(previewGestureModifier),
                    contentAlignment = Alignment.Center,
                ) {
                    when (val state = imageLoadState) {
                        TodoImagePreviewLoadState.Loading -> {
                            Text(
                                text = "Loading image...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ClaudeSlateLight,
                            )
                        }
                        TodoImagePreviewLoadState.Unavailable -> {
                            Text(
                                text = "Image unavailable.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ClaudeSlateLight,
                            )
                        }
                        is TodoImagePreviewLoadState.Ready -> {
                            Image(
                                bitmap = state.bitmap,
                                contentDescription = "Task image preview",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = previewScale
                                        scaleY = previewScale
                                        translationX = previewOffset.x
                                        translationY = previewOffset.y
                                    },
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PixelButton(
                    text = "CHANGE",
                    onClick = { onChange(item) },
                    primary = true,
                )
                PixelButton(
                    text = "REMOVE",
                    onClick = { onRemove(item) },
                    destructive = true,
                )
            }
        },
        shape = RectangleShape,
        containerColor = ClaudeGray100,
        tonalElevation = 0.dp,
    )
}

@Composable
private fun PixelDialogCloseButton(
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .border(1.dp, ClaudeGray300, RectangleShape)
            .background(ClaudeIvory)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "CLOSE",
            style = MaterialTheme.typography.labelSmall,
            color = ClaudeSlateDark,
            maxLines = 1,
        )
    }
}

internal data class PreviewTransform(
    val scale: Float,
    val offset: Offset,
)

internal fun calculatePreviewTransform(
    currentScale: Float,
    currentOffset: Offset,
    zoomChange: Float,
    panChange: Offset,
    centroid: Offset,
    viewportSize: Size,
    imageWidth: Int,
    imageHeight: Int,
): PreviewTransform {
    val safeCurrentScale = currentScale.coerceIn(MinPreviewScale, MaxPreviewScale)
    val safeZoomChange = if (zoomChange.isFinite() && zoomChange > 0f) zoomChange else 1f
    val nextScale = (safeCurrentScale * safeZoomChange).coerceIn(MinPreviewScale, MaxPreviewScale)
    val scaleChange = nextScale / safeCurrentScale
    val viewportCenter = Offset(
        x = viewportSize.width / 2f,
        y = viewportSize.height / 2f,
    )
    val centroidFromCenter = centroid - viewportCenter
    val nextOffset = (currentOffset * scaleChange) +
        (centroidFromCenter * (1f - scaleChange)) +
        panChange

    return PreviewTransform(
        scale = nextScale,
        offset = clampPreviewOffset(
            offset = nextOffset,
            scale = nextScale,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            viewportSize = viewportSize,
        ),
    )
}

private fun clampPreviewOffset(
    offset: Offset,
    scale: Float,
    imageWidth: Int,
    imageHeight: Int,
    viewportSize: Size,
): Offset {
    if (viewportSize.width <= 0f || viewportSize.height <= 0f) return Offset.Zero
    val fittedImageSize = fittedPreviewImageSize(
        imageWidth = imageWidth,
        imageHeight = imageHeight,
        viewportSize = viewportSize,
    )
    val maxX = max(0f, ((fittedImageSize.width * scale) - viewportSize.width) / 2f)
    val maxY = max(0f, ((fittedImageSize.height * scale) - viewportSize.height) / 2f)
    return Offset(
        x = offset.x.coerceIn(-maxX, maxX),
        y = offset.y.coerceIn(-maxY, maxY),
    )
}

private fun fittedPreviewImageSize(
    imageWidth: Int,
    imageHeight: Int,
    viewportSize: Size,
): Size {
    if (imageWidth <= 0 || imageHeight <= 0) return Size.Zero
    val fitScale = minOf(
        viewportSize.width / imageWidth.toFloat(),
        viewportSize.height / imageHeight.toFloat(),
    )
    return Size(
        width = imageWidth * fitScale,
        height = imageHeight * fitScale,
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
        is DeleteConfirmation.TrashTodos -> "Delete trash?"
    }
    val bodyText = when (confirmation) {
        is DeleteConfirmation.SingleTodo -> "This will move \"${confirmation.title}\" to TRASH."
        is DeleteConfirmation.CompletedTodos -> "This will move ${confirmation.count} completed task(s) to TRASH."
        is DeleteConfirmation.Checklist ->
            "This will move \"${confirmation.name}\" and ${confirmation.todoCount} task(s) to TRASH."
        is DeleteConfirmation.TrashTodos ->
            "This will permanently delete ${confirmation.count} task(s)."
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

internal fun isDueExpired(dueAtMillis: Long, nowMillis: Long): Boolean {
    return dueAtMillis > 0L && dueAtMillis <= nowMillis
}

private fun Long.isExpired(nowMillis: Long): Boolean {
    return isDueExpired(this, nowMillis)
}

internal fun formatDeadlineCountdown(dueAtMillis: Long, nowMillis: Long): String {
    if (dueAtMillis <= 0L) return "DDL --"

    val millisUntilDue = dueAtMillis - nowMillis
    val absoluteMillis = if (millisUntilDue < 0L) {
        -millisUntilDue
    } else {
        millisUntilDue
    }
    val totalMinutes = absoluteMillis / 60_000L
    val days = totalMinutes / (24L * 60L)
    val hours = (totalMinutes % (24L * 60L)) / 60L
    val minutes = totalMinutes % 60L
    val value = "${days}D ${hours.toTwoDigits()}H ${minutes.toTwoDigits()}M"

    return if (millisUntilDue <= 0L) {
        "DDL OVERDUE $value"
    } else {
        "DDL $value"
    }
}

private fun Long.toTwoDigits(): String {
    return toString().padStart(2, '0')
}

private fun TodoItem.subtitleText(
    nowMillis: Long,
    dueDateTime: String,
    dueDateTimeColor: Color,
    repeatText: String,
    showDeadlineCountdown: Boolean,
) = buildAnnotatedString {
    if (showDeadlineCountdown) {
        withStyle(SpanStyle(color = if (dueAtMillis.isExpired(nowMillis)) PixelError else ClaudeSlateLight)) {
            append(formatDeadlineCountdown(dueAtMillis, nowMillis))
        }
    } else {
        append(priority.uiLabel())
        append("  ")
        withStyle(SpanStyle(color = dueDateTimeColor)) {
            append(dueDateTime)
        }
        append(repeatText)
    }
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
            isTrashSelected = false,
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
            selectedReminderRepeat = ReminderRepeat.NONE,
            onReminderRepeatChange = {},
            dueAtMillis = defaultDueAtMillis(),
            onPickDate = {},
            onPickTime = {},
            taskEditorVisible = false,
            editingTaskId = null,
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
            showDeadlineCountdown = false,
            onDeadlineCountdownChange = {},
            onToggleTodo = { _, _ -> },
            onEditTodo = {},
            onOpenTodoImage = {},
            onDeleteEditingTodo = {},
            onDeleteCompleted = {},
            onRestoreTodo = {},
            onDeleteAllTrash = {},
            displayOrderIds = emptyList(),
            keepDisplayOrder = false,
            todoListScrollRequest = previewScrollRequest(),
            todoListHighlightRequest = previewHighlightRequest(),
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
            isTrashSelected = false,
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
            selectedReminderRepeat = ReminderRepeat.WEEKLY,
            onReminderRepeatChange = {},
            dueAtMillis = defaultDueAtMillis(),
            onPickDate = {},
            onPickTime = {},
            taskEditorVisible = true,
            editingTaskId = "1",
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
            showDeadlineCountdown = true,
            onDeadlineCountdownChange = {},
            onToggleTodo = { _, _ -> },
            onEditTodo = {},
            onOpenTodoImage = {},
            onDeleteEditingTodo = {},
            onDeleteCompleted = {},
            onRestoreTodo = {},
            onDeleteAllTrash = {},
            displayOrderIds = emptyList(),
            keepDisplayOrder = false,
            todoListScrollRequest = previewScrollRequest(),
            todoListHighlightRequest = previewHighlightRequest(),
            updateUiState = AppUpdateUiState(status = UpdateUiStatus.Available),
            onUpdateClick = {},
        )
    }
}

private fun previewTodos(): List<TodoItem> {
    val now = defaultDueAtMillis()
    return listOf(
        TodoItem("1", "Send the build to the test phone", TodoPriority.HIGH, now, false, 1L),
        TodoItem(
            "2",
            "Plan tomorrow's tiny wins",
            TodoPriority.MEDIUM,
            now + 3_600_000L,
            false,
            2L,
            imageFileName = "preview.img",
        ),
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
        TodoChecklist(
            id = TrashChecklistId,
            name = TrashChecklistName,
            items = emptyList(),
            createdAtMillis = 3L,
        ),
    )
}

private fun previewScrollRequest(): TodoListScrollRequest {
    return TodoListScrollRequest(
        sequence = 0,
        intent = TodoListScrollIntent.KeepPosition,
    )
}

private fun previewHighlightRequest(): TodoListHighlightRequest {
    return TodoListHighlightRequest(
        sequence = 0,
        ids = emptySet(),
    )
}
