package com.milesxue.pixeldone.data.local

import androidx.room.Entity

@Entity(tableName = "sync_metadata_owners", primaryKeys = ["ownerUserId"])
data class SyncMetadataOwnerEntity(
    val ownerUserId: String,
    val activeGeneration: String? = null,
    val activeFormatVersion: Int? = null,
    val stagingGeneration: String? = null,
    val stagingFormatVersion: Int? = null,
    val updatedAtMillis: Long,
)

@Entity(tableName = "sync_cursors", primaryKeys = ["ownerUserId", "generation"])
data class SyncCursorEntity(
    val ownerUserId: String,
    val generation: String,
    val remoteVersion: Long,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "sync_pristine_records",
    primaryKeys = ["ownerUserId", "generation", "recordType", "localId"],
)
data class SyncPristineRecordEntity(
    val ownerUserId: String,
    val generation: String,
    val recordType: String,
    val localId: String,
    val payloadJson: String,
    val remoteVersion: Long?,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "sync_conflict_records",
    primaryKeys = ["ownerUserId", "generation", "recordType", "localId"],
)
data class SyncConflictRecordEntity(
    val ownerUserId: String,
    val generation: String,
    val recordType: String,
    val localId: String,
    val localPayloadJson: String,
    val remotePayloadJson: String,
    val fieldsJson: String,
    val message: String,
    val remoteVersion: Long?,
    val createdAtMillis: Long,
)

@Entity(
    tableName = "sync_mutations",
    primaryKeys = ["ownerUserId", "generation", "mutationUuid"],
)
data class SyncMutationEntity(
    val ownerUserId: String,
    val generation: String,
    val mutationUuid: String,
    val payloadJson: String,
    val createdAtMillis: Long,
    val attempts: Int,
    val lastError: String? = null,
)

internal const val SyncMetadataDatabaseName = "pixel_done_sync.db"
