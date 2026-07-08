package com.milesxue.pixeldone.data.sync

import com.milesxue.pixeldone.domain.sync.AuthSession
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

internal class SupabaseRemoteTodoDataSource(
    private val httpClient: SupabaseRequestClient,
) : RemoteTodoDataSource {
    private val responseJson = Json { encodeDefaults = false; ignoreUnknownKeys = true }
    private val requestJson = Json { encodeDefaults = true }

    override suspend fun pullChanges(session: AuthSession, sinceVersion: Long?): RemoteChangeBatch {
        val userId = requireNotNull(session.userId) { "Signed-in session requires a user id." }
        val token = requireNotNull(session.accessToken) { "Signed-in session requires an access token." }
        val versionFilter = sinceVersion?.takeIf { it > 0L }?.let { "gt.$it" }
        val checklistBody = httpClient.request(
            method = "GET",
            path = "/rest/v1/todo_checklists",
            bearerToken = token,
            query = listOfNotNull(
                "select" to "*",
                "owner_user_id" to "eq.$userId",
                versionFilter?.let { "remote_version" to it },
                "order" to "remote_version.asc,sort_index.asc,created_at_millis.asc",
            ),
        )
        val itemBody = httpClient.request(
            method = "GET",
            path = "/rest/v1/todo_items",
            bearerToken = token,
            query = listOfNotNull(
                "select" to "*",
                "owner_user_id" to "eq.$userId",
                versionFilter?.let { "remote_version" to it },
                "order" to "remote_version.asc,checklist_local_id.asc,sort_index.asc,created_at_millis.asc",
            ),
        )
        val settingsBody = httpClient.request(
            method = "GET",
            path = "/rest/v1/user_settings",
            bearerToken = token,
            query = listOfNotNull(
                "select" to "*",
                "owner_user_id" to "eq.$userId",
                versionFilter?.let { "remote_version" to it },
                "limit" to "1",
            ),
        )
        val checklists = responseJson.decodeFromString(
            ListSerializer(SupabaseChecklistRow.serializer()),
            checklistBody,
        ).map { it.toRemoteRecord() }
        val items = responseJson.decodeFromString(
            ListSerializer(SupabaseTodoItemRow.serializer()),
            itemBody,
        ).map { it.toRemoteRecord() }
        val settings = responseJson.decodeFromString(
            ListSerializer(SupabaseUserSettingsRow.serializer()),
            settingsBody,
        ).firstOrNull()?.toRemoteRecord()
        return RemoteChangeBatch(
            serverVersion = maxServerVersion(sinceVersion, checklists, items, settings),
            checklists = checklists,
            items = items,
            settings = settings,
        )
    }

    override suspend fun pushMutations(
        session: AuthSession,
        batch: RemoteMutationBatch,
    ): RemotePushResult {
        val userId = requireNotNull(session.userId) { "Signed-in session requires a user id." }
        val token = requireNotNull(session.accessToken) { "Signed-in session requires an access token." }
        val checklists = if (batch.snapshot.checklists.isEmpty()) {
            emptyList()
        } else {
            val body = requestJson.encodeToString(
                ListSerializer(SupabaseChecklistUpsertRow.serializer()),
                batch.snapshot.checklists.map { SupabaseChecklistUpsertRow.fromRemoteRecord(it) },
            )
            val response = httpClient.request(
                method = "POST",
                path = "/rest/v1/todo_checklists",
                bearerToken = token,
                query = listOf("on_conflict" to "owner_user_id,local_id"),
                prefer = "resolution=merge-duplicates,return=representation",
                body = body,
            )
            responseJson.decodeFromString(
                ListSerializer(SupabaseChecklistRow.serializer()),
                response,
            ).map { it.toRemoteRecord() }
        }
        val items = if (batch.snapshot.items.isEmpty()) {
            emptyList()
        } else {
            val body = requestJson.encodeToString(
                ListSerializer(SupabaseTodoItemUpsertRow.serializer()),
                batch.snapshot.items.map { SupabaseTodoItemUpsertRow.fromRemoteRecord(it) },
            )
            val response = httpClient.request(
                method = "POST",
                path = "/rest/v1/todo_items",
                bearerToken = token,
                query = listOf("on_conflict" to "owner_user_id,local_id"),
                prefer = "resolution=merge-duplicates,return=representation",
                body = body,
            )
            responseJson.decodeFromString(
                ListSerializer(SupabaseTodoItemRow.serializer()),
                response,
            ).map { it.toRemoteRecord() }
        }
        val settings = batch.settings?.let { settingsRecord ->
            val response = httpClient.request(
                method = "POST",
                path = "/rest/v1/user_settings",
                bearerToken = token,
                query = listOf("on_conflict" to "owner_user_id"),
                prefer = "resolution=merge-duplicates,return=representation",
                body = requestJson.encodeToString(
                    ListSerializer(SupabaseUserSettingsUpsertRow.serializer()),
                    listOf(SupabaseUserSettingsUpsertRow.fromRemoteRecord(settingsRecord)),
                ),
            )
            responseJson.decodeFromString(
                ListSerializer(SupabaseUserSettingsRow.serializer()),
                response,
            ).firstOrNull()?.toRemoteRecord()
        }
        recordMutation(session, userId, batch.mutationUuid)
        return RemotePushResult(
            accepted = RemoteTodoSnapshot(checklists = checklists, items = items),
            settings = settings,
            serverVersion = maxServerVersion(null, checklists, items, settings),
        )
    }

    private suspend fun recordMutation(session: AuthSession, ownerUserId: String, mutationUuid: String) {
        val token = requireNotNull(session.accessToken) { "Signed-in session requires an access token." }
        val body = requestJson.encodeToString(
            ListSerializer(SupabaseMutationLogUpsertRow.serializer()),
            listOf(SupabaseMutationLogUpsertRow(ownerUserId = ownerUserId, mutationUuid = mutationUuid)),
        )
        httpClient.request(
            method = "POST",
            path = "/rest/v1/sync_mutation_log",
            bearerToken = token,
            query = listOf("on_conflict" to "owner_user_id,mutation_uuid"),
            prefer = "resolution=ignore-duplicates,return=minimal",
            body = body,
        )
    }

    private fun maxServerVersion(
        fallback: Long?,
        checklists: List<RemoteChecklistRecord>,
        items: List<RemoteTodoItemRecord>,
        settings: RemoteUserSettingsRecord?,
    ): Long = listOfNotNull(
        fallback,
        checklists.maxOfOrNull { it.remoteVersion ?: it.updatedAtMillis },
        items.maxOfOrNull { it.remoteVersion ?: it.updatedAtMillis },
        settings?.remoteVersion ?: settings?.updatedAtMillis,
    ).maxOrNull() ?: 0L
}

