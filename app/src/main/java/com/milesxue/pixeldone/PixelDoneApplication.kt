package com.milesxue.pixeldone

import android.app.Application
import com.milesxue.pixeldone.di.PixelDoneAppContainer
import com.milesxue.pixeldone.widget.PixelDoneWidgetUpdater

/**
 * Application-level entry point.
 *
 * Android creates the Application before activities, receivers, and services.
 * PixelDone keeps its long-lived dependency container here so every boundary uses the same data layer.
 */
class PixelDoneApplication : Application() {
    internal lateinit var appContainer: PixelDoneAppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = PixelDoneAppContainer(this)
        appContainer.todoRepository.observeTodoState {
            PixelDoneWidgetUpdater.requestUpdate(this)
        }
        PixelDoneWidgetUpdater.requestUpdate(this)
    }
}
