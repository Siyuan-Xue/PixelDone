package com.milesxue.pixeldone.data.sync

import android.util.Log
import com.milesxue.pixeldone.data.local.*
import com.milesxue.pixeldone.domain.sync.*
import com.milesxue.pixeldone.domain.todo.ClockProvider
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
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
    private val settingsStore: SettingsSyncLocalStore? = null,
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
    private var scheduledJob: Job? = null
    private var followUp = false
    private var observedUser: String? = null
    private var observedSession = false

    init {
        workScheduler.ensurePeriodicSync()
        scope.launch {
            authSessionRepository.session.collectLatest { session ->
                if (mutableStatus.value != SyncCoordinatorStatus.SYNCING) {
                    setState(mutableRunState.value.copy(status = statusFor(session), lastError = session.configurationError))
                }
                val user = session.userId.takeIf { session.signedIn }
                val changedUser = user != null && (!observedSession || user != observedUser)
                observedUser = user
                observedSession = true
                if (changedUser) schedule(0L, false)
            }
        }
    }

    override fun requestSync() = schedule(autoSyncDebounceMillis, true)
    override suspend fun syncNow(): SyncCoordinatorStatus = syncNowInternal(true)

    override suspend fun loadConflicts(): List<SyncConflictEntry> {
        val owner = authSessionRepository.session.value.userId
            .takeIf { authSessionRepository.session.value.signedIn } ?: return emptyList()
        return localStore.loadConflicts(owner).mapNotNull { it.toPresentation() }
    }

    override suspend fun resolveConflict(
        recordType: String,
        localId: String,
        choice: ConflictResolutionChoice,
    ): SyncCoordinatorStatus {
        val session = authSessionRepository.session.value
        val owner = session.userId.takeIf { session.signedIn } ?: return statusFor(session)
        val upload = syncMutex.withLock {
            val conflict = localStore.loadConflict(owner, recordType, localId) ?: return@withLock false
            val now = clockProvider.nowMillis()
            val applied = when (recordType) {
                SyncRecordTypeChecklist -> resolveChecklist(owner, conflict, choice, now)
                SyncRecordTypeItem -> resolveItem(owner, conflict, choice, now)
                SyncRecordTypeSettings -> resolveSettings(conflict, choice, now)
                else -> false
            }
            if (applied) localStore.clearConflict(owner, recordType, localId)
            applied && choice == ConflictResolutionChoice.KEEP_LOCAL
        }
        return if (upload) syncNowInternal(true) else refreshState(owner)
    }

    private suspend fun syncNowInternal(cancelScheduled: Boolean): SyncCoordinatorStatus {
        if (cancelScheduled) requestMutex.withLock { scheduledJob?.cancel(); scheduledJob = null }
        if (!syncMutex.tryLock()) {
            requestMutex.withLock { followUp = true }
            return SyncCoordinatorStatus.SYNCING
        }
        val result = try { performSync() } finally { syncMutex.unlock() }
        val runAgain = requestMutex.withLock { followUp.also { followUp = false } }
        if (runAgain) schedule(autoSyncDebounceMillis, false)
        return result
    }

    private fun schedule(delayMillis: Long, enqueueWork: Boolean) {
        if (enqueueWork) workScheduler.requestSync()
        scope.launch {
            requestMutex.withLock {
                if (syncMutex.isLocked || mutableStatus.value == SyncCoordinatorStatus.SYNCING) {
                    followUp = true
                    return@launch
                }
                scheduledJob?.cancel()
                scheduledJob = scope.launch {
                    if (delayMillis > 0) delay(delayMillis)
                    requestMutex.withLock { scheduledJob = null }
                    syncNowInternal(false)
                }
            }
        }
    }

    private suspend fun performSync(): SyncCoordinatorStatus {
        val start = authSessionRepository.session.value
        val ready = statusFor(start)
        if (ready != SyncCoordinatorStatus.IDLE && ready != SyncCoordinatorStatus.SYNCED) {
            setState(mutableRunState.value.copy(status = ready, lastError = start.configurationError))
            return ready
        }
        val owner = requireNotNull(start.userId)
        setState(mutableRunState.value.copy(status = SyncCoordinatorStatus.SYNCING, lastError = null))
        return try {
            val result = runWithRefresh(owner)
            val final = if (result.conflicts > 0) SyncCoordinatorStatus.CONFLICT else SyncCoordinatorStatus.SYNCED
            setState(SyncRunState(final, clockProvider.nowMillis(), result.pending, result.conflicts))
            final
        } catch (error: CancellationException) {
            throw error
        } catch (error: SyncSchemaMismatchException) {
            setState(mutableRunState.value.copy(status = SyncCoordinatorStatus.SERVER_UPDATE_REQUIRED, lastError = error.message))
            SyncCoordinatorStatus.SERVER_UPDATE_REQUIRED
        } catch (error: Exception) {
            Log.w(LogTag, "PixelDone sync failed: ${error.safeMessage()}")
            markError(owner, error.message ?: "Sync failed.")
            setState(mutableRunState.value.copy(
                status = SyncCoordinatorStatus.ERROR,
                pendingCount = localStore.loadPendingMutations(owner).size,
                conflictCount = localStore.loadConflicts(owner).size,
                lastError = error.message ?: "Sync failed.",
            ))
            SyncCoordinatorStatus.ERROR
        }
    }

    private suspend fun runWithRefresh(owner: String): Result {
        val session = authSessionRepository.refreshSessionIfNeeded(clockProvider.nowMillis())
        return try { runSync(session, owner) } catch (error: SyncRemoteException) {
            if (error.statusCode != 401) throw error
            runSync(authSessionRepository.refreshSessionIfNeeded(clockProvider.nowMillis(), true), owner)
        }
    }

    private suspend fun runSync(session: AuthSession, owner: String): Result {
        var cursor = localStore.loadSyncCursor(owner) ?: 0L
        var pulled = remoteDataSource.pullChanges(session, cursor)
        applyRemote(owner, pulled)
        cursor = pulled.serverVersion
        localStore.saveSyncCursor(owner, cursor, clockProvider.nowMillis())

        if (localStore.loadPendingMutations(owner).isEmpty()) createMutation(owner)
        val conflictKeys = localStore.loadConflicts(owner).mapTo(mutableSetOf()) { it.key }
        for (queued in localStore.loadPendingMutations(owner)) {
            val mutation = queued.withoutConflicts(conflictKeys)
            if (mutation.isEmpty()) {
                localStore.clearPendingMutation(owner, queued.mutationUuid)
                continue
            }
            val pushed = remoteDataSource.pushMutations(session, RemoteMutationBatch(
                mutation.mutationUuid, mutation.snapshot, mutation.settings, mutation.tombstones,
            ))
            applyPush(owner, mutation, pushed)
            localStore.clearPendingMutation(owner, queued.mutationUuid)
            if (pushed.conflicts.isNotEmpty()) {
                pulled = remoteDataSource.pullChanges(session, cursor)
                applyRemote(owner, pulled)
                cursor = pulled.serverVersion
            } else cursor = maxOf(cursor, pushed.serverVersion)
            localStore.saveSyncCursor(owner, cursor, clockProvider.nowMillis())
        }

        val now = clockProvider.nowMillis()
        val current = localStore.loadEntitySetForSync(now).withOwner(owner)
        val pristine = localStore.loadPristineSnapshot(owner)
        localStore.savePristineSnapshot(owner, current.buildPristine(owner, pristine), now)
        return Result(localStore.loadPendingMutations(owner).size, localStore.loadConflicts(owner).size)
    }

    private suspend fun applyRemote(owner: String, batch: RemoteChangeBatch) {
        val now = clockProvider.nowMillis()
        val pristine = localStore.loadPristineSnapshot(owner)
        val existing = localStore.loadConflicts(owner).associateBy { it.key }
        val found = mutableListOf<LocalSyncConflictRecord>()
        localStore.updateEntitySetFromSync(now) { original ->
            var state = original.withOwner(owner).applyTombstones(owner, batch.tombstones, now)
            val deleted = state.tombstones.mapTo(mutableSetOf()) { it.recordType + ":" + it.localId }
            batch.checklists.forEach { cloud ->
                val key = SyncRecordTypeChecklist + ":" + cloud.localId
                if (key !in deleted) {
                    val local = state.checklists.firstOrNull { it.ownerUserId == owner && it.localId == cloud.localId }
                    val base = pristine.checklists.firstOrNull { it.localId == cloud.localId }
                    val merged = mergeChecklist(local, cloud, base, existing[key], owner, now, found)
                    state = state.copy(checklists = state.checklists.filterNot {
                        it.ownerUserId == owner && it.localId == cloud.localId
                    } + merged)
                }
            }
            batch.items.forEach { cloud ->
                val key = SyncRecordTypeItem + ":" + cloud.localId
                if (key !in deleted) {
                    val local = state.items.firstOrNull { it.ownerUserId == owner && it.localId == cloud.localId }
                    val base = pristine.items.firstOrNull { it.localId == cloud.localId }
                    val merged = mergeItem(local, cloud, base, existing[key], owner, now, found)
                    state = state.copy(items = state.items.filterNot {
                        it.ownerUserId == owner && it.localId == cloud.localId
                    } + merged)
                }
            }
            state
        }
        found.forEach { localStore.recordConflict(owner, it) }
        mergeSettings(owner, batch.settings, existing[SyncRecordTypeSettings + ":language"], now)
    }

    private suspend fun mergeSettings(owner: String, cloud: RemoteUserSettingsRecord?, conflict: LocalSyncConflictRecord?, now: Long) {
        val store = settingsStore ?: return
        if (cloud == null) return
        val local = store.loadSettingsForSync(now)
        val cloudChanged = local.remoteVersion != null && cloud.remoteVersion != local.remoteVersion
        if (conflict != null || (local.syncState.isDirty() && cloudChanged && local.languageMode != cloud.languageMode)) {
            localStore.recordConflict(owner, LocalSyncConflictRecord(
                SyncRecordTypeSettings, "language",
                SyncJson.encodeToString(local.toRemoteRecord(owner)), SyncJson.encodeToString(cloud),
                listOf("language"), "Conflict: language", cloud.remoteVersion, now,
            ))
        } else if (!local.syncState.isDirty() || local.languageMode == cloud.languageMode) {
            store.applyRemoteSettings(cloud, now)
        }
    }

    private suspend fun createMutation(owner: String) {
        val now = clockProvider.nowMillis()
        val conflicts = localStore.loadConflicts(owner).mapTo(mutableSetOf()) { it.key }
        val state = localStore.loadEntitySetForSync(now).withOwner(owner)
        val snapshot = state.dirtySnapshot(owner, conflicts)
        val tombstones = state.tombstones.filter {
            it.ownerUserId == owner && it.syncState.isDirty() && (it.recordType + ":" + it.localId) !in conflicts
        }.map { it.toRemote() }
        val settings = settingsStore?.loadSettingsForSync(now)
            ?.takeIf { it.syncState.isDirty() && (SyncRecordTypeSettings + ":language") !in conflicts }
            ?.toRemoteRecord(owner)
        if (snapshot.checklists.isEmpty() && snapshot.items.isEmpty() && tombstones.isEmpty() && settings == null) return
        localStore.recordPendingMutation(owner, SyncMutationRecord(
            UUID.randomUUID().toString(), snapshot, settings, tombstones, now,
        ))
    }

    private suspend fun applyPush(owner: String, source: SyncMutationRecord, result: RemotePushResult) {
        val now = clockProvider.nowMillis()
        localStore.updateEntitySetFromSync(now) { it.acceptMetadata(owner, source, result, now) }
        result.settings?.let { settingsStore?.markSettingsSynced(it, now) }
    }

    private suspend fun resolveChecklist(
        owner: String,
        conflict: LocalSyncConflictRecord,
        choice: ConflictResolutionChoice,
        now: Long,
    ): Boolean {
        val local = conflict.localChecklist() ?: return false
        val cloud = conflict.cloudChecklist() ?: return false
        val selected = if (choice == ConflictResolutionChoice.KEEP_LOCAL) {
            local.copy(remoteId = cloud.remoteId ?: local.remoteId, remoteVersion = cloud.remoteVersion)
        } else cloud
        val syncState = if (choice == ConflictResolutionChoice.KEEP_LOCAL) SyncRecordState.NOT_SYNCED else SyncRecordState.SYNCED
        localStore.updateEntitySetFromSync(now) { state -> state.copy(
            checklists = state.checklists.filterNot { it.ownerUserId == owner && it.localId == conflict.localId } +
                selected.toEntity(now, syncState),
        ) }
        return true
    }

    private suspend fun resolveItem(
        owner: String,
        conflict: LocalSyncConflictRecord,
        choice: ConflictResolutionChoice,
        now: Long,
    ): Boolean {
        val local = conflict.localItem() ?: return false
        val cloud = conflict.cloudItem() ?: return false
        val selected = if (choice == ConflictResolutionChoice.KEEP_LOCAL) {
            local.copy(remoteId = cloud.remoteId ?: local.remoteId, remoteVersion = cloud.remoteVersion)
        } else cloud
        val syncState = if (choice == ConflictResolutionChoice.KEEP_LOCAL) SyncRecordState.NOT_SYNCED else SyncRecordState.SYNCED
        localStore.updateEntitySetFromSync(now) { state -> state.copy(
            items = state.items.filterNot { it.ownerUserId == owner && it.localId == conflict.localId } +
                selected.toEntity(now, syncState),
        ) }
        return true
    }

    private suspend fun resolveSettings(
        conflict: LocalSyncConflictRecord,
        choice: ConflictResolutionChoice,
        now: Long,
    ): Boolean {
        val store = settingsStore ?: return false
        val local = conflict.localSettings() ?: return false
        val cloud = conflict.cloudSettings() ?: return false
        if (choice == ConflictResolutionChoice.KEEP_LOCAL) {
            store.applyLocalSettingsForUpload(local.copy(remoteVersion = cloud.remoteVersion))
        } else store.applyRemoteSettings(cloud, now)
        return true
    }

    private suspend fun refreshState(owner: String): SyncCoordinatorStatus {
        val count = localStore.loadConflicts(owner).size
        val status = if (count > 0) SyncCoordinatorStatus.CONFLICT else SyncCoordinatorStatus.SYNCED
        setState(mutableRunState.value.copy(
            status = status,
            pendingCount = localStore.loadPendingMutations(owner).size,
            conflictCount = count,
            lastError = null,
        ))
        return status
    }

    private suspend fun markError(owner: String, message: String) {
        localStore.updateEntitySetFromSync(clockProvider.nowMillis()) { state -> state.copy(
            checklists = state.checklists.map {
                if (it.ownerUserId == owner && it.syncState.isDirty()) it.copy(
                    syncState = SyncRecordState.ERROR.name, lastSyncError = message.take(MaxErrorLength),
                ) else it
            },
            items = state.items.map {
                if (it.ownerUserId == owner && it.syncState.isDirty()) it.copy(
                    syncState = SyncRecordState.ERROR.name, lastSyncError = message.take(MaxErrorLength),
                ) else it
            },
            tombstones = state.tombstones.map {
                if (it.ownerUserId == owner && it.syncState.isDirty()) it.copy(
                    syncState = SyncRecordState.ERROR.name, lastSyncError = message.take(MaxErrorLength),
                ) else it
            },
        ) }
        settingsStore?.markSettingsSyncError(message)
    }

    private fun statusFor(session: AuthSession) = when {
        !session.cloudAvailable && session.configurationError == null -> SyncCoordinatorStatus.LOCAL_ONLY
        !session.cloudAvailable -> SyncCoordinatorStatus.NOT_CONFIGURED
        !session.signedIn -> SyncCoordinatorStatus.SIGNED_OUT
        else -> SyncCoordinatorStatus.IDLE
    }

    private fun setState(value: SyncRunState) {
        mutableRunState.value = value
        mutableStatus.value = value.status
    }

    private data class Result(val pending: Int, val conflicts: Int)

    companion object {
        private const val AutoSyncDebounceMillis = 700L
        private const val MaxErrorLength = 280
        private const val LogTag = "PixelDoneSync"
    }
}

