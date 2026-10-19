package app.wakesteps.data

import android.content.Context
import app.wakesteps.core.DayCalculator
import app.wakesteps.core.DayWindow
import app.wakesteps.core.NightWindow
import app.wakesteps.core.SleepNight
import app.wakesteps.core.SleepNights
import app.wakesteps.core.SleepSession
import app.wakesteps.core.SleepSpan
import app.wakesteps.core.SoundSleeps
import app.wakesteps.widget.WidgetPusher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
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

/** A plain midnight-to-midnight day, for comparing with Fitbit / Google. */
data class CalendarDay(val date: LocalDate, val totals: DayTotals)

/** Days of sleep history used to learn when the user's night is. */
private const val LEARN_DAYS = 14L

/** Turns raw Health Connect data into awake-day totals and keeps the widget in sync. */
class StepsEngine(
    private val context: Context,
    private val prefs: Prefs,
    private val health: HealthSource,
    private val google: GoogleHealth,
) {
    private val refreshLock = Mutex()

    /** Whether a Google Health failure is currently on record (so success only clears it once). */
    @Volatile private var googleErrorShown = true

    /**
     * Reads through Google Health when it's switched on and signed in, otherwise (or if that
     * fails) through Health Connect. Why Google Health failed is kept for the Settings sheet.
     */
    private suspend fun <T> read(
        settings: AppSettings,
        cloud: suspend GoogleHealth.(token: String) -> T?,
        local: suspend () -> T,
    ): T {
        if (settings.googleHealth) {
            val token = google.token()
            if (token == null) {
                noteGoogleError("Connect Google Health again in Settings.")
            } else {
                try {
                    val value = google.cloud(token)
                    if (googleErrorShown) noteGoogleError(null)
                    if (value != null) return value
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    noteGoogleError(e.message ?: e.javaClass.simpleName)
                }
            }
        }
        return local()
    }

    private suspend fun noteGoogleError(message: String?) {
        googleErrorShown = message != null
        prefs.setGoogleHealthError(message)
    }

    /**
     * Recent sleep: enough history to learn when the user's night is. With Google Health on, its
     * sleep is added to Health Connect's; overlapping copies of the same night merge into one.
     */
    private suspend fun recentSleeps(settings: AppSettings, now: Instant, days: Long): List<SleepSpan> {
        val from = now.minus(Duration.ofDays(maxOf(days, LEARN_DAYS) + 1))
        val local = health.sleeps(from, now, settings.sourcePackage)
        val cloud = if (settings.googleHealth) read(settings, { sleeps(it, from, now) }, { emptyList() }) else emptyList()
        return (local + cloud).sortedBy { it.start }
    }

    suspend fun today(settings: AppSettings, includeHourly: Boolean): TodaySummary {
        val now = Instant.now()
        val granted = health.grantedPermissions()
        val zone = ZoneId.systemDefault()
        val sleeps = recentSleeps(settings, now, days = 3)
        val overrides = prefs.currentOverrides()
        val window = DayCalculator.current(sleeps, now.minus(Duration.ofDays(3)), now, zone, settings.rules(), overrides)
        val night = DayCalculator.nightWindow(sleeps, zone, settings.rules())
        val end = window.endOr(now)
        val totals = read(settings, { totals(it, window.start, end) }) {
            health.totals(window.start, end, settings.sourcePackage, granted, settings.calorieMode)
        }
        val hourly = if (includeHourly) hourly(window, settings) else emptyList()
        val latest = read(settings, { latestStepTime(it, now.minus(Duration.ofHours(6)), now) }) {
            runCatching { health.latestStepTime(now.minus(Duration.ofDays(2)), now, settings.sourcePackage) }.getOrNull()
        }
        return TodaySummary(window, night, totals, hourly, latest, now)
    }

    /** Steps hour by hour across one awake day (up to now if it's still going). */
    suspend fun hourly(window: DayWindow, settings: AppSettings): List<Pair<Instant, Long>> {
        val end = window.endOr(Instant.now())
        return read(settings, { stepsBuckets(it, window.start, end, Duration.ofHours(1)) }) {
            health.stepsBuckets(window.start, end, Duration.ofHours(1), settings.sourcePackage)
        }
    }

    /** The awake days of the last [days] days, newest first: from sleep alone, so it's quick. */
    suspend fun dayWindows(settings: AppSettings, days: Long = 14): List<DayWindow> {
        val now = Instant.now()
        val sleeps = recentSleeps(settings, now, days)
        val overrides = prefs.currentOverrides()
        return DayCalculator.windows(
            sleeps, now.minus(Duration.ofDays(days + 1)), now, ZoneId.systemDefault(), settings.rules(), overrides,
        ).reversed()
    }

    /** Each of [windows] with its totals. */
    suspend fun history(settings: AppSettings, windows: List<DayWindow>): List<DaySummary> {
        val now = Instant.now()
        val granted = health.grantedPermissions()
        return windows.map { w ->
            val end = w.endOr(now)
            DaySummary(
                w,
                read(settings, { totals(it, w.start, end) }) {
                    health.totals(w.start, end, settings.sourcePackage, granted, settings.calorieMode)
                },
            )
        }
    }

    /**
     * The night each of [days] began with: the sleep the user woke from, as the day logic decided
     * (their own calls included), stage by stage, in the same order. Days that began without one
     * (after an all-nighter) are skipped.
     */
    suspend fun nights(settings: AppSettings, days: List<DayWindow>): List<SleepNight> {
        val spans = days.mapNotNull { it.wokeFrom }.distinct()
        if (spans.isEmpty()) return emptyList()
        val sessions = sleepSessions(settings, spans.minOf { it.start }, spans.maxOf { it.end })
        return spans.map { SleepNights.build(it, sessions) }
    }

    /**
     * The main sleep (the longest) that ended on each of [dates], for learning from scores entered
     * for nights older than the ones shown. One read per date; a date with no sleep is left out.
     */
    suspend fun nightsEndingOn(settings: AppSettings, dates: Set<LocalDate>, zone: ZoneId = ZoneId.systemDefault()): Map<LocalDate, SleepNight> =
        dates.mapNotNull { date ->
            // Bedtime can be any time from the afternoon before.
            val sessions = sleepSessions(settings, date.minusDays(1).atTime(12, 0).atZone(zone).toInstant(), date.plusDays(1).atStartOfDay(zone).toInstant())
            val main = sessions.filter { it.end.atZone(zone).toLocalDate() == date }.maxByOrNull { Duration.between(it.start, it.end) }
            main?.let { date to SleepNights.build(SleepSpan(it.start, it.end), sessions) }
        }.toMap()

    /**
     * [nights] with their sound sleep measured from heart rate, where Google Health has the band's
     * heart rate and the user has allowed it. Each night is worked out once and kept, except in its
     * first half day, while its heart rate may still be arriving.
     */
    suspend fun withSoundSleep(settings: AppSettings, nights: List<SleepNight>): List<SleepNight> {
        if (!settings.googleHealth || nights.none { it.staged }) return nights
        val kept = prefs.soundSleeps()
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        var token: String? = null
        var asked = false
        var resting: Map<LocalDate, Double>? = null
        val fresh = mutableMapOf<Pair<Instant, Instant>, KeptSound>()
        val out = nights.map { night ->
            if (!night.staged) return@map night
            val key = night.start to night.end
            val known = kept[key]
            if (known?.settled == true) return@map night.copy(sound = known.sound)
            if (!asked) {
                asked = true
                token = google.heartToken()
            }
            val measured = token?.let { t ->
                try {
                    val heart = google.heartRate(t, night.start, night.end)
                    val rates = resting ?: runCatching { google.restingHeartRates(t) }.getOrDefault(emptyMap()).also { resting = it }
                    val day = night.end.atZone(zone).toLocalDate()
                    SoundSleeps.of(night, heart, rates[day] ?: rates[day.minusDays(1)])
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            }
            if (measured == null) return@map known?.let { night.copy(sound = it.sound) } ?: night
            fresh[key] = KeptSound(measured, settled = night.end.isBefore(now.minus(Duration.ofHours(12))))
            night.copy(sound = measured)
        }
        if (fresh.isNotEmpty()) prefs.keepSoundSleeps(fresh)
        return out
    }

    /** Whether heart rate can be read: Google Health connected and heart rate allowed. */
    suspend fun heartAllowed(settings: AppSettings): Boolean = settings.googleHealth && google.heartToken() != null

    /**
     * Sleep sessions overlapping [from]..[to]. Google Health has the band's stages; without it (or
     * if it has nothing), Health Connect.
     */
    private suspend fun sleepSessions(settings: AppSettings, from: Instant, to: Instant): List<SleepSession> {
        val cloud: List<SleepSession> = if (settings.googleHealth) {
            // Google finds sleep by when it ended: anything overlapping ends after [from], and within a day of [to].
            read(settings, { sleepSessions(it, from, to.plus(Duration.ofDays(1))) }) { emptyList() }.filter { it.start < to }
        } else emptyList()
        return cloud.ifEmpty {
            try {
                health.sleepSessions(from, to, settings.sourcePackage)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    suspend fun calendarDays(settings: AppSettings, days: Long = 14): List<CalendarDay> {
        val now = LocalDateTime.now()
        return health.calendarDays(
            now.toLocalDate().minusDays(days - 1), now, settings.sourcePackage,
            health.grantedPermissions(), settings.calorieMode,
        ).map { (date, totals) -> CalendarDay(date, totals) }
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
            val previous = prefs.currentSnapshot()
            val newest = t.latestDataAt
            if (newest != null && (previous?.latestDataAt == null || newest > previous.latestDataAt)) {
                val delta = if (previous != null && previous.dayStart == t.window.start) t.totals.steps - previous.totals.steps else 0
                prefs.logSync(SyncEvent(seenAt = t.now, dataUpTo = newest, stepsDelta = delta))
            }
            Snapshot(
                totals = t.totals,
                dayStart = t.window.start,
                startReason = t.window.startReason,
                asleep = !t.window.isOngoing,
                updatedAt = t.now,
                latestDataAt = t.latestDataAt,
                error = null,
            ).also { prefs.saveSnapshot(it) }
        } catch (e: CancellationException) {
            throw e // e.g. the screen went off mid-refresh: not an error to show
        } catch (e: Exception) {
            prefs.saveError(e.message ?: e.javaClass.simpleName)
            null
        }
        WidgetPusher.pushAll(context)
        snapshot
    }
}
