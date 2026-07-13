package com.milesxue.pixeldone

import com.milesxue.pixeldone.data.sync.CurrentSyncMetadataFormatVersion
import com.milesxue.pixeldone.data.sync.ExpectedRemoteSchemaVersion
import com.milesxue.pixeldone.data.sync.RemoteChecklistRecord
import com.milesxue.pixeldone.data.sync.RemoteTodoItemRecord
import com.milesxue.pixeldone.data.sync.RemoteTodoSnapshot
import com.milesxue.pixeldone.data.sync.SyncMutationRecord
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SyncWireFormatTest {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
    }

    @Test
    fun remoteItemWireKeysStaySnakeCase() {
        val encoded = json.encodeToJsonElement(
            RemoteTodoItemRecord.serializer(),
            RemoteTodoItemRecord(
                localId = "todo-1",
                ownerUserId = "user-1",
                checklistLocalId = "main",
                sortIndex = 0,
                title = "One",
                priority = "HIGH",
                dueAtMillis = 1_000L,
                completed = false,
                createdAtMillis = 100L,
                updatedAtMillis = 200L,
                reminderRepeat = "NONE",
            ),
        ).jsonObject

        assertEquals(
            setOf(
                "local_id",
                "id",
                "owner_user_id",
                "checklist_local_id",
                "sort_index",
                "title",
                "priority",
                "due_at_millis",
                "completed",
                "created_at_millis",
                "updated_at_millis",
                "reminder_repeat",
                "trashed_from_checklist_id",
                "trashed_from_checklist_name",
                "trashed_at_millis",
                "remote_version",
            ),
            encoded.keys,
        )
    }

    @Test
    fun localMutationFormatAndVersionAreExplicit() {
        val mutation = SyncMutationRecord(
            mutationUuid = "mutation-1",
            snapshot = RemoteTodoSnapshot(),
            createdAtMillis = 100L,
        )
        val encoded = json.encodeToJsonElement(SyncMutationRecord.serializer(), mutation).jsonObject

        assertEquals(
            setOf(
                "mutationUuid",
                "snapshot",
                "settings",
                "tombstones",
                "createdAtMillis",
                "cleanedImagePaths",
                "attempts",
                "lastError",
            ),
            encoded.keys,
        )
        assertEquals("3.2", ExpectedRemoteSchemaVersion)
        assertEquals(1, CurrentSyncMetadataFormatVersion)
    }

    @Test
    fun legacyCamelCaseRemotePayloadIsNotAcceptedAtRuntime() {
        val legacy = """
            {
              "localId":"main",
              "remoteId":"remote-main",
              "ownerUserId":"user-1",
              "sortIndex":0,
              "name":"MAIN",
              "createdAtMillis":100,
              "updatedAtMillis":200,
              "remoteVersion":2
            }
        """.trimIndent()

        assertThrows(Exception::class.java) {
            json.decodeFromString(RemoteChecklistRecord.serializer(), legacy)
        }
    }
}