private fun mergeChecklist(
    local: TodoChecklistEntity?, cloud: RemoteChecklistRecord, base: RemoteChecklistRecord?,
    existing: LocalSyncConflictRecord?, owner: String, now: Long,
    conflicts: MutableList<LocalSyncConflictRecord>,
): TodoChecklistEntity {
    if (local == null || !local.syncState.isDirty() && existing == null) return cloud.toEntity(now, SyncRecordState.SYNCED)
    if (existing != null || local.syncState == SyncRecordState.CONFLICT.name) {
        conflicts += checklistConflict(local, cloud, base, owner, now)
        return local.copy(syncState = SyncRecordState.CONFLICT.name)
    }
    if (local.toRemote(owner).samePayload(cloud)) return cloud.toEntity(now, SyncRecordState.SYNCED)
    val sameRemoteBase = base == null && local.remoteVersion != null && local.remoteVersion == cloud.remoteVersion
    val localFields = base?.let(local::fieldsFrom) ?: local.fieldsFrom(cloud)
    val cloudFields = if (sameRemoteBase) emptySet() else
        base?.let(cloud::fieldsFrom) ?: cloud.fieldsFrom(local.toRemote(owner))
    val overlap = localFields intersect cloudFields
    if (overlap.isNotEmpty()) {
        conflicts += checklistConflict(local, cloud, base, owner, now, overlap)
        return local.copy(
            remoteId = cloud.remoteId ?: local.remoteId,
            remoteVersion = cloud.remoteVersion,
            syncState = SyncRecordState.CONFLICT.name,
            lastSyncError = "Conflict: ${overlap.sorted().joinToString(",")}",
        )
    }
    return cloud.toEntity(now, SyncRecordState.NOT_SYNCED).copy(
        sortIndex = if ("sort" in localFields) local.sortIndex else cloud.sortIndex,
        name = if ("name" in localFields) local.name else cloud.name,
        updatedAtMillis = maxOf(local.updatedAtMillis, cloud.updatedAtMillis),
        lastSyncedAtMillis = local.lastSyncedAtMillis,
    )
}

