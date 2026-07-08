package com.milesxue.pixeldone.data.sync

import com.milesxue.pixeldone.data.local.TodoEntitySet
import com.milesxue.pixeldone.domain.sync.AuthSession
import com.milesxue.pixeldone.domain.sync.SyncCoordinatorStatus
import com.milesxue.pixeldone.domain.sync.SyncRunState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

interface AuthSessionRepository {
    val session: StateFlow<AuthSession>
    suspend fun signIn(email: String, password: String): AuthSession
    suspend fun signUp(email: String, password: String): AuthSession
    suspend fun signOut()
    suspend fun refreshSessionIfNeeded(nowMillis: Long, force: Boolean = false): AuthSession
    suspend fun resetPassword(email: String) {
        throw SyncConfigurationException("Password reset is not configured.")
    }
}

class LocalOnlyAuthSessionRepository : AuthSessionRepository {
    private val localOnlySession = MutableStateFlow(AuthSession())
    override val session: StateFlow<AuthSession> = localOnlySession.asStateFlow()

    override suspend fun signIn(email: String, password: String): AuthSession {
        throw SyncConfigurationException("Cloud sync is not configured.")
    }

    override suspend fun signUp(email: String, password: String): AuthSession {
        throw SyncConfigurationException("Cloud sync is not configured.")
    }

    override suspend fun signOut() = Unit

    override suspend fun refreshSessionIfNeeded(nowMillis: Long, force: Boolean): AuthSession = localOnlySession.value
}

interface SyncCoordinator {
    val status: StateFlow<SyncCoordinatorStatus>
    val runState: StateFlow<SyncRunState>
    suspend fun syncNow(): SyncCoordinatorStatus
    fun requestSync()
}

interface SyncWorkScheduler {
    fun requestSync()
    fun ensurePeriodicSync()
}

object NoOpSyncWorkScheduler : SyncWorkScheduler {
    override fun requestSync() = Unit
    override fun ensurePeriodicSync() = Unit
}

internal interface TodoSyncLocalStore {
    suspend fun loadEntitySetForSync(nowMillis: Long): TodoEntitySet
    suspend fun replaceEntitySetFromSync(entitySet: TodoEntitySet)
    suspend fun updateEntitySetFromSync(
        nowMillis: Long,
        transform: (TodoEntitySet) -> TodoEntitySet,
    ): TodoEntitySet
    suspend fun loadSyncCursor(ownerUserId: String): Long?
    suspend fun saveSyncCursor(ownerUserId: String, remoteVersion: Long, updatedAtMillis: Long)
    suspend fun loadPristineSnapshot(ownerUserId: String): RemoteTodoSnapshot
    suspend fun savePristineSnapshot(ownerUserId: String, snapshot: RemoteTodoSnapshot, syncedAtMillis: Long)
    suspend fun loadPendingMutations(ownerUserId: String): List<SyncMutationRecord>
    suspend fun recordPendingMutation(ownerUserId: String, mutation: SyncMutationRecord)
    suspend fun clearPendingMutation(ownerUserId: String, mutationUuid: String)
}

internal interface SettingsSyncLocalStore {
    suspend fun loadSettingsForSync(nowMillis: Long): LocalSettingsSyncRecord
    suspend fun applyRemoteSettings(record: RemoteUserSettingsRecord, syncedAtMillis: Long)
    suspend fun markSettingsSynced(record: RemoteUserSettingsRecord, syncedAtMillis: Long)
    suspend fun markSettingsSyncError(message: String)
}

class LocalOnlySyncCoordinator : SyncCoordinator {
    private val localOnlyRunState = MutableStateFlow(SyncRunState(status = SyncCoordinatorStatus.LOCAL_ONLY))
    override val runState: StateFlow<SyncRunState> = localOnlyRunState.asStateFlow()
    override val status: StateFlow<SyncCoordinatorStatus> = MutableStateFlow(SyncCoordinatorStatus.LOCAL_ONLY).asStateFlow()

    override suspend fun syncNow(): SyncCoordinatorStatus = SyncCoordinatorStatus.LOCAL_ONLY
    override fun requestSync() = Unit
}

