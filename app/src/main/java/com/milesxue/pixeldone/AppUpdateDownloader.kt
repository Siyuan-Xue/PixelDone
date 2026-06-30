package com.milesxue.pixeldone

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

internal sealed interface AppUpdateDownloadResult {
    data class Started(val download: AppUpdateDownload) : AppUpdateDownloadResult
    data object Failed : AppUpdateDownloadResult
}

internal enum class AppUpdateDownloadCompletion {
    Success,
    Failed,
}

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

    suspend fun awaitCompletion(download: AppUpdateDownload): AppUpdateDownloadCompletion =
        withContext(Dispatchers.IO) {
            var elapsedMillis = 0L
            while (elapsedMillis <= UpdateDownloadMaxWaitMillis) {
                when (downloadStatus(download.downloadId)) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        val file = downloadedApkFile(download.fileName)
                        return@withContext if (file.isFile && file.length() > 0L) {
                            AppUpdateDownloadCompletion.Success
                        } else {
                            AppUpdateDownloadCompletion.Failed
                        }
                    }
                    DownloadManager.STATUS_FAILED -> {
                        return@withContext AppUpdateDownloadCompletion.Failed
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
            AppUpdateDownloadCompletion.Failed
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

    private fun downloadStatus(downloadId: Long): Int? {
        val query = DownloadManager.Query().setFilterById(downloadId)
        return try {
            downloadManager.query(query)?.use { cursor ->
                if (!cursor.moveToFirst()) return null
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                if (statusIndex < 0) return null
                cursor.getInt(statusIndex)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun downloadedApkFile(fileName: String): File =
        File(appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
}
