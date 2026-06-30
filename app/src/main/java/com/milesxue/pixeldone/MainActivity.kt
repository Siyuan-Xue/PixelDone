package com.milesxue.pixeldone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.toArgb
import com.milesxue.pixeldone.ui.theme.ClaudeIvory
import com.milesxue.pixeldone.ui.theme.ClaudeSlateDark
import com.milesxue.pixeldone.ui.todo.PixelDoneApp

/**
 * Android 应用入口。
 *
 * 教学说明：Activity 只负责连接 Android 生命周期、系统栏设置和顶层 Compose 内容。
 * 存储、提醒、更新下载等依赖都放到 Application/DI package 中，避免入口类变成“万能类”。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyPixelDoneSystemBars()
        setContent {
            PixelDoneApp()
        }
    }
}

fun ComponentActivity.applyPixelDoneSystemBars(darkTheme: Boolean = false) {
    val backgroundScrim = if (darkTheme) ClaudeSlateDark.toArgb() else ClaudeIvory.toArgb()
    enableEdgeToEdge(
        statusBarStyle = if (darkTheme) {
            SystemBarStyle.dark(scrim = android.graphics.Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(
                scrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
            )
        },
        navigationBarStyle = if (darkTheme) {
            SystemBarStyle.dark(scrim = backgroundScrim)
        } else {
            SystemBarStyle.light(
                scrim = backgroundScrim,
                darkScrim = backgroundScrim,
            )
        },
    )
}
