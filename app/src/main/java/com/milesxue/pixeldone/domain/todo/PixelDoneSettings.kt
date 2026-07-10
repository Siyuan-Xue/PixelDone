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

enum class AppLanguage(
    val syncValue: String,
    val localeTag: String?,
    val nativeDisplayName: String?,
) {
    SYSTEM("system", null, null),
    ENGLISH("en", "en", "English"),
    SIMPLIFIED_CHINESE("zh-Hans", "zh-Hans", "简体中文"),
    ARABIC("ar", "ar", "العربية"),
    FRENCH("fr", "fr", "Français"),
    RUSSIAN("ru", "ru", "Русский"),
    SPANISH("es", "es", "Español");

    companion object {
        fun fromSyncValue(value: String): AppLanguage = entries.firstOrNull { it.syncValue == value } ?: SYSTEM
    }
}
