package app.wakesteps.data

import app.wakesteps.core.NightScore
import app.wakesteps.core.ScoreCalibration
import app.wakesteps.core.SleepNight
import app.wakesteps.core.SleepScore
import java.time.LocalDate
import java.time.ZoneId

/** The morning a night ended: how Google Health dates a night's sleep score. */
fun SleepNight.wakeDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate = end.atZone(zone).toLocalDate()

object NightScores {

    /**
     * Each night's score, by the morning it ended: the one the user entered from Google Health,
     * or else the app's estimate, corrected by how earlier estimates compared with entered scores.
     * Nights without stages and without an entered score get none, as in Google Health.
     */
    fun of(nights: List<SleepNight>, entries: Map<LocalDate, ScoreEntry>, zone: ZoneId = ZoneId.systemDefault()): Map<LocalDate, NightScore> {
        val estimates = nights.associate { it.wakeDate(zone) to SleepScore.estimate(it) }
        // A night still shown is estimated afresh; an older one by the estimate kept from when it was.
        val pairs = entries.values.sortedBy { it.date }.mapNotNull { e -> (estimates[e.date] ?: e.estimate)?.let { it to e.score } }
        val calibration = ScoreCalibration.fit(pairs)
        return nights.mapNotNull { night ->
            val date = night.wakeDate(zone)
            val score = entries[date]?.let { NightScore(it.score, fromGoogle = true) }
                ?: estimates[date]?.let { NightScore(calibration.apply(it), fromGoogle = false) }
            score?.let { date to it }
        }.toMap()
    }
}
