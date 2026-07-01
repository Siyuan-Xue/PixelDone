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
import com.milesxue.pixeldone.reminder.AndroidReminderScheduler
import com.milesxue.pixeldone.reminder.ReminderScheduler

/**
 * 手动依赖注入容器。
 *
 * 初学者可以把它理解成“对象工厂”：负责创建 Repository、Store、Scheduler 等长期对象。
 * Activity 和 ViewModel 只使用这些对象，不再关心它们如何构造，从而降低耦合。
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
}

internal fun Context.pixelDoneAppContainer(): PixelDoneAppContainer {
    val application = applicationContext as? PixelDoneApplication
    return application?.appContainer ?: PixelDoneAppContainer(applicationContext)
}