private fun mergeItem(
    local: TodoItemEntity?, cloud: RemoteTodoItemRecord, base: RemoteTodoItemRecord?,
    existing: LocalSyncConflictRecord?, owner: String, now: Long,
    conflicts: MutableList<LocalSyncConflictRecord>,
): TodoItemEntity {
    if (local == null || !local.syncState.isDirty() && existing == null) return cloud.toEntity(now, SyncRecordState.SYNCED)
    if (existing != null || local.syncState == SyncRecordState.CONFLICT.name) {
        conflicts += itemConflict(local, cloud, base, owner, now)
        return local.copy(syncState = SyncRecordState.CONFLICT.name)
    }
    if (local.toRemote(owner).samePayload(cloud)) return cloud.toEntity(now, SyncRecordState.SYNCED)
    val sameRemoteBase = base == null && local.remoteVersion != null && local.remoteVersion == cloud.remoteVersion
    val localFields = base?.let(local::fieldsFrom) ?: local.fieldsFrom(cloud)
    val cloudFields = if (sameRemoteBase) emptySet() else
        base?.let(cloud::fieldsFrom) ?: cloud.fieldsFrom(local.toRemote(owner))
    val overlap = localFields intersect cloudFields
    if (overlap.isNotEmpty()) {
        conflicts += itemConflict(local, cloud, base, owner, now, overlap)
        return local.copy(
            remoteId = cloud.remoteId ?: local.remoteId,
            remoteVersion = cloud.remoteVersion,
            syncState = SyncRecordState.CONFLICT.name,
            lastSyncError = "Conflict: ${overlap.sorted().joinToString(",")}",
        )
    }
    val merged = cloud.toEntity(now, SyncRecordState.NOT_SYNCED)
    return merged.copy(
        checklistLocalId = if ("checklist" in localFields) local.checklistLocalId else merged.checklistLocalId,
        sortIndex = if ("sort" in localFields) local.sortIndex else merged.sortIndex,
        title = if ("title" in localFields) local.title else merged.title,
        priority = if ("priority" in localFields) local.priority else merged.priority,
        dueAtMillis = if ("due" in localFields) local.dueAtMillis else merged.dueAtMillis,
        completed = if ("completed" in localFields) local.completed else merged.completed,
        reminderRepeat = if ("repeat" in localFields) local.reminderRepeat else merged.reminderRepeat,
        imageLocalName = if ("image" in localFields) local.imageLocalName else merged.imageLocalName,
        imageRemotePath = if ("image" in localFields) local.imageRemotePath else merged.imageRemotePath,
        imageSyncState = if ("image" in localFields) local.imageSyncState else merged.imageSyncState,
        trashedFromChecklistId = if ("trash" in localFields) local.trashedFromChecklistId else merged.trashedFromChecklistId,
        trashedFromChecklistName = if ("trash" in localFields) local.trashedFromChecklistName else merged.trashedFromChecklistName,
        trashedAtMillis = if ("trash" in localFields) local.trashedAtMillis else merged.trashedAtMillis,
        updatedAtMillis = maxOf(local.updatedAtMillis, cloud.updatedAtMillis),
        lastSyncedAtMillis = local.lastSyncedAtMillis,
    )
}

