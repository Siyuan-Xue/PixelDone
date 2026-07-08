package com.milesxue.pixeldone

import com.milesxue.pixeldone.data.settings.InMemoryPixelDoneSettingsStore
import com.milesxue.pixeldone.data.sync.AuthSessionRepository
import com.milesxue.pixeldone.data.sync.SyncCoordinator
import com.milesxue.pixeldone.data.todo.TodoRepository
import com.milesxue.pixeldone.domain.sync.AuthSession
import com.milesxue.pixeldone.domain.sync.SyncCoordinatorStatus
import com.milesxue.pixeldone.domain.sync.SyncRunState
import com.milesxue.pixeldone.domain.todo.DockAction
import com.milesxue.pixeldone.domain.todo.DockConfig
import com.milesxue.pixeldone.domain.todo.DockPlusPlacement
import com.milesxue.pixeldone.domain.todo.ReminderCapability
import com.milesxue.pixeldone.domain.todo.SettingsChecklistId
import com.milesxue.pixeldone.domain.todo.SortMode
import com.milesxue.pixeldone.domain.todo.TodoItem
import com.milesxue.pixeldone.domain.todo.TodoPriority
import com.milesxue.pixeldone.domain.todo.TrashChecklistId
import com.milesxue.pixeldone.domain.todo.createInitialChecklistState
import com.milesxue.pixeldone.domain.todo.updateChecklistItems
import com.milesxue.pixeldone.reminder.ReminderScheduler
import com.milesxue.pixeldone.ui.todo.CloudAuthMode
import com.milesxue.pixeldone.ui.todo.PixelDoneAction
import com.milesxue.pixeldone.ui.todo.PixelDoneViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class PixelDoneViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun replaceChecklistStatePersistsAndSyncsReminders() {
        val initial = createInitialChecklistState(emptyList(), createdAtMillis = 1L)
        val repository = TodoRepository(InMemoryTodoStateStore(initial))
        val scheduler = FakeReminderScheduler()
        val viewModel = PixelDoneViewModel(repository, scheduler)
        val todo = TodoItem("one", "One", TodoPriority.HIGH, 2_000L, false, 1L)
        val updated = updateChecklistItems(initial, initial.selectedListId, listOf(todo))!!

        viewModel.onAction(PixelDoneAction.ReplaceChecklistState(updated))

        assertEquals(updated, repository.state.value)
        assertEquals(updated, viewModel.uiState.value.checklistState)
        assertEquals(listOf(todo), scheduler.lastCurrentItems)
    }

    @Test
    fun replaceChecklistStateRequestsCloudSync() {
        val initial = createInitialChecklistState(emptyList(), createdAtMillis = 1L)
        val repository = TodoRepository(InMemoryTodoStateStore(initial))
        val syncCoordinator = FakeSyncCoordinator()
        val viewModel = PixelDoneViewModel(
            todoRepository = repository,
            reminderScheduler = FakeReminderScheduler(),
            syncCoordinator = syncCoordinator,
        )
        val todo = TodoItem("one", "One", TodoPriority.HIGH, 2_000L, false, 1L)
        val updated = updateChecklistItems(initial, initial.selectedListId, listOf(todo))!!

        viewModel.onAction(PixelDoneAction.ReplaceChecklistState(updated))

        assertEquals(1, syncCoordinator.requestCount)
    }

    @Test
    fun replaceChecklistStateDoesNotRequestCloudSyncForSelectionOnly() {
        val initial = createInitialChecklistState(emptyList(), createdAtMillis = 1L)
        val repository = TodoRepository(InMemoryTodoStateStore(initial))
        val syncCoordinator = FakeSyncCoordinator()
        val viewModel = PixelDoneViewModel(
            todoRepository = repository,
            reminderScheduler = FakeReminderScheduler(),
            syncCoordinator = syncCoordinator,
        )
        val selectedTrash = initial.copy(selectedListId = TrashChecklistId)

        viewModel.onAction(PixelDoneAction.ReplaceChecklistState(selectedTrash))

        assertEquals(0, syncCoordinator.requestCount)
    }

    @Test
    fun replaceChecklistStateExcludesSettingsItemsFromReminderSync() {
        val initial = createInitialChecklistState(emptyList(), createdAtMillis = 1L)
        val repository = TodoRepository(InMemoryTodoStateStore(initial))
        val scheduler = FakeReminderScheduler()
        val viewModel = PixelDoneViewModel(repository, scheduler)
        val normalTodo = TodoItem("normal", "Normal", TodoPriority.HIGH, 2_000L, false, 1L)
        val settingsTodo = TodoItem("settings", "Settings", TodoPriority.HIGH, 2_000L, false, 1L)
        val withNormal = updateChecklistItems(initial, initial.selectedListId, listOf(normalTodo))!!
        val corruptedSettings = withNormal.copy(
            lists = withNormal.lists.map { checklist ->
                if (checklist.id == SettingsChecklistId) {
                    checklist.copy(items = listOf(settingsTodo))
                } else {
                    checklist
                }
            },
        )

        viewModel.onAction(PixelDoneAction.ReplaceChecklistState(corruptedSettings))

        assertEquals(listOf(normalTodo), scheduler.lastCurrentItems)
    }

    @Test
    fun uiPreferenceActionsUpdateStateWithoutTouchingRepository() {
        val initial = createInitialChecklistState(emptyList(), createdAtMillis = 1L)
        val repository = TodoRepository(InMemoryTodoStateStore(initial))
        val viewModel = PixelDoneViewModel(repository, FakeReminderScheduler())

        viewModel.onAction(PixelDoneAction.SetSortMode(SortMode.TIME))
        viewModel.onAction(PixelDoneAction.SetHideCompleted(true))
        viewModel.onAction(PixelDoneAction.SetDeadlineCountdownVisible(true))

        assertEquals(SortMode.TIME, viewModel.uiState.value.sortMode)
        assertEquals(true, viewModel.uiState.value.hideCompleted)
        assertEquals(true, viewModel.uiState.value.showDeadlineCountdown)
        assertEquals(initial, repository.state.value)
    }

    @Test
    fun persistedSettingsActionsUpdateSettingsStoreWithoutTouchingTodos() {
        val initial = createInitialChecklistState(emptyList(), createdAtMillis = 1L)
        val repository = TodoRepository(InMemoryTodoStateStore(initial))
        val settingsStore = InMemoryPixelDoneSettingsStore()
        val syncCoordinator = FakeSyncCoordinator()
        val viewModel = PixelDoneViewModel(
            todoRepository = repository,
            reminderScheduler = FakeReminderScheduler(),
            settingsStore = settingsStore,
            syncCoordinator = syncCoordinator,
        )
        val dockConfig = DockConfig(
            plusPlacement = DockPlusPlacement.LEFT_EDGE,
            actions = listOf(DockAction.SORT, DockAction.HIDE_DONE),
        )

        viewModel.onAction(PixelDoneAction.SetDarkTheme(true))
        viewModel.onAction(PixelDoneAction.SetDockConfig(dockConfig))
        viewModel.onAction(PixelDoneAction.SetShowUpdateDialogs(false))

        assertEquals(true, settingsStore.loadSettings().darkTheme)
        assertEquals(DockPlusPlacement.LEFT_EDGE, settingsStore.loadSettings().dockConfig.plusPlacement)
        assertEquals(listOf(DockAction.SORT, DockAction.HIDE_DONE), settingsStore.loadSettings().dockConfig.actions)
        assertEquals(false, settingsStore.loadSettings().showUpdateDialogs)
        assertEquals(settingsStore.loadSettings(), viewModel.uiState.value.settings)
        assertEquals(initial, repository.state.value)
        assertEquals(0, syncCoordinator.requestCount)
    }

    @Test
    fun signInSuccessClearsPasswordWithoutRequestingDuplicateSync() {
        val initial = createInitialChecklistState(emptyList(), createdAtMillis = 1L)
        val repository = TodoRepository(InMemoryTodoStateStore(initial))
        val authRepository = FakeAuthSessionRepository()
        val syncCoordinator = FakeSyncCoordinator()
        val viewModel = PixelDoneViewModel(
            todoRepository = repository,
            reminderScheduler = FakeReminderScheduler(),
            authSessionRepository = authRepository,
            syncCoordinator = syncCoordinator,
        )

        viewModel.onAction(PixelDoneAction.SetAuthEmail("person@example.com"))
        viewModel.onAction(PixelDoneAction.SetAuthPassword("secret"))
        viewModel.onAction(PixelDoneAction.SignIn)
        mainDispatcherRule.advanceUntilIdle()

        assertEquals(listOf("person@example.com" to "secret"), authRepository.signInRequests)
        assertEquals(true, viewModel.uiState.value.authSession.signedIn)
        assertEquals("person@example.com", viewModel.uiState.value.authSession.userEmail)
        assertEquals("", viewModel.uiState.value.authInput.password)
        assertEquals("Signed in.", viewModel.uiState.value.authInput.message)
        assertNull(viewModel.uiState.value.authInput.error)
        assertEquals(0, syncCoordinator.requestCount)
    }

    @Test
    fun signUpSuccessClearsPasswordWithoutRequestingDuplicateSync() {
        val initial = createInitialChecklistState(emptyList(), createdAtMillis = 1L)
        val repository = TodoRepository(InMemoryTodoStateStore(initial))
        val authRepository = FakeAuthSessionRepository()
        val syncCoordinator = FakeSyncCoordinator()
        val viewModel = PixelDoneViewModel(
            todoRepository = repository,
            reminderScheduler = FakeReminderScheduler(),
            authSessionRepository = authRepository,
            syncCoordinator = syncCoordinator,
        )

        viewModel.onAction(PixelDoneAction.SetCloudAuthMode(CloudAuthMode.SIGN_UP))
        viewModel.onAction(PixelDoneAction.SetAuthEmail("new@example.com"))
        viewModel.onAction(PixelDoneAction.SetAuthPassword("secret"))
        viewModel.onAction(PixelDoneAction.SignUp)
        mainDispatcherRule.advanceUntilIdle()

        assertEquals(listOf("new@example.com" to "secret"), authRepository.signUpRequests)
        assertEquals(true, viewModel.uiState.value.authSession.signedIn)
        assertEquals("new@example.com", viewModel.uiState.value.authSession.userEmail)
        assertEquals("", viewModel.uiState.value.authInput.password)
        assertEquals(CloudAuthMode.SIGN_UP, viewModel.uiState.value.authInput.mode)
        assertEquals("Signed up.", viewModel.uiState.value.authInput.message)
        assertNull(viewModel.uiState.value.authInput.error)
        assertEquals(0, syncCoordinator.requestCount)
    }

    @Test
    fun signUpFailureKeepsPanelStateAndShowsError() {
        val initial = createInitialChecklistState(emptyList(), createdAtMillis = 1L)
        val repository = TodoRepository(InMemoryTodoStateStore(initial))
        val authRepository = FakeAuthSessionRepository(signUpFailure = IllegalStateException("Email already registered"))
        val viewModel = PixelDoneViewModel(
            todoRepository = repository,
            reminderScheduler = FakeReminderScheduler(),
            authSessionRepository = authRepository,
        )

        viewModel.onAction(PixelDoneAction.SetCloudAuthMode(CloudAuthMode.SIGN_UP))
        viewModel.onAction(PixelDoneAction.SetAuthEmail("new@example.com"))
        viewModel.onAction(PixelDoneAction.SetAuthPassword("secret"))
        viewModel.onAction(PixelDoneAction.SignUp)
        mainDispatcherRule.advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.authSession.signedIn)
        assertEquals("secret", viewModel.uiState.value.authInput.password)
        assertEquals(CloudAuthMode.SIGN_UP, viewModel.uiState.value.authInput.mode)
        assertEquals("Email already registered", viewModel.uiState.value.authInput.error)
        assertNull(viewModel.uiState.value.authInput.message)
    }

    @Test
    fun cloudAuthModeChangeClearsMessagesWithoutClearingCredentials() {
        val initial = createInitialChecklistState(emptyList(), createdAtMillis = 1L)
        val repository = TodoRepository(InMemoryTodoStateStore(initial))
        val authRepository = FakeAuthSessionRepository(signInFailure = IllegalStateException("Wrong password"))
        val viewModel = PixelDoneViewModel(
            todoRepository = repository,
            reminderScheduler = FakeReminderScheduler(),
            authSessionRepository = authRepository,
        )

        viewModel.onAction(PixelDoneAction.SetAuthEmail("person@example.com"))
        viewModel.onAction(PixelDoneAction.SetAuthPassword("secret"))
        viewModel.onAction(PixelDoneAction.SignIn)
        mainDispatcherRule.advanceUntilIdle()
        viewModel.onAction(PixelDoneAction.SetCloudAuthMode(CloudAuthMode.SIGN_UP))

        val input = viewModel.uiState.value.authInput
        assertEquals("person@example.com", input.email)
        assertEquals("secret", input.password)
        assertEquals(CloudAuthMode.SIGN_UP, input.mode)
        assertNull(input.error)
        assertNull(input.message)
    }

    @Test
    fun syncNowErrorWritesErrorInsteadOfSuccessMessage() {
        val initial = createInitialChecklistState(emptyList(), createdAtMillis = 1L)
        val repository = TodoRepository(InMemoryTodoStateStore(initial))
        val syncCoordinator = FakeSyncCoordinator(SyncCoordinatorStatus.ERROR)
        val viewModel = PixelDoneViewModel(
            todoRepository = repository,
            reminderScheduler = FakeReminderScheduler(),
            syncCoordinator = syncCoordinator,
        )

        viewModel.onAction(PixelDoneAction.SyncNow)
        mainDispatcherRule.advanceUntilIdle()

        assertEquals("Sync failed.", viewModel.uiState.value.authInput.error)
        assertNull(viewModel.uiState.value.authInput.message)
    }

    @Test
    fun syncNowSuccessWritesSuccessMessageInsteadOfError() {
        val initial = createInitialChecklistState(emptyList(), createdAtMillis = 1L)
        val repository = TodoRepository(InMemoryTodoStateStore(initial))
        val syncCoordinator = FakeSyncCoordinator(SyncCoordinatorStatus.SYNCED)
        val viewModel = PixelDoneViewModel(
            todoRepository = repository,
            reminderScheduler = FakeReminderScheduler(),
            syncCoordinator = syncCoordinator,
        )

        viewModel.onAction(PixelDoneAction.SyncNow)
        mainDispatcherRule.advanceUntilIdle()

        assertEquals("Synced.", viewModel.uiState.value.authInput.message)
        assertNull(viewModel.uiState.value.authInput.error)
    }

    @Test
    fun cancelSignInClearsPasswordAndMessagesButKeepsEmail() {
        val initial = createInitialChecklistState(emptyList(), createdAtMillis = 1L)
        val repository = TodoRepository(InMemoryTodoStateStore(initial))
        val authRepository = FakeAuthSessionRepository(signInFailure = IllegalStateException("Wrong password"))
        val viewModel = PixelDoneViewModel(
            todoRepository = repository,
            reminderScheduler = FakeReminderScheduler(),
            authSessionRepository = authRepository,
        )

        viewModel.onAction(PixelDoneAction.SetAuthEmail("person@example.com"))
        viewModel.onAction(PixelDoneAction.SetAuthPassword("secret"))
        viewModel.onAction(PixelDoneAction.SignIn)
        mainDispatcherRule.advanceUntilIdle()

        assertEquals("Wrong password", viewModel.uiState.value.authInput.error)
        viewModel.onAction(PixelDoneAction.CancelSignIn)

        val input = viewModel.uiState.value.authInput
        assertEquals("person@example.com", input.email)
        assertEquals("", input.password)
        assertEquals(false, input.busy)
        assertNull(input.error)
        assertNull(input.message)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    fun advanceUntilIdle() {
        dispatcher.scheduler.advanceUntilIdle()
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

private class FakeReminderScheduler : ReminderScheduler {
    var lastPreviousItems: List<TodoItem> = emptyList()
    var lastCurrentItems: List<TodoItem> = emptyList()

    override fun sync(
        previousItems: List<TodoItem>,
        currentItems: List<TodoItem>,
    ): Set<ReminderCapability> {
        lastPreviousItems = previousItems
        lastCurrentItems = currentItems
        return emptySet()
    }

    override fun schedule(item: TodoItem): Set<ReminderCapability> = emptySet()

    override fun canScheduleExactAlarms(): Boolean = true

    override fun cancel(itemId: String) = Unit
}


private class FakeAuthSessionRepository(
    private val signInFailure: Exception? = null,
    private val signUpFailure: Exception? = null,
) : AuthSessionRepository {
    private val mutableSession = MutableStateFlow(
        AuthSession(
            cloudAvailable = true,
            displayLabel = "SIGNED OUT",
        ),
    )
    override val session: StateFlow<AuthSession> = mutableSession.asStateFlow()
    var signInRequests: List<Pair<String, String>> = emptyList()
    var signUpRequests: List<Pair<String, String>> = emptyList()

    override suspend fun signIn(email: String, password: String): AuthSession {
        signInRequests = signInRequests + (email to password)
        signInFailure?.let { throw it }
        val signedInSession = AuthSession(
            signedIn = true,
            userId = "user-1",
            userEmail = email,
            displayLabel = email,
            cloudAvailable = true,
            accessToken = "access-token",
        )
        mutableSession.value = signedInSession
        return signedInSession
    }

    override suspend fun signUp(email: String, password: String): AuthSession {
        signUpRequests = signUpRequests + (email to password)
        signUpFailure?.let { throw it }
        val signedInSession = AuthSession(
            signedIn = true,
            userId = "user-1",
            userEmail = email,
            displayLabel = email,
            cloudAvailable = true,
            accessToken = "access-token",
        )
        mutableSession.value = signedInSession
        return signedInSession
    }

    override suspend fun signOut() {
        mutableSession.value = mutableSession.value.copy(
            signedIn = false,
            accessToken = null,
        )
    }

    override suspend fun refreshSessionIfNeeded(nowMillis: Long, force: Boolean): AuthSession = mutableSession.value
}
private class FakeSyncCoordinator(
    initialStatus: SyncCoordinatorStatus = SyncCoordinatorStatus.IDLE,
) : SyncCoordinator {
    private val mutableStatus = MutableStateFlow(initialStatus)
    private val mutableRunState = MutableStateFlow(SyncRunState(status = initialStatus))
    override val status: StateFlow<SyncCoordinatorStatus> = mutableStatus.asStateFlow()
    override val runState: StateFlow<SyncRunState> = mutableRunState.asStateFlow()
    var requestCount: Int = 0

    override suspend fun syncNow(): SyncCoordinatorStatus = mutableStatus.value

    override fun requestSync() {
        requestCount += 1
    }
}
