package app.wakesteps.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class SleepScoreTest {

    private val bedtime = Instant.parse("2026-10-18T23:00:00Z")
    private fun at(minutes: Long): Instant = bedtime.plus(Duration.ofMinutes(minutes))
    private fun span(from: Long, to: Long, stage: SleepStage) = StageSpan(at(from), at(to), stage)

    /** [hours] in bed: 10 minutes to drift off, light/deep/REM cycles, [wakeUps] 15-minute wake-ups, [stirs] brief stirs. */
    private fun night(hours: Double, wakeUps: Int = 1, stirs: Int = 4): SleepNight {
        val end = (hours * 60).toLong()
        val stages = mutableListOf(span(0, 10, SleepStage.AWAKE))
        var t = 10L
        val cycle = listOf(SleepStage.LIGHT to 30L, SleepStage.DEEP to 25L, SleepStage.LIGHT to 15L, SleepStage.REM to 20L)
        val gaps = (1..wakeUps).map { end * it / (wakeUps + 1) }.toMutableList()
        while (t < end) {
            for ((stage, minutes) in cycle) {
                if (t >= end) break
                if (gaps.isNotEmpty() && t >= gaps.first()) {
                    gaps.removeAt(0)
                    stages += span(t, minOf(t + 15, end), SleepStage.AWAKE)
                    t = minOf(t + 15, end)
                }
                stages += span(t, minOf(t + minutes, end), stage)
                t = minOf(t + minutes, end)
            }
        }
        val shorts = (1..stirs).map { span(40L + it * 37, 41L + it * 37, SleepStage.AWAKE) }
        return SleepNights.build(SleepSpan(at(0), at(end)), listOf(SleepSession(at(0), at(end), stages, shorts)))
    }

    @Test
    fun googlesBands() {
        assertEquals(SleepRating.EXCELLENT, SleepRating.of(90))
        assertEquals(SleepRating.GOOD, SleepRating.of(89))
        assertEquals(SleepRating.GOOD, SleepRating.of(80))
        assertEquals(SleepRating.FAIR, SleepRating.of(79))
        assertEquals(SleepRating.FAIR, SleepRating.of(60))
        assertEquals(SleepRating.POOR, SleepRating.of(59))
    }

    @Test
    fun estimatesLandWhereGooglesWould() {
        val typical = SleepScore.estimate(night(7.5))!!
        val long = SleepScore.estimate(night(8.6, wakeUps = 0, stirs = 2))!!
        val short = SleepScore.estimate(night(5.2, wakeUps = 2, stirs = 6))!!
        assertTrue("a typical night is fair to good, got $typical", typical in 68..85)
        assertTrue("a long, unbroken night is good or better, got $long", long >= 85)
        assertTrue("a short, broken night is poor, got $short", short < 60)
        assertTrue(long > typical && typical > short)
    }

    @Test
    fun everyFactorIsThere() {
        val parts = SleepScore.parts(night(7.5))!!
        assertEquals(ScoreFactor.entries, parts.map { it.factor })
        assertTrue(parts.all { it.quality in 0f..1f })
        assertEquals(1, parts.first { it.factor == ScoreFactor.FULL_AWAKENINGS }.count)
    }

    @Test
    fun noScoreWithoutStages() {
        val plain = SleepNights.build(SleepSpan(at(0), at(420)), listOf(SleepSession(at(0), at(420))))
        assertNull(SleepScore.estimate(plain))
    }

    @Test
    fun learnsFromRealScores() {
        assertEquals(ScoreCalibration.NONE, ScoreCalibration.fit(emptyList()))
        // A couple of scores: just an offset.
        val few = ScoreCalibration.fit(listOf(80 to 74, 70 to 62))
        assertEquals(1.0, few.slope, 0.0)
        assertEquals(-7.0, few.offset, 0.001)
        assertEquals(68, few.apply(75))
        // Enough for a line: the estimates run 5 high across the board.
        val many = ScoreCalibration.fit(listOf(90 to 85, 60 to 55, 75 to 70, 50 to 45, 85 to 80))
        assertEquals(65, many.apply(70))
        // It never leaves 0 to 100.
        assertEquals(100, ScoreCalibration(1.0, 20.0).apply(95))
    }
}
