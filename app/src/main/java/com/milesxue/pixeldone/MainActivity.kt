package com.milesxue.pixeldone

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    private var requestedChecklistId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        applyPixelDoneLanguage(pixelDoneAppContainer().settingsStore.loadSettings().languageMode)
        super.onCreate(savedInstanceState)
        requestedChecklistId = intent.requestedChecklistId()
        applyPixelDoneSystemBars()
        setContent {
            PixelDoneApp(
                requestedChecklistId = requestedChecklistId,
                onChecklistRequestConsumed = { requestedChecklistId = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedChecklistId = intent.requestedChecklistId()
    }

    companion object {
        internal const val ActionOpenChecklist = "com.milesxue.pixeldone.action.OPEN_CHECKLIST"
        internal const val ExtraChecklistId = "com.milesxue.pixeldone.extra.CHECKLIST_ID"

        fun openChecklistIntent(context: Context, checklistId: String): Intent =
            Intent(context, MainActivity::class.java).apply {
                action = ActionOpenChecklist
                putExtra(ExtraChecklistId, checklistId)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
    }
}

private fun Intent?.requestedChecklistId(): String? =
    this?.takeIf { it.action == MainActivity.ActionOpenChecklist }
        ?.getStringExtra(MainActivity.ExtraChecklistId)
        ?.takeIf(String::isNotBlank)

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
