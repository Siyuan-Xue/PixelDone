package com.milesxue.pixeldone.data.sync

import com.milesxue.pixeldone.domain.sync.AuthSession
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * PixelDone 3.2 uses only the two transaction RPCs. Direct table writes are
 * intentionally unsupported so one cursor always represents one committed snapshot.
 */
internal class SupabaseRemoteTodoDataSource(
    private val httpClient: SupabaseRequestClient,
) : RemoteTodoDataSource {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = true
    }

    override suspend fun pullChanges(session: AuthSession, sinceVersion: Long?): RemoteChangeBatch {
        val token = requireNotNull(session.accessToken) { "Signed-in session requires an access token." }
        val body = json.encodeToString(
            RpcPullRequest.serializer(),
            RpcPullRequest(
                sinceVersion = sinceVersion ?: 0L,
                clientSchemaVersion = ExpectedRemoteSchemaVersion,
            ),
        )
        val response = rpcRequest(
            path = "/rest/v1/rpc/pixeldone_pull_changes",
            token = token,
            body = body,
        )
        return json.decodeFromString(RemoteChangeBatch.serializer(), response).also(::requireExpectedSchema)
    }

    override suspend fun pushMutations(
        session: AuthSession,
        batch: RemoteMutationBatch,
    ): RemotePushResult {
        val token = requireNotNull(session.accessToken) { "Signed-in session requires an access token." }
        val body = json.encodeToString(
            RpcMutationRequest.serializer(),
            RpcMutationRequest(
                mutationUuid = batch.mutationUuid,
                checklists = batch.snapshot.checklists,
                items = batch.snapshot.items,
                attachments = batch.snapshot.attachments,
                settings = batch.settings,
                tombstones = batch.tombstones,
                cleanedImagePaths = batch.cleanedImagePaths,
                clientSchemaVersion = ExpectedRemoteSchemaVersion,
            ),
        )
        val response = rpcRequest(
            path = "/rest/v1/rpc/pixeldone_apply_mutation",
            token = token,
            body = body,
        )
        return json.decodeFromString(RemotePushResult.serializer(), response).also(::requireExpectedSchema)
    }

    private suspend fun rpcRequest(path: String, token: String, body: String): String = try {
        httpClient.request(
            method = "POST",
            path = path,
            bearerToken = token,
            body = body,
        )
    } catch (error: SyncRemoteException) {
        if (error.statusCode == 404 || error.statusCode == 400) {
            throw SyncSchemaMismatchException(
                "PixelDone Cloud schema $ExpectedRemoteSchemaVersion is required. Run the 3.2 migration SQL.",
            )
        }
        throw error
    }

    private fun requireExpectedSchema(batch: RemoteChangeBatch) {
        if (batch.schemaVersion != ExpectedRemoteSchemaVersion) {
            throw SyncSchemaMismatchException(
                "PixelDone Cloud schema $ExpectedRemoteSchemaVersion is required; server reported ${batch.schemaVersion}.",
                requiredActionFor(batch.schemaVersion),
            )
        }
    }

    private fun requireExpectedSchema(result: RemotePushResult) {
        if (result.schemaVersion != ExpectedRemoteSchemaVersion) {
            throw SyncSchemaMismatchException(
                "PixelDone Cloud schema $ExpectedRemoteSchemaVersion is required; server reported ${result.schemaVersion}.",
                requiredActionFor(result.schemaVersion),
            )
        }
    }

    private fun requiredActionFor(serverVersion: String): SyncContractRequiredAction {
        val server = serverVersion.toContractParts()
        val client = ExpectedRemoteSchemaVersion.toContractParts()
        if (server == null || client == null) return SyncContractRequiredAction.UPDATE_APP
        val comparison = server.zip(client).firstOrNull { (left, right) -> left != right }
            ?.let { (left, right) -> left.compareTo(right) }
            ?: server.size.compareTo(client.size)
        return if (comparison > 0) {
            SyncContractRequiredAction.UPDATE_APP
        } else {
            SyncContractRequiredAction.UPDATE_SERVER
        }
    }

    private fun String.toContractParts(): List<Int>? = split('.').map { it.toIntOrNull() ?: return null }
}

@Serializable
private data class RpcPullRequest(
    @SerialName("p_since_version") val sinceVersion: Long,
    @SerialName("p_client_schema_version") val clientSchemaVersion: String,
)

@Serializable
private data class RpcMutationRequest(
    @SerialName("p_mutation_uuid") val mutationUuid: String,
    @SerialName("p_client_schema_version") val clientSchemaVersion: String,
    @SerialName("p_checklists") val checklists: List<RemoteChecklistRecord>,
    @SerialName("p_items") val items: List<RemoteTodoItemRecord>,
    @SerialName("p_attachments") val attachments: List<RemoteTodoAttachmentRecord>,
    @SerialName("p_settings") val settings: RemoteUserSettingsRecord?,
    @SerialName("p_tombstones") val tombstones: List<RemoteTombstoneRecord>,
    @SerialName("p_cleaned_image_paths") val cleanedImagePaths: List<String>,
)
