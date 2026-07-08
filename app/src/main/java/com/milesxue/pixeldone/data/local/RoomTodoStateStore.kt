package com.milesxue.pixeldone.data.local

import android.content.Context
import androidx.room.Room
import com.milesxue.pixeldone.data.sync.LocalSyncConflictRecord
import com.milesxue.pixeldone.data.sync.RemoteChecklistRecord
import com.milesxue.pixeldone.data.sync.RemoteTodoItemRecord
import com.milesxue.pixeldone.data.sync.RemoteTodoSnapshot
import com.milesxue.pixeldone.data.sync.SyncMutationRecord
import com.milesxue.pixeldone.data.sync.TodoSyncLocalStore
import com.milesxue.pixeldone.data.todo.TodoPreferences
import com.milesxue.pixeldone.data.todo.TodoStateStore
import com.milesxue.pixeldone.domain.todo.TodoChecklistState
import com.milesxue.pixeldone.domain.todo.createInitialChecklistState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class RoomTodoStateStore private constructor(
    private val database: PixelDoneDatabase,
    private val legacyPreferences: TodoPreferences,
) : TodoStateStore, TodoSyncLocalStore {
    private val dao = database.todoDao()
    private val writeMutex = Mutex()
    private val observerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncJson = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    override fun loadTodoState(nowMillis: Long): TodoChecklistState = runBlocking(Dispatchers.IO) {
        writeMutex.withLock {
            ensureMigrated(nowMillis)
            loadStateFromRoom(nowMillis) ?: createInitialChecklistState(emptyList(), nowMillis).also { state ->
                dao.replaceState(state.toTodoEntitySet(nowMillis))
            }
        }
    }

    override fun saveTodoState(state: TodoChecklistState) {
        val nowMillis = System.currentTimeMillis()
        runBlocking(Dispatchers.IO) {
            writeMutex.withLock {
                ensureMigrated(nowMillis)
                val previous = dao.getEntitySet()
                dao.replaceState(state.toTodoEntitySet(nowMillis, previousEntitySet = previous))
            }
        }
    }

    override fun observeTodoState(onChange: () -> Unit): () -> Unit {
        runBlocking(Dispatchers.IO) {
            writeMutex.withLock { ensureMigrated(System.currentTimeMillis()) }
        }
        val job = observerScope.launch {
            combine(
                dao.observeChecklists(),
                dao.observeItems(),
                dao.observeMetadata(),
            ) { _, _, _ -> Unit }
                .drop(1)
                .collect { onChange() }
        }
        return { job.cancel() }
    }

    override suspend fun loadEntitySetForSync(nowMillis: Long): TodoEntitySet = writeMutex.withLock {
        ensureMigrated(nowMillis)
        dao.getEntitySet() ?: createInitialChecklistState(emptyList(), nowMillis)
            .toTodoEntitySet(nowMillis)
            .also { dao.replaceState(it) }
    }

    override suspend fun replaceEntitySetFromSync(entitySet: TodoEntitySet) {
        writeMutex.withLock { dao.replaceState(entitySet) }
    }

    override suspend fun updateEntitySetFromSync(
        nowMillis: Long,
        transform: (TodoEntitySet) -> TodoEntitySet,
    ): TodoEntitySet = writeMutex.withLock {
        ensureMigrated(nowMillis)
        val current = dao.getEntitySet() ?: createInitialChecklistState(emptyList(), nowMillis)
            .toTodoEntitySet(nowMillis)
        val updated = transform(current)
        dao.replaceState(updated)
        updated
    }

    override suspend fun loadSyncCursor(ownerUserId: String): Long? = writeMutex.withLock {
        dao.getSyncCursor(ownerUserId)?.remoteVersion
    }

    override suspend fun saveSyncCursor(ownerUserId: String, remoteVersion: Long, updatedAtMillis: Long) {
        writeMutex.withLock {
            dao.insertSyncCursor(
                SyncCursorEntity(
                    ownerUserId = ownerUserId,
                    remoteVersion = remoteVersion,
                    updatedAtMillis = updatedAtMillis,
                ),
            )
        }
    }

    override suspend fun loadPristineSnapshot(ownerUserId: String): RemoteTodoSnapshot = writeMutex.withLock {
        val records = dao.getPristineRecords(ownerUserId)
        RemoteTodoSnapshot(
            checklists = records
                .filter { it.recordType == SyncRecordTypeChecklist }
                .mapNotNull { record ->
                    runCatching {
                        syncJson.decodeFromString(RemoteChecklistRecord.serializer(), record.payloadJson)
                    }.getOrNull()
                },
            items = records
                .filter { it.recordType == SyncRecordTypeItem }
                .mapNotNull { record ->
                    runCatching {
                        syncJson.decodeFromString(RemoteTodoItemRecord.serializer(), record.payloadJson)
                    }.getOrNull()
                },
        )
    }

    override suspend fun savePristineSnapshot(ownerUserId: String, snapshot: RemoteTodoSnapshot, syncedAtMillis: Long) {
        writeMutex.withLock {
            val records = snapshot.checklists.map { checklist ->
                SyncPristineRecordEntity(
                    ownerUserId = ownerUserId,
                    recordType = SyncRecordTypeChecklist,
                    localId = checklist.localId,
                    payloadJson = syncJson.encodeToString(RemoteChecklistRecord.serializer(), checklist),
                    remoteVersion = checklist.remoteVersion,
                    updatedAtMillis = syncedAtMillis,
                )
            } + snapshot.items.map { item ->
                SyncPristineRecordEntity(
                    ownerUserId = ownerUserId,
                    recordType = SyncRecordTypeItem,
                    localId = item.localId,
                    payloadJson = syncJson.encodeToString(RemoteTodoItemRecord.serializer(), item),
                    remoteVersion = item.remoteVersion,
                    updatedAtMillis = syncedAtMillis,
                )
            }
            dao.replacePristineRecords(ownerUserId, records)
        }
    }

    override suspend fun loadPendingMutations(ownerUserId: String): List<SyncMutationRecord> = writeMutex.withLock {
        dao.getSyncMutations(ownerUserId).mapNotNull { entity ->
            runCatching {
                syncJson.decodeFromString(SyncMutationRecord.serializer(), entity.payloadJson).copy(
                    attempts = entity.attempts,
                    lastError = entity.lastError,
                )
            }.getOrNull()
        }
    }

    override suspend fun recordPendingMutation(ownerUserId: String, mutation: SyncMutationRecord) {
        writeMutex.withLock {
            dao.insertSyncMutation(
                SyncMutationEntity(
                    ownerUserId = ownerUserId,
                    mutationUuid = mutation.mutationUuid,
                    payloadJson = syncJson.encodeToString(SyncMutationRecord.serializer(), mutation),
                    createdAtMillis = mutation.createdAtMillis,
                    attempts = mutation.attempts,
                    lastError = mutation.lastError,
                ),
            )
        }
    }

    override suspend fun clearPendingMutation(ownerUserId: String, mutationUuid: String) {
        writeMutex.withLock { dao.deleteSyncMutation(ownerUserId, mutationUuid) }
    }

    override suspend fun recordConflict(ownerUserId: String, conflict: LocalSyncConflictRecord) {
        writeMutex.withLock {
            dao.insertSyncConflict(
                SyncConflictRecordEntity(
                    ownerUserId = ownerUserId,
                    recordType = conflict.recordType,
                    localId = conflict.localId,
                    localPayloadJson = conflict.localPayloadJson,
                    remotePayloadJson = conflict.remotePayloadJson,
                    fieldsJson = syncJson.encodeToString(ListSerializer(String.serializer()), conflict.fields),
                    message = conflict.message,
                    remoteVersion = conflict.remoteVersion,
                    createdAtMillis = conflict.createdAtMillis,
                ),
            )
        }
    }

    override suspend fun loadConflicts(ownerUserId: String): List<LocalSyncConflictRecord> = writeMutex.withLock {
        dao.getSyncConflicts(ownerUserId).map { it.toLocalSyncConflictRecord() }
    }

    override suspend fun loadConflict(
        ownerUserId: String,
        recordType: String,
        localId: String,
    ): LocalSyncConflictRecord? = writeMutex.withLock {
        dao.getSyncConflict(ownerUserId, recordType, localId)?.toLocalSyncConflictRecord()
    }

    override suspend fun clearConflict(ownerUserId: String, recordType: String, localId: String) {
        writeMutex.withLock { dao.deleteSyncConflict(ownerUserId, recordType, localId) }
    }

    private suspend fun ensureMigrated(nowMillis: Long) {
        if (dao.metadataCount() > 0) return
        val legacyState = legacyPreferences.loadTodoState(nowMillis)
        dao.replaceState(legacyState.toTodoEntitySet(nowMillis))
    }

    private suspend fun loadStateFromRoom(nowMillis: Long): TodoChecklistState? {
        return todoEntitiesToState(
            metadata = dao.getMetadata(),
            checklists = dao.getChecklists(),
            items = dao.getItems(),
            fallbackCreatedAtMillis = nowMillis,
        )
    }

    private fun SyncConflictRecordEntity.toLocalSyncConflictRecord(): LocalSyncConflictRecord = LocalSyncConflictRecord(
        recordType = recordType,
        localId = localId,
        localPayloadJson = localPayloadJson,
        remotePayloadJson = remotePayloadJson,
        fields = runCatching {
            syncJson.decodeFromString(ListSerializer(String.serializer()), fieldsJson)
        }.getOrDefault(emptyList()),
        message = message,
        remoteVersion = remoteVersion,
        createdAtMillis = createdAtMillis,
    )

    companion object {
        private const val DatabaseName = "pixel_done_local.db"

        fun create(context: Context, legacyPreferences: TodoPreferences): RoomTodoStateStore {
            val database = Room.databaseBuilder(
                context.applicationContext,
                PixelDoneDatabase::class.java,
                DatabaseName,
            ).addMigrations(
                PixelDoneMigrations.Migration1To2,
                PixelDoneMigrations.Migration2To3,
                PixelDoneMigrations.Migration3To4,
            ).build()
            return RoomTodoStateStore(database, legacyPreferences)
        }
    }
}
