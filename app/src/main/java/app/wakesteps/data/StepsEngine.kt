package app.wakesteps.data

import android.content.Context
import androidx.glance.appwidget.updateAll
import app.wakesteps.core.DayCalculator
import app.wakesteps.core.DayWindow
import app.wakesteps.widget.StepsWidget
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

data class TodaySummary(
    val window: DayWindow,
    val totals: DayTotals,
    val hourly: List<Pair<Instant, Long>>,
    val latestDataAt: Instant?,
    val now: Instant,
)

data class DaySummary(val window: DayWindow, val totals: DayTotals)

/** Turns raw Health Connect data into awake-day totals and keeps the widget in sync. */
class StepsEngine(
    private val context: Context,
    private val prefs: Prefs,
    private val health: HealthSource,
) {
    private val refreshLock = Mutex()

    private suspend fun windows(settings: AppSettings, lookback: Duration, now: Instant): List<DayWindow> {
        val from = now.minus(lookback)
        // Read a bit further back so a sleep that started before `from` is still seen.
        val sleeps = health.sleeps(from.minus(Duration.ofDays(1)), now, settings.sourcePackage)
        return DayCalculator.windows(sleeps, from, now, ZoneId.systemDefault(), settings.rules())
    }

    suspend fun today(settings: AppSettings, includeHourly: Boolean): TodaySummary {
        val now = Instant.now()
        val granted = health.grantedPermissions()
        val window = windows(settings, Duration.ofDays(3), now).lastOrNull()
            ?: DayCalculator.current(emptyList(), now, now, ZoneId.systemDefault(), settings.rules())
        val end = window.endOr(now)
        val totals = health.totals(window.start, end, settings.sourcePackage, granted, settings.calorieMode)
        val hourly = if (includeHourly) {
            health.stepsBuckets(window.start, end, Duration.ofHours(1), settings.sourcePackage)
        } else {
            emptyList()
        }
        val latest = runCatching {
            health.latestStepTime(now.minus(Duration.ofDays(2)), now, settings.sourcePackage)
        }.getOrNull()
        return TodaySummary(window, totals, hourly, latest, now)
    }

    suspend fun history(settings: AppSettings, days: Long = 14): List<DaySummary> {
        val now = Instant.now()
        val granted = health.grantedPermissions()
        return windows(settings, Duration.ofDays(days + 1), now)
            .map {
                DaySummary(
                    it,
                    health.totals(it.start, it.endOr(now), settings.sourcePackage, granted, settings.calorieMode),
                )
            }
            .reversed()
    }

    /**
     * Recomputes today's totals, stores them for the widget and redraws every widget instance.
     * Safe to call from the app, a worker, the live service or a widget tap.
     */
    suspend fun refresh(knownSummary: TodaySummary? = null): Snapshot? = refreshLock.withLock {
        val settings = prefs.currentSettings()
        val snapshot = try {
            check(health.isAvailable) { "Health Connect isn't available" }
            check(health.hasCorePermissions()) { "Open the app to grant Health Connect access" }
            val t = knownSummary ?: today(settings, includeHourly = false)
            Snapshot(
                totals = t.totals,
                dayStart = t.window.start,
                startReason = t.window.startReason,
                asleep = !t.window.isOngoing,
                updatedAt = t.now,
                latestDataAt = t.latestDataAt,
                error = null,
            ).also { prefs.saveSnapshot(it) }
        } catch (e: Exception) {
            prefs.saveError(e.message ?: e.javaClass.simpleName)
            null
        }
        StepsWidget().updateAll(context)
        snapshot
    }
}
