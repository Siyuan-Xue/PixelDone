package com.milesxue.pixeldone

import org.junit.Assert.assertEquals
import org.junit.Test

class AppUpdateDownloaderTest {
    @Test
    fun updateReleaseApkFileName_usesExactFormalReleaseAssetName() {
        assertEquals(
            "PixelDone-2.4.5-release.apk",
            updateReleaseApkFileName("2.4.5"),
        )
    }
}
