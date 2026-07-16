package com.milesxue.pixeldone.di

import android.content.Context
import com.milesxue.pixeldone.BuildConfig
import com.milesxue.pixeldone.PixelDoneApplication
import com.milesxue.pixeldone.data.image.TodoImageStore
import com.milesxue.pixeldone.data.local.CompositeTodoSyncLocalStore
import com.milesxue.pixeldone.data.local.RoomSyncMetadataStore
import com.milesxue.pixeldone.data.local.RoomTodoStateStore
import com.milesxue.pixeldone.data.settings.DataStorePixelDoneSettingsStore
import com.milesxue.pixeldone.data.sync.AuthSessionRepository
import com.milesxue.pixeldone.data.sync.LocalOnlyAuthSessionRepository
import com.milesxue.pixeldone.data.sync.LocalOnlySyncCoordinator
import com.milesxue.pixeldone.data.sync.SecureAuthSessionStore
import com.milesxue.pixeldone.data.sync.SupabaseAuthSessionRepository
import com.milesxue.pixeldone.data.sync.SupabaseConfig
import com.milesxue.pixeldone.data.sync.SupabaseHttpClient
import com.milesxue.pixeldone.data.sync.SupabaseRemoteTodoDataSource
import com.milesxue.pixeldone.data.sync.SupabaseRealtimeSyncController
import com.milesxue.pixeldone.data.sync.SyncCoordinator
import com.milesxue.pixeldone.data.sync.TodoSyncCoordinator
import com.milesxue.pixeldone.data.sync.TodoAttachmentSyncService
import com.milesxue.pixeldone.data.sync.SupabaseTodoAttachmentRemoteStore
import com.milesxue.pixeldone.data.sync.WorkManagerSyncScheduler
import com.milesxue.pixeldone.data.todo.TodoPreferences
import com.milesxue.pixeldone.data.todo.TodoRepository
import com.milesxue.pixeldone.data.update.AppUpdateChannel
import com.milesxue.pixeldone.data.update.AppUpdateCheckCache
import com.milesxue.pixeldone.data.update.AppUpdateDownloader
import com.milesxue.pixeldone.data.update.UpdateService
import com.milesxue.pixeldone.domain.todo.ClockProvider
import com.milesxue.pixeldone.domain.todo.SystemClockProvider
import com.milesxue.pixeldone.reminder.ActiveXHighAlarmStore
import com.milesxue.pixeldone.reminder.AndroidReminderScheduler
import com.milesxue.pixeldone.reminder.ReminderScheduler

/**
 * Manual dependency container.
 *
 * The app stays single-module and local-first, so a small explicit container is still
 * enough. Cloud dependencies are only created when BuildConfig supplies Supabase config.
 */
internal class PixelDoneAppContainer(context: Context) {
    private val appContext = context.applicationContext

    val clockProvider: ClockProvider = SystemClockProvider
    val todoPreferences: TodoPreferences = TodoPreferences.create(appContext)
    val settingsStore = DataStorePixelDoneSettingsStore.create(appContext, todoPreferences)
    private val todoStateStore = RoomTodoStateStore.create(appContext, todoPreferences)
    private val syncMetadataStore = RoomSyncMetadataStore.create(appContext)
    private val todoSyncLocalStore = CompositeTodoSyncLocalStore(todoStateStore, syncMetadataStore)
    val todoRepository: TodoRepository = TodoRepository(todoStateStore)
    private val supabaseConfig = SupabaseConfig(
        baseUrl = BuildConfig.SUPABASE_URL,
        publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
        allowInsecureHttp = BuildConfig.ALLOW_INSECURE_SUPABASE_HTTP,
    )
    private val syncWorkScheduler = WorkManagerSyncScheduler(appContext)
    private val supabaseHttpClient = SupabaseHttpClient(supabaseConfig)
    val todoImageStore: TodoImageStore = TodoImageStore(appContext)
    val authSessionRepository: AuthSessionRepository = if (supabaseConfig.isConfigured) {
        SupabaseAuthSessionRepository(
            config = supabaseConfig,
            httpClient = supabaseHttpClient,
            sessionStore = SecureAuthSessionStore(appContext),
        )
    } else {
        LocalOnlyAuthSessionRepository()
    }
    val todoAttachmentSyncService: TodoAttachmentSyncService? = if (supabaseConfig.isConfigured) {
        TodoAttachmentSyncService(
            authRepository = authSessionRepository,
            localStore = todoStateStore,
            imageStore = todoImageStore,
            remoteStore = SupabaseTodoAttachmentRemoteStore(supabaseConfig),
        )
    } else {
        null
    }
    val syncCoordinator: SyncCoordinator = if (supabaseConfig.isConfigured) {
        TodoSyncCoordinator(
            authSessionRepository = authSessionRepository,
            localStore = todoSyncLocalStore,
            remoteDataSource = SupabaseRemoteTodoDataSource(supabaseHttpClient),
            clockProvider = clockProvider,
            settingsStore = settingsStore,
            workScheduler = syncWorkScheduler,
            attachmentSyncService = todoAttachmentSyncService,
        )
    } else {
        LocalOnlySyncCoordinator()
    }
    @Suppress("unused")
    private val realtimeSyncController = if (supabaseConfig.isConfigured) {
        SupabaseRealtimeSyncController(
            config = supabaseConfig,
            authRepository = authSessionRepository,
            coordinator = syncCoordinator,
        )
    } else {
        null
    }
    val appUpdateDownloader: AppUpdateDownloader = AppUpdateDownloader(appContext)
    val updateService: UpdateService = UpdateService(
        downloader = appUpdateDownloader,
        channel = AppUpdateChannel.fromBuildConfigValue(BuildConfig.UPDATE_CHANNEL),
        checkCache = AppUpdateCheckCache(appContext),
    )
    val reminderScheduler: ReminderScheduler = AndroidReminderScheduler(appContext)
    val activeXHighAlarmStore: ActiveXHighAlarmStore = ActiveXHighAlarmStore.create(appContext)
}

internal fun Context.pixelDoneAppContainer(): PixelDoneAppContainer {
    val application = applicationContext as? PixelDoneApplication
    return application?.appContainer ?: PixelDoneAppContainer(applicationContext)
}
