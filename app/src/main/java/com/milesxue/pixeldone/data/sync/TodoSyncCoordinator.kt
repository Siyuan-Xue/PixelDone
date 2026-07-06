package com.milesxue.pixeldone.data.sync

import android.util.Log
import com.milesxue.pixeldone.data.local.TodoChecklistEntity
import com.milesxue.pixeldone.data.local.TodoEntitySet
import com.milesxue.pixeldone.data.local.TodoItemEntity
import com.milesxue.pixeldone.domain.sync.AuthSession
import com.milesxue.pixeldone.domain.sync.ConflictResolutionSource
import com.milesxue.pixeldone.domain.sync.ConflictResolver
import com.milesxue.pixeldone.domain.sync.SyncCoordinatorStatus
import com.milesxue.pixeldone.domain.sync.SyncMergeCandidate
import com.milesxue.pixeldone.domain.sync.SyncRecordState
import com.milesxue.pixeldone.domain.todo.ClockProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class TodoSyncCoordinator(
    private val authSessionRepository: AuthSessionRepository,
    private val localStore: TodoSyncLocalStore,
    private val remoteDataSource: RemoteTodoDataSource,
    private val clockProvider: ClockProvider,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val autoSyncDebounceMillis: Long = AutoSyncDebounceMillis,
) : SyncCoordinator {
    private val syncMutex = Mutex()
    private val requestMutex = Mutex()
    private val mutableStatus = MutableStateFlow(statusFor(authSessionRepository.session.value))
    override val status: StateFlow<SyncCoordinatorStatus> = mutableStatus.asStateFlow()

    private var scheduledSyncJob: Job? = null
    private var pendingAutoSyncAfterCurrent = false
    private var observedSignedInUserId: String? = null
    private var hasObservedSession = false

    init {
        scope.launch {
            authSessionRepository.session.collectLatest { session ->
                if (mutableStatus.value != SyncCoordinatorStatus.SYNCING) {
                    mutableStatus.value = statusFor(session)
                }
                val signedInUserId = session.userId.takeIf { session.signedIn }
                val shouldTriggerSync = signedInUserId != null &&
                    (!hasObservedSession || observedSignedInUserId != signedInUserId)
                observedSignedInUserId = signedInUserId
                hasObservedSession = true
                if (shouldTriggerSync) scheduleSync(delayMillis = 0L)
            }
        }
    }

    override fun requestSync() {
        scheduleSync(delayMillis = autoSyncDebounceMillis)
    }

    override suspend fun syncNow(): SyncCoordinatorStatus = syncNowInternal(cancelScheduledSync = true)

    private suspend fun runScheduledSync(): SyncCoordinatorStatus {
        requestMutex.withLock { scheduledSyncJob = null }
        return syncNowInternal(cancelScheduledSync = false)
    }

    private suspend fun syncNowInternal(cancelScheduledSync: Boolean): SyncCoordinatorStatus {
        if (cancelScheduledSync) {
            requestMutex.withLock {
                scheduledSyncJob?.cancel()
                scheduledSyncJob = null
            }
        }
        if (!syncMutex.tryLock()) {
            requestMutex.withLock { pendingAutoSyncAfterCurrent = true }
            return SyncCoordinatorStatus.SYNCING
        }
        val result = try {
            performSyncLocked()
        } finally {
            syncMutex.unlock()
        }
        val shouldRunPending = requestMutex.withLock {
            val pending = pendingAutoSyncAfterCurrent
            pendingAutoSyncAfterCurrent = false
            pending
        }
        if (shouldRunPending) scheduleSync(delayMillis = autoSyncDebounceMillis)
        return result
    }

    private fun scheduleSync(delayMillis: Long) {
        scope.launch {
            requestMutex.withLock {
                if (syncMutex.isLocked || mutableStatus.value == SyncCoordinatorStatus.SYNCING) {
                    pendingAutoSyncAfterCurrent = true
                    return@launch
                }
                scheduledSyncJob?.cancel()
                scheduledSyncJob = scope.launch {
                    if (delayMillis > 0L) delay(delayMillis)
                    runScheduledSync()
                }
            }
        }
    }

    private suspend fun performSyncLocked(): SyncCoordinatorStatus {
        val startingSession = authSessionRepository.session.value
        val readyStatus = statusFor(startingSession)
        if (readyStatus != SyncCoordinatorStatus.IDLE && readyStatus != SyncCoordinatorStatus.SYNCED) {
            mutableStatus.value = readyStatus
            return readyStatus
        }
        val ownerUserId = requireNotNull(startingSession.userId)
        mutableStatus.value = SyncCoordinatorStatus.SYNCING
        return try {
            runSyncWithRefreshRetry(ownerUserId)
            mutableStatus.value = SyncCoordinatorStatus.SYNCED
            SyncCoordinatorStatus.SYNCED
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(LogTag, "PixelDone sync failed: ${error.safeLogMessage()}")
            markSyncError(ownerUserId, error.message ?: "Sync failed.")
            mutableStatus.value = SyncCoordinatorStatus.ERROR
            SyncCoordinatorStatus.ERROR
        }
    }

    private suspend fun runSyncWithRefreshRetry(ownerUserId: String) {
        val session = authSessionRepository.refreshSessionIfNeeded(clockProvider.nowMillis())
        try {
            runSync(session, ownerUserId)
        } catch (error: SyncRemoteException) {
            if (!error.isExpiredJwt()) throw error
            Log.i(LogTag, "Supabase access token expired during sync; refreshing and retrying once.")
            val refreshed = authSessionRepository.refreshSessionIfNeeded(
                nowMillis = clockProvider.nowMillis(),
                force = true,
            )
            runSync(refreshed, ownerUserId)
        }
    }

    private suspend fun runSync(session: AuthSession, ownerUserId: String) {
        val remote = remoteDataSource.pullSnapshot(session)
        val mergeNowMillis = clockProvider.nowMillis()
        val merged = localStore.updateEntitySetFromSync(mergeNowMillis) { latestLocal ->
            mergeSnapshots(
                local = latestLocal.withOwnerAttached(ownerUserId),
                remote = remote,
                ownerUserId = ownerUserId,
                syncedAtMillis = mergeNowMillis,
            )
        }
        val dirtySnapshot = merged.toDirtyRemoteSnapshot(ownerUserId)
        if (dirtySnapshot.checklists.isEmpty() && dirtySnapshot.items.isEmpty()) return

        val pushed = remoteDataSource.pushSnapshot(session, dirtySnapshot)
        val metadataNowMillis = clockProvider.nowMillis()
        localStore.updateEntitySetFromSync(metadataNowMillis) { latestLocal ->
            latestLocal.withOwnerAttached(ownerUserId).withPushedRemoteMetadataSafely(
                pushed = pushed,
                pushedSource = dirtySnapshot,
                ownerUserId = ownerUserId,
                syncedAtMillis = metadataNowMillis,
            )
        }
    }

    private suspend fun markSyncError(ownerUserId: String, message: String) {
        val nowMillis = clockProvider.nowMillis()
        localStore.updateEntitySetFromSync(nowMillis) { current ->
            current.copy(
                checklists = current.checklists.map { checklist ->
                    if (checklist.ownerUserId == ownerUserId && checklist.syncState != SyncRecordState.SYNCED.name) {
                        checklist.copy(syncState = SyncRecordState.ERROR.name, lastSyncError = message.take(MaxSyncErrorLength))
                    } else {
                        checklist
                    }
                },
                items = current.items.map { item ->
                    if (item.ownerUserId == ownerUserId && item.syncState != SyncRecordState.SYNCED.name) {
                        item.copy(syncState = SyncRecordState.ERROR.name, lastSyncError = message.take(MaxSyncErrorLength))
                    } else {
                        item
                    }
                },
            )
        }
    }

    private fun statusFor(session: AuthSession): SyncCoordinatorStatus = when {
        !session.cloudAvailable && session.configurationError == null -> SyncCoordinatorStatus.LOCAL_ONLY
        !session.cloudAvailable -> SyncCoordinatorStatus.NOT_CONFIGURED
        !session.signedIn -> SyncCoordinatorStatus.SIGNED_OUT
        else -> SyncCoordinatorStatus.IDLE
    }

    private fun SyncRemoteException.isExpiredJwt(): Boolean =
        statusCode == 401 ||
            message?.contains("JWT expired", ignoreCase = true) == true ||
            message?.contains("PGRST303", ignoreCase = true) == true

    private fun Throwable.safeLogMessage(): String = message
        ?.replace(Regex("Bearer\\s+[^\\s\"]+"), "Bearer <redacted>")
        ?.take(MaxSyncErrorLength)
        ?: this::class.java.simpleName

    companion object {
        private const val LogTag = "PixelDoneSync"
        private const val MaxSyncErrorLength = 280
        private const val AutoSyncDebounceMillis = 2_000L
    }
}

