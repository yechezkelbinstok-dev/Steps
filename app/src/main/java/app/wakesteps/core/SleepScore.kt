package app.wakesteps.core

import kotlin.math.roundToInt

/** Google's rating bands for a sleep score. */
enum class SleepRating(val from: Int) {
    EXCELLENT(90), GOOD(80), FAIR(60), POOR(0);

    companion object {
        fun of(score: Int): SleepRating = entries.first { score >= it.from }
    }
}

/** The six things Google's sleep score weighs, in the order its app lists them. */
enum class ScoreFactor { DURATION, TIME_TO_SOUND_SLEEP, SOUND_SLEEP, RESTLESSNESS, FULL_AWAKENINGS, INTERRUPTIONS }

/** How one factor went: its measure (minutes, or a count) and how well it did, from 0 (poorly) to 1 (as good as it gets). */
data class ScorePart(val factor: ScoreFactor, val minutes: Long, val count: Int, val quality: Float)

/**
 * An estimate of Google's sleep score from the same six factors its app lists: how long you slept,
 * how quickly you reached steady sleep, how much steady sleep you got, how restless you were, how
 * often you fully woke up, and how long those wake-ups lasted. Google also uses heart rate and
 * targets for your age and sex, and doesn't publish its weights, so this is an estimate;
 * [ScoreCalibration] pulls it towards the real scores the user enters.
 *
 * Like Google, there's no score for a night without stages.
 */
object SleepScore {

    private val WEIGHTS = mapOf(
        ScoreFactor.DURATION to 0.40f,
        ScoreFactor.TIME_TO_SOUND_SLEEP to 0.08f,
        ScoreFactor.SOUND_SLEEP to 0.20f,
        ScoreFactor.RESTLESSNESS to 0.12f,
        ScoreFactor.FULL_AWAKENINGS to 0.10f,
        ScoreFactor.INTERRUPTIONS to 0.10f,
    )

    fun parts(night: SleepNight): List<ScorePart>? {
        if (!night.staged) return null
        val asleep = night.asleep.toMinutes()
        val sound = night.soundSleep.toMinutes()
        val toSound = (night.timeToSoundSleep ?: night.inBed).toMinutes()
        val restless = night.time(SleepStage.RESTLESS).toMinutes()
        val awakenings = night.fullAwakenings
        val interrupted = night.interruptions.toMinutes()
        return listOf(
            // 3 hours earns nothing, 8½ hours full marks.
            ScorePart(ScoreFactor.DURATION, asleep, 0, ramp(asleep, 180, 510)),
            // Within 5 minutes is ideal; 45 or more earns nothing.
            ScorePart(ScoreFactor.TIME_TO_SOUND_SLEEP, toSound, 0, 1f - ramp(toSound, 5, 45)),
            ScorePart(ScoreFactor.SOUND_SLEEP, sound, 0, ramp(sound, 120, 480)),
            // As a share of the night: none is ideal, 15% earns nothing.
            ScorePart(ScoreFactor.RESTLESSNESS, restless, night.restlessMoments, 1f - (restless.toFloat() / asleep.coerceAtLeast(1) / 0.15f).coerceIn(0f, 1f)),
            // "Zero is ideal, but fewer than two is considered good for most people."
            ScorePart(ScoreFactor.FULL_AWAKENINGS, 0, awakenings, listOf(1f, 0.85f, 0.7f, 0.5f, 0.35f).getOrElse(awakenings) { 0.2f }),
            ScorePart(ScoreFactor.INTERRUPTIONS, interrupted, awakenings, 1f - ramp(interrupted, 0, 45)),
        )
    }

    /** The estimate, 0 to 100, before any calibration; null without stages. */
    fun estimate(night: SleepNight): Int? =
        parts(night)?.sumOf { (WEIGHTS.getValue(it.factor) * it.quality * 100).toDouble() }?.roundToInt()?.coerceIn(0, 100)

    /** 0 at [from] or below, 1 at [to] or above, straight in between. */
    private fun ramp(value: Long, from: Long, to: Long): Float = ((value - from).toFloat() / (to - from)).coerceIn(0f, 1f)
}

/**
 * Learns how the estimates compare with the real scores the user entered, and corrects for it:
 * with a few scores just an offset (the estimates run 6 high, say), from [LINE_FROM] on a full
 * line, with its slope held near 1 so a handful of nights can't bend it wildly.
 */
data class ScoreCalibration(val slope: Double, val offset: Double) {

    fun apply(estimate: Int): Int = (slope * estimate + offset).roundToInt().coerceIn(0, 100)

    companion object {
        val NONE = ScoreCalibration(1.0, 0.0)
        private const val LINE_FROM = 4

        /** Learns from the most recent (estimate, real score) pairs. */
        fun fit(pairs: List<Pair<Int, Int>>): ScoreCalibration {
            val recent = pairs.takeLast(30)
            if (recent.isEmpty()) return NONE
            val mx = recent.map { it.first.toDouble() }.average()
            val my = recent.map { it.second.toDouble() }.average()
            if (recent.size < LINE_FROM) return ScoreCalibration(1.0, my - mx)
            val sxx = recent.sumOf { (it.first - mx) * (it.first - mx) }
            val sxy = recent.sumOf { (it.first - mx) * (it.second - my) }
            // Pull the slope towards 1, more so when the estimates barely vary.
            val pull = 400.0
            val slope = ((sxy + pull) / (sxx + pull)).coerceIn(0.6, 1.6)
            return ScoreCalibration(slope, my - slope * mx)
        }
    }
}

/** A night's score as shown: the user's real one when entered, else the calibrated estimate. */
data class NightScore(val value: Int, val fromGoogle: Boolean) {
    val rating: SleepRating get() = SleepRating.of(value)
}