@Serializable
private data class SupabaseChecklistRow(
    @SerialName("id") val remoteId: String? = null,
    @SerialName("owner_user_id") val ownerUserId: String,
    @SerialName("local_id") val localId: String,
    @SerialName("sort_index") val sortIndex: Int,
    val name: String,
    @SerialName("created_at_millis") val createdAtMillis: Long,
    @SerialName("updated_at_millis") val updatedAtMillis: Long,
    @SerialName("deleted_at_millis") val deletedAtMillis: Long? = null,
    @SerialName("remote_version") val remoteVersion: Long? = null,
) {
    fun toRemoteRecord(): RemoteChecklistRecord = RemoteChecklistRecord(
        localId = localId,
        remoteId = remoteId,
        ownerUserId = ownerUserId,
        sortIndex = sortIndex,
        name = name,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
        deletedAtMillis = deletedAtMillis,
        remoteVersion = remoteVersion ?: updatedAtMillis,
    )
}

@Serializable
private data class SupabaseChecklistUpsertRow(
    @SerialName("owner_user_id") val ownerUserId: String,
    @SerialName("local_id") val localId: String,
    @SerialName("sort_index") val sortIndex: Int,
    val name: String,
    @SerialName("created_at_millis") val createdAtMillis: Long,
    @SerialName("updated_at_millis") val updatedAtMillis: Long,
    @SerialName("deleted_at_millis") val deletedAtMillis: Long?,
) {
    companion object {
        fun fromRemoteRecord(record: RemoteChecklistRecord): SupabaseChecklistUpsertRow = SupabaseChecklistUpsertRow(
            ownerUserId = record.ownerUserId,
            localId = record.localId,
            sortIndex = record.sortIndex,
            name = record.name,
            createdAtMillis = record.createdAtMillis,
            updatedAtMillis = record.updatedAtMillis,
            deletedAtMillis = record.deletedAtMillis,
        )
    }
}

@Serializable
private data class SupabaseTodoItemRow(
    @SerialName("id") val remoteId: String? = null,
    @SerialName("owner_user_id") val ownerUserId: String,
    @SerialName("local_id") val localId: String,
    @SerialName("checklist_local_id") val checklistLocalId: String,
    @SerialName("sort_index") val sortIndex: Int,
    val title: String,
    val priority: String,
    @SerialName("due_at_millis") val dueAtMillis: Long,
    val completed: Boolean,
    @SerialName("created_at_millis") val createdAtMillis: Long,
    @SerialName("updated_at_millis") val updatedAtMillis: Long,
    @SerialName("deleted_at_millis") val deletedAtMillis: Long? = null,
    @SerialName("reminder_repeat") val reminderRepeat: String,
    @SerialName("image_local_name") val imageLocalName: String? = null,
    @SerialName("image_remote_path") val imageRemotePath: String? = null,
    @SerialName("image_sync_state") val imageSyncState: String,
    @SerialName("trashed_from_checklist_id") val trashedFromChecklistId: String? = null,
    @SerialName("trashed_from_checklist_name") val trashedFromChecklistName: String? = null,
    @SerialName("trashed_at_millis") val trashedAtMillis: Long? = null,
    @SerialName("remote_version") val remoteVersion: Long? = null,
) {
    fun toRemoteRecord(): RemoteTodoItemRecord = RemoteTodoItemRecord(
        localId = localId,
        remoteId = remoteId,
        ownerUserId = ownerUserId,
        checklistLocalId = checklistLocalId,
        sortIndex = sortIndex,
        title = title,
        priority = priority,
        dueAtMillis = dueAtMillis,
        completed = completed,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
        deletedAtMillis = deletedAtMillis,
        reminderRepeat = reminderRepeat,
        imageLocalName = imageLocalName,
        imageRemotePath = imageRemotePath,
        imageSyncState = imageSyncState,
        trashedFromChecklistId = trashedFromChecklistId,
        trashedFromChecklistName = trashedFromChecklistName,
        trashedAtMillis = trashedAtMillis,
        remoteVersion = remoteVersion ?: updatedAtMillis,
    )
}

