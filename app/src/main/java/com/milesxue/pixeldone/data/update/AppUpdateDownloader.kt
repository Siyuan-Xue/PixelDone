package com.milesxue.pixeldone.data.update

import android.app.DownloadManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal const val UpdateApkMimeType = "application/vnd.android.package-archive"
private const val UpdateDownloadPollMillis = 750L
private const val UpdateDownloadMaxWaitMillis = 10 * 60 * 1_000L
private const val UpdateDownloadStalledWaitMillis = 30 * 1_000L
private const val UpdatePreferencesName = "pixel_done_update_downloads"
private const val ActiveDownloadVersionKey = "active_download_version"
private const val ActiveDownloadIdKey = "active_download_id"
private const val ActiveDownloadFileNameKey = "active_download_file_name"
private const val ActiveDownloadSourceKey = "active_download_source"
private const val ActiveDownloadUrlKey = "active_download_url"
private val PixelDoneUpdateApkRegex =
    Regex(
        "^${Regex.escape(PixelDoneProjectName)}-" +
            "(?:(\\d+(?:\\.\\d+){0,2})-release|" +
            "(\\d+(?:\\.\\d+){0,2}(?:-rc\\.\\d+)?)-debug)\\.apk$",
        RegexOption.IGNORE_CASE,
    )

internal fun updateReleaseApkFileName(version: String): String =
    updateApkFileName(PixelDoneProjectName, version, AppUpdateChannel.Formal)

internal fun updateReleaseApkVersion(fileName: String): String? =
    updateApkVersion(fileName)

internal fun isPixelDoneReleaseApkFileName(fileName: String): Boolean =
    isPixelDoneUpdateApkFileName(fileName)

internal fun updateApkVersion(fileName: String): String? =
    PixelDoneUpdateApkRegex.matchEntire(fileName)?.groupValues
        ?.let { values ->
            values.getOrNull(1)?.takeIf { it.isNotEmpty() }
                ?: values.getOrNull(2)?.takeIf { it.isNotEmpty() }
        }

internal fun isPixelDoneUpdateApkFileName(fileName: String): Boolean =
    updateApkVersion(fileName) != null

internal fun staleUpdateReleaseApkFileNames(
    fileNames: Iterable<String>,
    latestFileName: String?,
): List<String> =
    staleUpdateApkFileNames(fileNames, latestFileName)

internal fun staleUpdateApkFileNames(
    fileNames: Iterable<String>,
    latestFileName: String?,
): List<String> {
    return fileNames.filter { fileName ->
        isPixelDoneUpdateApkFileName(fileName) &&
            !fileName.equals(latestFileName, ignoreCase = true)
    }
}

internal fun shouldCleanInstalledUpdate(
    currentVersion: String,
    downloadedVersion: String,
): Boolean {
    return normalizedSemanticVersion(currentVersion) != null &&
        normalizedSemanticVersion(downloadedVersion) != null &&
        !isNewerSemanticVersion(downloadedVersion, currentVersion)
}

internal fun activeUpdateDownloadMatchesLatest(
    download: AppUpdateDownload?,
    latestVersion: String,
    latestFileName: String,
    latestSource: AppUpdateSource,
    latestUrl: String,
): Boolean {
    return download?.version == latestVersion &&
        download.fileName.equals(latestFileName, ignoreCase = true) &&
        download.source == latestSource &&
        download.url == latestUrl
}

internal fun isUpdateDownloadStalled(
    elapsedMillis: Long,
    lastProgressMillis: Long,
    stalledWaitMillis: Long = UpdateDownloadStalledWaitMillis,
): Boolean =
    elapsedMillis - lastProgressMillis >= stalledWaitMillis

internal data class AppUpdateDownloadRequest(
    val version: String,
    val fileName: String,
    val source: AppUpdateSource,
    val url: String,
)

internal fun appUpdateDownloadRequests(info: AppUpdateInfo): List<AppUpdateDownloadRequest> =
    info.downloadSources.map { source ->
        AppUpdateDownloadRequest(
            version = info.version,
            fileName = info.fileName,
            source = source.source,
            url = source.url,
        )
    }

internal data class AppUpdateDownload(
    val version: String,
    val downloadId: Long,
    val fileName: String,
    val source: AppUpdateSource,
    val url: String,
)

internal data class AppUpdateDownloadProgress(
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long? = null,
) {
    val percent: Int?
        get() = totalBytes
            ?.takeIf { it > 0L }
            ?.let { total ->
                ((bytesDownloaded.coerceAtLeast(0L) * 100L) / total)
                    .coerceIn(0L, 100L)
                    .toInt()
            }
}

internal sealed interface AppUpdateDownloadResult {
    data class Started(val download: AppUpdateDownload) : AppUpdateDownloadResult
    data object Failed : AppUpdateDownloadResult
}

internal enum class AppUpdateDownloadCompletion {
    Success,
    Failed,
}

