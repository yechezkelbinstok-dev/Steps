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
import app.wakesteps.core.SoundSleep
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

private val Context.dataStore by preferencesDataStore(name = "wakesteps")

enum class CalorieMode { TOTAL, ACTIVE }
enum class DistanceUnit { AUTO, KILOMETERS, MILES }

/** How the goal ring fills up to the goal. Past it, it's gold in every style. */
enum class WidgetStyle {
    /** The wallpaper's bright tone, even. */
    SOFT,
    /** The wallpaper's bright tone, faint at the start and fading in toward the tip. */
    FADE,
    /** Aqua to blue, with coral calories and sky-blue distance. */
    VIVID,
    /** Steel to bright platinum: silver until the goal, then gold. */
    PLATINUM,
    /** Deep violet to orchid. */
    AMETHYST,
    /** Raspberry to blush. */
    ROSE,
    /** Emerald to soft mint. */
    EMERALD,
}

/**
 * Only [goal], [widgetStyle], [googleHealth] and [restlessAsAwake] are chosen in the app. Everything else is
 * tuned in code (the user asks for changes rather than fiddling with settings), so those values are never read
 * from storage.
 */
data class AppSettings(
    val goal: Int = 10_000,
    val widgetStyle: WidgetStyle = WidgetStyle.SOFT,
    /** Read activity and sleep from Google Health (falling back to Health Connect when it can't). */
    val googleHealth: Boolean = false,
    /** Count restless moments as awake, rather than as their own category (which counts as asleep, like Google). */
    val restlessAsAwake: Boolean = false,
    /** Sleep mostly during the user's night counts (starts a new day) from this long. */
    val minNightSleepMinutes: Int = 60,
    /** Sleep mostly during the day is a nap unless it's at least this long. */
    val minDaySleepMinutes: Int = 300,
    val fallbackMinuteOfDay: Int = 5 * 60,
    /** Package whose data to use, or null for all sources (Health Connect's priority order). */
    val sourcePackage: String? = null,
    /** Refresh the widget as soon as the screen turns on, then watch for new data while it's on. */
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

/** A night's sound sleep as worked out from heart rate; [settled] once all its heart rate has surely arrived. */
data class KeptSound(val sound: SoundSleep, val settled: Boolean)

/** A sleep score read off the Google Health app for the night ending on [date], and the app's estimate for it. */
data class ScoreEntry(val date: LocalDate, val score: Int, val estimate: Int?)

/**
 * The scores the user read off the Google Health app and sent over (Oct 19, 2026), so the
 * estimates start out calibrated. Each is added once; anything entered in the app wins.
 */
private val SEED_SCORES = mapOf(
    LocalDate.of(2026, 10, 18) to 71,
    LocalDate.of(2026, 10, 15) to 52,
    LocalDate.of(2026, 10, 14) to 89,
    LocalDate.of(2026, 10, 13) to 47,
    // The second batch.
    LocalDate.of(2026, 10, 12) to 61,
    LocalDate.of(2026, 10, 11) to 60,
    LocalDate.of(2026, 10, 9) to 85,
    LocalDate.of(2026, 9, 18) to 75,
    LocalDate.of(2026, 9, 17) to 83,
    LocalDate.of(2026, 9, 16) to 84,
    LocalDate.of(2026, 9, 15) to 66,
    LocalDate.of(2026, 9, 14) to 77,
)

/** The first batch, which 1.12 marked as added with a single flag. */
private val FIRST_SEEDS = SEED_SCORES.keys.take(4).toSet()

/**
 * [entries] with each sent-over score not added before, and the dates added so far; null when
 * there's nothing new. A score already entered for a date stays. [added] is null until dates were
 * tracked, when [firstBatchAdded] says whether 1.12 added the first batch.
 */
internal fun withSeedScores(
    entries: Map<LocalDate, ScoreEntry>,
    added: Set<LocalDate>?,
    firstBatchAdded: Boolean,
): Pair<Map<LocalDate, ScoreEntry>, Set<LocalDate>>? {
    val done = added ?: if (firstBatchAdded) FIRST_SEEDS else emptySet()
    val fresh = SEED_SCORES.filterKeys { it !in done }
    if (fresh.isEmpty()) return null
    val out = entries.toMutableMap()
    for ((date, score) in fresh) if (date !in out) out[date] = ScoreEntry(date, score, null)
    return out to done + fresh.keys
}

/** New tracker data was found: seen at [seenAt], covering steps up to [dataUpTo]. */
data class SyncEvent(val seenAt: Instant, val dataUpTo: Instant, val stepsDelta: Long)

private const val SYNC_LOG_SIZE = 60

class Prefs(private val context: Context) {
    private object K {
        val goal = intPreferencesKey("goal")
        val widgetStyle = stringPreferencesKey("widget_style")
        val googleHealth = booleanPreferencesKey("google_health")
        val restlessAsAwake = booleanPreferencesKey("restless_as_awake")
        val googleHealthError = stringPreferencesKey("google_health_error")
        /** The user said no to the phone's step counter, so stop offering it. */
        val liveStepsDeclined = booleanPreferencesKey("live_steps_declined")

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

        /** Newline-separated "<seenAtEpochSec>|<dataUpToEpochSec>|<stepsDelta>", newest first. */
        val syncLog = stringPreferencesKey("sync_log")

        /** Newline-separated "<wake date>|<score>|<estimate or empty>". */
        val sleepScores = stringPreferencesKey("sleep_scores")
        /** 1.12 only: the first batch of scores was added. */
        val scoresSeeded = booleanPreferencesKey("sleep_scores_seeded")
        /** Comma-separated dates whose sent-over score has been added, so each is added only once. */
        val seededScoreDates = stringPreferencesKey("sleep_scores_seeded_dates")

        /** Which way of estimating the kept estimates came from (SleepScore.MODEL). */
        val scoreModel = intPreferencesKey("sleep_score_model")

        /** Newline-separated "<night start s>|<night end s>|<sound min>|<time to sound min or empty>|<1 if settled>". */
        val soundSleeps = stringPreferencesKey("sound_sleeps")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    val snapshot: Flow<Snapshot?> = context.dataStore.data.map { it.toSnapshot() }

    val overrides: Flow<Overrides> = context.dataStore.data.map { parseOverrides(it[K.overrides].orEmpty()) }

    /** Each time new tracker data showed up in Health Connect, newest first. */
    val syncLog: Flow<List<SyncEvent>> = context.dataStore.data.map { p ->
        p[K.syncLog].orEmpty().lineSequence().mapNotNull { line ->
            val parts = line.split('|')
            val seen = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            val upTo = parts.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
            SyncEvent(Instant.ofEpochSecond(seen), Instant.ofEpochSecond(upTo), parts.getOrNull(2)?.toLongOrNull() ?: 0)
        }.toList()
    }

    suspend fun logSync(event: SyncEvent) {
        context.dataStore.edit { p ->
            val line = "${event.seenAt.epochSecond}|${event.dataUpTo.epochSecond}|${event.stepsDelta}"
            p[K.syncLog] = (listOf(line) + p[K.syncLog].orEmpty().lines().filter { it.isNotBlank() })
                .take(SYNC_LOG_SIZE)
                .joinToString("\n")
        }
    }

    suspend fun currentSettings(): AppSettings = settings.first()

    /** The sync log as it stands now (for diagnostics). */
    suspend fun syncLogNow(): List<SyncEvent> = syncLog.first()

    /** Why the last Google Health read failed, or null if it worked. */
    val googleHealthError: Flow<String?> = context.dataStore.data.map { it[K.googleHealthError] }

    /** The recorded Google Health error as it stands now (for diagnostics). */
    suspend fun googleHealthErrorNow(): String? = googleHealthError.first()

    /** Sleep scores from the Google Health app, by the date of the morning the night ended. */
    val sleepScores: Flow<Map<LocalDate, ScoreEntry>> = context.dataStore.data.map { p -> parseScores(p[K.sleepScores]) }

    /** Saves (or with a null [score], removes) the Google Health score for the night ending on [date]. */
    suspend fun setSleepScore(date: LocalDate, score: Int?, estimate: Int?) = editScores { entries ->
        if (score == null) entries.remove(date) else entries[date] = ScoreEntry(date, score, estimate)
    }

    /**
     * Keeps the app's latest estimate next to each entered score, so the learning outlives the
     * nights shown (and follows any change in how nights are estimated while they're shown).
     */
    suspend fun noteEstimates(estimates: Map<LocalDate, Int>) = editScores { entries ->
        for ((date, estimate) in estimates) entries[date]?.takeIf { it.estimate != estimate }?.let { entries[date] = it.copy(estimate = estimate) }
    }

    /** Drops the kept estimates if they came from another way of estimating ([model]), to be worked out again. */
    suspend fun forgetEstimatesUnless(model: Int) {
        context.dataStore.edit { p ->
            if (p[K.scoreModel] == model) return@edit
            p[K.sleepScores] = formatScores(parseScores(p[K.sleepScores]).mapValues { it.value.copy(estimate = null) })
            p[K.scoreModel] = model
        }
    }

    /** Adds each score the user sent over, once. */
    suspend fun seedSleepScores() {
        context.dataStore.edit { p ->
            val added = p[K.seededScoreDates]?.split(',')?.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }?.toSet()
            val (entries, dates) = withSeedScores(parseScores(p[K.sleepScores]), added, p[K.scoresSeeded] == true) ?: return@edit
            p[K.sleepScores] = formatScores(entries)
            p[K.seededScoreDates] = dates.sorted().joinToString(",")
        }
    }

    /** Sound sleep already worked out, by night (its start and end). */
    suspend fun soundSleeps(): Map<Pair<Instant, Instant>, KeptSound> = parseSounds(context.dataStore.data.first()[K.soundSleeps])

    suspend fun keepSoundSleeps(fresh: Map<Pair<Instant, Instant>, KeptSound>) {
        context.dataStore.edit { p ->
            val all = parseSounds(p[K.soundSleeps]) + fresh
            p[K.soundSleeps] = all.entries.sortedByDescending { it.key.first }.take(120).joinToString("\n") { (night, kept) ->
                val (start, end) = night
                "${start.epochSecond}|${end.epochSecond}|${kept.sound.total.toMinutes()}|${kept.sound.timeTo?.toMinutes() ?: ""}|${if (kept.settled) 1 else 0}"
            }
        }
    }

    private fun parseSounds(text: String?): Map<Pair<Instant, Instant>, KeptSound> = text.orEmpty().lineSequence().mapNotNull { line ->
        val f = line.split('|')
        val start = f.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
        val end = f.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
        val total = f.getOrNull(2)?.toLongOrNull() ?: return@mapNotNull null
        val sound = SoundSleep(Duration.ofMinutes(total), f.getOrNull(3)?.toLongOrNull()?.let(Duration::ofMinutes))
        (Instant.ofEpochSecond(start) to Instant.ofEpochSecond(end)) to KeptSound(sound, f.getOrNull(4) == "1")
    }.toMap()

    private suspend fun editScores(change: (MutableMap<LocalDate, ScoreEntry>) -> Unit) {
        context.dataStore.edit { p ->
            val entries = parseScores(p[K.sleepScores]).toMutableMap()
            change(entries)
            p[K.sleepScores] = formatScores(entries)
        }
    }

    private fun parseScores(text: String?): Map<LocalDate, ScoreEntry> = text.orEmpty().lineSequence().mapNotNull { line ->
        val parts = line.split('|')
        val date = parts.getOrNull(0)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@mapNotNull null
        val score = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
        date to ScoreEntry(date, score, parts.getOrNull(2)?.toIntOrNull())
    }.toMap()

    private fun formatScores(entries: Map<LocalDate, ScoreEntry>): String =
        entries.values.sortedByDescending { it.date }.take(400).joinToString("\n") { "${it.date}|${it.score}|${it.estimate ?: ""}" }

    suspend fun liveStepsDeclined(): Boolean = context.dataStore.data.first()[K.liveStepsDeclined] ?: false

    suspend fun declineLiveSteps() {
        context.dataStore.edit { it[K.liveStepsDeclined] = true }
    }

    suspend fun setGoogleHealthError(message: String?) {
        context.dataStore.edit { p -> if (message == null) p.remove(K.googleHealthError) else p[K.googleHealthError] = message }
    }
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
            p[K.widgetStyle] = s.widgetStyle.name
            p[K.googleHealth] = s.googleHealth
            p[K.restlessAsAwake] = s.restlessAsAwake
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

    private fun Preferences.toSettings(): AppSettings = AppSettings(
        goal = this[K.goal] ?: AppSettings().goal,
        widgetStyle = enumOr(this[K.widgetStyle], AppSettings().widgetStyle),
        googleHealth = this[K.googleHealth] ?: false,
        restlessAsAwake = this[K.restlessAsAwake] ?: false,
    )

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
