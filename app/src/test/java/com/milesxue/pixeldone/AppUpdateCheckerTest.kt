package com.milesxue.pixeldone

import com.milesxue.pixeldone.data.update.AppUpdateCheckResult
import com.milesxue.pixeldone.data.update.AppUpdateInfo
import com.milesxue.pixeldone.data.update.GitHubRelease
import com.milesxue.pixeldone.data.update.ReleaseAsset
import com.milesxue.pixeldone.data.update.fetchLatestRelease
import com.milesxue.pixeldone.data.update.findReleaseApkUrl
import com.milesxue.pixeldone.data.update.isNewerSemanticVersion
import com.milesxue.pixeldone.data.update.parseGitHubRelease
import com.milesxue.pixeldone.data.update.releaseToUpdateCheckResult
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
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
    fun releaseToUpdateCheckResult_requiresExactLatestReleaseApk() {
        val release = GitHubRelease(
            tagName = "v1.3.1",
            htmlUrl = "https://github.com/Siyuan-Xue/PixelDone/releases/tag/v1.3.1",
            assets = listOf(
                ReleaseAsset("PixelDone-1.3.1-debug.apk", "https://example.test/debug.apk"),
            ),
        )

        assertEquals(
            AppUpdateCheckResult.Unavailable,
            releaseToUpdateCheckResult(
                release = release,
                projectName = "PixelDone",
                currentVersion = "1.0.0",
            ),
        )
    }

    @Test
    fun releaseToUpdateCheckResult_returnsAvailableWithExactLatestReleaseApk() {
        val release = GitHubRelease(
            tagName = "v1.3.1",
            htmlUrl = "https://github.com/Siyuan-Xue/PixelDone/releases/tag/v1.3.1",
            assets = listOf(
                ReleaseAsset(
                    "PixelDone-1.3.1-release.apk",
                    "https://example.test/release.apk",
                ),
            ),
        )

        val result = releaseToUpdateCheckResult(
            release = release,
            projectName = "PixelDone",
            currentVersion = "1.0.0",
        )

        assertTrue(result is AppUpdateCheckResult.Available)
        val available = result as AppUpdateCheckResult.Available
        assertEquals("1.3.1", available.info.version)
        assertEquals("https://example.test/release.apk", available.info.apkDownloadUrl)
    }

    @Test
    fun appUpdateInfo_keepsExactReleaseApkDownloadUrl() {
        val info = AppUpdateInfo(
            version = "1.3.1",
            releasePageUrl = "https://github.com/Siyuan-Xue/PixelDone/releases/tag/v1.3.1",
            apkDownloadUrl = "https://github.com/Siyuan-Xue/PixelDone/releases/download/v1.3.1/PixelDone-1.3.1-release.apk",
        )

        assertEquals(
            "https://github.com/Siyuan-Xue/PixelDone/releases/download/v1.3.1/PixelDone-1.3.1-release.apk",
            info.apkDownloadUrl,
        )
    }

    @Test
    fun fetchLatestRelease_returnsNullWhenConnectionTimesOut() = runBlocking {
        val release = fetchLatestRelease("https://example.test/releases/latest") { url ->
            TimeoutConnection(url)
        }

        assertNull(release)
    }

    @Test
    fun fetchLatestRelease_preservesCoroutineCancellation() {
        var cancelled = false

        try {
            runBlocking {
                fetchLatestRelease("https://example.test/releases/latest") { url ->
                    CancellingConnection(url)
                }
            }
        } catch (_: CancellationException) {
            cancelled = true
        }

        assertTrue(cancelled)
    }

    private class TimeoutConnection(url: URL) : HttpURLConnection(url) {
        override fun disconnect() = Unit

        override fun usingProxy(): Boolean = false

        override fun connect() = Unit

        override fun getResponseCode(): Int {
            throw SocketTimeoutException("timeout")
        }
    }

    private class CancellingConnection(url: URL) : HttpURLConnection(url) {
        override fun disconnect() = Unit

        override fun usingProxy(): Boolean = false

        override fun connect() = Unit

        override fun getResponseCode(): Int {
            throw CancellationException("cancelled")
        }
    }
}
