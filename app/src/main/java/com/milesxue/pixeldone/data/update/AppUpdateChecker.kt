package com.milesxue.pixeldone.data.update

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val PixelDoneGiteeReleasesApiUrl =
    "https://gitee.com/api/v5/repos/milesxue/PixelDone/releases"
internal const val PixelDoneGiteeReleasePageUrl =
    "https://gitee.com/milesxue/PixelDone/releases"
internal const val PixelDoneGitHubReleasesApiUrl =
    "https://api.github.com/repos/Siyuan-Xue/PixelDone/releases"
internal const val PixelDoneGitHubReleasePageUrl =
    "https://github.com/Siyuan-Xue/PixelDone/releases"
internal const val PixelDoneProjectName = "PixelDone"
private const val ReleasePageSize = 100

internal enum class AppUpdateSource {
    GitHub,
    Gitee,
}

internal enum class AppUpdateChannel {
    Formal,
    Beta,
    ;

    companion object {
        fun fromBuildConfigValue(value: String): AppUpdateChannel =
            when (value.trim().lowercase()) {
                "beta" -> Beta
                else -> Formal
            }
    }
}

internal data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
)

internal data class AppRelease(
    val id: Long,
    val tagName: String,
    val htmlUrl: String,
    val prerelease: Boolean,
    val assets: List<ReleaseAsset> = emptyList(),
)

internal typealias GiteeRelease = AppRelease

internal data class AppUpdateDownloadSource(
    val source: AppUpdateSource,
    val url: String,
)

internal data class AppUpdateInfo(
    val version: String,
    val releasePageUrl: String,
    val fileName: String,
    val downloadSources: List<AppUpdateDownloadSource>,
) {
    val apkDownloadUrl: String
        get() = downloadSources.firstOrNull()?.url.orEmpty()
}

internal sealed interface AppUpdateCheckResult {
    data class Available(val info: AppUpdateInfo) : AppUpdateCheckResult
    data object Current : AppUpdateCheckResult
    data object Unavailable : AppUpdateCheckResult
}

private data class UpdateCandidate(
    val release: AppRelease,
    val version: AppVersion,
    val fileName: String,
)

internal data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val rc: Int? = null,
) : Comparable<AppVersion> {
    val normalized: String
        get() = buildString {
            append(major)
            append(".")
            append(minor)
            append(".")
            append(patch)
            rc?.let { releaseCandidate ->
                append("-rc.")
                append(releaseCandidate)
            }
        }

    override fun compareTo(other: AppVersion): Int {
        compareValuesBy(this, other, AppVersion::major, AppVersion::minor, AppVersion::patch)
            .takeIf { it != 0 }
            ?.let { return it }
        return when {
            rc == other.rc -> 0
            rc == null -> 1
            other.rc == null -> -1
            else -> rc.compareTo(other.rc)
        }
    }
}

internal suspend fun checkPixelDoneUpdate(
    currentVersion: String,
    channel: AppUpdateChannel,
): AppUpdateCheckResult =
    checkAppUpdate(
        projectName = PixelDoneProjectName,
        currentVersion = currentVersion,
        channel = channel,
    )

internal suspend fun checkAppUpdate(
    projectName: String,
    currentVersion: String,
    channel: AppUpdateChannel,
    openConnection: (URL) -> HttpURLConnection = { url ->
        url.openConnection() as HttpURLConnection
    },
): AppUpdateCheckResult {
    val githubResult = checkSourceUpdate(
        source = AppUpdateSource.GitHub,
        releasesApiUrl = PixelDoneGitHubReleasesApiUrl,
        projectName = projectName,
        currentVersion = currentVersion,
        channel = channel,
        openConnection = openConnection,
    )
    if (githubResult is AppUpdateCheckResult.Available) {
        return githubResult.copy(
            info = githubResult.info.withFallbackSources(
                fallbackSources = fallbackGiteeDownloadSources(
                    tagName = "v${githubResult.info.version}",
                    fileName = githubResult.info.fileName,
                    openConnection = openConnection,
                ),
            ),
        )
    }
    if (githubResult == AppUpdateCheckResult.Current) return githubResult

    return checkSourceUpdate(
        source = AppUpdateSource.Gitee,
        releasesApiUrl = PixelDoneGiteeReleasesApiUrl,
        projectName = projectName,
        currentVersion = currentVersion,
        channel = channel,
        openConnection = openConnection,
    )
}

