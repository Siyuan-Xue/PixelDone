package com.milesxue.pixeldone.domain.todo

/**
 * User-visible local preferences for PixelDone.
 *
 * This model is pure Kotlin so UI and tests can depend on it without Android storage types.
 */
data class PixelDoneSettings(
    val darkTheme: Boolean = false,
    val dockConfig: DockConfig = DockConfig(),
    val neverShowUpdateDialog: Boolean = false,
    val futureSyncEnabled: Boolean = false,
    val languageMode: AppLanguage = AppLanguage.SYSTEM,
) {
    val showUpdateDialogs: Boolean
        get() = !neverShowUpdateDialog
}

enum class AppLanguage(val syncValue: String, val localeTag: String?) {
    SYSTEM("system", null),
    ENGLISH("en", "en"),
    SIMPLIFIED_CHINESE("zh-Hans", "zh-Hans"),
    ARABIC("ar", "ar"),
    FRENCH("fr", "fr"),
    RUSSIAN("ru", "ru"),
    SPANISH("es", "es");

    companion object {
        fun fromSyncValue(value: String): AppLanguage = entries.firstOrNull { it.syncValue == value } ?: SYSTEM
    }
}