private fun TodoEntitySet.withOwnerAttached(ownerUserId: String): TodoEntitySet = copy(
    checklists = checklists.map { it.withOwnerAttached(ownerUserId) },
    items = items.map { it.withOwnerAttached(ownerUserId) },
)

private fun TodoChecklistEntity.withOwnerAttached(ownerUserId: String): TodoChecklistEntity {
    if (this.ownerUserId == ownerUserId) return this
    return copy(
        ownerUserId = ownerUserId,
        syncState = SyncRecordState.NOT_SYNCED.name,
        lastSyncError = null,
    )
}

private fun TodoItemEntity.withOwnerAttached(ownerUserId: String): TodoItemEntity {
    if (this.ownerUserId == ownerUserId) return this
    return copy(
        ownerUserId = ownerUserId,
        syncState = SyncRecordState.NOT_SYNCED.name,
        lastSyncError = null,
    )
}

private fun mergeSnapshots(
    local: TodoEntitySet,
    remote: RemoteTodoSnapshot,
    ownerUserId: String,
    syncedAtMillis: Long,
): TodoEntitySet {
    val remoteChecklistsById = remote.checklists.associateBy { it.localId }
    val remoteItemsById = remote.items.associateBy { it.localId }
    val localChecklistsById = local.checklists.associateBy { it.localId }
    val localItemsById = local.items.associateBy { it.localId }
    val mergedChecklistIds = (localChecklistsById.keys + remoteChecklistsById.keys).toList()
    val mergedItemIds = (localItemsById.keys + remoteItemsById.keys).toList()

    return local.copy(
        checklists = mergedChecklistIds.mapNotNull { localId ->
            mergeChecklist(
                local = localChecklistsById[localId],
                remote = remoteChecklistsById[localId],
                ownerUserId = ownerUserId,
                syncedAtMillis = syncedAtMillis,
            )
        }.sortedWith(compareBy<TodoChecklistEntity> { it.sortIndex }.thenBy { it.createdAtMillis }),
        items = mergedItemIds.mapNotNull { localId ->
            mergeItem(
                local = localItemsById[localId],
                remote = remoteItemsById[localId],
                ownerUserId = ownerUserId,
                syncedAtMillis = syncedAtMillis,
            )
        }.sortedWith(
            compareBy<TodoItemEntity> { it.checklistLocalId }
                .thenBy { it.sortIndex }
                .thenBy { it.createdAtMillis },
        ),
    )
}

