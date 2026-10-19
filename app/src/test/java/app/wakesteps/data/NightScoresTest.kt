package app.wakesteps.data

import app.wakesteps.core.SleepNights
import app.wakesteps.core.SleepScore
import app.wakesteps.core.SleepSession
import app.wakesteps.core.SleepSpan
import app.wakesteps.core.SleepStage
import app.wakesteps.core.StageSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class NightScoresTest {

    private val zone = ZoneOffset.UTC

    /** A staged night ending at 7 AM on [day]: 7 hours of light and deep with a few minutes to drift off. */
    private fun night(day: LocalDate, deepMinutes: Long = 90) = run {
        val end = day.atTime(7, 0).toInstant(zone)
        val start = end.minus(Duration.ofHours(7))
        val stages = listOf(
            StageSpan(start, start.plusSeconds(600), SleepStage.AWAKE),
            StageSpan(start.plusSeconds(600), end.minus(Duration.ofMinutes(deepMinutes)), SleepStage.LIGHT),
            StageSpan(end.minus(Duration.ofMinutes(deepMinutes)), end, SleepStage.DEEP),
        )
        SleepNights.build(SleepSpan(start, end), listOf(SleepSession(start, end, stages)))
    }

    private val oct13 = LocalDate.of(2026, 10, 13)
    private val oct14 = LocalDate.of(2026, 10, 14)
    private val oct15 = LocalDate.of(2026, 10, 15)

    @Test
    fun anEnteredScoreIsShownAsIs() {
        val nights = listOf(night(oct14), night(oct13))
        val scores = NightScores.of(nights, mapOf(oct14 to ScoreEntry(oct14, 89, null)), zone)
        assertEquals(89, scores.getValue(oct14).value)
        assertTrue(scores.getValue(oct14).fromGoogle)
        assertFalse(scores.getValue(oct13).fromGoogle)
    }

    @Test
    fun estimatesLearnFromEnteredScores() {
        val nights = listOf(night(oct15), night(oct14))
        val raw = SleepScore.estimate(nights[0])!!
        // Google scored a similar night 10 below what the app estimated for it.
        val entries = mapOf(oct14 to ScoreEntry(oct14, SleepScore.estimate(nights[1])!! - 10, null))
        assertEquals(raw - 10, NightScores.of(nights, entries, zone).getValue(oct15).value)
        // Without any, the raw estimate.
        assertEquals(raw, NightScores.of(nights, emptyMap(), zone).getValue(oct15).value)
    }

    @Test
    fun aNightShownIsEstimatedAfreshAndAnOlderOneByItsKeptEstimate() {
        val nights = listOf(night(oct15), night(oct14))
        val raw = SleepScore.estimate(nights[0])!!
        val fresh = SleepScore.estimate(nights[1])!!
        val sep14 = LocalDate.of(2026, 9, 14)
        val entries = mapOf(
            // Kept from an older way of estimating: the fresh estimate is used instead.
            oct14 to ScoreEntry(oct14, fresh - 6, fresh + 20),
            // Not shown any more: its kept estimate is all there is.
            sep14 to ScoreEntry(sep14, 70, 76),
        )
        // Both say Google scores 6 below the estimate.
        assertEquals(raw - 6, NightScores.of(nights, entries, zone).getValue(oct15).value)
        assertNull("only nights shown get a score", NightScores.of(nights, entries, zone)[sep14])
    }

    @Test
    fun noScoreWithoutStagesUnlessEntered() {
        val end = oct15.atTime(7, 0).toInstant(zone)
        val start = end.minus(Duration.ofHours(7))
        val plain = SleepNights.build(SleepSpan(start, end), listOf(SleepSession(start, end)))
        assertNull(NightScores.of(listOf(plain), emptyMap(), zone)[oct15])
        assertEquals(60, NightScores.of(listOf(plain), mapOf(oct15 to ScoreEntry(oct15, 60, null)), zone).getValue(oct15).value)
    }
}
