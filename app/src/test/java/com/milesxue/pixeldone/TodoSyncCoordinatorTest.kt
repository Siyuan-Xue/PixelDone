package com.milesxue.pixeldone

import com.milesxue.pixeldone.data.local.TodoChecklistEntity
import com.milesxue.pixeldone.data.local.TodoEntitySet
import com.milesxue.pixeldone.data.local.TodoItemEntity
import com.milesxue.pixeldone.data.local.TodoStateMetadataEntity
import com.milesxue.pixeldone.data.sync.AuthSessionRepository
import com.milesxue.pixeldone.data.sync.RemoteChecklistRecord
import com.milesxue.pixeldone.data.sync.RemoteTodoDataSource
import com.milesxue.pixeldone.data.sync.RemoteTodoItemRecord
import com.milesxue.pixeldone.data.sync.RemoteTodoSnapshot
import com.milesxue.pixeldone.data.sync.TodoSyncCoordinator
import com.milesxue.pixeldone.data.sync.TodoSyncLocalStore
import com.milesxue.pixeldone.domain.sync.AuthSession
import com.milesxue.pixeldone.domain.sync.SyncCoordinatorStatus
import com.milesxue.pixeldone.domain.sync.SyncRecordState
import com.milesxue.pixeldone.domain.todo.ClockProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TodoSyncCoordinatorTest {
    @Test
    fun syncDoesNotResurrectTodoDeletedWhilePullWasRunning() = runTest {
        val localStore = FakeTodoSyncLocalStore(entitySetWith(item = syncedActiveItem()))
        val auth = CoordinatorAuthSessionRepository(signedOutSession())
        val remote = FakeRemoteTodoDataSource(
            pullSnapshot = RemoteTodoSnapshot(
                checklists = listOf(remoteMainChecklist()),
                items = listOf(remoteActiveItem()),
            ),
        )
        val pullGate = CompletableDeferred<Unit>()
        remote.pullGate = pullGate
        val coordinator = coordinator(auth = auth, localStore = localStore, remote = remote)
        advanceUntilIdle()

        auth.setSession(signedInSession(token = "token-1"))
        runCurrent()
        remote.pullStarted.await()
        localStore.entitySet = localStore.entitySet.copy(
            items = localStore.entitySet.items.map { item ->
                item.copy(
                    checklistLocalId = "trash",
                    updatedAtMillis = 2_000L,
                    deletedAtMillis = 2_000L,
                    trashedFromChecklistId = "main",
                    trashedFromChecklistName = "MAIN",
                    trashedAtMillis = 2_000L,
                    syncState = SyncRecordState.NOT_SYNCED.name,
                )
            },
        )

        pullGate.complete(Unit)
        advanceUntilIdle()
        assertEquals(SyncCoordinatorStatus.SYNCED, coordinator.status.value)

        val savedItem = localStore.entitySet.items.single { it.localId == "todo-1" }
        assertEquals("trash", savedItem.checklistLocalId)
        assertEquals(2_000L, savedItem.deletedAtMillis)
        assertEquals(2_000L, savedItem.trashedAtMillis)
        assertEquals(SyncRecordState.SYNCED.name, savedItem.syncState)
        assertEquals(1, remote.pushedSnapshots.size)
        assertEquals(2_000L, remote.pushedSnapshots.single().items.single().deletedAtMillis)
    }

    @Test
    fun pushMetadataDoesNotMarkTodoSyncedWhenItChangedDuringPush() = runTest {
        val dirtyItem = syncedActiveItem().copy(
            syncState = SyncRecordState.NOT_SYNCED.name,
            lastSyncedAtMillis = 900L,
        )
        val auth = CoordinatorAuthSessionRepository(signedOutSession())
        val localStore = FakeTodoSyncLocalStore(entitySetWith(item = dirtyItem))
        val remote = FakeRemoteTodoDataSource(pullSnapshot = RemoteTodoSnapshot())
        val pushGate = CompletableDeferred<Unit>()
        remote.pushGate = pushGate
        val coordinator = coordinator(auth = auth, localStore = localStore, remote = remote)
        advanceUntilIdle()

        auth.setSession(signedInSession(token = "token-1"))
        runCurrent()
        remote.pushStarted.await()
        localStore.entitySet = localStore.entitySet.copy(
            items = localStore.entitySet.items.map { item ->
                item.copy(
                    title = "Edited while pushing",
                    updatedAtMillis = 3_000L,
                    syncState = SyncRecordState.NOT_SYNCED.name,
                )
            },
        )

        pushGate.complete(Unit)
        advanceUntilIdle()
        assertEquals(SyncCoordinatorStatus.SYNCED, coordinator.status.value)

        val savedItem = localStore.entitySet.items.single { it.localId == "todo-1" }
        assertEquals("Edited while pushing", savedItem.title)
        assertEquals(3_000L, savedItem.updatedAtMillis)
        assertEquals(SyncRecordState.NOT_SYNCED.name, savedItem.syncState)
        assertEquals("remote-todo-1", savedItem.remoteId)
    }

    @Test
    fun requestSyncDebouncesRapidAutomaticRequests() = runTest {
        val localStore = FakeTodoSyncLocalStore(entitySetWith(item = syncedActiveItem()))
        val remote = FakeRemoteTodoDataSource()
        val coordinator = coordinator(localStore = localStore, remote = remote, autoSyncDebounceMillis = 1_000L)
        advanceUntilIdle()
        remote.resetCounts()

        coordinator.requestSync()
        coordinator.requestSync()
        coordinator.requestSync()
        runCurrent()
        advanceTimeBy(999L)
        runCurrent()
        assertEquals(0, remote.pullCount)

        advanceTimeBy(1L)
        advanceUntilIdle()

        assertEquals(1, remote.pullCount)
    }

    @Test
    fun automaticRequestsDuringSyncOnlyScheduleOneFollowUp() = runTest {
        val auth = CoordinatorAuthSessionRepository(signedOutSession())
        val localStore = FakeTodoSyncLocalStore(entitySetWith(item = syncedActiveItem()))
        val remote = FakeRemoteTodoDataSource()
        val pullGate = CompletableDeferred<Unit>()
        remote.pullGate = pullGate
        val coordinator = coordinator(
            auth = auth,
            localStore = localStore,
            remote = remote,
            autoSyncDebounceMillis = 1_000L,
        )
        advanceUntilIdle()

        auth.setSession(signedInSession(token = "token-1"))
        runCurrent()
        remote.pullStarted.await()
        coordinator.requestSync()
        coordinator.requestSync()
        coordinator.requestSync()
        runCurrent()

        pullGate.complete(Unit)
        advanceUntilIdle()
        advanceTimeBy(1_000L)
        advanceUntilIdle()

        assertEquals(2, remote.pullCount)
    }

    @Test
    fun tokenRefreshForSameUserDoesNotTriggerExtraSync() = runTest {
        val auth = CoordinatorAuthSessionRepository(signedOutSession())
        val localStore = FakeTodoSyncLocalStore(entitySetWith(item = syncedActiveItem()))
        val remote = FakeRemoteTodoDataSource()
        coordinator(auth = auth, localStore = localStore, remote = remote)
        advanceUntilIdle()

        auth.setSession(signedInSession(token = "token-1"))
        advanceUntilIdle()
        assertEquals(1, remote.pullCount)

        auth.setSession(signedInSession(token = "token-2"))
        advanceUntilIdle()

        assertEquals(1, remote.pullCount)
    }

    private fun TestScope.coordinator(
        auth: CoordinatorAuthSessionRepository = CoordinatorAuthSessionRepository(signedInSession()),
        localStore: FakeTodoSyncLocalStore,
        remote: FakeRemoteTodoDataSource,
        autoSyncDebounceMillis: Long = 0L,
    ): TodoSyncCoordinator = TodoSyncCoordinator(
        authSessionRepository = auth,
        localStore = localStore,
        remoteDataSource = remote,
        clockProvider = MutableClock(),
        scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
        autoSyncDebounceMillis = autoSyncDebounceMillis,
    )
}

