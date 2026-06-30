package com.milesxue.pixeldone.data.update

/**
 * 应用更新边界。
 *
 * 教学说明：检查 GitHub Release、启动 DownloadManager、打开安装界面都是“外部系统动作”。
 * UI 通过这个服务表达意图，而不是直接操作网络和下载组件。
 */
internal class UpdateService(private val downloader: AppUpdateDownloader) {
    suspend fun check(currentVersion: String): AppUpdateCheckResult =
        checkPixelDoneUpdate(currentVersion)

    fun enqueue(info: AppUpdateInfo): AppUpdateDownloadResult =
        downloader.enqueue(info)

    suspend fun awaitCompletion(download: AppUpdateDownload): AppUpdateDownloadCompletion =
        downloader.awaitCompletion(download)

    fun openInstallPrompt(download: AppUpdateDownload): Boolean =
        downloader.openInstallPrompt(download)
}
