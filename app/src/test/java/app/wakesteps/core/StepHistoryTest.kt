package app.wakesteps.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class StepHistoryTest {

    private val t0 = Instant.parse("2026-10-19T08:00:00Z")
    private fun at(seconds: Long) = t0.plusSeconds(seconds)

    @Test
    fun nothingBeforeTheFirstReading() {
        assertNull(StepHistory().stepsSince(t0))
    }

    @Test
    fun fillsInBetweenReadings() {
        val h = StepHistory()
        h.record(at(0), 100)
        h.record(at(30), 130)
        h.record(at(60), 160)
        // Halfway between 0 s (100) and 30 s (130) the counter stood at 115.
        assertEquals(45L, h.stepsSince(at(15)))
        assertEquals(30L, h.stepsSince(at(30)))
        assertEquals(0L, h.stepsSince(at(60)))
        assertEquals("nothing after the newest reading", 0L, h.stepsSince(at(600)))
    }

    @Test
    fun beforeTheOldestReadingCountsOnlyWhatThePhoneSaw() {
        val h = StepHistory()
        h.record(at(0), 100)
        h.record(at(60), 160)
        assertEquals(60L, h.stepsSince(at(-3600)))
    }

    @Test
    fun aWalkAfterAnIdleStretchIsPlacedWhenItHappened() {
        val h = StepHistory()
        h.record(at(0), 100)
        // An hour sitting still, then a walk: readings only come with steps.
        for (s in 3600L..3720L step 2) h.record(at(s), 100 + (s - 3600) / 2 + 1)
        // Nothing was walked in the first half hour, so everything since then counts.
        assertEquals(61L, h.stepsSince(at(1800)))
        // Half a minute into the walk, roughly half of it is still to come.
        val rest = h.stepsSince(at(3660))!!
        assertTrue("about 30 steps after 3660 s, got $rest", rest in 29..32)
    }

    @Test
    fun restartsAfterThePhoneDoes() {
        val h = StepHistory()
        h.record(at(0), 5000)
        h.record(at(60), 5100)
        h.record(at(120), 3) // rebooted: the counter started over
        assertEquals(0L, h.stepsSince(at(0)))
        h.record(at(180), 23)
        assertEquals(20L, h.stepsSince(at(0)))
        assertEquals(2, h.size())
    }

    @Test
    fun keepsReadingsAboutTenSecondsApartWithTheNewestCurrent() {
        val h = StepHistory()
        for (s in 0L..9L) h.record(at(s), 100 + s)
        assertEquals(2, h.size())
        assertEquals(StepHistory.Reading(at(9), 109), h.newest())
        for (s in 10L..100L) h.record(at(s), 100 + s)
        assertTrue("about one per 10 s, got ${h.size()}", h.size() in 10..12)
        assertEquals(200L, h.newest()!!.count)
    }

    @Test
    fun forgetsReadingsOlderThanItNeeds() {
        val h = StepHistory(keep = Duration.ofHours(1))
        for (minute in 0L..180L) h.record(at(minute * 60), minute * 10)
        // The last hour, plus one reading from just before it.
        assertEquals(62, h.size())
        assertEquals(600L, h.stepsSince(at(120 * 60)))
    }

    @Test
    fun aReadingCantGoBackInTime() {
        val h = StepHistory()
        h.record(at(0), 100)
        h.record(at(50), 150)
        h.record(at(40), 155) // the wall clock stepped back a little
        assertEquals(StepHistory.Reading(at(50), 155), h.newest())
        assertEquals(55L, h.stepsSince(at(0)))
    }
}
