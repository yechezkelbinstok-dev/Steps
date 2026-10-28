package app.wakesteps.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BarMinutesTest {

    private val day = 990 // 7:12 AM to 11:42 PM

    @Test
    fun theWholeDayIsHourByHour() {
        assertEquals(60, barMinutes(day.toDouble(), day))
        assertEquals(60, barMinutes(510.0, 510))
        assertEquals(60, barMinutes(120.0, 120))
    }

    @Test
    fun zoomingInMakesThemFinerAStepAtATime_downToMinutes() {
        // Pinching in slowly from the whole day to ten minutes across.
        val sizes = generateSequence(day.toDouble()) { it * 0.97 }.takeWhile { it >= 10.0 }.map { barMinutes(it, day) }.toList()
        assertEquals(listOf(60, 30, 20, 15, 10, 5, 3, 2, 1), sizes.distinct())
        assertTrue(sizes.zipWithNext().all { (a, b) -> b <= a })
    }

    @Test
    fun minuteByMinuteWithFortyMinutesOrLessAcross() {
        assertEquals(1, barMinutes(40.0, day))
        assertEquals(1, barMinutes(10.0, day))
        assertTrue(barMinutes(90.0, day) > 1)
    }

    @Test
    fun aDayJustBegunIsMinuteByMinute() {
        assertEquals(1, barMinutes(30.0, 30))
    }
}
