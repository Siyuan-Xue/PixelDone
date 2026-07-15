package com.milesxue.pixeldone.ui.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.milesxue.pixeldone.data.settings.InMemoryPixelDoneSettingsStore
import com.milesxue.pixeldone.data.settings.PixelDoneSettingsStore
import com.milesxue.pixeldone.data.sync.AuthSessionRepository
import com.milesxue.pixeldone.data.sync.LocalOnlyAuthSessionRepository
import com.milesxue.pixeldone.data.sync.LocalOnlySyncCoordinator
import com.milesxue.pixeldone.data.sync.SyncCoordinator
import com.milesxue.pixeldone.domain.sync.SyncCoordinatorStatus
import com.milesxue.pixeldone.data.todo.TodoRepository
import com.milesxue.pixeldone.domain.todo.ReminderCapability
import com.milesxue.pixeldone.domain.todo.TodoChecklistState
import com.milesxue.pixeldone.domain.todo.normalTodos
import com.milesxue.pixeldone.domain.todo.mergeUserTodoStateChange
import com.milesxue.pixeldone.reminder.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Screen-level state holder.
 *
 * The ViewModel belongs to the UI layer but contains no Compose code. It translates
 * user intent into repository updates and reminder scheduling, then exposes immutable state.
 */
class PixelDoneViewModel(
    private val todoRepository: TodoRepository,
    private val reminderScheduler: ReminderScheduler,
    private val settingsStore: PixelDoneSettingsStore = InMemoryPixelDoneSettingsStore(),
    private val authSessionRepository: AuthSessionRepository = LocalOnlyAuthSessionRepository(),
    private val syncCoordinator: SyncCoordinator = LocalOnlySyncCoordinator(),
) : ViewModel() {
    private val unregisterTodoObserver: () -> Unit
    private val unregisterSettingsObserver: () -> Unit
    private val _uiState = MutableStateFlow(
        PixelDoneUiState(
            checklistState = todoRepository.loadTodoState(),
            settings = settingsStore.loadSettings(),
            authSession = authSessionRepository.session.value,
            syncStatus = syncCoordinator.status.value,
            syncRunState = syncCoordinator.runState.value,
        ),
    )
    val uiState: StateFlow<PixelDoneUiState> = _uiState.asStateFlow()
    private var lastPresentedConflictSignature: Int? = null
    private var localMutationInProgress = false

    init {
        unregisterTodoObserver = todoRepository.observeTodoState {
            val previousState = _uiState.value.checklistState
            val currentState = todoRepository.state.value
            if (!localMutationInProgress && previousState.lists != currentState.lists) {
                reminderScheduler.sync(normalTodos(previousState), normalTodos(currentState))
            }
            _uiState.value = _uiState.value.copy(checklistState = currentState)
        }
        unregisterSettingsObserver = settingsStore.observeSettings {
            _uiState.value = _uiState.value.copy(settings = settingsStore.loadSettings())
        }
        viewModelScope.launch {
            authSessionRepository.session.collect { session ->
                _uiState.value = _uiState.value.copy(
                    authSession = session,
                    authInput = if (session.signedIn) {
                        _uiState.value.authInput.copy(password = "", busy = false, error = null)
                    } else {
                        _uiState.value.authInput
                    },
                )
            }
        }
        viewModelScope.launch {
            syncCoordinator.runState.collect { runState ->
                val conflicts = if (runState.status == SyncCoordinatorStatus.SYNCING) {
                    _uiState.value.syncConflicts
                } else {
                    syncCoordinator.loadConflicts()
                }
                val displayedRunState = runState.copy(conflictCount = conflicts.size)
                val signature = conflicts.takeIf { it.isNotEmpty() }?.hashCode()
                val isNewBatch = signature != null && signature != lastPresentedConflictSignature
                if (conflicts.isEmpty()) lastPresentedConflictSignature = null
                _uiState.value = _uiState.value.copy(
                    syncStatus = runState.status,
                    syncRunState = displayedRunState,
                    syncConflicts = conflicts,
                    conflictDialogVisible = when {
                        conflicts.isEmpty() -> false
                        isNewBatch -> true
                        else -> _uiState.value.conflictDialogVisible
                    },
                    resolvingConflictKey = if (isNewBatch) null else _uiState.value.resolvingConflictKey,
                )
                if (isNewBatch) lastPresentedConflictSignature = signature
            }
        }
    }

    fun onAction(action: PixelDoneAction) {
        when (action) {
            is PixelDoneAction.ReplaceChecklistState -> replaceChecklistState(action.state)
            is PixelDoneAction.SetSortMode -> _uiState.value = _uiState.value.copy(sortMode = action.sortMode)
            is PixelDoneAction.SetHideCompleted -> {
                _uiState.value = _uiState.value.copy(hideCompleted = action.hideCompleted)
            }
            is PixelDoneAction.SetDeadlineCountdownVisible -> {
                _uiState.value = _uiState.value.copy(showDeadlineCountdown = action.visible)
            }
            is PixelDoneAction.SetDarkTheme -> {
                settingsStore.saveDarkTheme(action.enabled)
                refreshSettingsState()
            }
            is PixelDoneAction.SetDockConfig -> {
                settingsStore.saveDockConfig(action.config)
                refreshSettingsState()
            }
            is PixelDoneAction.SetLanguage -> {
                settingsStore.saveLanguage(action.language)
                refreshSettingsState()
                syncCoordinator.requestSync()
            }
            is PixelDoneAction.SetShowUpdateDialogs -> {
                settingsStore.saveNeverShowUpdateDialog(!action.showDialogs)
                refreshSettingsState()
            }
            is PixelDoneAction.SetAuthEmail -> updateAuthInput { it.copy(email = action.email, error = null, message = null) }
            is PixelDoneAction.SetAuthPassword -> updateAuthInput { it.copy(password = action.password, error = null, message = null) }
            is PixelDoneAction.SetCloudAuthMode -> updateAuthInput { it.copy(mode = action.mode, error = null, message = null) }
            PixelDoneAction.SignIn -> signIn()
            PixelDoneAction.SignUp -> signUp()
            PixelDoneAction.CancelSignIn -> cancelSignIn()
            PixelDoneAction.SignOut -> signOut()
            PixelDoneAction.SyncNow -> syncNow()
            PixelDoneAction.OpenConflictDialog -> openConflictDialog()
            PixelDoneAction.DismissConflictDialog -> dismissConflictDialog()
            is PixelDoneAction.ResolveConflict -> resolveConflict(action)
            is PixelDoneAction.ChangePassword -> changePassword(action)
            PixelDoneAction.DismissPasswordChangeFeedback -> {
                updatePasswordChangeState { it.copy(message = null, error = null) }
            }
            PixelDoneAction.DismissAuthMessage -> updateAuthInput { it.copy(message = null, error = null) }
            PixelDoneAction.SystemActionConsumed -> {
                _uiState.value = _uiState.value.copy(pendingSystemAction = null)
            }
        }
    }

    fun replaceChecklistState(updatedState: TodoChecklistState): Set<ReminderCapability> {
        val uiBefore = _uiState.value.checklistState
        var committedBefore = uiBefore
        val committed = try {
            localMutationInProgress = true
            todoRepository.updateTodoState { latest ->
                committedBefore = latest
                mergeUserTodoStateChange(uiBefore, updatedState, latest)
            }
        } finally {
            localMutationInProgress = false
        }
        val missingCapabilities = reminderScheduler.sync(normalTodos(committedBefore), normalTodos(committed))
        _uiState.value = _uiState.value.copy(checklistState = committed)
        if (committedBefore.lists != committed.lists) {
            syncCoordinator.requestSync()
        }
        return missingCapabilities
    }

    private fun signIn() {
        submitAuth(
            successMessage = "Signed in.",
            defaultError = "Sign in failed.",
        ) { email, password ->
            authSessionRepository.signIn(email, password)
        }
    }

    private fun signUp() {
        submitAuth(
            successMessage = "Signed up.",
            defaultError = "Sign up failed.",
        ) { email, password ->
            authSessionRepository.signUp(email, password)
        }
    }

    private fun submitAuth(
        successMessage: String,
        defaultError: String,
        submit: suspend (email: String, password: String) -> Any?,
    ) {
        val input = _uiState.value.authInput
        val email = input.email.trim()
        if (email.isBlank() || input.password.isBlank()) {
            updateAuthInput { it.copy(error = "Email and password are required.", message = null) }
            return
        }
        viewModelScope.launch {
            updateAuthInput { it.copy(busy = true, error = null, message = null) }
            try {
                submit(email, input.password)
                updateAuthInput { it.copy(password = "", busy = false, error = null, message = successMessage) }
            } catch (error: Exception) {
                updateAuthInput {
                    it.copy(
                        busy = false,
                        error = error.message ?: defaultError,
                        message = null,
                    )
                }
            }
        }
    }

    private fun cancelSignIn() {
        updateAuthInput { it.copy(password = "", busy = false, message = null, error = null) }
    }

    private fun signOut() {
        viewModelScope.launch {
            updateAuthInput { it.copy(busy = true, error = null, message = null) }
            try {
                authSessionRepository.signOut()
                updateAuthInput { it.copy(password = "", busy = false, message = "Signed out.", error = null) }
            } catch (error: Exception) {
                updateAuthInput { it.copy(busy = false, error = error.message ?: "Sign out failed.") }
            }
        }
    }

    private fun changePassword(action: PixelDoneAction.ChangePassword) {
        if (action.currentPassword.isBlank() || action.newPassword.isBlank() || action.confirmation.isBlank()) {
            updatePasswordChangeState { it.copy(error = "All password fields are required.", message = null) }
            return
        }
        if (action.newPassword != action.confirmation) {
            updatePasswordChangeState { it.copy(error = "New passwords do not match.", message = null) }
            return
        }
        if (action.currentPassword == action.newPassword) {
            updatePasswordChangeState { it.copy(error = "Choose a different new password.", message = null) }
            return
        }
        viewModelScope.launch {
            updatePasswordChangeState { it.copy(busy = true, error = null, message = null) }
            try {
                val result = authSessionRepository.changePassword(action.currentPassword, action.newPassword)
                updatePasswordChangeState {
                    it.copy(
                        busy = false,
                        error = null,
                        message = if (result.globalLogoutCompleted) {
                            "Password changed. Sign in again."
                        } else {
                            "Password changed. This device signed out; some other sessions may still be active."
                        },
                    )
                }
            } catch (error: Exception) {
                updatePasswordChangeState {
                    it.copy(busy = false, error = error.message ?: "Password change failed.", message = null)
                }
            }
        }
    }

    private fun syncNow() {
        viewModelScope.launch {
            syncCoordinator.syncNow()
        }
    }

    private fun openConflictDialog() {
        viewModelScope.launch {
            val conflicts = syncCoordinator.loadConflicts()
            _uiState.value = _uiState.value.copy(
                syncConflicts = conflicts,
                syncRunState = _uiState.value.syncRunState.copy(conflictCount = conflicts.size),
                conflictDialogVisible = conflicts.isNotEmpty(),
                resolvingConflictKey = null,
            )
            lastPresentedConflictSignature = conflicts.hashCode()
        }
    }

    private fun dismissConflictDialog() {
        _uiState.value = _uiState.value.copy(
            conflictDialogVisible = false,
            resolvingConflictKey = null,
        )
    }

    private fun resolveConflict(action: PixelDoneAction.ResolveConflict) {
        val conflictKey = action.recordType + ":" + action.localId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(resolvingConflictKey = conflictKey)
            val status = syncCoordinator.resolveConflict(
                recordType = action.recordType,
                localId = action.localId,
                choice = action.choice,
            )
            val conflicts = syncCoordinator.loadConflicts()
            lastPresentedConflictSignature = conflicts.takeIf { it.isNotEmpty() }?.hashCode()
            _uiState.value = _uiState.value.copy(
                syncStatus = status,
                syncRunState = syncCoordinator.runState.value,
                syncConflicts = conflicts,
                conflictDialogVisible = conflicts.isNotEmpty(),
                resolvingConflictKey = null,
            )
        }
    }

    private fun refreshSettingsState() {
        _uiState.value = _uiState.value.copy(settings = settingsStore.loadSettings())
    }

    private fun updateAuthInput(transform: (AuthInputState) -> AuthInputState) {
        _uiState.value = _uiState.value.copy(authInput = transform(_uiState.value.authInput))
    }

    private fun updatePasswordChangeState(transform: (PasswordChangeState) -> PasswordChangeState) {
        _uiState.value = _uiState.value.copy(
            passwordChangeState = transform(_uiState.value.passwordChangeState),
        )
    }

    override fun onCleared() {
        unregisterTodoObserver()
        unregisterSettingsObserver()
        super.onCleared()
    }

    companion object {
        fun factory(
            todoRepository: TodoRepository,
            reminderScheduler: ReminderScheduler,
            settingsStore: PixelDoneSettingsStore,
            authSessionRepository: AuthSessionRepository,
            syncCoordinator: SyncCoordinator,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(PixelDoneViewModel::class.java)) {
                    return PixelDoneViewModel(
                        todoRepository = todoRepository,
                        reminderScheduler = reminderScheduler,
                        settingsStore = settingsStore,
                        authSessionRepository = authSessionRepository,
                        syncCoordinator = syncCoordinator,
                    ) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    }
}
