package app.wakesteps.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class HeartRateTest {

    private val midnight = Instant.parse("2026-11-23T05:00:00Z")
    private fun at(minute: Long): Instant = midnight.plus(Duration.ofMinutes(minute))
    private fun point(minute: Long, avg: Float, min: Float = avg, max: Float = avg) = HeartPoint(at(minute), avg, min, max)

    @Test
    fun zonesAsGoogleSetsThemFromHeartRateReserve() {
        // Google's heart rate page on Nov 23: moderate from 123, vigorous from 148, peak from 180.
        assertEquals(HeartZones(123, 148, 180), HeartZones.of(72.0))
        assertEquals(HeartZones.of(72.0), HeartZones.USUAL)
        // A lower resting rate leaves more reserve: every zone begins lower.
        assertEquals(HeartZones(110, 140, 177), HeartZones.of(50.0))
    }

    @Test
    fun whichZoneAHeartRateIsIn() {
        val zones = HeartZones.USUAL
        // "High of 120 bpm in light zone."
        assertEquals(HeartZone.LIGHT, zones.of(120f))
        assertEquals(HeartZone.MODERATE, zones.of(123f))
        assertEquals(HeartZone.MODERATE, zones.of(147.9f))
        assertEquals(HeartZone.VIGOROUS, zones.of(148f))
        assertEquals(HeartZone.PEAK, zones.of(180f))
    }

    @Test
    fun theRangeIsTheLowestLowAndTheHighestHigh() {
        val points = listOf(point(0, 70f, 66f, 74f), point(1, 90f, 63f, 120f), point(2, 80f), point(600, 140f))
        assertEquals(63 to 120, heartRange(points, at(0), at(3)))
        assertEquals(80 to 140, heartRange(points, at(2), at(1440)))
        assertNull(heartRange(points, at(3), at(600)))
    }

    @Test
    fun zoomedInBetweenTwoMinutesTheRangeIsTheMinuteThatRunsIntoIt() {
        // Half a minute across, from 00:01:10 to 00:01:40: no minute begins in it, but the one
        // that began at 00:01 runs through it.
        val points = listOf(point(0, 70f, 66f, 74f), point(1, 90f, 63f, 120f), point(2, 80f))
        val from = at(1).plusSeconds(10)
        val to = at(1).plusSeconds(40)
        assertNull(heartRange(points, from, to))
        assertEquals(63 to 120, heartRange(points, from, to, Duration.ofMinutes(1)))
        // A minute that ended before the view began isn't in it.
        assertEquals(63 to 120, heartRange(points, at(1), at(2), Duration.ofMinutes(1)))
    }

    @Test
    fun barsLineUpWithTheClockAndSkipEmptyStretches() {
        val points = listOf(point(1, 70f, 68f, 72f), point(14, 90f, 85f, 95f), point(15, 60f), point(61, 100f))
        val bars = heartBars(points, midnight, 15 * 60)
        assertEquals(listOf(HeartBar(0, 900, 68f, 95f), HeartBar(900, 900, 60f, 60f), HeartBar(3600, 900, 100f, 100f)), bars)
    }

    @Test
    fun barsOfAFewSecondsFromReadingsASecondApart() {
        // A reading every second from 00:10:03 to 00:10:21, climbing a beat a second.
        val start = at(10)
        val points = (3..21).map { HeartPoint(start.plusSeconds(it.toLong()), 80f + it, 80f + it, 80f + it) }
        val bars = heartBars(points, midnight, 10)
        assertEquals(
            listOf(HeartBar(600, 10, 83f, 89f), HeartBar(610, 10, 90f, 99f), HeartBar(620, 10, 100f, 101f)),
            bars,
        )
    }
}
