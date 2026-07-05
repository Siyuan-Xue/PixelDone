package com.milesxue.pixeldone.data.sync

import com.milesxue.pixeldone.data.local.RoomTodoStateStore
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class TodoSyncCoordinator(
    private val authSessionRepository: AuthSessionRepository,
    private val localStore: RoomTodoStateStore,
    private val remoteDataSource: RemoteTodoDataSource,
    private val clockProvider: ClockProvider,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : SyncCoordinator {
    private val syncMutex = Mutex()
    private val mutableStatus = MutableStateFlow(statusFor(authSessionRepository.session.value))
    override val status: StateFlow<SyncCoordinatorStatus> = mutableStatus.asStateFlow()

    init {
        scope.launch {
            authSessionRepository.session.collectLatest { session ->
                if (mutableStatus.value != SyncCoordinatorStatus.SYNCING) {
                    mutableStatus.value = statusFor(session)
                }
                if (session.signedIn) requestSync()
            }
        }
    }

    override fun requestSync() {
        scope.launch { syncNow() }
    }

    override suspend fun syncNow(): SyncCoordinatorStatus = syncMutex.withLock {
        val session = authSessionRepository.session.value
        val readyStatus = statusFor(session)
        if (readyStatus != SyncCoordinatorStatus.IDLE && readyStatus != SyncCoordinatorStatus.SYNCED) {
            mutableStatus.value = readyStatus
            return@withLock readyStatus
        }
        val ownerUserId = requireNotNull(session.userId)
        mutableStatus.value = SyncCoordinatorStatus.SYNCING
        val nowMillis = clockProvider.nowMillis()
        return@withLock try {
            val local = localStore.loadEntitySetForSync(nowMillis).withOwnerAttached(ownerUserId)
            val remote = remoteDataSource.pullSnapshot(session)
            val merged = mergeSnapshots(
                local = local,
                remote = remote,
                ownerUserId = ownerUserId,
                syncedAtMillis = nowMillis,
            )
            localStore.replaceEntitySetFromSync(merged)
            val dirtySnapshot = merged.toDirtyRemoteSnapshot(ownerUserId)
            if (dirtySnapshot.checklists.isNotEmpty() || dirtySnapshot.items.isNotEmpty()) {
                val pushed = remoteDataSource.pushSnapshot(session, dirtySnapshot)
                localStore.replaceEntitySetFromSync(
                    merged.withPushedRemoteMetadata(
                        pushed = pushed,
                        ownerUserId = ownerUserId,
                        syncedAtMillis = clockProvider.nowMillis(),
                    ),
                )
            }
            mutableStatus.value = SyncCoordinatorStatus.SYNCED
            SyncCoordinatorStatus.SYNCED
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            markSyncError(ownerUserId, error.message ?: "Sync failed.")
            mutableStatus.value = SyncCoordinatorStatus.ERROR
            SyncCoordinatorStatus.ERROR
        }
    }

    private suspend fun markSyncError(ownerUserId: String, message: String) {
        val nowMillis = clockProvider.nowMillis()
        val current = localStore.loadEntitySetForSync(nowMillis)
        localStore.replaceEntitySetFromSync(
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
            ),
        )
    }

    private fun statusFor(session: AuthSession): SyncCoordinatorStatus = when {
        !session.cloudAvailable && session.configurationError == null -> SyncCoordinatorStatus.LOCAL_ONLY
        !session.cloudAvailable -> SyncCoordinatorStatus.NOT_CONFIGURED
        !session.signedIn -> SyncCoordinatorStatus.SIGNED_OUT
        else -> SyncCoordinatorStatus.IDLE
    }

    companion object {
        private const val MaxSyncErrorLength = 280
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

private fun RemoteTodoItemRecord.toEntity(syncedAtMillis: Long): TodoItemEntity = TodoItemEntity(
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

private fun TodoEntitySet.withPushedRemoteMetadata(
    pushed: RemoteTodoSnapshot,
    ownerUserId: String,
    syncedAtMillis: Long,
): TodoEntitySet {
    val pushedChecklistsById = pushed.checklists.associateBy { it.localId }
    val pushedItemsById = pushed.items.associateBy { it.localId }
    return copy(
        checklists = checklists.map { checklist ->
            val remote = pushedChecklistsById[checklist.localId]
            if (checklist.ownerUserId == ownerUserId && remote != null) {
                checklist.copy(
                    remoteId = remote.remoteId ?: checklist.remoteId,
                    remoteVersion = remote.remoteVersion ?: checklist.remoteVersion,
                    syncState = SyncRecordState.SYNCED.name,
                    lastSyncedAtMillis = syncedAtMillis,
                    lastSyncError = null,
                )
            } else {
                checklist
            }
        },
        items = items.map { item ->
            val remote = pushedItemsById[item.localId]
            if (item.ownerUserId == ownerUserId && remote != null) {
                item.copy(
                    remoteId = remote.remoteId ?: item.remoteId,
                    remoteVersion = remote.remoteVersion ?: item.remoteVersion,
                    syncState = SyncRecordState.SYNCED.name,
                    lastSyncedAtMillis = syncedAtMillis,
                    lastSyncError = null,
                )
            } else {
                item
            }
        },
    )
}

private fun TodoChecklistEntity.recordClock(): Long = deletedAtMillis?.coerceAtLeast(updatedAtMillis) ?: updatedAtMillis
private fun RemoteChecklistRecord.recordClock(): Long = deletedAtMillis?.coerceAtLeast(updatedAtMillis) ?: updatedAtMillis
private fun TodoItemEntity.recordClock(): Long = deletedAtMillis?.coerceAtLeast(updatedAtMillis) ?: updatedAtMillis
private fun RemoteTodoItemRecord.recordClock(): Long = deletedAtMillis?.coerceAtLeast(updatedAtMillis) ?: updatedAtMillis