private fun checklistConflict(
    local: TodoChecklistEntity, cloud: RemoteChecklistRecord, base: RemoteChecklistRecord?,
    owner: String, now: Long, explicit: Set<String>? = null,
): LocalSyncConflictRecord {
    val fields = explicit ?: ((base?.let(local::fieldsFrom) ?: local.fieldsFrom(cloud)) intersect
        (base?.let(cloud::fieldsFrom) ?: cloud.fieldsFrom(local.toRemote(owner))))
    val shown = fields.ifEmpty { setOf("name") }
    return LocalSyncConflictRecord(
        SyncRecordTypeChecklist, local.localId, SyncJson.encodeToString(local.toRemote(owner)),
        SyncJson.encodeToString(cloud), shown.sorted(), "Conflict: ${shown.sorted().joinToString(",")}",
        cloud.remoteVersion, now,
    )
}

private fun itemConflict(
    local: TodoItemEntity, cloud: RemoteTodoItemRecord, base: RemoteTodoItemRecord?,
    owner: String, now: Long, explicit: Set<String>? = null,
): LocalSyncConflictRecord {
    val fields = explicit ?: ((base?.let(local::fieldsFrom) ?: local.fieldsFrom(cloud)) intersect
        (base?.let(cloud::fieldsFrom) ?: cloud.fieldsFrom(local.toRemote(owner))))
    val shown = fields.ifEmpty { setOf("title") }
    return LocalSyncConflictRecord(
        SyncRecordTypeItem, local.localId, SyncJson.encodeToString(local.toRemote(owner)),
        SyncJson.encodeToString(cloud), shown.sorted(), "Conflict: ${shown.sorted().joinToString(",")}",
        cloud.remoteVersion, now,
    )
}

