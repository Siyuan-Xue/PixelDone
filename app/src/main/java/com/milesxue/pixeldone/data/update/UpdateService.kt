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
    private val checkCache: AppUpdateCheckCache,
) {
    suspend fun check(
        currentVersion: String,
        forceRefresh: Boolean = false,
    ): AppUpdateCheckResult {
        if (!forceRefresh) checkCache.load(currentVersion)?.let { return it }
        return checkPixelDoneUpdate(
            currentVersion = currentVersion,
            channel = channel,
        ).also { result -> checkCache.save(currentVersion, result) }
    }

    fun enqueue(request: AppUpdateDownloadRequest): AppUpdateDownloadResult =
        downloader.enqueue(request)

    suspend fun awaitCompletion(
        download: AppUpdateDownload,
        onProgress: (AppUpdateDownloadProgress) -> Unit = {},
    ): AppUpdateDownloadCompletion =
        downloader.awaitCompletion(download, onProgress)

    suspend fun verifyDownloadedUpdate(download: AppUpdateDownload): AppUpdateVerificationResult =
        downloader.verifyDownloadedUpdate(download)

    suspend fun requestInstall(
        download: AppUpdateDownload,
        onProgress: (AppUpdateDownloadProgress) -> Unit = {},
    ): AppUpdateInstallStartResult =
        downloader.requestInstall(download, onProgress)

    fun consumeInstallStatus(): AppUpdateInstallStatus? = downloader.consumeInstallStatus()

    fun cleanupInstalledUpdate(currentVersion: String): Boolean =
        downloader.cleanupInstalledUpdate(currentVersion)
}
