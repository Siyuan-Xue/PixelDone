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
) {
    val showUpdateDialogs: Boolean
        get() = !neverShowUpdateDialog
}