private fun TodoEntitySet.applyTombstones(owner: String, cloud: List<RemoteTombstoneRecord>, now: Long): TodoEntitySet {
    if (cloud.isEmpty()) return this
    val keys = cloud.mapTo(mutableSetOf()) { it.recordType + ":" + it.localId }
    return copy(
        checklists = checklists.filterNot { (SyncRecordTypeChecklist + ":" + it.localId) in keys },
        items = items.filterNot { (SyncRecordTypeItem + ":" + it.localId) in keys },
        tombstones = tombstones.filterNot { (it.recordType + ":" + it.localId) in keys } + cloud.map {
            SyncTombstoneEntity(
                owner, it.recordType, it.localId, it.deletedAtMillis, it.remoteVersion,
                SyncRecordState.SYNCED.name, now,
            )
        },
    )
}

private fun TodoEntitySet.withOwner(owner: String) = copy(
    checklists = checklists.map {
        if (it.ownerUserId == null) it.copy(ownerUserId = owner, syncState = SyncRecordState.NOT_SYNCED.name) else it
    },
    items = items.map {
        if (it.ownerUserId == null) it.copy(ownerUserId = owner, syncState = SyncRecordState.NOT_SYNCED.name) else it
    },
)

private fun TodoEntitySet.dirtySnapshot(owner: String, conflicts: Set<String>) = RemoteTodoSnapshot(
    checklists = checklists.filter {
        it.ownerUserId == owner && it.syncState.isDirty() && (SyncRecordTypeChecklist + ":" + it.localId) !in conflicts
    }.map { it.toRemote(owner) },
    items = items.filter {
        it.ownerUserId == owner && it.syncState.isDirty() && (SyncRecordTypeItem + ":" + it.localId) !in conflicts
    }.map { it.toRemote(owner) },
)

