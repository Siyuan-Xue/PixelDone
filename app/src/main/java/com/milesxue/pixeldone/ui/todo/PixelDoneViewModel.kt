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
import com.milesxue.pixeldone.data.todo.TodoRepository
import com.milesxue.pixeldone.domain.todo.ReminderCapability
import com.milesxue.pixeldone.domain.todo.TodoChecklistState
import com.milesxue.pixeldone.domain.todo.normalTodos
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
        ),
    )
    val uiState: StateFlow<PixelDoneUiState> = _uiState.asStateFlow()

    init {
        unregisterTodoObserver = todoRepository.observeTodoState {
            _uiState.value = _uiState.value.copy(checklistState = todoRepository.state.value)
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
            syncCoordinator.status.collect { status ->
                _uiState.value = _uiState.value.copy(syncStatus = status)
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
                syncCoordinator.requestSync()
            }
            is PixelDoneAction.SetDockConfig -> {
                settingsStore.saveDockConfig(action.config)
                refreshSettingsState()
                syncCoordinator.requestSync()
            }
            is PixelDoneAction.SetShowUpdateDialogs -> {
                settingsStore.saveNeverShowUpdateDialog(!action.showDialogs)
                refreshSettingsState()
                syncCoordinator.requestSync()
            }
            is PixelDoneAction.SetAuthEmail -> updateAuthInput { it.copy(email = action.email, error = null, message = null) }
            is PixelDoneAction.SetAuthPassword -> updateAuthInput { it.copy(password = action.password, error = null, message = null) }
            PixelDoneAction.SignIn -> signIn()
            PixelDoneAction.CancelSignIn -> cancelSignIn()
            PixelDoneAction.SignOut -> signOut()
            PixelDoneAction.SyncNow -> syncNow()
            PixelDoneAction.DismissAuthMessage -> updateAuthInput { it.copy(message = null, error = null) }
            PixelDoneAction.SystemActionConsumed -> {
                _uiState.value = _uiState.value.copy(pendingSystemAction = null)
            }
        }
    }

    fun replaceChecklistState(updatedState: TodoChecklistState): Set<ReminderCapability> {
        val previousTodos = normalTodos(_uiState.value.checklistState)
        val updatedTodos = normalTodos(updatedState)
        todoRepository.saveTodoState(updatedState)
        val missingCapabilities = reminderScheduler.sync(previousTodos, updatedTodos)
        _uiState.value = _uiState.value.copy(checklistState = updatedState)
        syncCoordinator.requestSync()
        return missingCapabilities
    }

    private fun signIn() {
        val input = _uiState.value.authInput
        val email = input.email.trim()
        if (email.isBlank() || input.password.isBlank()) {
            updateAuthInput { it.copy(error = "Email and password are required.", message = null) }
            return
        }
        viewModelScope.launch {
            updateAuthInput { it.copy(busy = true, error = null, message = null) }
            try {
                authSessionRepository.signIn(email, input.password)
                updateAuthInput { it.copy(password = "", busy = false, error = null, message = "Signed in.") }
                syncCoordinator.requestSync()
            } catch (error: Exception) {
                updateAuthInput {
                    it.copy(
                        busy = false,
                        error = error.message ?: "Sign in failed.",
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

    private fun syncNow() {
        viewModelScope.launch {
            updateAuthInput { it.copy(error = null, message = null) }
            val status = syncCoordinator.syncNow()
            updateAuthInput { it.copy(message = status.settingsMessage()) }
        }
    }

    private fun refreshSettingsState() {
        _uiState.value = _uiState.value.copy(settings = settingsStore.loadSettings())
    }

    private fun updateAuthInput(transform: (AuthInputState) -> AuthInputState) {
        _uiState.value = _uiState.value.copy(authInput = transform(_uiState.value.authInput))
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

private fun com.milesxue.pixeldone.domain.sync.SyncCoordinatorStatus.settingsMessage(): String = when (this) {
    com.milesxue.pixeldone.domain.sync.SyncCoordinatorStatus.LOCAL_ONLY -> "Local only."
    com.milesxue.pixeldone.domain.sync.SyncCoordinatorStatus.NOT_CONFIGURED -> "Cloud sync needs setup."
    com.milesxue.pixeldone.domain.sync.SyncCoordinatorStatus.SIGNED_OUT -> "Sign in first."
    com.milesxue.pixeldone.domain.sync.SyncCoordinatorStatus.IDLE -> "Ready."
    com.milesxue.pixeldone.domain.sync.SyncCoordinatorStatus.SYNCING -> "Syncing."
    com.milesxue.pixeldone.domain.sync.SyncCoordinatorStatus.SYNCED -> "Synced."
    com.milesxue.pixeldone.domain.sync.SyncCoordinatorStatus.ERROR -> "Sync failed."
}