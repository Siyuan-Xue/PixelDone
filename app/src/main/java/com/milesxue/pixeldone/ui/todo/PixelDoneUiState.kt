package com.milesxue.pixeldone.ui.todo

import com.milesxue.pixeldone.domain.sync.SyncCoordinatorStatus
import com.milesxue.pixeldone.domain.todo.DockConfig
import com.milesxue.pixeldone.domain.todo.PixelDoneSettings
import com.milesxue.pixeldone.domain.todo.SortMode
import com.milesxue.pixeldone.domain.todo.TodoChecklistState

/**
 * Immutable state rendered by the PixelDone screen.
 *
 * This data class describes facts the screen needs. It does not read storage or call Android APIs.
 */
data class PixelDoneUiState(
    val checklistState: TodoChecklistState,
    val settings: PixelDoneSettings = PixelDoneSettings(),
    val syncStatus: SyncCoordinatorStatus = SyncCoordinatorStatus.LOCAL_ONLY,
    val sortMode: SortMode = SortMode.PRIORITY,
    val hideCompleted: Boolean = false,
    val showDeadlineCountdown: Boolean = false,
    val pendingSystemAction: PendingSystemAction? = null,
)

/**
 * One-shot system work that must be consumed once by the UI boundary.
 *
 * Permission requests, settings screens, and install prompts should not run repeatedly from plain state.
 */
sealed interface PendingSystemAction {
    data object RequestNotificationPermission : PendingSystemAction
    data class OpenExactAlarmSettings(val todoId: String) : PendingSystemAction
    data class OpenFullScreenIntentSettings(val todoId: String) : PendingSystemAction
    data class OpenUpdateInstaller(val downloadId: Long) : PendingSystemAction
}

sealed interface PixelDoneAction {
    data class ReplaceChecklistState(val state: TodoChecklistState) : PixelDoneAction
    data class SetSortMode(val sortMode: SortMode) : PixelDoneAction
    data class SetHideCompleted(val hideCompleted: Boolean) : PixelDoneAction
    data class SetDeadlineCountdownVisible(val visible: Boolean) : PixelDoneAction
    data class SetDarkTheme(val enabled: Boolean) : PixelDoneAction
    data class SetDockConfig(val config: DockConfig) : PixelDoneAction
    data class SetShowUpdateDialogs(val showDialogs: Boolean) : PixelDoneAction
    data object SystemActionConsumed : PixelDoneAction
}