internal suspend fun checkAppUpdate(
    releasesApiUrl: String,
    projectName: String,
    currentVersion: String,
    channel: AppUpdateChannel,
    openConnection: (URL) -> HttpURLConnection = { url ->
        url.openConnection() as HttpURLConnection
    },
): AppUpdateCheckResult =
    checkSourceUpdate(
        source = AppUpdateSource.Gitee,
        releasesApiUrl = releasesApiUrl,
        projectName = projectName,
        currentVersion = currentVersion,
        channel = channel,
        openConnection = openConnection,
    )

private suspend fun checkSourceUpdate(
    source: AppUpdateSource,
    releasesApiUrl: String,
    projectName: String,
    currentVersion: String,
    channel: AppUpdateChannel,
    openConnection: (URL) -> HttpURLConnection,
): AppUpdateCheckResult {
    val releases = fetchReleases(
        source = source,
        releasesApiUrl = releasesApiUrl,
        openConnection = openConnection,
    ) ?: return AppUpdateCheckResult.Unavailable
    val candidates = sortedUpdateCandidates(
        releases = releases,
        projectName = projectName,
        currentVersion = currentVersion,
        channel = channel,
    )
    if (candidates.isEmpty()) return AppUpdateCheckResult.Current

    for (candidate in candidates) {
        val assets = if (candidate.release.assets.isNotEmpty()) {
            candidate.release.assets
        } else {
            fetchReleaseAssets(
                source = source,
                releasesApiUrl = releasesApiUrl,
                releaseId = candidate.release.id,
                openConnection = openConnection,
            ) ?: return AppUpdateCheckResult.Unavailable
        }
        val asset = findReleaseApkAsset(assets, candidate.fileName) ?: continue
        return AppUpdateCheckResult.Available(
            AppUpdateInfo(
                version = candidate.version.normalized,
                releasePageUrl = candidate.release.htmlUrl,
                fileName = candidate.fileName,
                downloadSources = listOf(
                    AppUpdateDownloadSource(
                        source = source,
                        url = asset.downloadUrl,
                    ),
                ),
            ),
        )
    }
    return AppUpdateCheckResult.Unavailable
}

internal fun releasesToUpdateCheckResult(
    releases: List<GiteeRelease>,
    projectName: String,
    currentVersion: String,
    channel: AppUpdateChannel,
): AppUpdateCheckResult {
    val candidates = sortedUpdateCandidates(
        releases = releases,
        projectName = projectName,
        currentVersion = currentVersion,
        channel = channel,
    )
    if (candidates.isEmpty()) return AppUpdateCheckResult.Current

    candidates.forEach { candidate ->
        val asset = findReleaseApkAsset(candidate.release.assets, candidate.fileName)
        if (asset != null) {
            return AppUpdateCheckResult.Available(
                AppUpdateInfo(
                    version = candidate.version.normalized,
                    releasePageUrl = candidate.release.htmlUrl,
                    fileName = candidate.fileName,
                    downloadSources = listOf(
                        AppUpdateDownloadSource(
                            source = AppUpdateSource.Gitee,
                            url = asset.downloadUrl,
                        ),
                    ),
                ),
            )
        }
    }
    return AppUpdateCheckResult.Unavailable
}

private fun AppUpdateInfo.withFallbackSources(
    fallbackSources: List<AppUpdateDownloadSource>,
): AppUpdateInfo =
    copy(
        downloadSources = (downloadSources + fallbackSources).distinctBy { source ->
            source.source to source.url
        },
    )