private data class AppUpdateDownloadSnapshot(
    val status: Int?,
    val progress: AppUpdateDownloadProgress,
)

internal class AppUpdateDownloader(context: Context) {
    private val appContext = context.applicationContext
    private val downloadManager = appContext.getSystemService(DownloadManager::class.java)
    private val preferences: SharedPreferences =
        appContext.getSharedPreferences(UpdatePreferencesName, Context.MODE_PRIVATE)

    fun enqueue(request: AppUpdateDownloadRequest): AppUpdateDownloadResult {
        return try {
            val fileName = request.fileName
            cleanupStaleUpdateApkFiles(latestFileName = fileName)
            reusableActiveDownload(request)?.let { return AppUpdateDownloadResult.Started(it) }
            downloadedApkFile(fileName).delete()
            val downloadRequest = DownloadManager.Request(Uri.parse(request.url))
                .setTitle("${PixelDoneProjectName} ${request.version}")
                .setDescription("Downloading ${PixelDoneProjectName} update")
                .setMimeType(UpdateApkMimeType)
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
                )
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
                .setDestinationInExternalFilesDir(
                    appContext,
                    Environment.DIRECTORY_DOWNLOADS,
                    fileName,
                )
            val downloadId = downloadManager.enqueue(downloadRequest)
            val download = AppUpdateDownload(
                version = request.version,
                downloadId = downloadId,
                fileName = fileName,
                source = request.source,
                url = request.url,
            )
            rememberActiveDownload(download)
            AppUpdateDownloadResult.Started(download)
        } catch (_: Exception) {
            AppUpdateDownloadResult.Failed
        }
    }

    suspend fun awaitCompletion(
        download: AppUpdateDownload,
        onProgress: (AppUpdateDownloadProgress) -> Unit = {},
    ): AppUpdateDownloadCompletion {
        var elapsedMillis = 0L
        var lastProgressMillis = 0L
        var lastBytesDownloaded = 0L
        while (elapsedMillis <= UpdateDownloadMaxWaitMillis) {
            val snapshot = withContext(Dispatchers.IO) {
                downloadSnapshot(download.downloadId)
            }
            snapshot?.progress?.let(onProgress)
            val bytesDownloaded = snapshot?.progress?.bytesDownloaded ?: 0L
            if (bytesDownloaded > lastBytesDownloaded) {
                lastBytesDownloaded = bytesDownloaded
                lastProgressMillis = elapsedMillis
            }
            when (snapshot?.status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    return withContext(Dispatchers.IO) {
                        val file = downloadedApkFile(download.fileName)
                        if (file.isFile && file.length() > 0L) {
                            AppUpdateDownloadCompletion.Success
                        } else {
                            AppUpdateDownloadCompletion.Failed
                        }
                    }
                }
                DownloadManager.STATUS_FAILED -> {
                    forgetAndDelete(download)
                    return AppUpdateDownloadCompletion.Failed
                }
                DownloadManager.STATUS_PENDING,
                DownloadManager.STATUS_RUNNING,
                DownloadManager.STATUS_PAUSED,
                null,
                -> Unit
            }
            if (isUpdateDownloadStalled(elapsedMillis, lastProgressMillis)) {
                forgetAndDelete(download)
                return AppUpdateDownloadCompletion.Failed
            }
            delay(UpdateDownloadPollMillis)
            elapsedMillis += UpdateDownloadPollMillis
        }
        forgetAndDelete(download)
        return AppUpdateDownloadCompletion.Failed
    }

    fun cleanupInstalledUpdate(currentVersion: String): Boolean {
        var cleaned = false
        val activeDownload = activeDownload()
        if (
            activeDownload != null &&
            shouldCleanInstalledUpdate(
                currentVersion = currentVersion,
                downloadedVersion = activeDownload.version,
            )
        ) {
            cancelDownload(activeDownload)
            forgetActiveDownload(activeDownload)
            cleaned = downloadedApkFile(activeDownload.fileName).delete() || cleaned
        }
        cleaned = cleanupInstalledUpdateApkFiles(currentVersion) || cleaned
        return cleaned
    }

    fun openInstallPrompt(download: AppUpdateDownload): Boolean {
        return try {
            val file = downloadedApkFile(download.fileName)
            if (!file.isFile || file.length() <= 0L) return false
            val uri = FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, UpdateApkMimeType)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.clipData = ClipData.newUri(appContext.contentResolver, download.fileName, uri)
            appContext.startActivity(intent)
            true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }
    }

    private fun downloadSnapshot(downloadId: Long): AppUpdateDownloadSnapshot? {
        val query = DownloadManager.Query().setFilterById(downloadId)
        return try {
            downloadManager.query(query)?.use { cursor ->
                if (!cursor.moveToFirst()) return null
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                AppUpdateDownloadSnapshot(
                    status = if (statusIndex >= 0) cursor.getInt(statusIndex) else null,
                    progress = AppUpdateDownloadProgress(
                        bytesDownloaded = cursor
                            .longColumn(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                            .coerceAtLeast(0L),
                        totalBytes = cursor
                            .longColumn(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                            .takeIf { it > 0L },
                    ),
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun reusableActiveDownload(request: AppUpdateDownloadRequest): AppUpdateDownload? {
        val activeDownload = activeDownload()
        if (
            !activeUpdateDownloadMatchesLatest(
                download = activeDownload,
                latestVersion = request.version,
                latestFileName = request.fileName,
                latestSource = request.source,
                latestUrl = request.url,
            )
        ) {
            activeDownload?.let { download ->
                cancelDownload(download)
                forgetActiveDownload(download)
                downloadedApkFile(download.fileName).delete()
            }
            return null
        }

        val active = activeDownload ?: return null
        val status = downloadSnapshot(active.downloadId)?.status
        val targetFile = downloadedApkFile(active.fileName)
        return when (status) {
            DownloadManager.STATUS_PENDING,
            DownloadManager.STATUS_RUNNING,
            DownloadManager.STATUS_PAUSED,
            -> active
            DownloadManager.STATUS_SUCCESSFUL -> {
                if (targetFile.isFile && targetFile.length() > 0L) {
                    active
                } else {
                    forgetActiveDownload(active)
                    targetFile.delete()
                    null
                }
            }
            DownloadManager.STATUS_FAILED,
            null,
            -> {
                cancelDownload(active)
                forgetActiveDownload(active)
                targetFile.delete()
                null
            }
            else -> null
        }
    }

    private fun activeDownload(): AppUpdateDownload? {
        val version = preferences.getString(ActiveDownloadVersionKey, null)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val fileName = preferences.getString(ActiveDownloadFileNameKey, null)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val source = preferences.getString(ActiveDownloadSourceKey, null)
            ?.let { value ->
                runCatching { AppUpdateSource.valueOf(value) }.getOrNull()
            }
            ?: return null
        val url = preferences.getString(ActiveDownloadUrlKey, null)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val downloadId = preferences.getLong(ActiveDownloadIdKey, -1L)
            .takeIf { it >= 0L }
            ?: return null
        return AppUpdateDownload(
            version = version,
            downloadId = downloadId,
            fileName = fileName,
            source = source,
            url = url,
        )
    }

    private fun rememberActiveDownload(download: AppUpdateDownload) {
        preferences.edit()
            .putString(ActiveDownloadVersionKey, download.version)
            .putLong(ActiveDownloadIdKey, download.downloadId)
            .putString(ActiveDownloadFileNameKey, download.fileName)
            .putString(ActiveDownloadSourceKey, download.source.name)
            .putString(ActiveDownloadUrlKey, download.url)
            .apply()
    }

    private fun forgetActiveDownload(download: AppUpdateDownload? = null) {
        val active = activeDownload()
        if (download != null && active != null && active.downloadId != download.downloadId) return
        preferences.edit()
            .remove(ActiveDownloadVersionKey)
            .remove(ActiveDownloadIdKey)
            .remove(ActiveDownloadFileNameKey)
            .remove(ActiveDownloadSourceKey)
            .remove(ActiveDownloadUrlKey)
            .apply()
    }

    private fun forgetAndDelete(download: AppUpdateDownload) {
        cancelDownload(download)
        forgetActiveDownload(download)
        downloadedApkFile(download.fileName).delete()
    }

    private fun cancelDownload(download: AppUpdateDownload) {
        try {
            downloadManager.remove(download.downloadId)
        } catch (_: Exception) {
            Unit
        }
    }

    private fun cleanupStaleUpdateApkFiles(latestFileName: String): Boolean {
        val files = updateDownloadDir().listFiles()
            ?.filter { file -> file.isFile }
            ?: return false
        val staleFileNames = staleUpdateApkFileNames(
            fileNames = files.map { file -> file.name },
            latestFileName = latestFileName,
        ).toSet()
        return files
            .filter { file -> file.name in staleFileNames }
            .fold(false) { cleaned, file -> file.delete() || cleaned }
    }

    private fun cleanupInstalledUpdateApkFiles(currentVersion: String): Boolean {
        return updateDownloadDir()
            .listFiles()
            ?.filter { file -> file.isFile }
            ?.filter { file ->
                val version = updateApkVersion(file.name) ?: return@filter false
                shouldCleanInstalledUpdate(
                    currentVersion = currentVersion,
                    downloadedVersion = version,
                )
            }
            ?.fold(false) { cleaned, file -> file.delete() || cleaned }
            ?: false
    }

    private fun updateDownloadDir(): File =
        appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(appContext.filesDir, "updates")

    private fun downloadedApkFile(fileName: String): File =
        File(updateDownloadDir(), fileName)
}

private fun android.database.Cursor.longColumn(name: String): Long {
    val index = getColumnIndex(name)
    return if (index >= 0) getLong(index) else 0L
}
