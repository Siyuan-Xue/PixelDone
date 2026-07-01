package com.milesxue.pixeldone

import com.milesxue.pixeldone.data.update.AppUpdateChannel
import com.milesxue.pixeldone.data.update.AppUpdateCheckResult
import com.milesxue.pixeldone.data.update.AppUpdateDownloadSource
import com.milesxue.pixeldone.data.update.AppUpdateInfo
import com.milesxue.pixeldone.data.update.AppUpdateSource
import com.milesxue.pixeldone.data.update.GiteeRelease
import com.milesxue.pixeldone.data.update.PixelDoneGiteeReleasesApiUrl
import com.milesxue.pixeldone.data.update.PixelDoneGitHubReleasesApiUrl
import com.milesxue.pixeldone.data.update.ReleaseAsset
import com.milesxue.pixeldone.data.update.checkAppUpdate
import com.milesxue.pixeldone.data.update.fetchGiteeReleases
import com.milesxue.pixeldone.data.update.findReleaseApkAsset
import com.milesxue.pixeldone.data.update.isNewerSemanticVersion
import com.milesxue.pixeldone.data.update.parseGitHubReleases
import com.milesxue.pixeldone.data.update.parseGiteeReleaseAssets
import com.milesxue.pixeldone.data.update.parseGiteeReleases
import com.milesxue.pixeldone.data.update.releasesToUpdateCheckResult
import com.milesxue.pixeldone.data.update.updateApkFileName
import java.io.ByteArrayInputStream
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
    fun semanticVersionComparison_acceptsStableAndRcVersions() {
        assertTrue(isNewerSemanticVersion("v1.1.0", "1.0.0"))
        assertTrue(isNewerSemanticVersion("1.1", "1.0.9"))
        assertTrue(isNewerSemanticVersion("2.8.0-rc.2", "2.8.0-rc.1"))
        assertTrue(isNewerSemanticVersion("2.8.0", "2.8.0-rc.9"))
        assertFalse(isNewerSemanticVersion("2.8.0-rc.1", "2.8.0"))
        assertFalse(isNewerSemanticVersion("v1.0.0", "1.0.0"))
        assertFalse(isNewerSemanticVersion("not-a-version", "1.0.0"))
    }

    @Test
    fun updateApkFileName_usesChannelSpecificAssetNames() {
        assertEquals(
            "PixelDone-2.8.0-release.apk",
            updateApkFileName("PixelDone", "2.8.0", AppUpdateChannel.Formal),
        )
        assertEquals(
            "PixelDone-2.8.0-rc.1-debug.apk",
            updateApkFileName("PixelDone", "2.8.0-rc.1", AppUpdateChannel.Beta),
        )
    }

    @Test
    fun findReleaseApkAsset_requiresExactAssetName() {
        val assets = listOf(
            ReleaseAsset("PixelDone-2.8.0-rc.1-debug.apk", "https://example.test/debug.apk"),
            ReleaseAsset("PixelDone-2.8.0-release.apk", "https://example.test/release.apk"),
            ReleaseAsset("PixelRead-2.8.0-release.apk", "https://example.test/other.apk"),
        )

        assertEquals(
            "https://example.test/release.apk",
            findReleaseApkAsset(assets, "PixelDone-2.8.0-release.apk")?.downloadUrl,
        )
        assertNull(findReleaseApkAsset(assets, "PixelDone-2.8.1-release.apk"))
    }

    @Test
    fun parseGiteeReleases_readsReleaseListAndInlineAssets() {
        val releases = parseGiteeReleases(
            """
            [
              {
                "id": 42,
                "tag_name": "v2.8.0-rc.1",
                "html_url": "https://gitee.com/milesxue/PixelDone/releases/tag/v2.8.0-rc.1",
                "prerelease": true,
                "author": { "html_url": "https://gitee.com/milesxue" },
                "assets": [
                  {
                    "id": 7,
                    "name": "PixelDone-2.8.0-rc.1-debug.apk",
                    "browser_download_url": "https://gitee.com/download/debug.apk"
                  }
                ]
              }
            ]
            """.trimIndent(),
        )

        assertEquals(1, releases?.size)
        val release = releases?.single()
        assertEquals(42L, release?.id)
        assertEquals("v2.8.0-rc.1", release?.tagName)
        assertEquals(true, release?.prerelease)
        assertEquals(
            "https://gitee.com/milesxue/PixelDone/releases/tag/v2.8.0-rc.1",
            release?.htmlUrl,
        )
        assertEquals("PixelDone-2.8.0-rc.1-debug.apk", release?.assets?.single()?.name)
    }

    @Test
    fun parseGiteeReleaseAssets_readsAttachFileList() {
        val assets = parseGiteeReleaseAssets(
            """
            [
              {
                "id": 11,
                "name": "PixelDone-2.8.0-release.apk",
                "browser_download_url": "https://gitee.com/download/release.apk"
              }
            ]
            """.trimIndent(),
        )

        assertEquals(1, assets?.size)
        assertEquals("PixelDone-2.8.0-release.apk", assets?.single()?.name)
        assertEquals("https://gitee.com/download/release.apk", assets?.single()?.downloadUrl)
    }

    @Test
    fun parseGitHubReleases_readsReleaseListAndAssets() {
        val releases = parseGitHubReleases(
            """
            [
              {
                "id": 84,
                "tag_name": "v2.8.0",
                "html_url": "https://github.com/Siyuan-Xue/PixelDone/releases/tag/v2.8.0",
                "prerelease": false,
                "assets": [
                  {
                    "id": 12,
                    "name": "PixelDone-2.8.0-release.apk",
                    "browser_download_url": "https://github.com/download/release.apk"
                  }
                ]
              }
            ]
            """.trimIndent(),
        )

        assertEquals(1, releases?.size)
        val release = releases?.single()
        assertEquals(84L, release?.id)
        assertEquals("v2.8.0", release?.tagName)
        assertEquals(false, release?.prerelease)
        assertEquals(
            "https://github.com/Siyuan-Xue/PixelDone/releases/tag/v2.8.0",
            release?.htmlUrl,
        )
        assertEquals("PixelDone-2.8.0-release.apk", release?.assets?.single()?.name)
    }

    @Test
    fun checkAppUpdate_prefersGitHubAndAddsGiteeFallbackDownloadSource() = runBlocking {
        val result = checkAppUpdate(
            projectName = "PixelDone",
            currentVersion = "2.7.0",
            channel = AppUpdateChannel.Formal,
            openConnection = mappedConnections(
                githubReleasesPage(1) to releaseListJson(
                    id = 1L,
                    tagName = "v2.8.0",
                    htmlUrl = "https://github.com/Siyuan-Xue/PixelDone/releases/tag/v2.8.0",
                    prerelease = false,
                    assetName = "PixelDone-2.8.0-release.apk",
                    assetUrl = "https://github.com/download/PixelDone-2.8.0-release.apk",
                ),
                giteeReleasesPage(1) to releaseListJson(
                    id = 2L,
                    tagName = "v2.8.0",
                    htmlUrl = "https://gitee.com/milesxue/PixelDone/releases/tag/v2.8.0",
                    prerelease = false,
                    assetName = "PixelDone-2.8.0-release.apk",
                    assetUrl = "https://gitee.com/download/PixelDone-2.8.0-release.apk",
                ),
            ),
        )

        assertTrue(result is AppUpdateCheckResult.Available)
        val available = result as AppUpdateCheckResult.Available
        assertEquals("2.8.0", available.info.version)
        assertEquals("https://github.com/download/PixelDone-2.8.0-release.apk", available.info.apkDownloadUrl)
        assertEquals(
            listOf(AppUpdateSource.GitHub, AppUpdateSource.Gitee),
            available.info.downloadSources.map { it.source },
        )
    }

    @Test
    fun checkAppUpdate_usesGiteeWhenGitHubFails() = runBlocking {
        val result = checkAppUpdate(
            projectName = "PixelDone",
            currentVersion = "2.7.0",
            channel = AppUpdateChannel.Formal,
            openConnection = mappedConnections(
                githubReleasesPage(1) to null,
                giteeReleasesPage(1) to releaseListJson(
                    id = 2L,
                    tagName = "v2.8.0",
                    htmlUrl = "https://gitee.com/milesxue/PixelDone/releases/tag/v2.8.0",
                    prerelease = false,
                    assetName = "PixelDone-2.8.0-release.apk",
                    assetUrl = "https://gitee.com/download/PixelDone-2.8.0-release.apk",
                ),
            ),
        )

        assertTrue(result is AppUpdateCheckResult.Available)
        val available = result as AppUpdateCheckResult.Available
        assertEquals(listOf(AppUpdateSource.Gitee), available.info.downloadSources.map { it.source })
        assertEquals("https://gitee.com/download/PixelDone-2.8.0-release.apk", available.info.apkDownloadUrl)
    }

    @Test
    fun checkAppUpdate_usesGiteeWhenGitHubHasNoMatchingApk() = runBlocking {
        val result = checkAppUpdate(
            projectName = "PixelDone",
            currentVersion = "2.7.0",
            channel = AppUpdateChannel.Formal,
            openConnection = mappedConnections(
                githubReleasesPage(1) to releaseListJson(
                    id = 1L,
                    tagName = "v2.8.0",
                    htmlUrl = "https://github.com/Siyuan-Xue/PixelDone/releases/tag/v2.8.0",
                    prerelease = false,
                    assetName = "PixelDone-2.8.0-debug.apk",
                    assetUrl = "https://github.com/download/wrong.apk",
                ),
                giteeReleasesPage(1) to releaseListJson(
                    id = 2L,
                    tagName = "v2.8.0",
                    htmlUrl = "https://gitee.com/milesxue/PixelDone/releases/tag/v2.8.0",
                    prerelease = false,
                    assetName = "PixelDone-2.8.0-release.apk",
                    assetUrl = "https://gitee.com/download/PixelDone-2.8.0-release.apk",
                ),
            ),
        )

        assertTrue(result is AppUpdateCheckResult.Available)
        val available = result as AppUpdateCheckResult.Available
        assertEquals(listOf(AppUpdateSource.Gitee), available.info.downloadSources.map { it.source })
        assertEquals("https://gitee.com/download/PixelDone-2.8.0-release.apk", available.info.apkDownloadUrl)
    }

    @Test
    fun formalUpdate_ignoresPrereleaseAndUsesLatestStableWithExactAsset() {
        val result = releasesToUpdateCheckResult(
            releases = listOf(
                giteeRelease(
                    id = 1L,
                    tagName = "v2.9.0-rc.1",
                    prerelease = true,
                    assets = listOf(
                        ReleaseAsset(
                            "PixelDone-2.9.0-rc.1-debug.apk",
                            "https://example.test/2.9.0-rc.1-debug.apk",
                        ),
                    ),
                ),
                giteeRelease(
                    id = 2L,
                    tagName = "v2.8.0",
                    prerelease = false,
                    assets = listOf(
                        ReleaseAsset(
                            "PixelDone-2.8.0-release.apk",
                            "https://example.test/2.8.0-release.apk",
                        ),
                    ),
                ),
                giteeRelease(
                    id = 3L,
                    tagName = "v2.7.1",
                    prerelease = false,
                    assets = listOf(
                        ReleaseAsset(
                            "PixelDone-2.7.1-release.apk",
                            "https://example.test/2.7.1-release.apk",
                        ),
                    ),
                ),
            ),
            projectName = "PixelDone",
            currentVersion = "2.7.0",
            channel = AppUpdateChannel.Formal,
        )

        assertTrue(result is AppUpdateCheckResult.Available)
        val available = result as AppUpdateCheckResult.Available
        assertEquals("2.8.0", available.info.version)
        assertEquals("PixelDone-2.8.0-release.apk", available.info.fileName)
        assertEquals("https://example.test/2.8.0-release.apk", available.info.apkDownloadUrl)
    }

    @Test
    fun betaUpdate_requiresPrereleaseRcAndDebugAsset() {
        val result = releasesToUpdateCheckResult(
            releases = listOf(
                giteeRelease(
                    id = 1L,
                    tagName = "v2.8.0",
                    prerelease = false,
                    assets = listOf(
                        ReleaseAsset(
                            "PixelDone-2.8.0-release.apk",
                            "https://example.test/2.8.0-release.apk",
                        ),
                    ),
                ),
                giteeRelease(
                    id = 2L,
                    tagName = "v2.8.0-beta.1",
                    prerelease = true,
                    assets = listOf(
                        ReleaseAsset(
                            "PixelDone-2.8.0-beta.1-debug.apk",
                            "https://example.test/beta.apk",
                        ),
                    ),
                ),
                giteeRelease(
                    id = 3L,
                    tagName = "v2.8.0-rc.2",
                    prerelease = true,
                    assets = listOf(
                        ReleaseAsset(
                            "PixelDone-2.8.0-rc.2-debug.apk",
                            "https://example.test/2.8.0-rc.2-debug.apk",
                        ),
                    ),
                ),
                giteeRelease(
                    id = 4L,
                    tagName = "v2.8.0-rc.1",
                    prerelease = true,
                    assets = listOf(
                        ReleaseAsset(
                            "PixelDone-2.8.0-rc.1-debug.apk",
                            "https://example.test/2.8.0-rc.1-debug.apk",
                        ),
                    ),
                ),
            ),
            projectName = "PixelDone",
            currentVersion = "2.8.0-rc.1",
            channel = AppUpdateChannel.Beta,
        )

        assertTrue(result is AppUpdateCheckResult.Available)
        val available = result as AppUpdateCheckResult.Available
        assertEquals("2.8.0-rc.2", available.info.version)
        assertEquals("PixelDone-2.8.0-rc.2-debug.apk", available.info.fileName)
    }

    @Test
    fun betaUpdate_ordersBaseVersionBeforeRcNumber() {
        val result = releasesToUpdateCheckResult(
            releases = listOf(
                giteeRelease(
                    id = 1L,
                    tagName = "v2.8.9-rc.10",
                    prerelease = true,
                    assets = listOf(
                        ReleaseAsset(
                            "PixelDone-2.8.9-rc.10-debug.apk",
                            "https://example.test/2.8.9-rc.10-debug.apk",
                        ),
                    ),
                ),
                giteeRelease(
                    id = 2L,
                    tagName = "v2.9.0-rc.1",
                    prerelease = true,
                    assets = listOf(
                        ReleaseAsset(
                            "PixelDone-2.9.0-rc.1-debug.apk",
                            "https://example.test/2.9.0-rc.1-debug.apk",
                        ),
                    ),
                ),
            ),
            projectName = "PixelDone",
            currentVersion = "2.8.9-rc.9",
            channel = AppUpdateChannel.Beta,
        )

        assertTrue(result is AppUpdateCheckResult.Available)
        val available = result as AppUpdateCheckResult.Available
        assertEquals("2.9.0-rc.1", available.info.version)
    }

    @Test
    fun releaseSelection_returnsUnavailableWhenNewerReleaseLacksMatchingApk() {
        val result = releasesToUpdateCheckResult(
            releases = listOf(
                giteeRelease(
                    id = 1L,
                    tagName = "v2.8.0-rc.1",
                    prerelease = true,
                    assets = listOf(
                        ReleaseAsset(
                            "PixelDone-2.8.0-release.apk",
                            "https://example.test/wrong.apk",
                        ),
                    ),
                ),
            ),
            projectName = "PixelDone",
            currentVersion = "2.7.0",
            channel = AppUpdateChannel.Beta,
        )

        assertEquals(AppUpdateCheckResult.Unavailable, result)
    }

    @Test
    fun releaseSelection_returnsCurrentWhenNoNewerMatchingChannelExists() {
        val formalResult = releasesToUpdateCheckResult(
            releases = listOf(
                giteeRelease(
                    id = 1L,
                    tagName = "v2.7.0",
                    prerelease = false,
                    assets = listOf(
                        ReleaseAsset(
                            "PixelDone-2.7.0-release.apk",
                            "https://example.test/2.7.0-release.apk",
                        ),
                    ),
                ),
            ),
            projectName = "PixelDone",
            currentVersion = "2.7.0",
            channel = AppUpdateChannel.Formal,
        )
        val betaResult = releasesToUpdateCheckResult(
            releases = emptyList(),
            projectName = "PixelDone",
            currentVersion = "2.7.0",
            channel = AppUpdateChannel.Beta,
        )

        assertEquals(AppUpdateCheckResult.Current, formalResult)
        assertEquals(AppUpdateCheckResult.Current, betaResult)
    }

    @Test
    fun appUpdateInfo_keepsExactApkFileNameAndDownloadUrl() {
        val info = AppUpdateInfo(
            version = "2.8.0-rc.1",
            releasePageUrl = "https://gitee.com/milesxue/PixelDone/releases/tag/v2.8.0-rc.1",
            fileName = "PixelDone-2.8.0-rc.1-debug.apk",
            downloadSources = listOf(
                AppUpdateDownloadSource(
                    source = AppUpdateSource.GitHub,
                    url = "https://github.com/download/PixelDone-2.8.0-rc.1-debug.apk",
                ),
                AppUpdateDownloadSource(
                    source = AppUpdateSource.Gitee,
                    url = "https://gitee.com/download/PixelDone-2.8.0-rc.1-debug.apk",
                ),
            ),
        )

        assertEquals(
            "https://github.com/download/PixelDone-2.8.0-rc.1-debug.apk",
            info.apkDownloadUrl,
        )
        assertEquals("PixelDone-2.8.0-rc.1-debug.apk", info.fileName)
        assertEquals(listOf(AppUpdateSource.GitHub, AppUpdateSource.Gitee), info.downloadSources.map { it.source })
    }

    @Test
    fun fetchGiteeReleases_returnsNullWhenConnectionTimesOut() = runBlocking {
        val release = fetchGiteeReleases("https://example.test/releases") { url ->
            TimeoutConnection(url)
        }

        assertNull(release)
    }

    @Test
    fun fetchGiteeReleases_preservesCoroutineCancellation() {
        var cancelled = false

        try {
            runBlocking {
                fetchGiteeReleases("https://example.test/releases") { url ->
                    CancellingConnection(url)
                }
            }
        } catch (_: CancellationException) {
            cancelled = true
        }

        assertTrue(cancelled)
    }

    private fun giteeRelease(
        id: Long,
        tagName: String,
        prerelease: Boolean,
        assets: List<ReleaseAsset>,
    ): GiteeRelease =
        GiteeRelease(
            id = id,
            tagName = tagName,
            htmlUrl = "https://gitee.com/milesxue/PixelDone/releases/tag/$tagName",
            prerelease = prerelease,
            assets = assets,
        )

    private fun githubReleasesPage(page: Int): String =
        "$PixelDoneGitHubReleasesApiUrl?per_page=100&page=$page"

    private fun giteeReleasesPage(page: Int): String =
        "$PixelDoneGiteeReleasesApiUrl?direction=desc&per_page=100&page=$page"

    private fun releaseListJson(
        id: Long,
        tagName: String,
        htmlUrl: String,
        prerelease: Boolean,
        assetName: String,
        assetUrl: String,
    ): String =
        """
        [
          {
            "id": $id,
            "tag_name": "$tagName",
            "html_url": "$htmlUrl",
            "prerelease": $prerelease,
            "assets": [
              {
                "name": "$assetName",
                "browser_download_url": "$assetUrl"
              }
            ]
          }
        ]
        """.trimIndent()

    private fun mappedConnections(
        vararg responses: Pair<String, String?>,
    ): (URL) -> HttpURLConnection {
        val responseMap = responses.toMap()
        return { url ->
            responseMap[url.toString()]
                ?.let { body -> JsonConnection(url, body) }
                ?: TimeoutConnection(url)
        }
    }

    private class JsonConnection(url: URL, private val body: String) : HttpURLConnection(url) {
        override fun disconnect() = Unit

        override fun usingProxy(): Boolean = false

        override fun connect() = Unit

        override fun getResponseCode(): Int = HTTP_OK

        override fun getInputStream(): ByteArrayInputStream =
            ByteArrayInputStream(body.toByteArray())
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
