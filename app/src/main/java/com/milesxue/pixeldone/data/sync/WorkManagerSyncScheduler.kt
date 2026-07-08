package com.milesxue.pixeldone.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.milesxue.pixeldone.di.pixelDoneAppContainer
import com.milesxue.pixeldone.domain.sync.SyncCoordinatorStatus
import java.util.concurrent.TimeUnit

internal class WorkManagerSyncScheduler(context: Context) : SyncWorkScheduler {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)

    override fun requestSync() {
        val request = OneTimeWorkRequestBuilder<PixelDoneSyncWorker>()
            .setConstraints(SyncConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, OneTimeBackoffMinutes, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniqueWork(OneTimeSyncWorkName, ExistingWorkPolicy.REPLACE, request)
    }

    override fun ensurePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<PixelDoneSyncWorker>(PeriodicSyncMinutes, TimeUnit.MINUTES)
            .setConstraints(SyncConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, PeriodicBackoffMinutes, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(PeriodicSyncWorkName, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private companion object {
        const val OneTimeSyncWorkName = "PixelDoneCloudSyncNow"
        const val PeriodicSyncWorkName = "PixelDoneCloudSyncPeriodic"
        const val OneTimeBackoffMinutes = 1L
        const val PeriodicBackoffMinutes = 5L
        const val PeriodicSyncMinutes = 30L
        val SyncConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}

internal class PixelDoneSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val coordinator = applicationContext.pixelDoneAppContainer().syncCoordinator
        return when (coordinator.syncNow()) {
            SyncCoordinatorStatus.SYNCED,
            SyncCoordinatorStatus.CONFLICT,
            SyncCoordinatorStatus.LOCAL_ONLY,
            SyncCoordinatorStatus.NOT_CONFIGURED,
            SyncCoordinatorStatus.SIGNED_OUT,
            SyncCoordinatorStatus.IDLE -> Result.success()
            SyncCoordinatorStatus.SYNCING -> Result.retry()
            SyncCoordinatorStatus.ERROR -> Result.retry()
        }
    }
}
