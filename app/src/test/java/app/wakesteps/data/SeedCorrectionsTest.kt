package app.wakesteps.data

import app.wakesteps.core.DayCalculator
import app.wakesteps.core.DayRules
import app.wakesteps.core.SleepSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class SeedCorrectionsTest {

    private val zone = ZoneId.of("America/New_York")
    private val overrides = parseOverrides(SEED_CORRECTIONS)

    private fun t(s: String): Instant = LocalDateTime.parse(s).atZone(zone).toInstant()
    private fun local(i: Instant): LocalDateTime = i.atZone(zone).toLocalDateTime()

    @Test
    fun theJoinIsWednesdayNightsGapBeforeThursdayMorningsSleep() {
        val gap = overrides.joins.single()
        assertEquals(LocalDateTime.parse("2026-11-19T01:21"), local(gap.start))
        assertEquals(LocalDateTime.parse("2026-11-19T07:28"), local(gap.endInclusive))
    }

    @Test
    fun theDroppedSleepIsFridayMornings() {
        val stretch = overrides.ignoredSleeps.single()
        assertEquals(LocalDateTime.parse("2026-11-20T03:59"), local(stretch.start))
        assertEquals(LocalDateTime.parse("2026-11-20T08:24"), local(stretch.endInclusive))
    }

    @Test
    fun whatTheLastVersionPutInMeansNothing() {
        val retired = parseOverrides(RETIRED_CORRECTIONS)
        assertTrue(retired.joins.isEmpty() && retired.ignoredSleeps.isEmpty())
    }

    @Test
    fun theWeekAsItHappenedHasNoNightLongerThanItShouldBe() {
        // Wed night 8:32 PM to 1:21 AM, Thu 7:28 AM to 12:15 PM, someone else Fri 3:59 to 8:24 AM,
        // nothing over Shabbos, then Sunday 6:15 AM to 12:29 PM.
        val sleeps = listOf(
            SleepSpan(t("2026-11-17T23:40"), t("2026-11-18T08:10")),
            SleepSpan(t("2026-11-18T20:32"), t("2026-11-19T01:21")),
            SleepSpan(t("2026-11-19T07:28"), t("2026-11-19T12:15")),
            SleepSpan(t("2026-11-20T03:59"), t("2026-11-20T08:24")),
            SleepSpan(t("2026-11-22T06:15"), t("2026-11-22T12:29")),
        )
        val windows = DayCalculator.windows(sleeps, t("2026-11-16T00:00"), t("2026-11-22T12:55"), zone, DayRules(), overrides)
        val nights = windows.mapNotNull { it.wokeFrom }
        assertTrue(SleepSpan(t("2026-11-18T20:32"), t("2026-11-19T12:15")) in nights)
        assertEquals(SleepSpan(t("2026-11-22T06:15"), t("2026-11-22T12:29")), windows.last().wokeFrom)
        assertTrue(nights.all { it.duration <= Duration.ofHours(16) })
        assertTrue(windows.none { it.start == t("2026-11-20T08:24") })
    }
}