private suspend fun fallbackGiteeDownloadSources(
    tagName: String,
    fileName: String,
    openConnection: (URL) -> HttpURLConnection,
): List<AppUpdateDownloadSource> {
    val releases = fetchGiteeReleases(
        releasesApiUrl = PixelDoneGiteeReleasesApiUrl,
        openConnection = openConnection,
    ) ?: return emptyList()
    val release = releases.firstOrNull { release ->
        release.tagName.equals(tagName, ignoreCase = true)
    } ?: return emptyList()
    val assets = if (release.assets.isNotEmpty()) {
        release.assets
    } else {
        fetchGiteeReleaseAssets(
            releasesApiUrl = PixelDoneGiteeReleasesApiUrl,
            releaseId = release.id,
            openConnection = openConnection,
        ) ?: return emptyList()
    }
    val asset = findReleaseApkAsset(assets, fileName) ?: return emptyList()
    return listOf(AppUpdateDownloadSource(AppUpdateSource.Gitee, asset.downloadUrl))
}

internal suspend fun fetchGiteeReleases(
    releasesApiUrl: String,
    openConnection: (URL) -> HttpURLConnection = { url ->
        url.openConnection() as HttpURLConnection
    },
): List<GiteeRelease>? =
    fetchReleases(
        source = AppUpdateSource.Gitee,
        releasesApiUrl = releasesApiUrl,
        openConnection = openConnection,
    )

internal suspend fun fetchGitHubReleases(
    releasesApiUrl: String,
    openConnection: (URL) -> HttpURLConnection = { url ->
        url.openConnection() as HttpURLConnection
    },
): List<AppRelease>? =
    fetchReleases(
        source = AppUpdateSource.GitHub,
        releasesApiUrl = releasesApiUrl,
        openConnection = openConnection,
    )

private suspend fun fetchReleases(
    source: AppUpdateSource,
    releasesApiUrl: String,
    openConnection: (URL) -> HttpURLConnection,
): List<AppRelease>? {
    val releases = mutableListOf<AppRelease>()
    var page = 1
    while (true) {
        val body = fetchJson(
            url = pagedReleaseUrl(source, releasesApiUrl, page),
            openConnection = openConnection,
        ) ?: return null
        val pageReleases = parseReleases(
            json = body,
            fallbackReleasePageUrl = releasePageUrl(source),
        ) ?: return null
        if (pageReleases.isEmpty()) break
        releases += pageReleases
        if (pageReleases.size < ReleasePageSize) break
        page += 1
    }
    return releases
}

internal suspend fun fetchGiteeReleaseAssets(
    releasesApiUrl: String,
    releaseId: Long,
    openConnection: (URL) -> HttpURLConnection = { url ->
        url.openConnection() as HttpURLConnection
    },
): List<ReleaseAsset>? {
    return fetchReleaseAssets(
        source = AppUpdateSource.Gitee,
        releasesApiUrl = releasesApiUrl,
        releaseId = releaseId,
        openConnection = openConnection,
    )
}

private suspend fun fetchReleaseAssets(
    source: AppUpdateSource,
    releasesApiUrl: String,
    releaseId: Long,
    openConnection: (URL) -> HttpURLConnection,
): List<ReleaseAsset>? {
    if (source != AppUpdateSource.Gitee) return emptyList()
    val assets = mutableListOf<ReleaseAsset>()
    val assetsApiUrl = giteeReleaseAssetsApiUrl(releasesApiUrl, releaseId)
    var page = 1
    while (true) {
        val body = fetchJson(
            url = pagedReleaseUrl(source, assetsApiUrl, page),
            openConnection = openConnection,
        ) ?: return null
        val pageAssets = parseGiteeReleaseAssets(body) ?: return null
        if (pageAssets.isEmpty()) break
        assets += pageAssets
        if (pageAssets.size < ReleasePageSize) break
        page += 1
    }
    return assets
}

internal fun findReleaseApkAsset(
    assets: List<ReleaseAsset>,
    fileName: String,
): ReleaseAsset? =
    assets.firstOrNull { asset -> asset.name.equals(fileName, ignoreCase = true) }

internal fun updateApkFileName(
    projectName: String,
    version: String,
    channel: AppUpdateChannel,
): String =
    when (channel) {
        AppUpdateChannel.Formal -> "$projectName-$version-release.apk"
        AppUpdateChannel.Beta -> "$projectName-$version-debug.apk"
    }

