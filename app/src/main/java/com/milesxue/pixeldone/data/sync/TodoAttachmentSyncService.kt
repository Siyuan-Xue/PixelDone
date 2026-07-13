package com.milesxue.pixeldone.data.sync

import com.milesxue.pixeldone.data.image.TodoImageStore
import com.milesxue.pixeldone.data.local.TodoImageSyncState
import com.milesxue.pixeldone.domain.sync.AuthSession
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal interface TodoAttachmentRemoteStore {
    suspend fun upload(session: AuthSession, objectPath: String, contentType: String, bytes: ByteArray)
    suspend fun download(session: AuthSession, objectPath: String): ByteArray
    suspend fun delete(session: AuthSession, objectPath: String): Boolean
}

internal class SupabaseTodoAttachmentRemoteStore(
    private val config: SupabaseConfig,
) : TodoAttachmentRemoteStore {
    override suspend fun upload(
        session: AuthSession,
        objectPath: String,
        contentType: String,
        bytes: ByteArray,
    ) = withConnection(
        session = session,
        method = "POST",
        path = "/storage/v1/object/$BucketName/$objectPath",
        contentType = contentType,
        requestBytes = bytes,
        upsert = true,
    ) { _, _ -> Unit }

    override suspend fun download(session: AuthSession, objectPath: String): ByteArray = withConnection(
        session = session,
        method = "GET",
        path = "/storage/v1/object/authenticated/$BucketName/$objectPath",
    ) { connection, _ -> connection.inputStream.use { it.readBytes() } }

    override suspend fun delete(session: AuthSession, objectPath: String): Boolean = try {
        require(objectPath.matches(SafeObjectPath)) { "Invalid Storage object path." }
        withConnection(
            session = session,
            method = "DELETE",
            path = "/storage/v1/object/$BucketName",
            contentType = "application/json",
            requestBytes = """{"prefixes":["$objectPath"]}""".toByteArray(),
        ) { _, _ -> true }
    } catch (error: SyncRemoteException) {
        if (error.statusCode == 404) true else throw error
    }

    private suspend fun <T> withConnection(
        session: AuthSession,
        method: String,
        path: String,
        contentType: String? = null,
        requestBytes: ByteArray? = null,
        upsert: Boolean = false,
        read: (HttpURLConnection, Int) -> T,
    ): T = withContext(Dispatchers.IO) {
        config.configurationError()?.let { throw SyncConfigurationException(it) }
        val token = session.accessToken ?: throw SyncRemoteException("Signed-in session requires an access token.")
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(config.normalizedBaseUrl + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 10_000
                readTimeout = 30_000
                setRequestProperty("apikey", config.publishableKey)
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/json, application/octet-stream")
                if (upsert) setRequestProperty("x-upsert", "true")
                if (requestBytes != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", contentType ?: "application/octet-stream")
                    setFixedLengthStreamingMode(requestBytes.size)
                }
            }
            requestBytes?.let { bytes -> connection.outputStream.use { it.write(bytes) } }
            val code = connection.responseCode
            if (code !in 200..299) {
                val message = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw SyncRemoteException(
                    message.ifBlank { "Supabase Storage request failed with HTTP $code." },
                    code,
                )
            }
            read(connection, code)
        } catch (error: CancellationException) {
            throw error
        } catch (error: SyncRemoteException) {
            throw error
        } catch (error: Exception) {
            throw SyncRemoteException(error.message ?: "Supabase Storage request failed.")
        } finally {
            connection?.disconnect()
        }
    }

    private companion object {
        const val BucketName = "pixeldone-todo-images"
        val SafeObjectPath = Regex("^[A-Za-z0-9._/-]+$")
    }
}

/**
 * Transfers original image bytes independently from the todo transaction RPC.
 * A failed image transfer is recorded on that attachment and never prevents text,
 * completion, list, settings, or tombstone synchronization.
 */