private fun TodoEntitySet.buildPristine(owner: String, previous: RemoteTodoSnapshot): RemoteTodoSnapshot {
    val deleted = tombstones.mapTo(mutableSetOf()) { it.recordType + ":" + it.localId }
    val checklistMap = checklists.filter { it.ownerUserId == owner }.associateBy { it.localId }
    val itemMap = items.filter { it.ownerUserId == owner }.associateBy { it.localId }
    return RemoteTodoSnapshot(
        checklists = (previous.checklists.filter {
            val local = checklistMap[it.localId]
            local != null && local.syncState != SyncRecordState.SYNCED.name &&
                (SyncRecordTypeChecklist + ":" + it.localId) !in deleted
        } + checklistMap.values.filter { it.syncState == SyncRecordState.SYNCED.name }.map { it.toRemote(owner) })
            .distinctBy { it.localId },
        items = (previous.items.filter {
            val local = itemMap[it.localId]
            local != null && local.syncState != SyncRecordState.SYNCED.name &&
                (SyncRecordTypeItem + ":" + it.localId) !in deleted
        } + itemMap.values.filter { it.syncState == SyncRecordState.SYNCED.name }.map { it.toRemote(owner) })
            .distinctBy { it.localId },
    )
}

private fun TodoEntitySet.acceptMetadata(
    owner: String,
    source: SyncMutationRecord,
    result: RemotePushResult,
    now: Long,
): TodoEntitySet {
    val sourceLists = source.snapshot.checklists.associateBy { it.localId }
    val sourceItems = source.snapshot.items.associateBy { it.localId }
    val acceptedLists = result.accepted.checklists.associateBy { it.localId }
    val acceptedItems = result.accepted.items.associateBy { it.localId }
    val acceptedDeleted = result.tombstones.associateBy { it.recordType + ":" + it.localId }
    return copy(
        checklists = checklists.map { local ->
            val sent = sourceLists[local.localId]
            val accepted = acceptedLists[local.localId]
            if (local.ownerUserId == owner && sent != null && accepted != null) {
                val unchanged = local.toRemote(owner).samePayload(sent)
                local.copy(
                    remoteId = accepted.remoteId ?: local.remoteId,
                    remoteVersion = accepted.remoteVersion ?: local.remoteVersion,
                    syncState = if (unchanged) SyncRecordState.SYNCED.name else SyncRecordState.NOT_SYNCED.name,
                    lastSyncedAtMillis = if (unchanged) now else local.lastSyncedAtMillis,
                    lastSyncError = null,
                )
            } else local
        },
        items = items.map { local ->
            val sent = sourceItems[local.localId]
            val accepted = acceptedItems[local.localId]
            if (local.ownerUserId == owner && sent != null && accepted != null) {
                val unchanged = local.toRemote(owner).samePayload(sent)
                local.copy(
                    remoteId = accepted.remoteId ?: local.remoteId,
                    remoteVersion = accepted.remoteVersion ?: local.remoteVersion,
                    syncState = if (unchanged) SyncRecordState.SYNCED.name else SyncRecordState.NOT_SYNCED.name,
                    lastSyncedAtMillis = if (unchanged) now else local.lastSyncedAtMillis,
                    lastSyncError = null,
                )
            } else local
        },
        tombstones = tombstones.map { local ->
            val accepted = acceptedDeleted[local.recordType + ":" + local.localId]
            if (local.ownerUserId == owner && accepted != null) local.copy(
                remoteVersion = accepted.remoteVersion,
                syncState = SyncRecordState.SYNCED.name,
                lastSyncedAtMillis = now,
                lastSyncError = null,
            ) else local
        },
    )
}