private class CoordinatorAuthSessionRepository(initialSession: AuthSession) : AuthSessionRepository {
    private val mutableSession = MutableStateFlow(initialSession)
    override val session: StateFlow<AuthSession> = mutableSession.asStateFlow()

    fun setSession(session: AuthSession) {
        mutableSession.value = session
    }

    override suspend fun signIn(email: String, password: String): AuthSession = mutableSession.value
    override suspend fun signUp(email: String, password: String): AuthSession = mutableSession.value
    override suspend fun signOut() {
        mutableSession.value = signedOutSession()
    }

    override suspend fun refreshSessionIfNeeded(nowMillis: Long, force: Boolean): AuthSession = mutableSession.value
}

private class FakeTodoSyncLocalStore(initialEntitySet: TodoEntitySet) : TodoSyncLocalStore {
    var entitySet: TodoEntitySet = initialEntitySet
    var replaceCount: Int = 0
    var updateCount: Int = 0

    override suspend fun loadEntitySetForSync(nowMillis: Long): TodoEntitySet = entitySet

    override suspend fun replaceEntitySetFromSync(entitySet: TodoEntitySet) {
        this.entitySet = entitySet
        replaceCount += 1
    }

    override suspend fun updateEntitySetFromSync(
        nowMillis: Long,
        transform: (TodoEntitySet) -> TodoEntitySet,
    ): TodoEntitySet {
        updateCount += 1
        val updated = transform(entitySet)
        entitySet = updated
        return updated
    }
}

