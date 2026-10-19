package app.wakesteps.core

import java.time.Duration
import java.time.Instant

/** Sound sleep measured from heart rate: how much there was, and how long it took to first reach it. */
data class SoundSleep(val total: Duration, val timeTo: Duration?)

/**
 * Sound sleep the way Google describes it: the stretches of light, deep or REM sleep with a low,
 * steady heart rate, and time to sound sleep as the time from going to bed to the first of them.
 * Google doesn't publish its thresholds, so these are ours:
 *  - low: no higher than the day's resting heart rate (as Google works it out), nor than the
 *    night's own middle sleeping rate, so a night spent above resting counts as hardly sound;
 *  - steady: within [STEADY_BPM] beats a minute across five minutes;
 *  - a stretch counts once it has lasted [MIN_STRETCH] minutes.
 */
object SoundSleeps {

    private const val STEADY_BPM = 4.0
    private const val MIN_STRETCH = 5
    /** Minutes either side of each minute when judging how steady it was. */
    private const val AROUND = 2

    private val SLEEPING = setOf(SleepStage.LIGHT, SleepStage.DEEP, SleepStage.REM)

    /**
     * From each minute's average [heart] rate and the day's [resting] rate (if Google has one).
     * Null for a night without stages, or with heart rate for less than half of it (the band was off).
     */
    fun of(night: SleepNight, heart: List<Pair<Instant, Double>>, resting: Double?): SoundSleep? {
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
        if (rates.isEmpty()) return SoundSleep(Duration.ZERO, null)
        val middle = rates[rates.size / 2]
        val low = resting?.let { minOf(it, middle) } ?: middle
        val calm = BooleanArray(minutes) { m ->
            val rate = bpm[m]
            if (!sleeping[m] || rate == null || rate > low) return@BooleanArray false
            val around = (maxOf(0, m - AROUND)..minOf(minutes - 1, m + AROUND)).mapNotNull { bpm[it] }
            around.size >= 3 && around.max() - around.min() <= STEADY_BPM
        }
        var total = 0
        var first: Int? = null
        var run = 0
        for (m in 0..minutes) {
            if (m < minutes && calm[m]) {
                run++
                continue
            }
            if (run >= MIN_STRETCH) {
                total += run
                if (first == null) first = m - run
            }
            run = 0
        }
        return SoundSleep(Duration.ofMinutes(total.toLong()), first?.let { Duration.ofMinutes(it.toLong()) })
    }
}
