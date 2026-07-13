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
        SyncTombstoneEntity::class,
    ],
    version = 7,
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

    val Migration3To4: Migration = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS sync_conflict_records (
                    ownerUserId TEXT NOT NULL,
                    recordType TEXT NOT NULL,
                    localId TEXT NOT NULL,
                    localPayloadJson TEXT NOT NULL,
                    remotePayloadJson TEXT NOT NULL,
                    fieldsJson TEXT NOT NULL,
                    message TEXT NOT NULL,
                    remoteVersion INTEGER,
                    createdAtMillis INTEGER NOT NULL,
                    PRIMARY KEY(ownerUserId, recordType, localId)
                )
                """.trimIndent(),
            )
        }
    }

    val Migration4To5: Migration = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS sync_tombstones (
                    ownerUserId TEXT NOT NULL,
                    recordType TEXT NOT NULL,
                    localId TEXT NOT NULL,
                    deletedAtMillis INTEGER NOT NULL,
                    remoteVersion INTEGER,
                    syncState TEXT NOT NULL,
                    lastSyncedAtMillis INTEGER,
                    lastSyncError TEXT,
                    PRIMARY KEY(ownerUserId, recordType, localId)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT OR REPLACE INTO sync_tombstones(
                    ownerUserId, recordType, localId, deletedAtMillis, remoteVersion,
                    syncState, lastSyncedAtMillis, lastSyncError
                )
                SELECT ownerUserId, 'checklist', localId,
                       COALESCE(deletedAtMillis, updatedAtMillis), remoteVersion,
                       'NOT_SYNCED', lastSyncedAtMillis, NULL
                FROM todo_checklists
                WHERE ownerUserId IS NOT NULL AND deletedAtMillis IS NOT NULL
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT OR REPLACE INTO sync_tombstones(
                    ownerUserId, recordType, localId, deletedAtMillis, remoteVersion,
                    syncState, lastSyncedAtMillis, lastSyncError
                )
                SELECT ownerUserId, 'item', localId,
                       COALESCE(locallyPurgedAtMillis, updatedAtMillis), remoteVersion,
                       'NOT_SYNCED', lastSyncedAtMillis, NULL
                FROM todo_items
                WHERE ownerUserId IS NOT NULL AND locallyPurgedAtMillis IS NOT NULL
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE todo_checklists_v5 (
                    localId TEXT NOT NULL PRIMARY KEY,
                    sortIndex INTEGER NOT NULL,
                    remoteId TEXT,
                    ownerUserId TEXT,
                    name TEXT NOT NULL,
                    createdAtMillis INTEGER NOT NULL,
                    updatedAtMillis INTEGER NOT NULL,
                    syncState TEXT NOT NULL,
                    lastSyncedAtMillis INTEGER,
                    remoteVersion INTEGER,
                    lastSyncError TEXT
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO todo_checklists_v5
                SELECT localId, sortIndex, remoteId, ownerUserId, name, createdAtMillis,
                       updatedAtMillis, syncState, lastSyncedAtMillis, remoteVersion, lastSyncError
                FROM todo_checklists
                WHERE deletedAtMillis IS NULL
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE todo_checklists")
            db.execSQL("ALTER TABLE todo_checklists_v5 RENAME TO todo_checklists")

            db.execSQL(
                """
                CREATE TABLE todo_items_v5 (
                    localId TEXT NOT NULL PRIMARY KEY,
                    checklistLocalId TEXT NOT NULL,
                    sortIndex INTEGER NOT NULL,
                    remoteId TEXT,
                    ownerUserId TEXT,
                    title TEXT NOT NULL,
                    priority TEXT NOT NULL,
                    dueAtMillis INTEGER NOT NULL,
                    completed INTEGER NOT NULL,
                    createdAtMillis INTEGER NOT NULL,
                    updatedAtMillis INTEGER NOT NULL,
                    reminderRepeat TEXT NOT NULL,
                    imageLocalName TEXT,
                    imageRemotePath TEXT,
                    imageSyncState TEXT NOT NULL,
                    trashedFromChecklistId TEXT,
                    trashedFromChecklistName TEXT,
                    trashedAtMillis INTEGER,
                    syncState TEXT NOT NULL,
                    lastSyncedAtMillis INTEGER,
                    remoteVersion INTEGER,
                    lastSyncError TEXT
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO todo_items_v5
                SELECT localId, checklistLocalId, sortIndex, remoteId, ownerUserId, title,
                       priority, dueAtMillis, completed, createdAtMillis, updatedAtMillis,
                       reminderRepeat, imageLocalName, imageRemotePath, imageSyncState,
                       trashedFromChecklistId, trashedFromChecklistName,
                       COALESCE(trashedAtMillis, deletedAtMillis), syncState,
                       lastSyncedAtMillis, remoteVersion, lastSyncError
                FROM todo_items
                WHERE locallyPurgedAtMillis IS NULL
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE todo_items")
            db.execSQL("ALTER TABLE todo_items_v5 RENAME TO todo_items")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_todo_items_checklistLocalId ON todo_items(checklistLocalId)")
        }
    }

    val Migration5To6: Migration = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE todo_items ADD COLUMN imageAttachmentId TEXT")
            db.execSQL("ALTER TABLE todo_items ADD COLUMN imageContentSha256 TEXT")
            db.execSQL("ALTER TABLE todo_items ADD COLUMN imageContentType TEXT")
            db.execSQL("ALTER TABLE todo_items ADD COLUMN imageByteSize INTEGER")
            db.execSQL("ALTER TABLE todo_items ADD COLUMN imageUpdatedAtMillis INTEGER")
            db.execSQL("ALTER TABLE todo_items ADD COLUMN imageRemoteVersion INTEGER")
            db.execSQL("ALTER TABLE todo_items ADD COLUMN imageLastSyncError TEXT")
            db.execSQL(
                """
                UPDATE todo_items
                SET imageSyncState = 'PENDING_UPLOAD', imageUpdatedAtMillis = updatedAtMillis
                WHERE imageLocalName IS NOT NULL
                """.trimIndent(),
            )
        }
    }

    val Migration6To7: Migration = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // These tables contain rebuildable sync protocol state, not user-owned todo data.
            // PixelDone 3.2.1 deliberately cuts over instead of decoding legacy JSON payloads.
            db.execSQL("DROP TABLE IF EXISTS sync_cursors")
            db.execSQL("DROP TABLE IF EXISTS sync_pristine_records")
            db.execSQL("DROP TABLE IF EXISTS sync_mutations")
            db.execSQL("DROP TABLE IF EXISTS sync_conflict_records")
        }
    }
}
