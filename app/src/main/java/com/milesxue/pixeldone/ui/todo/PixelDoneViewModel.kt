package com.milesxue.pixeldone.ui.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.milesxue.pixeldone.data.settings.InMemoryPixelDoneSettingsStore
import com.milesxue.pixeldone.data.settings.PixelDoneSettingsStore
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
    private val syncCoordinator: SyncCoordinator = LocalOnlySyncCoordinator(),
) : ViewModel() {
    private val unregisterTodoObserver: () -> Unit
    private val unregisterSettingsObserver: () -> Unit
    private val _uiState = MutableStateFlow(
        PixelDoneUiState(
            checklistState = todoRepository.loadTodoState(),
            settings = settingsStore.loadSettings(),
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
            is PixelDoneAction.SetShowUpdateDialogs -> {
                settingsStore.saveNeverShowUpdateDialog(!action.showDialogs)
                refreshSettingsState()
            }
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
        return missingCapabilities
    }

    private fun refreshSettingsState() {
        _uiState.value = _uiState.value.copy(settings = settingsStore.loadSettings())
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
            syncCoordinator: SyncCoordinator,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(PixelDoneViewModel::class.java)) {
                    return PixelDoneViewModel(
                        todoRepository = todoRepository,
                        reminderScheduler = reminderScheduler,
                        settingsStore = settingsStore,
                        syncCoordinator = syncCoordinator,
                    ) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    }
}
