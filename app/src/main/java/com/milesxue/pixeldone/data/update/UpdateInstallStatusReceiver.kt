package com.milesxue.pixeldone.data.update

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build

internal enum class UpdateInstallStatusAction {
    LaunchPrompt,
    Clear,
    Fail,
}

internal fun updateInstallStatusAction(
    status: Int,
    hasConfirmationIntent: Boolean,
): UpdateInstallStatusAction = when {
    status == PackageInstaller.STATUS_PENDING_USER_ACTION && hasConfirmationIntent ->
        UpdateInstallStatusAction.LaunchPrompt
    status == PackageInstaller.STATUS_SUCCESS -> UpdateInstallStatusAction.Clear
    else -> UpdateInstallStatusAction.Fail
}

internal fun installPromptBackgroundActivityStartMode(sdkInt: Int): Int? = when {
    sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
        @Suppress("DEPRECATION")
        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
    else -> null
}

internal class UpdateInstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, statusIntent: Intent) {
        if (statusIntent.action != ACTION_INSTALL_STATUS) return

        val status = statusIntent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE,
        )
        val confirmationIntent = statusIntent.installConfirmationIntent()
        when (updateInstallStatusAction(status, confirmationIntent != null)) {
            UpdateInstallStatusAction.LaunchPrompt -> {
                val sessionId = statusIntent.getIntExtra(EXTRA_INSTALL_SESSION_ID, 0)
                if (launchInstallConfirmation(context, checkNotNull(confirmationIntent), sessionId)) {
                    recordUpdateInstallStatus(context, AppUpdateInstallStatus.Prompted)
                } else {
                    recordUpdateInstallStatus(context, AppUpdateInstallStatus.Failed)
                }
            }
            UpdateInstallStatusAction.Clear -> clearUpdateInstallStatus(context)
            UpdateInstallStatusAction.Fail ->
                recordUpdateInstallStatus(context, AppUpdateInstallStatus.Failed)
        }
    }

    private fun launchInstallConfirmation(
        context: Context,
        confirmationIntent: Intent,
        sessionId: Int,
    ): Boolean {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val prompt = PendingIntent.getActivity(
                    context,
                    sessionId,
                    confirmationIntent,
                    PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE,
                )
                prompt.send()
            } else {
                val mode = checkNotNull(
                    installPromptBackgroundActivityStartMode(Build.VERSION.SDK_INT),
                )
                val creatorOptions = ActivityOptions.makeBasic()
                    .setPendingIntentCreatorBackgroundActivityStartMode(mode)
                    .toBundle()
                val prompt = PendingIntent.getActivity(
                    context,
                    sessionId,
                    confirmationIntent,
                    PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE,
                    creatorOptions,
                )
                val senderOptions = ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(mode)
                    .toBundle()
                prompt.send(context, 0, null, null, null, null, senderOptions)
            }
            true
        } catch (_: PendingIntent.CanceledException) {
            false
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun Intent.installConfirmationIntent(): Intent? {
        if (getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE) !=
            PackageInstaller.STATUS_PENDING_USER_ACTION
        ) {
            return null
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(Intent.EXTRA_INTENT) as? Intent
        }
    }

    internal companion object {
        const val ACTION_INSTALL_STATUS = "com.milesxue.pixeldone.action.UPDATE_INSTALL_STATUS"
        const val EXTRA_INSTALL_SESSION_ID =
            "com.milesxue.pixeldone.extra.UPDATE_INSTALL_SESSION_ID"
    }
}
