package com.milesxue.pixeldone

import com.milesxue.pixeldone.data.update.AppUpdateDownload
import com.milesxue.pixeldone.data.update.AppUpdateDownloadProgress
import com.milesxue.pixeldone.data.update.activeUpdateDownloadMatchesLatest
import com.milesxue.pixeldone.data.update.isPixelDoneUpdateApkFileName
import com.milesxue.pixeldone.data.update.shouldCleanInstalledUpdate
import com.milesxue.pixeldone.data.update.staleUpdateApkFileNames
import com.milesxue.pixeldone.data.update.updateApkVersion
import com.milesxue.pixeldone.data.update.updateReleaseApkFileName
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
    fun activeUpdateDownloadMatchesLatest_requiresSameVersionAndFileName() {
        val active = AppUpdateDownload(
            version = "2.8.0-rc.2",
            downloadId = 42L,
            fileName = "PixelDone-2.8.0-rc.2-debug.apk",
        )

        assertTrue(
            activeUpdateDownloadMatchesLatest(
                download = active,
                latestVersion = "2.8.0-rc.2",
                latestFileName = "PixelDone-2.8.0-rc.2-debug.apk",
            ),
        )
        assertFalse(
            activeUpdateDownloadMatchesLatest(
                download = active,
                latestVersion = "2.6.0",
                latestFileName = "PixelDone-2.6.0-release.apk",
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
}
