package app.wakesteps.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.wakesteps.app
import app.wakesteps.data.Locator
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Where the user is, checked in the background on the day Shabbos or Yom Tov begins, so its times
 * are right even after travelling without opening the app: in the morning, and again an hour and a
 * half before it begins. Only with location allowed all the time; otherwise the app checks when
 * it's opened.
 */
class PlaceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!Locator.allowedInBackground(context)) return Result.success()
        val engine = context.app.engine
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val today = now.atZone(zone).toLocalDate()
        val tonight = engine.restBlocks(today, today.plusDays(1))
            .firstOrNull { it.start.isAfter(now) && it.start.atZone(zone).toLocalDate() == today }
            ?: return Result.success()
        engine.notePlace()
        if (!inputData.getBoolean(LAST, false)) {
            val again = tonight.start.minus(Duration.ofMinutes(90))
            if (again.isAfter(now)) {
                val request = OneTimeWorkRequestBuilder<PlaceWorker>()
                    .setInitialDelay(Duration.between(now, again).toMinutes(), TimeUnit.MINUTES)
                    .setInputData(workDataOf(LAST to true))
                    .build()
                WorkManager.getInstance(context).enqueueUniqueWork(BEFORE, ExistingWorkPolicy.REPLACE, request)
            }
        }
        return Result.success()
    }

    companion object {
        private const val DAILY = "place-daily"
        private const val BEFORE = "place-before-rest"
        private const val LAST = "last"

        /** Every morning at about 9; it only looks on a day Shabbos or Yom Tov begins. */
        fun scheduleDaily(context: Context) {
            val now = ZonedDateTime.now()
            var next = now.withHour(9).withMinute(0).withSecond(0).withNano(0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<PlaceWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
