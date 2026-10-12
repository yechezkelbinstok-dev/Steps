package app.wakesteps.core

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** A stretch of sleep as reported by Health Connect. */
data class SleepSpan(val start: Instant, val end: Instant) {
    val duration: Duration get() = Duration.between(start, end)
}

/**
 * Rules that decide where one "awake day" ends and the next begins.
 *
 * A day starts when you wake from a main sleep and ends when you next go to sleep. Whether a
 * sleep is "main" or a nap depends on when it happens and how long you'd been up, as well as
 * how long it is:
 *  - [minDaySleep] or longer: always main sleep
 *  - [minNightSleep] or longer and mostly during your night ([NightWindow]): main sleep
 *  - [minNightSleep] or longer after being up [longAwake] or more: main sleep, whatever the
 *    time (going to bed at 6 AM after a long night is still your night's sleep)
 *  - anything else is a nap
 *
 * An all-nighter is only decided in hindsight. Passing [fallbackTime] awake marks a possible
 * all-nighter; if you then go to sleep within [fallbackGrace] it was just a late night and the
 * day runs until bedtime. Only if you're still up after the grace period does a new day start,
 * back-dated to [fallbackTime]. The fallback also needs you to have been awake at least
 * [minAwakeBeforeFallback], so waking at 4:30 isn't cut off by a 5:00 fallback.
 */
data class DayRules(
    val minNightSleep: Duration = Duration.ofHours(1),
    val minDaySleep: Duration = Duration.ofHours(5),
    /** Wall-clock time a new day is forced when no main sleep happened. */
    val fallbackTime: LocalTime = LocalTime.of(5, 0),
    /** Sleep sessions this close together are one sleep (brief wake-ups, split logs). */
    val mergeGap: Duration = Duration.ofMinutes(60),
    /** Being up this long in the middle of the night still doesn't split the night. */
    val nightMergeGap: Duration = Duration.ofHours(3),
    val minAwakeBeforeFallback: Duration = Duration.ofHours(6),
    /** Going to sleep within this long after [fallbackTime] means it wasn't an all-nighter. */
    val fallbackGrace: Duration = Duration.ofHours(6),
    /** After being up this long, any real sleep is your night's sleep, whatever the time. */
    val longAwake: Duration = Duration.ofHours(18),
)

/**
 * When the user's night is: 4 hours either side of their usual mid-sleep time, learned from
 * their recent long sleeps, plus midnight–6 AM, which always counts as night.
 */
data class NightWindow(val midpoint: LocalTime, val learned: Boolean) {
    val start: LocalTime get() = midpoint.minusMinutes(HALF_WIDTH_MIN.toLong())
    val end: LocalTime get() = midpoint.plusMinutes(HALF_WIDTH_MIN.toLong())

    fun contains(t: LocalTime): Boolean {
        val m = t.toSecondOfDay() / 60
        if (m < CORE_END_MIN) return true
        val d = abs(m - midpoint.toSecondOfDay() / 60)
        return minOf(d, DAY_MIN - d) <= HALF_WIDTH_MIN
    }

    companion object {
        private const val DAY_MIN = 24 * 60
        private const val HALF_WIDTH_MIN = 4 * 60
        private const val CORE_END_MIN = 6 * 60
        /** Until we've seen a few nights: 11 PM – 7 AM. */
        val DEFAULT = NightWindow(LocalTime.of(3, 0), learned = false)
        /** Sleeps at least this long are used to learn when the user's night is. */
        val LEARN_FROM: Duration = Duration.ofHours(4)
        const val MIN_SAMPLES = 3
    }
}

/**
 * The user's own judgement calls, which beat the automatic rules.
 *  - [sleepCalls]: a sleep containing the key instant is main sleep (true) or a nap (false).
 *    Keyed by an instant inside the sleep so the call survives small changes when the
 *    tracker re-syncs.
 *  - [skippedResets]: all-nighter fallback boundaries that should not start a new day.
 *  - [manualStarts]: times the user says a new day started (e.g. the watch wasn't worn).
 */
data class Overrides(
    val sleepCalls: Map<Instant, Boolean> = emptyMap(),
    val skippedResets: Set<Instant> = emptySet(),
    val manualStarts: Set<Instant> = emptySet(),
) {
    /** The user's call on this sleep, if they made one. */
    fun callFor(span: SleepSpan): Boolean? =
        sleepCalls.entries.firstOrNull { it.key >= span.start && it.key <= span.end }?.value

    companion object {
        val NONE = Overrides()
    }
}

enum class StartReason { WOKE_UP, FALLBACK, MANUAL }
enum class EndReason { WENT_TO_SLEEP, FALLBACK, MANUAL }

/** One awake day. [end] is null while the day is still in progress. */
data class DayWindow(
    val start: Instant,
    val end: Instant?,
    val startReason: StartReason,
    val endReason: EndReason?,
    /** The sleep you woke from to start this day, when [startReason] is [StartReason.WOKE_UP]. */
    val wokeFrom: SleepSpan? = null,
    /** Sleeps during this day that were treated as naps. */
    val naps: List<SleepSpan> = emptyList(),
) {
    val isOngoing: Boolean get() = end == null
    fun endOr(now: Instant): Instant = end ?: now
}

