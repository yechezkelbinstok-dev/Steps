package app.wakesteps.ui

import app.wakesteps.core.DayWindow
import app.wakesteps.core.EndReason
import app.wakesteps.core.RestBlock
import app.wakesteps.core.RestDay
import app.wakesteps.core.StartReason
import app.wakesteps.data.CalendarDay
import app.wakesteps.data.DaySummary
import app.wakesteps.data.DayTotals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class MonthCellsTest {

    private val zone = ZoneId.of("America/New_York")
    private val october = YearMonth.of(2026, 10)
    private fun at(d: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 10, d, h, m).atZone(zone).toInstant()
    private fun day(d: Int, steps: Long, startHour: Int = 7, reason: StartReason = StartReason.WOKE_UP) =
        DaySummary(DayWindow(at(d, startHour), at(d, 23), reason, EndReason.WENT_TO_SLEEP), DayTotals(steps, null, null))

    // Shabbos Oct 17: Friday sunset to Saturday nightfall, then motzei Shabbos until bed.
    private val shabbos = RestBlock(at(16, 18, 54), at(17, 19, 52), listOf(RestDay(LocalDate.of(2026, 10, 17), "Shabbos", "Vayera")))
    private val motzei = DaySummary(DayWindow(at(17, 19, 52), at(17, 23, 30), StartReason.AFTER_REST, EndReason.WENT_TO_SLEEP), DayTotals(1240, null, null))

    @Test
    fun eachDayOnTheDateItBegan() {
        val days = listOf(day(15, 8120), day(16, 11250), motzei, day(18, 9050))
        val cells = awakeMonthCells(october, days, listOf(shabbos), today = LocalDate.of(2026, 10, 18), zone = zone)
        assertEquals(31, cells.size)
        assertEquals(8120L, cells[14].steps)
        assertEquals(11250L, cells[15].steps)
        assertTrue(cells[17].today)
        assertNull("nothing read for the 3rd", cells[2].steps)
    }

    @Test
    fun shabbosIsCandles_withMotzeiShabbosFoldedIn() {
        val cells = awakeMonthCells(october, listOf(day(16, 11250), motzei), listOf(shabbos), LocalDate.of(2026, 10, 18), zone)
        val saturday = cells[16]
        assertSame(shabbos, saturday.rest)
        assertEquals(1240L, saturday.steps)
        assertSame(motzei, saturday.open)
    }

    @Test
    fun midnightToMidnight() {
        val calendar = listOf(CalendarDay(LocalDate.of(2026, 10, 15), DayTotals(9000, null, null)))
        val cells = calendarMonthCells(october, calendar, listOf(shabbos), LocalDate.of(2026, 10, 18))
        assertEquals(9000L, cells[14].steps)
        assertSame(shabbos, cells[16].rest)
        assertNull(cells[14].open)
    }
}
