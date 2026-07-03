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
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import com.milesxue.pixeldone.ui.theme.PixelDoneTheme
import com.milesxue.pixeldone.ui.todo.components.FloatingNewTaskButton
import com.milesxue.pixeldone.ui.todo.components.PixelAlarmIcon
import com.milesxue.pixeldone.ui.todo.components.PixelButton
import com.milesxue.pixeldone.ui.todo.components.PixelItemDeleteButton
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
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
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
import com.milesxue.pixeldone.domain.todo.*
import com.milesxue.pixeldone.reminder.ActiveXHighAlarm
import com.milesxue.pixeldone.reminder.XHighAlarmService
import com.milesxue.pixeldone.ui.theme.PixelDoneColors
internal const val CompletionSortDelayMillis = 2_000L
private const val MinuteMillis = 60_000L
private const val TodoHighlightFadeMillis = 180

private const val DeveloperCredit = "CODEX & XUE"
private val PixelReadTopBarContentHeight = 36.dp
private val PixelReadFrameInset = 8.dp
private val PixelDoneFooterHeight = 24.dp
private val DialogActionMinHeight = 44.dp
private const val InitialUpdateCheckDelayMillis = 600L
private const val UpdateStatusVisibleMillis = 3_000L
private const val MinPreviewScale = 1f
private const val MaxPreviewScale = 6f
private const val BytesPerMegabyte = 1024.0 * 1024.0

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
    val progress: AppUpdateDownloadProgress = AppUpdateDownloadProgress(),
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
            status == UpdateUiStatus.Offline ||
            status == UpdateUiStatus.Installing
}

internal fun formatUpdateDownloadMessage(
    version: String,
    progress: AppUpdateDownloadProgress = AppUpdateDownloadProgress(),
): String {
    val base = "downloading: v$version"
    progress.percent?.let { percent ->
        return "$base $percent%"
    }
    return if (progress.bytesDownloaded > 0L) {
        "$base ${formatDownloadedMegabytes(progress.bytesDownloaded)}"
    } else {
        base
    }
}

internal fun formatDownloadedMegabytes(bytes: Long): String {
    return String.format(
        Locale.US,
        "%.1fMB",
        bytes.coerceAtLeast(0L) / BytesPerMegabyte,
    )
}

internal fun shouldShowAvailableUpdateDialog(
    neverShowUpdateDialog: Boolean,
    hasActiveUpdateDownload: Boolean,
): Boolean = !neverShowUpdateDialog && !hasActiveUpdateDownload

internal fun shouldShowUpdatePromptSetting(neverShowUpdateDialog: Boolean): Boolean =
    !neverShowUpdateDialog

internal enum class UpdateInstallPermissionAction {
    OpenInstaller,
    RequestInstallPermission,
}

internal fun updateInstallPermissionAction(hasInstallUpdatePermission: Boolean): UpdateInstallPermissionAction {
    return if (hasInstallUpdatePermission) {
        UpdateInstallPermissionAction.OpenInstaller
    } else {
        UpdateInstallPermissionAction.RequestInstallPermission
    }
}

internal fun hasFullScreenIntentAccessForSdk(
    sdkInt: Int,
    canUseFullScreenIntent: Boolean,
): Boolean = sdkInt < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || canUseFullScreenIntent

internal enum class SystemReminderPermissionTarget {
    EXACT_ALARM,
    FULL_SCREEN_INTENT,
}

internal data class SystemReminderPermissionDecision(
    val target: SystemReminderPermissionTarget,
    val queueFullScreenFollowUp: Boolean,
)