class SyncConfigurationException(message: String) : Exception(message)
class SyncRemoteException(message: String, val statusCode: Int? = null) : Exception(message)

@Serializable
data class RemoteTodoSnapshot(
    val checklists: List<RemoteChecklistRecord> = emptyList(),
    val items: List<RemoteTodoItemRecord> = emptyList(),
)

@Serializable
data class RemoteChangeBatch(
    val serverVersion: Long,
    val checklists: List<RemoteChecklistRecord> = emptyList(),
    val items: List<RemoteTodoItemRecord> = emptyList(),
    val settings: RemoteUserSettingsRecord? = null,
    val tombstones: List<RemoteTombstoneRecord> = emptyList(),
)

@Serializable
data class RemoteMutationBatch(
    val mutationUuid: String,
    val snapshot: RemoteTodoSnapshot = RemoteTodoSnapshot(),
    val settings: RemoteUserSettingsRecord? = null,
)

@Serializable
data class RemotePushResult(
    val accepted: RemoteTodoSnapshot = RemoteTodoSnapshot(),
    val settings: RemoteUserSettingsRecord? = null,
    val conflicts: List<RemoteConflictRecord> = emptyList(),
    val serverVersion: Long,
)

@Serializable
data class SyncMutationRecord(
    val mutationUuid: String,
    val snapshot: RemoteTodoSnapshot = RemoteTodoSnapshot(),
    val settings: RemoteUserSettingsRecord? = null,
    val createdAtMillis: Long,
    val attempts: Int = 0,
    val lastError: String? = null,
)

@Serializable
data class RemoteConflictRecord(
    val recordType: String,
    val localId: String,
    val message: String,
)

@Serializable
data class RemoteTombstoneRecord(
    val recordType: String,
    val localId: String,
    val remoteVersion: Long,
)

@Serializable
data class RemoteChecklistRecord(
    val localId: String,
    val remoteId: String? = null,
    val ownerUserId: String,
    val sortIndex: Int,
    val name: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val deletedAtMillis: Long? = null,
    val remoteVersion: Long? = null,
)

@Serializable
data class RemoteTodoItemRecord(
    val localId: String,
    val remoteId: String? = null,
    val ownerUserId: String,
    val checklistLocalId: String,
    val sortIndex: Int,
    val title: String,
    val priority: String,
    val dueAtMillis: Long,
    val completed: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val deletedAtMillis: Long? = null,
    val reminderRepeat: String,
    val imageLocalName: String? = null,
    val imageRemotePath: String? = null,
    val imageSyncState: String,
    val trashedFromChecklistId: String? = null,
    val trashedFromChecklistName: String? = null,
    val trashedAtMillis: Long? = null,
    val remoteVersion: Long? = null,
)

@Serializable
data class RemoteUserSettingsRecord(
    val ownerUserId: String,
    val darkTheme: Boolean,
    val dockPlusPlacement: String,
    val dockActions: List<String>,
    val updatedAtMillis: Long,
    val remoteVersion: Long? = null,
)

data class LocalSettingsSyncRecord(
    val darkTheme: Boolean,
    val dockPlusPlacement: String,
    val dockActions: List<String>,
    val updatedAtMillis: Long,
    val syncState: String,
    val lastSyncedAtMillis: Long? = null,
    val remoteVersion: Long? = null,
    val lastSyncError: String? = null,
) {
    fun toRemoteRecord(ownerUserId: String): RemoteUserSettingsRecord = RemoteUserSettingsRecord(
        ownerUserId = ownerUserId,
        darkTheme = darkTheme,
        dockPlusPlacement = dockPlusPlacement,
        dockActions = dockActions,
        updatedAtMillis = updatedAtMillis,
        remoteVersion = remoteVersion,
    )
}

interface RemoteTodoDataSource {
    suspend fun pullChanges(session: AuthSession, sinceVersion: Long?): RemoteChangeBatch
    suspend fun pushMutations(session: AuthSession, batch: RemoteMutationBatch): RemotePushResult
}