@Serializable
private data class SupabaseTodoItemUpsertRow(
    @SerialName("owner_user_id") val ownerUserId: String,
    @SerialName("local_id") val localId: String,
    @SerialName("checklist_local_id") val checklistLocalId: String,
    @SerialName("sort_index") val sortIndex: Int,
    val title: String,
    val priority: String,
    @SerialName("due_at_millis") val dueAtMillis: Long,
    val completed: Boolean,
    @SerialName("created_at_millis") val createdAtMillis: Long,
    @SerialName("updated_at_millis") val updatedAtMillis: Long,
    @SerialName("deleted_at_millis") val deletedAtMillis: Long?,
    @SerialName("reminder_repeat") val reminderRepeat: String,
    @SerialName("image_local_name") val imageLocalName: String?,
    @SerialName("image_remote_path") val imageRemotePath: String?,
    @SerialName("image_sync_state") val imageSyncState: String,
    @SerialName("trashed_from_checklist_id") val trashedFromChecklistId: String?,
    @SerialName("trashed_from_checklist_name") val trashedFromChecklistName: String?,
    @SerialName("trashed_at_millis") val trashedAtMillis: Long?,
) {
    companion object {
        fun fromRemoteRecord(record: RemoteTodoItemRecord): SupabaseTodoItemUpsertRow = SupabaseTodoItemUpsertRow(
            ownerUserId = record.ownerUserId,
            localId = record.localId,
            checklistLocalId = record.checklistLocalId,
            sortIndex = record.sortIndex,
            title = record.title,
            priority = record.priority,
            dueAtMillis = record.dueAtMillis,
            completed = record.completed,
            createdAtMillis = record.createdAtMillis,
            updatedAtMillis = record.updatedAtMillis,
            deletedAtMillis = record.deletedAtMillis,
            reminderRepeat = record.reminderRepeat,
            imageLocalName = record.imageLocalName,
            imageRemotePath = record.imageRemotePath,
            imageSyncState = record.imageSyncState,
            trashedFromChecklistId = record.trashedFromChecklistId,
            trashedFromChecklistName = record.trashedFromChecklistName,
            trashedAtMillis = record.trashedAtMillis,
        )
    }
}

@Serializable
private data class SupabaseUserSettingsRow(
    @SerialName("owner_user_id") val ownerUserId: String,
    @SerialName("dark_theme") val darkTheme: Boolean,
    @SerialName("dock_plus_placement") val dockPlusPlacement: String,
    @SerialName("dock_actions") val dockActions: List<String>,
    @SerialName("updated_at_millis") val updatedAtMillis: Long,
    @SerialName("remote_version") val remoteVersion: Long? = null,
) {
    fun toRemoteRecord(): RemoteUserSettingsRecord = RemoteUserSettingsRecord(
        ownerUserId = ownerUserId,
        darkTheme = darkTheme,
        dockPlusPlacement = dockPlusPlacement,
        dockActions = dockActions,
        updatedAtMillis = updatedAtMillis,
        remoteVersion = remoteVersion ?: updatedAtMillis,
    )
}

@Serializable
private data class SupabaseUserSettingsUpsertRow(
    @SerialName("owner_user_id") val ownerUserId: String,
    @SerialName("dark_theme") val darkTheme: Boolean,
    @SerialName("dock_plus_placement") val dockPlusPlacement: String,
    @SerialName("dock_actions") val dockActions: List<String>,
    @SerialName("updated_at_millis") val updatedAtMillis: Long,
) {
    companion object {
        fun fromRemoteRecord(record: RemoteUserSettingsRecord): SupabaseUserSettingsUpsertRow =
            SupabaseUserSettingsUpsertRow(
                ownerUserId = record.ownerUserId,
                darkTheme = record.darkTheme,
                dockPlusPlacement = record.dockPlusPlacement,
                dockActions = record.dockActions,
                updatedAtMillis = record.updatedAtMillis,
            )
    }
}

@Serializable
private data class SupabaseMutationLogUpsertRow(
    @SerialName("owner_user_id") val ownerUserId: String,
    @SerialName("mutation_uuid") val mutationUuid: String,
)
