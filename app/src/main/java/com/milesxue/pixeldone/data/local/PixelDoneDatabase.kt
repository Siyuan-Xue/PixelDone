package com.milesxue.pixeldone.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TodoStateMetadataEntity::class,
        TodoChecklistEntity::class,
        TodoItemEntity::class,
        SyncCursorEntity::class,
        SyncPristineRecordEntity::class,
        SyncMutationEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class PixelDoneDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao
}

internal object PixelDoneMigrations {
    val Migration1To2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE todo_items ADD COLUMN locallyPurgedAtMillis INTEGER")
        }
    }

    val Migration2To3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS sync_cursors (
                    ownerUserId TEXT NOT NULL PRIMARY KEY,
                    remoteVersion INTEGER NOT NULL,
                    updatedAtMillis INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS sync_pristine_records (
                    ownerUserId TEXT NOT NULL,
                    recordType TEXT NOT NULL,
                    localId TEXT NOT NULL,
                    payloadJson TEXT NOT NULL,
                    remoteVersion INTEGER,
                    updatedAtMillis INTEGER NOT NULL,
                    PRIMARY KEY(ownerUserId, recordType, localId)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS sync_mutations (
                    ownerUserId TEXT NOT NULL,
                    mutationUuid TEXT NOT NULL,
                    payloadJson TEXT NOT NULL,
                    createdAtMillis INTEGER NOT NULL,
                    attempts INTEGER NOT NULL,
                    lastError TEXT,
                    PRIMARY KEY(ownerUserId, mutationUuid)
                )
                """.trimIndent(),
            )
        }
    }
}
