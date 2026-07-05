package com.milesxue.pixeldone.data.settings

import android.content.Context
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import com.milesxue.pixeldone.data.todo.TodoPreferences
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
) : PixelDoneSettingsStore {
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
        editSettings { stored -> stored.copy(settings = stored.settings.copy(darkTheme = enabled)) }
    }

    override fun saveDockConfig(config: DockConfig) {
        editSettings { stored -> stored.copy(settings = stored.settings.copy(dockConfig = config.normalized())) }
    }

    override fun saveNeverShowUpdateDialog(neverShow: Boolean) {
        editSettings { stored -> stored.copy(settings = stored.settings.copy(neverShowUpdateDialog = neverShow)) }
    }

    override fun saveFutureSyncEnabled(enabled: Boolean) {
        editSettings { stored -> stored.copy(settings = stored.settings.copy(futureSyncEnabled = enabled)) }
    }

    override fun observeSettings(onChange: () -> Unit): () -> Unit {
        runBlocking(Dispatchers.IO) { ensureMigrated() }
        val job = storeScope.launch {
            dataStore.data.drop(1).collect { onChange() }
        }
        return { job.cancel() }
    }

    private fun editSettings(transform: (StoredPixelDoneSettings) -> StoredPixelDoneSettings) {
        runBlocking(Dispatchers.IO) {
            ensureMigrated()
            dataStore.updateData { stored -> transform(stored).copy(migratedFromSharedPreferences = true) }
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
) : PixelDoneSettingsStore {
    private var settings = initialSettings
    private val listeners = mutableListOf<() -> Unit>()

    override fun loadSettings(): PixelDoneSettings = settings

    override fun saveDarkTheme(enabled: Boolean) {
        update(settings.copy(darkTheme = enabled))
    }

    override fun saveDockConfig(config: DockConfig) {
        update(settings.copy(dockConfig = config.normalized()))
    }

    override fun saveNeverShowUpdateDialog(neverShow: Boolean) {
        update(settings.copy(neverShowUpdateDialog = neverShow))
    }

    override fun saveFutureSyncEnabled(enabled: Boolean) {
        update(settings.copy(futureSyncEnabled = enabled))
    }

    override fun observeSettings(onChange: () -> Unit): () -> Unit {
        listeners += onChange
        return { listeners -= onChange }
    }

    private fun update(updatedSettings: PixelDoneSettings) {
        settings = updatedSettings
        listeners.forEach { it() }
    }
}

private data class StoredPixelDoneSettings(
    val settings: PixelDoneSettings = PixelDoneSettings(),
    val migratedFromSharedPreferences: Boolean = false,
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
        }.store(output, null)
    }
}

private fun Properties.booleanProperty(name: String, defaultValue: Boolean): Boolean {
    return getProperty(name)?.toBooleanStrictOrNull() ?: defaultValue
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
