package app.wakesteps.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.wakesteps.core.DayRules
import app.wakesteps.core.Overrides
import app.wakesteps.core.SleepSpan
import app.wakesteps.core.StartReason
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

private val Context.dataStore by preferencesDataStore(name = "wakesteps")

enum class CalorieMode { TOTAL, ACTIVE }
enum class DistanceUnit { AUTO, KILOMETERS, MILES }

data class AppSettings(
    val goal: Int = 10_000,
    /** Sleep mostly during the user's night counts (starts a new day) from this long. */
    val minNightSleepMinutes: Int = 60,
    /** Sleep mostly during the day is a nap unless it's at least this long. */
    val minDaySleepMinutes: Int = 300,
    val fallbackMinuteOfDay: Int = 5 * 60,
    /** Package whose data to use, or null for all sources (Health Connect's priority order). */
    val sourcePackage: String? = null,
    /** Refresh the widget as soon as the screen turns on, then every minute while it stays on. */
    val liveMode: Boolean = true,
    val calorieMode: CalorieMode = CalorieMode.TOTAL,
    val distanceUnit: DistanceUnit = DistanceUnit.AUTO,
) {
    val fallbackTime: LocalTime get() = LocalTime.of(fallbackMinuteOfDay / 60, fallbackMinuteOfDay % 60)

    fun rules() = DayRules(
        minNightSleep = Duration.ofMinutes(minNightSleepMinutes.toLong()),
        minDaySleep = Duration.ofMinutes(minDaySleepMinutes.toLong()),
        fallbackTime = fallbackTime,
    )
}

/** Totals for one awake day. Calories/distance are null when access wasn't granted. */
data class DayTotals(
    val steps: Long,
    val calories: Double?,
    val distanceMeters: Double?,
)

/** Last computed result, shared between the app and the widget. */
data class Snapshot(
    val totals: DayTotals,
    val dayStart: Instant,
    val startReason: StartReason,
    val asleep: Boolean,
    val updatedAt: Instant,
    /** End time of the newest step record, i.e. how fresh the tracker's sync is. */
    val latestDataAt: Instant?,
    val error: String?,
)

class Prefs(private val context: Context) {
    private object K {
        val goal = intPreferencesKey("goal")
        val minNightSleep = intPreferencesKey("min_night_sleep_minutes")
        val minDaySleep = intPreferencesKey("min_day_sleep_minutes")
        val fallback = intPreferencesKey("fallback_minute_of_day")
        val source = stringPreferencesKey("source_package")
        val live = booleanPreferencesKey("live_refresh")
        val calorieMode = stringPreferencesKey("calorie_mode")
        val distanceUnit = stringPreferencesKey("distance_unit")

        val snapSteps = longPreferencesKey("snap_steps")
        val snapCalories = doublePreferencesKey("snap_calories")
        val snapDistance = doublePreferencesKey("snap_distance")
        val snapStart = longPreferencesKey("snap_start")
        val snapReason = stringPreferencesKey("snap_reason")
        val snapAsleep = booleanPreferencesKey("snap_asleep")
        val snapUpdated = longPreferencesKey("snap_updated")
        val snapLatest = longPreferencesKey("snap_latest")
        val snapError = stringPreferencesKey("snap_error")

        /** Entries: "S|<epochSec>|1" main sleep, "S|<epochSec>|0" nap, "R|<epochSec>" skipped reset,
         *  "M|<epochSec>" manual day start. */
        val overrides = stringSetPreferencesKey("overrides")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    val snapshot: Flow<Snapshot?> = context.dataStore.data.map { it.toSnapshot() }

    val overrides: Flow<Overrides> = context.dataStore.data.map { parseOverrides(it[K.overrides].orEmpty()) }

    suspend fun currentSettings(): AppSettings = settings.first()
    suspend fun currentOverrides(): Overrides = overrides.first()

    /** Rule a sleep main (true) or a nap (false), replacing any earlier call on it. */
    suspend fun setSleepCall(span: SleepSpan, main: Boolean) = editOverrides { entries ->
        entries.removeAll { it.startsWith("S|") && epoch(it) in span.start.epochSecond..span.end.epochSecond }
        val mid = span.start.plus(Duration.between(span.start, span.end).dividedBy(2))
        entries += "S|${mid.epochSecond}|${if (main) 1 else 0}"
    }

    suspend fun clearSleepCall(span: SleepSpan) = editOverrides { entries ->
        entries.removeAll { it.startsWith("S|") && epoch(it) in span.start.epochSecond..span.end.epochSecond }
    }

    suspend fun setResetSkipped(at: Instant, skipped: Boolean) = editOverrides { entries ->
        if (skipped) entries += "R|${at.epochSecond}" else entries -= "R|${at.epochSecond}"
    }

