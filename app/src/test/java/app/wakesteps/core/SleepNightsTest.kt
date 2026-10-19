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

    /** 11 PM to 7 AM: 15 min to drift off, a 10-minute wake-up, two brief stirs, a few minutes up before rising. */
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
        // The brief stirs are cut out of the deep and light sleep they interrupted.
        assertEquals(mins(253), n.time(SleepStage.LIGHT))
        assertEquals(mins(79), n.time(SleepStage.DEEP))
        assertEquals(mins(110), n.time(SleepStage.REM))
        assertEquals(mins(3), n.time(SleepStage.RESTLESS))
        assertEquals("restless counts as asleep", mins(445), n.asleep)
        assertEquals(mins(35), n.awake)
        assertEquals(445f / 480f, n.efficiency, 0.0001f)
        assertEquals(mins(442), n.soundSleep)
        assertEquals(mins(15), n.fellAsleepIn)
        assertEquals(mins(15), n.timeToSoundSleep)
        assertEquals("only the 10-minute one is a full awakening", 1, n.fullAwakenings)
        assertEquals(mins(10), n.interruptions)
        assertEquals(2, n.restlessMoments)
        assertEquals(
            listOf(SleepStage.AWAKE, SleepStage.RESTLESS, SleepStage.REM, SleepStage.LIGHT, SleepStage.DEEP),
            n.lanes,
        )
        assertEquals(SleepStage.DEEP, n.stageAt(at(150)))
        assertEquals(SleepStage.RESTLESS, n.stageAt(at(301)))
        assertNull(n.stageAt(at(500)))
    }

    @Test
    fun partOfTheNight() {
        val n = SleepNights.build(SleepSpan(at(0), at(480)), listOf(night)).within(at(100), at(260))
        assertEquals(mins(160), n.inBed)
        assertEquals("light 100-120 and 250-260", mins(30), n.time(SleepStage.LIGHT))
        assertEquals("deep 120-200, less the stir", mins(79), n.time(SleepStage.DEEP))
        assertEquals(mins(1), n.time(SleepStage.RESTLESS))
        assertEquals(mins(40), n.time(SleepStage.REM))
        assertEquals(mins(10), n.time(SleepStage.AWAKE))
        assertEquals(StageSpan(at(100), at(120), SleepStage.LIGHT), n.stages.first())
    }

    @Test
    fun restlessCanCountAsAwake() {
        val n = SleepNights.build(SleepSpan(at(0), at(480)), listOf(night)).restlessAsAwake()
        assertEquals(Duration.ZERO, n.time(SleepStage.RESTLESS))
        assertEquals(mins(38), n.awake)
        assertEquals(mins(442), n.asleep)
        assertEquals("each stir counts as waking up", 3, n.fullAwakenings)
        assertEquals(mins(13), n.interruptions)
        assertEquals(0, n.restlessMoments)
        assertFalse(SleepStage.RESTLESS in n.lanes)
    }

    @Test
    fun aWakeUpOfFiveMinutesOrLessIsRestless() {
        val session = SleepSession(
            at(0), at(200),
            listOf(span(0, 3, SleepStage.AWAKE), span(3, 100, SleepStage.LIGHT), span(100, 104, SleepStage.AWAKE), span(104, 200, SleepStage.LIGHT)),
        )
        val n = SleepNights.build(SleepSpan(at(0), at(200)), listOf(session))
        assertEquals("drifting off stays awake, however short", span(0, 3, SleepStage.AWAKE), n.stages.first())
        assertEquals(span(100, 104, SleepStage.RESTLESS), n.stages[2])
        assertEquals(0, n.fullAwakenings)
        assertEquals(1, n.restlessMoments)
        assertEquals(mins(197), n.asleep)
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
        assertEquals(1, n.fullAwakenings)
        assertEquals(mins(20), n.interruptions)
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
        assertNull(n.timeToSoundSleep)
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
        assertEquals(0, n.fullAwakenings)
    }
}
