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
    suspend fun insertMetadata(metadata: TodoStateMetadataEntity)

    @Query("DELETE FROM todo_items")
    suspend fun deleteItems()

    @Query("DELETE FROM todo_checklists")
    suspend fun deleteChecklists()

    @Query("DELETE FROM todo_state_metadata")
    suspend fun deleteMetadata()

    @Transaction
    suspend fun replaceState(entitySet: TodoEntitySet) {
        deleteItems()
        deleteChecklists()
        deleteMetadata()
        insertChecklists(entitySet.checklists)
        insertItems(entitySet.items)
        insertMetadata(entitySet.metadata)
    }

    @Transaction
    suspend fun getEntitySet(): TodoEntitySet? {
        val metadata = getMetadata() ?: return null
        return TodoEntitySet(
            metadata = metadata,
            checklists = getChecklists(),
            items = getItems(),
        )
    }
}