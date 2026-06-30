package com.milesxue.pixeldone.data.update

import android.app.DownloadManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
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

internal fun updateReleaseApkFileName(version: String): String =
    "$PixelDoneProjectName-$version-release.apk"

internal data class AppUpdateDownload(
    val version: String,
    val downloadId: Long,
    val fileName: String,
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

    fun enqueue(info: AppUpdateInfo): AppUpdateDownloadResult {
        return try {
            val fileName = updateReleaseApkFileName(info.version)
            downloadedApkFile(fileName).delete()
            val request = DownloadManager.Request(Uri.parse(info.apkDownloadUrl))
                .setTitle("${PixelDoneProjectName} ${info.version}")
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
            val downloadId = downloadManager.enqueue(request)
            AppUpdateDownloadResult.Started(
                AppUpdateDownload(
                    version = info.version,
                    downloadId = downloadId,
                    fileName = fileName,
                ),
            )
        } catch (_: Exception) {
            AppUpdateDownloadResult.Failed
        }
    }

    suspend fun awaitCompletion(
        download: AppUpdateDownload,
        onProgress: (AppUpdateDownloadProgress) -> Unit = {},
    ): AppUpdateDownloadCompletion {
        var elapsedMillis = 0L
        while (elapsedMillis <= UpdateDownloadMaxWaitMillis) {
            val snapshot = withContext(Dispatchers.IO) {
                downloadSnapshot(download.downloadId)
            }
            snapshot?.progress?.let(onProgress)
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
                    return AppUpdateDownloadCompletion.Failed
                }
                DownloadManager.STATUS_PENDING,
                DownloadManager.STATUS_RUNNING,
                DownloadManager.STATUS_PAUSED,
                null,
                -> Unit
            }
            delay(UpdateDownloadPollMillis)
            elapsedMillis += UpdateDownloadPollMillis
        }
        return AppUpdateDownloadCompletion.Failed
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

    private fun downloadedApkFile(fileName: String): File =
        File(appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
}

private fun android.database.Cursor.longColumn(name: String): Long {
    val index = getColumnIndex(name)
    return if (index >= 0) getLong(index) else 0L
}