private fun mergeChecklist(
    local: TodoChecklistEntity?,
    remote: RemoteChecklistRecord?,
    ownerUserId: String,
    syncedAtMillis: Long,
): TodoChecklistEntity? = when {
    local == null && remote == null -> null
    local == null -> remote!!.toEntity(syncedAtMillis)
    remote == null -> local.withOwnerAttached(ownerUserId).asDirtyIfNeeded()
    else -> {
        val ownedLocal = local.withOwnerAttached(ownerUserId)
        val resolution = ConflictResolver.resolveLastWriteWins(
            local = SyncMergeCandidate(
                value = ownedLocal,
                updatedAtMillis = ownedLocal.updatedAtMillis,
                deletedAtMillis = ownedLocal.deletedAtMillis,
                remoteVersion = ownedLocal.remoteVersion,
            ),
            remote = SyncMergeCandidate(
                value = remote,
                updatedAtMillis = remote.updatedAtMillis,
                deletedAtMillis = remote.deletedAtMillis,
                remoteVersion = remote.remoteVersion,
            ),
        )
        if (resolution.source == ConflictResolutionSource.REMOTE) {
            remote.toEntity(syncedAtMillis)
        } else {
            val localClock = ownedLocal.recordClock()
            val remoteClock = remote.recordClock()
            val needsPush = ownedLocal.syncState != SyncRecordState.SYNCED.name || localClock > remoteClock
            ownedLocal.copy(
                remoteId = remote.remoteId ?: ownedLocal.remoteId,
                remoteVersion = remote.remoteVersion ?: ownedLocal.remoteVersion,
                syncState = if (needsPush) SyncRecordState.NOT_SYNCED.name else SyncRecordState.SYNCED.name,
                lastSyncedAtMillis = if (needsPush) ownedLocal.lastSyncedAtMillis else syncedAtMillis,
                lastSyncError = null,
            )
        }
    }
}

