package com.milesxue.pixeldone

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.pm.PackageInstaller
import com.milesxue.pixeldone.data.update.AppUpdateDownload
import com.milesxue.pixeldone.data.update.AppUpdateDownloadSource
import com.milesxue.pixeldone.data.update.AppUpdateDownloadProgress
import com.milesxue.pixeldone.data.update.AppUpdateInfo
import com.milesxue.pixeldone.data.update.AppUpdateSource
import com.milesxue.pixeldone.data.update.activeUpdateDownloadMatchesLatest
import com.milesxue.pixeldone.data.update.appUpdateDownloadRequests
import com.milesxue.pixeldone.data.update.fullScreenIntentPermissionStateForUpdate
import com.milesxue.pixeldone.data.update.installPromptBackgroundActivityStartMode
import com.milesxue.pixeldone.data.update.isPixelDoneUpdateApkFileName
import com.milesxue.pixeldone.data.update.isUpdateDownloadStalled
import com.milesxue.pixeldone.data.update.parseUpdateChecksum
import com.milesxue.pixeldone.data.update.sha256Hex
import com.milesxue.pixeldone.data.update.shouldCleanInstalledUpdate
import com.milesxue.pixeldone.data.update.staleUpdateApkFileNames
import com.milesxue.pixeldone.data.update.updateApkVersion
import com.milesxue.pixeldone.data.update.updateReleaseApkFileName
import com.milesxue.pixeldone.data.update.UpdateInstallStatusAction
import com.milesxue.pixeldone.data.update.UpdateInstallStatusPendingIntentFlags
import com.milesxue.pixeldone.data.update.updateInstallStatusAction
import com.milesxue.pixeldone.ui.todo.formatDownloadedMegabytes
import com.milesxue.pixeldone.ui.todo.formatUpdateDownloadMessage
import com.milesxue.pixeldone.ui.todo.shouldShowAvailableUpdateDialog
import com.milesxue.pixeldone.ui.todo.shouldShowUpdatePromptSetting
import com.milesxue.pixeldone.ui.todo.UpdateInstallPermissionAction
import com.milesxue.pixeldone.ui.todo.updateInstallPermissionAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateDownloaderTest {
    @Test
    fun parseUpdateChecksum_requiresExactHashAndApkFileName() {
        val hash = "a".repeat(64)
        assertEquals(
            hash.uppercase(),
            parseUpdateChecksum("$hash  PixelDone-3.2.5-release.apk", "PixelDone-3.2.5-release.apk"),
        )
        assertNull(parseUpdateChecksum("$hash  other.apk", "PixelDone-3.2.5-release.apk"))
        assertNull(parseUpdateChecksum("not-a-hash  PixelDone-3.2.5-release.apk", "PixelDone-3.2.5-release.apk"))
    }

    @Test
    fun sha256Hex_usesUppercaseDigest() {
        assertEquals(
            "BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD",
            sha256Hex("abc".toByteArray()),
        )
    }

    @Test
    fun updateReleaseApkFileName_usesExactFormalReleaseAssetName() {
        assertEquals(
            "PixelDone-2.4.5-release.apk",
            updateReleaseApkFileName("2.4.5"),
        )
    }

    @Test
    fun updateApkVersion_acceptsFormalAndRcDebugPixelDoneApkNames() {
        assertTrue(isPixelDoneUpdateApkFileName("PixelDone-2.5.6-release.apk"))
        assertEquals("2.5.6", updateApkVersion("PixelDone-2.5.6-release.apk"))
        assertTrue(isPixelDoneUpdateApkFileName("PixelDone-2.8.0-rc.1-debug.apk"))
        assertEquals("2.8.0-rc.1", updateApkVersion("PixelDone-2.8.0-rc.1-debug.apk"))
        assertFalse(isPixelDoneUpdateApkFileName("PixelDone-2.8.0-rc.1-release.apk"))
        assertFalse(isPixelDoneUpdateApkFileName("PixelRead-2.5.6-release.apk"))
        assertFalse(isPixelDoneUpdateApkFileName("PixelDone-release.apk"))
    }

    @Test
    fun staleUpdateApkFileNames_keepsOnlyTheLatestTargetApk() {
        assertEquals(
            listOf(
                "PixelDone-2.5.4-release.apk",
                "PixelDone-2.8.0-rc.1-debug.apk",
            ),
            staleUpdateApkFileNames(
                fileNames = listOf(
                    "PixelDone-2.5.4-release.apk",
                    "PixelDone-2.8.0-rc.1-debug.apk",
                    "PixelDone-2.8.0-rc.2-debug.apk",
                    "PixelRead-2.5.5-release.apk",
                ),
                latestFileName = "PixelDone-2.8.0-rc.2-debug.apk",
            ),
        )
    }

    @Test
    fun shouldCleanInstalledUpdate_onlyCleansWhenCurrentVersionReachedDownloadedVersion() {
        assertFalse(shouldCleanInstalledUpdate(currentVersion = "2.5.5", downloadedVersion = "2.5.6"))
        assertTrue(shouldCleanInstalledUpdate(currentVersion = "2.5.6", downloadedVersion = "2.5.6"))
        assertTrue(shouldCleanInstalledUpdate(currentVersion = "2.6.0", downloadedVersion = "2.5.7"))
        assertFalse(shouldCleanInstalledUpdate(currentVersion = "2.8.0-rc.1", downloadedVersion = "2.8.0-rc.2"))
        assertTrue(shouldCleanInstalledUpdate(currentVersion = "2.8.0-rc.2", downloadedVersion = "2.8.0-rc.1"))
        assertTrue(shouldCleanInstalledUpdate(currentVersion = "2.8.0", downloadedVersion = "2.8.0-rc.9"))
        assertFalse(shouldCleanInstalledUpdate(currentVersion = "bad", downloadedVersion = "2.5.6"))
        assertFalse(shouldCleanInstalledUpdate(currentVersion = "2.5.6", downloadedVersion = "bad"))
    }

    @Test
    fun activeUpdateDownloadMatchesLatest_requiresSameVersionFileNameSourceAndUrl() {
        val active = AppUpdateDownload(
            version = "2.8.0-rc.2",
            downloadId = 42L,
            fileName = "PixelDone-2.8.0-rc.2-debug.apk",
            source = AppUpdateSource.GitHub,
            url = "https://github.com/download/PixelDone-2.8.0-rc.2-debug.apk",
        )

        assertTrue(
            activeUpdateDownloadMatchesLatest(
                download = active,
                latestVersion = "2.8.0-rc.2",
                latestFileName = "PixelDone-2.8.0-rc.2-debug.apk",
                latestSource = AppUpdateSource.GitHub,
                latestUrl = "https://github.com/download/PixelDone-2.8.0-rc.2-debug.apk",
            ),
        )
        assertFalse(
            activeUpdateDownloadMatchesLatest(
                download = active,
                latestVersion = "2.6.0",
                latestFileName = "PixelDone-2.6.0-release.apk",
                latestSource = AppUpdateSource.GitHub,
                latestUrl = "https://github.com/download/PixelDone-2.8.0-rc.2-debug.apk",
            ),
        )
        assertFalse(
            activeUpdateDownloadMatchesLatest(
                download = active,
                latestVersion = "2.8.0-rc.2",
                latestFileName = "PixelDone-2.8.0-rc.2-debug.apk",
                latestSource = AppUpdateSource.Gitee,
                latestUrl = "https://gitee.com/download/PixelDone-2.8.0-rc.2-debug.apk",
            ),
        )
    }

    @Test
    fun appUpdateDownloadRequests_keepsGitHubThenGiteeQueueForSameApkFile() {
        val info = AppUpdateInfo(
            version = "2.8.0-rc.2",
            releasePageUrl = "https://github.com/Siyuan-Xue/PixelDone/releases/tag/v2.8.0-rc.2",
            fileName = "PixelDone-2.8.0-rc.2-debug.apk",
            downloadSources = listOf(
                AppUpdateDownloadSource(
                    source = AppUpdateSource.GitHub,
                    url = "https://github.com/download/PixelDone-2.8.0-rc.2-debug.apk",
                ),
                AppUpdateDownloadSource(
                    source = AppUpdateSource.Gitee,
                    url = "https://gitee.com/download/PixelDone-2.8.0-rc.2-debug.apk",
                ),
            ),
        )

        val requests = appUpdateDownloadRequests(info)

        assertEquals(listOf(AppUpdateSource.GitHub, AppUpdateSource.Gitee), requests.map { it.source })
        assertEquals(
            listOf(
                "PixelDone-2.8.0-rc.2-debug.apk",
                "PixelDone-2.8.0-rc.2-debug.apk",
            ),
            requests.map { it.fileName },
        )
        assertEquals(
            "https://gitee.com/download/PixelDone-2.8.0-rc.2-debug.apk",
            requests.last().url,
        )
    }

    @Test
    fun isUpdateDownloadStalled_usesElapsedTimeSinceLastByteProgress() {
        assertFalse(
            isUpdateDownloadStalled(
                elapsedMillis = 29_999L,
                lastProgressMillis = 0L,
                stalledWaitMillis = 30_000L,
            ),
        )
        assertTrue(
            isUpdateDownloadStalled(
                elapsedMillis = 30_000L,
                lastProgressMillis = 0L,
                stalledWaitMillis = 30_000L,
            ),
        )
        assertFalse(
            isUpdateDownloadStalled(
                elapsedMillis = 45_000L,
                lastProgressMillis = 20_000L,
                stalledWaitMillis = 30_000L,
            ),
        )
    }

    @Test
    fun appUpdateDownloadProgress_calculatesClampedPercentWhenTotalIsKnown() {
        assertEquals(42, AppUpdateDownloadProgress(bytesDownloaded = 42L, totalBytes = 100L).percent)
        assertEquals(100, AppUpdateDownloadProgress(bytesDownloaded = 120L, totalBytes = 100L).percent)
        assertNull(AppUpdateDownloadProgress(bytesDownloaded = 42L, totalBytes = null).percent)
        assertNull(AppUpdateDownloadProgress(bytesDownloaded = 42L, totalBytes = 0L).percent)
    }

    @Test
    fun formatUpdateDownloadMessage_showsPercentWhenKnown() {
        assertEquals(
            "downloading: v2.5.6 42%",
            formatUpdateDownloadMessage(
                version = "2.5.6",
                progress = AppUpdateDownloadProgress(bytesDownloaded = 42L, totalBytes = 100L),
            ),
        )
    }

    @Test
    fun formatUpdateDownloadMessage_showsMegabytesWhenTotalIsUnknown() {
        assertEquals("1.5MB", formatDownloadedMegabytes(1_572_864L))
        assertEquals(
            "downloading: v2.5.6 1.5MB",
            formatUpdateDownloadMessage(
                version = "2.5.6",
                progress = AppUpdateDownloadProgress(bytesDownloaded = 1_572_864L),
            ),
        )
    }

    @Test
    fun shouldShowAvailableUpdateDialog_respectsPreferenceAndActiveDownload() {
        assertTrue(
            shouldShowAvailableUpdateDialog(
                neverShowUpdateDialog = false,
                hasActiveUpdateDownload = false,
            ),
        )
        assertFalse(
            shouldShowAvailableUpdateDialog(
                neverShowUpdateDialog = true,
                hasActiveUpdateDownload = false,
            ),
        )
        assertFalse(
            shouldShowAvailableUpdateDialog(
                neverShowUpdateDialog = false,
                hasActiveUpdateDownload = true,
            ),
        )
    }

    @Test
    fun shouldShowUpdatePromptSetting_invertsStoredNeverShowPreference() {
        assertTrue(shouldShowUpdatePromptSetting(neverShowUpdateDialog = false))
        assertFalse(shouldShowUpdatePromptSetting(neverShowUpdateDialog = true))
    }

    @Test
    fun updateInstallPermissionAction_requestsConfigurationWhenInstallerPermissionIsMissing() {
        assertEquals(
            UpdateInstallPermissionAction.RequestInstallPermission,
            updateInstallPermissionAction(hasInstallUpdatePermission = false),
        )
        assertEquals(
            UpdateInstallPermissionAction.OpenInstaller,
            updateInstallPermissionAction(hasInstallUpdatePermission = true),
        )
    }

    @Test
    fun fullScreenIntentPermissionStateForUpdate_isUnsetBeforeAndroid14() {
        assertNull(
            fullScreenIntentPermissionStateForUpdate(
                sdkInt = 33,
                currentlyGranted = true,
            ),
        )
        assertNull(
            fullScreenIntentPermissionStateForUpdate(
                sdkInt = 33,
                currentlyGranted = false,
            ),
        )
    }

    @Test
    fun fullScreenIntentPermissionStateForUpdate_preservesAndroid14AndLaterState() {
        assertEquals(
            PackageInstaller.SessionParams.PERMISSION_STATE_GRANTED,
            fullScreenIntentPermissionStateForUpdate(
                sdkInt = 34,
                currentlyGranted = true,
            ),
        )
        assertEquals(
            PackageInstaller.SessionParams.PERMISSION_STATE_DENIED,
            fullScreenIntentPermissionStateForUpdate(
                sdkInt = 37,
                currentlyGranted = false,
            ),
        )
    }

    @Test
    fun updateInstallStatusAction_onlyLaunchesAValidPendingUserPrompt() {
        assertEquals(
            UpdateInstallStatusAction.LaunchPrompt,
            updateInstallStatusAction(
                status = PackageInstaller.STATUS_PENDING_USER_ACTION,
                hasConfirmationIntent = true,
            ),
        )
        assertEquals(
            UpdateInstallStatusAction.Fail,
            updateInstallStatusAction(
                status = PackageInstaller.STATUS_PENDING_USER_ACTION,
                hasConfirmationIntent = false,
            ),
        )
        assertEquals(
            UpdateInstallStatusAction.Clear,
            updateInstallStatusAction(
                status = PackageInstaller.STATUS_SUCCESS,
                hasConfirmationIntent = false,
            ),
        )
        assertEquals(
            UpdateInstallStatusAction.Fail,
            updateInstallStatusAction(
                status = PackageInstaller.STATUS_FAILURE_ABORTED,
                hasConfirmationIntent = false,
            ),
        )
    }

    @Test
    fun installPromptBackgroundActivityStartMode_tracksAndroidBalRequirements() {
        assertNull(installPromptBackgroundActivityStartMode(sdkInt = 33))
        @Suppress("DEPRECATION")
        val installPromptMode = ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
        assertEquals(installPromptMode, installPromptBackgroundActivityStartMode(sdkInt = 34))
        assertEquals(installPromptMode, installPromptBackgroundActivityStartMode(sdkInt = 35))
        assertEquals(installPromptMode, installPromptBackgroundActivityStartMode(sdkInt = 36))
        assertEquals(installPromptMode, installPromptBackgroundActivityStartMode(sdkInt = 37))
    }

    @Test
    fun installStatusCallbackIsOneShotAndMutable() {
        assertTrue(UpdateInstallStatusPendingIntentFlags and PendingIntent.FLAG_ONE_SHOT != 0)
        assertTrue(UpdateInstallStatusPendingIntentFlags and PendingIntent.FLAG_MUTABLE != 0)
        assertTrue(UpdateInstallStatusPendingIntentFlags and PendingIntent.FLAG_UPDATE_CURRENT != 0)
    }
}
