package com.milesxue.pixeldone

import com.milesxue.pixeldone.data.local.SyncRecordTypeItem
import com.milesxue.pixeldone.data.local.TodoChecklistEntity
import com.milesxue.pixeldone.data.local.TodoEntitySet
import com.milesxue.pixeldone.data.local.TodoItemEntity
import com.milesxue.pixeldone.data.local.TodoStateMetadataEntity
import com.milesxue.pixeldone.data.sync.AuthSessionRepository
import com.milesxue.pixeldone.data.sync.LocalSyncConflictRecord
import com.milesxue.pixeldone.data.sync.RemoteChangeBatch
import com.milesxue.pixeldone.data.sync.RemoteChecklistRecord
import com.milesxue.pixeldone.data.sync.RemoteMutationBatch
import com.milesxue.pixeldone.data.sync.RemotePushResult
import com.milesxue.pixeldone.data.sync.RemoteTodoDataSource
import com.milesxue.pixeldone.data.sync.RemoteTodoItemRecord
import com.milesxue.pixeldone.data.sync.RemoteTodoSnapshot
import com.milesxue.pixeldone.data.sync.SyncMutationRecord
import com.milesxue.pixeldone.data.sync.SyncMetadataSession
import com.milesxue.pixeldone.data.sync.SyncNetworkException
import com.milesxue.pixeldone.data.sync.TodoSyncCoordinator
import com.milesxue.pixeldone.data.sync.TodoSyncLocalStore
import com.milesxue.pixeldone.domain.sync.AuthSession
import com.milesxue.pixeldone.domain.sync.ConflictResolutionChoice
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
                    trashedFromChecklistId = "main",
                    trashedFromChecklistName = "MAIN",
                    trashedAtMillis = 2_000L,
                    syncState = SyncRecordState.NOT_SYNCED.name,
                )
            },
        )

        pullGate.complete(Unit)
        advanceUntilIdle()
        assertEquals(SyncCoordinatorStatus.STABLE, coordinator.status.value)

        val savedItem = localStore.entitySet.items.single { it.localId == "todo-1" }
        assertEquals("trash", savedItem.checklistLocalId)
        assertEquals(2_000L, savedItem.trashedAtMillis)
        assertEquals(SyncRecordState.SYNCED.name, savedItem.syncState)
        assertEquals(1, remote.pushedSnapshots.size)
        assertEquals(2_000L, remote.pushedSnapshots.single().items.single().trashedAtMillis)
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
        assertEquals(SyncCoordinatorStatus.STABLE, coordinator.status.value)

        val savedItem = localStore.entitySet.items.single { it.localId == "todo-1" }
        assertEquals("Edited while pushing", savedItem.title)
        assertEquals(3_000L, savedItem.updatedAtMillis)
        assertEquals(SyncRecordState.NOT_SYNCED.name, savedItem.syncState)
        assertEquals("remote-todo-1", savedItem.remoteId)
    }

    @Test
    fun syncConflictWritesLocalConflictRecordWithBothPayloads() = runTest {
        val localItem = syncedActiveItem().copy(
            title = "Local title",
            updatedAtMillis = 2_000L,
            syncState = SyncRecordState.NOT_SYNCED.name,
        )
        val localStore = FakeTodoSyncLocalStore(entitySetWith(item = localItem))
        localStore.pristineSnapshot = RemoteTodoSnapshot(
            checklists = listOf(remoteMainChecklist()),
            items = listOf(remoteActiveItem()),
        )
        val remote = FakeRemoteTodoDataSource(
            pullSnapshot = RemoteTodoSnapshot(
                checklists = listOf(remoteMainChecklist()),
                items = listOf(
                    remoteActiveItem().copy(
                        title = "Cloud title",
                        updatedAtMillis = 3_000L,
                        remoteVersion = 3_000L,
                    ),
                ),
            ),
        )
        val coordinator = coordinator(localStore = localStore, remote = remote)
        advanceUntilIdle()

        assertEquals(SyncCoordinatorStatus.CONFLICT, coordinator.status.value)
        val conflict = localStore.conflicts.single()
        assertEquals(SyncRecordTypeItem, conflict.recordType)
        assertEquals("todo-1", conflict.localId)
        assertEquals(listOf("title"), conflict.fields)
        assertEquals(3_000L, conflict.remoteVersion)
        assertEquals(true, conflict.localPayloadJson.contains("Local title"))
        assertEquals(true, conflict.remotePayloadJson.contains("Cloud title"))
    }

    @Test
    fun rebaselineRebuildsExplicitDomainConflictWithoutLegacyMetadata() = runTest {
        val localStore = FakeTodoSyncLocalStore(
            entitySetWith(
                item = syncedActiveItem().copy(
                    title = "Local title",
                    updatedAtMillis = 2_000L,
                    remoteVersion = 1_000L,
                    syncState = SyncRecordState.CONFLICT.name,
                ),
            ),
        )
        val remote = FakeRemoteTodoDataSource(
            RemoteTodoSnapshot(
                items = listOf(
                    remoteActiveItem().copy(
                        title = "Cloud title",
                        updatedAtMillis = 3_000L,
                        remoteVersion = 3_000L,
                    ),
                ),
            ),
        )

        val coordinator = coordinator(localStore = localStore, remote = remote)
        advanceUntilIdle()

        assertEquals(SyncCoordinatorStatus.CONFLICT, coordinator.status.value)
        assertEquals("Local title", localStore.entitySet.items.single().title)
        assertEquals(listOf("title"), localStore.conflicts.single().fields)
        assertEquals(0, remote.pushCount)
    }

    @Test
    fun rebaselineClearsExplicitDomainConflictWhenCloudAlreadyMatches() = runTest {
        val localStore = FakeTodoSyncLocalStore(
            entitySetWith(item = syncedActiveItem().copy(syncState = SyncRecordState.CONFLICT.name)),
        )
        val remote = FakeRemoteTodoDataSource(
            RemoteTodoSnapshot(items = listOf(remoteActiveItem())),
        )

        val coordinator = coordinator(localStore = localStore, remote = remote)
        advanceUntilIdle()

        assertEquals(SyncCoordinatorStatus.STABLE, coordinator.status.value)
        assertEquals(SyncRecordState.SYNCED.name, localStore.entitySet.items.single().syncState)
        assertEquals(emptyList<LocalSyncConflictRecord>(), localStore.conflicts)
        assertEquals(0, remote.pushCount)
    }

    @Test
    fun unresolvedConflictIsNotUploadedAfterEmptyIncrementalPull() = runTest {
        val localItem = syncedActiveItem().copy(
            title = "Local title",
            updatedAtMillis = 2_000L,
            syncState = SyncRecordState.NOT_SYNCED.name,
        )
        val localStore = FakeTodoSyncLocalStore(entitySetWith(item = localItem)).apply {
            pristineSnapshot = RemoteTodoSnapshot(
                checklists = listOf(remoteMainChecklist()),
                items = listOf(remoteActiveItem()),
            )
        }
        val remote = FakeRemoteTodoDataSource(RemoteTodoSnapshot(
            items = listOf(remoteActiveItem().copy(title = "Cloud title", remoteVersion = 3_000L)),
        ))
        val coordinator = coordinator(localStore = localStore, remote = remote)
        advanceUntilIdle()
        assertEquals(SyncCoordinatorStatus.CONFLICT, coordinator.status.value)
        remote.pullSnapshot = RemoteTodoSnapshot()
        remote.resetCounts()

        val status = coordinator.syncNow()
        advanceUntilIdle()

        assertEquals(SyncCoordinatorStatus.CONFLICT, status)
        assertEquals(0, remote.pushCount)
        assertEquals(SyncRecordState.CONFLICT.name, localStore.entitySet.items.single().syncState)
        assertEquals(1, localStore.conflicts.size)
    }

    @Test
    fun nonOverlappingLocalAndCloudFieldsMergeWithoutConflict() = runTest {
        val local = syncedActiveItem().copy(
            title = "Local title",
            updatedAtMillis = 2_000L,
            syncState = SyncRecordState.NOT_SYNCED.name,
        )
        val store = FakeTodoSyncLocalStore(entitySetWith(item = local)).apply {
            pristineSnapshot = RemoteTodoSnapshot(items = listOf(remoteActiveItem()))
        }
        val remote = FakeRemoteTodoDataSource(RemoteTodoSnapshot(
            items = listOf(remoteActiveItem().copy(completed = true, updatedAtMillis = 3_000L, remoteVersion = 3_000L)),
        ))
        val coordinator = coordinator(localStore = store, remote = remote)
        advanceUntilIdle()

        val merged = store.entitySet.items.single()
        assertEquals(SyncCoordinatorStatus.STABLE, coordinator.status.value)
        assertEquals("Local title", merged.title)
        assertEquals(true, merged.completed)
        assertEquals(emptyList<LocalSyncConflictRecord>(), store.conflicts)
    }

    @Test
    fun localImageMetadataIsNeverUploadedAndSurvivesCloudRefresh() = runTest {
        val local = syncedActiveItem().copy(
            imageLocalName = "private-photo.jpg",
            imageRemotePath = null,
            imageSyncState = SyncRecordState.LOCAL_ONLY.name,
            title = "Local title",
            updatedAtMillis = 2_000L,
            syncState = SyncRecordState.NOT_SYNCED.name,
        )
        val store = FakeTodoSyncLocalStore(entitySetWith(item = local)).apply {
            pristineSnapshot = RemoteTodoSnapshot(items = listOf(remoteActiveItem()))
        }
        val remote = FakeRemoteTodoDataSource(
            RemoteTodoSnapshot(
                items = listOf(remoteActiveItem().copy(completed = true, remoteVersion = 3_000L)),
            ),
        )

        val coordinator = coordinator(localStore = store, remote = remote)
        advanceUntilIdle()

        assertEquals(SyncCoordinatorStatus.STABLE, coordinator.status.value)
        val uploaded = remote.pushedSnapshots.single().items.single()
        assertEquals(null, uploaded.imageLocalName)
        assertEquals(null, uploaded.imageRemotePath)
        assertEquals(SyncRecordState.LOCAL_ONLY.name, uploaded.imageSyncState)
        val merged = store.entitySet.items.single()
        assertEquals("private-photo.jpg", merged.imageLocalName)
        assertEquals(true, merged.completed)
    }

    @Test
    fun keepLocalClearsConflictAndUploadsLocalVersion() = runTest {
        val localItem = syncedActiveItem().copy(
            title = "Local title",
            updatedAtMillis = 2_000L,
            syncState = SyncRecordState.NOT_SYNCED.name,
        )
        val localStore = FakeTodoSyncLocalStore(entitySetWith(item = localItem))
        localStore.pristineSnapshot = RemoteTodoSnapshot(
            checklists = listOf(remoteMainChecklist()),
            items = listOf(remoteActiveItem()),
        )
        val remote = FakeRemoteTodoDataSource(
            pullSnapshot = RemoteTodoSnapshot(
                checklists = listOf(remoteMainChecklist()),
                items = listOf(
                    remoteActiveItem().copy(
                        title = "Cloud title",
                        updatedAtMillis = 3_000L,
                        remoteVersion = 3_000L,
                    ),
                ),
            ),
        )
        val coordinator = coordinator(localStore = localStore, remote = remote)
        advanceUntilIdle()
        remote.pullSnapshot = RemoteTodoSnapshot(checklists = listOf(remoteMainChecklist()))

        val status = coordinator.resolveConflict(
            recordType = SyncRecordTypeItem,
            localId = "todo-1",
            choice = ConflictResolutionChoice.KEEP_LOCAL,
        )
        advanceUntilIdle()

        assertEquals(SyncCoordinatorStatus.STABLE, status)
        assertEquals(emptyList<LocalSyncConflictRecord>(), localStore.conflicts)
        assertEquals("Local title", remote.pushedSnapshots.last().items.single().title)
        val savedItem = localStore.entitySet.items.single { it.localId == "todo-1" }
        assertEquals("Local title", savedItem.title)
        assertEquals(SyncRecordState.SYNCED.name, savedItem.syncState)
    }

    @Test
    fun keepCloudClearsConflictAndAppliesCloudVersionAsSynced() = runTest {
        val localItem = syncedActiveItem().copy(
            title = "Local title",
            updatedAtMillis = 2_000L,
            syncState = SyncRecordState.NOT_SYNCED.name,
        )
        val localStore = FakeTodoSyncLocalStore(entitySetWith(item = localItem))
        localStore.pristineSnapshot = RemoteTodoSnapshot(
            checklists = listOf(remoteMainChecklist()),
            items = listOf(remoteActiveItem()),
        )
        val remote = FakeRemoteTodoDataSource(
            pullSnapshot = RemoteTodoSnapshot(
                checklists = listOf(remoteMainChecklist()),
                items = listOf(
                    remoteActiveItem().copy(
                        title = "Cloud title",
                        updatedAtMillis = 3_000L,
                        remoteVersion = 3_000L,
                    ),
                ),
            ),
        )
        val coordinator = coordinator(localStore = localStore, remote = remote)
        advanceUntilIdle()

        val status = coordinator.resolveConflict(
            recordType = SyncRecordTypeItem,
            localId = "todo-1",
            choice = ConflictResolutionChoice.KEEP_CLOUD,
        )

        assertEquals(SyncCoordinatorStatus.STABLE, status)
        assertEquals(emptyList<LocalSyncConflictRecord>(), localStore.conflicts)
        val savedItem = localStore.entitySet.items.single { it.localId == "todo-1" }
        assertEquals("Cloud title", savedItem.title)
        assertEquals(3_000L, savedItem.remoteVersion)
        assertEquals(SyncRecordState.SYNCED.name, savedItem.syncState)
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

    @Test
    fun corruptConflictPayloadInvalidatesMetadataAndRebaselinesWithoutGhostCount() = runTest {
        val localStore = FakeTodoSyncLocalStore(entitySetWith(item = syncedActiveItem())).apply {
            conflicts += LocalSyncConflictRecord(
                recordType = SyncRecordTypeItem,
                localId = "todo-legacy",
                localPayloadJson = "{\"localId\":\"todo-legacy\"}",
                remotePayloadJson = "{\"localId\":\"todo-legacy\"}",
                fields = listOf("title"),
                message = "Legacy conflict",
                createdAtMillis = 1L,
            )
        }
        val remote = FakeRemoteTodoDataSource()
        val coordinator = coordinator(localStore = localStore, remote = remote)

        advanceUntilIdle()

        assertEquals(SyncCoordinatorStatus.STABLE, coordinator.status.value)
        assertEquals(1, localStore.metadataInvalidationCount)
        assertEquals(2, remote.pullCount)
        assertEquals(0, coordinator.runState.value.conflictCount)
        assertEquals(emptyList<LocalSyncConflictRecord>(), localStore.conflicts)
    }

    @Test
    fun networkFailureRetriesOnceAndNeverCreatesConflict() = runTest {
        val localStore = FakeTodoSyncLocalStore(entitySetWith(item = syncedActiveItem()))
        val remote = FakeRemoteTodoDataSource().apply {
            pullFailure = SyncNetworkException("unexpected end of stream")
        }
        val coordinator = coordinator(localStore = localStore, remote = remote)

        advanceUntilIdle()

        assertEquals(SyncCoordinatorStatus.NETWORK_ERROR, coordinator.status.value)
        assertEquals(2, remote.pullCount)
        assertEquals(0, coordinator.runState.value.conflictCount)
        assertEquals(
            "Network unavailable. Check Wi-Fi, mobile data, or VPN.",
            coordinator.runState.value.lastError,
        )
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

    var syncCursor: Long? = null
    var pristineSnapshot: RemoteTodoSnapshot = RemoteTodoSnapshot()
    val pendingMutations = mutableListOf<SyncMutationRecord>()
    val conflicts = mutableListOf<LocalSyncConflictRecord>()
    var metadataInvalidationCount = 0

    override suspend fun beginSyncMetadata(ownerUserId: String, nowMillis: Long): SyncMetadataSession =
        SyncMetadataSession(generation = "fake", rebuilding = false)

    override suspend fun invalidateSyncMetadata(ownerUserId: String) {
        metadataInvalidationCount += 1
        syncCursor = null
        pristineSnapshot = RemoteTodoSnapshot()
        pendingMutations.clear()
        conflicts.clear()
    }

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

    override suspend fun loadSyncCursor(ownerUserId: String): Long? = syncCursor

    override suspend fun saveSyncCursor(ownerUserId: String, remoteVersion: Long, updatedAtMillis: Long) {
        syncCursor = remoteVersion
    }

    override suspend fun loadPristineSnapshot(ownerUserId: String): RemoteTodoSnapshot = pristineSnapshot

    override suspend fun savePristineSnapshot(ownerUserId: String, snapshot: RemoteTodoSnapshot, syncedAtMillis: Long) {
        pristineSnapshot = snapshot
    }

    override suspend fun loadPendingMutations(ownerUserId: String): List<SyncMutationRecord> = pendingMutations.toList()

    override suspend fun recordPendingMutation(ownerUserId: String, mutation: SyncMutationRecord) {
        pendingMutations.removeAll { it.mutationUuid == mutation.mutationUuid }
        pendingMutations += mutation
    }

    override suspend fun clearPendingMutation(ownerUserId: String, mutationUuid: String) {
        pendingMutations.removeAll { it.mutationUuid == mutationUuid }
    }

    override suspend fun recordConflict(ownerUserId: String, conflict: LocalSyncConflictRecord) {
        conflicts.removeAll { it.recordType == conflict.recordType && it.localId == conflict.localId }
        conflicts += conflict
    }

    override suspend fun loadConflicts(ownerUserId: String): List<LocalSyncConflictRecord> = conflicts.toList()

    override suspend fun loadConflict(
        ownerUserId: String,
        recordType: String,
        localId: String,
    ): LocalSyncConflictRecord? = conflicts.firstOrNull { it.recordType == recordType && it.localId == localId }

    override suspend fun clearConflict(ownerUserId: String, recordType: String, localId: String) {
        conflicts.removeAll { it.recordType == recordType && it.localId == localId }
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
    var pullFailure: Exception? = null

    override suspend fun pullChanges(session: AuthSession, sinceVersion: Long?): RemoteChangeBatch {
        pullCount += 1
        if (!pullStarted.isCompleted) pullStarted.complete(Unit)
        pullGate?.await()
        pullFailure?.let { throw it }
        return RemoteChangeBatch(
            schemaVersion = "3.1",
            serverVersion = 1_000L,
            checklists = pullSnapshot.checklists,
            items = pullSnapshot.items,
        )
    }

    override suspend fun pushMutations(session: AuthSession, batch: RemoteMutationBatch): RemotePushResult {
        pushCount += 1
        pushedSnapshots += batch.snapshot
        if (!pushStarted.isCompleted) pushStarted.complete(Unit)
        pushGate?.await()
        val snapshot = batch.snapshot
        val accepted = snapshot.copy(
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
        return RemotePushResult(
            schemaVersion = "3.1",
            accepted = accepted,
            settings = batch.settings,
            serverVersion = 2_000L,
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
