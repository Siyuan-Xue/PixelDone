package com.milesxue.pixeldone

import com.milesxue.pixeldone.data.sync.RemoteChecklistRecord
import com.milesxue.pixeldone.data.sync.RemoteMutationBatch
import com.milesxue.pixeldone.data.sync.RemoteTodoItemRecord
import com.milesxue.pixeldone.data.sync.RemoteTodoSnapshot
import com.milesxue.pixeldone.data.sync.SupabaseRemoteTodoDataSource
import com.milesxue.pixeldone.data.sync.SupabaseRequestClient
import com.milesxue.pixeldone.domain.sync.AuthSession
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseRemoteTodoDataSourceTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun pushMutationsUsesStableUpsertKeysAndMutationLog() = runTest {
        val client = RecordingSupabaseRequestClient()
        val dataSource = SupabaseRemoteTodoDataSource(client)
        val snapshot = RemoteTodoSnapshot(
            checklists = listOf(
                RemoteChecklistRecord(
                    localId = "list-1",
                    ownerUserId = "user-1",
                    sortIndex = 0,
                    name = "MAIN",
                    createdAtMillis = 100L,
                    updatedAtMillis = 200L,
                    deletedAtMillis = null,
                    remoteVersion = null,
                ),
            ),
            items = listOf(
                RemoteTodoItemRecord(
                    localId = "item-1",
                    ownerUserId = "user-1",
                    checklistLocalId = "list-1",
                    sortIndex = 0,
                    title = "First",
                    priority = "HIGH",
                    dueAtMillis = 1_000L,
                    completed = false,
                    createdAtMillis = 100L,
                    updatedAtMillis = 250L,
                    deletedAtMillis = null,
                    reminderRepeat = "NONE",
                    imageLocalName = null,
                    imageRemotePath = null,
                    imageSyncState = "LOCAL_ONLY",
                    trashedFromChecklistId = null,
                    trashedFromChecklistName = null,
                    trashedAtMillis = null,
                    remoteVersion = null,
                ),
                RemoteTodoItemRecord(
                    localId = "item-2",
                    ownerUserId = "user-1",
                    checklistLocalId = "list-1",
                    sortIndex = 1,
                    title = "Second",
                    priority = "LOW",
                    dueAtMillis = 2_000L,
                    completed = true,
                    createdAtMillis = 120L,
                    updatedAtMillis = 300L,
                    deletedAtMillis = 350L,
                    reminderRepeat = "DAILY",
                    imageLocalName = "camera.jpg",
                    imageRemotePath = "images/camera.jpg",
                    imageSyncState = "NOT_SYNCED",
                    trashedFromChecklistId = "list-1",
                    trashedFromChecklistName = "MAIN",
                    trashedAtMillis = 350L,
                    remoteVersion = 12L,
                ),
            ),
        )

        val pushed = dataSource.pushMutations(
            session = AuthSession(
                signedIn = true,
                userId = "user-1",
                accessToken = "access-token",
                cloudAvailable = true,
            ),
            batch = RemoteMutationBatch(mutationUuid = "mutation-1", snapshot = snapshot),
        )

        assertEquals("remote-list-1", pushed.accepted.checklists.single().remoteId)
        assertEquals(listOf("remote-item-1", "remote-item-2"), pushed.accepted.items.map { it.remoteId })

        val checklistRequest = client.requests.single { it.path == "/rest/v1/todo_checklists" }
        val checklistObjects = json.parseToJsonElement(checklistRequest.body!!).jsonArray.map { it.jsonObject }
        assertEquals(1, checklistObjects.size)
        assertFalse(checklistObjects.single().containsKey("id"))
        assertEquals(ExpectedChecklistUpsertKeys, checklistObjects.single().keys)
        assertTrue(checklistObjects.single().getValue("deleted_at_millis") is JsonNull)

        val itemRequest = client.requests.single { it.path == "/rest/v1/todo_items" }
        val itemObjects = json.parseToJsonElement(itemRequest.body!!).jsonArray.map { it.jsonObject }
        assertEquals(2, itemObjects.size)
        assertEquals(ExpectedTodoItemUpsertKeys, itemObjects[0].keys)
        assertEquals(itemObjects[0].keys, itemObjects[1].keys)
        assertFalse(itemObjects[0].containsKey("id"))
        assertTrue(itemObjects[0].getValue("image_local_name") is JsonNull)
        assertTrue(itemObjects[0].getValue("trashed_from_checklist_id") is JsonNull)
        assertEquals("camera.jpg", itemObjects[1].getValue("image_local_name").jsonPrimitive.content)
        assertEquals("MAIN", itemObjects[1].getValue("trashed_from_checklist_name").jsonPrimitive.content)
        assertFalse(client.requests.any { it.path == "/rest/v1/user_settings" })

        val mutationRequest = client.requests.single { it.path == "/rest/v1/sync_mutation_log" }
        val mutationObject = json.parseToJsonElement(mutationRequest.body!!).jsonArray.single().jsonObject
        assertEquals("user-1", mutationObject.getValue("owner_user_id").jsonPrimitive.content)
        assertEquals("mutation-1", mutationObject.getValue("mutation_uuid").jsonPrimitive.content)
    }

    @Test
    fun pullChangesUsesRemoteVersionCursor() = runTest {
        val client = RecordingSupabaseRequestClient()
        val dataSource = SupabaseRemoteTodoDataSource(client)

        dataSource.pullChanges(
            session = AuthSession(
                signedIn = true,
                userId = "user-1",
                accessToken = "access-token",
                cloudAvailable = true,
            ),
            sinceVersion = 42L,
        )

        val checklistRequest = client.requests.single { it.path == "/rest/v1/todo_checklists" }
        assertTrue("remote_version" to "gt.42" in checklistRequest.query)
        val itemRequest = client.requests.single { it.path == "/rest/v1/todo_items" }
        assertTrue("remote_version" to "gt.42" in itemRequest.query)
        assertFalse(client.requests.any { it.path == "/rest/v1/user_settings" })
    }

    private companion object {
        val ExpectedChecklistUpsertKeys = setOf(
            "owner_user_id",
            "local_id",
            "sort_index",
            "name",
            "created_at_millis",
            "updated_at_millis",
            "deleted_at_millis",
        )
        val ExpectedTodoItemUpsertKeys = setOf(
            "owner_user_id",
            "local_id",
            "checklist_local_id",
            "sort_index",
            "title",
            "priority",
            "due_at_millis",
            "completed",
            "created_at_millis",
            "updated_at_millis",
            "deleted_at_millis",
            "reminder_repeat",
            "image_local_name",
            "image_remote_path",
            "image_sync_state",
            "trashed_from_checklist_id",
            "trashed_from_checklist_name",
            "trashed_at_millis",
        )
    }
}

