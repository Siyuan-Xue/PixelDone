package com.milesxue.pixeldone.ui.todo

import android.Manifest
import android.app.NotificationManager
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
import com.milesxue.pixeldone.ui.theme.PixelDoneTheme
import com.milesxue.pixeldone.applyPixelDoneLanguage
import com.milesxue.pixeldone.R
import com.milesxue.pixeldone.ui.todo.components.PixelAlarmIcon
import com.milesxue.pixeldone.ui.todo.components.PixelButton
import com.milesxue.pixeldone.ui.todo.components.PixelItemDeleteButton
import com.milesxue.pixeldone.ui.todo.components.PixelItemImageButton
import com.milesxue.pixeldone.ui.todo.components.PixelPanel
import com.milesxue.pixeldone.ui.todo.components.PixelRestoreButton
import com.milesxue.pixeldone.ui.todo.components.PixelSegmentedControl
import com.milesxue.pixeldone.ui.todo.components.cloudLoginLogoutPolylines
import com.milesxue.pixeldone.ui.todo.components.PixelSettingsButton
import com.milesxue.pixeldone.ui.todo.components.PrioritySlider
import com.milesxue.pixeldone.ui.todo.components.priorityColor
import com.milesxue.pixeldone.ui.todo.components.uiLabel
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.milesxue.pixeldone.BuildConfig
import com.milesxue.pixeldone.applyPixelDoneSystemBars
import com.milesxue.pixeldone.data.image.TodoImageStore
import com.milesxue.pixeldone.di.pixelDoneAppContainer
import com.milesxue.pixeldone.data.update.AppUpdateDownload
import com.milesxue.pixeldone.data.update.AppUpdateDownloadCompletion
import com.milesxue.pixeldone.data.update.AppUpdateDownloadProgress
import com.milesxue.pixeldone.data.update.AppUpdateDownloadResult
import com.milesxue.pixeldone.data.update.AppUpdateCheckResult
import com.milesxue.pixeldone.data.update.AppUpdateInfo
import com.milesxue.pixeldone.data.update.appUpdateDownloadRequests
import com.milesxue.pixeldone.domain.sync.AuthSession
import com.milesxue.pixeldone.domain.sync.ConflictResolutionChoice
import com.milesxue.pixeldone.domain.sync.SyncConflictEntry
import com.milesxue.pixeldone.domain.sync.SyncCoordinatorStatus
import com.milesxue.pixeldone.domain.sync.SyncRunState
import com.milesxue.pixeldone.domain.todo.AllDockActions
import com.milesxue.pixeldone.domain.todo.DefaultChecklistId
import com.milesxue.pixeldone.domain.todo.DefaultChecklistName
import com.milesxue.pixeldone.domain.todo.DockAction
import com.milesxue.pixeldone.domain.todo.DockConfig
import com.milesxue.pixeldone.domain.todo.DockPlusPlacement
import com.milesxue.pixeldone.domain.todo.MaxDockActions
import com.milesxue.pixeldone.domain.todo.ReminderCapability
import com.milesxue.pixeldone.domain.todo.ReminderRepeat
import com.milesxue.pixeldone.domain.todo.SettingsChecklistId
import com.milesxue.pixeldone.domain.todo.SettingsChecklistName
import com.milesxue.pixeldone.domain.todo.SortMode
import com.milesxue.pixeldone.domain.todo.TodoChecklist
import com.milesxue.pixeldone.domain.todo.TodoChecklistState
import com.milesxue.pixeldone.domain.todo.TodoItem
import com.milesxue.pixeldone.domain.todo.TodoPriority
import com.milesxue.pixeldone.domain.todo.TrashChecklistId
import com.milesxue.pixeldone.domain.todo.AppLanguage
import com.milesxue.pixeldone.domain.todo.TrashChecklistName
import com.milesxue.pixeldone.domain.todo.activeTodoCount
import com.milesxue.pixeldone.domain.todo.advanceRepeatingTodoAfterReminder
import com.milesxue.pixeldone.domain.todo.advanceRepeatingTodosAfterReminder
import com.milesxue.pixeldone.domain.todo.completedTodoCount
import com.milesxue.pixeldone.domain.todo.createTodoChecklist
import com.milesxue.pixeldone.domain.todo.createTodoItem
import com.milesxue.pixeldone.domain.todo.deleteAllTrashTodos
import com.milesxue.pixeldone.domain.todo.deleteTodoChecklist
import com.milesxue.pixeldone.domain.todo.isChecklistNameAvailable
import com.milesxue.pixeldone.domain.todo.isNormalChecklist
import com.milesxue.pixeldone.domain.todo.isSettingsChecklist
import com.milesxue.pixeldone.domain.todo.isSettingsChecklistId
import com.milesxue.pixeldone.domain.todo.isSpecialChecklistId
import com.milesxue.pixeldone.domain.todo.isTrashChecklist
import com.milesxue.pixeldone.domain.todo.moveCompletedTodosToTrash
import com.milesxue.pixeldone.domain.todo.moveTodoItemToTrash
import com.milesxue.pixeldone.domain.todo.moveTodoItemsToChecklist
import com.milesxue.pixeldone.domain.todo.normalChecklistCount
import com.milesxue.pixeldone.domain.todo.normalTodos
import com.milesxue.pixeldone.domain.todo.normalizeDockActions
import com.milesxue.pixeldone.domain.todo.normalizeRepeatingDueAtMillis
import com.milesxue.pixeldone.domain.todo.renameTodoChecklist
import com.milesxue.pixeldone.domain.todo.requiredReminderCapabilities
import com.milesxue.pixeldone.domain.todo.restoreTodoFromTrash
import com.milesxue.pixeldone.domain.todo.selectTodoChecklist
import com.milesxue.pixeldone.domain.todo.selectedChecklistOf
import com.milesxue.pixeldone.domain.todo.snoozeTodoAfterReminder
import com.milesxue.pixeldone.domain.todo.snoozeTodosAfterReminder
import com.milesxue.pixeldone.domain.todo.toggleDockActionSelection
import com.milesxue.pixeldone.domain.todo.toggleTodoCompletion
import com.milesxue.pixeldone.domain.todo.trashTodos
import com.milesxue.pixeldone.domain.todo.updateChecklistItems
import com.milesxue.pixeldone.domain.todo.updateTodoImageFileName
import com.milesxue.pixeldone.domain.todo.updateTodoItem
import com.milesxue.pixeldone.domain.todo.visibleTodos
import com.milesxue.pixeldone.reminder.ActiveXHighAlarm
import com.milesxue.pixeldone.reminder.XHighAlarmService
import com.milesxue.pixeldone.ui.theme.PixelDoneColors
import com.milesxue.pixeldone.ui.theme.PixelDonePalette
internal const val CompletionSortDelayMillis = 2_000L
private const val MinuteMillis = 60_000L
private const val TodoHighlightFadeMillis = 180

private const val DeveloperCredit = "CODEX & XUE"
private val PixelReadTopBarContentHeight = 36.dp
private val PixelReadFrameInset = 8.dp
private val PixelDoneFooterHeight = 24.dp
private val DialogActionMinHeight = 44.dp
private const val MinPreviewScale = 1f
private const val MaxPreviewScale = 6f

private data class PermissionSettingsState(
    val notificationsGranted: Boolean,
    val exactAlarmGranted: Boolean,
    val fullScreenIntentGranted: Boolean,
    val installUpdatesGranted: Boolean,
)

private sealed interface DeleteConfirmation {
    data class SingleTodo(val id: String, val title: String) : DeleteConfirmation
    data class CompletedTodos(val count: Int) : DeleteConfirmation
    data class Checklist(val id: String, val name: String, val todoCount: Int) : DeleteConfirmation
    data class TrashTodos(val count: Int) : DeleteConfirmation
}

private enum class BatchMoveMode {
    IDLE,
    SELECTING,
    TARGET_PICKER,
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

internal data class TodoListHighlightRequest(
    val sequence: Int,
    val ids: Set<String>,
)

internal fun consumeTodoListHighlightRequest(
    request: TodoListHighlightRequest,
    consumedSequence: Int,
): TodoListHighlightRequest {
    return if (request.sequence == consumedSequence) {
        request.copy(ids = emptySet())
    } else {
        request
    }
}

internal data class ChecklistBackNavigation(
    val targetId: String,
    val remainingStack: List<String>,
)

internal fun pushChecklistBackStack(
    stack: List<String>,
    currentId: String,
    targetId: String,
    validIds: Set<String>,
): List<String> {
    val prunedStack = stack.filter { it in validIds }
    if (currentId == targetId || currentId !in validIds || targetId !in validIds) {
        return prunedStack
    }
    return prunedStack + currentId
}

internal fun nextChecklistBackNavigation(
    stack: List<String>,
    validIds: Set<String>,
    currentId: String,
): ChecklistBackNavigation? {
    val remainingStack = stack.filter { it in validIds }.toMutableList()
    while (remainingStack.isNotEmpty()) {
        val candidateId = remainingStack.removeAt(remainingStack.lastIndex)
        if (candidateId != currentId) {
            return ChecklistBackNavigation(
                targetId = candidateId,
                remainingStack = remainingStack,
            )
        }
    }
    return null
}

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
    data object CloudSignIn : EditorMode
}

