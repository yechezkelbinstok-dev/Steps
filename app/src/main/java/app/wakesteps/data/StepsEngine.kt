package app.wakesteps.data

import android.content.Context
import androidx.glance.appwidget.updateAll
import app.wakesteps.core.DayCalculator
import app.wakesteps.core.DayWindow
import app.wakesteps.core.NightWindow
import app.wakesteps.core.SleepSpan
import app.wakesteps.widget.StepsWidget
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

data class TodaySummary(
    val window: DayWindow,
    /** When the user's night is, as learned from recent sleep. */
    val night: NightWindow,
    val totals: DayTotals,
    val hourly: List<Pair<Instant, Long>>,
    val latestDataAt: Instant?,
    val now: Instant,
)

data class DaySummary(val window: DayWindow, val totals: DayTotals)

/** Days of sleep history used to learn when the user's night is. */
private const val LEARN_DAYS = 14L

/** Turns raw Health Connect data into awake-day totals and keeps the widget in sync. */
class StepsEngine(
    private val context: Context,
    private val prefs: Prefs,
    private val health: HealthSource,
) {
    private val refreshLock = Mutex()

    /** Recent sleep: enough history to learn when the user's night is. */
    private suspend fun recentSleeps(settings: AppSettings, now: Instant, days: Long): List<SleepSpan> =
        health.sleeps(now.minus(Duration.ofDays(maxOf(days, LEARN_DAYS) + 1)), now, settings.sourcePackage)

    suspend fun today(settings: AppSettings, includeHourly: Boolean): TodaySummary {
        val now = Instant.now()
        val granted = health.grantedPermissions()
        val zone = ZoneId.systemDefault()
        val sleeps = recentSleeps(settings, now, days = 3)
        val overrides = prefs.currentOverrides()
        val window = DayCalculator.current(sleeps, now.minus(Duration.ofDays(3)), now, zone, settings.rules(), overrides)
        val night = DayCalculator.nightWindow(sleeps, zone, settings.rules())
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
        return TodaySummary(window, night, totals, hourly, latest, now)
    }

    suspend fun history(settings: AppSettings, days: Long = 14): List<DaySummary> {
        val now = Instant.now()
        val granted = health.grantedPermissions()
        val sleeps = recentSleeps(settings, now, days)
        val overrides = prefs.currentOverrides()
        return DayCalculator.windows(
            sleeps, now.minus(Duration.ofDays(days + 1)), now, ZoneId.systemDefault(), settings.rules(), overrides,
        )
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