internal fun isNewerSemanticVersion(latestVersion: String, currentVersion: String): Boolean {
    val latest = parseAppVersion(latestVersion) ?: return false
    val current = parseAppVersion(currentVersion) ?: return false
    return latest > current
}

internal fun normalizedSemanticVersion(version: String): String? =
    parseAppVersion(version)?.normalized

internal fun parseGiteeReleases(json: String): List<GiteeRelease>? =
    parseReleases(json, PixelDoneGiteeReleasePageUrl)

internal fun parseGitHubReleases(json: String): List<AppRelease>? =
    parseReleases(json, PixelDoneGitHubReleasePageUrl)

internal fun parseGiteeReleaseAssets(json: String): List<ReleaseAsset>? =
    extractTopLevelArrayObjects(json)?.map { assetJson ->
        parseReleaseAsset(assetJson) ?: return null
    }

private fun parseReleases(
    json: String,
    fallbackReleasePageUrl: String,
): List<AppRelease>? =
    extractTopLevelArrayObjects(json)?.map { releaseJson ->
        parseRelease(releaseJson, fallbackReleasePageUrl) ?: return null
    }

private fun sortedUpdateCandidates(
    releases: List<GiteeRelease>,
    projectName: String,
    currentVersion: String,
    channel: AppUpdateChannel,
): List<UpdateCandidate> {
    val current = parseAppVersion(currentVersion) ?: return emptyList()
    return releases.mapNotNull { release ->
        val version = releaseVersionForChannel(release, channel) ?: return@mapNotNull null
        if (version <= current) return@mapNotNull null
        UpdateCandidate(
            release = release,
            version = version,
            fileName = updateApkFileName(
                projectName = projectName,
                version = version.normalized,
                channel = channel,
            ),
        )
    }.sortedByDescending { candidate -> candidate.version }
}

private fun releaseVersionForChannel(
    release: GiteeRelease,
    channel: AppUpdateChannel,
): AppVersion? =
    when (channel) {
        AppUpdateChannel.Formal -> {
            if (release.prerelease) null else parseStableReleaseTag(release.tagName)
        }
        AppUpdateChannel.Beta -> {
            if (!release.prerelease) null else parseRcReleaseTag(release.tagName)
        }
    }

private suspend fun fetchJson(
    url: String,
    openConnection: (URL) -> HttpURLConnection,
): String? =
    withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = openConnection(URL(url)).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", PixelDoneProjectName)
            }
            if (connection.responseCode !in 200..299) return@withContext null
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

private fun pagedReleaseUrl(source: AppUpdateSource, baseUrl: String, page: Int): String {
    val separator = if (baseUrl.contains("?")) "&" else "?"
    val parameters = when (source) {
        AppUpdateSource.GitHub -> "per_page=$ReleasePageSize&page=$page"
        AppUpdateSource.Gitee -> "direction=desc&per_page=$ReleasePageSize&page=$page"
    }
    return "$baseUrl$separator$parameters"
}

private fun releasePageUrl(source: AppUpdateSource): String {
    return when (source) {
        AppUpdateSource.GitHub -> PixelDoneGitHubReleasePageUrl
        AppUpdateSource.Gitee -> PixelDoneGiteeReleasePageUrl
    }
}

private fun giteeReleaseAssetsApiUrl(releasesApiUrl: String, releaseId: Long): String {
    val repoApiUrl = releasesApiUrl.substringBeforeLast("/releases")
    return "$repoApiUrl/releases/$releaseId/attach_files"
}

private val AppVersionRegex =
    Regex("^v?(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?:-rc\\.(\\d+))?$", RegexOption.IGNORE_CASE)
private val StableReleaseTagRegex =
    Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)$", RegexOption.IGNORE_CASE)
private val RcReleaseTagRegex =
    Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)-rc\\.(\\d+)$", RegexOption.IGNORE_CASE)

