package com.milesxue.pixeldone.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.milesxue.pixeldone.data.sync.LocalSyncConflictRecord
import com.milesxue.pixeldone.data.sync.RemoteChecklistRecord
import com.milesxue.pixeldone.data.sync.RemoteTodoSnapshot
import com.milesxue.pixeldone.data.sync.SyncMutationRecord
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncStorageInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun cleanDatabases() {
        context.deleteDatabase(DomainMigrationDatabaseName)
        context.deleteDatabase(SyncMetadataDatabaseName)
    }

    @After
    fun cleanupDatabases() {
        context.deleteDatabase(DomainMigrationDatabaseName)
        context.deleteDatabase(SyncMetadataDatabaseName)
    }

    @Test
    fun freshVersion7CreatesOnlyDomainTables() = runBlocking {
        val database = Room.databaseBuilder(
            context,
            PixelDoneDatabase::class.java,
            DomainMigrationDatabaseName,
        )
            .allowMainThreadQueries()
            .build()

        try {
            val tableNames = readTableNames(database)
            assertTrue("todo_state_metadata" in tableNames)
            assertTrue("todo_checklists" in tableNames)
            assertTrue("todo_items" in tableNames)
            assertTrue("sync_tombstones" in tableNames)
            assertFalse("sync_cursors" in tableNames)
            assertFalse("sync_pristine_records" in tableNames)
            assertFalse("sync_mutations" in tableNames)
            assertFalse("sync_conflict_records" in tableNames)
        } finally {
            database.close()
        }
    }

    @Test
    fun migration5To6To7PreservesAttachmentUploadIntentAndDomainData() = runBlocking {
        createVersion5DomainDatabase()
        val database = Room.databaseBuilder(
            context,
            PixelDoneDatabase::class.java,
            DomainMigrationDatabaseName,
        )
            .addMigrations(
                PixelDoneMigrations.Migration5To6,
                PixelDoneMigrations.Migration6To7,
            )
            .allowMainThreadQueries()
            .build()

        try {
            val tableNames = readTableNames(database)
            assertFalse("sync_cursors" in tableNames)
            assertFalse("sync_pristine_records" in tableNames)
            assertFalse("sync_mutations" in tableNames)
            assertFalse("sync_conflict_records" in tableNames)

            val state = database.todoDao().getEntitySet()
            val item = state?.items?.single()
            assertEquals("Keep v5", item?.title)
            assertEquals("photo.jpg", item?.imageLocalName)
            assertEquals("PENDING_UPLOAD", item?.imageSyncState)
            assertEquals(1000L, item?.imageUpdatedAtMillis)
            assertEquals("deleted-v5", state?.tombstones?.single()?.localId)
        } finally {
            database.close()
        }
    }

    @Test
    fun migration6To7DropsOnlyDerivedSyncTables() = runBlocking {
        createVersion6DomainDatabase()
        val database = Room.databaseBuilder(
            context,
            PixelDoneDatabase::class.java,
            DomainMigrationDatabaseName,
        )
            .addMigrations(PixelDoneMigrations.Migration6To7)
            .allowMainThreadQueries()
            .build()

        try {
            val tableNames = readTableNames(database)
            assertFalse("sync_cursors" in tableNames)
            assertFalse("sync_pristine_records" in tableNames)
            assertFalse("sync_mutations" in tableNames)
            assertFalse("sync_conflict_records" in tableNames)
            assertTrue("todo_items" in tableNames)
            assertTrue("sync_tombstones" in tableNames)

            val state = database.todoDao().getEntitySet()
            assertEquals("Keep me", state?.items?.single()?.title)
            assertEquals("deleted-todo", state?.tombstones?.single()?.localId)
        } finally {
            database.close()
        }
    }

    private fun readTableNames(database: PixelDoneDatabase): Set<String> = buildSet {
        database.openHelper.writableDatabase.query(
            "SELECT name FROM sqlite_master WHERE type = 'table'",
        ).use { cursor ->
            while (cursor.moveToNext()) add(cursor.getString(0))
        }
    }

    private fun createVersion5DomainDatabase() {
        val file = context.getDatabasePath(DomainMigrationDatabaseName)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE todo_state_metadata (id TEXT NOT NULL PRIMARY KEY, selectedListLocalId TEXT NOT NULL, updatedAtMillis INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE todo_checklists (localId TEXT NOT NULL PRIMARY KEY, sortIndex INTEGER NOT NULL, remoteId TEXT, ownerUserId TEXT, name TEXT NOT NULL, createdAtMillis INTEGER NOT NULL, updatedAtMillis INTEGER NOT NULL, syncState TEXT NOT NULL, lastSyncedAtMillis INTEGER, remoteVersion INTEGER, lastSyncError TEXT)")
            db.execSQL("CREATE TABLE todo_items (localId TEXT NOT NULL PRIMARY KEY, checklistLocalId TEXT NOT NULL, sortIndex INTEGER NOT NULL, remoteId TEXT, ownerUserId TEXT, title TEXT NOT NULL, priority TEXT NOT NULL, dueAtMillis INTEGER NOT NULL, completed INTEGER NOT NULL, createdAtMillis INTEGER NOT NULL, updatedAtMillis INTEGER NOT NULL, reminderRepeat TEXT NOT NULL, imageLocalName TEXT, imageRemotePath TEXT, imageSyncState TEXT NOT NULL, trashedFromChecklistId TEXT, trashedFromChecklistName TEXT, trashedAtMillis INTEGER, syncState TEXT NOT NULL, lastSyncedAtMillis INTEGER, remoteVersion INTEGER, lastSyncError TEXT)")
            db.execSQL("CREATE INDEX index_todo_items_checklistLocalId ON todo_items(checklistLocalId)")
            db.execSQL("CREATE TABLE sync_tombstones (ownerUserId TEXT NOT NULL, recordType TEXT NOT NULL, localId TEXT NOT NULL, deletedAtMillis INTEGER NOT NULL, remoteVersion INTEGER, syncState TEXT NOT NULL, lastSyncedAtMillis INTEGER, lastSyncError TEXT, PRIMARY KEY(ownerUserId, recordType, localId))")
            createLegacySyncTables(db)
            db.execSQL("INSERT INTO todo_state_metadata VALUES ('todo_state', 'main', 1000)")
            db.execSQL("INSERT INTO todo_checklists VALUES ('main', 0, 'remote-main', 'user-1', 'MAIN', 100, 1000, 'SYNCED', 1000, 1, NULL)")
            db.execSQL("INSERT INTO todo_items VALUES ('todo-v5', 'main', 0, 'remote-todo-v5', 'user-1', 'Keep v5', 'HIGH', 2000, 0, 100, 1000, 'NONE', 'photo.jpg', NULL, 'LOCAL_ONLY', NULL, NULL, NULL, 'SYNCED', 1000, 1, NULL)")
            db.execSQL("INSERT INTO sync_tombstones VALUES ('user-1', 'item', 'deleted-v5', 900, 2, 'NOT_SYNCED', NULL, NULL)")
            db.version = 5
        }
    }

    @Test
    fun generationActivatesAtomicallyAndInvalidationStartsClean() = runBlocking {
        val database = SyncMetadataDatabase.create(context)
        val store = RoomSyncMetadataStore(database)
        val owner = "user-1"
        val json = Json { encodeDefaults = true; explicitNulls = true }
        val checklist = RemoteChecklistRecord(
            localId = "main",
            ownerUserId = owner,
            sortIndex = 0,
            name = "MAIN",
            createdAtMillis = 100L,
            updatedAtMillis = 200L,
            remoteVersion = 3L,
        )
        val conflict = LocalSyncConflictRecord(
            recordType = SyncRecordTypeChecklist,
            localId = "main",
            localPayloadJson = json.encodeToString(checklist.copy(name = "Local")),
            remotePayloadJson = json.encodeToString(checklist.copy(name = "Cloud")),
            fields = listOf("name"),
            message = "Conflict: name",
            remoteVersion = 3L,
            createdAtMillis = 300L,
        )

        try {
            val first = store.beginSyncMetadata(owner, 1L)
            assertTrue(first.rebuilding)
            assertNull(store.loadSyncCursor(owner))
            store.saveSyncCursor(owner, 3L, 2L)
            store.savePristineSnapshot(owner, RemoteTodoSnapshot(checklists = listOf(checklist)), 2L)
            store.recordPendingMutation(
                owner,
                SyncMutationRecord(UUID.randomUUID().toString(), createdAtMillis = 2L),
            )
            store.recordConflict(owner, conflict)
            store.completeSyncMetadata(owner, first, 3L)

            val active = store.beginSyncMetadata(owner, 4L)
            assertFalse(active.rebuilding)
            assertEquals(3L, store.loadSyncCursor(owner))
            assertEquals("MAIN", store.loadPristineSnapshot(owner).checklists.single().name)
            assertEquals(1, store.loadPendingMutations(owner).size)
            assertEquals(1, store.loadConflicts(owner).size)
            store.completeSyncMetadata(owner, active, 5L)

            val interrupted = store.beginSyncMetadata(owner, 5L)
            assertFalse(interrupted.rebuilding)
            store.saveSyncCursor(owner, 99L, 5L)
            store.abortSyncMetadata(owner, interrupted)
            assertEquals(3L, store.loadSyncCursor(owner))

            store.invalidateSyncMetadata(owner)
            val rebuilt = store.beginSyncMetadata(owner, 6L)
            assertTrue(rebuilt.rebuilding)
            assertNull(store.loadSyncCursor(owner))
            assertEquals(emptyList<LocalSyncConflictRecord>(), store.loadConflicts(owner))
            store.abortSyncMetadata(owner, rebuilt)
        } finally {
            database.close()
        }
    }

    private fun createVersion6DomainDatabase() {
        val file = context.getDatabasePath(DomainMigrationDatabaseName)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE todo_state_metadata (id TEXT NOT NULL PRIMARY KEY, selectedListLocalId TEXT NOT NULL, updatedAtMillis INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE todo_checklists (localId TEXT NOT NULL PRIMARY KEY, sortIndex INTEGER NOT NULL, remoteId TEXT, ownerUserId TEXT, name TEXT NOT NULL, createdAtMillis INTEGER NOT NULL, updatedAtMillis INTEGER NOT NULL, syncState TEXT NOT NULL, lastSyncedAtMillis INTEGER, remoteVersion INTEGER, lastSyncError TEXT)")
            db.execSQL("CREATE TABLE todo_items (localId TEXT NOT NULL PRIMARY KEY, checklistLocalId TEXT NOT NULL, sortIndex INTEGER NOT NULL, remoteId TEXT, ownerUserId TEXT, title TEXT NOT NULL, priority TEXT NOT NULL, dueAtMillis INTEGER NOT NULL, completed INTEGER NOT NULL, createdAtMillis INTEGER NOT NULL, updatedAtMillis INTEGER NOT NULL, reminderRepeat TEXT NOT NULL, imageLocalName TEXT, imageRemotePath TEXT, imageSyncState TEXT NOT NULL, trashedFromChecklistId TEXT, trashedFromChecklistName TEXT, trashedAtMillis INTEGER, syncState TEXT NOT NULL, lastSyncedAtMillis INTEGER, remoteVersion INTEGER, lastSyncError TEXT, imageAttachmentId TEXT, imageContentSha256 TEXT, imageContentType TEXT, imageByteSize INTEGER, imageUpdatedAtMillis INTEGER, imageRemoteVersion INTEGER, imageLastSyncError TEXT)")
            db.execSQL("CREATE INDEX index_todo_items_checklistLocalId ON todo_items(checklistLocalId)")
            db.execSQL("CREATE TABLE sync_tombstones (ownerUserId TEXT NOT NULL, recordType TEXT NOT NULL, localId TEXT NOT NULL, deletedAtMillis INTEGER NOT NULL, remoteVersion INTEGER, syncState TEXT NOT NULL, lastSyncedAtMillis INTEGER, lastSyncError TEXT, PRIMARY KEY(ownerUserId, recordType, localId))")
            createLegacySyncTables(db)
            db.execSQL("INSERT INTO todo_state_metadata VALUES ('todo_state', 'main', 1000)")
            db.execSQL("INSERT INTO todo_checklists VALUES ('main', 0, 'remote-main', 'user-1', 'MAIN', 100, 1000, 'SYNCED', 1000, 1, NULL)")
            db.execSQL("INSERT INTO todo_items VALUES ('todo-1', 'main', 0, 'remote-todo-1', 'user-1', 'Keep me', 'HIGH', 2000, 0, 100, 1000, 'NONE', NULL, NULL, 'LOCAL_ONLY', NULL, NULL, NULL, 'SYNCED', 1000, 1, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)")
            db.execSQL("INSERT INTO sync_tombstones VALUES ('user-1', 'item', 'deleted-todo', 900, 2, 'NOT_SYNCED', NULL, NULL)")
            db.execSQL("INSERT INTO sync_conflict_records VALUES ('user-1', 'item', 'legacy', '{}', '{}', '[]', 'legacy', 1, 100)")
            db.version = 6
        }
    }

    private fun createLegacySyncTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE sync_cursors (ownerUserId TEXT NOT NULL PRIMARY KEY, remoteVersion INTEGER NOT NULL, updatedAtMillis INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE sync_pristine_records (ownerUserId TEXT NOT NULL, recordType TEXT NOT NULL, localId TEXT NOT NULL, payloadJson TEXT NOT NULL, remoteVersion INTEGER, updatedAtMillis INTEGER NOT NULL, PRIMARY KEY(ownerUserId, recordType, localId))")
        db.execSQL("CREATE TABLE sync_mutations (ownerUserId TEXT NOT NULL, mutationUuid TEXT NOT NULL, payloadJson TEXT NOT NULL, createdAtMillis INTEGER NOT NULL, attempts INTEGER NOT NULL, lastError TEXT, PRIMARY KEY(ownerUserId, mutationUuid))")
        db.execSQL("CREATE TABLE sync_conflict_records (ownerUserId TEXT NOT NULL, recordType TEXT NOT NULL, localId TEXT NOT NULL, localPayloadJson TEXT NOT NULL, remotePayloadJson TEXT NOT NULL, fieldsJson TEXT NOT NULL, message TEXT NOT NULL, remoteVersion INTEGER, createdAtMillis INTEGER NOT NULL, PRIMARY KEY(ownerUserId, recordType, localId))")
    }

    private companion object {
        const val DomainMigrationDatabaseName = "pixel_done_migration_test.db"
    }
}
