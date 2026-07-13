package com.milesxue.pixeldone.data.local

import android.content.Context
import com.milesxue.pixeldone.data.sync.CurrentSyncMetadataFormatVersion
import com.milesxue.pixeldone.data.sync.LocalSyncConflictRecord
import com.milesxue.pixeldone.data.sync.RemoteChecklistRecord
import com.milesxue.pixeldone.data.sync.RemoteTodoAttachmentRecord
import com.milesxue.pixeldone.data.sync.RemoteTodoItemRecord
import com.milesxue.pixeldone.data.sync.RemoteTodoSnapshot
import com.milesxue.pixeldone.data.sync.SyncMetadataCorruptException
import com.milesxue.pixeldone.data.sync.SyncMetadataLocalStore
import com.milesxue.pixeldone.data.sync.SyncMetadataSession
import com.milesxue.pixeldone.data.sync.SyncMutationRecord
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

internal class RoomSyncMetadataStore internal constructor(
    private val database: SyncMetadataDatabase,
) : SyncMetadataLocalStore {
    private val dao = database.syncMetadataDao()
    private val mutex = Mutex()
    private val workingGenerations = mutableMapOf<String, String>()
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
    }

    override suspend fun beginSyncMetadata(ownerUserId: String, nowMillis: Long): SyncMetadataSession =
        mutex.withLock {
            val owner = dao.getOwner(ownerUserId)
            val active = owner?.activeGeneration.takeIf {
                owner?.activeFormatVersion == CurrentSyncMetadataFormatVersion
            }
            val staging = UUID.randomUUID().toString()
            dao.startStagingGeneration(
                ownerUserId = ownerUserId,
                generation = staging,
                formatVersion = CurrentSyncMetadataFormatVersion,
                nowMillis = nowMillis,
                sourceGeneration = active,
            )
            workingGenerations[ownerUserId] = staging
            SyncMetadataSession(generation = staging, rebuilding = active == null)
        }

    override suspend fun completeSyncMetadata(
        ownerUserId: String,
        session: SyncMetadataSession,
        nowMillis: Long,
    ) {
        mutex.withLock {
            check(workingGenerations[ownerUserId] == session.generation)
            dao.activateStagingGeneration(
                ownerUserId = ownerUserId,
                generation = requireNotNull(session.generation),
                formatVersion = CurrentSyncMetadataFormatVersion,
                nowMillis = nowMillis,
            )
            workingGenerations.remove(ownerUserId)
        }
    }

    override suspend fun abortSyncMetadata(ownerUserId: String, session: SyncMetadataSession) {
        mutex.withLock {
            session.generation?.let { dao.discardStagingGeneration(ownerUserId, it) }
            if (workingGenerations[ownerUserId] == session.generation) {
                workingGenerations.remove(ownerUserId)
            }
        }
    }

    override suspend fun invalidateSyncMetadata(ownerUserId: String) {
        mutex.withLock {
            workingGenerations.remove(ownerUserId)
            dao.invalidateOwner(ownerUserId)
        }
    }

    override suspend fun loadSyncCursor(ownerUserId: String): Long? = mutex.withLock {
        val generation = generationForRead(ownerUserId) ?: return@withLock null
        dao.getCursor(ownerUserId, generation)?.remoteVersion
    }

    override suspend fun saveSyncCursor(ownerUserId: String, remoteVersion: Long, updatedAtMillis: Long) {
        mutex.withLock {
            val generation = requireGeneration(ownerUserId)
            dao.insertCursor(SyncCursorEntity(ownerUserId, generation, remoteVersion, updatedAtMillis))
        }
    }

    override suspend fun loadPristineSnapshot(ownerUserId: String): RemoteTodoSnapshot = mutex.withLock {
        val generation = generationForRead(ownerUserId) ?: return@withLock RemoteTodoSnapshot()
        val records = dao.getPristineRecords(ownerUserId, generation)
        val checklists = mutableListOf<RemoteChecklistRecord>()
        val items = mutableListOf<RemoteTodoItemRecord>()
        val attachments = mutableListOf<RemoteTodoAttachmentRecord>()
        records.forEach { record ->
            when (record.recordType) {
                SyncRecordTypeChecklist -> checklists += decode(
                    label = "pristine checklist ${record.localId}",
                ) { json.decodeFromString(RemoteChecklistRecord.serializer(), record.payloadJson) }
                SyncRecordTypeItem -> items += decode(
                    label = "pristine item ${record.localId}",
                ) { json.decodeFromString(RemoteTodoItemRecord.serializer(), record.payloadJson) }
                SyncRecordTypeAttachment -> attachments += decode(
                    label = "pristine attachment ${record.localId}",
                ) { json.decodeFromString(RemoteTodoAttachmentRecord.serializer(), record.payloadJson) }
                else -> throw SyncMetadataCorruptException(
                    "Unknown pristine record type '${record.recordType}'.",
                )
            }
        }
        RemoteTodoSnapshot(checklists, items, attachments)
    }

    override suspend fun savePristineSnapshot(
        ownerUserId: String,
        snapshot: RemoteTodoSnapshot,
        syncedAtMillis: Long,
    ) {
        mutex.withLock {
            val generation = requireGeneration(ownerUserId)
            val records = snapshot.checklists.map { checklist ->
                SyncPristineRecordEntity(
                    ownerUserId,
                    generation,
                    SyncRecordTypeChecklist,
                    checklist.localId,
                    json.encodeToString(RemoteChecklistRecord.serializer(), checklist),
                    checklist.remoteVersion,
                    syncedAtMillis,
                )
            } + snapshot.items.map { item ->
                SyncPristineRecordEntity(
                    ownerUserId,
                    generation,
                    SyncRecordTypeItem,
                    item.localId,
                    json.encodeToString(RemoteTodoItemRecord.serializer(), item),
                    item.remoteVersion,
                    syncedAtMillis,
                )
            } + snapshot.attachments.map { attachment ->
                SyncPristineRecordEntity(
                    ownerUserId,
                    generation,
                    SyncRecordTypeAttachment,
                    attachment.todoLocalId,
                    json.encodeToString(RemoteTodoAttachmentRecord.serializer(), attachment),
                    attachment.remoteVersion,
                    syncedAtMillis,
                )
            }
            dao.replacePristineRecords(ownerUserId, generation, records)
        }
    }

    override suspend fun loadPendingMutations(ownerUserId: String): List<SyncMutationRecord> = mutex.withLock {
        val generation = generationForRead(ownerUserId) ?: return@withLock emptyList()
        dao.getMutations(ownerUserId, generation).map { entity ->
            decode("mutation ${entity.mutationUuid}") {
                json.decodeFromString(SyncMutationRecord.serializer(), entity.payloadJson)
            }.also { mutation ->
                if (mutation.mutationUuid != entity.mutationUuid) {
                    throw SyncMetadataCorruptException("Mutation UUID does not match its database key.")
                }
            }.copy(attempts = entity.attempts, lastError = entity.lastError)
        }
    }

    override suspend fun recordPendingMutation(ownerUserId: String, mutation: SyncMutationRecord) {
        mutex.withLock {
            val generation = requireGeneration(ownerUserId)
            dao.insertMutation(
                SyncMutationEntity(
                    ownerUserId,
                    generation,
                    mutation.mutationUuid,
                    json.encodeToString(SyncMutationRecord.serializer(), mutation),
                    mutation.createdAtMillis,
                    mutation.attempts,
                    mutation.lastError,
                ),
            )
        }
    }

    override suspend fun clearPendingMutation(ownerUserId: String, mutationUuid: String) {
        mutex.withLock {
            val generation = generationForRead(ownerUserId) ?: return@withLock
            dao.deleteMutation(ownerUserId, generation, mutationUuid)
        }
    }

    override suspend fun recordConflict(ownerUserId: String, conflict: LocalSyncConflictRecord) {
        mutex.withLock {
            val generation = requireGeneration(ownerUserId)
            dao.insertConflict(
                SyncConflictRecordEntity(
                    ownerUserId,
                    generation,
                    conflict.recordType,
                    conflict.localId,
                    conflict.localPayloadJson,
                    conflict.remotePayloadJson,
                    json.encodeToString(ListSerializer(String.serializer()), conflict.fields),
                    conflict.message,
                    conflict.remoteVersion,
                    conflict.createdAtMillis,
                ),
            )
        }
    }

    override suspend fun loadConflicts(ownerUserId: String): List<LocalSyncConflictRecord> = mutex.withLock {
        val generation = generationForRead(ownerUserId) ?: return@withLock emptyList()
        dao.getConflicts(ownerUserId, generation).map(::toLocalConflict)
    }

    override suspend fun loadConflict(
        ownerUserId: String,
        recordType: String,
        localId: String,
    ): LocalSyncConflictRecord? = mutex.withLock {
        val generation = generationForRead(ownerUserId) ?: return@withLock null
        dao.getConflict(ownerUserId, generation, recordType, localId)?.let(::toLocalConflict)
    }

    override suspend fun clearConflict(ownerUserId: String, recordType: String, localId: String) {
        mutex.withLock {
            val generation = generationForRead(ownerUserId) ?: return@withLock
            dao.deleteConflict(ownerUserId, generation, recordType, localId)
        }
    }

    private suspend fun generationForRead(ownerUserId: String): String? {
        workingGenerations[ownerUserId]?.let { return it }
        val owner = dao.getOwner(ownerUserId) ?: return null
        return owner.activeGeneration.takeIf {
            owner.activeFormatVersion == CurrentSyncMetadataFormatVersion
        }
    }

    private suspend fun requireGeneration(ownerUserId: String): String =
        generationForRead(ownerUserId)
            ?: error("Sync metadata session has not started for owner $ownerUserId.")

    private fun toLocalConflict(entity: SyncConflictRecordEntity): LocalSyncConflictRecord =
        LocalSyncConflictRecord(
            recordType = entity.recordType,
            localId = entity.localId,
            localPayloadJson = entity.localPayloadJson,
            remotePayloadJson = entity.remotePayloadJson,
            fields = decode("conflict fields ${entity.recordType}:${entity.localId}") {
                json.decodeFromString(ListSerializer(String.serializer()), entity.fieldsJson)
            },
            message = entity.message,
            remoteVersion = entity.remoteVersion,
            createdAtMillis = entity.createdAtMillis,
        )

    private inline fun <T> decode(label: String, block: () -> T): T = try {
        block()
    } catch (error: Exception) {
        throw SyncMetadataCorruptException("Invalid current-format $label.", error)
    }

    companion object {
        fun create(context: Context): RoomSyncMetadataStore =
            RoomSyncMetadataStore(SyncMetadataDatabase.create(context))
    }
}