    suspend fun setManualStart(at: Instant, present: Boolean) = editOverrides { entries ->
        if (present) entries += "M|${at.epochSecond}" else entries -= "M|${at.epochSecond}"
    }

    /** Drops every call the user made between [from] and [to] (inclusive). */
    suspend fun clearOverrides(from: Instant, to: Instant) = editOverrides { entries ->
        entries.removeAll { epoch(it) in from.epochSecond..to.epochSecond }
    }

    private suspend fun editOverrides(change: (MutableSet<String>) -> Unit) {
        context.dataStore.edit { p ->
            val entries = p[K.overrides].orEmpty().toMutableSet()
            change(entries)
            // Keep a couple of months; older days aren't shown anywhere.
            val cutoff = Instant.now().minus(Duration.ofDays(60)).epochSecond
            entries.removeAll { epoch(it) < cutoff }
            p[K.overrides] = entries
        }
    }

    private fun epoch(entry: String): Long = entry.split('|').getOrNull(1)?.toLongOrNull() ?: 0

    private fun parseOverrides(entries: Set<String>): Overrides {
        val calls = mutableMapOf<Instant, Boolean>()
        val skipped = mutableSetOf<Instant>()
        val manual = mutableSetOf<Instant>()
        for (e in entries) {
            val parts = e.split('|')
            val at = parts.getOrNull(1)?.toLongOrNull()?.let(Instant::ofEpochSecond) ?: continue
            when (parts[0]) {
                "S" -> calls[at] = parts.getOrNull(2) == "1"
                "R" -> skipped += at
                "M" -> manual += at
            }
        }
        return Overrides(calls, skipped, manual)
    }
    suspend fun currentSnapshot(): Snapshot? = snapshot.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { p ->
            val s = transform(p.toSettings())
            p[K.goal] = s.goal
            p[K.minNightSleep] = s.minNightSleepMinutes
            p[K.minDaySleep] = s.minDaySleepMinutes
            p[K.fallback] = s.fallbackMinuteOfDay
            if (s.sourcePackage == null) p.remove(K.source) else p[K.source] = s.sourcePackage
            p[K.live] = s.liveMode
            p[K.calorieMode] = s.calorieMode.name
            p[K.distanceUnit] = s.distanceUnit.name
        }
    }

    suspend fun saveSnapshot(s: Snapshot) {
        context.dataStore.edit { p ->
            p[K.snapSteps] = s.totals.steps
            p.putOrRemove(K.snapCalories, s.totals.calories)
            p.putOrRemove(K.snapDistance, s.totals.distanceMeters)
            p[K.snapStart] = s.dayStart.toEpochMilli()
            p[K.snapReason] = s.startReason.name
            p[K.snapAsleep] = s.asleep
            p[K.snapUpdated] = s.updatedAt.toEpochMilli()
            p.putOrRemove(K.snapLatest, s.latestDataAt?.toEpochMilli())
            p.putOrRemove(K.snapError, s.error)
        }
    }

    /** Records a failed refresh without throwing away the last good numbers. */
    suspend fun saveError(message: String) {
        context.dataStore.edit { p -> p[K.snapError] = message }
    }

    private fun <T> androidx.datastore.preferences.core.MutablePreferences.putOrRemove(
        key: Preferences.Key<T>,
        value: T?,
    ) {
        if (value == null) remove(key) else set(key, value)
    }

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            goal = this[K.goal] ?: d.goal,
            minNightSleepMinutes = this[K.minNightSleep] ?: d.minNightSleepMinutes,
            minDaySleepMinutes = this[K.minDaySleep] ?: d.minDaySleepMinutes,
            fallbackMinuteOfDay = this[K.fallback] ?: d.fallbackMinuteOfDay,
            sourcePackage = this[K.source],
            liveMode = this[K.live] ?: d.liveMode,
            calorieMode = enumOr(this[K.calorieMode], d.calorieMode),
            distanceUnit = enumOr(this[K.distanceUnit], d.distanceUnit),
        )
    }

    private fun Preferences.toSnapshot(): Snapshot? {
        val start = this[K.snapStart] ?: return null
        return Snapshot(
            totals = DayTotals(
                steps = this[K.snapSteps] ?: 0,
                calories = this[K.snapCalories],
                distanceMeters = this[K.snapDistance],
            ),
            dayStart = Instant.ofEpochMilli(start),
            startReason = enumOr(this[K.snapReason], StartReason.WOKE_UP),
            asleep = this[K.snapAsleep] ?: false,
            updatedAt = Instant.ofEpochMilli(this[K.snapUpdated] ?: start),
            latestDataAt = this[K.snapLatest]?.let(Instant::ofEpochMilli),
            error = this[K.snapError],
        )
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default
}
