package com.milesxue.pixeldone.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface SyncMetadataDao {
    @Query("SELECT * FROM sync_metadata_owners WHERE ownerUserId = :ownerUserId LIMIT 1")
    suspend fun getOwner(ownerUserId: String): SyncMetadataOwnerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOwner(owner: SyncMetadataOwnerEntity)

    @Query("DELETE FROM sync_metadata_owners WHERE ownerUserId = :ownerUserId")
    suspend fun deleteOwner(ownerUserId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCursor(cursor: SyncCursorEntity)

    @Query("SELECT * FROM sync_cursors WHERE ownerUserId = :ownerUserId AND generation = :generation LIMIT 1")
    suspend fun getCursor(ownerUserId: String, generation: String): SyncCursorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPristineRecords(records: List<SyncPristineRecordEntity>)

    @Query("SELECT * FROM sync_pristine_records WHERE ownerUserId = :ownerUserId AND generation = :generation")
    suspend fun getPristineRecords(ownerUserId: String, generation: String): List<SyncPristineRecordEntity>

    @Query("DELETE FROM sync_pristine_records WHERE ownerUserId = :ownerUserId AND generation = :generation")
    suspend fun deletePristineRecords(ownerUserId: String, generation: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMutation(mutation: SyncMutationEntity)

    @Query("SELECT * FROM sync_mutations WHERE ownerUserId = :ownerUserId AND generation = :generation ORDER BY createdAtMillis ASC")
    suspend fun getMutations(ownerUserId: String, generation: String): List<SyncMutationEntity>

    @Query("DELETE FROM sync_mutations WHERE ownerUserId = :ownerUserId AND generation = :generation AND mutationUuid = :mutationUuid")
    suspend fun deleteMutation(ownerUserId: String, generation: String, mutationUuid: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConflict(conflict: SyncConflictRecordEntity)

    @Query("SELECT * FROM sync_conflict_records WHERE ownerUserId = :ownerUserId AND generation = :generation ORDER BY createdAtMillis ASC")
    suspend fun getConflicts(ownerUserId: String, generation: String): List<SyncConflictRecordEntity>

    @Query("SELECT * FROM sync_conflict_records WHERE ownerUserId = :ownerUserId AND generation = :generation AND recordType = :recordType AND localId = :localId LIMIT 1")
    suspend fun getConflict(
        ownerUserId: String,
        generation: String,
        recordType: String,
        localId: String,
    ): SyncConflictRecordEntity?

    @Query("DELETE FROM sync_conflict_records WHERE ownerUserId = :ownerUserId AND generation = :generation AND recordType = :recordType AND localId = :localId")
    suspend fun deleteConflict(
        ownerUserId: String,
        generation: String,
        recordType: String,
        localId: String,
    )

    @Query("DELETE FROM sync_cursors WHERE ownerUserId = :ownerUserId AND generation = :generation")
    suspend fun deleteCursors(ownerUserId: String, generation: String)

    @Query("DELETE FROM sync_mutations WHERE ownerUserId = :ownerUserId AND generation = :generation")
    suspend fun deleteMutations(ownerUserId: String, generation: String)

    @Query("DELETE FROM sync_conflict_records WHERE ownerUserId = :ownerUserId AND generation = :generation")
    suspend fun deleteConflicts(ownerUserId: String, generation: String)

    @Transaction
    suspend fun replacePristineRecords(
        ownerUserId: String,
        generation: String,
        records: List<SyncPristineRecordEntity>,
    ) {
        deletePristineRecords(ownerUserId, generation)
        if (records.isNotEmpty()) insertPristineRecords(records)
    }

    @Transaction
    suspend fun deleteGeneration(ownerUserId: String, generation: String) {
        deleteCursors(ownerUserId, generation)
        deletePristineRecords(ownerUserId, generation)
        deleteMutations(ownerUserId, generation)
        deleteConflicts(ownerUserId, generation)
    }

    @Transaction
    suspend fun startStagingGeneration(
        ownerUserId: String,
        generation: String,
        formatVersion: Int,
        nowMillis: Long,
        sourceGeneration: String? = null,
    ) {
        val current = getOwner(ownerUserId)
        current?.stagingGeneration?.let { deleteGeneration(ownerUserId, it) }
        upsertOwner(
            (current ?: SyncMetadataOwnerEntity(ownerUserId = ownerUserId, updatedAtMillis = nowMillis)).copy(
                stagingGeneration = generation,
                stagingFormatVersion = formatVersion,
                updatedAtMillis = nowMillis,
            ),
        )
        if (sourceGeneration != null) {
            getCursor(ownerUserId, sourceGeneration)?.let {
                insertCursor(it.copy(generation = generation))
            }
            val pristine = getPristineRecords(ownerUserId, sourceGeneration)
                .map { it.copy(generation = generation) }
            if (pristine.isNotEmpty()) insertPristineRecords(pristine)
            getMutations(ownerUserId, sourceGeneration).forEach {
                insertMutation(it.copy(generation = generation))
            }
            getConflicts(ownerUserId, sourceGeneration).forEach {
                insertConflict(it.copy(generation = generation))
            }
        }
    }

    @Transaction
    suspend fun activateStagingGeneration(
        ownerUserId: String,
        generation: String,
        formatVersion: Int,
        nowMillis: Long,
    ) {
        val current = requireNotNull(getOwner(ownerUserId))
        check(current.stagingGeneration == generation && current.stagingFormatVersion == formatVersion)
        upsertOwner(
            current.copy(
                activeGeneration = generation,
                activeFormatVersion = formatVersion,
                stagingGeneration = null,
                stagingFormatVersion = null,
                updatedAtMillis = nowMillis,
            ),
        )
        current.activeGeneration?.takeIf { it != generation }?.let { deleteGeneration(ownerUserId, it) }
    }

    @Transaction
    suspend fun discardStagingGeneration(ownerUserId: String, generation: String) {
        val current = getOwner(ownerUserId) ?: return
        if (current.stagingGeneration != generation) return
        deleteGeneration(ownerUserId, generation)
        upsertOwner(
            current.copy(
                stagingGeneration = null,
                stagingFormatVersion = null,
            ),
        )
    }

    @Transaction
    suspend fun invalidateOwner(ownerUserId: String) {
        val current = getOwner(ownerUserId)
        current?.activeGeneration?.let { deleteGeneration(ownerUserId, it) }
        current?.stagingGeneration?.takeIf { it != current.activeGeneration }?.let {
            deleteGeneration(ownerUserId, it)
        }
        deleteOwner(ownerUserId)
    }
}
