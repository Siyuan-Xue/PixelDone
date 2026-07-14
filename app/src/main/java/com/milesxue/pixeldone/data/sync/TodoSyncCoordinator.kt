package com.milesxue.pixeldone.data.sync

import android.util.Log
import com.milesxue.pixeldone.data.local.*
import com.milesxue.pixeldone.domain.sync.*
import com.milesxue.pixeldone.domain.todo.ClockProvider
import com.milesxue.pixeldone.domain.todo.SettingsChecklistId
import com.milesxue.pixeldone.domain.todo.TrashChecklistId
import com.milesxue.pixeldone.domain.todo.isSpecialChecklistId
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
    private val attachmentSyncService: TodoAttachmentSyncService? = null,
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
        return try {
            // External readers wait for generation activation and never observe staging rows.
            syncMutex.withLock { loadConflictEntries(owner) }
        } catch (error: SyncMetadataCorruptException) {
            recoverCorruptMetadata(owner, error)
            emptyList()
        }
    }

    override suspend fun resolveConflict(
        recordType: String,
        localId: String,
        choice: ConflictResolutionChoice,
    ): SyncCoordinatorStatus {
        val session = authSessionRepository.session.value
        val owner = session.userId.takeIf { session.signedIn } ?: return statusFor(session)
        val upload = try {
            syncMutex.withLock {
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
        } catch (error: SyncMetadataCorruptException) {
            recoverCorruptMetadata(owner, error)
            return SyncCoordinatorStatus.IDLE
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
        if (ready != SyncCoordinatorStatus.IDLE) {
            setState(mutableRunState.value.copy(status = ready, lastError = start.configurationError))
            return ready
        }
        val owner = requireNotNull(start.userId)
        setState(mutableRunState.value.copy(status = SyncCoordinatorStatus.SYNCING, lastError = null))
        return try {
            val result = runWithMetadataRecovery(owner)
            val final = when {
                result.conflicts > 0 -> SyncCoordinatorStatus.CONFLICT
                result.pending > 0 -> SyncCoordinatorStatus.PENDING
                else -> SyncCoordinatorStatus.STABLE
            }
            setState(SyncRunState(final, clockProvider.nowMillis(), result.pending, result.conflicts))
            final
        } catch (error: CancellationException) {
            throw error
        } catch (error: AuthSessionExpiredException) {
            logWarning("PixelDone sync stopped because the auth session expired.")
            setState(
                mutableRunState.value.copy(
                    status = SyncCoordinatorStatus.SIGNED_OUT,
                    lastError = SessionExpiredMessage,
                ),
            )
            SyncCoordinatorStatus.SIGNED_OUT
        } catch (error: SyncSchemaMismatchException) {
            val status = when (error.requiredAction) {
                SyncContractRequiredAction.UPDATE_APP -> SyncCoordinatorStatus.APP_UPDATE_REQUIRED
                SyncContractRequiredAction.UPDATE_SERVER -> SyncCoordinatorStatus.SERVER_UPDATE_REQUIRED
            }
            setState(mutableRunState.value.copy(status = status, lastError = error.message))
            status
        } catch (error: SyncNetworkException) {
            logWarning("PixelDone sync network failure: ${error.safeMessage()}")
            val message = NetworkErrorMessage
            markError(owner, message)
            val counts = loadFailureCounts(owner)
            setState(
                mutableRunState.value.copy(
                    status = SyncCoordinatorStatus.NETWORK_ERROR,
                    pendingCount = counts.pending,
                    conflictCount = counts.conflicts,
                    lastError = message,
                ),
            )
            SyncCoordinatorStatus.NETWORK_ERROR
        } catch (error: Exception) {
            logWarning("PixelDone sync failed: ${error.safeMessage()}")
            markError(owner, error.message ?: "Sync failed.")
            val counts = loadFailureCounts(owner)
            setState(mutableRunState.value.copy(
                status = SyncCoordinatorStatus.ERROR,
                pendingCount = counts.pending,
                conflictCount = counts.conflicts,
                lastError = error.message ?: "Sync failed.",
            ))
            SyncCoordinatorStatus.ERROR
        }
    }

    private suspend fun runWithMetadataRecovery(owner: String): Result {
        var recovered = false
        while (true) {
            val session = localStore.beginSyncMetadata(owner, clockProvider.nowMillis())
            try {
                val result = runWithRefresh(owner)
                localStore.completeSyncMetadata(owner, session, clockProvider.nowMillis())
                return result
            } catch (error: SyncMetadataCorruptException) {
                localStore.abortSyncMetadata(owner, session)
                if (recovered) throw error
                logWarning("Rebuilding corrupt sync metadata: ${error.safeMessage()}")
                localStore.invalidateSyncMetadata(owner)
                recovered = true
            } catch (error: Exception) {
                localStore.abortSyncMetadata(owner, session)
                throw error
            }
        }
    }

    private suspend fun runWithRefresh(owner: String): Result {
        val session = authSessionRepository.refreshSessionIfNeeded(clockProvider.nowMillis())
        return try { runSync(session, owner) } catch (error: SyncRemoteException) {
            if (error.statusCode != 401) throw error
            runSync(authSessionRepository.refreshSessionIfNeeded(clockProvider.nowMillis(), true), owner)
        }
    }

    private suspend fun pullWithRetry(session: AuthSession, cursor: Long): RemoteChangeBatch =
        retryNetwork { remoteDataSource.pullChanges(session, cursor) }

    private suspend fun pushWithRetry(
        session: AuthSession,
        batch: RemoteMutationBatch,
    ): RemotePushResult = retryNetwork { remoteDataSource.pushMutations(session, batch) }

    private suspend fun <T> retryNetwork(block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (error: SyncNetworkException) {
                if (attempt++ >= MaxNetworkRetries) throw error
                delay(NetworkRetryDelayMillis * attempt)
            }
        }
    }

    private suspend fun runSync(session: AuthSession, owner: String): Result {
        clearSyntheticChecklistConflicts(owner)
        var cursor = localStore.loadSyncCursor(owner) ?: 0L
        var pulled = pullWithRetry(session, cursor)
        applyRemote(owner, pulled)
        cursor = pulled.serverVersion
        localStore.saveSyncCursor(owner, cursor, clockProvider.nowMillis())

        var cleanedImagePaths = attachmentSyncService
            ?.deleteCleanupPaths(session, pulled.imageCleanupPaths)
            .orEmpty()
        attachmentSyncService?.preparePendingUploads(owner, session)

        if (localStore.loadPendingMutations(owner).isEmpty()) {
            createMutation(owner, cleanedImagePaths)
            cleanedImagePaths = emptyList()
        } else if (cleanedImagePaths.isNotEmpty()) {
            localStore.recordPendingMutation(
                owner,
                SyncMutationRecord(
                    mutationUuid = UUID.randomUUID().toString(),
                    createdAtMillis = clockProvider.nowMillis(),
                    cleanedImagePaths = cleanedImagePaths,
                ),
            )
            cleanedImagePaths = emptyList()
        }
        val conflictKeys = localStore.loadConflicts(owner).mapTo(mutableSetOf()) { it.key }
        var mutationPass = 0
        while (mutationPass++ < MaxMutationPasses) {
            val queuedMutations = localStore.loadPendingMutations(owner)
            if (queuedMutations.isEmpty()) break
            for (queued in queuedMutations) {
                val mutation = queued.withoutConflicts(conflictKeys)
                if (mutation.isEmpty()) {
                    localStore.clearPendingMutation(owner, queued.mutationUuid)
                    continue
                }
                val pushed = pushWithRetry(
                    session,
                    RemoteMutationBatch(
                        mutationUuid = mutation.mutationUuid,
                        snapshot = mutation.snapshot,
                        settings = mutation.settings,
                        tombstones = mutation.tombstones,
                        cleanedImagePaths = mutation.cleanedImagePaths,
                    ),
                )
                applyPush(owner, mutation, pushed)
                localStore.clearPendingMutation(owner, queued.mutationUuid)
                val newlyCleaned = attachmentSyncService
                    ?.deleteCleanupPaths(session, pushed.imageCleanupPaths)
                    .orEmpty()
                if (newlyCleaned.isNotEmpty()) {
                    localStore.recordPendingMutation(
                        owner,
                        SyncMutationRecord(
                            mutationUuid = UUID.randomUUID().toString(),
                            createdAtMillis = clockProvider.nowMillis(),
                            cleanedImagePaths = newlyCleaned,
                        ),
                    )
                }
                if (pushed.conflicts.isNotEmpty()) {
                    pulled = pullWithRetry(session, cursor)
                    applyRemote(owner, pulled)
                    cursor = pulled.serverVersion
                } else cursor = maxOf(cursor, pushed.serverVersion)
                localStore.saveSyncCursor(owner, cursor, clockProvider.nowMillis())
            }
        }

        val now = clockProvider.nowMillis()
        val current = localStore.loadEntitySetForSync(now).withOwner(owner)
        val pristine = localStore.loadPristineSnapshot(owner)
        localStore.savePristineSnapshot(owner, current.buildPristine(owner, pristine), now)
        return Result(localStore.loadPendingMutations(owner).size, loadConflictEntries(owner).size)
    }

    private suspend fun applyRemote(owner: String, batch: RemoteChangeBatch) {
        val now = clockProvider.nowMillis()
        val pristine = localStore.loadPristineSnapshot(owner)
        val existing = localStore.loadConflicts(owner).associateBy { it.key }
        val found = mutableListOf<LocalSyncConflictRecord>()
        val staleLocalImages = linkedSetOf<String>()
        localStore.updateEntitySetFromSync(now) { original ->
            val owned = original.withOwner(owner)
            val deletedItemIds = batch.tombstones
                .filter { it.recordType == SyncRecordTypeItem }
                .mapTo(mutableSetOf()) { it.localId }
            val applicableTombstones = batch.tombstones.filterNot {
                it.recordType == SyncRecordTypeChecklist && isSpecialChecklistId(it.localId)
            }
            val deletedChecklistIds = applicableTombstones
                .filter { it.recordType == SyncRecordTypeChecklist }
                .mapTo(mutableSetOf()) { it.localId }
            owned.items.filter {
                it.localId in deletedItemIds || it.checklistLocalId in deletedChecklistIds
            }.mapNotNullTo(staleLocalImages) { it.imageLocalName }
            var state = owned.applyTombstones(owner, applicableTombstones, now)
            val deleted = state.tombstones.mapTo(mutableSetOf()) { it.recordType + ":" + it.localId }
            batch.checklists.filterNot { isSpecialChecklistId(it.localId) }.forEach { cloud ->
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
            batch.attachments.forEach { cloud ->
                val localIndex = state.items.indexOfFirst {
                    it.ownerUserId == owner && it.localId == cloud.todoLocalId
                }
                if (localIndex >= 0) {
                    val local = state.items[localIndex]
                    val merged = mergeAttachment(local, cloud, attachmentSyncService)
                    if (local.imageLocalName != merged.imageLocalName) {
                        local.imageLocalName?.let(staleLocalImages::add)
                    }
                    state = state.copy(items = state.items.toMutableList().also { it[localIndex] = merged })
                }
            }
            state
        }
        staleLocalImages.forEach { attachmentSyncService?.deleteLocalImage(it) }
        found.forEach { localStore.recordConflict(owner, it) }
        mergeSettings(owner, batch.settings, existing[SyncRecordTypeSettings + ":language"], now)
    }

    private suspend fun mergeSettings(owner: String, cloud: RemoteUserSettingsRecord?, conflict: LocalSyncConflictRecord?, now: Long) {
        val store = settingsStore ?: return
        if (cloud == null) return
        val local = store.loadSettingsForSync(now)
        val cloudChanged = local.remoteVersion != null && cloud.remoteVersion != local.remoteVersion
        if (local.languageMode == cloud.languageMode) {
            store.applyRemoteSettings(cloud, now)
        } else if (
            conflict != null ||
            local.syncState == SyncRecordState.CONFLICT.name ||
            (local.syncState.isDirty() && cloudChanged)
        ) {
            localStore.recordConflict(owner, LocalSyncConflictRecord(
                SyncRecordTypeSettings, "language",
                SyncJson.encodeToString(local.toRemoteRecord(owner)), SyncJson.encodeToString(cloud),
                listOf("language"), "Conflict: language", cloud.remoteVersion, now,
            ))
        } else if (!local.syncState.isDirty()) {
            store.applyRemoteSettings(cloud, now)
        }
    }

    private suspend fun createMutation(owner: String, cleanedImagePaths: List<String> = emptyList()) {
        val now = clockProvider.nowMillis()
        val conflicts = localStore.loadConflicts(owner).mapTo(mutableSetOf()) { it.key }
        val state = localStore.loadEntitySetForSync(now).withOwner(owner)
        val snapshot = state.dirtySnapshot(owner, conflicts)
        val tombstones = state.tombstones.filter {
            it.ownerUserId == owner && it.syncState.isDirty() &&
                !(it.recordType == SyncRecordTypeChecklist && isSpecialChecklistId(it.localId)) &&
                (it.recordType + ":" + it.localId) !in conflicts
        }.map { it.toRemote() }
        val settings = settingsStore?.loadSettingsForSync(now)
            ?.takeIf { it.syncState.isDirty() && (SyncRecordTypeSettings + ":language") !in conflicts }
            ?.toRemoteRecord(owner)
        if (
            snapshot.checklists.isEmpty() && snapshot.items.isEmpty() && snapshot.attachments.isEmpty() &&
            tombstones.isEmpty() && settings == null && cleanedImagePaths.isEmpty()
        ) return
        localStore.recordPendingMutation(owner, SyncMutationRecord(
            UUID.randomUUID().toString(), snapshot, settings, tombstones, now, cleanedImagePaths,
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
        val local = conflict.localChecklist()
        val cloud = conflict.cloudChecklist()
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
        val local = conflict.localItem()
        val cloud = conflict.cloudItem()
        val selected = if (choice == ConflictResolutionChoice.KEEP_LOCAL) {
            local.copy(remoteId = cloud.remoteId ?: local.remoteId, remoteVersion = cloud.remoteVersion)
        } else cloud
        val syncState = if (choice == ConflictResolutionChoice.KEEP_LOCAL) SyncRecordState.NOT_SYNCED else SyncRecordState.SYNCED
        localStore.updateEntitySetFromSync(now) { state ->
            val currentLocal = state.items.firstOrNull {
                it.ownerUserId == owner && it.localId == conflict.localId
            }
            state.copy(
                items = state.items.filterNot { it.ownerUserId == owner && it.localId == conflict.localId } +
                    selected.toEntity(now, syncState).withLocalImageFrom(currentLocal),
            )
        }
        return true
    }

    private suspend fun resolveSettings(
        conflict: LocalSyncConflictRecord,
        choice: ConflictResolutionChoice,
        now: Long,
    ): Boolean {
        val store = settingsStore ?: return false
        val local = conflict.localSettings()
        val cloud = conflict.cloudSettings()
        if (choice == ConflictResolutionChoice.KEEP_LOCAL) {
            store.applyLocalSettingsForUpload(local.copy(remoteVersion = cloud.remoteVersion))
        } else store.applyRemoteSettings(cloud, now)
        return true
    }

    private suspend fun refreshState(owner: String): SyncCoordinatorStatus {
        val count = loadConflictEntries(owner).size
        val pending = localStore.loadPendingMutations(owner).size
        val status = when {
            count > 0 -> SyncCoordinatorStatus.CONFLICT
            pending > 0 -> SyncCoordinatorStatus.PENDING
            else -> SyncCoordinatorStatus.STABLE
        }
        setState(mutableRunState.value.copy(
            status = status,
            pendingCount = pending,
            conflictCount = count,
            lastError = null,
        ))
        return status
    }

    private suspend fun loadConflictEntries(owner: String): List<SyncConflictEntry> {
        val checklistNames = localStore.loadEntitySetForSync(clockProvider.nowMillis()).checklists
            .associate { it.localId to it.name }
        return localStore.loadConflicts(owner)
            .filterNot { it.isSyntheticChecklistConflict() }
            .map { it.toPresentation(checklistNames) }
    }

    private suspend fun clearSyntheticChecklistConflicts(owner: String) {
        localStore.loadConflicts(owner)
            .filter { it.isSyntheticChecklistConflict() }
            .forEach { conflict ->
                localStore.clearConflict(owner, conflict.recordType, conflict.localId)
            }
    }

    private suspend fun loadFailureCounts(owner: String): Result = try {
        Result(
            pending = localStore.loadPendingMutations(owner).size,
            conflicts = loadConflictEntries(owner).size,
        )
    } catch (error: SyncMetadataCorruptException) {
        recoverCorruptMetadata(owner, error)
        Result(0, 0)
    }

    private suspend fun recoverCorruptMetadata(owner: String, error: SyncMetadataCorruptException) {
        logWarning("Invalidating corrupt sync metadata: ${error.safeMessage()}")
        localStore.invalidateSyncMetadata(owner)
        setState(
            mutableRunState.value.copy(
                status = SyncCoordinatorStatus.IDLE,
                pendingCount = 0,
                conflictCount = 0,
                lastError = null,
            ),
        )
        schedule(0L, false)
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

    private fun logWarning(message: String) {
        // android.util.Log is unavailable in local JVM tests; logging must never alter sync behavior.
        runCatching { Log.w(LogTag, message) }
    }

    private data class Result(val pending: Int, val conflicts: Int)

    companion object {
        private const val AutoSyncDebounceMillis = 700L
        private const val MaxErrorLength = 280
        private const val MaxMutationPasses = 4
        private const val MaxNetworkRetries = 1
        private const val NetworkRetryDelayMillis = 350L
        private const val NetworkErrorMessage = "Network unavailable. Check Wi-Fi, mobile data, or VPN."
        private const val SessionExpiredMessage = "Session expired. Sign in again."
        private const val LogTag = "PixelDoneSync"
    }
}

private fun mergeChecklist(
    local: TodoChecklistEntity?, cloud: RemoteChecklistRecord, base: RemoteChecklistRecord?,
    existing: LocalSyncConflictRecord?, owner: String, now: Long,
    conflicts: MutableList<LocalSyncConflictRecord>,
): TodoChecklistEntity {
    if (local == null) return cloud.toEntity(now, SyncRecordState.SYNCED)
    if (!local.syncState.isDirty() && local.syncState != SyncRecordState.CONFLICT.name && existing == null) {
        return cloud.toEntity(now, SyncRecordState.SYNCED)
    }
    if (local.toRemote(owner).samePayload(cloud)) return cloud.toEntity(now, SyncRecordState.SYNCED)
    if (existing != null || local.syncState == SyncRecordState.CONFLICT.name) {
        conflicts += checklistConflict(local, cloud, base, owner, now)
        return local.copy(syncState = SyncRecordState.CONFLICT.name)
    }
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
    if (local == null) return cloud.toEntity(now, SyncRecordState.SYNCED)
    if (!local.syncState.isDirty() && local.syncState != SyncRecordState.CONFLICT.name && existing == null) {
        return cloud.toEntity(now, SyncRecordState.SYNCED).withLocalImageFrom(local)
    }
    if (local.toRemote(owner).samePayload(cloud)) {
        return cloud.toEntity(now, SyncRecordState.SYNCED).withLocalImageFrom(local)
    }
    if (existing != null || local.syncState == SyncRecordState.CONFLICT.name) {
        conflicts += itemConflict(local, cloud, base, owner, now)
        return local.copy(syncState = SyncRecordState.CONFLICT.name)
    }
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
        imageLocalName = local.imageLocalName,
        imageRemotePath = local.imageRemotePath,
        imageSyncState = local.imageSyncState,
        imageAttachmentId = local.imageAttachmentId,
        imageContentSha256 = local.imageContentSha256,
        imageContentType = local.imageContentType,
        imageByteSize = local.imageByteSize,
        imageUpdatedAtMillis = local.imageUpdatedAtMillis,
        imageRemoteVersion = local.imageRemoteVersion,
        imageLastSyncError = local.imageLastSyncError,
        trashedFromChecklistId = if ("trash" in localFields) local.trashedFromChecklistId else merged.trashedFromChecklistId,
        trashedFromChecklistName = if ("trash" in localFields) local.trashedFromChecklistName else merged.trashedFromChecklistName,
        trashedAtMillis = if ("trash" in localFields) local.trashedAtMillis else merged.trashedAtMillis,
        updatedAtMillis = maxOf(local.updatedAtMillis, cloud.updatedAtMillis),
        lastSyncedAtMillis = local.lastSyncedAtMillis,
    )
}

private fun mergeAttachment(
    local: TodoItemEntity,
    cloud: RemoteTodoAttachmentRecord,
    service: TodoAttachmentSyncService?,
): TodoItemEntity {
    val localAttachmentDirty = local.imageSyncState in setOf(
        TodoImageSyncState.LocalOnly,
        TodoImageSyncState.PendingUpload,
        TodoImageSyncState.MetadataPending,
        TodoImageSyncState.PendingDelete,
        TodoImageSyncState.Error,
    )
    val localUpdated = local.imageUpdatedAtMillis ?: Long.MIN_VALUE
    if (localAttachmentDirty && localUpdated > cloud.updatedAtMillis) {
        return local.copy(imageRemoteVersion = cloud.remoteVersion)
    }
    if (cloud.deletedAtMillis != null) {
        return local.copy(
            imageLocalName = null,
            imageRemotePath = null,
            imageSyncState = TodoImageSyncState.LocalOnly,
            imageAttachmentId = null,
            imageContentSha256 = null,
            imageContentType = null,
            imageByteSize = null,
            imageUpdatedAtMillis = cloud.updatedAtMillis,
            imageRemoteVersion = cloud.remoteVersion,
            imageLastSyncError = null,
        )
    }
    val localMatches = service?.localFileMatches(local.imageLocalName, cloud.contentSha256) == true
    val cacheName = if (localMatches) local.imageLocalName else service?.cacheFileName(cloud)
    return local.copy(
        imageLocalName = cacheName,
        imageRemotePath = cloud.objectPath,
        imageSyncState = if (localMatches) TodoImageSyncState.Synced else TodoImageSyncState.RemoteOnly,
        imageAttachmentId = cloud.attachmentId,
        imageContentSha256 = cloud.contentSha256,
        imageContentType = cloud.contentType,
        imageByteSize = cloud.byteSize,
        imageUpdatedAtMillis = cloud.updatedAtMillis,
        imageRemoteVersion = cloud.remoteVersion,
        imageLastSyncError = null,
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
    val applicable = cloud.filterNot {
        it.recordType == SyncRecordTypeChecklist && isSpecialChecklistId(it.localId)
    }
    if (applicable.isEmpty()) return this
    val keys = applicable.mapTo(mutableSetOf()) { it.recordType + ":" + it.localId }
    val deletedChecklistIds = applicable
        .filter { it.recordType == SyncRecordTypeChecklist }
        .mapTo(mutableSetOf()) { it.localId }
    return copy(
        checklists = checklists.filterNot { (SyncRecordTypeChecklist + ":" + it.localId) in keys },
        items = items.filterNot {
            (SyncRecordTypeItem + ":" + it.localId) in keys || it.checklistLocalId in deletedChecklistIds
        },
        tombstones = tombstones.filterNot { (it.recordType + ":" + it.localId) in keys } + applicable.map {
            SyncTombstoneEntity(
                owner, it.recordType, it.localId, it.deletedAtMillis, it.remoteVersion,
                SyncRecordState.SYNCED.name, now,
            )
        },
    )
}

private fun TodoEntitySet.withOwner(owner: String) = copy(
    checklists = checklists.map {
        when {
            isSpecialChecklistId(it.localId) -> it.copy(
                remoteId = null,
                ownerUserId = null,
                syncState = SyncRecordState.LOCAL_ONLY.name,
                lastSyncedAtMillis = null,
                remoteVersion = null,
                lastSyncError = null,
            )
            it.ownerUserId == null -> it.copy(
                ownerUserId = owner,
                syncState = SyncRecordState.NOT_SYNCED.name,
            )
            else -> it
        }
    },
    items = items.map {
        if (it.ownerUserId == null) it.copy(ownerUserId = owner, syncState = SyncRecordState.NOT_SYNCED.name) else it
    },
)

private fun TodoEntitySet.dirtySnapshot(owner: String, conflicts: Set<String>) = RemoteTodoSnapshot(
    checklists = checklists.filter {
        !isSpecialChecklistId(it.localId) && it.ownerUserId == owner && it.syncState.isDirty() &&
            (SyncRecordTypeChecklist + ":" + it.localId) !in conflicts
    }.map { it.toRemote(owner) },
    items = items.filter {
        it.ownerUserId == owner && it.syncState.isDirty() && (SyncRecordTypeItem + ":" + it.localId) !in conflicts
    }.map { it.toRemote(owner) },
    attachments = items.filter {
        it.ownerUserId == owner &&
            it.imageSyncState in setOf(TodoImageSyncState.MetadataPending, TodoImageSyncState.PendingDelete) &&
            (SyncRecordTypeAttachment + ":" + it.localId) !in conflicts
    }.mapNotNull { it.toRemoteAttachment(owner) },
)

private fun TodoEntitySet.buildPristine(owner: String, previous: RemoteTodoSnapshot): RemoteTodoSnapshot {
    val deleted = tombstones.mapTo(mutableSetOf()) { it.recordType + ":" + it.localId }
    val checklistMap = checklists.filter {
        it.ownerUserId == owner && !isSpecialChecklistId(it.localId)
    }.associateBy { it.localId }
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
        attachments = (previous.attachments.filter { remote ->
            val local = itemMap[remote.todoLocalId]
            local != null && local.imageSyncState != TodoImageSyncState.Synced &&
                (SyncRecordTypeItem + ":" + remote.todoLocalId) !in deleted
        } + itemMap.values.mapNotNull { local ->
            local.takeIf {
                it.imageSyncState in setOf(TodoImageSyncState.Synced, TodoImageSyncState.RemoteOnly)
            }?.toRemoteAttachment(owner)
        }).distinctBy { it.todoLocalId },
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
    val sourceAttachments = source.snapshot.attachments.associateBy { it.todoLocalId }
    val acceptedAttachments = result.accepted.attachments.associateBy { it.todoLocalId }
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
            val itemWithMetadata = if (local.ownerUserId == owner && sent != null && accepted != null) {
                val unchanged = local.toRemote(owner).samePayload(sent)
                local.copy(
                    remoteId = accepted.remoteId ?: local.remoteId,
                    remoteVersion = accepted.remoteVersion ?: local.remoteVersion,
                    syncState = if (unchanged) SyncRecordState.SYNCED.name else SyncRecordState.NOT_SYNCED.name,
                    lastSyncedAtMillis = if (unchanged) now else local.lastSyncedAtMillis,
                    lastSyncError = null,
                )
            } else local
            val sentAttachment = sourceAttachments[local.localId]
            val acceptedAttachment = acceptedAttachments[local.localId]
            if (local.ownerUserId == owner && sentAttachment != null && acceptedAttachment != null) {
                if (acceptedAttachment.deletedAtMillis != null) {
                    itemWithMetadata.copy(
                        imageLocalName = null,
                        imageRemotePath = null,
                        imageSyncState = TodoImageSyncState.LocalOnly,
                        imageAttachmentId = null,
                        imageContentSha256 = null,
                        imageContentType = null,
                        imageByteSize = null,
                        imageUpdatedAtMillis = acceptedAttachment.updatedAtMillis,
                        imageRemoteVersion = acceptedAttachment.remoteVersion,
                        imageLastSyncError = null,
                    )
                } else {
                    itemWithMetadata.copy(
                        imageRemotePath = acceptedAttachment.objectPath,
                        imageSyncState = TodoImageSyncState.Synced,
                        imageAttachmentId = acceptedAttachment.attachmentId,
                        imageContentSha256 = acceptedAttachment.contentSha256,
                        imageContentType = acceptedAttachment.contentType,
                        imageByteSize = acceptedAttachment.byteSize,
                        imageUpdatedAtMillis = acceptedAttachment.updatedAtMillis,
                        imageRemoteVersion = acceptedAttachment.remoteVersion,
                        imageLastSyncError = null,
                    )
                }
            } else itemWithMetadata
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
        checklists = snapshot.checklists.filter {
            !isSpecialChecklistId(it.localId) &&
                (SyncRecordTypeChecklist + ":" + it.localId) !in conflicts
        },
        items = snapshot.items.filter { (SyncRecordTypeItem + ":" + it.localId) !in conflicts },
        attachments = snapshot.attachments.filter {
            (SyncRecordTypeAttachment + ":" + it.todoLocalId) !in conflicts
        },
    ),
    settings = settings.takeIf { (SyncRecordTypeSettings + ":language") !in conflicts },
    tombstones = tombstones.filter {
        !(it.recordType == SyncRecordTypeChecklist && isSpecialChecklistId(it.localId)) &&
            (it.recordType + ":" + it.localId) !in conflicts
    },
)

private fun SyncMutationRecord.isEmpty() = snapshot.checklists.isEmpty() && snapshot.items.isEmpty() &&
    snapshot.attachments.isEmpty() && settings == null && tombstones.isEmpty() && cleanedImagePaths.isEmpty()

private fun TodoChecklistEntity.toRemote(owner: String) = RemoteChecklistRecord(
    localId, remoteId, owner, sortIndex, name, createdAtMillis, updatedAtMillis, remoteVersion,
)

private fun TodoItemEntity.toRemote(owner: String) = RemoteTodoItemRecord(
    localId = localId,
    remoteId = remoteId,
    ownerUserId = owner,
    checklistLocalId = checklistLocalId,
    sortIndex = sortIndex,
    title = title,
    priority = priority,
    dueAtMillis = dueAtMillis,
    completed = completed,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
    reminderRepeat = reminderRepeat,
    imageLocalName = null,
    imageRemotePath = null,
    imageSyncState = SyncRecordState.LOCAL_ONLY.name,
    trashedFromChecklistId = trashedFromChecklistId,
    trashedFromChecklistName = trashedFromChecklistName,
    trashedAtMillis = trashedAtMillis,
    remoteVersion = remoteVersion,
)

private fun TodoItemEntity.toRemoteAttachment(owner: String): RemoteTodoAttachmentRecord? {
    val updatedAt = imageUpdatedAtMillis ?: updatedAtMillis
    if (imageSyncState == TodoImageSyncState.PendingDelete) {
        return RemoteTodoAttachmentRecord(
            ownerUserId = owner,
            todoLocalId = localId,
            updatedAtMillis = updatedAt,
            deletedAtMillis = updatedAt,
            remoteVersion = imageRemoteVersion,
        )
    }
    return RemoteTodoAttachmentRecord(
        ownerUserId = owner,
        todoLocalId = localId,
        attachmentId = imageAttachmentId ?: return null,
        objectPath = imageRemotePath ?: return null,
        contentSha256 = imageContentSha256 ?: return null,
        contentType = imageContentType ?: return null,
        byteSize = imageByteSize ?: return null,
        updatedAtMillis = updatedAt,
        remoteVersion = imageRemoteVersion,
    )
}

private fun SyncTombstoneEntity.toRemote() = RemoteTombstoneRecord(
    ownerUserId, recordType, localId, deletedAtMillis, remoteVersion,
)

private fun RemoteChecklistRecord.toEntity(now: Long, state: SyncRecordState) = TodoChecklistEntity(
    localId, sortIndex, remoteId, ownerUserId, name, createdAtMillis, updatedAtMillis,
    state.name, if (state == SyncRecordState.SYNCED) now else null, remoteVersion,
)

private fun RemoteTodoItemRecord.toEntity(now: Long, state: SyncRecordState) = TodoItemEntity(
    localId, checklistLocalId, sortIndex, remoteId, ownerUserId, title, priority, dueAtMillis,
    completed, createdAtMillis, updatedAtMillis, reminderRepeat, null, null,
    SyncRecordState.LOCAL_ONLY.name, trashedFromChecklistId, trashedFromChecklistName, trashedAtMillis,
    state.name, if (state == SyncRecordState.SYNCED) now else null, remoteVersion,
)

private fun TodoItemEntity.withLocalImageFrom(local: TodoItemEntity?) = copy(
    imageLocalName = local?.imageLocalName,
    imageRemotePath = local?.imageRemotePath,
    imageSyncState = local?.imageSyncState ?: SyncRecordState.LOCAL_ONLY.name,
    imageAttachmentId = local?.imageAttachmentId,
    imageContentSha256 = local?.imageContentSha256,
    imageContentType = local?.imageContentType,
    imageByteSize = local?.imageByteSize,
    imageUpdatedAtMillis = local?.imageUpdatedAtMillis,
    imageRemoteVersion = local?.imageRemoteVersion,
    imageLastSyncError = local?.imageLastSyncError,
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
        trashedFromChecklistId == other.trashedFromChecklistId &&
        trashedFromChecklistName == other.trashedFromChecklistName && trashedAtMillis == other.trashedAtMillis

private val LocalSyncConflictRecord.key get() = recordType + ":" + localId
private fun String.isDirty() = this == SyncRecordState.LOCAL_ONLY.name ||
    this == SyncRecordState.NOT_SYNCED.name || this == SyncRecordState.ERROR.name

private val SyncJson = Json { encodeDefaults = true }
private fun LocalSyncConflictRecord.localChecklist() =
    decodeConflictPayload("local checklist") {
        SyncJson.decodeFromString<RemoteChecklistRecord>(localPayloadJson)
    }
private fun LocalSyncConflictRecord.cloudChecklist() =
    decodeConflictPayload("cloud checklist") {
        SyncJson.decodeFromString<RemoteChecklistRecord>(remotePayloadJson)
    }
private fun LocalSyncConflictRecord.localItem() =
    decodeConflictPayload("local item") {
        SyncJson.decodeFromString<RemoteTodoItemRecord>(localPayloadJson)
    }
private fun LocalSyncConflictRecord.cloudItem() =
    decodeConflictPayload("cloud item") {
        SyncJson.decodeFromString<RemoteTodoItemRecord>(remotePayloadJson)
    }
private fun LocalSyncConflictRecord.localSettings() =
    decodeConflictPayload("local settings") {
        SyncJson.decodeFromString<RemoteUserSettingsRecord>(localPayloadJson)
    }
private fun LocalSyncConflictRecord.cloudSettings() =
    decodeConflictPayload("cloud settings") {
        SyncJson.decodeFromString<RemoteUserSettingsRecord>(remotePayloadJson)
    }

private fun LocalSyncConflictRecord.isSyntheticChecklistConflict(): Boolean =
    recordType == SyncRecordTypeChecklist && localId in setOf(TrashChecklistId, SettingsChecklistId)

private fun LocalSyncConflictRecord.toPresentation(
    checklistNames: Map<String, String>,
): SyncConflictEntry = when (recordType) {
    SyncRecordTypeChecklist -> {
        val local = localChecklist()
        val cloud = cloudChecklist()
        SyncConflictEntry(
            recordType, localId, firstNonBlank(local.name, cloud.name, localId), fields, message,
            fields.map { SyncConflictValue(it, local.valueFor(it)) },
            fields.map { SyncConflictValue(it, cloud.valueFor(it)) },
        )
    }
    SyncRecordTypeItem -> {
        val local = localItem()
        val cloud = cloudItem()
        SyncConflictEntry(
            recordType, localId, firstNonBlank(local.title, cloud.title, localId), fields, message,
            fields.map { SyncConflictValue(it, local.valueFor(it, checklistNames)) },
            fields.map { SyncConflictValue(it, cloud.valueFor(it, checklistNames)) },
        )
    }
    SyncRecordTypeSettings -> {
        val local = localSettings()
        val cloud = cloudSettings()
        SyncConflictEntry(
            recordType, localId, local.languageMode, fields, message,
            listOf(SyncConflictValue("language", local.languageMode)),
            listOf(SyncConflictValue("language", cloud.languageMode)),
        )
    }
    else -> throw SyncMetadataCorruptException("Unknown conflict record type '$recordType'.")
}

private inline fun <T> decodeConflictPayload(label: String, block: () -> T): T = try {
    block()
} catch (error: Exception) {
    throw SyncMetadataCorruptException("Invalid current-format $label conflict payload.", error)
}

private fun RemoteChecklistRecord.valueFor(field: String) = when (field) {
    "sort" -> (sortIndex + 1).toString()
    else -> name
}

private fun RemoteTodoItemRecord.valueFor(field: String, checklistNames: Map<String, String>) = when (field) {
    "checklist" -> checklistNames[checklistLocalId] ?: checklistLocalId
    "sort" -> (sortIndex + 1).toString()
    "title" -> title
    "priority" -> priority
    "due" -> dueAtMillis.formatMillis()
    "completed" -> if (completed) "COMPLETED" else "ACTIVE"
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