internal class TodoAttachmentSyncService(
    private val authRepository: AuthSessionRepository,
    private val localStore: TodoSyncLocalStore,
    private val imageStore: TodoImageStore,
    private val remoteStore: TodoAttachmentRemoteStore,
) {
    fun cacheFileName(record: RemoteTodoAttachmentRecord): String? {
        val attachmentId = record.attachmentId ?: return null
        val hash = record.contentSha256 ?: return null
        val contentType = record.contentType ?: return null
        return runCatching {
            imageStore.remoteCacheFileName(record.todoLocalId, attachmentId, hash, contentType)
        }.getOrNull()
    }

    fun localFileMatches(fileName: String?, expectedSha256: String?): Boolean {
        if (expectedSha256 == null) return false
        return imageStore.inspectImage(fileName)?.contentSha256 == expectedSha256
    }

    fun deleteLocalImage(fileName: String?) {
        imageStore.deleteImage(fileName)
    }

    suspend fun preparePendingUploads(owner: String, session: AuthSession) {
        val snapshot = localStore.loadEntitySetForSync(System.currentTimeMillis())
        snapshot.items.filter { item ->
            item.ownerUserId == owner &&
                item.imageLocalName != null &&
                item.imageSyncState in setOf(
                    TodoImageSyncState.LocalOnly,
                    TodoImageSyncState.PendingUpload,
                    TodoImageSyncState.Error,
                )
        }.forEach { item ->
            val metadata = imageStore.inspectImage(item.imageLocalName)
            val bytes = metadata?.let { imageStore.readImageBytes(it.fileName) }
            if (metadata == null || bytes == null) {
                updateItem(item.localId) { current ->
                    current.copy(
                        imageSyncState = TodoImageSyncState.Error,
                        imageLastSyncError = "Image must be JPEG, PNG, or WebP and no larger than 10 MiB.",
                    )
                }
                return@forEach
            }
            val attachmentId = item.imageAttachmentId ?: UUID.randomUUID().toString()
            val extension = when (metadata.contentType) {
                "image/jpeg" -> "jpg"
                "image/png" -> "png"
                else -> "webp"
            }
            val objectPath = "$owner/${item.localId}/$attachmentId-${metadata.contentSha256}.$extension"
            try {
                remoteStore.upload(session, objectPath, metadata.contentType, bytes)
                updateItem(item.localId) { current ->
                    if (current.imageLocalName != item.imageLocalName) current else current.copy(
                        imageAttachmentId = attachmentId,
                        imageRemotePath = objectPath,
                        imageContentSha256 = metadata.contentSha256,
                        imageContentType = metadata.contentType,
                        imageByteSize = metadata.byteSize,
                        imageUpdatedAtMillis = current.imageUpdatedAtMillis ?: System.currentTimeMillis(),
                        imageSyncState = TodoImageSyncState.MetadataPending,
                        imageLastSyncError = null,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                updateItem(item.localId) { current ->
                    current.copy(
                        imageSyncState = TodoImageSyncState.Error,
                        imageLastSyncError = (error.message ?: "Image upload failed.").take(MaxErrorLength),
                    )
                }
            }
        }
    }

    suspend fun deleteCleanupPaths(session: AuthSession, objectPaths: List<String>): List<String> {
        return objectPaths.distinct().filter { path ->
            runCatching { remoteStore.delete(session, path) }.getOrDefault(false)
        }
    }

    suspend fun ensureCached(todoLocalId: String): String? {
        val now = System.currentTimeMillis()
        val current = localStore.loadEntitySetForSync(now).items.firstOrNull { it.localId == todoLocalId }
            ?: return null
        val expectedHash = current.imageContentSha256 ?: return current.imageLocalName
        imageStore.inspectImage(current.imageLocalName)?.takeIf { it.contentSha256 == expectedHash }?.let {
            return it.fileName
        }
        val objectPath = current.imageRemotePath ?: return null
        val attachmentId = current.imageAttachmentId ?: return null
        val contentType = current.imageContentType ?: return null
        val byteSize = current.imageByteSize ?: return null
        val session = authRepository.refreshSessionIfNeeded(now)
        if (!session.signedIn) return null
        val bytes = try {
            remoteStore.download(session, objectPath)
        } catch (error: SyncRemoteException) {
            if (error.statusCode != 401) return null
            val refreshed = authRepository.refreshSessionIfNeeded(System.currentTimeMillis(), force = true)
            runCatching { remoteStore.download(refreshed, objectPath) }.getOrNull() ?: return null
        }
        val fileName = imageStore.cacheRemoteImage(
            todoLocalId = todoLocalId,
            attachmentId = attachmentId,
            expectedSha256 = expectedHash,
            contentType = contentType,
            expectedByteSize = byteSize,
            bytes = bytes,
        ) ?: return null
        updateItem(todoLocalId) { latest ->
            if (latest.imageRemotePath != objectPath || latest.imageContentSha256 != expectedHash) latest else latest.copy(
                imageLocalName = fileName,
                imageSyncState = TodoImageSyncState.Synced,
                imageLastSyncError = null,
            )
        }
        return fileName
    }

    private suspend fun updateItem(todoLocalId: String, transform: (com.milesxue.pixeldone.data.local.TodoItemEntity) -> com.milesxue.pixeldone.data.local.TodoItemEntity) {
        localStore.updateEntitySetFromSync(System.currentTimeMillis()) { state ->
            state.copy(items = state.items.map { if (it.localId == todoLocalId) transform(it) else it })
        }
    }

    private companion object {
        const val MaxErrorLength = 280
    }
}
