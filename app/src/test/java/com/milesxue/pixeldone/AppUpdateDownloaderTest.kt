package com.milesxue.pixeldone

import com.milesxue.pixeldone.data.update.AppUpdateDownloadProgress
import com.milesxue.pixeldone.data.update.updateReleaseApkFileName
import com.milesxue.pixeldone.ui.todo.formatDownloadedMegabytes
import com.milesxue.pixeldone.ui.todo.formatUpdateDownloadMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun appUpdateDownloadProgress_calculatesClampedPercentWhenTotalIsKnown() {
        assertEquals(42, AppUpdateDownloadProgress(bytesDownloaded = 42L, totalBytes = 100L).percent)
        assertEquals(100, AppUpdateDownloadProgress(bytesDownloaded = 120L, totalBytes = 100L).percent)
        assertNull(AppUpdateDownloadProgress(bytesDownloaded = 42L, totalBytes = null).percent)
        assertNull(AppUpdateDownloadProgress(bytesDownloaded = 42L, totalBytes = 0L).percent)
    }

    @Test
    fun formatUpdateDownloadMessage_showsPercentWhenKnown() {
        assertEquals(
            "downloading: v2.5.5 42%",
            formatUpdateDownloadMessage(
                version = "2.5.5",
                progress = AppUpdateDownloadProgress(bytesDownloaded = 42L, totalBytes = 100L),
            ),
        )
    }

    @Test
    fun formatUpdateDownloadMessage_showsMegabytesWhenTotalIsUnknown() {
        assertEquals("1.5MB", formatDownloadedMegabytes(1_572_864L))
        assertEquals(
            "downloading: v2.5.5 1.5MB",
            formatUpdateDownloadMessage(
                version = "2.5.5",
                progress = AppUpdateDownloadProgress(bytesDownloaded = 1_572_864L),
            ),
        )
    }
}