@Composable
internal fun PixelDoneApp() {
    val context = LocalContext.current
    val checklistLockedText = stringResource(R.string.checklist_locked)
    val nameRequiredText = stringResource(R.string.name_required)
    val nameExistsText = stringResource(R.string.name_exists)
    val checklistSaveFailedText = stringResource(R.string.checklist_save_failed)
    val latestUpdateText = stringResource(R.string.latest_version_status, BuildConfig.VERSION_NAME)
    val getVersionTemplate = stringResource(R.string.get_version_status)
    val allowInstallText = stringResource(R.string.allow_install)
    val installingVersionTemplate = stringResource(R.string.installing_version)
    val updateFailedText = stringResource(R.string.update_failed)
    val checkingUpdateText = stringResource(R.string.checking_update)
    val updateScope = rememberCoroutineScope()
    val appContainer = remember(context) { context.pixelDoneAppContainer() }
    val todoRepository = remember(appContainer) { appContainer.todoRepository }
    val imageStore = remember(appContainer) { appContainer.todoImageStore }
    val updateService = remember(appContainer) { appContainer.updateService }
    val reminderScheduler = remember(appContainer) { appContainer.reminderScheduler }
    val activeXHighAlarmStore = remember(appContainer) { appContainer.activeXHighAlarmStore }
    val viewModelFactory = remember(appContainer) {
        PixelDoneViewModel.factory(
            todoRepository = todoRepository,
            reminderScheduler = reminderScheduler,
            settingsStore = appContainer.settingsStore,
            authSessionRepository = appContainer.authSessionRepository,
            syncCoordinator = appContainer.syncCoordinator,
        )
    }
    val viewModel: PixelDoneViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val checklistState = uiState.checklistState
    val settings = uiState.settings
    val darkTheme = settings.darkTheme
    val dockConfig = settings.dockConfig
    val neverShowUpdateDialog = settings.neverShowUpdateDialog
    val languageMode = settings.languageMode
    val syncStatusText = uiState.syncStatus.settingsLabel()
    val authSession = uiState.authSession
    val authInput = uiState.authInput
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
    var pendingUpdateInstallDownload by remember { mutableStateOf<AppUpdateDownload?>(null) }
    var deleteConfirmation by remember { mutableStateOf<DeleteConfirmation?>(null) }
    var imagePreviewTodoId by remember { mutableStateOf<String?>(null) }
    var pendingImageTodoId by remember { mutableStateOf<String?>(null) }
    var updateUiState by remember { mutableStateOf(AppUpdateUiState()) }
    var updateCheckInFlight by remember { mutableStateOf(false) }
    var activeUpdateDownload by remember { mutableStateOf<AppUpdateDownload?>(null) }
    var showUpdatePromptDialog by remember { mutableStateOf(false) }
    var updatePromptInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var showUpdateProgressDialog by remember { mutableStateOf(false) }
    var updateProgressDialogDismissed by remember { mutableStateOf(false) }
    var permissionRefreshTick by remember { mutableStateOf(0) }
    var activeXHighAlarm by remember { mutableStateOf(activeXHighAlarmStore.load()) }
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
    var batchMoveMode by remember { mutableStateOf(BatchMoveMode.IDLE) }
    var batchMoveSelectedTodoIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var batchDeleteActive by remember { mutableStateOf(false) }
    var checklistBackStack by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(languageMode) {
        applyPixelDoneLanguage(languageMode)
    }
    val selectedChecklist = selectedChecklistOf(checklistState)
    val checklistIds = checklistState.lists.map { it.id }
    val checklistIdSet = checklistIds.toSet()
    val checklistBackNavigation = nextChecklistBackNavigation(
        stack = checklistBackStack,
        validIds = checklistIdSet,
        currentId = selectedChecklist.id,
    )
    val currentChecklistState by rememberUpdatedState(checklistState)
    val isTrashSelected = isTrashChecklist(selectedChecklist)
    val isSettingsSelected = isSettingsChecklist(selectedChecklist)
    val isNormalChecklistSelected = isNormalChecklist(selectedChecklist)
    val todos = selectedChecklist.items
    val activeCount = activeTodoCount(selectedChecklist)
    val completedCount = completedTodoCount(selectedChecklist)
    val editingTaskId = (editorMode as? EditorMode.EditTask)?.id
    val editingChecklistId = (editorMode as? EditorMode.EditChecklist)?.id
    val taskEditorVisible = editorMode is EditorMode.NewTask || editorMode is EditorMode.EditTask
    val checklistEditorVisible =
        editorMode is EditorMode.NewChecklist || editorMode is EditorMode.EditChecklist
    val cloudSignInEditorVisible = editorMode is EditorMode.CloudSignIn
    val imagePreviewItem = todos.firstOrNull { it.id == imagePreviewTodoId }

    LaunchedEffect(darkTheme) {
        (context as? ComponentActivity)?.applyPixelDoneSystemBars(darkTheme)
    }

    fun cleanupInstalledUpdateIfNeeded() {
        updateService.cleanupInstalledUpdate(BuildConfig.VERSION_NAME)
    }

    LaunchedEffect(Unit) {
        cleanupInstalledUpdateIfNeeded()
        val currentTodos = normalTodos(checklistState)
        reminderScheduler.sync(currentTodos, currentTodos)
    }

    LaunchedEffect(checklistIds) {
        val prunedStack = checklistBackStack.filter { it in checklistIdSet }
        if (prunedStack != checklistBackStack) {
            checklistBackStack = prunedStack
        }
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

    LaunchedEffect(authSession.signedIn, authSession.cloudAvailable) {
        if (cloudSignInEditorVisible && (authSession.signedIn || !authSession.cloudAvailable)) {
            editorMode = EditorMode.None
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

    fun clearBatchMoveState() {
        batchMoveMode = BatchMoveMode.IDLE
        batchMoveSelectedTodoIds = emptySet()
    }

    fun clearBatchDeleteState() {
        batchDeleteActive = false
    }

    fun closeTransientControls() {
        clearBatchMoveState()
        clearBatchDeleteState()
    }

    fun consumeTodoListHighlight(sequence: Int) {
        todoListHighlightRequest = consumeTodoListHighlightRequest(
            request = todoListHighlightRequest,
            consumedSequence = sequence,
        )
    }

    fun toggleBatchMoveSelection(todoId: String) {
        if (batchMoveMode == BatchMoveMode.IDLE) return
        batchMoveSelectedTodoIds = if (todoId in batchMoveSelectedTodoIds) {
            batchMoveSelectedTodoIds - todoId
        } else {
            batchMoveSelectedTodoIds + todoId
        }
    }

    fun openBatchMoveTargetPicker() {
        if (batchMoveMode == BatchMoveMode.SELECTING && batchMoveSelectedTodoIds.isNotEmpty()) {
            clearBatchDeleteState()
            batchMoveMode = BatchMoveMode.TARGET_PICKER
        }
    }

    fun closeBatchMoveTargetPicker() {
        if (batchMoveMode == BatchMoveMode.TARGET_PICKER) {
            batchMoveMode = BatchMoveMode.SELECTING
        }
    }

    fun moveSelectedTodosToChecklist(targetChecklistId: String, orderedSelectedIds: List<String>) {
        val selectedIds = orderedSelectedIds.ifEmpty { batchMoveSelectedTodoIds.toList() }
        val selectedIdSet = selectedIds.toSet()
        val updatedState = moveTodoItemsToChecklist(
            state = checklistState,
            sourceChecklistId = selectedChecklist.id,
            targetChecklistId = targetChecklistId,
            todoIds = selectedIds,
        ) ?: return
        checklistBackStack = pushChecklistBackStack(
            stack = checklistBackStack,
            currentId = selectedChecklist.id,
            targetId = updatedState.selectedListId,
            validIds = updatedState.lists.mapTo(mutableSetOf()) { it.id },
        )
        updateChecklistState(updatedState)
        clearBatchMoveState()
        keepDisplayOrderDuringSortDelay = false
        pendingTodoToggleFeedback = PendingTodoToggleFeedback()
        displayOrderIds = emptyList()
        requestTodoListScroll(TodoListScrollIntent.RevealTodos(selectedIdSet))
        requestTodoListHighlight(selectedIdSet)
    }

    BackHandler(enabled = batchMoveMode != BatchMoveMode.IDLE) {
        when (batchMoveMode) {
            BatchMoveMode.TARGET_PICKER -> closeBatchMoveTargetPicker()
            BatchMoveMode.SELECTING -> clearBatchMoveState()
            BatchMoveMode.IDLE -> Unit
        }
    }

    BackHandler(enabled = batchDeleteActive) {
        clearBatchDeleteState()
    }

    fun hasNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun hasFullScreenIntentAccess(): Boolean {
        val canUseFullScreenIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == true
        } else {
            true
        }
        return hasFullScreenIntentAccessForSdk(Build.VERSION.SDK_INT, canUseFullScreenIntent)
    }

    fun hasInstallUpdatePermission(): Boolean {
        return context.packageManager.canRequestPackageInstalls()
    }

    fun currentPermissionSettingsState(): PermissionSettingsState {
        return PermissionSettingsState(
            notificationsGranted = hasNotificationPermission(),
            exactAlarmGranted = reminderScheduler.canScheduleExactAlarms(),
            fullScreenIntentGranted = hasFullScreenIntentAccess(),
            installUpdatesGranted = hasInstallUpdatePermission(),
        )
    }

    fun tryStartActivity(intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    fun packageSettingsIntent(action: String): Intent {
        return Intent(action).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    fun openPackageSettingsAction(action: String) {
        if (tryStartActivity(packageSettingsIntent(action))) return
        tryStartActivity(packageSettingsIntent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS))
    }

    fun openAppNotificationSettings() {
        val notificationSettingsIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        if (tryStartActivity(notificationSettingsIntent)) return
        openPackageSettingsAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
    }

    fun openFullScreenIntentSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            openAppNotificationSettings()
            return
        }

        if (tryStartActivity(packageSettingsIntent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT))) {
            return
        }
        openAppNotificationSettings()
    }

    fun openInstallUpdateSettings() {
        openPackageSettingsAction(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
    }

    fun refreshActiveXHighAlarm() {
        activeXHighAlarm = activeXHighAlarmStore.load()
    }

    fun startActiveXHighAlarmAction(action: String) {
        val alarm = activeXHighAlarm ?: activeXHighAlarmStore.load() ?: return
        context.startService(
            XHighAlarmService.actionIntent(
                context = context,
                todoIds = alarm.todoIds,
                action = action,
                companionShortItems = alarm.companionShortItems(),
                firedDueAtMillis = alarm.firedDueAtMillis,
            ),
        )
        activeXHighAlarmStore.clear()
        activeXHighAlarm = null
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
        val decision = systemReminderPermissionDecision(missingCapabilities) ?: return
        if (decision.queueFullScreenFollowUp) {
            pendingFullScreenPermissionTodoId = item.id
        }
        val action = when (decision.target) {
            SystemReminderPermissionTarget.EXACT_ALARM -> Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
            SystemReminderPermissionTarget.FULL_SCREEN_INTENT ->
                Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT
        }
        openPackageSettingsAction(action)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionRefreshTick += 1
        if (granted) {
            val currentTodos = normalTodos(currentChecklistState)
            reminderScheduler.sync(currentTodos, currentTodos)
            pendingReminderPermissionTodoId
                ?.let { id -> normalTodos(currentChecklistState).firstOrNull { it.id == id } }
                ?.let(::requestSystemReminderPermissionIfNeeded)
        }
        pendingReminderPermissionTodoId = null
    }

    fun requestNotificationPermissionFromSettings() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasNotificationPermission()
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openAppNotificationSettings()
        }
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
        if (!isNormalChecklistSelected) return
        closeTransientControls()
        clearEditor()
        dueAtMillis = defaultDueAtMillis()
        editorMode = EditorMode.NewTask
    }

    fun openNewChecklistEditor() {
        closeTransientControls()
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
        if (!isNormalChecklistSelected) return false
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
        if (!isNormalChecklistSelected) return
        closeTransientControls()
        editorMode = EditorMode.EditTask(item.id)
        titleInput = item.title
        selectedPriority = item.priority
        selectedReminderRepeat = item.reminderRepeat
        dueAtMillis = item.dueAtMillis
    }

    fun startEditingChecklist(checklist: TodoChecklist) {
        if (!isNormalChecklist(checklist)) return
        closeTransientControls()
        headerExpanded = true
        editorMode = EditorMode.EditChecklist(checklist.id)
        checklistNameInput = checklist.name
        checklistEditorError = null
    }

    fun submitChecklist(): Boolean {
        val editingId = editingChecklistId
        if (editingId != null && isSpecialChecklistId(editingId)) {
            checklistEditorError = checklistLockedText
            return false
        }
        val trimmedName = checklistNameInput.trim()
        checklistEditorError = when {
            trimmedName.isEmpty() -> nameRequiredText
            !isChecklistNameAvailable(checklistState, trimmedName, editingId) -> nameExistsText
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
            checklistEditorError = checklistSaveFailedText
            return false
        }

        if (updatedState.selectedListId != checklistState.selectedListId) {
            checklistBackStack = pushChecklistBackStack(
                stack = checklistBackStack,
                currentId = selectedChecklist.id,
                targetId = updatedState.selectedListId,
                validIds = updatedState.lists.mapTo(mutableSetOf()) { it.id },
            )
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
        if (editorMode is EditorMode.CloudSignIn) {
            viewModel.onAction(PixelDoneAction.CancelSignIn)
        }
        clearEditor()
        editorMode = EditorMode.None
    }

    fun selectChecklist(id: String, recordBackStack: Boolean = true) {
        val updatedState = selectTodoChecklist(checklistState, id) ?: return
        if (recordBackStack) {
            checklistBackStack = pushChecklistBackStack(
                stack = checklistBackStack,
                currentId = selectedChecklist.id,
                targetId = updatedState.selectedListId,
                validIds = updatedState.lists.mapTo(mutableSetOf()) { it.id },
            )
        }
        updateChecklistState(updatedState)
        closeTransientControls()
        clearEditor()
        editorMode = EditorMode.None
        keepDisplayOrderDuringSortDelay = false
        pendingTodoToggleFeedback = PendingTodoToggleFeedback()
        displayOrderIds = emptyList()
        requestTodoListScroll(TodoListScrollIntent.ScrollToTop)
        headerExpanded = false
    }

    fun navigateBackChecklist() {
        val navigation = nextChecklistBackNavigation(
            stack = checklistBackStack,
            validIds = checklistState.lists.mapTo(mutableSetOf()) { it.id },
            currentId = selectedChecklist.id,
        ) ?: return
        checklistBackStack = navigation.remainingStack
        selectChecklist(navigation.targetId, recordBackStack = false)
    }

    BackHandler(
        enabled = batchMoveMode == BatchMoveMode.IDLE && !batchDeleteActive && checklistBackNavigation != null,
    ) {
        navigateBackChecklist()
    }

    BackHandler(enabled = cloudSignInEditorVisible) {
        cancelEditing()
    }

    fun toggleBatchDeleteMode() {
        if (!isNormalChecklistSelected || todos.isEmpty()) {
            clearBatchDeleteState()
            return
        }
        clearBatchMoveState()
        batchDeleteActive = !batchDeleteActive
    }

    fun quickDeleteTodo(todoId: String) {
        if (!isNormalChecklistSelected) return
        requestTodoListScroll(TodoListScrollIntent.KeepPosition)
        val updatedState = moveTodoItemToTrash(
            state = checklistState,
            checklistId = selectedChecklist.id,
            todoId = todoId,
            trashedAtMillis = System.currentTimeMillis(),
        ) ?: return
        updateChecklistState(updatedState)
        val updatedSelectedTodos = updatedState.lists.firstOrNull { it.id == selectedChecklist.id }?.items
        if (updatedSelectedTodos?.isEmpty() == true) {
            clearBatchDeleteState()
        }
        if (imagePreviewTodoId == todoId) {
            imagePreviewTodoId = null
        }
        if (editingTaskId == todoId) {
            clearEditor()
            editorMode = EditorMode.None
        }
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

    fun latestUpdateMessage(): String = latestUpdateText

    fun availableUpdateMessage(info: AppUpdateInfo): String =
        String.format(Locale.getDefault(), getVersionTemplate, BuildConfig.VERSION_NAME, info.version)

    fun setNeverShowUpdateDialog(neverShow: Boolean) {
        viewModel.onAction(PixelDoneAction.SetShowUpdateDialogs(!neverShow))
    }

    fun setShowUpdateDialogs(showDialogs: Boolean) {
        viewModel.onAction(PixelDoneAction.SetShowUpdateDialogs(showDialogs))
    }

    fun setDarkThemePreference(enabled: Boolean) {
        viewModel.onAction(PixelDoneAction.SetDarkTheme(enabled))
    }

    fun setDockConfigPreference(config: DockConfig) {
        viewModel.onAction(PixelDoneAction.SetDockConfig(config.normalized()))
    }
    fun setLanguagePreference(language: AppLanguage) {
        viewModel.onAction(PixelDoneAction.SetLanguage(language))
    }
    fun setAuthEmail(email: String) {
        viewModel.onAction(PixelDoneAction.SetAuthEmail(email))
    }

    fun setAuthPassword(password: String) {
        viewModel.onAction(PixelDoneAction.SetAuthPassword(password))
    }

    fun setCloudAuthMode(mode: CloudAuthMode) {
        viewModel.onAction(PixelDoneAction.SetCloudAuthMode(mode))
    }

    fun openCloudSignInEditor() {
        if (!isSettingsSelected || !authSession.cloudAvailable || authSession.signedIn) return
        closeTransientControls()
        clearEditor()
        viewModel.onAction(PixelDoneAction.DismissAuthMessage)
        editorMode = EditorMode.CloudSignIn
    }

    fun cancelCloudSignIn() {
        viewModel.onAction(PixelDoneAction.CancelSignIn)
        if (editorMode is EditorMode.CloudSignIn) {
            editorMode = EditorMode.None
        }
    }

    fun signInToCloud() {
        viewModel.onAction(PixelDoneAction.SignIn)
    }

    fun signUpToCloud() {
        viewModel.onAction(PixelDoneAction.SignUp)
    }

    fun resetCloudPassword() {
        viewModel.onAction(PixelDoneAction.ResetPassword)
    }

    fun signOutFromCloud() {
        if (editorMode is EditorMode.CloudSignIn) {
            editorMode = EditorMode.None
        }
        viewModel.onAction(PixelDoneAction.SignOut)
    }

    fun syncCloudNow() {
        viewModel.onAction(PixelDoneAction.SyncNow)
    }

    fun openConflictDialog() {
        viewModel.onAction(PixelDoneAction.OpenConflictDialog)
    }

    fun dismissConflictDialog() {
        viewModel.onAction(PixelDoneAction.DismissConflictDialog)
    }

    fun resolveConflict(recordType: String, localId: String, choice: ConflictResolutionChoice) {
        viewModel.onAction(PixelDoneAction.ResolveConflict(recordType, localId, choice))
    }

    fun dismissUpdatePromptDialog() {
        showUpdatePromptDialog = false
        updatePromptInfo = null
    }

    fun revealUpdateProgressDialog() {
        showUpdateProgressDialog = true
        updateProgressDialogDismissed = false
    }

    fun dismissUpdateProgressDialog() {
        showUpdateProgressDialog = false
        updateProgressDialogDismissed = true
    }

    fun openDownloadedUpdate(download: AppUpdateDownload): Boolean {
        when (updateInstallPermissionAction(hasInstallUpdatePermission())) {
            UpdateInstallPermissionAction.RequestInstallPermission -> {
                pendingUpdateInstallDownload = download
                updateUiState = AppUpdateUiState(
                    status = UpdateUiStatus.Installing,
                    message = allowInstallText,
                )
                openInstallUpdateSettings()
                return true
            }
            UpdateInstallPermissionAction.OpenInstaller -> Unit
        }

        pendingUpdateInstallDownload = null
        return updateService.openInstallPrompt(download)
    }

    fun startUpdateDownload(info: AppUpdateInfo, revealProgressDialog: Boolean = false) {
        if (
            activeUpdateDownload?.version == info.version &&
            activeUpdateDownload?.fileName == info.fileName
        ) {
            if (revealProgressDialog) revealUpdateProgressDialog()
            return
        }
        if (revealProgressDialog) revealUpdateProgressDialog()
        updateScope.launch {
            for (request in appUpdateDownloadRequests(info)) {
                when (val result = updateService.enqueue(request)) {
                    is AppUpdateDownloadResult.Started -> {
                        activeUpdateDownload = result.download
                        updateUiState = AppUpdateUiState(
                            status = UpdateUiStatus.Downloading,
                            info = info,
                            message = formatLocalizedUpdateDownloadMessage(context, info.version),
                        )
                        val completion = updateService.awaitCompletion(result.download) { progress ->
                            if (activeUpdateDownload?.downloadId == result.download.downloadId) {
                                updateUiState = AppUpdateUiState(
                                    status = UpdateUiStatus.Downloading,
                                    info = info,
                                    message = formatLocalizedUpdateDownloadMessage(context, info.version, progress),
                                    progress = progress,
                                )
                            }
                        }
                        if (activeUpdateDownload?.downloadId != result.download.downloadId) {
                            return@launch
                        }
                        activeUpdateDownload = null
                        if (
                            completion == AppUpdateDownloadCompletion.Success &&
                            openDownloadedUpdate(result.download)
                        ) {
                            showUpdateProgressDialog = false
                            updateProgressDialogDismissed = false
                            updateUiState = AppUpdateUiState(
                                status = UpdateUiStatus.Installing,
                                message = String.format(Locale.getDefault(), installingVersionTemplate, info.version),
                            )
                            return@launch
                        }
                    }
                    AppUpdateDownloadResult.Failed -> Unit
                }
            }
            showUpdateProgressDialog = false
            updateProgressDialogDismissed = false
            updateUiState = AppUpdateUiState(
                status = UpdateUiStatus.Offline,
                message = updateFailedText,
            )
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
                if (
                    shouldShowAvailableUpdateDialog(
                        neverShowUpdateDialog = neverShowUpdateDialog,
                        hasActiveUpdateDownload = activeUpdateDownload != null,
                    )
                ) {
                    updatePromptInfo = result.info
                    showUpdatePromptDialog = true
                } else {
                    dismissUpdatePromptDialog()
                }
            }
            AppUpdateCheckResult.Current -> {
                dismissUpdatePromptDialog()
                if (showCurrentOrOfflineStatus) {
                    updateUiState = AppUpdateUiState(
                        status = UpdateUiStatus.Latest,
                        message = latestUpdateMessage(),
                    )
                }
            }
            AppUpdateCheckResult.Unavailable -> {
                dismissUpdatePromptDialog()
                if (showCurrentOrOfflineStatus) {
                    updateUiState = AppUpdateUiState(
                        status = UpdateUiStatus.Offline,
                        message = updateFailedText,
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
                    refreshActiveXHighAlarm()
                    cleanupInstalledUpdateIfNeeded()
                    checkForUpdateSilently()
                }
                if (event == Lifecycle.Event.ON_RESUME) {
                    permissionRefreshTick += 1
                    refreshActiveXHighAlarm()
                    cleanupInstalledUpdateIfNeeded()
                    val currentTodos = normalTodos(currentChecklistState)
                    reminderScheduler.sync(currentTodos, currentTodos)
                    val pendingInstall = pendingUpdateInstallDownload
                    if (pendingInstall != null && hasInstallUpdatePermission()) {
                        pendingUpdateInstallDownload = null
                        updateUiState = if (updateService.openInstallPrompt(pendingInstall)) {
                            AppUpdateUiState(
                                status = UpdateUiStatus.Installing,
                                message = String.format(Locale.getDefault(), installingVersionTemplate, pendingInstall.version),
                            )
                        } else {
                            AppUpdateUiState(
                                status = UpdateUiStatus.Offline,
                                message = updateFailedText,
                            )
                        }
                    }
                    val fullScreenFollowUpTodoId = pendingFullScreenPermissionTodoId
                    pendingFullScreenPermissionTodoId = null
                    fullScreenFollowUpTodoId?.let { id ->
                        val item = normalTodos(currentChecklistState).firstOrNull { it.id == id }
                        if (
                            item != null &&
                            reminderScheduler.canScheduleExactAlarms() &&
                            !hasFullScreenIntentAccess()
                        ) {
                            openFullScreenIntentSettings()
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
            dismissUpdatePromptDialog()
            startUpdateDownload(availableInfo, revealProgressDialog = true)
            return
        }
        if (updateCheckInFlight || activeUpdateDownload != null) return

        updateCheckInFlight = true
        updateUiState = AppUpdateUiState(
            status = UpdateUiStatus.Checking,
            message = checkingUpdateText,
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

    PixelDoneTheme(darkTheme = darkTheme) {
        PixelDoneScreen(
            checklists = checklistState.lists,
            selectedChecklistId = selectedChecklist.id,
            selectedChecklistName = selectedChecklist.localizedDisplayName(),
            isTrashSelected = isTrashSelected,
            isSettingsSelected = isSettingsSelected,
            headerExpanded = headerExpanded,
            onHeaderExpandedChange = { headerExpanded = it },
            onSelectChecklist = { id -> selectChecklist(id) },
            onEditChecklist = { id ->
                checklistState.lists
                    .firstOrNull { it.id == id && isNormalChecklist(it) }
                    ?.let(::startEditingChecklist)
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
            canDeleteChecklist = isNormalChecklistSelected && normalChecklistCount(checklistState) > 1,
            onSubmitChecklist = ::submitChecklist,
            cloudSignInEditorVisible = cloudSignInEditorVisible,
            onOpenCloudSignIn = ::openCloudSignInEditor,
            onCancelCloudSignIn = ::cancelCloudSignIn,
            onDeleteChecklist = {
                editingChecklistId?.let { id ->
                    checklistState.lists
                        .firstOrNull { it.id == id && isNormalChecklist(it) }
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
                if (isNormalChecklistSelected) {
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
                if (isNormalChecklistSelected) {
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
            targetMoveChecklists = checklistState.lists.filter {
                isNormalChecklist(it) && it.id != selectedChecklist.id
            },
            dockConfig = dockConfig,
            onDockConfigChange = ::setDockConfigPreference,
            batchMoveMode = batchMoveMode,
            batchMoveSelectedTodoIds = batchMoveSelectedTodoIds,
            batchDeleteActive = batchDeleteActive,
            onToggleBatchMoveSelection = ::toggleBatchMoveSelection,
            onOpenBatchMoveTargetPicker = ::openBatchMoveTargetPicker,
            onCloseBatchMoveTargetPicker = ::closeBatchMoveTargetPicker,
            onCancelBatchMove = ::clearBatchMoveState,
            onMoveSelectedTodosToChecklist = ::moveSelectedTodosToChecklist,
            onToggleBatchDelete = ::toggleBatchDeleteMode,
            onQuickDeleteTodo = ::quickDeleteTodo,
            displayOrderIds = displayOrderIds,
            keepDisplayOrder = keepDisplayOrderDuringSortDelay,
            todoListScrollRequest = todoListScrollRequest,
            todoListHighlightRequest = todoListHighlightRequest,
            onTodoListHighlightConsumed = ::consumeTodoListHighlight,
            updateUiState = updateUiState,
            onUpdateClick = ::handleUpdateClick,
            darkTheme = darkTheme,
            onDarkThemeChange = ::setDarkThemePreference,
            language = languageMode,
            onLanguageChange = ::setLanguagePreference,
            showUpdateDialogs = shouldShowUpdatePromptSetting(neverShowUpdateDialog),
            onShowUpdateDialogsChange = ::setShowUpdateDialogs,
            currentVersion = BuildConfig.VERSION_NAME,
            syncStatusText = syncStatusText,
            syncStatus = uiState.syncStatus,
            syncRunState = uiState.syncRunState,
            authSession = authSession,
            authInput = authInput,
            onAuthEmailChange = ::setAuthEmail,
            onAuthPasswordChange = ::setAuthPassword,
            onAuthModeChange = ::setCloudAuthMode,
            onSignIn = ::signInToCloud,
            onSignUp = ::signUpToCloud,
            onResetPassword = ::resetCloudPassword,
            onSignOut = ::signOutFromCloud,
            onSyncNow = ::syncCloudNow,
            onOpenConflictDialog = ::openConflictDialog,
            permissionSettingsState = permissionRefreshTick.let { currentPermissionSettingsState() },
            onRequestNotificationPermission = ::requestNotificationPermissionFromSettings,
            onRequestExactAlarmPermission = {
                openPackageSettingsAction(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            },
            onRequestFullScreenIntentPermission = ::openFullScreenIntentSettings,
            onRequestInstallUpdatesPermission = ::openInstallUpdateSettings,
            activeXHighAlarm = activeXHighAlarm,
            onSnoozeActiveXHighAlarm = {
                startActiveXHighAlarmAction(XHighAlarmService.ACTION_SNOOZE_ALARM)
            },
            onStopActiveXHighAlarm = {
                startActiveXHighAlarmAction(XHighAlarmService.ACTION_STOP_ALARM)
            },
        )
        UpdateAvailableDialog(
            info = if (showUpdatePromptDialog) updatePromptInfo else null,
            currentVersion = BuildConfig.VERSION_NAME,
            neverShowUpdateDialog = neverShowUpdateDialog,
            onNeverShowUpdateDialogChange = ::setNeverShowUpdateDialog,
            onUpdate = { info ->
                dismissUpdatePromptDialog()
                startUpdateDownload(info, revealProgressDialog = true)
            },
            onDismiss = ::dismissUpdatePromptDialog,
        )
        UpdateDownloadProgressDialog(
            download = if (showUpdateProgressDialog && !updateProgressDialogDismissed) {
                activeUpdateDownload
            } else {
                null
            },
            updateUiState = updateUiState,
            onDismiss = ::dismissUpdateProgressDialog,
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
        SyncConflictDialog(
            visible = uiState.conflictDialogVisible,
            conflicts = uiState.syncConflicts,
            resolvingConflictKey = uiState.resolvingConflictKey,
            onResolve = ::resolveConflict,
            onDismiss = ::dismissConflictDialog,
        )
    }
}

@Composable
private fun PixelDoneScreen(
    checklists: List<TodoChecklist>,
    selectedChecklistId: String,
    selectedChecklistName: String,
    isTrashSelected: Boolean,
    isSettingsSelected: Boolean,
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
    cloudSignInEditorVisible: Boolean,
    onOpenCloudSignIn: () -> Unit,
    onCancelCloudSignIn: () -> Unit,
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
    targetMoveChecklists: List<TodoChecklist>,
    dockConfig: DockConfig,
    onDockConfigChange: (DockConfig) -> Unit,
    batchMoveMode: BatchMoveMode,
    batchMoveSelectedTodoIds: Set<String>,
    batchDeleteActive: Boolean,
    onToggleBatchMoveSelection: (String) -> Unit,
    onOpenBatchMoveTargetPicker: () -> Unit,
    onCloseBatchMoveTargetPicker: () -> Unit,
    onCancelBatchMove: () -> Unit,
    onMoveSelectedTodosToChecklist: (String, List<String>) -> Unit,
    onToggleBatchDelete: () -> Unit,
    onQuickDeleteTodo: (String) -> Unit,
    displayOrderIds: List<String>,
    keepDisplayOrder: Boolean,
    todoListScrollRequest: TodoListScrollRequest,
    todoListHighlightRequest: TodoListHighlightRequest,
    onTodoListHighlightConsumed: (Int) -> Unit,
    updateUiState: AppUpdateUiState,
    onUpdateClick: () -> Unit,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    showUpdateDialogs: Boolean,
    onShowUpdateDialogsChange: (Boolean) -> Unit,
    currentVersion: String,
    syncStatusText: String,
    syncStatus: SyncCoordinatorStatus,
    syncRunState: SyncRunState = SyncRunState(),
    authSession: AuthSession,
    authInput: AuthInputState,
    onAuthEmailChange: (String) -> Unit,
    onAuthPasswordChange: (String) -> Unit,
    onAuthModeChange: (CloudAuthMode) -> Unit,
    onSignIn: () -> Unit,
    onSignUp: () -> Unit,
    onResetPassword: () -> Unit = {},
    onSignOut: () -> Unit,
    onSyncNow: () -> Unit,
    onOpenConflictDialog: () -> Unit = {},
    permissionSettingsState: PermissionSettingsState,
    onRequestNotificationPermission: () -> Unit,
    onRequestExactAlarmPermission: () -> Unit,
    onRequestFullScreenIntentPermission: () -> Unit,
    onRequestInstallUpdatesPermission: () -> Unit,
    activeXHighAlarm: ActiveXHighAlarm? = null,
    onSnoozeActiveXHighAlarm: () -> Unit = {},
    onStopActiveXHighAlarm: () -> Unit = {},
) {
    val colors = PixelDoneColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
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
                isSettingsSelected = isSettingsSelected,
                activeCount = activeCount,
                completedCount = completedCount,
                expanded = headerExpanded,
                onExpandedChange = onHeaderExpandedChange,
                onSelectChecklist = onSelectChecklist,
                onEditChecklist = onEditChecklist,
            )
            activeXHighAlarm?.let { alarm ->
                XHighAlarmControlPanel(
                    alarm = alarm,
                    onSnooze = onSnoozeActiveXHighAlarm,
                    onStop = onStopActiveXHighAlarm,
                )
            }
            TaskWorkspacePanel(
                todos = todos,
                isTrashSelected = isTrashSelected,
                isSettingsSelected = isSettingsSelected,
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
                targetMoveChecklists = targetMoveChecklists,
                dockConfig = dockConfig,
                onDockConfigChange = onDockConfigChange,
                batchMoveMode = batchMoveMode,
                batchMoveSelectedTodoIds = batchMoveSelectedTodoIds,
                batchDeleteActive = batchDeleteActive,
                onToggleBatchMoveSelection = onToggleBatchMoveSelection,
                onOpenBatchMoveTargetPicker = onOpenBatchMoveTargetPicker,
                onCloseBatchMoveTargetPicker = onCloseBatchMoveTargetPicker,
                onCancelBatchMove = onCancelBatchMove,
                onMoveSelectedTodosToChecklist = onMoveSelectedTodosToChecklist,
                onToggleBatchDelete = onToggleBatchDelete,
                onQuickDeleteTodo = onQuickDeleteTodo,
                displayOrderIds = displayOrderIds,
                keepDisplayOrder = keepDisplayOrder,
                todoListScrollRequest = todoListScrollRequest,
                todoListHighlightRequest = todoListHighlightRequest,
                onTodoListHighlightConsumed = onTodoListHighlightConsumed,
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
                cloudSignInEditorVisible = cloudSignInEditorVisible,
                onOpenCloudSignIn = onOpenCloudSignIn,
                onCancelCloudSignIn = onCancelCloudSignIn,
                updateUiState = updateUiState,
                onUpdateClick = onUpdateClick,
                darkTheme = darkTheme,
                onDarkThemeChange = onDarkThemeChange,
                language = language,
                onLanguageChange = onLanguageChange,
                showUpdateDialogs = showUpdateDialogs,
                onShowUpdateDialogsChange = onShowUpdateDialogsChange,
                currentVersion = currentVersion,
                syncStatusText = syncStatusText,
                syncStatus = syncStatus,
                syncRunState = syncRunState,
                authSession = authSession,
                authInput = authInput,
                onAuthEmailChange = onAuthEmailChange,
                onAuthPasswordChange = onAuthPasswordChange,
                onAuthModeChange = onAuthModeChange,
                onSignIn = onSignIn,
                onSignUp = onSignUp,
                onResetPassword = onResetPassword,
                onSignOut = onSignOut,
                onSyncNow = onSyncNow,
                onOpenConflictDialog = onOpenConflictDialog,
                permissionSettingsState = permissionSettingsState,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onRequestExactAlarmPermission = onRequestExactAlarmPermission,
                onRequestFullScreenIntentPermission = onRequestFullScreenIntentPermission,
                onRequestInstallUpdatesPermission = onRequestInstallUpdatesPermission,
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
    isSettingsSelected: Boolean,
    activeCount: Int,
    completedCount: Int,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelectChecklist: (String) -> Unit,
    onEditChecklist: (String) -> Unit,
) {
    val scrollState = rememberScrollState()
    val colors = PixelDoneColors.current
    val statusText = when {
        isSettingsSelected -> stringResource(R.string.options)
        isTrashSelected -> stringResource(R.string.items_count, activeCount + completedCount)
        else -> stringResource(R.string.active_done_count, activeCount, completedCount)
    }

    PixelPanel(
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
                color = colors.textPrimary,
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
                color = colors.textSecondary,
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
                        canEdit = isNormalChecklist(checklist),
                        onSelect = { onSelectChecklist(checklist.id) },
                        onEdit = { onEditChecklist(checklist.id) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.long_press_add_list),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun XHighAlarmControlPanel(
    alarm: ActiveXHighAlarm,
    onSnooze: () -> Unit,
    onStop: () -> Unit,
) {
    val colors = PixelDoneColors.current
    PixelPanel(
        borderColor = colors.error,
        color = colors.destructiveSurface,
        contentPadding = PaddingValues(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (alarm.displayCount == 1) {
                        stringResource(R.string.xhigh_alarm_ringing)
                    } else {
                        pluralStringResource(R.plurals.xhigh_alarms_count, alarm.displayCount, alarm.displayCount)
                    },
                    color = colors.error,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = alarm.firedDueAtMillis.formatDateTime(),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = alarm.primaryTitle(),
                color = colors.textPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PixelButton(
                    text = stringResource(R.string.snooze_10),
                    onClick = onSnooze,
                    modifier = Modifier.weight(1f),
                    selected = true,
                )
                PixelButton(
                    text = stringResource(R.string.stop),
                    onClick = onStop,
                    modifier = Modifier.weight(1f),
                    destructive = true,
                )
            }
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
    val colors = PixelDoneColors.current
    val borderColor = if (selected) colors.primaryInteractive else colors.borderWeak
    val backgroundColor = if (selected) colors.selectedSurface else colors.surfaceSoft
    val subtitle = when {
        isSettingsChecklist(checklist) -> stringResource(R.string.app_options)
        isTrashChecklist(checklist) -> stringResource(R.string.items_count, checklist.items.size)
        else -> stringResource(
            R.string.active_done_count,
            activeTodoCount(checklist),
            completedTodoCount(checklist),
        )
    }

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
                text = checklist.localizedDisplayName(),
                color = colors.textPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
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
private fun TodoChecklist.localizedDisplayName(): String = when {
    isSettingsChecklist(this) -> stringResource(R.string.options)
    isTrashChecklist(this) -> stringResource(R.string.field_trash)
    else -> name
}

@Composable
private fun TaskWorkspacePanel(
    todos: List<TodoItem>,
    isTrashSelected: Boolean,
    isSettingsSelected: Boolean,
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
    targetMoveChecklists: List<TodoChecklist>,
    dockConfig: DockConfig,
    onDockConfigChange: (DockConfig) -> Unit,
    batchMoveMode: BatchMoveMode,
    batchMoveSelectedTodoIds: Set<String>,
    batchDeleteActive: Boolean,
    onToggleBatchMoveSelection: (String) -> Unit,
    onOpenBatchMoveTargetPicker: () -> Unit,
    onCloseBatchMoveTargetPicker: () -> Unit,
    onCancelBatchMove: () -> Unit,
    onMoveSelectedTodosToChecklist: (String, List<String>) -> Unit,
    onToggleBatchDelete: () -> Unit,
    onQuickDeleteTodo: (String) -> Unit,
    displayOrderIds: List<String>,
    keepDisplayOrder: Boolean,
    todoListScrollRequest: TodoListScrollRequest,
    todoListHighlightRequest: TodoListHighlightRequest,
    onTodoListHighlightConsumed: (Int) -> Unit,
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
    cloudSignInEditorVisible: Boolean,
    onOpenCloudSignIn: () -> Unit,
    onCancelCloudSignIn: () -> Unit,
    updateUiState: AppUpdateUiState,
    onUpdateClick: () -> Unit,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    showUpdateDialogs: Boolean,
    onShowUpdateDialogsChange: (Boolean) -> Unit,
    currentVersion: String,
    syncStatusText: String,
    syncStatus: SyncCoordinatorStatus,
    syncRunState: SyncRunState = SyncRunState(),
    authSession: AuthSession,
    authInput: AuthInputState,
    onAuthEmailChange: (String) -> Unit,
    onAuthPasswordChange: (String) -> Unit,
    onAuthModeChange: (CloudAuthMode) -> Unit,
    onSignIn: () -> Unit,
    onSignUp: () -> Unit,
    onResetPassword: () -> Unit = {},
    onSignOut: () -> Unit,
    onSyncNow: () -> Unit,
    onOpenConflictDialog: () -> Unit = {},
    permissionSettingsState: PermissionSettingsState,
    onRequestNotificationPermission: () -> Unit,
    onRequestExactAlarmPermission: () -> Unit,
    onRequestFullScreenIntentPermission: () -> Unit,
    onRequestInstallUpdatesPermission: () -> Unit,
    compactForKeyboard: Boolean,
    modifier: Modifier = Modifier,
) {
    val isNormalChecklistSelected = !isTrashSelected && !isSettingsSelected

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PixelReadFrameInset),
    ) {
        if (isSettingsSelected) {
            SettingsPanel(
                darkTheme = darkTheme,
                onDarkThemeChange = onDarkThemeChange,
                language = language,
                onLanguageChange = onLanguageChange,
                showUpdateDialogs = showUpdateDialogs,
                onShowUpdateDialogsChange = onShowUpdateDialogsChange,
                currentVersion = currentVersion,
                syncStatusText = syncStatusText,
                syncStatus = syncStatus,
                syncRunState = syncRunState,
                authSession = authSession,
                authInput = authInput,
                onOpenCloudSignIn = onOpenCloudSignIn,
                onSignOut = onSignOut,
                onSyncNow = onSyncNow,
                onOpenConflictDialog = onOpenConflictDialog,
                updateUiState = updateUiState,
                onUpdateClick = onUpdateClick,
                dockConfig = dockConfig,
                onDockConfigChange = onDockConfigChange,
                permissionState = permissionSettingsState,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onRequestExactAlarmPermission = onRequestExactAlarmPermission,
                onRequestFullScreenIntentPermission = onRequestFullScreenIntentPermission,
                onRequestInstallUpdatesPermission = onRequestInstallUpdatesPermission,
                modifier = Modifier.weight(1f),
            )
        } else {
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
                targetMoveChecklists = targetMoveChecklists,
                dockConfig = dockConfig,
                batchMoveMode = batchMoveMode,
                batchMoveSelectedTodoIds = batchMoveSelectedTodoIds,
                batchDeleteActive = batchDeleteActive,
                onToggleBatchMoveSelection = onToggleBatchMoveSelection,
                onOpenBatchMoveTargetPicker = onOpenBatchMoveTargetPicker,
                onCloseBatchMoveTargetPicker = onCloseBatchMoveTargetPicker,
                onCancelBatchMove = onCancelBatchMove,
                onMoveSelectedTodosToChecklist = onMoveSelectedTodosToChecklist,
                onToggleBatchDelete = onToggleBatchDelete,
                onQuickDeleteTodo = onQuickDeleteTodo,
                displayOrderIds = displayOrderIds,
                keepDisplayOrder = keepDisplayOrder,
                todoListScrollRequest = todoListScrollRequest,
                todoListHighlightRequest = todoListHighlightRequest,
                onTodoListHighlightConsumed = onTodoListHighlightConsumed,
                showNewTaskButton = isNormalChecklistSelected && !taskEditorVisible && !checklistEditorVisible,
                onOpenTaskEditor = onOpenTaskEditor,
                onOpenChecklistEditor = onOpenChecklistEditor,
                editingTaskId = editingTaskId,
                onCancelEdit = onCancelEdit,
                modifier = Modifier.weight(1f),
            )
        }
        if (isNormalChecklistSelected && taskEditorVisible) {
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
        } else if (isNormalChecklistSelected && checklistEditorVisible) {
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
        } else if (isSettingsSelected && cloudSignInEditorVisible) {
            CloudAuthEditorPanel(
                authInput = authInput,
                onAuthEmailChange = onAuthEmailChange,
                onAuthPasswordChange = onAuthPasswordChange,
                onAuthModeChange = onAuthModeChange,
                onSignIn = onSignIn,
                onSignUp = onSignUp,
                onResetPassword = onResetPassword,
                onCancelAuth = onCancelCloudSignIn,
                compactForKeyboard = compactForKeyboard,
            )
        }
    }
}

@Composable
private fun SettingsPanel(
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    showUpdateDialogs: Boolean,
    onShowUpdateDialogsChange: (Boolean) -> Unit,
    currentVersion: String,
    syncStatusText: String,
    syncStatus: SyncCoordinatorStatus,
    syncRunState: SyncRunState = SyncRunState(),
    authSession: AuthSession,
    authInput: AuthInputState,
    onOpenCloudSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onSyncNow: () -> Unit,
    onOpenConflictDialog: () -> Unit = {},
    updateUiState: AppUpdateUiState,
    onUpdateClick: () -> Unit,
    dockConfig: DockConfig,
    onDockConfigChange: (DockConfig) -> Unit,
    permissionState: PermissionSettingsState,
    onRequestNotificationPermission: () -> Unit,
    onRequestExactAlarmPermission: () -> Unit,
    onRequestFullScreenIntentPermission: () -> Unit,
    onRequestInstallUpdatesPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PixelDoneColors.current
    val scrollState = rememberScrollState()
    val updateActionText = when (updateUiState.status) {
        UpdateUiStatus.Available -> stringResource(R.string.update)
        UpdateUiStatus.Checking -> stringResource(R.string.checking_update)
        UpdateUiStatus.Downloading -> stringResource(R.string.downloading_update)
        UpdateUiStatus.Installing -> stringResource(R.string.install_updates)
        else -> stringResource(R.string.check_update)
    }
    val updateActionEnabled = updateUiState.status != UpdateUiStatus.Checking &&
        updateUiState.status != UpdateUiStatus.Downloading &&
        updateUiState.status != UpdateUiStatus.Installing
    val lightThemeLabel = stringResource(R.string.theme_light)
    val darkThemeLabel = stringResource(R.string.theme_dark)
    val onLabel = stringResource(R.string.on)
    val offLabel = stringResource(R.string.off)

    PixelPanel(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            SettingsSection(title = stringResource(R.string.settings_cloud)) {
                SettingsCloudPanel(
                    authSession = authSession,
                    authInput = authInput,
                    syncStatusText = syncStatusText,
                    syncStatus = syncStatus,
                    syncRunState = syncRunState,
                    onOpenCloudSignIn = onOpenCloudSignIn,
                    onSignOut = onSignOut,
                    onSyncNow = onSyncNow,
                    onOpenConflictDialog = onOpenConflictDialog,
                )
            }
            SettingsSection(title = stringResource(R.string.settings_display)) {
                SettingsLanguageSelector(
                    value = language,
                    onSelected = onLanguageChange,
                )
                SettingsSegmentedRow(
                    title = stringResource(R.string.settings_theme),
                    value = darkTheme,
                    options = listOf(false, true),
                    label = { if (it) darkThemeLabel else lightThemeLabel },
                    onSelected = onDarkThemeChange,
                )
            }

            SettingsSection(title = stringResource(R.string.settings_dock)) {
                DockSettingsPreview(dockConfig = dockConfig)
                SettingsSegmentedRow(
                    title = stringResource(R.string.plus),
                    value = dockConfig.plusPlacement,
                    options = listOf(
                        DockPlusPlacement.CENTER,
                        DockPlusPlacement.LEFT_EDGE,
                        DockPlusPlacement.RIGHT_EDGE,
                    ),
                    label = { it.settingsLabel() },
                    onSelected = { placement ->
                        onDockConfigChange(dockConfig.copy(plusPlacement = placement))
                    },
                )
                DockActionSettingsList(
                    dockConfig = dockConfig,
                    onDockConfigChange = onDockConfigChange,
                )
            }


            SettingsSection(title = stringResource(R.string.settings_updates)) {
                SettingsSegmentedRow(
                    title = stringResource(R.string.update_popup),
                    value = showUpdateDialogs,
                    options = listOf(true, false),
                    label = { if (it) onLabel else offLabel },
                    onSelected = onShowUpdateDialogsChange,
                )
                SettingsActionRow(
                    title = stringResource(R.string.check_update),
                    value = updateUiState.message ?: stringResource(R.string.current_version, currentVersion),
                    actionText = updateActionText,
                    onAction = onUpdateClick,
                    enabled = updateActionEnabled,
                )
            }

            SettingsSection(title = stringResource(R.string.settings_permissions)) {
                SettingsPermissionRow(
                    title = stringResource(R.string.notifications),
                    granted = permissionState.notificationsGranted,
                    onAction = onRequestNotificationPermission,
                )
                SettingsPermissionRow(
                    title = stringResource(R.string.exact_alarm),
                    granted = permissionState.exactAlarmGranted,
                    onAction = onRequestExactAlarmPermission,
                )
                SettingsPermissionRow(
                    title = stringResource(R.string.full_screen),
                    granted = permissionState.fullScreenIntentGranted,
                    onAction = onRequestFullScreenIntentPermission,
                )
                SettingsPermissionRow(
                    title = stringResource(R.string.install_updates),
                    granted = permissionState.installUpdatesGranted,
                    onAction = onRequestInstallUpdatesPermission,
                )
            }

            SettingsSection(title = stringResource(R.string.settings_about)) {
                SettingsAboutTextRow(
                    title = stringResource(R.string.app),
                    value = "PixelDone",
                )
                SettingsAboutTextRow(
                    title = stringResource(R.string.maker),
                    value = "CODEX & XUE",
                    valueColor = colors.primary,
                )
                SettingsAboutTextRow(
                    title = stringResource(R.string.sync),
                    value = syncStatusText,
                    valueColor = colors.primaryInteractive,
                )
                SettingsAboutTextRow(
                    title = stringResource(R.string.update_permissions),
                    value = stringResource(R.string.same_package_signature),
                )
            }
        }
    }
}

@Composable
private fun SettingsCloudPanel(
    authSession: AuthSession,
    authInput: AuthInputState,
    syncStatusText: String,
    syncStatus: SyncCoordinatorStatus,
    syncRunState: SyncRunState = SyncRunState(),
    onOpenCloudSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onSyncNow: () -> Unit,
    onOpenConflictDialog: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = PixelDoneColors.current
    val accountLabel = when {
        !authSession.cloudAvailable -> stringResource(R.string.needs_setup)
        authSession.signedIn -> authSession.userEmail ?: authSession.userId ?: stringResource(R.string.signed_in)
        else -> stringResource(R.string.signed_out)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, colors.borderWeak, RectangleShape)
            .background(colors.surfaceSoft)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsRowText(
                title = stringResource(R.string.account),
                value = accountLabel,
                modifier = Modifier.weight(1f),
            )
            when {
                authSession.signedIn -> CloudIconAction(
                    icon = CloudActionIcon.LOGOUT,
                    contentDescription = stringResource(R.string.sign_out),
                    onClick = onSignOut,
                    enabled = !authInput.busy,
                )
                authSession.cloudAvailable -> CloudIconAction(
                    icon = CloudActionIcon.LOGIN,
                    contentDescription = stringResource(R.string.sign_in),
                    onClick = onOpenCloudSignIn,
                    enabled = !authInput.busy,
                )
            }
        }

        if (!authSession.cloudAvailable) {
            Text(
                text = authSession.configurationError ?: stringResource(R.string.cloud_local_only_detail),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsRowText(
                    title = stringResource(R.string.sync),
                    value = syncStatusText,
                    modifier = Modifier.weight(1f),
                    valueColor = syncStatus.settingsValueColor(colors),
                )
                if (authSession.signedIn) {
                    CloudIconAction(
                        icon = CloudActionIcon.SYNC,
                        contentDescription = stringResource(R.string.sync_now),
                        onClick = onSyncNow,
                        enabled = syncStatus != SyncCoordinatorStatus.SYNCING && !authInput.busy,
                    )
                }
            }
            if (authSession.signedIn && syncRunState.pendingCount > 0) {
                SettingsRowText(
                    title = stringResource(R.string.pending),
                    value = syncRunState.pendingCount.toString(),
                    valueColor = colors.textSecondary,
                )
            }
            if (authSession.signedIn && syncRunState.conflictCount > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SettingsRowText(
                        title = stringResource(R.string.conflicts),
                        value = syncRunState.conflictCount.toString(),
                        valueColor = colors.primary,
                        modifier = Modifier.weight(1f),
                    )
                    ClayReviewButton(
                        text = stringResource(R.string.review),
                        onClick = onOpenConflictDialog,
                        modifier = Modifier.width(88.dp),
                    )
                }
            }
        }

        if (authSession.signedIn) authInput.error?.let { error ->
            Text(
                text = localizedAuthText(error),
                style = MaterialTheme.typography.labelSmall,
                color = colors.error,
            )
        }
    }
}

private enum class CloudActionIcon { LOGIN, LOGOUT, SYNC }

@Composable
private fun CloudIconAction(
    icon: CloudActionIcon,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = PixelDoneColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val iconColor = if (enabled) colors.textSecondary else colors.disabledText
    Box(
        modifier = modifier
            .size(44.dp)
            .background(if (pressed && enabled) colors.selectedSurface else Color.Transparent)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) {
            val stroke = 2.dp.toPx()
            val pixelStroke = Stroke(
                width = stroke,
                cap = StrokeCap.Square,
                join = StrokeJoin.Miter,
            )
            when (icon) {
                CloudActionIcon.LOGIN, CloudActionIcon.LOGOUT -> {
                    cloudLoginLogoutPolylines(logout = icon == CloudActionIcon.LOGOUT).forEach { polyline ->
                        val path = Path().apply {
                            val first = polyline.points.first()
                            moveTo(first.x.dp.toPx(), first.y.dp.toPx())
                            polyline.points.drop(1).forEach { point ->
                                lineTo(point.x.dp.toPx(), point.y.dp.toPx())
                            }
                        }
                        drawPath(path, iconColor, style = pixelStroke)
                    }
                }
                CloudActionIcon.SYNC -> {
                    val upperArrow = Path().apply {
                        moveTo(18.dp.toPx(), 12.dp.toPx())
                        cubicTo(
                            18.dp.toPx(), 7.dp.toPx(),
                            15.dp.toPx(), 4.dp.toPx(),
                            11.dp.toPx(), 4.dp.toPx(),
                        )
                        moveTo(11.dp.toPx(), 1.dp.toPx())
                        lineTo(7.dp.toPx(), 5.dp.toPx())
                        lineTo(11.dp.toPx(), 9.dp.toPx())
                    }
                    val lowerArrow = Path().apply {
                        moveTo(4.dp.toPx(), 10.dp.toPx())
                        cubicTo(
                            4.dp.toPx(), 15.dp.toPx(),
                            7.dp.toPx(), 18.dp.toPx(),
                            11.dp.toPx(), 18.dp.toPx(),
                        )
                        moveTo(11.dp.toPx(), 13.dp.toPx())
                        lineTo(15.dp.toPx(), 17.dp.toPx())
                        lineTo(11.dp.toPx(), 21.dp.toPx())
                    }
                    drawPath(upperArrow, iconColor, style = pixelStroke)
                    drawPath(lowerArrow, iconColor, style = pixelStroke)
                }
            }
        }
    }
}

@Composable
private fun ClayReviewButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = PixelDoneColors.current
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .background(colors.primary, RectangleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = text },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = colors.onPrimary)
    }
}

@Composable
private fun SettingsTextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = PixelDoneColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val backgroundColor = if (pressed && enabled) colors.selectedSurface else Color.Transparent
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .background(backgroundColor, RectangleShape)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .semantics { contentDescription = text }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) colors.primaryInteractive else colors.disabledText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SettingsLanguageSelector(
    value: AppLanguage,
    onSelected: (AppLanguage) -> Unit,
) {
    val colors = PixelDoneColors.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SettingsRowText(title = stringResource(R.string.settings_language), value = value.displayName())
        AppLanguage.entries.chunked(2).forEach { languages ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                languages.forEach { language ->
                    LanguageChoice(
                        language = language,
                        selected = language == value,
                        onSelected = onSelected,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (languages.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun LanguageChoice(
    language: AppLanguage,
    selected: Boolean,
    onSelected: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PixelDoneColors.current
    val languageLabel = language.displayName()
    val selectionLabel = if (selected) stringResource(R.string.selected) else stringResource(R.string.not_selected)
    Row(
        modifier = modifier
            .heightIn(min = 44.dp)
            .background(if (selected) colors.selectedSurface else Color.Transparent)
            .clickable { onSelected(language) }
            .semantics {
                contentDescription = languageLabel
                stateDescription = selectionLabel
            }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(Modifier.size(14.dp)) {
            drawRect(if (selected) colors.primary else colors.borderWeak)
            if (selected) {
                drawRect(colors.surface, topLeft = Offset(4.dp.toPx(), 4.dp.toPx()), size = Size(6.dp.toPx(), 6.dp.toPx()))
            }
        }
        Text(
            text = languageLabel,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.textPrimary else colors.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AppLanguage.displayName(): String = when (this) {
    AppLanguage.SYSTEM -> stringResource(R.string.language_system)
    else -> requireNotNull(nativeDisplayName)
}
@Composable
private fun settingsTextFieldColors(colors: PixelDonePalette) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = colors.primary,
    unfocusedBorderColor = colors.borderWeak,
    focusedContainerColor = colors.surfaceSoft,
    unfocusedContainerColor = colors.surfaceSoft,
    errorBorderColor = colors.error,
    errorContainerColor = colors.surfaceSoft,
    disabledContainerColor = colors.surfaceSoft,
    cursorColor = colors.primaryInteractive,
    focusedLabelColor = colors.primaryInteractive,
    unfocusedLabelColor = colors.textSecondary,
    errorLabelColor = colors.error,
    focusedTextColor = colors.textPrimary,
    unfocusedTextColor = colors.textPrimary,
    errorTextColor = colors.textPrimary,
    disabledTextColor = colors.textSecondary,
)
@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingsSectionTitle(title)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            content()
        }
    }
}

@Composable
private fun DockSettingsPreview(
    dockConfig: DockConfig,
    modifier: Modifier = Modifier,
) {
    val colors = PixelDoneColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, colors.borderWeak, RectangleShape)
            .background(colors.surfaceSoft)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SettingsRowText(
            title = stringResource(R.string.preview),
            value = dockConfig.previewLabel(),
        )
        BottomActionDock(
            config = dockConfig,
            actionState = previewDockActionState(),
            onActionClick = {},
            onAddClick = {},
            onAddLongClick = {},
        )
    }
}

@Composable
private fun DockActionSettingsList(
    dockConfig: DockConfig,
    onDockConfigChange: (DockConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PixelDoneColors.current
    val normalizedConfig = dockConfig.normalized()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, colors.borderWeak, RectangleShape)
            .background(colors.surfaceSoft)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SettingsRowText(
            title = stringResource(R.string.functions),
            value = stringResource(
                R.string.selected_count,
                normalizedConfig.actions.size,
                MaxDockActions,
            ),
        )
        AllDockActions.forEach { action ->
            DockActionSettingsRow(
                action = action,
                selectedIndex = normalizedConfig.actions.indexOf(action),
                selectedCount = normalizedConfig.actions.size,
                onToggle = {
                    onDockConfigChange(
                        normalizedConfig.copy(
                            actions = toggleDockActionSelection(normalizedConfig.actions, action),
                        ),
                    )
                },
                onMove = { offset ->
                    onDockConfigChange(
                        normalizedConfig.copy(
                            actions = movedDockActions(normalizedConfig.actions, action, offset),
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun DockActionSettingsRow(
    action: DockAction,
    selectedIndex: Int,
    selectedCount: Int,
    onToggle: () -> Unit,
    onMove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PixelDoneColors.current
    val selected = selectedIndex >= 0
    val borderColor = if (selected) colors.primaryInteractive else colors.borderWeak
    val backgroundColor = if (selected) colors.selectedSurface else colors.surfaceRaised
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(if (selected) 2.dp else 1.dp, borderColor, RectangleShape)
            .background(backgroundColor)
            .clickable(onClick = onToggle)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (selected) String.format(Locale.US, "%02d", selectedIndex + 1) else "--",
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) colors.primaryInteractive else colors.textSecondary,
            modifier = Modifier.width(28.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .size(34.dp)
                .border(1.dp, colors.borderWeak, RectangleShape)
                .background(colors.surfaceSoft),
            contentAlignment = Alignment.Center,
        ) {
            DockActionIcon(
                action = action,
                color = if (selected) colors.textPrimary else colors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
        SettingsRowText(
            title = action.settingsTitle(),
            value = action.settingsValue(),
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            SettingsOrderButton(
                direction = -1,
                enabled = selectedIndex > 0,
                onClick = { onMove(-1) },
            )
            SettingsOrderButton(
                direction = 1,
                enabled = selectedIndex < selectedCount - 1,
                onClick = { onMove(1) },
            )
        }
    }
}

@Composable
private fun SettingsOrderButton(
    direction: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PixelDoneColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val iconColor = if (enabled) colors.textSecondary else colors.disabledText
    Box(
        modifier = modifier
            .size(32.dp)
            .border(1.dp, colors.borderWeak, RectangleShape)
            .background(if (pressed && enabled) colors.selectedSurface else colors.surfaceSoft)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(14.dp)) {
            val strokeWidth = 2.dp.toPx()
            if (direction < 0) {
                drawLine(iconColor, Offset(7.dp.toPx(), 2.dp.toPx()), Offset(2.dp.toPx(), 7.dp.toPx()), strokeWidth)
                drawLine(iconColor, Offset(7.dp.toPx(), 2.dp.toPx()), Offset(12.dp.toPx(), 7.dp.toPx()), strokeWidth)
                drawLine(iconColor, Offset(7.dp.toPx(), 3.dp.toPx()), Offset(7.dp.toPx(), 12.dp.toPx()), strokeWidth)
            } else {
                drawLine(iconColor, Offset(7.dp.toPx(), 12.dp.toPx()), Offset(2.dp.toPx(), 7.dp.toPx()), strokeWidth)
                drawLine(iconColor, Offset(7.dp.toPx(), 12.dp.toPx()), Offset(12.dp.toPx(), 7.dp.toPx()), strokeWidth)
                drawLine(iconColor, Offset(7.dp.toPx(), 11.dp.toPx()), Offset(7.dp.toPx(), 2.dp.toPx()), strokeWidth)
            }
        }
    }
}

private fun movedDockActions(
    actions: List<DockAction>,
    action: DockAction,
    offset: Int,
): List<DockAction> {
    val normalizedActions = normalizeDockActions(actions).toMutableList()
    val fromIndex = normalizedActions.indexOf(action)
    if (fromIndex < 0) return normalizedActions
    val toIndex = (fromIndex + offset).coerceIn(0, normalizedActions.lastIndex)
    if (fromIndex == toIndex) return normalizedActions
    normalizedActions.removeAt(fromIndex)
    normalizedActions.add(toIndex, action)
    return normalizedActions
}

@Composable
private fun DockConfig.previewLabel(): String {
    val normalizedConfig = normalized()
    return "${normalizedConfig.plusPlacement.settingsLabel()}  ${normalizedConfig.actions.size}/$MaxDockActions"
}

@Composable
private fun DockPlusPlacement.settingsLabel(): String = when (this) {
    DockPlusPlacement.CENTER -> stringResource(R.string.center)
    DockPlusPlacement.LEFT_EDGE -> stringResource(R.string.left)
    DockPlusPlacement.RIGHT_EDGE -> stringResource(R.string.right)
}

@Composable
private fun SettingsSectionTitle(text: String) {
    val colors = PixelDoneColors.current
    Text(
        text = text,
        color = colors.primary,
        fontFamily = FontFamily.Monospace,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun SettingsActionRow(
    title: String,
    value: String,
    actionText: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = PixelDoneColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, colors.borderWeak, RectangleShape)
            .background(colors.surfaceSoft)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsRowText(
            title = title,
            value = value,
            modifier = Modifier.weight(1f),
        )
        PixelButton(
            text = actionText,
            onClick = onAction,
            enabled = enabled,
            modifier = Modifier.width(104.dp),
            primary = false,
            selected = false,
        )
    }
}

@Composable
private fun SettingsPermissionRow(
    title: String,
    granted: Boolean,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsRowText(
            title = title,
            value = granted.permissionLabel(),
            modifier = Modifier.weight(1f),
        )
        PermissionStatusButton(
            granted = granted,
            onClick = onAction,
        )
    }
}

@Composable
private fun PermissionStatusButton(
    granted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PixelDoneColors.current
    val grantedDescription = stringResource(R.string.permission_granted)
    val setupDescription = stringResource(R.string.permission_needs_setup)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val glyphColor = if (granted) colors.success else colors.error
    Box(
        modifier = modifier
            .size(36.dp)
            .background(if (pressed) colors.selectedSurface else Color.Transparent, RectangleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .semantics {
                contentDescription = if (granted) grantedDescription else setupDescription
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(18.dp)) {
            val strokeWidth = 2.2.dp.toPx()
            if (granted) {
                drawLine(
                    color = glyphColor,
                    start = Offset(2.dp.toPx(), 9.dp.toPx()),
                    end = Offset(7.dp.toPx(), 14.dp.toPx()),
                    strokeWidth = strokeWidth,
                )
                drawLine(
                    color = glyphColor,
                    start = Offset(7.dp.toPx(), 14.dp.toPx()),
                    end = Offset(16.dp.toPx(), 3.dp.toPx()),
                    strokeWidth = strokeWidth,
                )
            } else {
                drawLine(
                    color = glyphColor,
                    start = Offset(9.dp.toPx(), 2.dp.toPx()),
                    end = Offset(9.dp.toPx(), 12.dp.toPx()),
                    strokeWidth = strokeWidth,
                )
                drawCircle(
                    color = glyphColor,
                    radius = 1.6.dp.toPx(),
                    center = Offset(9.dp.toPx(), 16.dp.toPx()),
                )
            }
        }
    }
}

@Composable
private fun SettingsAboutTextRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
) {
    val colors = PixelDoneColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(128.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = valueColor ?: colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun <T> SettingsSegmentedRow(
    title: String,
    value: T,
    options: List<T>,
    label: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PixelDoneColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, colors.borderWeak, RectangleShape)
            .background(colors.surfaceSoft)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SettingsRowText(title = title, value = label(value))
        PixelSegmentedControl(
            options = options,
            selected = value,
            label = label,
            onSelected = onSelected,
        )
    }
}

@Composable
private fun SettingsRowText(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
) {
    val colors = PixelDoneColors.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = valueColor ?: colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Boolean.permissionLabel(): String =
    if (this) stringResource(R.string.granted) else stringResource(R.string.needs_setup)

@Composable
private fun SyncCoordinatorStatus.settingsLabel(): String = when (this) {
    SyncCoordinatorStatus.LOCAL_ONLY -> stringResource(R.string.local_only)
    SyncCoordinatorStatus.NOT_CONFIGURED -> stringResource(R.string.needs_setup)
    SyncCoordinatorStatus.SIGNED_OUT -> stringResource(R.string.signed_out)
    SyncCoordinatorStatus.IDLE -> stringResource(R.string.ready)
    SyncCoordinatorStatus.SYNCING -> stringResource(R.string.syncing)
    SyncCoordinatorStatus.SYNCED -> stringResource(R.string.synced)
    SyncCoordinatorStatus.CONFLICT -> stringResource(R.string.conflict)
    SyncCoordinatorStatus.SERVER_UPDATE_REQUIRED -> stringResource(R.string.server_update_required)
    SyncCoordinatorStatus.ERROR -> stringResource(R.string.error)
}

internal fun SyncCoordinatorStatus.settingsValueColor(colors: PixelDonePalette): Color = when (this) {
    SyncCoordinatorStatus.ERROR,
    SyncCoordinatorStatus.SERVER_UPDATE_REQUIRED,
    SyncCoordinatorStatus.CONFLICT -> colors.error
    SyncCoordinatorStatus.SYNCED -> colors.success
    else -> colors.textSecondary
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
    val colors = PixelDoneColors.current

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
                text = if (isEditing) stringResource(R.string.edit_task) else stringResource(R.string.new_task),
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary,
            )
            Text(
                text = if (isEditing) stringResource(R.string.cancel) else stringResource(R.string.close),
                style = MaterialTheme.typography.labelLarge,
                color = colors.primaryInteractive,
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
                label = { Text(stringResource(R.string.name)) },
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
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.borderWeak,
                    focusedContainerColor = colors.surfaceSoft,
                    unfocusedContainerColor = colors.surfaceSoft,
                    cursorColor = colors.primaryInteractive,
                    focusedLabelColor = colors.primaryInteractive,
                    unfocusedLabelColor = colors.textSecondary,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                ),
            )
            if (compactForKeyboard) {
                return@Column
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.field_priority),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(6.dp))
            PrioritySlider(
                selected = selectedPriority,
                onSelected = onPriorityChange,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.time_alarm),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
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
                text = stringResource(R.string.field_repeat),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
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
                    text = stringResource(R.string.delete_task),
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
                text = if (isEditing) stringResource(R.string.save) else stringResource(R.string.add),
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
                        text = stringResource(R.string.cancel),
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
private fun CloudAuthEditorPanel(
    authInput: AuthInputState,
    onAuthEmailChange: (String) -> Unit,
    onAuthPasswordChange: (String) -> Unit,
    onAuthModeChange: (CloudAuthMode) -> Unit,
    onSignIn: () -> Unit,
    onSignUp: () -> Unit,
    onResetPassword: () -> Unit = {},
    onCancelAuth: () -> Unit,
    compactForKeyboard: Boolean,
) {
    val focusManager = LocalFocusManager.current
    val emailFocusRequester = remember { FocusRequester() }
    val colors = PixelDoneColors.current
    val verticalGap = if (compactForKeyboard) 8.dp else 12.dp
    val submitAuth = if (authInput.mode == CloudAuthMode.SIGN_UP) onSignUp else onSignIn
    val submitText = when {
        authInput.busy && authInput.mode == CloudAuthMode.SIGN_UP -> stringResource(R.string.signing_up)
        authInput.busy -> stringResource(R.string.signing_in)
        authInput.mode == CloudAuthMode.SIGN_UP -> stringResource(R.string.sign_up)
        else -> stringResource(R.string.sign_in)
    }
    var passwordVisible by remember { mutableStateOf(false) }
    val passwordTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation()

    LaunchedEffect(Unit) {
        emailFocusRequester.requestFocus()
    }

    PixelPanel {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCancelAuth() },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = authInput.mode.cloudAuthTitle(),
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary,
            )
            Text(
                text = stringResource(R.string.cancel),
                style = MaterialTheme.typography.labelLarge,
                color = colors.primaryInteractive,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        PixelSegmentedControl(
            options = listOf(CloudAuthMode.SIGN_IN, CloudAuthMode.SIGN_UP),
            selected = authInput.mode,
            label = { it.cloudAuthLabel() },
            onSelected = { mode ->
                if (!authInput.busy) onAuthModeChange(mode)
            },
        )
        Spacer(modifier = Modifier.height(verticalGap))
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = authInput.email,
                onValueChange = onAuthEmailChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(emailFocusRequester),
                singleLine = true,
                shape = RectangleShape,
                textStyle = MaterialTheme.typography.bodyLarge,
                label = { Text(stringResource(R.string.email)) },
                enabled = !authInput.busy,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
                colors = settingsTextFieldColors(colors),
            )
            Spacer(modifier = Modifier.height(verticalGap))
            OutlinedTextField(
                value = authInput.password,
                onValueChange = onAuthPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = authInput.error != null,
                shape = RectangleShape,
                textStyle = MaterialTheme.typography.bodyLarge,
                label = { Text(stringResource(R.string.password)) },
                enabled = !authInput.busy,
                visualTransformation = passwordTransformation,
                trailingIcon = {
                    SettingsTextAction(
                    text = if (passwordVisible) stringResource(R.string.hide) else stringResource(R.string.show),
                        onClick = { passwordVisible = !passwordVisible },
                        enabled = !authInput.busy,
                        modifier = Modifier.width(56.dp),
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submitAuth() }),
                colors = settingsTextFieldColors(colors),
            )
            authInput.error?.let { error ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = localizedAuthText(error),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.error,
                )
            }
            if (authInput.mode == CloudAuthMode.SIGN_IN) {
                Spacer(modifier = Modifier.height(6.dp))
                SettingsTextAction(
                    text = stringResource(R.string.reset),
                    onClick = onResetPassword,
                    enabled = !authInput.busy,
                    modifier = Modifier.width(72.dp),
                )
            }
            Spacer(modifier = Modifier.height(verticalGap))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PixelButton(
                    text = submitText,
                    onClick = {
                        submitAuth()
                        focusManager.clearFocus()
                    },
                    enabled = !authInput.busy,
                    modifier = Modifier.weight(1f),
                    primary = true,
                )
                PixelButton(
                    text = stringResource(R.string.cancel),
                    onClick = {
                        focusManager.clearFocus()
                        onCancelAuth()
                    },
                    modifier = Modifier.weight(1f),
                    primary = false,
                )
            }
        }
    }
}

@Composable
private fun CloudAuthMode.cloudAuthLabel(): String = when (this) {
        CloudAuthMode.SIGN_IN -> stringResource(R.string.sign_in)
        CloudAuthMode.SIGN_UP -> stringResource(R.string.sign_up)
}

@Composable
private fun CloudAuthMode.cloudAuthTitle(): String = when (this) {
        CloudAuthMode.SIGN_IN -> stringResource(R.string.sync_sign_in)
        CloudAuthMode.SIGN_UP -> stringResource(R.string.sync_sign_up)
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
    val colors = PixelDoneColors.current

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
                text = if (isEditing) stringResource(R.string.edit_list) else stringResource(R.string.new_list),
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary,
            )
            Text(
                text = stringResource(R.string.cancel),
                style = MaterialTheme.typography.labelLarge,
                color = colors.primaryInteractive,
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
                label = { Text(stringResource(R.string.list_name)) },
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
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.borderWeak,
                    errorBorderColor = colors.error,
                    focusedContainerColor = colors.surfaceSoft,
                    unfocusedContainerColor = colors.surfaceSoft,
                    errorContainerColor = colors.surfaceSoft,
                    cursorColor = colors.primaryInteractive,
                    focusedLabelColor = colors.primaryInteractive,
                    unfocusedLabelColor = colors.textSecondary,
                    errorLabelColor = colors.error,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    errorTextColor = colors.textPrimary,
                ),
            )
            errorText?.let { text ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.error,
                )
            }
            if (compactForKeyboard) {
                return@Column
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (isEditing) {
                PixelButton(
                    text = if (canDeleteChecklist) stringResource(R.string.delete_list) else stringResource(R.string.keep_one_list),
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
                    text = stringResource(R.string.save),
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
                    text = stringResource(R.string.cancel),
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
    targetMoveChecklists: List<TodoChecklist>,
    dockConfig: DockConfig,
    batchMoveMode: BatchMoveMode,
    batchMoveSelectedTodoIds: Set<String>,
    batchDeleteActive: Boolean,
    onToggleBatchMoveSelection: (String) -> Unit,
    onOpenBatchMoveTargetPicker: () -> Unit,
    onCloseBatchMoveTargetPicker: () -> Unit,
    onCancelBatchMove: () -> Unit,
    onMoveSelectedTodosToChecklist: (String, List<String>) -> Unit,
    onToggleBatchDelete: () -> Unit,
    onQuickDeleteTodo: (String) -> Unit,
    displayOrderIds: List<String>,
    keepDisplayOrder: Boolean,
    todoListScrollRequest: TodoListScrollRequest,
    todoListHighlightRequest: TodoListHighlightRequest,
    onTodoListHighlightConsumed: (Int) -> Unit,
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
    val orderedBatchMoveSelectedIds = visibleItemIds.filter { it in batchMoveSelectedTodoIds }
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
    val batchMoveActive = batchMoveMode != BatchMoveMode.IDLE
    val showBottomActions = !isTrashSelected && showNewTaskButton
    val listBottomPadding = when {
        !showBottomActions -> 4.dp
        batchMoveActive -> 96.dp
        else -> 84.dp
    }

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
        if (todoListHighlightRequest.sequence > 0) {
            onTodoListHighlightConsumed(todoListHighlightRequest.sequence)
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
            Text(
                text = stringResource(R.string.trash_retention),
                style = MaterialTheme.typography.labelSmall,
                color = PixelDoneColors.current.textSecondary,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            PixelButton(
                text = stringResource(R.string.delete_all),
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    onDeleteAllTrash()
                },
                enabled = todos.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                destructive = true,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            if (visibleItems.isEmpty()) {
                EmptyState(
                    text = if (isTrashSelected) {
                        stringResource(R.string.trash_empty)
                    } else if (todos.isEmpty()) {
            stringResource(R.string.add_task_to_begin)
                    } else {
            stringResource(R.string.done_tasks_hidden)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = listBottomPadding),
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
                                batchMoveActive = batchMoveActive,
                                batchDeleteActive = batchDeleteActive,
                                selectedForBatch = item.id in batchMoveSelectedTodoIds,
                                onToggleBatchSelection = onToggleBatchMoveSelection,
                                onQuickDeleteTodo = onQuickDeleteTodo,
                            )
                        }
                    }
                }
            }
            if (showBottomActions) {
                when {
                    batchMoveMode == BatchMoveMode.TARGET_PICKER -> {
                        BatchMoveTargetPanel(
                            targetChecklists = targetMoveChecklists,
                            onTargetSelected = { targetChecklistId ->
                                onMoveSelectedTodosToChecklist(
                                    targetChecklistId,
                                    orderedBatchMoveSelectedIds,
                                )
                            },
                            onCancel = onCloseBatchMoveTargetPicker,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                            .padding(bottom = 72.dp),
                        )
                    }
                }
                if (batchMoveActive) {
                    BatchMoveBar(
                        selectedCount = batchMoveSelectedTodoIds.size,
                        onMove = onOpenBatchMoveTargetPicker,
                        onCancel = onCancelBatchMove,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp),
                    )
                } else {
                    BottomActionDock(
                        config = dockConfig,
                        actionState = dockActionState(
                            sortMode = sortMode,
                            hideCompleted = hideCompleted,
                            showDeadlineCountdown = showDeadlineCountdown,
                            completedCount = completedCount,
                            totalCount = todos.size,
                            batchDeleteActive = batchDeleteActive,
                        ),
                        onActionClick = { action ->
                            when (action) {
                                DockAction.SORT -> {
                                    onSortModeChange(
                                        if (sortMode == SortMode.PRIORITY) {
                                            SortMode.TIME
                                        } else {
                                            SortMode.PRIORITY
                                        },
                                    )
                                }
                                DockAction.DEADLINE -> onDeadlineCountdownChange(!showDeadlineCountdown)
                                DockAction.HIDE_DONE -> onHideCompletedChange(!hideCompleted)
                                DockAction.DELETE_DONE -> onDeleteCompleted()
                                DockAction.BATCH_DELETE -> onToggleBatchDelete()
                            }
                        },
                        onAddClick = onOpenTaskEditor,
                        onAddLongClick = onOpenChecklistEditor,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp),
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
private fun BatchMoveBar(
    selectedCount: Int,
    onMove: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PixelDoneColors.current
    PixelPanel(
        modifier = modifier,
        borderWidth = 1.dp,
        contentPadding = PaddingValues(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$selectedCount ${stringResource(R.string.selected)}",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            PixelButton(
                text = stringResource(R.string.move),
                onClick = onMove,
                enabled = selectedCount > 0,
                primary = selectedCount > 0,
                modifier = Modifier.width(96.dp),
            )
            PixelButton(
                text = stringResource(R.string.cancel),
                onClick = onCancel,
                modifier = Modifier.width(104.dp),
            )
        }
    }
}

@Composable
private fun BatchMoveTargetPanel(
    targetChecklists: List<TodoChecklist>,
    onTargetSelected: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PixelDoneColors.current
    val scrollState = rememberScrollState()
    PixelPanel(
        modifier = modifier,
        borderWidth = 1.dp,
        contentPadding = PaddingValues(8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PanelLabel(stringResource(R.string.target_list))
            if (targetChecklists.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_target_list),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    targetChecklists.forEach { checklist ->
                        PixelButton(
                            text = checklist.name.uppercase(Locale.US),
                            onClick = { onTargetSelected(checklist.id) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            PixelButton(
                text = stringResource(R.string.cancel),
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PanelLabel(text: String) {
    val colors = PixelDoneColors.current
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = colors.textSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun BatchSelectionMarker(
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = PixelDoneColors.current
    Box(
        modifier = modifier
            .size(16.dp)
            .border(1.dp, if (selected) colors.primaryInteractive else colors.borderWeak, RectangleShape)
            .background(if (selected) colors.selectedSurface else colors.surfaceRaised),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Canvas(modifier = Modifier.size(10.dp)) {
                val strokeWidth = 1.8.dp.toPx()
                drawLine(
                    color = colors.textPrimary,
                    start = Offset(1.dp.toPx(), 5.dp.toPx()),
                    end = Offset(4.dp.toPx(), 8.dp.toPx()),
                    strokeWidth = strokeWidth,
                )
                drawLine(
                    color = colors.textPrimary,
                    start = Offset(4.dp.toPx(), 8.dp.toPx()),
                    end = Offset(9.dp.toPx(), 2.dp.toPx()),
                    strokeWidth = strokeWidth,
                )
            }
        }
    }
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
    batchMoveActive: Boolean,
    batchDeleteActive: Boolean,
    selectedForBatch: Boolean,
    onToggleBatchSelection: (String) -> Unit,
    onQuickDeleteTodo: (String) -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val colors = PixelDoneColors.current
    val itemBackground = when {
        selectedForBatch -> colors.selectedSurface
        item.completed -> colors.completedSurface
        else -> colors.surfaceSoft
    }
    val borderColor by animateColorAsState(
        targetValue = if (highlighted || selectedForBatch) colors.primaryInteractive else colors.borderWeak,
        animationSpec = tween(durationMillis = TodoHighlightFadeMillis),
        label = "todoRowHighlightBorder",
    )
    val borderWidth = if (highlighted || selectedForBatch) 2.dp else 1.dp
    val dueDateTime = item.dueAtMillis.formatDateTime()
    val dueDateTimeColor = if (item.dueAtMillis.isExpired(nowMillis)) colors.error else colors.textSecondary
    val repeatText = if (item.reminderRepeat != ReminderRepeat.NONE) {
        "  ${item.reminderRepeat.uiLabel()}"
    } else {
        ""
    }
    val priorityText = item.priority.uiLabel()
    val deadlineText = localizedDeadlineCountdown(item.dueAtMillis, nowMillis)
    val showXHighAlarmIcon = item.priority == TodoPriority.XHIGH && !item.completed
    val rowPadding = if (item.completed) {
        PaddingValues(horizontal = 8.dp, vertical = 6.dp)
    } else {
        PaddingValues(8.dp)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(borderWidth, borderColor, RectangleShape)
            .background(itemBackground)
            .clickable {
                if (batchMoveActive) {
                    onToggleBatchSelection(item.id)
                } else if (batchDeleteActive) {
                    Unit
                } else {
                    when (todoRowClickAction(item.id, editingTaskId)) {
                        TodoRowClickAction.Edit -> onEditTodo(item)
                        TodoRowClickAction.CancelEdit -> onCancelEdit()
                    }
                }
            }
            .padding(rowPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .width(5.dp)
                .height(if (item.completed) 32.dp else 48.dp)
                .background(item.priority.priorityColor()),
        )
        if (batchMoveActive) {
            BatchSelectionMarker(selected = selectedForBatch)
        }
        Checkbox(
            checked = item.completed,
            onCheckedChange = { checked ->
                if (checked) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                }
                onToggleTodo(item.id, checked)
            },
            colors = CheckboxDefaults.colors(
                checkedColor = colors.success,
                uncheckedColor = colors.textSecondary,
                checkmarkColor = colors.background,
            ),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (item.completed) colors.textSecondary else colors.textPrimary,
                textDecoration = if (item.completed) TextDecoration.LineThrough else null,
                maxLines = if (item.completed) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!item.completed) {
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
                            priorityText = priorityText,
                            deadlineText = deadlineText,
                            showDeadlineCountdown = showDeadlineCountdown,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (showXHighAlarmIcon) {
                        Spacer(modifier = Modifier.width(4.dp))
                        PixelAlarmIcon(
                            color = colors.textSecondary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
        if (batchDeleteActive && !batchMoveActive) {
            PixelItemDeleteButton(
                onClick = { onQuickDeleteTodo(item.id) },
            )
        } else if (!item.completed && !batchMoveActive) {
            PixelItemImageButton(
                hasImage = item.imageFileName != null,
                onClick = { onOpenTodoImage(item) },
            )
        }
    }
}

@Composable
private fun TrashTodoRow(
    item: TodoItem,
    onRestoreTodo: (String) -> Unit,
) {
    val colors = PixelDoneColors.current
    val itemBackground = if (item.completed) colors.completedSurface else colors.surfaceSoft
    val sourceName = item.trashedFromChecklistName?.takeIf { it.isNotBlank() }
        ?: DefaultChecklistName
    val rowPadding = if (item.completed) {
        PaddingValues(horizontal = 8.dp, vertical = 6.dp)
    } else {
        PaddingValues(8.dp)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.borderWeak, RectangleShape)
            .background(itemBackground)
            .padding(rowPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .width(5.dp)
                .height(if (item.completed) 32.dp else 48.dp)
                .background(item.priority.priorityColor()),
        )
        Checkbox(
            checked = item.completed,
            onCheckedChange = null,
            enabled = false,
            colors = CheckboxDefaults.colors(
                checkedColor = colors.success,
                uncheckedColor = colors.textSecondary,
                disabledCheckedColor = colors.success.copy(alpha = 0.45f),
                disabledUncheckedColor = colors.borderWeak,
                disabledIndeterminateColor = colors.borderWeak,
                checkmarkColor = colors.background,
            ),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (item.completed) colors.textSecondary else colors.textPrimary,
                textDecoration = if (item.completed) TextDecoration.LineThrough else null,
                maxLines = if (item.completed) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!item.completed) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.from_list, sourceName),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        PixelRestoreButton(
            onClick = { onRestoreTodo(item.id) },
        )
    }
}

@Composable
private fun EmptyState(text: String, modifier: Modifier = Modifier) {
    val colors = PixelDoneColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, colors.borderWeak, RectangleShape)
            .background(colors.surfaceRaised)
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun Footer(
    updateUiState: AppUpdateUiState,
    onUpdateClick: () -> Unit,
) {
    val colors = PixelDoneColors.current
    val message = updateUiState.message
    val updateContentDescription = updateUiState.status.localizedContentDescription()
    if (message != null) {
        val messageColor = when (updateUiState.status) {
            UpdateUiStatus.Offline -> colors.error
            UpdateUiStatus.Available,
            UpdateUiStatus.Downloading,
            UpdateUiStatus.Installing,
            -> colors.primary
            else -> colors.textPrimary
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(PixelDoneFooterHeight)
                .clickable(
                    enabled = updateUiState.status == UpdateUiStatus.Available,
                    onClick = onUpdateClick,
                )
                .semantics { contentDescription = updateContentDescription },
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
            color = colors.textPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
            maxLines = 1,
        )
        Text(
            text = DeveloperCredit,
            color = colors.primary,
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
            idleColor = colors.textSecondary,
            activeColor = colors.primary,
            errorColor = colors.error,
        )
    }
}

@Composable
private fun UpdateUiStatus.localizedContentDescription(): String = when (this) {
    UpdateUiStatus.Idle -> stringResource(R.string.check_update)
    UpdateUiStatus.Checking -> stringResource(R.string.checking_update)
    UpdateUiStatus.Latest -> stringResource(R.string.latest_version)
    UpdateUiStatus.Available -> stringResource(R.string.update_available)
    UpdateUiStatus.Offline -> stringResource(R.string.update_failed)
    UpdateUiStatus.Downloading -> stringResource(R.string.downloading_update)
    UpdateUiStatus.Installing -> stringResource(R.string.install_updates)
}

@Composable
private fun localizedAuthText(text: String): String = when (text) {
    "Signed in." -> stringResource(R.string.auth_signed_in)
    "Sign in failed." -> stringResource(R.string.auth_sign_in_failed)
    "Signed up." -> stringResource(R.string.auth_signed_up)
    "Sign up failed." -> stringResource(R.string.auth_sign_up_failed)
    "Email and password are required." -> stringResource(R.string.auth_email_password_required)
    "Signed out." -> stringResource(R.string.auth_signed_out)
    "Sign out failed." -> stringResource(R.string.auth_sign_out_failed)
    "Email is required." -> stringResource(R.string.auth_email_required)
    "Reset email sent." -> stringResource(R.string.auth_reset_sent)
    "Reset failed." -> stringResource(R.string.auth_reset_failed)
    else -> text
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
    val updateContentDescription = state.status.localizedContentDescription()

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
                .semantics { contentDescription = updateContentDescription },
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
private fun DialogActionRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun DialogTextActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PixelDoneColors.current
    Box(
        modifier = modifier
            .heightIn(min = DialogActionMinHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = colors.primaryInteractive,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SyncConflictDialog(
    visible: Boolean,
    conflicts: List<SyncConflictEntry>,
    resolvingConflictKey: String?,
    onResolve: (String, String, ConflictResolutionChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    val colors = PixelDoneColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.sync_conflicts),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
        },
        text = {
            if (conflicts.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_conflicts),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(
                        items = conflicts,
                        key = { conflict -> conflict.recordType + ":" + conflict.localId },
                    ) { conflict ->
                        val conflictKey = conflict.recordType + ":" + conflict.localId
                        SyncConflictReviewItem(
                            conflict = conflict,
                            resolving = resolvingConflictKey == conflictKey,
                            onKeepLocal = {
                                onResolve(
                                    conflict.recordType,
                                    conflict.localId,
                                    ConflictResolutionChoice.KEEP_LOCAL,
                                )
                            },
                            onKeepCloud = {
                                onResolve(
                                    conflict.recordType,
                                    conflict.localId,
                                    ConflictResolutionChoice.KEEP_CLOUD,
                                )
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            DialogTextActionButton(
                text = stringResource(R.string.close),
                onClick = onDismiss,
            )
        },
        shape = RectangleShape,
        containerColor = colors.surface,
        tonalElevation = 0.dp,
    )
}

@Composable
private fun SyncConflictReviewItem(
    conflict: SyncConflictEntry,
    resolving: Boolean,
    onKeepLocal: () -> Unit,
    onKeepCloud: () -> Unit,
) {
    val colors = PixelDoneColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.borderWeak, RectangleShape)
            .background(colors.surfaceSoft)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = conflict.title,
            style = MaterialTheme.typography.labelLarge,
            color = colors.textPrimary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        conflict.fields.forEach { field ->
            val localValue = conflict.localValues.firstOrNull { it.label == field }?.value.orEmpty()
            val cloudValue = conflict.cloudValues.firstOrNull { it.label == field }?.value.orEmpty()
            SyncConflictFieldRow(
                field = field,
                localValue = localValue,
                cloudValue = cloudValue,
            )
        }

        DialogActionRow(modifier = Modifier.fillMaxWidth()) {
            PixelButton(
                text = if (resolving) "..." else stringResource(R.string.keep_local),
                onClick = onKeepLocal,
                enabled = !resolving,
                modifier = Modifier.weight(1f),
                primary = false,
            )
            PixelButton(
                text = if (resolving) "..." else stringResource(R.string.keep_cloud),
                onClick = onKeepCloud,
                enabled = !resolving,
                modifier = Modifier.weight(1f),
                primary = true,
            )
        }
    }
}

@Composable
private fun SyncConflictFieldRow(
    field: String,
    localValue: String,
    cloudValue: String,
    modifier: Modifier = Modifier,
) {
    val colors = PixelDoneColors.current
    Column(
        modifier = modifier
            .border(1.dp, colors.borderWeak, RectangleShape)
            .background(colors.surface)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = conflictFieldLabel(field),
            style = MaterialTheme.typography.labelSmall,
            color = colors.error,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.local) + "  " + localValue,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.cloud) + "  " + cloudValue,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun conflictFieldLabel(field: String): String = when (field) {
    "name" -> stringResource(R.string.field_name)
    "sort" -> stringResource(R.string.field_sort)
    "checklist" -> stringResource(R.string.field_checklist)
    "title" -> stringResource(R.string.field_title)
    "priority" -> stringResource(R.string.field_priority)
    "due" -> stringResource(R.string.field_due)
    "completed" -> stringResource(R.string.field_completed)
    "repeat" -> stringResource(R.string.field_repeat)
    "image" -> stringResource(R.string.field_image)
    "trash" -> stringResource(R.string.field_trash)
    "language" -> stringResource(R.string.field_language)
    else -> field
}
@Composable
private fun UpdateAvailableDialog(
    info: AppUpdateInfo?,
    currentVersion: String,
    neverShowUpdateDialog: Boolean,
    onNeverShowUpdateDialogChange: (Boolean) -> Unit,
    onUpdate: (AppUpdateInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    if (info == null) return
    val colors = PixelDoneColors.current
    val enabledDescription = stringResource(R.string.enabled)
    val disabledDescription = stringResource(R.string.disabled)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.update_available),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.update_available_detail, currentVersion, info.version),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                Text(
                    modifier = Modifier
                        .toggleable(
                            value = neverShowUpdateDialog,
                            role = Role.Checkbox,
                            onValueChange = onNeverShowUpdateDialogChange,
                        )
                        .semantics {
                            stateDescription = if (neverShowUpdateDialog) {
                                enabledDescription
                            } else {
                                disabledDescription
                            }
                        }
                        .padding(vertical = 2.dp),
                    text = stringResource(R.string.do_not_show_again),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (neverShowUpdateDialog) colors.primaryInteractive else colors.textSecondary,
                    fontWeight = if (neverShowUpdateDialog) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        confirmButton = {
            DialogActionRow {
                DialogTextActionButton(
                    text = stringResource(R.string.later),
                    onClick = onDismiss,
                )
                PixelButton(
                    text = stringResource(R.string.update),
                    onClick = { onUpdate(info) },
                    primary = true,
                )
            }
        },
        shape = RectangleShape,
        containerColor = colors.surface,
        tonalElevation = 0.dp,
    )
}

@Composable
private fun UpdateDownloadProgressDialog(
    download: AppUpdateDownload?,
    updateUiState: AppUpdateUiState,
    onDismiss: () -> Unit,
) {
    if (download == null) return

    val colors = PixelDoneColors.current
    val context = LocalContext.current
    val message = updateUiState.message ?: formatLocalizedUpdateDownloadMessage(context, download.version)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.downloading_update),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                UpdateProgressBar(progress = updateUiState.progress)
                Text(
                    text = stringResource(R.string.download_continues_silently),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        },
        confirmButton = {
            DialogTextActionButton(
                text = stringResource(R.string.close),
                onClick = onDismiss,
            )
        },
        shape = RectangleShape,
        containerColor = colors.surface,
        tonalElevation = 0.dp,
    )
}

@Composable
private fun UpdateProgressBar(
    progress: AppUpdateDownloadProgress,
    modifier: Modifier = Modifier,
) {
    val colors = PixelDoneColors.current
    val percent = progress.percent
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .border(1.dp, colors.primary, RectangleShape)
            .background(colors.primary.copy(alpha = 0.14f)),
    ) {
        if (percent != null && percent > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(percent.coerceIn(0, 100) / 100f)
                    .height(8.dp)
                    .background(colors.primary),
            )
        }
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
    val colors = PixelDoneColors.current

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
                    text = stringResource(R.string.task_image),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                DialogTextActionButton(
                    text = stringResource(R.string.close),
                    onClick = onDismiss,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 320.dp)
                        .border(1.dp, colors.borderWeak, RectangleShape)
                        .background(colors.surfaceSoft)
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
                                text = stringResource(R.string.loading_image),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                            )
                        }
                        TodoImagePreviewLoadState.Unavailable -> {
                            Text(
                                text = stringResource(R.string.image_unavailable),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                            )
                        }
                        is TodoImagePreviewLoadState.Ready -> {
                            Image(
                                bitmap = state.bitmap,
                                contentDescription = stringResource(R.string.task_image_preview),
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
            DialogActionRow {
                PixelButton(
                    text = stringResource(R.string.change),
                    onClick = { onChange(item) },
                    clayOutline = true,
                )
                PixelButton(
                    text = stringResource(R.string.remove),
                    onClick = { onRemove(item) },
                    destructive = true,
                )
            }
        },
        shape = RectangleShape,
        containerColor = colors.surface,
        tonalElevation = 0.dp,
    )
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
    val colors = PixelDoneColors.current
    val titleText = when (confirmation) {
        is DeleteConfirmation.SingleTodo -> stringResource(R.string.delete_task_title)
        is DeleteConfirmation.CompletedTodos -> stringResource(R.string.delete_done_title)
        is DeleteConfirmation.Checklist -> stringResource(R.string.delete_list_title)
        is DeleteConfirmation.TrashTodos -> stringResource(R.string.delete_trash_title)
    }
    val bodyText = when (confirmation) {
        is DeleteConfirmation.SingleTodo ->
            stringResource(R.string.move_task_to_trash, confirmation.title)
        is DeleteConfirmation.CompletedTodos -> pluralStringResource(
            R.plurals.move_completed_to_trash,
            confirmation.count,
            confirmation.count,
        )
        is DeleteConfirmation.Checklist -> pluralStringResource(
            R.plurals.move_list_to_trash,
            confirmation.todoCount,
            confirmation.name,
            confirmation.todoCount,
        )
        is DeleteConfirmation.TrashTodos -> pluralStringResource(
            R.plurals.permanently_delete_tasks,
            confirmation.count,
            confirmation.count,
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = titleText,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
        },
        text = {
            Text(
                text = bodyText,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        },
        confirmButton = {
            DialogActionRow {
                DialogTextActionButton(
                    text = stringResource(R.string.close),
                    onClick = onDismiss,
                )
                PixelButton(
                    text = stringResource(R.string.delete),
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        onConfirm()
                    },
                    destructive = true,
                )
            }
        },
        shape = RectangleShape,
        containerColor = colors.surface,
        tonalElevation = 0.dp,
    )
}

private val DateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val TimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DateTimeUiFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private fun formatLocalizedUpdateDownloadMessage(
    context: Context,
    version: String,
    progress: AppUpdateDownloadProgress = AppUpdateDownloadProgress(),
): String {
    progress.percent?.let { percent ->
        return context.getString(R.string.downloading_version_percent, version, percent)
    }
    return if (progress.bytesDownloaded > 0L) {
        context.getString(
            R.string.downloading_version_size,
            version,
            formatDownloadedMegabytes(progress.bytesDownloaded),
        )
    } else {
        context.getString(R.string.downloading_version, version)
    }
}

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

@Composable
private fun localizedDeadlineCountdown(dueAtMillis: Long, nowMillis: Long): String {
    if (dueAtMillis <= 0L) return stringResource(R.string.deadline_none)

    val millisUntilDue = dueAtMillis - nowMillis
    val absoluteMillis = kotlin.math.abs(millisUntilDue)
    val totalMinutes = absoluteMillis / 60_000L
    val days = totalMinutes / (24L * 60L)
    val hours = (totalMinutes % (24L * 60L)) / 60L
    val minutes = totalMinutes % 60L
    val value = stringResource(
        R.string.deadline_duration,
        days,
        hours.toTwoDigits(),
        minutes.toTwoDigits(),
    )
    return if (millisUntilDue <= 0L) {
        stringResource(R.string.deadline_overdue_value, value)
    } else {
        stringResource(R.string.deadline_remaining_value, value)
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
    priorityText: String,
    deadlineText: String,
    showDeadlineCountdown: Boolean,
) = buildAnnotatedString {
    if (showDeadlineCountdown) {
        withStyle(SpanStyle(color = dueDateTimeColor)) {
            append(deadlineText)
        }
    } else {
        append(priorityText)
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
            isSettingsSelected = false,
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
            cloudSignInEditorVisible = false,
            onOpenCloudSignIn = {},
            onCancelCloudSignIn = {},
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
            targetMoveChecklists = checklists.filter {
                isNormalChecklist(it) && it.id != checklists.first().id
            },
            dockConfig = DockConfig(),
            onDockConfigChange = {},
            batchMoveMode = BatchMoveMode.IDLE,
            batchMoveSelectedTodoIds = emptySet(),
            batchDeleteActive = false,
            onToggleBatchMoveSelection = { _ -> },
            onOpenBatchMoveTargetPicker = {},
            onCloseBatchMoveTargetPicker = {},
            onCancelBatchMove = {},
            onMoveSelectedTodosToChecklist = { _, _ -> },
            onToggleBatchDelete = {},
            onQuickDeleteTodo = {},
            displayOrderIds = emptyList(),
            keepDisplayOrder = false,
            todoListScrollRequest = previewScrollRequest(),
            todoListHighlightRequest = previewHighlightRequest(),
            onTodoListHighlightConsumed = {},
            updateUiState = AppUpdateUiState(),
            onUpdateClick = {},
            darkTheme = false,
            onDarkThemeChange = {},
            language = AppLanguage.SYSTEM,
            onLanguageChange = {},
            showUpdateDialogs = true,
            onShowUpdateDialogsChange = {},
            currentVersion = "2.9.0",
            syncStatusText = "LOCAL ONLY",
            syncStatus = SyncCoordinatorStatus.LOCAL_ONLY,
            authSession = AuthSession(),
            authInput = AuthInputState(),
            onAuthEmailChange = {},
            onAuthPasswordChange = {},
            onAuthModeChange = {},
            onSignIn = {},
            onSignUp = {},
            onSignOut = {},
            onSyncNow = {},
            permissionSettingsState = previewPermissionSettingsState(),
            onRequestNotificationPermission = {},
            onRequestExactAlarmPermission = {},
            onRequestFullScreenIntentPermission = {},
            onRequestInstallUpdatesPermission = {},
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
            isSettingsSelected = false,
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
            cloudSignInEditorVisible = false,
            onOpenCloudSignIn = {},
            onCancelCloudSignIn = {},
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
            targetMoveChecklists = checklists.filter {
                isNormalChecklist(it) && it.id != checklists.first().id
            },
            dockConfig = DockConfig(),
            onDockConfigChange = {},
            batchMoveMode = BatchMoveMode.IDLE,
            batchMoveSelectedTodoIds = emptySet(),
            batchDeleteActive = false,
            onToggleBatchMoveSelection = { _ -> },
            onOpenBatchMoveTargetPicker = {},
            onCloseBatchMoveTargetPicker = {},
            onCancelBatchMove = {},
            onMoveSelectedTodosToChecklist = { _, _ -> },
            onToggleBatchDelete = {},
            onQuickDeleteTodo = {},
            displayOrderIds = emptyList(),
            keepDisplayOrder = false,
            todoListScrollRequest = previewScrollRequest(),
            todoListHighlightRequest = previewHighlightRequest(),
            onTodoListHighlightConsumed = {},
            updateUiState = AppUpdateUiState(status = UpdateUiStatus.Available),
            onUpdateClick = {},
            darkTheme = false,
            onDarkThemeChange = {},
            language = AppLanguage.SYSTEM,
            onLanguageChange = {},
            showUpdateDialogs = true,
            onShowUpdateDialogsChange = {},
            currentVersion = "2.7.0",
            syncStatusText = "LOCAL ONLY",
            syncStatus = SyncCoordinatorStatus.LOCAL_ONLY,
            authSession = AuthSession(),
            authInput = AuthInputState(),
            onAuthEmailChange = {},
            onAuthPasswordChange = {},
            onAuthModeChange = {},
            onSignIn = {},
            onSignUp = {},
            onSignOut = {},
            onSyncNow = {},
            permissionSettingsState = previewPermissionSettingsState(),
            onRequestNotificationPermission = {},
            onRequestExactAlarmPermission = {},
            onRequestFullScreenIntentPermission = {},
            onRequestInstallUpdatesPermission = {},
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
        TodoChecklist(
            id = SettingsChecklistId,
            name = SettingsChecklistName,
            items = emptyList(),
            createdAtMillis = 4L,
        ),
    )
}

private fun previewPermissionSettingsState(): PermissionSettingsState {
    return PermissionSettingsState(
        notificationsGranted = true,
        exactAlarmGranted = false,
        fullScreenIntentGranted = true,
        installUpdatesGranted = false,
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
