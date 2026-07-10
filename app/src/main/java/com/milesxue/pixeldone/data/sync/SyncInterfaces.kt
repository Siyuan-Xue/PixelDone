package com.milesxue.pixeldone.data.sync

import com.milesxue.pixeldone.data.local.TodoEntitySet
import com.milesxue.pixeldone.domain.sync.AuthSession
import com.milesxue.pixeldone.domain.sync.ConflictResolutionChoice
import com.milesxue.pixeldone.domain.sync.SyncConflictEntry
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
    suspend fun loadConflicts(): List<SyncConflictEntry>
    suspend fun resolveConflict(recordType: String, localId: String, choice: ConflictResolutionChoice): SyncCoordinatorStatus
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
    suspend fun recordConflict(ownerUserId: String, conflict: LocalSyncConflictRecord)
    suspend fun loadConflicts(ownerUserId: String): List<LocalSyncConflictRecord>
    suspend fun loadConflict(ownerUserId: String, recordType: String, localId: String): LocalSyncConflictRecord?
    suspend fun clearConflict(ownerUserId: String, recordType: String, localId: String)
}

internal interface SettingsSyncLocalStore {
    suspend fun loadSettingsForSync(nowMillis: Long): LocalSettingsSyncRecord
    suspend fun applyRemoteSettings(record: RemoteUserSettingsRecord, syncedAtMillis: Long)
    suspend fun markSettingsSynced(record: RemoteUserSettingsRecord, syncedAtMillis: Long)
    suspend fun applyLocalSettingsForUpload(record: RemoteUserSettingsRecord)
    suspend fun markSettingsSyncError(message: String)
}

class LocalOnlySyncCoordinator : SyncCoordinator {
    private val localOnlyRunState = MutableStateFlow(SyncRunState(status = SyncCoordinatorStatus.LOCAL_ONLY))
    override val runState: StateFlow<SyncRunState> = localOnlyRunState.asStateFlow()
    override val status: StateFlow<SyncCoordinatorStatus> = MutableStateFlow(SyncCoordinatorStatus.LOCAL_ONLY).asStateFlow()

    override suspend fun syncNow(): SyncCoordinatorStatus = SyncCoordinatorStatus.LOCAL_ONLY
    override suspend fun loadConflicts(): List<SyncConflictEntry> = emptyList()
    override suspend fun resolveConflict(
        recordType: String,
        localId: String,
        choice: ConflictResolutionChoice,
    ): SyncCoordinatorStatus = SyncCoordinatorStatus.LOCAL_ONLY
    override fun requestSync() = Unit
}

class SyncConfigurationException(message: String) : Exception(message)
class SyncRemoteException(message: String, val statusCode: Int? = null) : Exception(message)
class SyncSchemaMismatchException(message: String) : Exception(message)

@Serializable
data class RemoteTodoSnapshot(
    val checklists: List<RemoteChecklistRecord> = emptyList(),
    val items: List<RemoteTodoItemRecord> = emptyList(),
)

@Serializable
data class RemoteChangeBatch(
    @kotlinx.serialization.SerialName("schema_version") val schemaVersion: String,
    @kotlinx.serialization.SerialName("server_version") val serverVersion: Long,
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
    val tombstones: List<RemoteTombstoneRecord> = emptyList(),
)

@Serializable
data class RemotePushResult(
    @kotlinx.serialization.SerialName("schema_version") val schemaVersion: String,
    val accepted: RemoteTodoSnapshot = RemoteTodoSnapshot(),
    val settings: RemoteUserSettingsRecord? = null,
    val tombstones: List<RemoteTombstoneRecord> = emptyList(),
    val conflicts: List<RemoteConflictRecord> = emptyList(),
    @kotlinx.serialization.SerialName("server_version") val serverVersion: Long,
)

@Serializable
data class SyncMutationRecord(
    val mutationUuid: String,
    val snapshot: RemoteTodoSnapshot = RemoteTodoSnapshot(),
    val settings: RemoteUserSettingsRecord? = null,
    val tombstones: List<RemoteTombstoneRecord> = emptyList(),
    val createdAtMillis: Long,
    val attempts: Int = 0,
    val lastError: String? = null,
)

@Serializable
data class LocalSyncConflictRecord(
    val recordType: String,
    val localId: String,
    val localPayloadJson: String,
    val remotePayloadJson: String,
    val fields: List<String>,
    val message: String,
    val remoteVersion: Long? = null,
    val createdAtMillis: Long,
)

