package app.wakesteps.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.wakesteps.core.DayRules
import app.wakesteps.core.StartReason
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

private val Context.dataStore by preferencesDataStore(name = "wakesteps")

data class AppSettings(
    val goal: Int = 10_000,
    val minSleepMinutes: Int = 180,
    val fallbackMinuteOfDay: Int = 5 * 60,
    /** Package whose data to use, or null for all sources (Health Connect's priority order). */
    val sourcePackage: String? = null,
    val liveMode: Boolean = false,
) {
    val fallbackTime: LocalTime get() = LocalTime.of(fallbackMinuteOfDay / 60, fallbackMinuteOfDay % 60)

    fun rules() = DayRules(
        minSleep = Duration.ofMinutes(minSleepMinutes.toLong()),
        fallbackTime = fallbackTime,
    )
}

/** Last computed result, shared between the app, the widget and the live notification. */
data class Snapshot(
    val steps: Long,
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
        val minSleep = intPreferencesKey("min_sleep_minutes")
        val fallback = intPreferencesKey("fallback_minute_of_day")
        val source = stringPreferencesKey("source_package")
        val live = booleanPreferencesKey("live_mode")

        val snapSteps = longPreferencesKey("snap_steps")
        val snapStart = longPreferencesKey("snap_start")
        val snapReason = stringPreferencesKey("snap_reason")
        val snapAsleep = booleanPreferencesKey("snap_asleep")
        val snapUpdated = longPreferencesKey("snap_updated")
        val snapLatest = longPreferencesKey("snap_latest")
        val snapError = stringPreferencesKey("snap_error")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    val snapshot: Flow<Snapshot?> = context.dataStore.data.map { it.toSnapshot() }

    suspend fun currentSettings(): AppSettings = settings.first()
    suspend fun currentSnapshot(): Snapshot? = snapshot.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { p ->
            val s = transform(p.toSettings())
            p[K.goal] = s.goal
            p[K.minSleep] = s.minSleepMinutes
            p[K.fallback] = s.fallbackMinuteOfDay
            if (s.sourcePackage == null) p.remove(K.source) else p[K.source] = s.sourcePackage
            p[K.live] = s.liveMode
        }
    }

    suspend fun saveSnapshot(s: Snapshot) {
        context.dataStore.edit { p ->
            p[K.snapSteps] = s.steps
            p[K.snapStart] = s.dayStart.toEpochMilli()
            p[K.snapReason] = s.startReason.name
            p[K.snapAsleep] = s.asleep
            p[K.snapUpdated] = s.updatedAt.toEpochMilli()
            if (s.latestDataAt == null) p.remove(K.snapLatest) else p[K.snapLatest] = s.latestDataAt.toEpochMilli()
            if (s.error == null) p.remove(K.snapError) else p[K.snapError] = s.error
        }
    }

    /** Records a failed refresh without throwing away the last good numbers. */
    suspend fun saveError(message: String) {
        context.dataStore.edit { p -> p[K.snapError] = message }
    }

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            goal = this[K.goal] ?: d.goal,
            minSleepMinutes = this[K.minSleep] ?: d.minSleepMinutes,
            fallbackMinuteOfDay = this[K.fallback] ?: d.fallbackMinuteOfDay,
            sourcePackage = this[K.source],
            liveMode = this[K.live] ?: d.liveMode,
        )
    }

    private fun Preferences.toSnapshot(): Snapshot? {
        val start = this[K.snapStart] ?: return null
        return Snapshot(
            steps = this[K.snapSteps] ?: 0,
            dayStart = Instant.ofEpochMilli(start),
            startReason = runCatching { StartReason.valueOf(this[K.snapReason] ?: "") }
                .getOrDefault(StartReason.WOKE_UP),
            asleep = this[K.snapAsleep] ?: false,
            updatedAt = Instant.ofEpochMilli(this[K.snapUpdated] ?: start),
            latestDataAt = this[K.snapLatest]?.let(Instant::ofEpochMilli),
            error = this[K.snapError],
        )
    }
}