private data class RecordedSupabaseRequest(
    val method: String,
    val path: String,
    val bearerToken: String?,
    val query: List<Pair<String, String>>,
    val prefer: String?,
    val body: String?,
)

private class RecordingSupabaseRequestClient : SupabaseRequestClient {
    val requests = mutableListOf<RecordedSupabaseRequest>()

    override suspend fun request(
        method: String,
        path: String,
        bearerToken: String?,
        query: List<Pair<String, String>>,
        prefer: String?,
        body: String?,
    ): String {
        requests += RecordedSupabaseRequest(
            method = method,
            path = path,
            bearerToken = bearerToken,
            query = query,
            prefer = prefer,
            body = body,
        )
        return when (path) {
            "/rest/v1/todo_checklists" -> ChecklistResponse
            "/rest/v1/todo_items" -> TodoItemResponse
            "/rest/v1/sync_mutation_log" -> ""
            else -> "[]"
        }
    }

    private companion object {
        const val ChecklistResponse = """
            [{
              "id":"remote-list-1",
              "owner_user_id":"user-1",
              "local_id":"list-1",
              "sort_index":0,
              "name":"MAIN",
              "created_at_millis":100,
              "updated_at_millis":200,
              "deleted_at_millis":null,
              "remote_version":200
            }]
        """
        const val TodoItemResponse = """
            [
              {
                "id":"remote-item-1",
                "owner_user_id":"user-1",
                "local_id":"item-1",
                "checklist_local_id":"list-1",
                "sort_index":0,
                "title":"First",
                "priority":"HIGH",
                "due_at_millis":1000,
                "completed":false,
                "created_at_millis":100,
                "updated_at_millis":250,
                "deleted_at_millis":null,
                "reminder_repeat":"NONE",
                "image_local_name":null,
                "image_remote_path":null,
                "image_sync_state":"LOCAL_ONLY",
                "trashed_from_checklist_id":null,
                "trashed_from_checklist_name":null,
                "trashed_at_millis":null,
                "remote_version":250
              },
              {
                "id":"remote-item-2",
                "owner_user_id":"user-1",
                "local_id":"item-2",
                "checklist_local_id":"list-1",
                "sort_index":1,
                "title":"Second",
                "priority":"LOW",
                "due_at_millis":2000,
                "completed":true,
                "created_at_millis":120,
                "updated_at_millis":300,
                "deleted_at_millis":350,
                "reminder_repeat":"DAILY",
                "image_local_name":"camera.jpg",
                "image_remote_path":"images/camera.jpg",
                "image_sync_state":"NOT_SYNCED",
                "trashed_from_checklist_id":"list-1",
                "trashed_from_checklist_name":"MAIN",
                "trashed_at_millis":350,
                "remote_version":300
              }
            ]
        """
    }
}
