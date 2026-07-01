package com.milesxue.pixeldone.data.update

/**
 * 应用更新边界。
 *
 * 教学说明：检查 GitHub/Gitee Release、启动 DownloadManager、打开安装界面都是“外部系统动作”。
 * UI 通过这个服务表达意图，而不是直接操作网络和下载组件。
 */
internal class UpdateService(
    private val downloader: AppUpdateDownloader,
    private val channel: AppUpdateChannel,
) {
    suspend fun check(currentVersion: String): AppUpdateCheckResult =
        checkPixelDoneUpdate(
            currentVersion = currentVersion,
            channel = channel,
        )

    fun enqueue(request: AppUpdateDownloadRequest): AppUpdateDownloadResult =
        downloader.enqueue(request)

    suspend fun awaitCompletion(
        download: AppUpdateDownload,
        onProgress: (AppUpdateDownloadProgress) -> Unit = {},
    ): AppUpdateDownloadCompletion =
        downloader.awaitCompletion(download, onProgress)

    fun openInstallPrompt(download: AppUpdateDownload): Boolean =
        downloader.openInstallPrompt(download)

    fun cleanupInstalledUpdate(currentVersion: String): Boolean =
        downloader.cleanupInstalledUpdate(currentVersion)
}
