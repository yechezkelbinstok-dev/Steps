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
    fun barsLineUpWithTheClockAndSkipEmptyStretches() {
        val points = listOf(point(1, 70f, 68f, 72f), point(14, 90f, 85f, 95f), point(15, 60f), point(61, 100f))
        val bars = heartBars(points, midnight, 15)
        assertEquals(listOf(HeartBar(0, 15, 68f, 95f), HeartBar(15, 15, 60f, 60f), HeartBar(60, 15, 100f, 100f)), bars)
    }
}
