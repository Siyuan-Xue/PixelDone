package com.milesxue.pixeldone

import com.milesxue.pixeldone.data.sync.*
import com.milesxue.pixeldone.domain.sync.AuthSession
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseRemoteTodoDataSourceTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val session = AuthSession(
        signedIn = true,
        userId = "user-1",
        accessToken = "access-token",
        cloudAvailable = true,
    )

    @Test
    fun pushMutationUsesSingleTransactionalRpc() = runTest {
        val client = RecordingSupabaseRequestClient()
        val dataSource = SupabaseRemoteTodoDataSource(client)
        val snapshot = RemoteTodoSnapshot(
            checklists = listOf(RemoteChecklistRecord(
                localId = "list-1", ownerUserId = "user-1", sortIndex = 0, name = "MAIN",
                createdAtMillis = 100L, updatedAtMillis = 200L,
            )),
            items = listOf(RemoteTodoItemRecord(
                localId = "item-1", ownerUserId = "user-1", checklistLocalId = "list-1",
                sortIndex = 0, title = "First", priority = "HIGH", dueAtMillis = 1_000L,
                completed = false, createdAtMillis = 100L, updatedAtMillis = 250L,
                reminderRepeat = "NONE", imageSyncState = "LOCAL_ONLY",
            )),
        )
        val result = dataSource.pushMutations(
            session,
            RemoteMutationBatch(
                mutationUuid = "mutation-1",
                snapshot = snapshot,
                settings = RemoteUserSettingsRecord(languageMode = "fr", updatedAtMillis = 300L),
                tombstones = listOf(RemoteTombstoneRecord(
                    recordType = "item", localId = "item-old", deletedAtMillis = 275L,
                )),
            ),
        )

        assertEquals("3.1", result.schemaVersion)
        assertEquals("remote-list-1", result.accepted.checklists.single().remoteId)
        val request = client.requests.single()
        assertEquals("POST", request.method)
        assertEquals("/rest/v1/rpc/pixeldone_apply_mutation", request.path)
        val body = json.parseToJsonElement(requireNotNull(request.body)).jsonObject
        assertEquals("mutation-1", body.getValue("p_mutation_uuid").jsonPrimitive.content)
        assertEquals(1, body.getValue("p_checklists").jsonArray.size)
        assertEquals(1, body.getValue("p_items").jsonArray.size)
        assertEquals("fr", body.getValue("p_settings").jsonObject.getValue("language_mode").jsonPrimitive.content)
        assertEquals("item-old", body.getValue("p_tombstones").jsonArray.single().jsonObject.getValue("local_id").jsonPrimitive.content)
        assertFalse(client.requests.any { it.path.startsWith("/rest/v1/todo_") })
    }

    @Test
    fun pullUsesSingleSnapshotRpcWithGlobalCursor() = runTest {
        val client = RecordingSupabaseRequestClient()
        val result = SupabaseRemoteTodoDataSource(client).pullChanges(session, 42L)

        assertEquals(84L, result.serverVersion)
        val request = client.requests.single()
        assertEquals("/rest/v1/rpc/pixeldone_pull_changes", request.path)
        val body = json.parseToJsonElement(requireNotNull(request.body)).jsonObject
        assertEquals(42L, body.getValue("p_since_version").jsonPrimitive.content.toLong())
        assertTrue(request.query.isEmpty())
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
            requests += RecordedSupabaseRequest(method, path, bearerToken, query, prefer, body)
            return when (path) {
                "/rest/v1/rpc/pixeldone_pull_changes" -> PullResponse
                "/rest/v1/rpc/pixeldone_apply_mutation" -> PushResponse
                else -> error("Unexpected path: $path")
            }
        }

        private companion object {
            const val PullResponse = """
                {"schema_version":"3.1","server_version":84,"checklists":[],"items":[],"settings":null,"tombstones":[]}
            """
            const val PushResponse = """
                {"schema_version":"3.1","server_version":85,
                 "accepted":{"checklists":[{"id":"remote-list-1","owner_user_id":"user-1","local_id":"list-1","sort_index":0,"name":"MAIN","created_at_millis":100,"updated_at_millis":200,"remote_version":85}],"items":[]},
                 "settings":null,"tombstones":[],"conflicts":[]}
            """
        }
    }
}
