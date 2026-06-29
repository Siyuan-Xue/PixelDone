package com.milesxue.pixeldone

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment

internal data class PendingUpdateDownload(
    val version: String,
    val downloadId: Long,
)

internal sealed interface AppUpdateDownloadResult {
    data class Started(val download: PendingUpdateDownload) : AppUpdateDownloadResult
    data class Reused(val download: PendingUpdateDownload) : AppUpdateDownloadResult
    data object Failed : AppUpdateDownloadResult
}

internal class AppUpdateDownloader(context: Context) {
    private val appContext = context.applicationContext
    private val downloadManager = appContext.getSystemService(DownloadManager::class.java)

    fun enqueueOrReuse(
        info: AppUpdateInfo,
        pending: PendingUpdateDownload?,
    ): AppUpdateDownloadResult {
        if (pending?.version == info.version && pending.downloadId >= 0L) {
            val reusable = isReusableDownload(pending.downloadId)
            if (reusable) {
                return AppUpdateDownloadResult.Reused(pending)
            }
        }

        return try {
            val fileName = "${PixelDoneProjectName}-${info.version}-release.apk"
            val request = DownloadManager.Request(Uri.parse(info.apkDownloadUrl))
                .setTitle("${PixelDoneProjectName} ${info.version}")
                .setDescription("Downloading ${PixelDoneProjectName} update")
                .setMimeType("application/vnd.android.package-archive")
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
                PendingUpdateDownload(version = info.version, downloadId = downloadId),
            )
        } catch (_: Exception) {
            AppUpdateDownloadResult.Failed
        }
    }

    private fun isReusableDownload(downloadId: Long): Boolean {
        val query = DownloadManager.Query().setFilterById(downloadId)
        return try {
            downloadManager.query(query)?.use { cursor ->
                if (!cursor.moveToFirst()) return false
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                if (statusIndex < 0) return false
                when (cursor.getInt(statusIndex)) {
                    DownloadManager.STATUS_PENDING,
                    DownloadManager.STATUS_RUNNING,
                    DownloadManager.STATUS_PAUSED,
                    DownloadManager.STATUS_SUCCESSFUL,
                    -> true
                    else -> false
                }
            } ?: false
        } catch (_: Exception) {
            false
        }
    }
}
