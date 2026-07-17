package com.milesxue.pixeldone.data.update

import android.Manifest
import android.app.DownloadManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.os.Environment
import com.milesxue.pixeldone.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal const val UpdateApkMimeType = "application/vnd.android.package-archive"
private const val UpdateDownloadPollMillis = 750L
private const val UpdateDownloadMaxWaitMillis = 30 * 60 * 1_000L
private const val UpdateDownloadStalledWaitMillis = 90 * 1_000L
private const val UpdatePreferencesName = "pixel_done_update_downloads"
private const val ActiveDownloadVersionKey = "active_download_version"
private const val ActiveDownloadIdKey = "active_download_id"
private const val ActiveDownloadFileNameKey = "active_download_file_name"
private const val ActiveDownloadSourceKey = "active_download_source"
private const val ActiveDownloadUrlKey = "active_download_url"
private const val ActiveDownloadChecksumUrlKey = "active_download_checksum_url"
private const val UpdateInstallStatusKey = "update_install_status"
// The session requires user action, so consume its first callback and avoid cold-starting PixelDone
// again for the final result after Android has replaced the package.
internal const val UpdateInstallStatusPendingIntentFlags =
    PendingIntent.FLAG_UPDATE_CURRENT or
        PendingIntent.FLAG_MUTABLE or
        PendingIntent.FLAG_ONE_SHOT

internal fun updateInstallStatusPendingIntent(
    context: Context,
    sessionId: Int,
): PendingIntent {
    val appContext = context.applicationContext
    val callbackIntent = Intent(appContext, UpdateInstallStatusReceiver::class.java).apply {
        action = UpdateInstallStatusReceiver.ACTION_INSTALL_STATUS
        putExtra(UpdateInstallStatusReceiver.EXTRA_INSTALL_SESSION_ID, sessionId)
    }
    return PendingIntent.getBroadcast(
        appContext,
        sessionId,
        callbackIntent,
        UpdateInstallStatusPendingIntentFlags,
    )
}

private val PixelDoneUpdateApkRegex =
    Regex(
        "^${Regex.escape(PixelDoneProjectName)}-" +
            "(?:(\\d+(?:\\.\\d+){0,2})-release|" +
            "(\\d+(?:\\.\\d+){0,2}(?:-rc\\.\\d+)?)-debug)\\.apk$",
        RegexOption.IGNORE_CASE,
    )

internal fun recordUpdateInstallStatus(context: Context, status: AppUpdateInstallStatus) {
    context.applicationContext
        .getSharedPreferences(UpdatePreferencesName, Context.MODE_PRIVATE)
        .edit()
        .putString(UpdateInstallStatusKey, status.name)
        .apply()
}

internal fun consumeUpdateInstallStatus(context: Context): AppUpdateInstallStatus? {
    val preferences = context.applicationContext
        .getSharedPreferences(UpdatePreferencesName, Context.MODE_PRIVATE)
    val status = preferences.getString(UpdateInstallStatusKey, null)
        ?.let { value -> runCatching { AppUpdateInstallStatus.valueOf(value) }.getOrNull() }
    if (status != null) preferences.edit().remove(UpdateInstallStatusKey).apply()
    return status
}

internal fun clearUpdateInstallStatus(context: Context) {
    context.applicationContext
        .getSharedPreferences(UpdatePreferencesName, Context.MODE_PRIVATE)
        .edit()
        .remove(UpdateInstallStatusKey)
        .apply()
}

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
    val checksumUrl: String = "$url.sha256",
)

internal fun appUpdateDownloadRequests(info: AppUpdateInfo): List<AppUpdateDownloadRequest> =
    info.downloadSources.map { source ->
        AppUpdateDownloadRequest(
            version = info.version,
            fileName = info.fileName,
            source = source.source,
            url = source.url,
            checksumUrl = source.checksumUrl,
        )
    }

