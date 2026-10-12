package app.wakesteps.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
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
        // Still up at noon, more than 6 h past 5 AM: an all-nighter, back-dated to 5 AM.
        val day = DayCalculator.current(sleeps, t("2026-10-10T00:00"), t("2026-10-12T12:00"), zone, rules)
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
        val merged = DayCalculator.mainSleeps(sleeps, zone, rules)
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

    // ------------------------------------------------------------ naps vs. sleep

    @Test
    fun shortSleepAtNightStartsNewDay() {
        // Up all day, then only 4–6 AM: that's still your night's sleep.
        val sleeps = listOf(
            sleep("2026-10-10T23:00", "2026-10-11T07:00"),
            sleep("2026-10-12T04:00", "2026-10-12T06:00"),
        )
        val day = DayCalculator.current(sleeps, t("2026-10-10T00:00"), t("2026-10-12T12:00"), zone, rules)
        assertEquals(t("2026-10-12T06:00"), day.start)
        assertEquals(StartReason.WOKE_UP, day.startReason)
    }

    @Test
    fun sameLengthSleepInAfternoonIsNap() {
        val sleeps = listOf(
            sleep("2026-10-11T23:30", "2026-10-12T07:00"),
            sleep("2026-10-12T16:00", "2026-10-12T18:00"),
        )
        val day = DayCalculator.current(sleeps, t("2026-10-10T00:00"), t("2026-10-12T20:00"), zone, rules)
        assertEquals(t("2026-10-12T07:00"), day.start)
    }

    @Test
    fun longDaytimeSleepStillCounts() {
        // All-nighter, then 9 AM – 3 PM recovery sleep: the day starts when you get up at 3.
        val sleeps = listOf(
            sleep("2026-10-10T23:00", "2026-10-11T07:00"),
            sleep("2026-10-12T09:00", "2026-10-12T15:00"),
        )
        val day = DayCalculator.current(sleeps, t("2026-10-10T00:00"), t("2026-10-12T18:00"), zone, rules)
        assertEquals(t("2026-10-12T15:00"), day.start)
    }

    @Test
    fun eveningDozeDoesNotSplitTheDay() {
        val sleeps = listOf(
            sleep("2026-10-11T00:00", "2026-10-11T08:00"),
            sleep("2026-10-11T20:30", "2026-10-11T22:00"),
            sleep("2026-10-12T02:00", "2026-10-12T08:00"),
        )
        val windows = DayCalculator.windows(sleeps, t("2026-10-10T20:00"), t("2026-10-12T12:00"), zone, rules)
        val yesterday = windows[windows.size - 2]
        assertEquals(t("2026-10-11T08:00"), yesterday.start)
        assertEquals(t("2026-10-12T02:00"), yesterday.end)
    }

    @Test
    fun wakingInTheNightDoesNotSplitTheNight() {
        // Asleep 12:30–2:30, up for two hours, back to sleep 4:30–8: one night, day starts at 8.
        val sleeps = listOf(
            sleep("2026-10-11T00:00", "2026-10-11T08:00"),
            sleep("2026-10-12T00:30", "2026-10-12T02:30"),
            sleep("2026-10-12T04:30", "2026-10-12T08:00"),
        )
        val windows = DayCalculator.windows(sleeps, t("2026-10-10T20:00"), t("2026-10-12T12:00"), zone, rules)
        assertEquals(t("2026-10-12T08:00"), windows.last().start)
        assertEquals(t("2026-10-12T00:30"), windows[windows.size - 2].end)
        assertTrue(windows.none { it.start == t("2026-10-12T02:30") })
    }

    @Test
    fun nightIsLearnedAcrossMidnight() {
        val sleeps = (5..9).map { d -> sleep("2026-10-0${d}T23:00", "2026-10-${"%02d".format(d + 1)}T07:00") }
        val night = DayCalculator.nightWindow(sleeps, zone, rules)
        assertTrue(night.learned)
        assertEquals(LocalTime.of(3, 0), night.midpoint)
    }

    @Test
    fun lateSleeperShortMorningSleepCounts() {
        // Usually asleep 4 AM – noon, so a short 9–11 AM sleep is their night, not a nap.
        val usual = (5..9).map { d -> sleep("2026-10-${"%02d".format(d)}T04:00", "2026-10-${"%02d".format(d)}T12:00") }
        val sleeps = usual + sleep("2026-10-11T09:00", "2026-10-11T11:00")
        val day = DayCalculator.current(sleeps, t("2026-10-08T00:00"), t("2026-10-11T15:00"), zone, rules)
        assertEquals(t("2026-10-11T11:00"), day.start)
    }

    @Test
    fun morningSleepAfterLongNightIsTheNightsSleep() {
        // Usually 11 PM – 7 AM, but up since 7 AM yesterday and asleep 9–11 AM: that's the
        // night's sleep, not a nap, and there's no all-nighter reset at 5.
        val usual = (5..9).map { d -> sleep("2026-10-0${d}T23:00", "2026-10-${"%02d".format(d + 1)}T07:00") }
        val sleeps = usual + sleep("2026-10-11T09:00", "2026-10-11T11:00")
        val windows = DayCalculator.windows(sleeps, t("2026-10-08T00:00"), t("2026-10-11T15:00"), zone, rules)
        assertEquals(t("2026-10-11T11:00"), windows.last().start)
        assertEquals(t("2026-10-10T07:00"), windows[windows.size - 2].start)
        assertEquals(t("2026-10-11T09:00"), windows[windows.size - 2].end)
    }

    // ------------------------------------------------------------ late nights vs. all-nighters

    @Test
    fun bedAt6AmIsALateNightNotAnAllNighter() {
        // Woke 7 AM, stayed up, slept 6–9 AM: yesterday runs until 6 AM, today starts at 9.
        val sleeps = listOf(
            sleep("2026-10-10T23:00", "2026-10-11T07:00"),
            sleep("2026-10-12T06:00", "2026-10-12T09:00"),
        )
        val windows = DayCalculator.windows(sleeps, t("2026-10-10T00:00"), t("2026-10-12T13:00"), zone, rules)
        assertEquals(t("2026-10-12T09:00"), windows.last().start)
        assertEquals(StartReason.WOKE_UP, windows.last().startReason)
        val yesterday = windows[windows.size - 2]
        assertEquals(t("2026-10-11T07:00"), yesterday.start)
        assertEquals(t("2026-10-12T06:00"), yesterday.end)
        assertTrue(windows.none { it.startReason == StartReason.FALLBACK && it.start == t("2026-10-12T05:00") })
    }

    @Test
    fun allNighterIsOnlyDecidedAfterTheGracePeriod() {
        val sleeps = listOf(sleep("2026-10-10T23:00", "2026-10-11T07:00"))
        // 8 AM, still up: could still go to bed, so it's still yesterday's day.
        val early = DayCalculator.current(sleeps, t("2026-10-10T00:00"), t("2026-10-12T08:00"), zone, rules)
        assertEquals(t("2026-10-11T07:00"), early.start)
        // 11:30 AM, still up: now it's an all-nighter and the new day counts from 5 AM.
        val late = DayCalculator.current(sleeps, t("2026-10-10T00:00"), t("2026-10-12T11:30"), zone, rules)
        assertEquals(t("2026-10-12T05:00"), late.start)
    }

    @Test
    fun shortDozeDoesNotRescueAnAllNighter() {
        val sleeps = listOf(
            sleep("2026-10-10T23:00", "2026-10-11T07:00"),
            sleep("2026-10-12T06:00", "2026-10-12T06:40"),
        )
        val day = DayCalculator.current(sleeps, t("2026-10-10T00:00"), t("2026-10-12T12:00"), zone, rules)
        assertEquals(t("2026-10-12T05:00"), day.start)
        assertEquals(listOf(t("2026-10-12T06:00")), day.naps.map { it.start })
    }

    @Test
    fun crashSleepLongAfterAllNighterStartsNewDay() {
        // Up all night and all morning (reset confirmed), then 2–4 PM: after 31 h up that's
        // real sleep, so a new day starts at 4.
        val sleeps = listOf(
            sleep("2026-10-10T23:00", "2026-10-11T07:00"),
            sleep("2026-10-12T14:00", "2026-10-12T16:00"),
        )
        val windows = DayCalculator.windows(sleeps, t("2026-10-10T00:00"), t("2026-10-12T20:00"), zone, rules)
        assertEquals(t("2026-10-12T16:00"), windows.last().start)
        assertEquals(t("2026-10-12T05:00"), windows[windows.size - 2].start)
    }

    // ------------------------------------------------------------ judgement calls

    @Test
    fun napCalledSleepStartsNewDay() {
        val sleeps = listOf(
            sleep("2026-10-11T23:30", "2026-10-12T07:00"),
            sleep("2026-10-12T16:00", "2026-10-12T18:00"),
        )
        val overrides = Overrides(sleepCalls = mapOf(t("2026-10-12T17:00") to true))
        val day = DayCalculator.current(sleeps, t("2026-10-10T00:00"), t("2026-10-12T20:00"), zone, rules, overrides)
        assertEquals(t("2026-10-12T18:00"), day.start)
    }

    @Test
    fun sleepCalledNapMergesWithDayBefore() {
        val sleeps = listOf(
            sleep("2026-10-10T23:00", "2026-10-11T07:00"),
            sleep("2026-10-11T23:30", "2026-10-12T07:00"),
        )
        val overrides = Overrides(sleepCalls = mapOf(t("2026-10-12T03:00") to false))
        val day = DayCalculator.current(sleeps, t("2026-10-10T00:00"), t("2026-10-12T12:00"), zone, rules, overrides)
        // The 5 AM reset falls inside the sleep they called a nap, so it doesn't split the day.
        assertEquals(t("2026-10-11T07:00"), day.start)
        assertEquals(listOf(t("2026-10-11T23:30")), day.naps.map { it.start })
    }

    @Test
    fun skippedResetKeepsDayGoing() {
        val sleeps = listOf(sleep("2026-10-10T23:00", "2026-10-11T07:00"))
        val overrides = Overrides(skippedResets = setOf(t("2026-10-12T05:00")))
        val day = DayCalculator.current(sleeps, t("2026-10-10T00:00"), t("2026-10-12T14:00"), zone, rules, overrides)
        assertEquals(t("2026-10-11T07:00"), day.start)
    }

    @Test
    fun manualStartWhenSleepWasNotTracked() {
        // Watch off overnight: no sleep logged. "I woke at 8:30" starts the day there,
        // and the 5 AM reset doesn't leave a 5:00–8:30 sliver of a day behind.
        val sleeps = listOf(sleep("2026-10-10T23:00", "2026-10-11T07:00"))
        val overrides = Overrides(manualStarts = setOf(t("2026-10-12T08:30")))
        val windows = DayCalculator.windows(sleeps, t("2026-10-10T00:00"), t("2026-10-12T12:00"), zone, rules, overrides)
        assertEquals(t("2026-10-12T08:30"), windows.last().start)
        assertEquals(StartReason.MANUAL, windows.last().startReason)
        assertEquals(t("2026-10-11T07:00"), windows[windows.size - 2].start)
        assertEquals(EndReason.MANUAL, windows[windows.size - 2].endReason)
    }

    @Test
    fun napsAreListedOnTheirDay() {
        val sleeps = listOf(
            sleep("2026-10-11T23:30", "2026-10-12T07:00"),
            sleep("2026-10-12T14:00", "2026-10-12T14:40"),
        )
        val day = DayCalculator.current(sleeps, t("2026-10-10T00:00"), t("2026-10-12T20:00"), zone, rules)
        assertEquals(listOf(t("2026-10-12T14:00")), day.naps.map { it.start })
        assertEquals(t("2026-10-11T23:30"), day.wokeFrom?.start)
    }
}
