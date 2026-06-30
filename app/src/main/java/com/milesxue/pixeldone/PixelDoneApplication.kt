package com.milesxue.pixeldone

import android.app.Application
import com.milesxue.pixeldone.di.PixelDoneAppContainer

/**
 * PixelDone 的应用级入口。
 *
 * 教学说明：Android 会先创建 Application，再创建 Activity、Receiver、Service。
 * 我们把全局依赖容器放在这里，让不同 Android 组件拿到同一套数据层与系统服务封装。
 */
class PixelDoneApplication : Application() {
    internal lateinit var appContainer: PixelDoneAppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = PixelDoneAppContainer(this)
    }
}