internal fun systemReminderPermissionDecision(
    missingCapabilities: Set<ReminderCapability>,
): SystemReminderPermissionDecision? {
    return when {
        ReminderCapability.EXACT_ALARM_ACCESS in missingCapabilities -> {
            SystemReminderPermissionDecision(
                target = SystemReminderPermissionTarget.EXACT_ALARM,
                queueFullScreenFollowUp =
                    ReminderCapability.FULL_SCREEN_INTENT_ACCESS in missingCapabilities,
            )
        }
        ReminderCapability.FULL_SCREEN_INTENT_ACCESS in missingCapabilities -> {
            SystemReminderPermissionDecision(
                target = SystemReminderPermissionTarget.FULL_SCREEN_INTENT,
                queueFullScreenFollowUp = false,
            )
        }
        else -> null
    }
}

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
}

@Composable
internal fun PixelDoneApp() {
    val context = LocalContext.current
    val updateScope = rememberCoroutineScope()
    val appContainer = remember(context) { context.pixelDoneAppContainer() }
    val todoPreferences = remember(appContainer) { appContainer.todoPreferences }
    val todoRepository = remember(appContainer) { appContainer.todoRepository }
    val imageStore = remember(appContainer) { appContainer.todoImageStore }
    val updateService = remember(appContainer) { appContainer.updateService }
    val reminderScheduler = remember(appContainer) { appContainer.reminderScheduler }
    val activeXHighAlarmStore = remember(appContainer) { appContainer.activeXHighAlarmStore }
    val viewModelFactory = remember(appContainer) {
        PixelDoneViewModel.factory(
            todoRepository = todoRepository,
            reminderScheduler = reminderScheduler,
        )
    }
    val viewModel: PixelDoneViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val checklistState = uiState.checklistState
    var darkTheme by remember { mutableStateOf(todoPreferences.loadDarkTheme()) }
    var dockConfig by remember { mutableStateOf(todoPreferences.loadDockConfig()) }
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
    var neverShowUpdateDialog by remember {
        mutableStateOf(todoPreferences.loadNeverShowUpdateDialog())
    }
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
        val canUseFullScreenIntent =
            context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == true
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
            checklistEditorError = "This list is locked."
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

    fun latestUpdateMessage(): String =
        "latest: ${versionLabel(BuildConfig.VERSION_NAME)}"

    fun availableUpdateMessage(info: AppUpdateInfo): String =
        "get: ${versionLabel(BuildConfig.VERSION_NAME)} -> ${versionLabel(info.version)}"

    fun setNeverShowUpdateDialog(neverShow: Boolean) {
        neverShowUpdateDialog = neverShow
        todoPreferences.saveNeverShowUpdateDialog(neverShow)
    }

    fun setShowUpdateDialogs(showDialogs: Boolean) {
        setNeverShowUpdateDialog(!showDialogs)
    }

    fun setDarkThemePreference(enabled: Boolean) {
        darkTheme = enabled
        todoPreferences.saveDarkTheme(enabled)
    }

    fun setDockConfigPreference(config: DockConfig) {
        val normalizedConfig = config.normalized()
        dockConfig = normalizedConfig
        todoPreferences.saveDockConfig(normalizedConfig)
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
                    message = "allow install",
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
                            message = formatUpdateDownloadMessage(info.version),
                        )
                        val completion = updateService.awaitCompletion(result.download) { progress ->
                            if (activeUpdateDownload?.downloadId == result.download.downloadId) {
                                updateUiState = AppUpdateUiState(
                                    status = UpdateUiStatus.Downloading,
                                    info = info,
                                    message = formatUpdateDownloadMessage(info.version, progress),
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
                                message = "install: ${versionLabel(info.version)}",
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
                message = "update failed",
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
                                message = "install: ${versionLabel(pendingInstall.version)}",
                            )
                        } else {
                            AppUpdateUiState(
                                status = UpdateUiStatus.Offline,
                                message = "update failed",
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

    PixelDoneTheme(darkTheme = darkTheme) {
        PixelDoneScreen(
            checklists = checklistState.lists,
            selectedChecklistId = selectedChecklist.id,
            selectedChecklistName = selectedChecklist.name,
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
            showUpdateDialogs = shouldShowUpdatePromptSetting(neverShowUpdateDialog),
            onShowUpdateDialogsChange = ::setShowUpdateDialogs,
            currentVersion = BuildConfig.VERSION_NAME,
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
    showUpdateDialogs: Boolean,
    onShowUpdateDialogsChange: (Boolean) -> Unit,
    currentVersion: String,
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
    BoxWithConstraints(
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
                updateUiState = updateUiState,
                onUpdateClick = onUpdateClick,
                darkTheme = darkTheme,
                onDarkThemeChange = onDarkThemeChange,
                showUpdateDialogs = showUpdateDialogs,
                onShowUpdateDialogsChange = onShowUpdateDialogsChange,
                currentVersion = currentVersion,
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
        isSettingsSelected -> "OPTIONS"
        isTrashSelected -> "ITEMS ${activeCount + completedCount}"
        else -> "ACTIVE $activeCount  DONE $completedCount"
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
                text = "LONG PRESS \"+\" TO CREATE LIST",
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
                    text = if (alarm.displayCount == 1) "XHIGH ALARM RINGING" else "${alarm.displayCount} XHIGH ALARMS",
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
                    text = "SNOOZE 10",
                    onClick = onSnooze,
                    modifier = Modifier.weight(1f),
                    selected = true,
                )
                PixelButton(
                    text = "STOP",
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
        isSettingsChecklist(checklist) -> "APP OPTIONS"
        isTrashChecklist(checklist) -> "ITEMS ${checklist.items.size}"
        else -> "ACTIVE ${activeTodoCount(checklist)}  DONE ${completedTodoCount(checklist)}"
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
                text = checklist.name,
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
    updateUiState: AppUpdateUiState,
    onUpdateClick: () -> Unit,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    showUpdateDialogs: Boolean,
    onShowUpdateDialogsChange: (Boolean) -> Unit,
    currentVersion: String,
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
                showUpdateDialogs = showUpdateDialogs,
                onShowUpdateDialogsChange = onShowUpdateDialogsChange,
                currentVersion = currentVersion,
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
        }
    }
}

@Composable
private fun SettingsPanel(
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    showUpdateDialogs: Boolean,
    onShowUpdateDialogsChange: (Boolean) -> Unit,
    currentVersion: String,
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
        UpdateUiStatus.Available -> "UPDATE"
        UpdateUiStatus.Checking -> "CHECKING"
        UpdateUiStatus.Downloading -> "DOWNLOADING"
        UpdateUiStatus.Installing -> "INSTALL"
        else -> "CHECK"
    }
    val updateActionEnabled = updateUiState.status != UpdateUiStatus.Checking &&
        updateUiState.status != UpdateUiStatus.Downloading &&
        updateUiState.status != UpdateUiStatus.Installing

    PixelPanel(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            SettingsSection(title = "DISPLAY") {
                SettingsSegmentedRow(
                    title = "THEME",
                    value = darkTheme,
                    options = listOf(false, true),
                    label = { if (it) "DARK" else "LIGHT" },
                    onSelected = onDarkThemeChange,
                )
            }

            SettingsSection(title = "DOCK") {
                DockSettingsPreview(dockConfig = dockConfig)
                SettingsSegmentedRow(
                    title = "PLUS",
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

            SettingsSection(title = "UPDATES") {
                SettingsSegmentedRow(
                    title = "UPDATE POPUP",
                    value = showUpdateDialogs,
                    options = listOf(true, false),
                    label = { if (it) "ON" else "OFF" },
                    onSelected = onShowUpdateDialogsChange,
                )
                SettingsActionRow(
                    title = "CHECK UPDATE",
                    value = updateUiState.message ?: "current: v$currentVersion",
                    actionText = updateActionText,
                    onAction = onUpdateClick,
                    enabled = updateActionEnabled,
                )
            }

            SettingsSection(title = "PERMISSIONS") {
                SettingsPermissionRow(
                    title = "NOTIFICATIONS",
                    granted = permissionState.notificationsGranted,
                    onAction = onRequestNotificationPermission,
                )
                SettingsPermissionRow(
                    title = "EXACT ALARM",
                    granted = permissionState.exactAlarmGranted,
                    onAction = onRequestExactAlarmPermission,
                )
                SettingsPermissionRow(
                    title = "FULL SCREEN",
                    granted = permissionState.fullScreenIntentGranted,
                    onAction = onRequestFullScreenIntentPermission,
                )
                SettingsPermissionRow(
                    title = "INSTALL UPDATES",
                    granted = permissionState.installUpdatesGranted,
                    onAction = onRequestInstallUpdatesPermission,
                )
            }

            SettingsSection(title = "ABOUT") {
                SettingsAboutTextRow(
                    title = "APP",
                    value = "PixelDone",
                )
                SettingsAboutTextRow(
                    title = "MAKER",
                    value = "CODEX x XUE",
                    valueColor = colors.primary,
                )
                SettingsAboutTextRow(
                    title = "UPDATE PERMISSIONS",
                    value = "same package + signature",
                )
            }
        }
    }
}

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
            title = "PREVIEW",
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
            title = "FUNCTIONS",
            value = "${normalizedConfig.actions.size}/$MaxDockActions SELECTED",
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

private fun DockConfig.previewLabel(): String {
    val normalizedConfig = normalized()
    return "${normalizedConfig.plusPlacement.settingsLabel()}  ${normalizedConfig.actions.size}/$MaxDockActions"
}

private fun DockPlusPlacement.settingsLabel(): String = when (this) {
    DockPlusPlacement.CENTER -> "CENTER"
    DockPlusPlacement.LEFT_EDGE -> "LEFT"
    DockPlusPlacement.RIGHT_EDGE -> "RIGHT"
}

private fun DockAction.settingsTitle(): String = when (this) {
    DockAction.SORT -> "PRI/TIME"
    DockAction.DEADLINE -> "DDL"
    DockAction.HIDE_DONE -> "HIDE/UNHIDE"
    DockAction.DELETE_DONE -> "DELETE DONE"
    DockAction.BATCH_DELETE -> "DELETE MODE"
}

private fun DockAction.settingsValue(): String = when (this) {
    DockAction.SORT -> "sort mode"
    DockAction.DEADLINE -> "deadline countdown"
    DockAction.HIDE_DONE -> "done visibility"
    DockAction.DELETE_DONE -> "completed cleanup"
    DockAction.BATCH_DELETE -> "row quick delete"
}

private fun DockAction.contentDescription(): String = when (this) {
    DockAction.SORT -> "TOGGLE SORT"
    DockAction.DEADLINE -> "TOGGLE DEADLINE"
    DockAction.HIDE_DONE -> "TOGGLE DONE VISIBILITY"
    DockAction.DELETE_DONE -> "DELETE COMPLETED TASKS"
    DockAction.BATCH_DELETE -> "TOGGLE QUICK DELETE"
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
            selected = value == "NEEDS SETUP",
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
                contentDescription = if (granted) "PERMISSION GRANTED" else "PERMISSION NEEDS SETUP"
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
    label: (T) -> String,
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

private fun Boolean.permissionLabel(): String =
    if (this) "GRANTED" else "NEEDS SETUP"

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
                text = if (isEditing) "EDIT TASK" else "NEW TASK",
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary,
            )
            Text(
                text = if (isEditing) "CANCEL" else "CLOSE",
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
                text = "PRIORITY",
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
                text = "TIME / ALARM",
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
                text = "REPEAT",
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
                text = if (isEditing) "EDIT LIST" else "NEW LIST",
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary,
            )
            Text(
                text = "CANCEL",
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

private data class DockActionUiState(
    val active: Boolean,
    val enabled: Boolean = true,
)

private fun dockActionState(
    sortMode: SortMode,
    hideCompleted: Boolean,
    showDeadlineCountdown: Boolean,
    completedCount: Int,
    totalCount: Int,
    batchDeleteActive: Boolean,
): Map<DockAction, DockActionUiState> = mapOf(
    DockAction.SORT to DockActionUiState(active = sortMode == SortMode.TIME),
    DockAction.DEADLINE to DockActionUiState(active = showDeadlineCountdown),
    DockAction.HIDE_DONE to DockActionUiState(active = hideCompleted),
    DockAction.DELETE_DONE to DockActionUiState(
        active = false,
        enabled = completedCount > 0,
    ),
    DockAction.BATCH_DELETE to DockActionUiState(
        active = batchDeleteActive,
        enabled = totalCount > 0,
    ),
)

private fun previewDockActionState(): Map<DockAction, DockActionUiState> = mapOf(
    DockAction.SORT to DockActionUiState(active = false),
    DockAction.DEADLINE to DockActionUiState(active = false),
    DockAction.HIDE_DONE to DockActionUiState(active = false),
    DockAction.DELETE_DONE to DockActionUiState(active = false),
    DockAction.BATCH_DELETE to DockActionUiState(active = false),
)

@Composable
private fun BottomActionDock(
    config: DockConfig,
    actionState: Map<DockAction, DockActionUiState>,
    onActionClick: (DockAction) -> Unit,
    onAddClick: () -> Unit,
    onAddLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val normalizedConfig = config.normalized()
    if (
        normalizedConfig.plusPlacement == DockPlusPlacement.CENTER &&
        normalizedConfig.actions.size <= 4
    ) {
        val sides = centerDockActionSides(normalizedConfig.actions)
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(64.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.BottomEnd,
            ) {
                DockActionGroup(
                    actions = sides.left,
                    actionState = actionState,
                    onActionClick = onActionClick,
                    modifier = Modifier.padding(end = 18.dp),
                )
            }
            FloatingNewTaskButton(
                onClick = onAddClick,
                onLongClick = onAddLongClick,
                modifier = Modifier.width(56.dp),
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.BottomStart,
            ) {
                DockActionGroup(
                    actions = sides.right,
                    actionState = actionState,
                    onActionClick = onActionClick,
                    modifier = Modifier.padding(start = 18.dp),
                )
            }
        }
        return
    }

    val items = orderedDockItems(normalizedConfig)
    val spacing = if (normalizedConfig.actions.size >= 5) 5.dp else 18.dp
    val alignment = when (normalizedConfig.plusPlacement) {
        DockPlusPlacement.CENTER -> Alignment.CenterHorizontally
        DockPlusPlacement.LEFT_EDGE -> Alignment.Start
        DockPlusPlacement.RIGHT_EDGE -> Alignment.End
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
        horizontalArrangement = Arrangement.spacedBy(spacing, alignment),
        verticalAlignment = Alignment.Bottom,
    ) {
        items.forEach { item ->
            when (item) {
                DockItem.Add -> {
                    FloatingNewTaskButton(
                        onClick = onAddClick,
                        onLongClick = onAddLongClick,
                    )
                }
                is DockItem.Action -> {
                    val state = actionState[item.action] ?: DockActionUiState(active = false)
                    DockIconButton(
                        action = item.action,
                        active = state.active,
                        enabled = state.enabled,
                        onClick = { onActionClick(item.action) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DockActionGroup(
    actions: List<DockAction>,
    actionState: Map<DockAction, DockActionUiState>,
    onActionClick: (DockAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        actions.forEach { action ->
            val state = actionState[action] ?: DockActionUiState(active = false)
            DockIconButton(
                action = action,
                active = state.active,
                enabled = state.enabled,
                onClick = { onActionClick(action) },
            )
        }
    }
}

@Composable
private fun DockIconButton(
    action: DockAction,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PixelDoneColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val iconColor = when {
        !enabled -> colors.disabledText
        active -> colors.primary
        else -> colors.textPrimary
    }
    val borderColor = when {
        !enabled -> colors.borderWeak
        active -> colors.primary
        else -> colors.textPrimary
    }
    val backgroundColor = when {
        !enabled -> colors.disabledSurface
        pressed -> colors.surfaceRaised
        else -> colors.selectedSurface
    }

    Box(
        modifier = modifier
            .size(44.dp)
            .background(backgroundColor, RectangleShape)
            .border(2.dp, borderColor, RectangleShape)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .semantics { contentDescription = action.contentDescription() },
        contentAlignment = Alignment.Center,
    ) {
        DockActionIcon(action = action, color = iconColor)
    }
}

@Composable
private fun DockActionIcon(
    action: DockAction,
    color: Color,
    modifier: Modifier = Modifier.size(22.dp),
) {
    Canvas(modifier = modifier) {
        val iconSize = min(size.width, size.height)
        val left = (size.width - iconSize) / 2f
        val top = (size.height - iconSize) / 2f
        fun x(value: Float): Float = left + iconSize * value / 22f
        fun y(value: Float): Float = top + iconSize * value / 22f
        fun offset(rawX: Float, rawY: Float): Offset = Offset(x(rawX), y(rawY))
        val strokeWidth = iconSize * 2.2f / 22f
        val thinStrokeWidth = iconSize * 1.6f / 22f
        val heavyStrokeWidth = iconSize * 2.8f / 22f
        val iconStroke = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        fun line(
            startX: Float,
            startY: Float,
            endX: Float,
            endY: Float,
            width: Float = strokeWidth,
        ) {
            drawLine(
                color = color,
                start = offset(startX, startY),
                end = offset(endX, endY),
                strokeWidth = width,
                cap = StrokeCap.Round,
            )
        }
        when (action) {
            DockAction.SORT -> {
                line(5f, 2.5f, 5f, 17.2f, heavyStrokeWidth)
                line(1.8f, 13.8f, 5f, 17.2f, heavyStrokeWidth)
                line(8.2f, 13.8f, 5f, 17.2f, heavyStrokeWidth)
                line(11f, 4f, 20f, 4f, strokeWidth)
                line(11f, 8.5f, 18f, 8.5f, strokeWidth)
                line(11f, 13f, 16f, 13f, strokeWidth)
                line(11f, 17.5f, 13f, 17.5f, strokeWidth)
            }
            DockAction.DEADLINE -> {
                val flamePath = Path().apply {
                    moveTo(x(5f), y(12.5f))
                    cubicTo(x(2.8f), y(9.2f), x(5.2f), y(5.5f), x(7.4f), y(5.1f))
                    cubicTo(x(7.3f), y(7.2f), x(9.6f), y(7.8f), x(10.3f), y(5.6f))
                    cubicTo(x(11.2f), y(3.1f), x(13.7f), y(2.4f), x(15.5f), y(2.8f))
                    cubicTo(x(14.3f), y(5.4f), x(14.9f), y(7.5f), x(16.8f), y(8.6f))
                    cubicTo(x(18.8f), y(9.8f), x(18.4f), y(12.4f), x(16.7f), y(14.1f))
                }
                drawPath(color = color, path = flamePath, style = iconStroke)
                drawCircle(
                    color = color,
                    radius = iconSize * 5.6f / 22f,
                    center = offset(11f, 13.7f),
                    style = iconStroke,
                )
                line(11f, 13.7f, 11f, 9.9f, thinStrokeWidth)
                line(11f, 13.7f, 14.2f, 16.1f, thinStrokeWidth)
            }
            DockAction.HIDE_DONE -> {
                val eyePath = Path().apply {
                    moveTo(x(3f), y(11f))
                    quadraticTo(x(7.1f), y(5.8f), x(11f), y(5.8f))
                    quadraticTo(x(15f), y(5.8f), x(19f), y(11f))
                    quadraticTo(x(15f), y(16.2f), x(11f), y(16.2f))
                    quadraticTo(x(7.1f), y(16.2f), x(3f), y(11f))
                }
                drawPath(color = color, path = eyePath, style = iconStroke)
                drawCircle(
                    color = color,
                    radius = iconSize * 2.7f / 22f,
                    center = offset(11f, 11f),
                    style = Stroke(
                        width = thinStrokeWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
                line(5.2f, 3.8f, 16.8f, 18.2f, heavyStrokeWidth)
            }
            DockAction.DELETE_DONE -> {
                line(5f, 6f, 17f, 6f, heavyStrokeWidth)
                line(9f, 3f, 13f, 3f, heavyStrokeWidth)
                line(4.3f, 8f, 5.1f, 18.3f, heavyStrokeWidth)
                line(17.7f, 8f, 16.9f, 18.3f, heavyStrokeWidth)
                line(5.1f, 18.3f, 16.9f, 18.3f, heavyStrokeWidth)
                line(8.3f, 9f, 8.3f, 16f, strokeWidth)
                line(11f, 9f, 11f, 16f, strokeWidth)
                line(13.7f, 9f, 13.7f, 16f, strokeWidth)
            }
            DockAction.BATCH_DELETE -> {
                line(4f, 4.5f, 11.5f, 4.5f, heavyStrokeWidth)
                line(6.7f, 2.5f, 9.3f, 2.5f, heavyStrokeWidth)
                line(4.7f, 6.5f, 5.3f, 14.8f, strokeWidth)
                line(10.8f, 6.5f, 10.2f, 14.8f, strokeWidth)
                line(5.3f, 14.8f, 10.2f, 14.8f, strokeWidth)
                line(7f, 8f, 7f, 13f, thinStrokeWidth)
                line(9f, 8f, 9f, 13f, thinStrokeWidth)
                line(14f, 5f, 20f, 5f, strokeWidth)
                line(14f, 10f, 19f, 10f, strokeWidth)
                line(14f, 15f, 18f, 15f, strokeWidth)
                line(18f, 13f, 20f, 15f, heavyStrokeWidth)
                line(20f, 15f, 18f, 17f, heavyStrokeWidth)
            }
        }
    }
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
                text = "$selectedCount SELECTED",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            PixelButton(
                text = "MOVE",
                onClick = onMove,
                enabled = selectedCount > 0,
                primary = selectedCount > 0,
                modifier = Modifier.width(96.dp),
            )
            PixelButton(
                text = "CANCEL",
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
            PanelLabel("TARGET LIST")
            if (targetChecklists.isEmpty()) {
                Text(
                    text = "NO TARGET LIST",
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
                text = "CANCEL",
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
                    text = "FROM $sourceName",
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Update available",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "PixelDone can update from v$currentVersion to v${info.version}.",
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
                            stateDescription = if (neverShowUpdateDialog) "enabled" else "disabled"
                        }
                        .padding(vertical = 2.dp),
                    text = "DO NOT SHOW AGAIN",
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
                    text = "LATER",
                    onClick = onDismiss,
                )
                PixelButton(
                    text = "UPDATE",
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
    val message = updateUiState.message ?: formatUpdateDownloadMessage(download.version)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Downloading update",
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
                    text = "You can close this dialog. The update will keep downloading silently.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        },
        confirmButton = {
            DialogTextActionButton(
                text = "CLOSE",
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
                    text = "Task image",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                DialogTextActionButton(
                    text = "CLOSE",
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
                                text = "Loading image...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                            )
                        }
                        TodoImagePreviewLoadState.Unavailable -> {
                            Text(
                                text = "Image unavailable.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
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
            DialogActionRow {
                PixelButton(
                    text = "CHANGE",
                    onClick = { onChange(item) },
                    clayOutline = true,
                )
                PixelButton(
                    text = "REMOVE",
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
                    text = "CLOSE",
                    onClick = onDismiss,
                )
                PixelButton(
                    text = "DELETE",
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
        withStyle(SpanStyle(color = dueDateTimeColor)) {
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
            showUpdateDialogs = true,
            onShowUpdateDialogsChange = {},
            currentVersion = "2.9.0",
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
            showUpdateDialogs = true,
            onShowUpdateDialogsChange = {},
            currentVersion = "2.7.0",
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
