package com.milesxue.pixeldone.di

import android.content.Context
import com.milesxue.pixeldone.BuildConfig
import com.milesxue.pixeldone.PixelDoneApplication
import com.milesxue.pixeldone.data.image.TodoImageStore
import com.milesxue.pixeldone.data.todo.TodoPreferences
import com.milesxue.pixeldone.data.todo.TodoRepository
import com.milesxue.pixeldone.data.update.AppUpdateDownloader
import com.milesxue.pixeldone.data.update.AppUpdateChannel
import com.milesxue.pixeldone.data.update.UpdateService
import com.milesxue.pixeldone.domain.todo.ClockProvider
import com.milesxue.pixeldone.domain.todo.SystemClockProvider
import com.milesxue.pixeldone.reminder.ActiveXHighAlarmStore
import com.milesxue.pixeldone.reminder.AndroidReminderScheduler
import com.milesxue.pixeldone.reminder.ReminderScheduler

/**
 * Manual dependency container.
 *
 * This small app does not need Hilt yet. The container creates long-lived repositories,
 * stores, schedulers, and update services behind a single construction boundary.
 */
internal class PixelDoneAppContainer(context: Context) {
    private val appContext = context.applicationContext

    val clockProvider: ClockProvider = SystemClockProvider
    val todoPreferences: TodoPreferences = TodoPreferences.create(appContext)
    val todoRepository: TodoRepository = TodoRepository(todoPreferences)
    val todoImageStore: TodoImageStore = TodoImageStore(appContext)
    val appUpdateDownloader: AppUpdateDownloader = AppUpdateDownloader(appContext)
    val updateService: UpdateService = UpdateService(
        downloader = appUpdateDownloader,
        channel = AppUpdateChannel.fromBuildConfigValue(BuildConfig.UPDATE_CHANNEL),
    )
    val reminderScheduler: ReminderScheduler = AndroidReminderScheduler(appContext)
    val activeXHighAlarmStore: ActiveXHighAlarmStore = ActiveXHighAlarmStore.create(appContext)
}

internal fun Context.pixelDoneAppContainer(): PixelDoneAppContainer {
    val application = applicationContext as? PixelDoneApplication
    return application?.appContainer ?: PixelDoneAppContainer(applicationContext)
}
