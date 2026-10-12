package app.wakesteps.core

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** A stretch of sleep as reported by Health Connect. */
data class SleepSpan(val start: Instant, val end: Instant) {
    val duration: Duration get() = Duration.between(start, end)
}

/**
 * Rules that decide where one "awake day" ends and the next begins.
 *
 * A day normally starts when you wake from a main sleep and ends when you next go to sleep.
 * If you never sleep (an all-nighter), a new day is forced at [fallbackTime], but only once
 * you've been awake for at least [minAwakeBeforeFallback] so that waking at 4:30 doesn't
 * get cut off by a 5:00 fallback half an hour later.
 */
data class DayRules(
    /** Sleeps shorter than this (after merging) are naps and don't start a new day. */
    val minSleep: Duration = Duration.ofHours(3),
    /** Wall-clock time a new day is forced when no main sleep happened. */
    val fallbackTime: LocalTime = LocalTime.of(5, 0),
    /** Sleep sessions separated by less than this are treated as one night (wake-ups, split logs). */
    val mergeGap: Duration = Duration.ofMinutes(60),
    val minAwakeBeforeFallback: Duration = Duration.ofHours(6),
)

enum class StartReason { WOKE_UP, FALLBACK }
enum class EndReason { WENT_TO_SLEEP, FALLBACK }

/** One awake day. [end] is null while the day is still in progress. */
data class DayWindow(
    val start: Instant,
    val end: Instant?,
    val startReason: StartReason,
    val endReason: EndReason?,
) {
    val isOngoing: Boolean get() = end == null
    fun endOr(now: Instant): Instant = end ?: now
}

object DayCalculator {

    /** Merges fragmented sleep sessions and drops naps. */
    fun mainSleeps(raw: List<SleepSpan>, rules: DayRules): List<SleepSpan> {
        val merged = mutableListOf<SleepSpan>()
        for (s in raw.filter { it.end > it.start }.sortedBy { it.start }) {
            val last = merged.lastOrNull()
            if (last != null && Duration.between(last.end, s.start) <= rules.mergeGap) {
                merged[merged.lastIndex] = SleepSpan(last.start, maxOf(last.end, s.end))
            } else {
                merged += s
            }
        }
        return merged.filter { it.duration >= rules.minSleep }
    }

    /**
     * Splits the timeline between [from] and [now] into awake days, oldest first.
     * The last window is the current day (ongoing unless you're currently asleep).
     * Time before [from] is unknown, so the partial stretch right after [from] is only
     * counted from the first fallback boundary onward.
     */
    fun windows(
        raw: List<SleepSpan>,
        from: Instant,
        now: Instant,
        zone: ZoneId,
        rules: DayRules,
    ): List<DayWindow> {
        val sleeps = mainSleeps(raw, rules).filter { it.end > from && it.start < now }
        val out = mutableListOf<DayWindow>()

        val firstSleepStart = sleeps.firstOrNull()?.start
        val leadStart = nextFallback(from, zone, rules)
        if (leadStart < (firstSleepStart ?: now)) {
            split(leadStart, StartReason.FALLBACK, firstSleepStart, now, zone, rules, out)
        }

        sleeps.forEachIndexed { i, sleep ->
            if (sleep.end >= now) return@forEachIndexed // still asleep
            split(sleep.end, StartReason.WOKE_UP, sleeps.getOrNull(i + 1)?.start, now, zone, rules, out)
        }
        return out
    }

    /** The current day, or a fallback-aligned day if nothing could be computed. */
    fun current(
        raw: List<SleepSpan>,
        from: Instant,
        now: Instant,
        zone: ZoneId,
        rules: DayRules,
    ): DayWindow = windows(raw, from, now, zone, rules).lastOrNull()
        ?: DayWindow(previousFallback(now, zone, rules), null, StartReason.FALLBACK, null)

    private fun split(
        start: Instant,
        reason: StartReason,
        end: Instant?,
        now: Instant,
        zone: ZoneId,
        rules: DayRules,
        out: MutableList<DayWindow>,
    ) {
        val limit = end ?: now
        var cur = start
        var curReason = reason
        while (true) {
            val f = nextFallback(cur.plus(rules.minAwakeBeforeFallback), zone, rules)
            if (f < limit) {
                out += DayWindow(cur, f, curReason, EndReason.FALLBACK)
                cur = f
                curReason = StartReason.FALLBACK
            } else {
                out += DayWindow(cur, end, curReason, end?.let { EndReason.WENT_TO_SLEEP })
                return
            }
        }
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
