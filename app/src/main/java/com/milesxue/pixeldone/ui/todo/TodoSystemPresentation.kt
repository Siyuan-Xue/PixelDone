package com.milesxue.pixeldone.ui.todo

import android.os.Build
import com.milesxue.pixeldone.data.update.AppUpdateDownloadProgress
import com.milesxue.pixeldone.data.update.AppUpdateInfo
import com.milesxue.pixeldone.domain.todo.ReminderCapability
import java.util.Locale

internal const val InitialUpdateCheckDelayMillis = 600L
internal const val UpdateStatusVisibleMillis = 3_000L
private const val BytesPerMegabyte = 1024.0 * 1024.0

internal enum class UpdateUiStatus {
    Idle,
    Checking,
    Latest,
    Available,
    Offline,
    Downloading,
    Installing,
}

internal data class AppUpdateUiState(
    val status: UpdateUiStatus = UpdateUiStatus.Idle,
    val info: AppUpdateInfo? = null,
    val message: String? = null,
    val progress: AppUpdateDownloadProgress = AppUpdateDownloadProgress(),
) {
    val contentDescription: String
        get() = when (status) {
            UpdateUiStatus.Idle -> "CHECK UPDATE"
            UpdateUiStatus.Checking -> "CHECKING UPDATE"
            UpdateUiStatus.Latest -> "LATEST VERSION"
            UpdateUiStatus.Available -> "GET UPDATE"
            UpdateUiStatus.Offline -> "UPDATE CHECK UNAVAILABLE"
            UpdateUiStatus.Downloading -> "DOWNLOADING UPDATE"
            UpdateUiStatus.Installing -> "INSTALLING UPDATE"
        }

    val shouldAutoRestore: Boolean
        get() = status == UpdateUiStatus.Latest ||
            status == UpdateUiStatus.Offline ||
            status == UpdateUiStatus.Installing
}

internal fun formatUpdateDownloadMessage(
    version: String,
    progress: AppUpdateDownloadProgress = AppUpdateDownloadProgress(),
): String {
    val base = "downloading: v$version"
    progress.percent?.let { percent ->
        return "$base $percent%"
    }
    return if (progress.bytesDownloaded > 0L) {
        "$base ${formatDownloadedMegabytes(progress.bytesDownloaded)}"
    } else {
        base
    }
}

internal fun formatDownloadedMegabytes(bytes: Long): String {
    return String.format(
        Locale.US,
        "%.1fMB",
        bytes.coerceAtLeast(0L) / BytesPerMegabyte,
    )
}

internal fun shouldShowAvailableUpdateDialog(
    neverShowUpdateDialog: Boolean,
    hasActiveUpdateDownload: Boolean,
): Boolean = !neverShowUpdateDialog && !hasActiveUpdateDownload

internal fun shouldShowUpdatePromptSetting(neverShowUpdateDialog: Boolean): Boolean =
    !neverShowUpdateDialog

internal enum class UpdateInstallPermissionAction {
    OpenInstaller,
    RequestInstallPermission,
}

internal fun updateInstallPermissionAction(hasInstallUpdatePermission: Boolean): UpdateInstallPermissionAction {
    return if (hasInstallUpdatePermission) {
        UpdateInstallPermissionAction.OpenInstaller
    } else {
        UpdateInstallPermissionAction.RequestInstallPermission
    }
}

internal fun hasFullScreenIntentAccessForSdk(
    sdkInt: Int,
    canUseFullScreenIntent: Boolean,
): Boolean = sdkInt < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || canUseFullScreenIntent

internal enum class SystemReminderPermissionTarget {
    EXACT_ALARM,
    FULL_SCREEN_INTENT,
}

internal data class SystemReminderPermissionDecision(
    val target: SystemReminderPermissionTarget,
    val queueFullScreenFollowUp: Boolean,
)

internal fun systemReminderPermissionDecision(
    missingCapabilities: Set<ReminderCapability>,
): SystemReminderPermissionDecision? {
    return when {
        ReminderCapability.EXACT_ALARM_ACCESS in missingCapabilities -> {
            SystemReminderPermissionDecision(
                target = SystemReminderPermissionTarget.EXACT_ALARM,
                queueFullScreenFollowUp =
                    ReminderCapability.FULL_SCREEN_INTENT_ACCESS in missingCapabilities,
            )
        }
        ReminderCapability.FULL_SCREEN_INTENT_ACCESS in missingCapabilities -> {
            SystemReminderPermissionDecision(
                target = SystemReminderPermissionTarget.FULL_SCREEN_INTENT,
                queueFullScreenFollowUp = false,
            )
        }
        else -> null
    }
}
