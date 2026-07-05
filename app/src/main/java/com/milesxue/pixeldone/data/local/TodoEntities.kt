package com.milesxue.pixeldone.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.milesxue.pixeldone.domain.sync.SyncRecordState

@Entity(tableName = "todo_state_metadata")
data class TodoStateMetadataEntity(
    @PrimaryKey val id: String = TodoStateMetadataId,
    val selectedListLocalId: String,
    val updatedAtMillis: Long,
)

@Entity(tableName = "todo_checklists")
data class TodoChecklistEntity(
    @PrimaryKey val localId: String,
    val sortIndex: Int,
    val remoteId: String? = null,
    val ownerUserId: String? = null,
    val name: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val deletedAtMillis: Long? = null,
    val syncState: String = SyncRecordState.LOCAL_ONLY.name,
    val lastSyncedAtMillis: Long? = null,
    val remoteVersion: Long? = null,
    val lastSyncError: String? = null,
)

@Entity(
    tableName = "todo_items",
    indices = [Index(value = ["checklistLocalId"])],
)
data class TodoItemEntity(
    @PrimaryKey val localId: String,
    val checklistLocalId: String,
    val sortIndex: Int,
    val remoteId: String? = null,
    val ownerUserId: String? = null,
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
    val imageSyncState: String = SyncRecordState.LOCAL_ONLY.name,
    val trashedFromChecklistId: String? = null,
    val trashedFromChecklistName: String? = null,
    val trashedAtMillis: Long? = null,
    val syncState: String = SyncRecordState.LOCAL_ONLY.name,
    val lastSyncedAtMillis: Long? = null,
    val remoteVersion: Long? = null,
    val lastSyncError: String? = null,
)

const val TodoStateMetadataId = "todo_state"
