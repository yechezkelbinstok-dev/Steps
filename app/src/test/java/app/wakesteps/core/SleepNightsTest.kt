package app.wakesteps.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class SleepNightsTest {

    private val bedtime = Instant.parse("2026-10-18T23:00:00Z")

    /** [minutes] after bedtime. */
    private fun at(minutes: Long): Instant = bedtime.plus(Duration.ofMinutes(minutes))
    private fun span(from: Long, to: Long, stage: SleepStage) = StageSpan(at(from), at(to), stage)
    private fun mins(n: Long): Duration = Duration.ofMinutes(n)

    /** 11 PM to 7 AM: 15 min to drift off, a wake-up at 3 AM, a few minutes up before rising. */
    private val night = SleepSession(
        start = at(0), end = at(480),
        stages = listOf(
            span(0, 15, SleepStage.AWAKE),
            span(15, 120, SleepStage.LIGHT),
            span(120, 200, SleepStage.DEEP),
            span(200, 240, SleepStage.REM),
            span(240, 250, SleepStage.AWAKE),
            span(250, 400, SleepStage.LIGHT),
            span(400, 470, SleepStage.REM),
            span(470, 480, SleepStage.AWAKE),
        ),
        shortAwakenings = listOf(span(130, 131, SleepStage.AWAKE), span(300, 302, SleepStage.AWAKE)),
    )

    @Test
    fun aStagedNight() {
        val n = SleepNights.build(SleepSpan(at(0), at(480)), listOf(night))
        assertTrue(n.staged)
        assertEquals(mins(480), n.inBed)
        assertEquals(mins(255), n.time(SleepStage.LIGHT))
        assertEquals(mins(80), n.time(SleepStage.DEEP))
        assertEquals(mins(110), n.time(SleepStage.REM))
        assertEquals(mins(445), n.asleep)
        assertEquals(mins(35), n.awake)
        assertEquals(445f / 480f, n.efficiency, 0.0001f)
        assertEquals(mins(15), n.fellAsleepIn)
        assertEquals("only the 3 AM one counts as waking up", 1, n.wakeUps)
        assertEquals(2, n.shortAwakenings)
        assertEquals(listOf(SleepStage.AWAKE, SleepStage.REM, SleepStage.LIGHT, SleepStage.DEEP), n.lanes)
        assertEquals(SleepStage.DEEP, n.stageAt(at(150)))
        assertNull(n.stageAt(at(500)))
    }

    @Test
    fun twoSessionsMakeOneNightWithTheGapAwake() {
        val first = SleepSession(at(0), at(180), listOf(span(0, 180, SleepStage.LIGHT)))
        val second = SleepSession(at(200), at(420), listOf(span(200, 420, SleepStage.DEEP)))
        val n = SleepNights.build(SleepSpan(at(0), at(420)), listOf(second, first))
        assertEquals(
            listOf(span(0, 180, SleepStage.LIGHT), span(180, 200, SleepStage.AWAKE), span(200, 420, SleepStage.DEEP)),
            n.stages,
        )
        assertEquals(1, n.wakeUps)
        assertEquals(mins(400), n.asleep)
    }

    @Test
    fun aSessionWithoutStagesIsJustAsleep() {
        val plain = SleepSession(at(0), at(420), minutesToFallAsleep = 12)
        val n = SleepNights.build(SleepSpan(at(0), at(420)), listOf(plain))
        assertFalse(n.staged)
        assertEquals(listOf(span(0, 420, SleepStage.ASLEEP)), n.stages)
        assertEquals(mins(420), n.asleep)
        assertEquals(mins(12), n.fellAsleepIn)
        assertEquals(listOf(SleepStage.AWAKE, SleepStage.ASLEEP), n.lanes)
    }

    @Test
    fun stagesAreClippedToTheNightAndNeighboursJoined() {
        val session = SleepSession(
            at(-30), at(300),
            listOf(span(-30, 60, SleepStage.LIGHT), span(60, 100, SleepStage.LIGHT), span(100, 300, SleepStage.DEEP)),
        )
        val n = SleepNights.build(SleepSpan(at(0), at(240)), listOf(session))
        assertEquals(listOf(span(0, 100, SleepStage.LIGHT), span(100, 240, SleepStage.DEEP)), n.stages)
        assertEquals(mins(240), n.inBed)
    }

    @Test
    fun overlappingRecordingsDontCountTwice() {
        // Two apps logged the same night; the earlier one wins where they overlap.
        val a = SleepSession(at(0), at(300), listOf(span(0, 300, SleepStage.LIGHT)))
        val b = SleepSession(at(60), at(360), listOf(span(60, 360, SleepStage.DEEP)))
        val n = SleepNights.build(SleepSpan(at(0), at(360)), listOf(a, b))
        assertEquals(listOf(span(0, 300, SleepStage.LIGHT), span(300, 360, SleepStage.DEEP)), n.stages)
        assertEquals(mins(360), n.asleep)
    }

    @Test
    fun noRecordingsLeavesJustTheSpan() {
        val n = SleepNights.build(SleepSpan(at(0), at(420)), emptyList())
        assertTrue(n.stages.isEmpty())
        assertEquals(mins(420), n.asleep)
        assertNull(n.fellAsleepIn)
    }
}