object DayCalculator {

    private val SAMPLE = Duration.ofMinutes(5)
    private val MANUAL_SUPPRESS = Duration.ofHours(12)

    /** Learns the user's night from the mid-points of their recent long sleeps. */
    fun nightWindow(raw: List<SleepSpan>, zone: ZoneId, rules: DayRules): NightWindow {
        val long = merge(raw) { a, b -> Duration.between(a.end, b.start) <= rules.mergeGap }
            .filter { it.duration >= NightWindow.LEARN_FROM }
        if (long.size < NightWindow.MIN_SAMPLES) return NightWindow.DEFAULT
        // Average on a 24-hour clock face so 11 PM and 1 AM average to midnight, not noon.
        var x = 0.0
        var y = 0.0
        for (s in long) {
            val mid = s.start.plus(s.duration.dividedBy(2)).atZone(zone).toLocalTime()
            val angle = mid.toSecondOfDay() / 86_400.0 * 2 * PI
            x += cos(angle)
            y += sin(angle)
        }
        val angle = atan2(y, x).let { if (it < 0) it + 2 * PI else it }
        val minutes = (angle / (2 * PI) * 1440).roundToInt() % 1440
        return NightWindow(LocalTime.of(minutes / 60, minutes % 60), learned = true)
    }

    /** The sleeps that start a new day, after merging fragments and dropping naps. */
    fun mainSleeps(
        raw: List<SleepSpan>,
        zone: ZoneId,
        rules: DayRules,
        overrides: Overrides = Overrides.NONE,
    ): List<SleepSpan> = classify(raw, zone, rules, overrides).filter { it.second }.map { it.first }

    /** Every (merged) sleep paired with whether it's main sleep (true) or a nap (false). */
    private fun classify(
        raw: List<SleepSpan>,
        zone: ZoneId,
        rules: DayRules,
        overrides: Overrides,
    ): List<Pair<SleepSpan, Boolean>> {
        val night = nightWindow(raw, zone, rules)
        val merged = merge(raw) { a, b ->
            val gap = Duration.between(a.end, b.start)
            // Never glue together two sleeps the user has ruled on differently.
            val callA = overrides.callFor(a)
            val callB = overrides.callFor(b)
            val conflicting = callA != null && callB != null && callA != callB
            !conflicting && (
                gap <= rules.mergeGap ||
                    (gap <= rules.nightMergeGap && nightFraction(a.end, b.start, zone, night) == 1.0)
                )
        }
        // Walk forward in time so each sleep knows how long you'd been up before it.
        var lastWake: Instant? = null
        return merged.map { s ->
            lastWake = maxOfNullable(lastWake, overrides.manualStarts.filter { it <= s.start }.maxOrNull())
            val upFor = lastWake?.let { Duration.between(it, s.start) }
            val auto = s.duration >= rules.minDaySleep ||
                (
                    s.duration >= rules.minNightSleep && (
                        nightFraction(s.start, s.end, zone, night) > 0.5 ||
                            (upFor != null && upFor >= rules.longAwake)
                        )
                    )
            val main = overrides.callFor(s) ?: auto
            if (main) lastWake = s.end
            s to main
        }
    }

    private fun maxOfNullable(a: Instant?, b: Instant?): Instant? = when {
        a == null -> b
        b == null -> a
        else -> maxOf(a, b)
    }

    /**
     * Splits the timeline between [from] and [now] into awake days, oldest first.
     * The last window is the current day (ongoing unless you're currently asleep).
     * Time before [from] is unknown, so the partial stretch right after [from] is only
     * counted from the first fallback boundary onward. Sleeps from before [from] are still
     * used to learn when the user's night is.
     */
    fun windows(
        raw: List<SleepSpan>,
        from: Instant,
        now: Instant,
        zone: ZoneId,
        rules: DayRules,
        overrides: Overrides = Overrides.NONE,
    ): List<DayWindow> {
        val all = classify(raw, zone, rules, overrides).filter { (s, _) -> s.end > from && s.start < now }
        val naps = all.filter { !it.second }.map { it.first }
        // Day boundaries: main sleeps, plus zero-length breaks where the user started a day.
        val breaks = (
            all.filter { it.second }.map { Break(it.first.start, it.first.end, it.first) } +
                overrides.manualStarts
                    .filter { it > from && it < now && all.none { (s, main) -> main && it > s.start && it < s.end } }
                    .map { Break(it, it, null) }
            ).sortedBy { it.start }

        val out = mutableListOf<DayWindow>()
        val ctx = SplitContext(now, zone, rules, overrides, naps, out)

        val firstBreak = breaks.firstOrNull()
        val leadStart = nextFallback(from, zone, rules)
        if (leadStart < (firstBreak?.start ?: now)) {
            ctx.split(leadStart, StartReason.FALLBACK, null, firstBreak)
        }
        breaks.forEachIndexed { i, b ->
            if (b.end >= now) return@forEachIndexed // still asleep
            val reason = if (b.sleep != null) StartReason.WOKE_UP else StartReason.MANUAL
            ctx.split(b.end, reason, b.sleep, breaks.getOrNull(i + 1))
        }
        return out
    }

