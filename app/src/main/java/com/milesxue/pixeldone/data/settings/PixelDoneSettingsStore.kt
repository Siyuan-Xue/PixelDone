package com.milesxue.pixeldone.data.settings

import android.content.Context
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import com.milesxue.pixeldone.data.sync.LocalSettingsSyncRecord
import com.milesxue.pixeldone.data.sync.RemoteUserSettingsRecord
import com.milesxue.pixeldone.data.sync.SettingsSyncLocalStore
import com.milesxue.pixeldone.data.todo.TodoPreferences
import com.milesxue.pixeldone.domain.sync.SyncRecordState
import com.milesxue.pixeldone.domain.todo.DefaultDockActions
import com.milesxue.pixeldone.domain.todo.DockAction
import com.milesxue.pixeldone.domain.todo.DockConfig
import com.milesxue.pixeldone.domain.todo.DockPlusPlacement
import com.milesxue.pixeldone.domain.todo.PixelDoneSettings
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.Properties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

interface PixelDoneSettingsStore {
    fun loadSettings(): PixelDoneSettings
    fun saveDarkTheme(enabled: Boolean)
    fun saveDockConfig(config: DockConfig)
    fun saveNeverShowUpdateDialog(neverShow: Boolean)
    fun saveFutureSyncEnabled(enabled: Boolean)
    fun observeSettings(onChange: () -> Unit): () -> Unit
}

