package app.wakesteps.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class SoundSleepTest {

    private val bedtime = Instant.parse("2026-10-18T23:00:00Z")
    private fun at(minutes: Long): Instant = bedtime.plus(Duration.ofMinutes(minutes))
    private fun span(from: Long, to: Long, stage: SleepStage) = StageSpan(at(from), at(to), stage)
    private fun mins(n: Long): Duration = Duration.ofMinutes(n)

    /** 4 hours: drifting off, light, deep, REM, light again; [stirs] are brief wake-ups. */
    private fun night(stirs: List<StageSpan> = emptyList()) = SleepNights.build(
        SleepSpan(at(0), at(240)),
        listOf(
            SleepSession(
                at(0), at(240),
                listOf(
                    span(0, 10, SleepStage.AWAKE),
                    span(10, 100, SleepStage.LIGHT),
                    span(100, 160, SleepStage.DEEP),
                    span(160, 200, SleepStage.REM),
                    span(200, 240, SleepStage.LIGHT),
                ),
                stirs,
            ),
        ),
    )

    /**
     * Each minute's heart rate: high while settling, a calm 55 through the light sleep, a calmer 52
     * through deep, jumping about in REM, and a steady but higher 56 in the last light sleep.
     */
    private fun heart(upTo: Long = 240): List<Pair<Instant, Double>> = (0 until upTo).map { m ->
        at(m) to when (m) {
            in 0 until 10 -> 75.0
            in 10 until 30 -> 66.0
            in 30 until 100 -> 55.0
            in 100 until 160 -> 52.0
            in 160 until 200 -> if (m % 2 == 0L) 58.0 else 66.0
            else -> 56.0
        }
    }

    @Test
    fun calmLowStretchesCount() {
        // The night's middle sleeping rate is 55, under a resting rate of 60, so 55 and below is low.
        // Minutes 32 to 157 are low and steady (the edges see the jumps either side).
        val sound = SoundSleeps.of(night(), heart(), resting = 60.0)!!
        assertEquals(mins(126), sound.total)
        assertEquals(mins(32), sound.timeTo)
    }

    @Test
    fun aRestingRateUnderTheNightsMeansLittleSoundSleep() {
        val sound = SoundSleeps.of(night(), heart(), resting = 50.0)!!
        assertEquals(Duration.ZERO, sound.total)
        assertNull(sound.timeTo)
    }

    @Test
    fun aStirBreaksAStretchButBothHalvesCount() {
        val sound = SoundSleeps.of(night(listOf(span(120, 121, SleepStage.AWAKE))), heart(), resting = 60.0)!!
        assertEquals("32-119 and 121-157", mins(125), sound.total)
    }

    @Test
    fun withoutTheNightsHeartRateThereIsNoAnswer() {
        // The band was off after 100 minutes.
        assertNull(SoundSleeps.of(night(), heart(upTo = 100), resting = 60.0))
    }

    @Test
    fun theRulesAreLearntFromGooglesFigures() {
        val heart = SoundSleeps.prepare(night(), heart(), resting = 60.0)!!
        // Google counted the steady 56 of the last light sleep too: a little more lenient than the defaults.
        val google = SoundSleeps.measure(heart, SoundRules(lowOffset = 1.0))
        val figures = GoogleFigures(google.timeTo!!, google.total, Duration.ZERO, Duration.ZERO)
        val learnt = SoundSleeps.tune(List(3) { heart to figures })
        assertEquals(google, SoundSleeps.measure(heart, learnt))
        // With too few nights to learn from, the defaults.
        assertEquals(SoundRules.DEFAULT, SoundSleeps.tune(listOf(heart to figures)))
    }

    @Test
    fun theNightUsesItForSoundSleep() {
        val measured = night().copy(sound = SoundSleep(mins(126), mins(32)))
        assertEquals(mins(126), measured.soundSleep)
        assertEquals(mins(32), measured.timeToSoundSleep)
        // Without it, deep and REM stand in.
        assertEquals(mins(100), night().soundSleep)
    }
}