private class FakeRemoteTodoDataSource(
    var pullSnapshot: RemoteTodoSnapshot = RemoteTodoSnapshot(),
) : RemoteTodoDataSource {
    var pullCount: Int = 0
    var pushCount: Int = 0
    val pushedSnapshots = mutableListOf<RemoteTodoSnapshot>()
    var pullStarted = CompletableDeferred<Unit>()
    var pushStarted = CompletableDeferred<Unit>()
    var pullGate: CompletableDeferred<Unit>? = null
    var pushGate: CompletableDeferred<Unit>? = null

    override suspend fun pullSnapshot(session: AuthSession): RemoteTodoSnapshot {
        pullCount += 1
        if (!pullStarted.isCompleted) pullStarted.complete(Unit)
        pullGate?.await()
        return pullSnapshot
    }

    override suspend fun pushSnapshot(session: AuthSession, snapshot: RemoteTodoSnapshot): RemoteTodoSnapshot {
        pushCount += 1
        pushedSnapshots += snapshot
        if (!pushStarted.isCompleted) pushStarted.complete(Unit)
        pushGate?.await()
        return snapshot.copy(
            checklists = snapshot.checklists.map { checklist ->
                checklist.copy(
                    remoteId = checklist.remoteId ?: "remote-${checklist.localId}",
                    remoteVersion = checklist.updatedAtMillis,
                )
            },
            items = snapshot.items.map { item ->
                item.copy(
                    remoteId = item.remoteId ?: "remote-${item.localId}",
                    remoteVersion = item.updatedAtMillis,
                )
            },
        )
    }

    fun resetCounts() {
        pullCount = 0
        pushCount = 0
        pushedSnapshots.clear()
        pullStarted = CompletableDeferred()
        pushStarted = CompletableDeferred()
    }
}

private class MutableClock : ClockProvider {
    private var nowMillis = 10_000L
    override fun nowMillis(): Long = nowMillis++
}

private fun signedInSession(token: String = "access-token"): AuthSession = AuthSession(
    signedIn = true,
    userId = "user-1",
    userEmail = "person@example.com",
    displayLabel = "person@example.com",
    cloudAvailable = true,
    accessToken = token,
)

private fun signedOutSession(): AuthSession = AuthSession(
    signedIn = false,
    displayLabel = "SIGNED OUT",
    cloudAvailable = true,
)

private fun entitySetWith(item: TodoItemEntity): TodoEntitySet = TodoEntitySet(
    metadata = TodoStateMetadataEntity(selectedListLocalId = "main", updatedAtMillis = 1_000L),
    checklists = listOf(
        TodoChecklistEntity(
            localId = "main",
            sortIndex = 0,
            remoteId = "remote-main",
            ownerUserId = "user-1",
            name = "MAIN",
            createdAtMillis = 100L,
            updatedAtMillis = 1_000L,
            syncState = SyncRecordState.SYNCED.name,
            lastSyncedAtMillis = 1_000L,
            remoteVersion = 1_000L,
        ),
    ),
    items = listOf(item),
)

private fun syncedActiveItem(): TodoItemEntity = TodoItemEntity(
    localId = "todo-1",
    checklistLocalId = "main",
    sortIndex = 0,
    remoteId = "remote-todo-1",
    ownerUserId = "user-1",
    title = "One",
    priority = "HIGH",
    dueAtMillis = 2_000L,
    completed = false,
    createdAtMillis = 100L,
    updatedAtMillis = 1_000L,
    reminderRepeat = "NONE",
    imageSyncState = SyncRecordState.LOCAL_ONLY.name,
    syncState = SyncRecordState.SYNCED.name,
    lastSyncedAtMillis = 1_000L,
    remoteVersion = 1_000L,
)

private fun remoteMainChecklist(): RemoteChecklistRecord = RemoteChecklistRecord(
    localId = "main",
    remoteId = "remote-main",
    ownerUserId = "user-1",
    sortIndex = 0,
    name = "MAIN",
    createdAtMillis = 100L,
    updatedAtMillis = 1_000L,
    remoteVersion = 1_000L,
)

private fun remoteActiveItem(): RemoteTodoItemRecord = RemoteTodoItemRecord(
    localId = "todo-1",
    remoteId = "remote-todo-1",
    ownerUserId = "user-1",
    checklistLocalId = "main",
    sortIndex = 0,
    title = "One",
    priority = "HIGH",
    dueAtMillis = 2_000L,
    completed = false,
    createdAtMillis = 100L,
    updatedAtMillis = 1_000L,
    reminderRepeat = "NONE",
    imageSyncState = SyncRecordState.LOCAL_ONLY.name,
    remoteVersion = 1_000L,
)