internal data class AppUpdateDownload(
    val version: String,
    val downloadId: Long,
    val fileName: String,
    val source: AppUpdateSource,
    val url: String,
    val checksumUrl: String = "$url.sha256",
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

internal enum class AppUpdateInstallStartResult {
    Requested,
    Failed,
}

internal enum class AppUpdateVerificationResult {
    Verified,
    Failed,
}

internal enum class AppUpdateInstallStatus {
    Prompted,
    Failed,
}

internal fun parseUpdateChecksum(text: String, expectedFileName: String): String? {
    val match = Regex("^([0-9a-fA-F]{64})\\s+(.+)$")
        .matchEntire(text.trim())
        ?: return null
    if (match.groupValues[2].trim() != expectedFileName) return null
    return match.groupValues[1].uppercase()
}

internal fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02X".format(byte) }

internal fun fullScreenIntentPermissionStateForUpdate(
    sdkInt: Int,
    currentlyGranted: Boolean,
): Int? {
    if (sdkInt < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
    return if (currentlyGranted) {
        PackageInstaller.SessionParams.PERMISSION_STATE_GRANTED
    } else {
        PackageInstaller.SessionParams.PERMISSION_STATE_DENIED
    }
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
                checksumUrl = request.checksumUrl,
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

    suspend fun requestInstall(download: AppUpdateDownload): AppUpdateInstallStartResult {
        return requestInstall(download) { }
    }

    suspend fun verifyDownloadedUpdate(download: AppUpdateDownload): AppUpdateVerificationResult =
        withContext(Dispatchers.IO) {
            val file = downloadedApkFile(download.fileName)
            val verified = try {
                verifyDownloadedFile(download, file)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                false
            }
            if (verified) {
                AppUpdateVerificationResult.Verified
            } else {
                forgetAndDelete(download)
                AppUpdateVerificationResult.Failed
            }
        }

    private fun verifyDownloadedFile(download: AppUpdateDownload, file: File): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        val checksumText = fetchChecksum(download.checksumUrl) ?: return false
        val expectedHash = parseUpdateChecksum(checksumText, download.fileName) ?: return false
        val actualHash = file.inputStream().buffered().use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
            digest.digest().joinToString("") { byte -> "%02X".format(byte) }
        }
        return actualHash == expectedHash && verifyArchiveIdentity(file, download.version)
    }

    suspend fun requestInstall(
        download: AppUpdateDownload,
        onProgress: (AppUpdateDownloadProgress) -> Unit,
    ): AppUpdateInstallStartResult {
        val fullScreenIntentGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            appContext.getSystemService(NotificationManager::class.java)
                ?.canUseFullScreenIntent() == true
        } else {
            true
        }
        return withContext(Dispatchers.IO) {
            val file = downloadedApkFile(download.fileName)
            if (!file.isFile || file.length() <= 0L) {
                return@withContext AppUpdateInstallStartResult.Failed
            }

            val packageInstaller = appContext.packageManager.packageInstaller
            var sessionId: Int? = null
            var committed = false
            try {
                val params = PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL,
                ).apply {
                    setAppPackageName(appContext.packageName)
                    setSize(file.length())
                    setInstallReason(PackageManager.INSTALL_REASON_USER)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        setPackageSource(PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        val state = checkNotNull(
                            fullScreenIntentPermissionStateForUpdate(
                                sdkInt = Build.VERSION.SDK_INT,
                                currentlyGranted = fullScreenIntentGranted,
                            ),
                        )
                        setPermissionState(Manifest.permission.USE_FULL_SCREEN_INTENT, state)
                    }
                }
                sessionId = packageInstaller.createSession(params)
                packageInstaller.openSession(sessionId).use { session ->
                    file.inputStream().buffered().use { input ->
                        session.openWrite("base.apk", 0L, file.length()).use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var copied = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                copied += read
                                onProgress(
                                    AppUpdateDownloadProgress(
                                        bytesDownloaded = copied,
                                        totalBytes = file.length(),
                                    ),
                                )
                            }
                            session.fsync(output)
                        }
                    }
                    val callback = updateInstallStatusPendingIntent(appContext, sessionId)
                    session.commit(callback.intentSender)
                    committed = true
                }
                AppUpdateInstallStartResult.Requested
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                AppUpdateInstallStartResult.Failed
            } finally {
                if (!committed) {
                    sessionId?.let { id -> runCatching { packageInstaller.abandonSession(id) } }
                }
            }
        }
    }

    fun consumeInstallStatus(): AppUpdateInstallStatus? =
        consumeUpdateInstallStatus(appContext)

    private fun fetchChecksum(url: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Accept", "text/plain")
                setRequestProperty("User-Agent", PixelDoneProjectName)
            }
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    @Suppress("DEPRECATION")
    private fun verifyArchiveIdentity(file: File, expectedVersion: String): Boolean {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val archive = appContext.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: return false
        if (archive.packageName != appContext.packageName) return false
        if (archive.versionName != expectedVersion) return false
        val archiveVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archive.longVersionCode
        } else {
            archive.versionCode.toLong()
        }
        if (archiveVersionCode <= BuildConfig.VERSION_CODE.toLong()) return false

        val installed = appContext.packageManager.getPackageInfo(appContext.packageName, flags)
        val archiveHashes = packageSignatures(archive).map(::signatureSha256).toSet()
        val installedHashes = packageSignatures(installed).map(::signatureSha256).toSet()
        if (archiveHashes.isEmpty() || archiveHashes.intersect(installedHashes).isEmpty()) return false
        val expectedSigner = BuildConfig.EXPECTED_UPDATE_SIGNER_SHA256.trim().uppercase()
        return expectedSigner.isEmpty() ||
            (expectedSigner in archiveHashes && expectedSigner in installedHashes)
    }

    @Suppress("DEPRECATION")
    private fun packageSignatures(info: android.content.pm.PackageInfo): List<Signature> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo ?: return emptyList()
            val signers = if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
            signers?.toList().orEmpty()
        } else {
            info.signatures?.toList().orEmpty()
        }
    }

    private fun signatureSha256(signature: Signature): String =
        sha256Hex(signature.toByteArray())

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
        val checksumUrl = preferences.getString(ActiveDownloadChecksumUrlKey, null)
            ?.takeIf { it.isNotBlank() }
            ?: "$url.sha256"
        val downloadId = preferences.getLong(ActiveDownloadIdKey, -1L)
            .takeIf { it >= 0L }
            ?: return null
        return AppUpdateDownload(
            version = version,
            downloadId = downloadId,
            fileName = fileName,
            source = source,
            url = url,
            checksumUrl = checksumUrl,
        )
    }

    private fun rememberActiveDownload(download: AppUpdateDownload) {
        preferences.edit()
            .putString(ActiveDownloadVersionKey, download.version)
            .putLong(ActiveDownloadIdKey, download.downloadId)
            .putString(ActiveDownloadFileNameKey, download.fileName)
            .putString(ActiveDownloadSourceKey, download.source.name)
            .putString(ActiveDownloadUrlKey, download.url)
            .putString(ActiveDownloadChecksumUrlKey, download.checksumUrl)
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
            .remove(ActiveDownloadChecksumUrlKey)
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