class DataStorePixelDoneSettingsStore private constructor(
    context: Context,
    private val legacyPreferences: TodoPreferences,
) : PixelDoneSettingsStore, SettingsSyncLocalStore {
    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dataStore = DataStoreFactory.create(
        serializer = StoredPixelDoneSettingsSerializer,
        scope = storeScope,
        produceFile = {
            File(context.applicationContext.filesDir, "datastore/pixel_done_settings.properties").also { file ->
                file.parentFile?.mkdirs()
            }
        },
    )

    override fun loadSettings(): PixelDoneSettings = runBlocking(Dispatchers.IO) {
        ensureMigrated()
        dataStore.data.map { stored -> stored.settings }.first()
    }

    override fun saveDarkTheme(enabled: Boolean) {
        editSettings(markSyncDirty = true) { stored ->
            stored.copy(settings = stored.settings.copy(darkTheme = enabled))
        }
    }

    override fun saveDockConfig(config: DockConfig) {
        editSettings(markSyncDirty = true) { stored ->
            stored.copy(settings = stored.settings.copy(dockConfig = config.normalized()))
        }
    }

    override fun saveNeverShowUpdateDialog(neverShow: Boolean) {
        editSettings(markSyncDirty = false) { stored ->
            stored.copy(settings = stored.settings.copy(neverShowUpdateDialog = neverShow))
        }
    }

    override fun saveFutureSyncEnabled(enabled: Boolean) {
        editSettings(markSyncDirty = false) { stored ->
            stored.copy(settings = stored.settings.copy(futureSyncEnabled = enabled))
        }
    }

    override fun observeSettings(onChange: () -> Unit): () -> Unit {
        runBlocking(Dispatchers.IO) { ensureMigrated() }
        val job = storeScope.launch {
            dataStore.data.drop(1).collect { onChange() }
        }
        return { job.cancel() }
    }

    override suspend fun loadSettingsForSync(nowMillis: Long): LocalSettingsSyncRecord {
        ensureMigrated()
        return dataStore.data.map { stored -> stored.toLocalSettingsSyncRecord(nowMillis) }.first()
    }

    override suspend fun applyRemoteSettings(record: RemoteUserSettingsRecord, syncedAtMillis: Long) {
        ensureMigrated()
        dataStore.updateData { stored ->
            stored.copy(
                settings = stored.settings.copy(
                    darkTheme = record.darkTheme,
                    dockConfig = record.toDockConfig(),
                ),
                settingsUpdatedAtMillis = record.updatedAtMillis,
                settingsSyncState = SyncRecordState.SYNCED.name,
                settingsLastSyncedAtMillis = syncedAtMillis,
                settingsRemoteVersion = record.remoteVersion,
                settingsLastSyncError = null,
                migratedFromSharedPreferences = true,
            )
        }
    }

    override suspend fun markSettingsSynced(record: RemoteUserSettingsRecord, syncedAtMillis: Long) {
        ensureMigrated()
        dataStore.updateData { stored ->
            val unchangedSincePush = stored.settings.darkTheme == record.darkTheme &&
                stored.settings.dockConfig.normalized() == record.toDockConfig().normalized() &&
                stored.settingsUpdatedAtMillis == record.updatedAtMillis
            stored.copy(
                settingsSyncState = if (unchangedSincePush) SyncRecordState.SYNCED.name else SyncRecordState.NOT_SYNCED.name,
                settingsLastSyncedAtMillis = if (unchangedSincePush) syncedAtMillis else stored.settingsLastSyncedAtMillis,
                settingsRemoteVersion = record.remoteVersion ?: stored.settingsRemoteVersion,
                settingsLastSyncError = null,
                migratedFromSharedPreferences = true,
            )
        }
    }

    override suspend fun markSettingsSyncError(message: String) {
        ensureMigrated()
        dataStore.updateData { stored ->
            stored.copy(
                settingsSyncState = if (stored.settingsSyncState == SyncRecordState.SYNCED.name) {
                    stored.settingsSyncState
                } else {
                    SyncRecordState.ERROR.name
                },
                settingsLastSyncError = message.take(MaxSettingsSyncErrorLength),
                migratedFromSharedPreferences = true,
            )
        }
    }

    private fun editSettings(
        markSyncDirty: Boolean,
        transform: (StoredPixelDoneSettings) -> StoredPixelDoneSettings,
    ) {
        runBlocking(Dispatchers.IO) {
            ensureMigrated()
            val nowMillis = System.currentTimeMillis()
            dataStore.updateData { stored ->
                val updated = transform(stored)
                if (markSyncDirty && updated.settings.syncPayload() != stored.settings.syncPayload()) {
                    updated.copy(
                        settingsUpdatedAtMillis = nowMillis,
                        settingsSyncState = SyncRecordState.NOT_SYNCED.name,
                        settingsLastSyncError = null,
                        migratedFromSharedPreferences = true,
                    )
                } else {
                    updated.copy(migratedFromSharedPreferences = true)
                }
            }
        }
    }

    private suspend fun ensureMigrated() {
        dataStore.updateData { stored ->
            if (stored.migratedFromSharedPreferences) return@updateData stored
            StoredPixelDoneSettings(
                settings = PixelDoneSettings(
                    darkTheme = legacyPreferences.loadDarkTheme(),
                    dockConfig = legacyPreferences.loadDockConfig(),
                    neverShowUpdateDialog = legacyPreferences.loadNeverShowUpdateDialog(),
                    futureSyncEnabled = false,
                ),
                migratedFromSharedPreferences = true,
            )
        }
    }

    companion object {
        fun create(context: Context, legacyPreferences: TodoPreferences): DataStorePixelDoneSettingsStore {
            return DataStorePixelDoneSettingsStore(context.applicationContext, legacyPreferences)
        }
    }
}

