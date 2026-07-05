package com.milesxue.pixeldone.data.sync

import com.milesxue.pixeldone.domain.sync.AuthSession
import com.milesxue.pixeldone.domain.sync.SyncCoordinatorStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface AuthSessionRepository {
    val session: StateFlow<AuthSession>
}

class LocalOnlyAuthSessionRepository : AuthSessionRepository {
    private val localOnlySession = MutableStateFlow(AuthSession())
    override val session: StateFlow<AuthSession> = localOnlySession.asStateFlow()
}

interface SyncCoordinator {
    val status: StateFlow<SyncCoordinatorStatus>
}

class LocalOnlySyncCoordinator : SyncCoordinator {
    private val localOnlyStatus = MutableStateFlow(SyncCoordinatorStatus.LOCAL_ONLY)
    override val status: StateFlow<SyncCoordinatorStatus> = localOnlyStatus.asStateFlow()
}

data class RemoteTodoRecord(
    val localId: String?,
    val remoteId: String,
    val ownerUserId: String,
    val checklistLocalId: String?,
    val title: String?,
    val updatedAtMillis: Long,
    val deletedAtMillis: Long?,
    val remoteVersion: Long,
)

interface RemoteTodoDataSource {
    suspend fun pullTodos(ownerUserId: String, sinceMillis: Long?): List<RemoteTodoRecord>
    suspend fun pushTodos(ownerUserId: String, records: List<RemoteTodoRecord>)
}
