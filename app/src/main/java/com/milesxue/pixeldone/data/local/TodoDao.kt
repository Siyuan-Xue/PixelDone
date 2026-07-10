package com.milesxue.pixeldone.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {
    @Query("SELECT * FROM todo_checklists ORDER BY sortIndex ASC, createdAtMillis ASC")
    suspend fun getChecklists(): List<TodoChecklistEntity>

    @Query("SELECT * FROM todo_items ORDER BY checklistLocalId ASC, sortIndex ASC, createdAtMillis ASC")
    suspend fun getItems(): List<TodoItemEntity>

    @Query("SELECT * FROM sync_tombstones ORDER BY deletedAtMillis ASC")
    suspend fun getTombstones(): List<SyncTombstoneEntity>

    @Query("SELECT * FROM todo_state_metadata WHERE id = :id LIMIT 1")
    suspend fun getMetadata(id: String = TodoStateMetadataId): TodoStateMetadataEntity?

    @Query("SELECT COUNT(*) FROM todo_state_metadata")
    suspend fun metadataCount(): Int

    @Query("SELECT * FROM todo_checklists ORDER BY sortIndex ASC, createdAtMillis ASC")
    fun observeChecklists(): Flow<List<TodoChecklistEntity>>

    @Query("SELECT * FROM todo_items ORDER BY checklistLocalId ASC, sortIndex ASC, createdAtMillis ASC")
    fun observeItems(): Flow<List<TodoItemEntity>>

    @Query("SELECT * FROM todo_state_metadata WHERE id = :id LIMIT 1")
    fun observeMetadata(id: String = TodoStateMetadataId): Flow<TodoStateMetadataEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChecklists(checklists: List<TodoChecklistEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<TodoItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTombstones(tombstones: List<SyncTombstoneEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadata(metadata: TodoStateMetadataEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncCursor(cursor: SyncCursorEntity)

    @Query("SELECT * FROM sync_cursors WHERE ownerUserId = :ownerUserId LIMIT 1")
    suspend fun getSyncCursor(ownerUserId: String): SyncCursorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPristineRecords(records: List<SyncPristineRecordEntity>)

    @Query("SELECT * FROM sync_pristine_records WHERE ownerUserId = :ownerUserId")
    suspend fun getPristineRecords(ownerUserId: String): List<SyncPristineRecordEntity>

    @Query("DELETE FROM sync_pristine_records WHERE ownerUserId = :ownerUserId")
    suspend fun deletePristineRecords(ownerUserId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncMutation(mutation: SyncMutationEntity)

    @Query("SELECT * FROM sync_mutations WHERE ownerUserId = :ownerUserId ORDER BY createdAtMillis ASC")
    suspend fun getSyncMutations(ownerUserId: String): List<SyncMutationEntity>

    @Query("DELETE FROM sync_mutations WHERE ownerUserId = :ownerUserId AND mutationUuid = :mutationUuid")
    suspend fun deleteSyncMutation(ownerUserId: String, mutationUuid: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncConflict(conflict: SyncConflictRecordEntity)

    @Query("SELECT * FROM sync_conflict_records WHERE ownerUserId = :ownerUserId ORDER BY createdAtMillis ASC")
    suspend fun getSyncConflicts(ownerUserId: String): List<SyncConflictRecordEntity>

    @Query("SELECT * FROM sync_conflict_records WHERE ownerUserId = :ownerUserId AND recordType = :recordType AND localId = :localId LIMIT 1")
    suspend fun getSyncConflict(ownerUserId: String, recordType: String, localId: String): SyncConflictRecordEntity?

    @Query("DELETE FROM sync_conflict_records WHERE ownerUserId = :ownerUserId AND recordType = :recordType AND localId = :localId")
    suspend fun deleteSyncConflict(ownerUserId: String, recordType: String, localId: String)

    @Query("DELETE FROM todo_items")
    suspend fun deleteItems()

    @Query("DELETE FROM todo_checklists")
    suspend fun deleteChecklists()

    @Query("DELETE FROM todo_state_metadata")
    suspend fun deleteMetadata()

    @Query("DELETE FROM sync_tombstones")
    suspend fun deleteTombstones()

    @Transaction
    suspend fun replaceState(entitySet: TodoEntitySet) {
        deleteItems()
        deleteChecklists()
        deleteMetadata()
        insertChecklists(entitySet.checklists)
        insertItems(entitySet.items)
        deleteTombstones()
        if (entitySet.tombstones.isNotEmpty()) insertTombstones(entitySet.tombstones)
        insertMetadata(entitySet.metadata)
    }

    @Transaction
    suspend fun replacePristineRecords(ownerUserId: String, records: List<SyncPristineRecordEntity>) {
        deletePristineRecords(ownerUserId)
        if (records.isNotEmpty()) insertPristineRecords(records)
    }

    @Transaction
    suspend fun getEntitySet(): TodoEntitySet? {
        val metadata = getMetadata() ?: return null
        return TodoEntitySet(
            metadata = metadata,
            checklists = getChecklists(),
            items = getItems(),
            tombstones = getTombstones(),
        )
    }
}