private fun parseAppVersion(version: String): AppVersion? {
    val values = AppVersionRegex.matchEntire(version.trim())?.groupValues ?: return null
    return AppVersion(
        major = values[1].toIntOrNull() ?: return null,
        minor = values.getOrNull(2)?.takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0,
        patch = values.getOrNull(3)?.takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0,
        rc = values.getOrNull(4)?.takeIf { it.isNotEmpty() }?.toIntOrNull(),
    )
}

private fun parseStableReleaseTag(tagName: String): AppVersion? {
    val values = StableReleaseTagRegex.matchEntire(tagName.trim())?.groupValues ?: return null
    return AppVersion(
        major = values[1].toIntOrNull() ?: return null,
        minor = values[2].toIntOrNull() ?: return null,
        patch = values[3].toIntOrNull() ?: return null,
    )
}

private fun parseRcReleaseTag(tagName: String): AppVersion? {
    val values = RcReleaseTagRegex.matchEntire(tagName.trim())?.groupValues ?: return null
    return AppVersion(
        major = values[1].toIntOrNull() ?: return null,
        minor = values[2].toIntOrNull() ?: return null,
        patch = values[3].toIntOrNull() ?: return null,
        rc = values[4].toIntOrNull() ?: return null,
    )
}

private fun parseRelease(json: String, fallbackReleasePageUrl: String): AppRelease? {
    val id = extractTopLevelJsonLong(json, "id") ?: return null
    val tagName = extractTopLevelJsonString(json, "tag_name") ?: return null
    val htmlUrl = extractTopLevelJsonString(json, "html_url")
        ?: "$fallbackReleasePageUrl/tag/$tagName"
    val prerelease = extractTopLevelJsonBoolean(json, "prerelease") ?: false
    val assets = extractTopLevelJsonArray(json, "assets")
        ?.let(::extractJsonObjects)
        ?.mapNotNull(::parseReleaseAsset)
        ?: emptyList()

    return AppRelease(
        id = id,
        tagName = tagName,
        htmlUrl = htmlUrl,
        prerelease = prerelease,
        assets = assets,
    )
}

private fun parseReleaseAsset(json: String): ReleaseAsset? {
    val name = extractTopLevelJsonString(json, "name") ?: return null
    val url = extractTopLevelJsonString(json, "browser_download_url") ?: return null
    return ReleaseAsset(name = name, downloadUrl = url)
}

private fun extractTopLevelArrayObjects(json: String): List<String>? {
    val trimmed = json.trim()
    if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return null
    return extractJsonObjects(trimmed.substring(1, trimmed.lastIndex))
}

private fun extractTopLevelJsonString(json: String, key: String): String? {
    val valueStart = findTopLevelJsonValueStart(json, key) ?: return null
    if (json.getOrNull(valueStart) != '"') return null
    return readJsonStringToken(json, valueStart)?.value
}

private fun extractTopLevelJsonLong(json: String, key: String): Long? {
    val valueStart = findTopLevelJsonValueStart(json, key) ?: return null
    val valueEnd = json.indexOfFirstFrom(valueStart) { char ->
        char == ',' || char == '}' || char.isWhitespace()
    }.takeIf { it >= 0 } ?: json.length
    return json.substring(valueStart, valueEnd).trim().toLongOrNull()
}

private fun extractTopLevelJsonBoolean(json: String, key: String): Boolean? {
    val valueStart = findTopLevelJsonValueStart(json, key) ?: return null
    return when {
        json.startsWith("true", valueStart) -> true
        json.startsWith("false", valueStart) -> false
        else -> null
    }
}

private fun extractTopLevelJsonArray(json: String, key: String): String? {
    val startIndex = findTopLevelJsonValueStart(json, key) ?: return null
    if (json.getOrNull(startIndex) != '[') return null

    var depth = 0
    var inString = false
    var escaped = false
    for (index in startIndex until json.length) {
        val char = json[index]
        if (inString) {
            when {
                escaped -> escaped = false
                char == '\\' -> escaped = true
                char == '"' -> inString = false
            }
            continue
        }
        when (char) {
            '"' -> inString = true
            '[' -> depth += 1
            ']' -> {
                depth -= 1
                if (depth == 0) return json.substring(startIndex + 1, index)
            }
        }
    }
    return null
}

