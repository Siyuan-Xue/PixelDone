package com.milesxue.pixeldone.ui.todo

import com.milesxue.pixeldone.domain.sync.AuthSession
import com.milesxue.pixeldone.domain.sync.ConflictResolutionChoice
import com.milesxue.pixeldone.domain.sync.SyncConflictEntry
import com.milesxue.pixeldone.domain.sync.SyncCoordinatorStatus
import com.milesxue.pixeldone.domain.sync.SyncRunState
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
    val authSession: AuthSession = AuthSession(),
    val authInput: AuthInputState = AuthInputState(),
    val syncStatus: SyncCoordinatorStatus = SyncCoordinatorStatus.LOCAL_ONLY,
    val syncRunState: SyncRunState = SyncRunState(),
    val sortMode: SortMode = SortMode.PRIORITY,
    val hideCompleted: Boolean = false,
    val showDeadlineCountdown: Boolean = false,
    val syncConflicts: List<SyncConflictEntry> = emptyList(),
    val conflictDialogVisible: Boolean = false,
    val resolvingConflictKey: String? = null,
    val pendingSystemAction: PendingSystemAction? = null,
)

data class AuthInputState(
    val email: String = "",
    val password: String = "",
    val mode: CloudAuthMode = CloudAuthMode.SIGN_IN,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

enum class CloudAuthMode {
    SIGN_IN,
    SIGN_UP,
}

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
    data class SetAuthEmail(val email: String) : PixelDoneAction
    data class SetAuthPassword(val password: String) : PixelDoneAction
    data class SetCloudAuthMode(val mode: CloudAuthMode) : PixelDoneAction
    data object SignIn : PixelDoneAction
    data object SignUp : PixelDoneAction
    data object CancelSignIn : PixelDoneAction
    data object SignOut : PixelDoneAction
    data object SyncNow : PixelDoneAction
    data object OpenConflictDialog : PixelDoneAction
    data object DismissConflictDialog : PixelDoneAction
    data class ResolveConflict(
        val recordType: String,
        val localId: String,
        val choice: ConflictResolutionChoice,
    ) : PixelDoneAction
    data object ResetPassword : PixelDoneAction
    data object DismissAuthMessage : PixelDoneAction
    data object SystemActionConsumed : PixelDoneAction
}
