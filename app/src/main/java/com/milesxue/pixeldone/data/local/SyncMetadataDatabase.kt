package com.milesxue.pixeldone.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SyncMetadataOwnerEntity::class,
        SyncCursorEntity::class,
        SyncPristineRecordEntity::class,
        SyncConflictRecordEntity::class,
        SyncMutationEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class SyncMetadataDatabase : RoomDatabase() {
    abstract fun syncMetadataDao(): SyncMetadataDao

    companion object {
        fun create(context: Context): SyncMetadataDatabase = Room.databaseBuilder(
            context.applicationContext,
            SyncMetadataDatabase::class.java,
            SyncMetadataDatabaseName,
        )
            // This database contains only rebuildable protocol state. User-owned data lives elsewhere.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .build()
    }
}