private fun mergeItem(
    local: TodoItemEntity?,
    remote: RemoteTodoItemRecord?,
    ownerUserId: String,
    syncedAtMillis: Long,
): TodoItemEntity? = when {
    local == null && remote == null -> null
    local == null -> remote!!.toEntity(syncedAtMillis)
    remote == null -> local.withOwnerAttached(ownerUserId).asDirtyIfNeeded()
    else -> {
        val ownedLocal = local.withOwnerAttached(ownerUserId)
        val resolution = ConflictResolver.resolveLastWriteWins(
            local = SyncMergeCandidate(
                value = ownedLocal,
                updatedAtMillis = ownedLocal.updatedAtMillis,
                deletedAtMillis = ownedLocal.deletedAtMillis,
                remoteVersion = ownedLocal.remoteVersion,
            ),
            remote = SyncMergeCandidate(
                value = remote,
                updatedAtMillis = remote.updatedAtMillis,
                deletedAtMillis = remote.deletedAtMillis,
                remoteVersion = remote.remoteVersion,
            ),
        )
        if (resolution.source == ConflictResolutionSource.REMOTE) {
            remote.toEntity(
                syncedAtMillis = syncedAtMillis,
                locallyPurgedAtMillis = ownedLocal.locallyPurgedAtMillis.takeIf { remote.deletedAtMillis != null },
            )
        } else {
            val localClock = ownedLocal.recordClock()
            val remoteClock = remote.recordClock()
            val needsPush = ownedLocal.syncState != SyncRecordState.SYNCED.name || localClock > remoteClock
            ownedLocal.copy(
                remoteId = remote.remoteId ?: ownedLocal.remoteId,
                remoteVersion = remote.remoteVersion ?: ownedLocal.remoteVersion,
                syncState = if (needsPush) SyncRecordState.NOT_SYNCED.name else SyncRecordState.SYNCED.name,
                lastSyncedAtMillis = if (needsPush) ownedLocal.lastSyncedAtMillis else syncedAtMillis,
                lastSyncError = null,
            )
        }
    }
}

private fun TodoChecklistEntity.asDirtyIfNeeded(): TodoChecklistEntity =
    if (syncState == SyncRecordState.SYNCED.name) this else copy(syncState = SyncRecordState.NOT_SYNCED.name)

private fun TodoItemEntity.asDirtyIfNeeded(): TodoItemEntity =
    if (syncState == SyncRecordState.SYNCED.name) this else copy(syncState = SyncRecordState.NOT_SYNCED.name)

