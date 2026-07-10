package com.milesxue.pixeldone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.toArgb
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.milesxue.pixeldone.di.pixelDoneAppContainer
import com.milesxue.pixeldone.domain.todo.AppLanguage
import com.milesxue.pixeldone.ui.theme.ClaudeIvory
import com.milesxue.pixeldone.ui.theme.ClaudeSlateDark
import com.milesxue.pixeldone.ui.todo.PixelDoneApp

/**
 * Android entry point.
 *
 * MainActivity owns lifecycle setup, system bars, and the top-level Compose host only.
 * Storage, reminders, and update dependencies are created by the Application/DI layer.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        applyPixelDoneLanguage(pixelDoneAppContainer().settingsStore.loadSettings().languageMode)
        super.onCreate(savedInstanceState)
        applyPixelDoneSystemBars()
        setContent {
            PixelDoneApp()
        }
    }
}

internal fun applyPixelDoneLanguage(language: AppLanguage) {
    val locales = language.localeTag
        ?.let(LocaleListCompat::forLanguageTags)
        ?: LocaleListCompat.getEmptyLocaleList()
    if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != locales.toLanguageTags()) {
        AppCompatDelegate.setApplicationLocales(locales)
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