@Serializable
data class RemoteConflictRecord(
    @kotlinx.serialization.SerialName("record_type") val recordType: String,
    @kotlinx.serialization.SerialName("local_id") val localId: String,
    val message: String,
)

@Serializable
data class RemoteTombstoneRecord(
    @kotlinx.serialization.SerialName("owner_user_id") val ownerUserId: String? = null,
    @kotlinx.serialization.SerialName("record_type") val recordType: String,
    @kotlinx.serialization.SerialName("local_id") val localId: String,
    @kotlinx.serialization.SerialName("deleted_at_millis") val deletedAtMillis: Long,
    @kotlinx.serialization.SerialName("remote_version") val remoteVersion: Long? = null,
)

@Serializable
data class RemoteChecklistRecord(
    @kotlinx.serialization.SerialName("local_id") val localId: String,
    @kotlinx.serialization.SerialName("id") val remoteId: String? = null,
    @kotlinx.serialization.SerialName("owner_user_id") val ownerUserId: String,
    @kotlinx.serialization.SerialName("sort_index") val sortIndex: Int,
    val name: String,
    @kotlinx.serialization.SerialName("created_at_millis") val createdAtMillis: Long,
    @kotlinx.serialization.SerialName("updated_at_millis") val updatedAtMillis: Long,
    @kotlinx.serialization.SerialName("remote_version") val remoteVersion: Long? = null,
)

@Serializable
data class RemoteTodoItemRecord(
    @kotlinx.serialization.SerialName("local_id") val localId: String,
    @kotlinx.serialization.SerialName("id") val remoteId: String? = null,
    @kotlinx.serialization.SerialName("owner_user_id") val ownerUserId: String,
    @kotlinx.serialization.SerialName("checklist_local_id") val checklistLocalId: String,
    @kotlinx.serialization.SerialName("sort_index") val sortIndex: Int,
    val title: String,
    val priority: String,
    @kotlinx.serialization.SerialName("due_at_millis") val dueAtMillis: Long,
    val completed: Boolean,
    @kotlinx.serialization.SerialName("created_at_millis") val createdAtMillis: Long,
    @kotlinx.serialization.SerialName("updated_at_millis") val updatedAtMillis: Long,
    @kotlinx.serialization.SerialName("reminder_repeat") val reminderRepeat: String,
    @kotlinx.serialization.SerialName("image_local_name") val imageLocalName: String? = null,
    @kotlinx.serialization.SerialName("image_remote_path") val imageRemotePath: String? = null,
    @kotlinx.serialization.SerialName("image_sync_state") val imageSyncState: String,
    @kotlinx.serialization.SerialName("trashed_from_checklist_id") val trashedFromChecklistId: String? = null,
    @kotlinx.serialization.SerialName("trashed_from_checklist_name") val trashedFromChecklistName: String? = null,
    @kotlinx.serialization.SerialName("trashed_at_millis") val trashedAtMillis: Long? = null,
    @kotlinx.serialization.SerialName("remote_version") val remoteVersion: Long? = null,
)

@Serializable
data class RemoteUserSettingsRecord(
    @kotlinx.serialization.SerialName("owner_user_id") val ownerUserId: String? = null,
    @kotlinx.serialization.SerialName("language_mode") val languageMode: String,
    @kotlinx.serialization.SerialName("updated_at_millis") val updatedAtMillis: Long,
    @kotlinx.serialization.SerialName("remote_version") val remoteVersion: Long? = null,
)

data class LocalSettingsSyncRecord(
    val languageMode: String,
    val updatedAtMillis: Long,
    val syncState: String,
    val lastSyncedAtMillis: Long? = null,
    val remoteVersion: Long? = null,
    val lastSyncError: String? = null,
) {
    fun toRemoteRecord(ownerUserId: String): RemoteUserSettingsRecord = RemoteUserSettingsRecord(
        ownerUserId = ownerUserId,
        languageMode = languageMode,
        updatedAtMillis = updatedAtMillis,
        remoteVersion = remoteVersion,
    )
}

interface RemoteTodoDataSource {
    suspend fun pullChanges(session: AuthSession, sinceVersion: Long?): RemoteChangeBatch
    suspend fun pushMutations(session: AuthSession, batch: RemoteMutationBatch): RemotePushResult
}

const val ExpectedRemoteSchemaVersion = "3.1"