private fun RemoteChecklistRecord.toEntity(syncedAtMillis: Long): TodoChecklistEntity = TodoChecklistEntity(
    localId = localId,
    sortIndex = sortIndex,
    remoteId = remoteId,
    ownerUserId = ownerUserId,
    name = name,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
    deletedAtMillis = deletedAtMillis,
    syncState = SyncRecordState.SYNCED.name,
    lastSyncedAtMillis = syncedAtMillis,
    remoteVersion = remoteVersion,
    lastSyncError = null,
)

private fun RemoteTodoItemRecord.toEntity(
    syncedAtMillis: Long,
    locallyPurgedAtMillis: Long? = null,
): TodoItemEntity = TodoItemEntity(
    localId = localId,
    checklistLocalId = checklistLocalId,
    sortIndex = sortIndex,
    remoteId = remoteId,
    ownerUserId = ownerUserId,
    title = title,
    priority = priority,
    dueAtMillis = dueAtMillis,
    completed = completed,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
    deletedAtMillis = deletedAtMillis,
    reminderRepeat = reminderRepeat,
    imageLocalName = imageLocalName,
    imageRemotePath = imageRemotePath,
    imageSyncState = imageSyncState,
    trashedFromChecklistId = trashedFromChecklistId,
    trashedFromChecklistName = trashedFromChecklistName,
    trashedAtMillis = trashedAtMillis,
    locallyPurgedAtMillis = locallyPurgedAtMillis,
    syncState = SyncRecordState.SYNCED.name,
    lastSyncedAtMillis = syncedAtMillis,
    remoteVersion = remoteVersion,
    lastSyncError = null,
)

private fun TodoEntitySet.toDirtyRemoteSnapshot(ownerUserId: String): RemoteTodoSnapshot = RemoteTodoSnapshot(
    checklists = checklists
        .filter { it.ownerUserId == ownerUserId && it.syncState != SyncRecordState.SYNCED.name }
        .map { it.toRemoteRecord(ownerUserId) },
    items = items
        .filter { it.ownerUserId == ownerUserId && it.syncState != SyncRecordState.SYNCED.name }
        .map { it.toRemoteRecord(ownerUserId) },
)

private fun TodoChecklistEntity.toRemoteRecord(ownerUserId: String): RemoteChecklistRecord = RemoteChecklistRecord(
    localId = localId,
    remoteId = remoteId,
    ownerUserId = ownerUserId,
    sortIndex = sortIndex,
    name = name,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
    deletedAtMillis = deletedAtMillis,
    remoteVersion = remoteVersion,
)

private fun TodoItemEntity.toRemoteRecord(ownerUserId: String): RemoteTodoItemRecord = RemoteTodoItemRecord(
    localId = localId,
    remoteId = remoteId,
    ownerUserId = ownerUserId,
    checklistLocalId = checklistLocalId,
    sortIndex = sortIndex,
    title = title,
    priority = priority,
    dueAtMillis = dueAtMillis,
    completed = completed,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
    deletedAtMillis = deletedAtMillis,
    reminderRepeat = reminderRepeat,
    imageLocalName = imageLocalName,
    imageRemotePath = imageRemotePath,
    imageSyncState = imageSyncState,
    trashedFromChecklistId = trashedFromChecklistId,
    trashedFromChecklistName = trashedFromChecklistName,
    trashedAtMillis = trashedAtMillis,
    remoteVersion = remoteVersion,
)

