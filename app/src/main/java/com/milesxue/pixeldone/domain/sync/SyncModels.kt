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
    CONFLICT,
    SERVER_UPDATE_REQUIRED,
    ERROR,
}

data class SyncRunState(
    val status: SyncCoordinatorStatus = SyncCoordinatorStatus.LOCAL_ONLY,
    val lastSyncedAtMillis: Long? = null,
    val pendingCount: Int = 0,
    val conflictCount: Int = 0,
    val lastError: String? = null,
)

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

enum class ConflictResolutionChoice {
    KEEP_LOCAL,
    KEEP_CLOUD,
}

data class SyncConflictValue(
    val label: String,
    val value: String,
)

data class SyncConflictEntry(
    val recordType: String,
    val localId: String,
    val title: String,
    val fields: List<String>,
    val message: String,
    val localValues: List<SyncConflictValue>,
    val cloudValues: List<SyncConflictValue>,
)

enum class ConflictField {
    CHECKLIST_SORT_INDEX,
    CHECKLIST_NAME,
    TODO_CHECKLIST,
    TODO_SORT_INDEX,
    TODO_TITLE,
    TODO_PRIORITY,
    TODO_DUE_TIME,
    TODO_COMPLETED,
    TODO_REMINDER_REPEAT,
    TODO_IMAGE,
    TODO_TRASH_STATE,
    SETTINGS_LANGUAGE,
}

data class SyncConflict(
    val recordType: String,
    val localId: String,
    val fields: Set<ConflictField>,
    val message: String,
)