class InMemoryPixelDoneSettingsStore(
    initialSettings: PixelDoneSettings = PixelDoneSettings(),
) : PixelDoneSettingsStore, SettingsSyncLocalStore {
    private var stored = StoredPixelDoneSettings(settings = initialSettings, migratedFromSharedPreferences = true)
    private val listeners = mutableListOf<() -> Unit>()

    override fun loadSettings(): PixelDoneSettings = stored.settings

    override fun saveDarkTheme(enabled: Boolean) {
        update(stored.copy(settings = stored.settings.copy(darkTheme = enabled)).dirtyIfSyncPayloadChanged(stored))
    }

    override fun saveDockConfig(config: DockConfig) {
        update(stored.copy(settings = stored.settings.copy(dockConfig = config.normalized())).dirtyIfSyncPayloadChanged(stored))
    }

    override fun saveNeverShowUpdateDialog(neverShow: Boolean) {
        update(stored.copy(settings = stored.settings.copy(neverShowUpdateDialog = neverShow)))
    }

    override fun saveFutureSyncEnabled(enabled: Boolean) {
        update(stored.copy(settings = stored.settings.copy(futureSyncEnabled = enabled)))
    }

    override fun observeSettings(onChange: () -> Unit): () -> Unit {
        listeners += onChange
        return { listeners -= onChange }
    }

    override suspend fun loadSettingsForSync(nowMillis: Long): LocalSettingsSyncRecord = stored.toLocalSettingsSyncRecord(nowMillis)

    override suspend fun applyRemoteSettings(record: RemoteUserSettingsRecord, syncedAtMillis: Long) {
        update(
            stored.copy(
                settings = stored.settings.copy(
                    darkTheme = record.darkTheme,
                    dockConfig = record.toDockConfig(),
                ),
                settingsUpdatedAtMillis = record.updatedAtMillis,
                settingsSyncState = SyncRecordState.SYNCED.name,
                settingsLastSyncedAtMillis = syncedAtMillis,
                settingsRemoteVersion = record.remoteVersion,
                settingsLastSyncError = null,
            ),
        )
    }

    override suspend fun markSettingsSynced(record: RemoteUserSettingsRecord, syncedAtMillis: Long) {
        stored = stored.copy(
            settingsSyncState = SyncRecordState.SYNCED.name,
            settingsLastSyncedAtMillis = syncedAtMillis,
            settingsRemoteVersion = record.remoteVersion,
            settingsLastSyncError = null,
        )
    }

    override suspend fun markSettingsSyncError(message: String) {
        stored = stored.copy(
            settingsSyncState = SyncRecordState.ERROR.name,
            settingsLastSyncError = message.take(MaxSettingsSyncErrorLength),
        )
    }

    private fun update(updatedSettings: StoredPixelDoneSettings) {
        stored = updatedSettings
        listeners.forEach { it() }
    }
}

private data class StoredPixelDoneSettings(
    val settings: PixelDoneSettings = PixelDoneSettings(),
    val migratedFromSharedPreferences: Boolean = false,
    val settingsUpdatedAtMillis: Long = 0L,
    val settingsSyncState: String = SyncRecordState.LOCAL_ONLY.name,
    val settingsLastSyncedAtMillis: Long? = null,
    val settingsRemoteVersion: Long? = null,
    val settingsLastSyncError: String? = null,
)

private object StoredPixelDoneSettingsSerializer : Serializer<StoredPixelDoneSettings> {
    override val defaultValue: StoredPixelDoneSettings = StoredPixelDoneSettings()

    override suspend fun readFrom(input: InputStream): StoredPixelDoneSettings {
        val properties = Properties()
        return runCatching {
            properties.load(input)
            StoredPixelDoneSettings(
                settings = PixelDoneSettings(
                    darkTheme = properties.booleanProperty("darkTheme", false),
                    dockConfig = DockConfig(
                        plusPlacement = properties.dockPlusPlacementProperty(),
                        actions = properties.dockActionsProperty(),
                    ).normalized(),
                    neverShowUpdateDialog = properties.booleanProperty("neverShowUpdateDialog", false),
                    futureSyncEnabled = properties.booleanProperty("futureSyncEnabled", false),
                ),
                migratedFromSharedPreferences = properties.booleanProperty("migratedFromSharedPreferences", false),
                settingsUpdatedAtMillis = properties.longProperty("settingsUpdatedAtMillis", 0L),
                settingsSyncState = properties.getProperty("settingsSyncState") ?: SyncRecordState.LOCAL_ONLY.name,
                settingsLastSyncedAtMillis = properties.nullableLongProperty("settingsLastSyncedAtMillis"),
                settingsRemoteVersion = properties.nullableLongProperty("settingsRemoteVersion"),
                settingsLastSyncError = properties.getProperty("settingsLastSyncError").takeUnless { it.isNullOrBlank() },
            )
        }.getOrDefault(defaultValue)
    }