private fun SyncMutationRecord.withoutConflicts(conflicts: Set<String>) = copy(
    snapshot = RemoteTodoSnapshot(
        checklists = snapshot.checklists.filter { (SyncRecordTypeChecklist + ":" + it.localId) !in conflicts },
        items = snapshot.items.filter { (SyncRecordTypeItem + ":" + it.localId) !in conflicts },
    ),
    settings = settings.takeIf { (SyncRecordTypeSettings + ":language") !in conflicts },
    tombstones = tombstones.filter { (it.recordType + ":" + it.localId) !in conflicts },
)

private fun SyncMutationRecord.isEmpty() = snapshot.checklists.isEmpty() && snapshot.items.isEmpty() &&
    settings == null && tombstones.isEmpty()

private fun TodoChecklistEntity.toRemote(owner: String) = RemoteChecklistRecord(
    localId, remoteId, owner, sortIndex, name, createdAtMillis, updatedAtMillis, remoteVersion,
)

private fun TodoItemEntity.toRemote(owner: String) = RemoteTodoItemRecord(
    localId, remoteId, owner, checklistLocalId, sortIndex, title, priority, dueAtMillis,
    completed, createdAtMillis, updatedAtMillis, reminderRepeat, imageLocalName, imageRemotePath,
    imageSyncState, trashedFromChecklistId, trashedFromChecklistName, trashedAtMillis, remoteVersion,
)

private fun SyncTombstoneEntity.toRemote() = RemoteTombstoneRecord(
    ownerUserId, recordType, localId, deletedAtMillis, remoteVersion,
)

private fun RemoteChecklistRecord.toEntity(now: Long, state: SyncRecordState) = TodoChecklistEntity(
    localId, sortIndex, remoteId, ownerUserId, name, createdAtMillis, updatedAtMillis,
    state.name, if (state == SyncRecordState.SYNCED) now else null, remoteVersion,
)

private fun RemoteTodoItemRecord.toEntity(now: Long, state: SyncRecordState) = TodoItemEntity(
    localId, checklistLocalId, sortIndex, remoteId, ownerUserId, title, priority, dueAtMillis,
    completed, createdAtMillis, updatedAtMillis, reminderRepeat, imageLocalName, imageRemotePath,
    imageSyncState, trashedFromChecklistId, trashedFromChecklistName, trashedAtMillis,
    state.name, if (state == SyncRecordState.SYNCED) now else null, remoteVersion,
)

private fun TodoChecklistEntity.fieldsFrom(base: RemoteChecklistRecord) = buildSet {
    if (sortIndex != base.sortIndex) add("sort")
    if (name != base.name) add("name")
}
private fun RemoteChecklistRecord.fieldsFrom(base: RemoteChecklistRecord) = buildSet {
    if (sortIndex != base.sortIndex) add("sort")
    if (name != base.name) add("name")
}
private fun TodoItemEntity.fieldsFrom(base: RemoteTodoItemRecord) = buildSet {
    if (checklistLocalId != base.checklistLocalId) add("checklist")
    if (sortIndex != base.sortIndex) add("sort")
    if (title != base.title) add("title")
    if (priority != base.priority) add("priority")
    if (dueAtMillis != base.dueAtMillis) add("due")
    if (completed != base.completed) add("completed")
    if (reminderRepeat != base.reminderRepeat) add("repeat")
    if (imageLocalName != base.imageLocalName || imageRemotePath != base.imageRemotePath || imageSyncState != base.imageSyncState) add("image")
    if (trashedFromChecklistId != base.trashedFromChecklistId || trashedFromChecklistName != base.trashedFromChecklistName || trashedAtMillis != base.trashedAtMillis) add("trash")
}
private fun RemoteTodoItemRecord.fieldsFrom(base: RemoteTodoItemRecord) = buildSet {
    if (checklistLocalId != base.checklistLocalId) add("checklist")
    if (sortIndex != base.sortIndex) add("sort")
    if (title != base.title) add("title")
    if (priority != base.priority) add("priority")
    if (dueAtMillis != base.dueAtMillis) add("due")
    if (completed != base.completed) add("completed")
    if (reminderRepeat != base.reminderRepeat) add("repeat")
    if (imageLocalName != base.imageLocalName || imageRemotePath != base.imageRemotePath || imageSyncState != base.imageSyncState) add("image")
    if (trashedFromChecklistId != base.trashedFromChecklistId || trashedFromChecklistName != base.trashedFromChecklistName || trashedAtMillis != base.trashedAtMillis) add("trash")
}

