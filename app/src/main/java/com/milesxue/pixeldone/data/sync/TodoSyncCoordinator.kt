package com.milesxue.pixeldone.data.sync

import android.util.Log
import com.milesxue.pixeldone.data.local.SyncRecordTypeChecklist
import com.milesxue.pixeldone.data.local.SyncRecordTypeItem
import com.milesxue.pixeldone.data.local.TodoChecklistEntity
import com.milesxue.pixeldone.data.local.TodoEntitySet
import com.milesxue.pixeldone.data.local.TodoItemEntity
import com.milesxue.pixeldone.domain.sync.AuthSession
import com.milesxue.pixeldone.domain.sync.ConflictResolutionChoice
import com.milesxue.pixeldone.domain.sync.ConflictResolutionSource
import com.milesxue.pixeldone.domain.sync.ConflictResolver
import com.milesxue.pixeldone.domain.sync.SyncConflictEntry
import com.milesxue.pixeldone.domain.sync.SyncConflictValue
import com.milesxue.pixeldone.domain.sync.SyncCoordinatorStatus
import com.milesxue.pixeldone.domain.sync.SyncMergeCandidate
import com.milesxue.pixeldone.domain.sync.SyncRecordState
import com.milesxue.pixeldone.domain.sync.SyncRunState
import com.milesxue.pixeldone.domain.todo.ClockProvider
import com.milesxue.pixeldone.domain.todo.TrashChecklistId
import com.milesxue.pixeldone.domain.todo.TrashChecklistName
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class TodoSyncCoordinator(
    private val authSessionRepository: AuthSessionRepository,
    private val localStore: TodoSyncLocalStore,
    private val remoteDataSource: RemoteTodoDataSource,
    private val clockProvider: ClockProvider,
    private val workScheduler: SyncWorkScheduler = NoOpSyncWorkScheduler,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val autoSyncDebounceMillis: Long = AutoSyncDebounceMillis,
) : SyncCoordinator {
    private val syncMutex = Mutex()
    private val requestMutex = Mutex()
    private val mutableStatus = MutableStateFlow(statusFor(authSessionRepository.session.value))
    private val mutableRunState = MutableStateFlow(SyncRunState(status = mutableStatus.value))
    override val status: StateFlow<SyncCoordinatorStatus> = mutableStatus.asStateFlow()
    override val runState: StateFlow<SyncRunState> = mutableRunState.asStateFlow()

    private var scheduledSyncJob: Job? = null
    private var pendingAutoSyncAfterCurrent = false
    private var observedSignedInUserId: String? = null
    private var hasObservedSession = false

    init {
        workScheduler.ensurePeriodicSync()
        scope.launch {
            authSessionRepository.session.collectLatest { session ->
                if (mutableStatus.value != SyncCoordinatorStatus.SYNCING) {
                    setRunState(mutableRunState.value.copy(status = statusFor(session), lastError = session.configurationError))
                }
                val signedInUserId = session.userId.takeIf { session.signedIn }
                val shouldTriggerSync = signedInUserId != null &&
                    (!hasObservedSession || observedSignedInUserId != signedInUserId)
                observedSignedInUserId = signedInUserId
                hasObservedSession = true
                if (shouldTriggerSync) scheduleSync(delayMillis = 0L, enqueueWork = false)
            }
        }
    }

    override fun requestSync() {
        scheduleSync(delayMillis = autoSyncDebounceMillis, enqueueWork = true)
    }

    override suspend fun syncNow(): SyncCoordinatorStatus = syncNowInternal(cancelScheduledSync = true)

    override suspend fun loadConflicts(): List<SyncConflictEntry> {
        val session = authSessionRepository.session.value
        val ownerUserId = session.userId.takeIf { session.signedIn } ?: return emptyList()
        return localStore.loadConflicts(ownerUserId).mapNotNull { it.toSyncConflictEntry() }
    }

    override suspend fun resolveConflict(
        recordType: String,
        localId: String,
        choice: ConflictResolutionChoice,
    ): SyncCoordinatorStatus {
        val session = authSessionRepository.session.value
        val ownerUserId = session.userId.takeIf { session.signedIn }
            ?: return statusFor(session).also { setRunState(mutableRunState.value.copy(status = it)) }
        val needsUpload = syncMutex.withLock {
            val conflict = localStore.loadConflict(ownerUserId, recordType, localId)
                ?: return@withLock false
            val nowMillis = clockProvider.nowMillis()
            val applied = when (choice) {
                ConflictResolutionChoice.KEEP_LOCAL -> applyLocalConflictVersion(
                    ownerUserId = ownerUserId,
                    conflict = conflict,
                    syncedAtMillis = nowMillis,
                )
                ConflictResolutionChoice.KEEP_CLOUD -> applyCloudConflictVersion(
                    ownerUserId = ownerUserId,
                    conflict = conflict,
                    syncedAtMillis = nowMillis,
                )
            }
            if (applied) {
                localStore.clearConflict(ownerUserId, recordType, localId)
            }
            if (choice == ConflictResolutionChoice.KEEP_CLOUD && applied) {
                savePristineFromCurrentSyncedRecords(ownerUserId, nowMillis)
            }
            choice == ConflictResolutionChoice.KEEP_LOCAL && applied
        }
        return if (needsUpload) {
            syncNowInternal(cancelScheduledSync = true)
        } else {
            refreshResolvedConflictRunState(ownerUserId)
        }
    }

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
        if (shouldRunPending) scheduleSync(delayMillis = autoSyncDebounceMillis, enqueueWork = false)
        return result
    }

    private fun scheduleSync(delayMillis: Long, enqueueWork: Boolean) {
        if (enqueueWork) workScheduler.requestSync()
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
            setRunState(mutableRunState.value.copy(status = readyStatus, lastError = startingSession.configurationError))
            return readyStatus
        }
        val ownerUserId = requireNotNull(startingSession.userId)
        setRunState(mutableRunState.value.copy(status = SyncCoordinatorStatus.SYNCING, lastError = null))
        return try {
            val result = runSyncWithRefreshRetry(ownerUserId)
            val finalStatus = if (result.conflictCount > 0) SyncCoordinatorStatus.CONFLICT else SyncCoordinatorStatus.SYNCED
            setRunState(
                SyncRunState(
                    status = finalStatus,
                    lastSyncedAtMillis = clockProvider.nowMillis(),
                    pendingCount = result.pendingCount,
                    conflictCount = result.conflictCount,
                    lastError = null,
                ),
            )
            finalStatus
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(LogTag, "PixelDone sync failed: ${error.safeLogMessage()}")
            markSyncError(ownerUserId, error.message ?: "Sync failed.")
            val pendingCount = localStore.loadPendingMutations(ownerUserId).size
            setRunState(
                mutableRunState.value.copy(
                    status = SyncCoordinatorStatus.ERROR,
                    pendingCount = pendingCount,
                    lastError = error.message ?: "Sync failed.",
                ),
            )
            SyncCoordinatorStatus.ERROR
        }
    }

    private suspend fun runSyncWithRefreshRetry(ownerUserId: String): SyncResult {
        val session = authSessionRepository.refreshSessionIfNeeded(clockProvider.nowMillis())
        try {
            return runSync(session, ownerUserId)
        } catch (error: SyncRemoteException) {
            if (!error.isExpiredJwt()) throw error
            Log.i(LogTag, "Supabase access token expired during sync; refreshing and retrying once.")
            val refreshed = authSessionRepository.refreshSessionIfNeeded(
                nowMillis = clockProvider.nowMillis(),
                force = true,
            )
            return runSync(refreshed, ownerUserId)
        }
    }

    private suspend fun runSync(session: AuthSession, ownerUserId: String): SyncResult {
        val sinceVersion = localStore.loadSyncCursor(ownerUserId)
        val remoteChanges = remoteDataSource.pullChanges(session, sinceVersion)
        val pristine = localStore.loadPristineSnapshot(ownerUserId)
        val mergeNowMillis = clockProvider.nowMillis()
        val mergeResult = MergeAccumulator()
        localStore.updateEntitySetFromSync(mergeNowMillis) { latestLocal ->
            mergeSnapshots(
                local = latestLocal.withOwnerAttached(ownerUserId),
                remote = remoteChanges.toSnapshot(),
                pristine = pristine,
                ownerUserId = ownerUserId,
                syncedAtMillis = mergeNowMillis,
                mergeResult = mergeResult,
            )
        }
        mergeResult.conflicts.forEach { conflict ->
            localStore.recordConflict(ownerUserId, conflict)
        }

        val pendingBeforeNew = localStore.loadPendingMutations(ownerUserId)
        if (pendingBeforeNew.isEmpty()) createPendingMutationIfNeeded(ownerUserId)
        val pendingMutations = localStore.loadPendingMutations(ownerUserId)
        var latestServerVersion = remoteChanges.serverVersion
        for (mutation in pendingMutations) {
            val result = remoteDataSource.pushMutations(
                session = session,
                batch = RemoteMutationBatch(
                    mutationUuid = mutation.mutationUuid,
                    snapshot = mutation.snapshot,
                ),
            )
            val metadataNowMillis = clockProvider.nowMillis()
            localStore.updateEntitySetFromSync(metadataNowMillis) { latestLocal ->
                latestLocal.withOwnerAttached(ownerUserId).withPushedRemoteMetadataSafely(
                    pushed = result.accepted,
                    pushedSource = mutation.snapshot,
                    ownerUserId = ownerUserId,
                    syncedAtMillis = metadataNowMillis,
                )
            }
            localStore.clearPendingMutation(ownerUserId, mutation.mutationUuid)
            latestServerVersion = latestServerVersion.coerceAtLeast(result.serverVersion)
            localStore.saveSyncCursor(ownerUserId, latestServerVersion, metadataNowMillis)
        }

        val finalNowMillis = clockProvider.nowMillis()
        localStore.saveSyncCursor(ownerUserId, latestServerVersion, finalNowMillis)
        val latest = localStore.loadEntitySetForSync(finalNowMillis).withOwnerAttached(ownerUserId)
        localStore.savePristineSnapshot(
            ownerUserId = ownerUserId,
            snapshot = latest.toRemoteSnapshotForPristine(ownerUserId),
            syncedAtMillis = finalNowMillis,
        )
        val pendingCount = localStore.loadPendingMutations(ownerUserId).size
        val conflictCount = latest.conflictCount()
        return SyncResult(pendingCount = pendingCount, conflictCount = conflictCount)
    }

    private suspend fun applyLocalConflictVersion(
        ownerUserId: String,
        conflict: LocalSyncConflictRecord,
        syncedAtMillis: Long,
    ): Boolean = when (conflict.recordType) {
        SyncRecordTypeChecklist -> {
            val record = conflict.decodeLocalChecklist() ?: return false
            localStore.updateEntitySetFromSync(syncedAtMillis) { latest ->
                latest.copy(
                    checklists = latest.checklists.map { checklist ->
                        if (checklist.ownerUserId == ownerUserId && checklist.localId == conflict.localId) {
                            record.toEntityForResolution(syncedAtMillis, SyncRecordState.NOT_SYNCED)
                        } else {
                            checklist
                        }
                    },
                )
            }
            true
        }
        SyncRecordTypeItem -> {
            val record = conflict.decodeLocalItem() ?: return false
            localStore.updateEntitySetFromSync(syncedAtMillis) { latest ->
                latest.copy(
                    items = latest.items.map { item ->
                        if (item.ownerUserId == ownerUserId && item.localId == conflict.localId) {
                            record.toEntityForResolution(syncedAtMillis, SyncRecordState.NOT_SYNCED)
                        } else {
                            item
                        }
                    },
                )
            }
            true
        }
        else -> false
    }

    private suspend fun applyCloudConflictVersion(
        ownerUserId: String,
        conflict: LocalSyncConflictRecord,
        syncedAtMillis: Long,
    ): Boolean = when (conflict.recordType) {
        SyncRecordTypeChecklist -> {
            val record = conflict.decodeRemoteChecklist() ?: return false
            localStore.updateEntitySetFromSync(syncedAtMillis) { latest ->
                latest.copy(
                    checklists = latest.checklists.map { checklist ->
                        if (checklist.ownerUserId == ownerUserId && checklist.localId == conflict.localId) {
                            record.toEntityForResolution(syncedAtMillis, SyncRecordState.SYNCED)
                        } else {
                            checklist
                        }
                    },
                )
            }
            true
        }
        SyncRecordTypeItem -> {
            val record = conflict.decodeRemoteItem() ?: return false
            localStore.updateEntitySetFromSync(syncedAtMillis) { latest ->
                latest.copy(
                    items = latest.items.map { item ->
                        if (item.ownerUserId == ownerUserId && item.localId == conflict.localId) {
                            record.toEntityForResolution(syncedAtMillis, SyncRecordState.SYNCED)
                        } else {
                            item
                        }
                    },
                )
            }
            true
        }
        else -> false
    }

    private suspend fun savePristineFromCurrentSyncedRecords(ownerUserId: String, syncedAtMillis: Long) {
        val latest = localStore.loadEntitySetForSync(syncedAtMillis).withOwnerAttached(ownerUserId)
        localStore.savePristineSnapshot(
            ownerUserId = ownerUserId,
            snapshot = latest.toRemoteSnapshotForPristine(ownerUserId),
            syncedAtMillis = syncedAtMillis,
        )
    }

    private suspend fun refreshResolvedConflictRunState(ownerUserId: String): SyncCoordinatorStatus {
        val nowMillis = clockProvider.nowMillis()
        val latest = localStore.loadEntitySetForSync(nowMillis).withOwnerAttached(ownerUserId)
        val pendingCount = localStore.loadPendingMutations(ownerUserId).size
        val conflictCount = latest.conflictCount()
        val finalStatus = if (conflictCount > 0) SyncCoordinatorStatus.CONFLICT else SyncCoordinatorStatus.SYNCED
        setRunState(
            mutableRunState.value.copy(
                status = finalStatus,
                pendingCount = pendingCount,
                conflictCount = conflictCount,
                lastError = null,
            ),
        )
        return finalStatus
    }

    private suspend fun createPendingMutationIfNeeded(ownerUserId: String) {
        val nowMillis = clockProvider.nowMillis()
        val latest = localStore.loadEntitySetForSync(nowMillis).withOwnerAttached(ownerUserId)
        val dirtySnapshot = latest.toDirtyRemoteSnapshot(ownerUserId)
        if (dirtySnapshot.checklists.isEmpty() && dirtySnapshot.items.isEmpty()) return
        localStore.recordPendingMutation(
            ownerUserId = ownerUserId,
            mutation = SyncMutationRecord(
                mutationUuid = UUID.randomUUID().toString(),
                snapshot = dirtySnapshot,
                createdAtMillis = nowMillis,
            ),
        )
    }

    private suspend fun markSyncError(ownerUserId: String, message: String) {
        val nowMillis = clockProvider.nowMillis()
        localStore.updateEntitySetFromSync(nowMillis) { current ->
            current.copy(
                checklists = current.checklists.map { checklist ->
                    if (checklist.ownerUserId == ownerUserId && checklist.syncState.isRetryableSyncState()) {
                        checklist.copy(syncState = SyncRecordState.ERROR.name, lastSyncError = message.take(MaxSyncErrorLength))
                    } else {
                        checklist
                    }
                },
                items = current.items.map { item ->
                    if (item.ownerUserId == ownerUserId && item.syncState.isRetryableSyncState()) {
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

    private fun setRunState(state: SyncRunState) {
        mutableRunState.value = state
        mutableStatus.value = state.status
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

private data class SyncResult(
    val pendingCount: Int,
    val conflictCount: Int,
)

private class MergeAccumulator {
    var conflictCount: Int = 0
    val conflicts = mutableListOf<LocalSyncConflictRecord>()
}

private fun RemoteChangeBatch.toSnapshot(): RemoteTodoSnapshot = RemoteTodoSnapshot(
    checklists = checklists,
    items = items,
)

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
    pristine: RemoteTodoSnapshot,
    ownerUserId: String,
    syncedAtMillis: Long,
    mergeResult: MergeAccumulator,
): TodoEntitySet {
    val remoteChecklistsById = remote.checklists.associateBy { it.localId }
    val remoteItemsById = remote.items.associateBy { it.localId }
    val pristineChecklistsById = pristine.checklists.associateBy { it.localId }
    val pristineItemsById = pristine.items.associateBy { it.localId }
    val localChecklistsById = local.checklists.associateBy { it.localId }
    val localItemsById = local.items.associateBy { it.localId }
    val mergedChecklistIds = (localChecklistsById.keys + remoteChecklistsById.keys).toList()
    val mergedItemIds = (localItemsById.keys + remoteItemsById.keys).toList()

    return local.copy(
        checklists = mergedChecklistIds.mapNotNull { localId ->
            mergeChecklist(
                local = localChecklistsById[localId],
                remote = remoteChecklistsById[localId],
                pristine = pristineChecklistsById[localId],
                ownerUserId = ownerUserId,
                syncedAtMillis = syncedAtMillis,
                mergeResult = mergeResult,
            )
        }.sortedWith(compareBy<TodoChecklistEntity> { it.sortIndex }.thenBy { it.createdAtMillis }),
        items = mergedItemIds.mapNotNull { localId ->
            mergeItem(
                local = localItemsById[localId],
                remote = remoteItemsById[localId],
                pristine = pristineItemsById[localId],
                ownerUserId = ownerUserId,
                syncedAtMillis = syncedAtMillis,
                mergeResult = mergeResult,
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
    pristine: RemoteChecklistRecord?,
    ownerUserId: String,
    syncedAtMillis: Long,
    mergeResult: MergeAccumulator,
): TodoChecklistEntity? = when {
    local == null && remote == null -> null
    local == null -> remote!!.toEntity(syncedAtMillis)
    remote == null -> local.withOwnerAttached(ownerUserId).asDirtyIfNeeded()
    else -> {
        val ownedLocal = local.withOwnerAttached(ownerUserId)
        val conflictFields = if (pristine != null && ownedLocal.syncState.isRetryableSyncState()) {
            ownedLocal.changedFieldsFrom(pristine).intersect(remote.changedFieldsFrom(pristine))
        } else {
            emptySet()
        }
        if (conflictFields.isNotEmpty()) {
            val sortedFields = conflictFields.sorted()
            mergeResult.conflictCount += 1
            mergeResult.conflicts += LocalSyncConflictRecord(
                recordType = SyncRecordTypeChecklist,
                localId = ownedLocal.localId,
                localPayloadJson = SyncPayloadJson.encodeToString(ownedLocal.toRemoteRecord(ownerUserId)),
                remotePayloadJson = SyncPayloadJson.encodeToString(remote),
                fields = sortedFields,
                message = "Conflict: ${sortedFields.joinToString(",")}",
                remoteVersion = remote.remoteVersion,
                createdAtMillis = syncedAtMillis,
            )
            ownedLocal.copy(
                remoteId = remote.remoteId ?: ownedLocal.remoteId,
                remoteVersion = remote.remoteVersion ?: ownedLocal.remoteVersion,
                syncState = SyncRecordState.CONFLICT.name,
                lastSyncError = "Conflict: ${sortedFields.joinToString(",")}",
            )
        } else {
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
}

private fun mergeItem(
    local: TodoItemEntity?,
    remote: RemoteTodoItemRecord?,
    pristine: RemoteTodoItemRecord?,
    ownerUserId: String,
    syncedAtMillis: Long,
    mergeResult: MergeAccumulator,
): TodoItemEntity? = when {
    local == null && remote == null -> null
    local == null -> remote!!.toEntity(syncedAtMillis)
    remote == null -> local.withOwnerAttached(ownerUserId).asDirtyIfNeeded()
    else -> {
        val ownedLocal = local.withOwnerAttached(ownerUserId)
        val conflictFields = if (pristine != null && ownedLocal.syncState.isRetryableSyncState()) {
            ownedLocal.changedFieldsFrom(pristine).intersect(remote.changedFieldsFrom(pristine))
        } else {
            emptySet()
        }
        val hasDeleteEditConflict = ownedLocal.hasDeleteEditConflict(remote)
        if (conflictFields.isNotEmpty() || hasDeleteEditConflict) {
            val sortedFields = (conflictFields + listOfNotNull("delete".takeIf { hasDeleteEditConflict })).sorted()
            mergeResult.conflictCount += 1
            mergeResult.conflicts += LocalSyncConflictRecord(
                recordType = SyncRecordTypeItem,
                localId = ownedLocal.localId,
                localPayloadJson = SyncPayloadJson.encodeToString(ownedLocal.toRemoteRecord(ownerUserId)),
                remotePayloadJson = SyncPayloadJson.encodeToString(remote),
                fields = sortedFields,
                message = "Conflict: ${sortedFields.joinToString(",")}",
                remoteVersion = remote.remoteVersion,
                createdAtMillis = syncedAtMillis,
            )
            ownedLocal.moveToTrashForRemoteDelete(remote).copy(
                remoteId = remote.remoteId ?: ownedLocal.remoteId,
                remoteVersion = remote.remoteVersion ?: ownedLocal.remoteVersion,
                syncState = SyncRecordState.CONFLICT.name,
                lastSyncError = "Conflict: ${sortedFields.joinToString(",")}",
            )
        } else {
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
}

private fun TodoChecklistEntity.asDirtyIfNeeded(): TodoChecklistEntity =
    if (syncState == SyncRecordState.SYNCED.name) this else copy(syncState = SyncRecordState.NOT_SYNCED.name)

private fun TodoItemEntity.asDirtyIfNeeded(): TodoItemEntity =
    if (syncState == SyncRecordState.SYNCED.name) this else copy(syncState = SyncRecordState.NOT_SYNCED.name)

private fun TodoChecklistEntity.changedFieldsFrom(pristine: RemoteChecklistRecord): Set<String> = buildSet {
    if (sortIndex != pristine.sortIndex) add("sort")
    if (name != pristine.name) add("name")
    if (deletedAtMillis != pristine.deletedAtMillis) add("delete")
}

private fun RemoteChecklistRecord.changedFieldsFrom(pristine: RemoteChecklistRecord): Set<String> = buildSet {
    if (sortIndex != pristine.sortIndex) add("sort")
    if (name != pristine.name) add("name")
    if (deletedAtMillis != pristine.deletedAtMillis) add("delete")
}

private fun TodoItemEntity.changedFieldsFrom(pristine: RemoteTodoItemRecord): Set<String> = buildSet {
    if (checklistLocalId != pristine.checklistLocalId) add("checklist")
    if (sortIndex != pristine.sortIndex) add("sort")
    if (title != pristine.title) add("title")
    if (priority != pristine.priority) add("priority")
    if (dueAtMillis != pristine.dueAtMillis) add("due")
    if (completed != pristine.completed) add("completed")
    if (reminderRepeat != pristine.reminderRepeat) add("repeat")
    if (imageLocalName != pristine.imageLocalName || imageRemotePath != pristine.imageRemotePath) add("image")
    if (deletedAtMillis != pristine.deletedAtMillis || trashedAtMillis != pristine.trashedAtMillis) add("delete")
}

private fun RemoteTodoItemRecord.changedFieldsFrom(pristine: RemoteTodoItemRecord): Set<String> = buildSet {
    if (checklistLocalId != pristine.checklistLocalId) add("checklist")
    if (sortIndex != pristine.sortIndex) add("sort")
    if (title != pristine.title) add("title")
    if (priority != pristine.priority) add("priority")
    if (dueAtMillis != pristine.dueAtMillis) add("due")
    if (completed != pristine.completed) add("completed")
    if (reminderRepeat != pristine.reminderRepeat) add("repeat")
    if (imageLocalName != pristine.imageLocalName || imageRemotePath != pristine.imageRemotePath) add("image")
    if (deletedAtMillis != pristine.deletedAtMillis || trashedAtMillis != pristine.trashedAtMillis) add("delete")
}

private fun TodoItemEntity.hasDeleteEditConflict(remote: RemoteTodoItemRecord): Boolean =
    remote.deletedAtMillis != null && deletedAtMillis == null && syncState.isRetryableSyncState()

private fun TodoItemEntity.moveToTrashForRemoteDelete(remote: RemoteTodoItemRecord): TodoItemEntity {
    val remoteDeletedAt = remote.deletedAtMillis ?: return this
    return copy(
        checklistLocalId = TrashChecklistId,
        deletedAtMillis = remoteDeletedAt,
        trashedAtMillis = trashedAtMillis ?: remoteDeletedAt,
        trashedFromChecklistId = trashedFromChecklistId ?: checklistLocalId,
        trashedFromChecklistName = trashedFromChecklistName ?: TrashChecklistName,
    )
}

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
        .filter { it.ownerUserId == ownerUserId && it.syncState.isRetryableSyncState() }
        .map { it.toRemoteRecord(ownerUserId) },
    items = items
        .filter { it.ownerUserId == ownerUserId && it.syncState.isRetryableSyncState() }
        .map { it.toRemoteRecord(ownerUserId) },
)

private fun TodoEntitySet.toRemoteSnapshotForPristine(ownerUserId: String): RemoteTodoSnapshot = RemoteTodoSnapshot(
    checklists = checklists
        .filter { it.ownerUserId == ownerUserId && it.syncState == SyncRecordState.SYNCED.name }
        .map { it.toRemoteRecord(ownerUserId) },
    items = items
        .filter { it.ownerUserId == ownerUserId && it.syncState == SyncRecordState.SYNCED.name }
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

private fun TodoEntitySet.conflictCount(): Int =
    checklists.count { it.syncState == SyncRecordState.CONFLICT.name } +
        items.count { it.syncState == SyncRecordState.CONFLICT.name }

private fun String.isRetryableSyncState(): Boolean =
    this == SyncRecordState.NOT_SYNCED.name || this == SyncRecordState.ERROR.name

private fun TodoChecklistEntity.recordClock(): Long = deletedAtMillis?.coerceAtLeast(updatedAtMillis) ?: updatedAtMillis
private fun RemoteChecklistRecord.recordClock(): Long = deletedAtMillis?.coerceAtLeast(updatedAtMillis) ?: updatedAtMillis
private fun TodoItemEntity.recordClock(): Long = deletedAtMillis?.coerceAtLeast(updatedAtMillis) ?: updatedAtMillis
private fun RemoteTodoItemRecord.recordClock(): Long = deletedAtMillis?.coerceAtLeast(updatedAtMillis) ?: updatedAtMillis


private val SyncPayloadJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

private val ConflictDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private fun LocalSyncConflictRecord.decodeLocalChecklist(): RemoteChecklistRecord? =
    runCatching { SyncPayloadJson.decodeFromString<RemoteChecklistRecord>(localPayloadJson) }.getOrNull()

private fun LocalSyncConflictRecord.decodeRemoteChecklist(): RemoteChecklistRecord? =
    runCatching { SyncPayloadJson.decodeFromString<RemoteChecklistRecord>(remotePayloadJson) }.getOrNull()

private fun LocalSyncConflictRecord.decodeLocalItem(): RemoteTodoItemRecord? =
    runCatching { SyncPayloadJson.decodeFromString<RemoteTodoItemRecord>(localPayloadJson) }.getOrNull()

private fun LocalSyncConflictRecord.decodeRemoteItem(): RemoteTodoItemRecord? =
    runCatching { SyncPayloadJson.decodeFromString<RemoteTodoItemRecord>(remotePayloadJson) }.getOrNull()

private fun LocalSyncConflictRecord.toSyncConflictEntry(): SyncConflictEntry? = when (recordType) {
    SyncRecordTypeChecklist -> {
        val local = decodeLocalChecklist() ?: return null
        val remote = decodeRemoteChecklist() ?: return null
        SyncConflictEntry(
            recordType = recordType,
            localId = localId,
            title = firstNonBlank(local.name, remote.name, localId),
            fields = fields,
            message = message,
            localValues = local.summaryValues(),
            cloudValues = remote.summaryValues(),
        )
    }
    SyncRecordTypeItem -> {
        val local = decodeLocalItem() ?: return null
        val remote = decodeRemoteItem() ?: return null
        SyncConflictEntry(
            recordType = recordType,
            localId = localId,
            title = firstNonBlank(local.title, remote.title, localId),
            fields = fields,
            message = message,
            localValues = local.summaryValues(),
            cloudValues = remote.summaryValues(),
        )
    }
    else -> null
}

private fun RemoteChecklistRecord.summaryValues(): List<SyncConflictValue> = listOf(
    SyncConflictValue("NAME", name),
    SyncConflictValue("SORT", sortIndex.toString()),
    SyncConflictValue("DELETED", deletedAtMillis.formatNullableMillis()),
    SyncConflictValue("UPDATED", updatedAtMillis.formatMillis()),
    SyncConflictValue("VERSION", remoteVersion?.toString() ?: "none"),
)

private fun RemoteTodoItemRecord.summaryValues(): List<SyncConflictValue> = listOf(
    SyncConflictValue("TITLE", title),
    SyncConflictValue("LIST", checklistLocalId),
    SyncConflictValue("PRIORITY", priority),
    SyncConflictValue("DUE", dueAtMillis.formatMillis()),
    SyncConflictValue("DONE", if (completed) "yes" else "no"),
    SyncConflictValue("REPEAT", reminderRepeat),
    SyncConflictValue("TRASH", trashedAtMillis.formatNullableMillis()),
    SyncConflictValue("DELETED", deletedAtMillis.formatNullableMillis()),
    SyncConflictValue("IMAGE", imageRemotePath ?: imageLocalName ?: "none"),
    SyncConflictValue("UPDATED", updatedAtMillis.formatMillis()),
    SyncConflictValue("VERSION", remoteVersion?.toString() ?: "none"),
)

private fun RemoteChecklistRecord.toEntityForResolution(
    syncedAtMillis: Long,
    syncState: SyncRecordState,
): TodoChecklistEntity = toEntity(syncedAtMillis).copy(
    syncState = syncState.name,
    lastSyncedAtMillis = if (syncState == SyncRecordState.SYNCED) syncedAtMillis else null,
    lastSyncError = null,
)

private fun RemoteTodoItemRecord.toEntityForResolution(
    syncedAtMillis: Long,
    syncState: SyncRecordState,
): TodoItemEntity = toEntity(syncedAtMillis).copy(
    syncState = syncState.name,
    lastSyncedAtMillis = if (syncState == SyncRecordState.SYNCED) syncedAtMillis else null,
    lastSyncError = null,
)

private fun Long.formatMillis(): String = Instant.ofEpochMilli(this)
    .atZone(ZoneId.systemDefault())
    .format(ConflictDateFormatter)

private fun Long?.formatNullableMillis(): String = this?.formatMillis() ?: "none"

private fun firstNonBlank(vararg values: String): String = values.firstOrNull { it.isNotBlank() } ?: "Untitled"
