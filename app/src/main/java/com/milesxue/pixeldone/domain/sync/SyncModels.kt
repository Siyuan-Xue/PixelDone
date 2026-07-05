package com.milesxue.pixeldone.domain.sync

/**
 * Sync record state stored locally and mapped to the Supabase tables.
 */
enum class SyncRecordState {
    LOCAL_ONLY,
    NOT_SYNCED,
    SYNCED,
    CONFLICT,
    ERROR,
}

enum class SyncCoordinatorStatus {
    LOCAL_ONLY,
    NOT_CONFIGURED,
    SIGNED_OUT,
    IDLE,
    SYNCING,
    SYNCED,
    ERROR,
}

data class AuthSession(
    val signedIn: Boolean = false,
    val userId: String? = null,
    val userEmail: String? = null,
    val displayLabel: String = "LOCAL ONLY",
    val cloudAvailable: Boolean = false,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val expiresAtMillis: Long? = null,
    val configurationError: String? = null,
    val insecureHttpAllowed: Boolean = false,
)

data class SyncRecordMetadata(
    val localId: String,
    val remoteId: String? = null,
    val ownerUserId: String? = null,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val deletedAtMillis: Long? = null,
    val syncState: SyncRecordState = SyncRecordState.LOCAL_ONLY,
    val lastSyncedAtMillis: Long? = null,
    val remoteVersion: Long? = null,
    val lastSyncError: String? = null,
)

data class SyncMergeCandidate<T>(
    val value: T,
    val updatedAtMillis: Long,
    val deletedAtMillis: Long? = null,
    val remoteVersion: Long? = null,
)

enum class ConflictResolutionSource {
    LOCAL,
    REMOTE,
}

data class ConflictResolution<T>(
    val source: ConflictResolutionSource,
    val value: T,
    val deletedAtMillis: Long?,
)

object ConflictResolver {
    fun <T> resolveLastWriteWins(
        local: SyncMergeCandidate<T>,
        remote: SyncMergeCandidate<T>,
    ): ConflictResolution<T> {
        val localClock = local.deletedAtMillis?.coerceAtLeast(local.updatedAtMillis) ?: local.updatedAtMillis
        val remoteClock = remote.deletedAtMillis?.coerceAtLeast(remote.updatedAtMillis) ?: remote.updatedAtMillis
        return if (remoteClock > localClock) {
            ConflictResolution(
                source = ConflictResolutionSource.REMOTE,
                value = remote.value,
                deletedAtMillis = remote.deletedAtMillis,
            )
        } else {
            ConflictResolution(
                source = ConflictResolutionSource.LOCAL,
                value = local.value,
                deletedAtMillis = local.deletedAtMillis,
            )
        }
    }
}