private fun RemoteChecklistRecord.samePayload(other: RemoteChecklistRecord) =
    localId == other.localId && sortIndex == other.sortIndex && name == other.name &&
        createdAtMillis == other.createdAtMillis && updatedAtMillis == other.updatedAtMillis

private fun RemoteTodoItemRecord.samePayload(other: RemoteTodoItemRecord) =
    localId == other.localId && checklistLocalId == other.checklistLocalId && sortIndex == other.sortIndex &&
        title == other.title && priority == other.priority && dueAtMillis == other.dueAtMillis &&
        completed == other.completed && createdAtMillis == other.createdAtMillis &&
        updatedAtMillis == other.updatedAtMillis && reminderRepeat == other.reminderRepeat &&
        imageLocalName == other.imageLocalName && imageRemotePath == other.imageRemotePath &&
        imageSyncState == other.imageSyncState && trashedFromChecklistId == other.trashedFromChecklistId &&
        trashedFromChecklistName == other.trashedFromChecklistName && trashedAtMillis == other.trashedAtMillis

private val LocalSyncConflictRecord.key get() = recordType + ":" + localId
private fun String.isDirty() = this == SyncRecordState.LOCAL_ONLY.name ||
    this == SyncRecordState.NOT_SYNCED.name || this == SyncRecordState.ERROR.name

private val SyncJson = Json { encodeDefaults = true; ignoreUnknownKeys = true }
private fun LocalSyncConflictRecord.localChecklist() =
    runCatching { SyncJson.decodeFromString<RemoteChecklistRecord>(localPayloadJson) }.getOrNull()
private fun LocalSyncConflictRecord.cloudChecklist() =
    runCatching { SyncJson.decodeFromString<RemoteChecklistRecord>(remotePayloadJson) }.getOrNull()
private fun LocalSyncConflictRecord.localItem() =
    runCatching { SyncJson.decodeFromString<RemoteTodoItemRecord>(localPayloadJson) }.getOrNull()
private fun LocalSyncConflictRecord.cloudItem() =
    runCatching { SyncJson.decodeFromString<RemoteTodoItemRecord>(remotePayloadJson) }.getOrNull()
private fun LocalSyncConflictRecord.localSettings() =
    runCatching { SyncJson.decodeFromString<RemoteUserSettingsRecord>(localPayloadJson) }.getOrNull()
private fun LocalSyncConflictRecord.cloudSettings() =
    runCatching { SyncJson.decodeFromString<RemoteUserSettingsRecord>(remotePayloadJson) }.getOrNull()

private fun LocalSyncConflictRecord.toPresentation(): SyncConflictEntry? = when (recordType) {
    SyncRecordTypeChecklist -> {
        val local = localChecklist() ?: return null
        val cloud = cloudChecklist() ?: return null
        SyncConflictEntry(
            recordType, localId, firstNonBlank(local.name, cloud.name, localId), fields, message,
            fields.map { SyncConflictValue(it, local.valueFor(it)) },
            fields.map { SyncConflictValue(it, cloud.valueFor(it)) },
        )
    }
    SyncRecordTypeItem -> {
        val local = localItem() ?: return null
        val cloud = cloudItem() ?: return null
        SyncConflictEntry(
            recordType, localId, firstNonBlank(local.title, cloud.title, localId), fields, message,
            fields.map { SyncConflictValue(it, local.valueFor(it)) },
            fields.map { SyncConflictValue(it, cloud.valueFor(it)) },
        )
    }
    SyncRecordTypeSettings -> {
        val local = localSettings() ?: return null
        val cloud = cloudSettings() ?: return null
        SyncConflictEntry(
            recordType, localId, local.languageMode, fields, message,
            listOf(SyncConflictValue("language", local.languageMode)),
            listOf(SyncConflictValue("language", cloud.languageMode)),
        )
    }
    else -> null
}

private fun RemoteChecklistRecord.valueFor(field: String) = when (field) {
    "sort" -> sortIndex.toString()
    else -> name
}

private fun RemoteTodoItemRecord.valueFor(field: String) = when (field) {
    "checklist" -> checklistLocalId
    "sort" -> sortIndex.toString()
    "title" -> title
    "priority" -> priority
    "due" -> dueAtMillis.formatMillis()
    "completed" -> completed.toString()
    "repeat" -> reminderRepeat
    "image" -> imageRemotePath ?: imageLocalName ?: "—"
    "trash" -> trashedAtMillis?.formatMillis() ?: "—"
    else -> "—"
}

private fun Long.formatMillis() = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
private fun firstNonBlank(vararg values: String) = values.firstOrNull { it.isNotBlank() } ?: "Untitled"
private fun Throwable.safeMessage() = message
    ?.replace(Regex("(?i)bearer\\s+[A-Za-z0-9._-]+"), "Bearer [redacted]")
    ?: javaClass.simpleName