private fun findTopLevelJsonValueStart(json: String, key: String): Int? {
    var depth = 0
    var inString = false
    var escaped = false
    var index = 0
    while (index < json.length) {
        val char = json[index]
        if (inString) {
            when {
                escaped -> escaped = false
                char == '\\' -> escaped = true
                char == '"' -> inString = false
            }
            index += 1
            continue
        }

        when (char) {
            '"' -> {
                if (depth == 1) {
                    val token = readJsonStringToken(json, index)
                    if (token != null) {
                        val colonIndex = json.skipWhitespace(token.endIndex)
                        if (json.getOrNull(colonIndex) == ':' && token.value == key) {
                            return json.skipWhitespace(colonIndex + 1)
                        }
                        index = token.endIndex
                        continue
                    }
                }
                inString = true
            }
            '{', '[' -> depth += 1
            '}', ']' -> depth -= 1
        }
        index += 1
    }
    return null
}

private data class JsonStringToken(
    val value: String,
    val endIndex: Int,
)

private fun readJsonStringToken(json: String, startQuote: Int): JsonStringToken? {
    if (json.getOrNull(startQuote) != '"') return null
    val raw = StringBuilder()
    var index = startQuote + 1
    var escaped = false
    while (index < json.length) {
        val char = json[index]
        when {
            escaped -> {
                raw.append('\\')
                raw.append(char)
                escaped = false
            }
            char == '\\' -> escaped = true
            char == '"' -> {
                return JsonStringToken(
                    value = unescapeJsonString(raw.toString()),
                    endIndex = index + 1,
                )
            }
            else -> raw.append(char)
        }
        index += 1
    }
    return null
}

private fun extractJsonObjects(jsonArray: String): List<String> {
    val objects = mutableListOf<String>()
    var startIndex = -1
    var depth = 0
    var inString = false
    var escaped = false

    for (index in jsonArray.indices) {
        val char = jsonArray[index]
        if (inString) {
            when {
                escaped -> escaped = false
                char == '\\' -> escaped = true
                char == '"' -> inString = false
            }
            continue
        }
        when (char) {
            '"' -> inString = true
            '{' -> {
                if (depth == 0) startIndex = index
                depth += 1
            }
            '}' -> {
                depth -= 1
                if (depth == 0 && startIndex >= 0) {
                    objects += jsonArray.substring(startIndex, index + 1)
                    startIndex = -1
                }
            }
        }
    }
    return objects
}

private fun unescapeJsonString(value: String): String {
    val builder = StringBuilder()
    var index = 0
    while (index < value.length) {
        val char = value[index]
        if (char != '\\' || index == value.lastIndex) {
            builder.append(char)
            index += 1
            continue
        }

        val escaped = value[index + 1]
        when (escaped) {
            '"', '\\', '/' -> builder.append(escaped)
            'b' -> builder.append('\b')
            'f' -> builder.append('\u000C')
            'n' -> builder.append('\n')
            'r' -> builder.append('\r')
            't' -> builder.append('\t')
            'u' -> {
                val hex = value.substringOrNull(index + 2, index + 6)
                val codePoint = hex?.toIntOrNull(16)
                if (codePoint != null) {
                    builder.append(codePoint.toChar())
                    index += 4
                }
            }
            else -> builder.append(escaped)
        }
        index += 2
    }
    return builder.toString()
}

private inline fun String.indexOfFirstFrom(
    startIndex: Int,
    predicate: (Char) -> Boolean,
): Int {
    for (index in startIndex until length) {
        if (predicate(this[index])) return index
    }
    return -1
}

private fun String.skipWhitespace(startIndex: Int): Int {
    var index = startIndex
    while (index < length && this[index].isWhitespace()) {
        index += 1
    }
    return index
}

private fun String.substringOrNull(startIndex: Int, endIndex: Int): String? =
    if (startIndex >= 0 && endIndex <= length && startIndex <= endIndex) {
        substring(startIndex, endIndex)
    } else {
        null
    }