private fun TodoEntitySet.withPushedRemoteMetadataSafely(
    pushed: RemoteTodoSnapshot,
    pushedSource: RemoteTodoSnapshot,
    ownerUserId: String,
    syncedAtMillis: Long,
): TodoEntitySet {
    val pushedChecklistsById = pushed.checklists.associateBy { it.localId }
    val pushedItemsById = pushed.items.associateBy { it.localId }
    val sourceChecklistsById = pushedSource.checklists.associateBy { it.localId }
    val sourceItemsById = pushedSource.items.associateBy { it.localId }
    return copy(
        checklists = checklists.map { checklist ->
            val remote = pushedChecklistsById[checklist.localId]
            val source = sourceChecklistsById[checklist.localId]
            if (checklist.ownerUserId == ownerUserId && remote != null && source != null) {
                val unchangedSincePush = checklist.hasSameSyncPayloadAs(source, ownerUserId)
                checklist.copy(
                    remoteId = remote.remoteId ?: checklist.remoteId,
                    remoteVersion = remote.remoteVersion ?: checklist.remoteVersion,
                    syncState = if (unchangedSincePush) SyncRecordState.SYNCED.name else SyncRecordState.NOT_SYNCED.name,
                    lastSyncedAtMillis = if (unchangedSincePush) syncedAtMillis else checklist.lastSyncedAtMillis,
                    lastSyncError = null,
                )
            } else {
                checklist
            }
        },
        items = items.map { item ->
            val remote = pushedItemsById[item.localId]
            val source = sourceItemsById[item.localId]
            if (item.ownerUserId == ownerUserId && remote != null && source != null) {
                val unchangedSincePush = item.hasSameSyncPayloadAs(source, ownerUserId)
                item.copy(
                    remoteId = remote.remoteId ?: item.remoteId,
                    remoteVersion = remote.remoteVersion ?: item.remoteVersion,
                    syncState = if (unchangedSincePush) SyncRecordState.SYNCED.name else SyncRecordState.NOT_SYNCED.name,
                    lastSyncedAtMillis = if (unchangedSincePush) syncedAtMillis else item.lastSyncedAtMillis,
                    lastSyncError = null,
                )
            } else {
                item
            }
        },
    )
}

private fun TodoChecklistEntity.hasSameSyncPayloadAs(record: RemoteChecklistRecord, ownerUserId: String): Boolean =
    toRemoteRecord(ownerUserId).hasSameSyncPayloadAs(record)

private fun TodoItemEntity.hasSameSyncPayloadAs(record: RemoteTodoItemRecord, ownerUserId: String): Boolean =
    toRemoteRecord(ownerUserId).hasSameSyncPayloadAs(record)

private fun RemoteChecklistRecord.hasSameSyncPayloadAs(other: RemoteChecklistRecord): Boolean =
    localId == other.localId &&
        ownerUserId == other.ownerUserId &&
        sortIndex == other.sortIndex &&
        name == other.name &&
        createdAtMillis == other.createdAtMillis &&
        updatedAtMillis == other.updatedAtMillis &&
        deletedAtMillis == other.deletedAtMillis

private fun RemoteTodoItemRecord.hasSameSyncPayloadAs(other: RemoteTodoItemRecord): Boolean =
    localId == other.localId &&
        ownerUserId == other.ownerUserId &&
        checklistLocalId == other.checklistLocalId &&
        sortIndex == other.sortIndex &&
        title == other.title &&
        priority == other.priority &&
        dueAtMillis == other.dueAtMillis &&
        completed == other.completed &&
        createdAtMillis == other.createdAtMillis &&
        updatedAtMillis == other.updatedAtMillis &&
        deletedAtMillis == other.deletedAtMillis &&
        reminderRepeat == other.reminderRepeat &&
        imageLocalName == other.imageLocalName &&
        imageRemotePath == other.imageRemotePath &&
        imageSyncState == other.imageSyncState &&
        trashedFromChecklistId == other.trashedFromChecklistId &&
        trashedFromChecklistName == other.trashedFromChecklistName &&
        trashedAtMillis == other.trashedAtMillis

private fun TodoChecklistEntity.recordClock(): Long = deletedAtMillis?.coerceAtLeast(updatedAtMillis) ?: updatedAtMillis
private fun RemoteChecklistRecord.recordClock(): Long = deletedAtMillis?.coerceAtLeast(updatedAtMillis) ?: updatedAtMillis
private fun TodoItemEntity.recordClock(): Long = deletedAtMillis?.coerceAtLeast(updatedAtMillis) ?: updatedAtMillis
private fun RemoteTodoItemRecord.recordClock(): Long = deletedAtMillis?.coerceAtLeast(updatedAtMillis) ?: updatedAtMillis