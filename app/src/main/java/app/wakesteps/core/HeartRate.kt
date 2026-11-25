package app.wakesteps.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.math.floor
import kotlin.math.roundToInt

/** One window of heart rate (a minute, or a few seconds): when it began, and its average, lowest and highest. */
data class HeartPoint(val at: Instant, val avg: Float, val min: Float, val max: Float)

/** Google's heart-rate zones, lowest first. */
enum class HeartZone { LIGHT, MODERATE, VIGOROUS, PEAK }

/**
 * Where each of Google's heart-rate zones begins, in beats a minute. Google sets them from heart
 * rate reserve, the room between maximum and resting heart rate: moderate from 40% of it,
 * vigorous from 60%, peak from 85%. Below moderate is light.
 */
data class HeartZones(val moderate: Int, val vigorous: Int, val peak: Int) {

    fun of(bpm: Float): HeartZone = when {
        bpm >= peak -> HeartZone.PEAK
        bpm >= vigorous -> HeartZone.VIGOROUS
        bpm >= moderate -> HeartZone.MODERATE
        else -> HeartZone.LIGHT
    }

    companion object {
        /**
         * The user's maximum heart rate as Google has it (220 less their age). Their zones on
         * Google's heart rate page on Nov 23, 2026 (moderate from 123, vigorous from 148, peak
         * from 180) come from 200 with a resting rate of 72.
         */
        const val MAX = 200

        /** The zones for a resting rate of [resting]. */
        fun of(resting: Double, max: Int = MAX): HeartZones {
            val reserve = max - resting
            fun from(share: Double) = floor(resting + share * reserve).toInt()
            return HeartZones(from(0.40), from(0.60), from(0.85))
        }

        /** As Google had them on Nov 23, 2026: for a day without a resting rate to go on. */
        val USUAL = of(72.0)
    }
}

/**
 * A calendar day's heart rate, as Google's heart rate page shows it: midnight to midnight, each
 * minute's average, lowest and highest ([points], oldest first, minutes without any left out),
 * with the day's resting rate (if Google has one yet) and the zones from it.
 */
data class HeartDay(
    val date: LocalDate,
    val start: Instant,
    val end: Instant,
    val points: List<HeartPoint>,
    val resting: Double?,
    val zones: HeartZones,
) {
    /** How many minutes long the day is (1,440, or 1,380 and 1,500 when the clocks change). */
    val minutes: Int get() = Duration.between(start, end).toMinutes().toInt().coerceAtLeast(1)

    /** The lowest and highest heart rate of the day, if it has any. */
    val range: Pair<Int, Int>? get() = heartRange(points, start, end)
}

/**
 * The lowest and highest heart rate among the [points] from [from] up to [to], if there were any:
 * those that began then, and those that began earlier but whose [window] (how long each point
 * stands for) ran into it.
 */
fun heartRange(points: List<HeartPoint>, from: Instant, to: Instant, window: Duration = Duration.ZERO): Pair<Int, Int>? {
    var lo = Float.MAX_VALUE
    var hi = -Float.MAX_VALUE
    for (p in points) {
        if (!p.at.isBefore(to)) continue
        if (p.at.isBefore(from) && !p.at.plus(window).isAfter(from)) continue
        lo = minOf(lo, p.min)
        hi = maxOf(hi, p.max)
    }
    return if (hi < lo) null else lo.roundToInt() to hi.roundToInt()
}

/** One of a chart's bars: [seconds] seconds from [from] seconds into the day, with the lowest and highest in them. */
data class HeartBar(val from: Int, val seconds: Int, val min: Float, val max: Float)

/**
 * [points] (oldest first) from [start] (midnight) in bars of [seconds], so lined up with the
 * clock (a 15-minute bar runs from :00, :15, :30 or :45, a 10-second one from :00, :10, :20…),
 * each with the lowest and highest in it. Bars without any heart rate are left out.
 */
fun heartBars(points: List<HeartPoint>, start: Instant, seconds: Int): List<HeartBar> {
    val out = mutableListOf<HeartBar>()
    var key = Long.MIN_VALUE
    var lo = 0f
    var hi = 0f
    for (p in points) {
        val k = Math.floorDiv(Duration.between(start, p.at).seconds, seconds.toLong())
        if (k != key) {
            if (key != Long.MIN_VALUE) out += HeartBar((key * seconds).toInt(), seconds, lo, hi)
            key = k
            lo = p.min
            hi = p.max
        } else {
            lo = minOf(lo, p.min)
            hi = maxOf(hi, p.max)
        }
    }
    if (key != Long.MIN_VALUE) out += HeartBar((key * seconds).toInt(), seconds, lo, hi)
    return out
}
