package com.arnit.imagex.cleanup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.arnit.imagex.di.ServiceLocator
import java.util.concurrent.TimeUnit

/**
 * Periodic scratch cleanup. The worker deletes everything older than the
 * default TTL; it only ever touches the app-private temp dir.
 */
class TempCleanupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try {
            ServiceLocator.tempFileManager(applicationContext).purgeOlderThan()
        } catch (_: Exception) {
            return Result.retry()
        }
        return Result.success()
    }

    companion object {
        private const val PERIODIC_NAME = "arnit_imagex_temp_cleanup_periodic"
        private const val ONE_SHOT_NAME = "arnit_imagex_temp_cleanup_onstart"

        fun schedule(context: Context) {
            val manager = WorkManager.getInstance(context)
            manager.enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<TempCleanupWorker>(12, TimeUnit.HOURS).build()
            )
            manager.enqueueUniqueWork(
                ONE_SHOT_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<TempCleanupWorker>()
                    .setInitialDelay(0, TimeUnit.MILLISECONDS)
                    .build()
            )
        }
    }
}