    override suspend fun writeTo(t: StoredPixelDoneSettings, output: OutputStream) {
        Properties().apply {
            setProperty("darkTheme", t.settings.darkTheme.toString())
            setProperty("dockPlusPlacement", t.settings.dockConfig.plusPlacement.name)
            setProperty("dockActions", t.settings.dockConfig.actions.joinToString(DockActionSeparator) { it.name })
            setProperty("neverShowUpdateDialog", t.settings.neverShowUpdateDialog.toString())
            setProperty("futureSyncEnabled", t.settings.futureSyncEnabled.toString())
            setProperty("migratedFromSharedPreferences", t.migratedFromSharedPreferences.toString())
            setProperty("settingsUpdatedAtMillis", t.settingsUpdatedAtMillis.toString())
            setProperty("settingsSyncState", t.settingsSyncState)
            setProperty("settingsLastSyncedAtMillis", t.settingsLastSyncedAtMillis?.toString().orEmpty())
            setProperty("settingsRemoteVersion", t.settingsRemoteVersion?.toString().orEmpty())
            setProperty("settingsLastSyncError", t.settingsLastSyncError.orEmpty())
        }.store(output, null)
    }
}

private fun StoredPixelDoneSettings.toLocalSettingsSyncRecord(nowMillis: Long): LocalSettingsSyncRecord {
    val updatedAt = settingsUpdatedAtMillis.takeIf { it > 0L } ?: nowMillis
    return LocalSettingsSyncRecord(
        darkTheme = settings.darkTheme,
        dockPlusPlacement = settings.dockConfig.normalized().plusPlacement.name,
        dockActions = settings.dockConfig.normalized().actions.map { it.name },
        updatedAtMillis = updatedAt,
        syncState = settingsSyncState,
        lastSyncedAtMillis = settingsLastSyncedAtMillis,
        remoteVersion = settingsRemoteVersion,
        lastSyncError = settingsLastSyncError,
    )
}

private fun StoredPixelDoneSettings.dirtyIfSyncPayloadChanged(previous: StoredPixelDoneSettings): StoredPixelDoneSettings =
    if (settings.syncPayload() == previous.settings.syncPayload()) {
        this
    } else {
        copy(
            settingsUpdatedAtMillis = System.currentTimeMillis(),
            settingsSyncState = SyncRecordState.NOT_SYNCED.name,
            settingsLastSyncError = null,
        )
    }

private fun PixelDoneSettings.syncPayload(): Pair<Boolean, DockConfig> = darkTheme to dockConfig.normalized()

private fun RemoteUserSettingsRecord.toDockConfig(): DockConfig = DockConfig(
    plusPlacement = runCatching { DockPlusPlacement.valueOf(dockPlusPlacement) }.getOrDefault(DockPlusPlacement.CENTER),
    actions = dockActions.mapNotNull { action -> runCatching { DockAction.valueOf(action) }.getOrNull() },
).normalized()

private fun Properties.booleanProperty(name: String, defaultValue: Boolean): Boolean {
    return getProperty(name)?.toBooleanStrictOrNull() ?: defaultValue
}

private fun Properties.longProperty(name: String, defaultValue: Long): Long {
    return getProperty(name)?.toLongOrNull() ?: defaultValue
}

private fun Properties.nullableLongProperty(name: String): Long? {
    return getProperty(name)?.takeIf { it.isNotBlank() }?.toLongOrNull()
}

private fun Properties.dockPlusPlacementProperty(): DockPlusPlacement {
    return getProperty("dockPlusPlacement")
        ?.let { value -> runCatching { DockPlusPlacement.valueOf(value) }.getOrNull() }
        ?: DockPlusPlacement.CENTER
}

private fun Properties.dockActionsProperty(): List<DockAction> {
    return getProperty("dockActions")
        ?.split(DockActionSeparator)
        ?.mapNotNull { value -> runCatching { DockAction.valueOf(value) }.getOrNull() }
        ?: DefaultDockActions
}

private const val DockActionSeparator = ","
private const val MaxSettingsSyncErrorLength = 280
