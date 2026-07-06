package com.milesxue.pixeldone.data.sync

import com.milesxue.pixeldone.data.local.TodoEntitySet
import com.milesxue.pixeldone.domain.sync.AuthSession
import com.milesxue.pixeldone.domain.sync.SyncCoordinatorStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface AuthSessionRepository {
    val session: StateFlow<AuthSession>
    suspend fun signIn(email: String, password: String): AuthSession
    suspend fun signUp(email: String, password: String): AuthSession
    suspend fun signOut()
    suspend fun refreshSessionIfNeeded(nowMillis: Long, force: Boolean = false): AuthSession
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
    suspend fun syncNow(): SyncCoordinatorStatus
    fun requestSync()
}

internal interface TodoSyncLocalStore {
    suspend fun loadEntitySetForSync(nowMillis: Long): TodoEntitySet
    suspend fun replaceEntitySetFromSync(entitySet: TodoEntitySet)
    suspend fun updateEntitySetFromSync(
        nowMillis: Long,
        transform: (TodoEntitySet) -> TodoEntitySet,
    ): TodoEntitySet
}

class LocalOnlySyncCoordinator : SyncCoordinator {
    private val localOnlyStatus = MutableStateFlow(SyncCoordinatorStatus.LOCAL_ONLY)
    override val status: StateFlow<SyncCoordinatorStatus> = localOnlyStatus.asStateFlow()

    override suspend fun syncNow(): SyncCoordinatorStatus = SyncCoordinatorStatus.LOCAL_ONLY
    override fun requestSync() = Unit
}

class SyncConfigurationException(message: String) : Exception(message)
class SyncRemoteException(message: String, val statusCode: Int? = null) : Exception(message)

data class RemoteTodoSnapshot(
    val checklists: List<RemoteChecklistRecord> = emptyList(),
    val items: List<RemoteTodoItemRecord> = emptyList(),
)

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

interface RemoteTodoDataSource {
    suspend fun pullSnapshot(session: AuthSession): RemoteTodoSnapshot
    suspend fun pushSnapshot(session: AuthSession, snapshot: RemoteTodoSnapshot): RemoteTodoSnapshot
}