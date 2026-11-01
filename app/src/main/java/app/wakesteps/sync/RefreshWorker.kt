package app.wakesteps.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.wakesteps.app
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/** Background refresh of the widget. 15 minutes is the shortest period Android allows. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        applicationContext.app.engine.refresh()
        return Result.success()
    }

    companion object {
        private const val PERIODIC = "periodic-refresh"
        private const val ONE_SHOT = "one-shot-refresh"
        private const val AT = "refresh-at"

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** One refresh at [at] (as soon after as Android allows), replacing any set before. */
        fun refreshAt(context: Context, at: Instant, now: Instant = Instant.now()) {
            val request = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setInitialDelay(Duration.between(now, at).toMillis().coerceAtLeast(0), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(AT, ExistingWorkPolicy.REPLACE, request)
        }

        fun refreshNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(ONE_SHOT, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
