package app.wakesteps.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class DayCalculatorTest {
    private val zone = ZoneId.of("America/New_York")
    private val rules = DayRules()

    private fun t(s: String): Instant = LocalDateTime.parse(s).atZone(zone).toInstant()
    private fun sleep(a: String, b: String) = SleepSpan(t(a), t(b))

    @Test
    fun dayStartsAtWakeUp() {
        val sleeps = listOf(sleep("2026-10-11T23:30", "2026-10-12T07:12"))
        val day = DayCalculator.current(sleeps, t("2026-10-10T00:00"), t("2026-10-12T15:00"), zone, rules)
        assertEquals(t("2026-10-12T07:12"), day.start)
        assertEquals(StartReason.WOKE_UP, day.startReason)
        assertNull(day.end)
    }

    @Test
    fun lateNightStaysOnSameDay() {
        // Woke at 9am, it's now 2am the next calendar day and you haven't slept: still the same day.
        val sleeps = listOf(sleep("2026-10-11T01:00", "2026-10-11T09:00"))
        val day = DayCalculator.current(sleeps, t("2026-10-10T00:00"), t("2026-10-12T02:00"), zone, rules)
        assertEquals(t("2026-10-11T09:00"), day.start)
    }

    @Test
    fun allNighterFallsBackAtFallbackTime() {
        val sleeps = listOf(sleep("2026-10-11T01:00", "2026-10-11T09:00"))
        val day = DayCalculator.current(sleeps, t("2026-10-10T00:00"), t("2026-10-12T10:00"), zone, rules)
        assertEquals(t("2026-10-12T05:00"), day.start)
        assertEquals(StartReason.FALLBACK, day.startReason)
    }

    @Test
    fun earlyWakeIsNotCutOffBySoonFallback() {
        // Woke at 4:30; the 5:00 fallback is too soon after waking to count.
        val sleeps = listOf(sleep("2026-10-11T22:00", "2026-10-12T04:30"))
        val day = DayCalculator.current(sleeps, t("2026-10-10T00:00"), t("2026-10-12T12:00"), zone, rules)
        assertEquals(t("2026-10-12T04:30"), day.start)
    }

    @Test
    fun napsDoNotStartANewDay() {
        val sleeps = listOf(
            sleep("2026-10-11T23:00", "2026-10-12T07:00"),
            sleep("2026-10-12T14:00", "2026-10-12T15:00"),
        )
        val windows = DayCalculator.windows(sleeps, t("2026-10-11T12:00"), t("2026-10-12T18:00"), zone, rules)
        assertEquals(t("2026-10-12T07:00"), windows.last().start)
    }

    @Test
    fun fragmentedNightIsMerged() {
        val sleeps = listOf(
            sleep("2026-10-11T23:00", "2026-10-12T01:30"),
            sleep("2026-10-12T02:00", "2026-10-12T04:00"),
            sleep("2026-10-12T04:20", "2026-10-12T06:45"),
        )
        val merged = DayCalculator.mainSleeps(sleeps, rules)
        assertEquals(1, merged.size)
        assertEquals(Duration.ofMinutes(465), merged.single().duration)
    }

    @Test
    fun historyWindowsEndAtBedtime() {
        val sleeps = listOf(
            sleep("2026-10-10T23:00", "2026-10-11T07:00"),
            sleep("2026-10-11T23:45", "2026-10-12T07:30"),
        )
        val windows = DayCalculator.windows(sleeps, t("2026-10-10T20:00"), t("2026-10-12T12:00"), zone, rules)
        val yesterday = windows[windows.size - 2]
        assertEquals(t("2026-10-11T07:00"), yesterday.start)
        assertEquals(t("2026-10-11T23:45"), yesterday.end)
        assertEquals(EndReason.WENT_TO_SLEEP, yesterday.endReason)
    }

    @Test
    fun noSleepDataUsesFallbackBoundaries() {
        val day = DayCalculator.current(emptyList(), t("2026-10-10T12:00"), t("2026-10-12T03:00"), zone, rules)
        assertEquals(t("2026-10-11T05:00"), day.start)
        assertEquals(StartReason.FALLBACK, day.startReason)
    }

    @Test
    fun currentlyAsleepEndsTheDay() {
        val sleeps = listOf(
            sleep("2026-10-11T00:00", "2026-10-11T08:00"),
            sleep("2026-10-11T23:00", "2026-10-12T07:00"),
        )
        val day = DayCalculator.current(sleeps, t("2026-10-10T12:00"), t("2026-10-12T03:00"), zone, rules)
        assertEquals(t("2026-10-11T08:00"), day.start)
        assertEquals(t("2026-10-11T23:00"), day.end)
    }
}
