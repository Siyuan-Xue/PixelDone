package com.milesxue.pixeldone

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.milesxue.pixeldone.data.update.AppUpdateInstallStatus
import com.milesxue.pixeldone.data.update.clearUpdateInstallStatus
import com.milesxue.pixeldone.data.update.consumeUpdateInstallStatus
import com.milesxue.pixeldone.data.update.updateInstallStatusPendingIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class UpdateInstallStatusReceiverDeviceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun oneShotPackageInstallerCallbackLaunchesPromptOnlyOnce() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        clearUpdateInstallStatus(context)
        val confirmationIntent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        )
        val sessionId = 91_001
        val callback = updateInstallStatusPendingIntent(context, sessionId)
        val pendingUserAction = Intent().apply {
            putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_PENDING_USER_ACTION)
            putExtra(Intent.EXTRA_INTENT, confirmationIntent)
        }

        try {
            runShellCommand("am force-stop com.android.settings")
            runShellCommand("input keyevent KEYCODE_HOME")
            SystemClock.sleep(500L)
            instrumentation.runOnMainSync {
                callback.send(context, 0, pendingUserAction)
            }
            assertTrue(
                "Android Settings was not launched",
                waitForActivityRecord("com.android.settings"),
            )
            assertEquals(
                AppUpdateInstallStatus.Prompted,
                consumeUpdateInstallStatus(context),
            )
            val finalResultWasRejected = try {
                callback.send(
                    context,
                    0,
                    Intent().putExtra(
                        PackageInstaller.EXTRA_STATUS,
                        PackageInstaller.STATUS_SUCCESS,
                    ),
                )
                false
            } catch (_: PendingIntent.CanceledException) {
                true
            }
            assertTrue(
                "The final installer callback could still cold-start PixelDone",
                finalResultWasRejected,
            )
        } finally {
            runShellCommand("input keyevent KEYCODE_BACK")
            clearUpdateInstallStatus(context)
        }
    }

    private fun waitForActivityRecord(packageName: String): Boolean {
        val deadline = SystemClock.elapsedRealtime() + 5_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            val launched = runShellCommand("dumpsys activity activities")
                .lineSequence()
                .any { line ->
                    "ActivityRecord{" in line && packageName in line
                }
            if (launched) return true
            SystemClock.sleep(100L)
        }
        return false
    }

    private fun runShellCommand(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            .bufferedReader()
            .use { reader -> reader.readText() }
    }
}
