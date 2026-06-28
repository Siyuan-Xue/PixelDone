package com.milesxue.pixeldone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateCheckerTest {
    @Test
    fun semanticVersionComparison_acceptsVPrefixAndThreePartVersions() {
        assertTrue(isNewerSemanticVersion("v1.1.0", "1.0.0"))
        assertTrue(isNewerSemanticVersion("1.1", "1.0.9"))
        assertFalse(isNewerSemanticVersion("v1.0.0", "1.0.0"))
        assertFalse(isNewerSemanticVersion("not-a-version", "1.0.0"))
    }

    @Test
    fun findReleaseApkUrl_requiresMatchingProjectVersionAndReleaseApkName() {
        val release = GitHubRelease(
            tagName = "v1.1.0",
            htmlUrl = "https://github.com/Siyuan-Xue/PixelDone/releases/tag/v1.1.0",
            assets = listOf(
                ReleaseAsset("PixelDone-1.1.0-debug.apk", "https://example.test/debug.apk"),
                ReleaseAsset("PixelDone-1.1.0-release.apk", "https://example.test/release.apk"),
                ReleaseAsset("PixelRead-1.1.0-release.apk", "https://example.test/other.apk"),
            ),
        )

        assertEquals(
            "https://example.test/release.apk",
            findReleaseApkUrl(release, "PixelDone", "1.1.0"),
        )
        assertNull(findReleaseApkUrl(release, "PixelDone", "1.2.0"))
    }

    @Test
    fun parseGitHubRelease_readsTagPageAndAssets() {
        val release = parseGitHubRelease(
            """
            {
              "tag_name": "v1.1.0",
              "html_url": "https://github.com/Siyuan-Xue/PixelDone/releases/tag/v1.1.0",
              "assets": [
                {
                  "name": "PixelDone-1.1.0-release.apk",
                  "browser_download_url": "https://github.com/Siyuan-Xue/PixelDone/releases/download/v1.1.0/PixelDone-1.1.0-release.apk",
                  "uploader": { "login": "Siyuan-Xue" }
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals("v1.1.0", release?.tagName)
        assertEquals(
            "https://github.com/Siyuan-Xue/PixelDone/releases/tag/v1.1.0",
            release?.htmlUrl,
        )
        assertEquals("PixelDone-1.1.0-release.apk", release?.assets?.single()?.name)
    }

    @Test
    fun appUpdateInfo_actionUrlPrefersApkAndFallsBackToReleasePage() {
        val withApk = AppUpdateInfo(
            version = "1.3.0",
            releasePageUrl = "https://github.com/Siyuan-Xue/PixelDone/releases/tag/v1.3.0",
            apkDownloadUrl = "https://github.com/Siyuan-Xue/PixelDone/releases/download/v1.3.0/PixelDone-1.3.0-release.apk",
        )
        val withoutApk = withApk.copy(apkDownloadUrl = null)

        assertEquals(
            "https://github.com/Siyuan-Xue/PixelDone/releases/download/v1.3.0/PixelDone-1.3.0-release.apk",
            withApk.actionUrl,
        )
        assertEquals(
            "https://github.com/Siyuan-Xue/PixelDone/releases/tag/v1.3.0",
            withoutApk.actionUrl,
        )
    }
}