    /** A boundary between days: a main sleep, or (with [sleep] null) a user-set day start. */
    private class Break(val start: Instant, val end: Instant, val sleep: SleepSpan?)

    private class SplitContext(
        val now: Instant,
        val zone: ZoneId,
        val rules: DayRules,
        val overrides: Overrides,
        val naps: List<SleepSpan>,
        val out: MutableList<DayWindow>,
    ) {
        /** One awake stretch from [start] to [next] (or now), cut at fallback boundaries. */
        fun split(start: Instant, reason: StartReason, wokeFrom: SleepSpan?, next: Break?) {
            val end = next?.start
            val endReason = next?.let { if (it.sleep != null) EndReason.WENT_TO_SLEEP else EndReason.MANUAL }
            val limit = end ?: now
            var cur = start
            var curReason = reason
            var curWoke = wokeFrom
            val manualEnd = next?.takeIf { it.sleep == null }?.start
            while (true) {
                var f = nextFallback(cur.plus(rules.minAwakeBeforeFallback), zone, rules)
                while (suppressed(f, manualEnd)) f = nextFallback(f.plusSeconds(60), zone, rules)
                // Only an all-nighter once you've stayed up through the grace period: going to
                // sleep before then (or it simply not being that late yet) means a late night.
                if (f < limit && limit > f.plus(rules.fallbackGrace)) {
                    out += DayWindow(cur, f, curReason, EndReason.FALLBACK, curWoke, napsIn(cur, f))
                    cur = f
                    curReason = StartReason.FALLBACK
                    curWoke = null
                } else {
                    out += DayWindow(cur, end, curReason, endReason, curWoke, napsIn(cur, limit))
                    return
                }
            }
        }

        private fun napsIn(a: Instant, b: Instant) = naps.filter { it.start >= a && it.start < b }

        /**
         * An all-nighter reset doesn't apply when the user skipped it, when it falls inside a
         * sleep the user called a nap (they asked for the days to merge), or shortly before a
         * day start they set by hand (they're saying when the next day really began).
         */
        private fun suppressed(f: Instant, manualEnd: Instant?): Boolean =
            f in overrides.skippedResets ||
                naps.any { overrides.callFor(it) == false && f >= it.start && f <= it.end } ||
                (manualEnd != null && f < manualEnd && f >= manualEnd.minus(MANUAL_SUPPRESS))
    }

    /** The current day, or a fallback-aligned day if nothing could be computed. */
    fun current(
        raw: List<SleepSpan>,
        from: Instant,
        now: Instant,
        zone: ZoneId,
        rules: DayRules,
        overrides: Overrides = Overrides.NONE,
    ): DayWindow = windows(raw, from, now, zone, rules, overrides).lastOrNull()
        ?: DayWindow(previousFallback(now, zone, rules), null, StartReason.FALLBACK, null)

    private fun merge(raw: List<SleepSpan>, joins: (SleepSpan, SleepSpan) -> Boolean): List<SleepSpan> {
        val merged = mutableListOf<SleepSpan>()
        for (s in raw.filter { it.end > it.start }.sortedBy { it.start }) {
            val last = merged.lastOrNull()
            if (last != null && (s.start <= last.end || joins(last, s))) {
                merged[merged.lastIndex] = SleepSpan(last.start, maxOf(last.end, s.end))
            } else {
                merged += s
            }
        }
        return merged
    }

    /** Share of the time between [a] and [b] that falls in the user's night, sampled every 5 min. */
    private fun nightFraction(a: Instant, b: Instant, zone: ZoneId, night: NightWindow): Double {
        if (!b.isAfter(a)) return 1.0
        var total = 0
        var inNight = 0
        var t = a.plus(SAMPLE.dividedBy(2))
        while (t < b) {
            total++
            if (night.contains(t.atZone(zone).toLocalTime())) inNight++
            t = t.plus(SAMPLE)
        }
        if (total == 0) return if (night.contains(a.atZone(zone).toLocalTime())) 1.0 else 0.0
        return inNight.toDouble() / total
    }

    /** First fallback boundary at or after [t]. */
    fun nextFallback(t: Instant, zone: ZoneId, rules: DayRules): Instant {
        val date = t.atZone(zone).toLocalDate()
        val today = date.atTime(rules.fallbackTime).atZone(zone).toInstant()
        return if (today >= t) today else date.plusDays(1).atTime(rules.fallbackTime).atZone(zone).toInstant()
    }

    /** Last fallback boundary at or before [t]. */
    fun previousFallback(t: Instant, zone: ZoneId, rules: DayRules): Instant {
        val date = t.atZone(zone).toLocalDate()
        val today = date.atTime(rules.fallbackTime).atZone(zone).toInstant()
        return if (today <= t) today else date.minusDays(1).atTime(rules.fallbackTime).atZone(zone).toInstant()
    }
}
