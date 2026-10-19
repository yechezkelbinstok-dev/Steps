package app.wakesteps.core

import java.time.Duration
import java.time.Instant
import kotlin.math.abs

/** Sound sleep measured from heart rate: how much there was, and how long it took to first reach it. */
data class SoundSleep(val total: Duration, val timeTo: Duration?)

/**
 * What counts as sound: a minute of light, deep or REM sleep whose heart rate is no higher than
 * the day's resting rate plus [lowOffset] (and, with [capAtMiddle], than the night's own middle
 * sleeping rate plus [lowOffset]), steady within [steadyBpm] across five minutes, in a stretch
 * lasting [minStretch] minutes or more.
 */
data class SoundRules(
    val lowOffset: Double = 0.0,
    val steadyBpm: Double = 4.0,
    val minStretch: Int = 5,
    val capAtMiddle: Boolean = true,
) {
    companion object {
        val DEFAULT = SoundRules()
    }
}

/**
 * Sound sleep the way Google describes it: the stretches of light, deep or REM sleep with a low,
 * steady heart rate, and time to sound sleep as the time from going to bed to the first of them.
 * Google doesn't publish its thresholds, so the app learns them ([tune]) from nights where it has
 * Google's own figures, starting from [SoundRules.DEFAULT].
 */
object SoundSleeps {

    /** Minutes either side of each minute when judging how steady it was. */
    private const val AROUND = 2

    private val SLEEPING = setOf(SleepStage.LIGHT, SleepStage.DEEP, SleepStage.REM)

    /** A night's heart rate minute by minute, and which minutes were asleep, ready to judge by any rules. */
    class NightHeart internal constructor(
        internal val bpm: Array<Double?>,
        internal val sleeping: BooleanArray,
        internal val resting: Double?,
        internal val middle: Double?,
    )

    /**
     * From each minute's average [heart] rate and the day's [resting] rate (if Google has one). Null
     * for a night without stages, or with heart rate for less than half of it (the band was off).
     */
    fun prepare(night: SleepNight, heart: List<Pair<Instant, Double>>, resting: Double?): NightHeart? {
        val minutes = night.inBed.toMinutes().toInt()
        if (minutes <= 0 || !night.staged) return null
        val bpm = arrayOfNulls<Double>(minutes)
        for ((at, rate) in heart) {
            val m = Math.floorDiv(Duration.between(night.start, at).seconds, 60L).toInt()
            if (m in 0 until minutes) bpm[m] = rate
        }
        if (bpm.count { it != null } * 2 < minutes) return null
        val sleeping = BooleanArray(minutes) { m -> night.stageAt(night.start.plusSeconds(m * 60L + 30)) in SLEEPING }
        val rates = (0 until minutes).filter { sleeping[it] }.mapNotNull { bpm[it] }.sorted()
        return NightHeart(bpm, sleeping, resting, rates.getOrNull(rates.size / 2))
    }

    fun measure(night: NightHeart, rules: SoundRules = SoundRules.DEFAULT): SoundSleep {
        val middle = night.middle ?: return SoundSleep(Duration.ZERO, null)
        val bpm = night.bpm
        val minutes = bpm.size
        val base = when {
            night.resting == null -> middle
            rules.capAtMiddle -> minOf(night.resting, middle)
            else -> night.resting
        }
        val low = base + rules.lowOffset
        val calm = BooleanArray(minutes) { m ->
            val rate = bpm[m]
            if (!night.sleeping[m] || rate == null || rate > low) return@BooleanArray false
            var lo = Double.MAX_VALUE
            var hi = -Double.MAX_VALUE
            var seen = 0
            for (k in maxOf(0, m - AROUND)..minOf(minutes - 1, m + AROUND)) {
                val r = bpm[k] ?: continue
                lo = minOf(lo, r)
                hi = maxOf(hi, r)
                seen++
            }
            seen >= 3 && hi - lo <= rules.steadyBpm
        }
        var total = 0
        var first: Int? = null
        var run = 0
        for (m in 0..minutes) {
            if (m < minutes && calm[m]) {
                run++
                continue
            }
            if (run >= rules.minStretch) {
                total += run
                if (first == null) first = m - run
            }
            run = 0
        }
        return SoundSleep(Duration.ofMinutes(total.toLong()), first?.let { Duration.ofMinutes(it.toLong()) })
    }

    fun of(night: SleepNight, heart: List<Pair<Instant, Double>>, resting: Double?, rules: SoundRules = SoundRules.DEFAULT): SoundSleep? =
        prepare(night, heart, resting)?.let { measure(it, rules) }

    /**
     * The rules that come closest to Google's own sound sleep (and, half as much, its time to sound
     * sleep) on nights where both are known. With fewer than three such nights, the defaults.
     */
    fun tune(examples: List<Pair<NightHeart, GoogleFigures>>): SoundRules {
        if (examples.size < 3) return SoundRules.DEFAULT
        fun error(rules: SoundRules): Double = examples.sumOf { (heart, google) ->
            val ours = measure(heart, rules)
            val toSound = ours.timeTo?.toMinutes() ?: heart.bpm.size.toLong()
            abs(ours.total.toMinutes() - google.sound.toMinutes()).toDouble() +
                0.5 * abs(toSound - google.timeToSound.toMinutes())
        }
        val candidates = sequence {
            for (offset in listOf(-6.0, -4.0, -3.0, -2.0, -1.0, 0.0, 1.0, 2.0, 3.0, 4.0, 6.0, 8.0))
                for (steady in listOf(2.0, 3.0, 4.0, 5.0, 6.0, 8.0, 12.0))
                    for (stretch in listOf(1, 3, 5, 10))
                        for (cap in listOf(true, false)) yield(SoundRules(offset, steady, stretch, cap))
        }
        // The best fit; on a tie, whichever is nearest the defaults.
        return candidates.minWith(compareBy<SoundRules> { error(it) }.thenBy { distance(it) })
    }

    private fun distance(r: SoundRules): Double =
        abs(r.lowOffset) + abs(r.steadyBpm - 4.0) + abs(r.minStretch - 5) + if (r.capAtMiddle) 0.0 else 1.0
}
