package app.wakesteps.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class RestDaysTest {

    private val cincinnati = Place.CINCINNATI
    private val zone = ZoneId.of("America/New_York")
    private val jerusalem = Place(31.778, 35.235, ZoneId.of("Asia/Jerusalem"), "Jerusalem")

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int): Instant = LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant()

    private fun blocks(from: LocalDate, to: LocalDate, place: Place = cincinnati) = RestDays.blocks(from, to) { place }

    @Test
    fun shabbosRunsFromShkiahToNightfall_asChabadOrgHasIt() {
        // Chabad.org for Cincinnati 45237: candles 5:11 PM (20 minutes before sunset), Shabbos ends 6:12 PM.
        val shabbos = blocks(LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 7)).single()
        assertEquals(at(2026, 11, 6, 17, 31), shabbos.start)
        assertEquals(at(2026, 11, 7, 18, 12), shabbos.end)
        // And the week after: candles 5:05 PM, Shabbos ends 6:06 PM.
        val next = blocks(LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 14)).single()
        assertEquals(at(2026, 11, 13, 17, 25), next.start)
        assertEquals(at(2026, 11, 14, 18, 6), next.end)
    }

    @Test
    fun aPlainShabbosHasItsParsha() {
        val shabbos = blocks(LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 7)).single()
        assertTrue(shabbos.justShabbos)
        assertEquals("Shabbos · Chayei Sara", shabbos.title)
    }

    @Test
    fun weekdaysAreNeither() {
        assertNull(RestDays.day(LocalDate.of(2026, 11, 4), inIsrael = false))
        assertTrue(blocks(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 5)).isEmpty())
    }

    @Test
    fun yomTovIsTwoDaysOutsideIsrael_andJoinsShabbos() {
        // Sukkos 5787 began on Shabbos, Sep 26, 2026.
        val sukkos = blocks(LocalDate.of(2026, 9, 26), LocalDate.of(2026, 9, 27)).single()
        assertEquals(listOf(LocalDate.of(2026, 9, 26), LocalDate.of(2026, 9, 27)), sukkos.days.map { it.date })
        assertEquals("Sukkos", sukkos.title)
        assertFalse(sukkos.justShabbos)
        // In Israel, one day.
        val inIsrael = blocks(LocalDate.of(2026, 9, 26), LocalDate.of(2026, 9, 27), jerusalem).single()
        assertEquals(listOf(LocalDate.of(2026, 9, 26)), inIsrael.days.map { it.date })
    }

    @Test
    fun roshHashanahIntoShabbos_isOneBlockOfThreeDays() {
        // Rosh Hashanah 5785 was Thu Oct 3 and Fri Oct 4, 2024, with Shabbos straight after.
        val block = blocks(LocalDate.of(2024, 10, 4), LocalDate.of(2024, 10, 4)).single()
        assertEquals(LocalDate.of(2024, 10, 3), block.days.first().date)
        assertEquals(3, block.days.size)
        assertEquals("Rosh Hashanah · Shabbos", block.title)
        assertTrue(Duration.between(block.start, block.end) > Duration.ofHours(72))
    }

    @Test
    fun yomKippur() {
        val block = blocks(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 21)).single()
        assertEquals("Yom Kippur", block.title)
    }

    private fun window(start: Instant, end: Instant?, reason: StartReason = StartReason.WOKE_UP) =
        DayWindow(start, end, reason, end?.let { EndReason.WENT_TO_SLEEP }, wokeFrom = SleepSpan(start.minus(Duration.ofHours(7)), start))

    @Test
    fun fridayEndsAtShkiah_andWhatsLeftOfSaturdayStartsAtNightfall() {
        val shabbos = blocks(LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 7)).single()
        // No sleep was tracked Friday night, so the day logic ran Friday into an all-nighter.
        val friday = DayWindow(at(2026, 11, 6, 7, 30), at(2026, 11, 7, 5, 0), StartReason.WOKE_UP, EndReason.FALLBACK)
        val saturday = DayWindow(at(2026, 11, 7, 5, 0), at(2026, 11, 7, 23, 40), StartReason.FALLBACK, EndReason.WENT_TO_SLEEP)
        val sunday = window(at(2026, 11, 8, 7, 10), null)
        val out = RestDays.clip(listOf(friday, saturday, sunday), listOf(shabbos), now = at(2026, 11, 8, 12, 0))
        assertEquals(3, out.size)
        assertEquals(DayWindow(at(2026, 11, 6, 7, 30), shabbos.start, StartReason.WOKE_UP, EndReason.REST), out[0])
        assertEquals(DayWindow(shabbos.end, at(2026, 11, 7, 23, 40), StartReason.AFTER_REST, EndReason.WENT_TO_SLEEP), out[1])
        assertEquals(sunday, out[2])
    }

    @Test
    fun duringShabbos_thereIsNoDayGoing() {
        val shabbos = blocks(LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 7)).single()
        val friday = window(at(2026, 11, 6, 7, 30), null)
        val out = RestDays.clip(listOf(friday), listOf(shabbos), now = at(2026, 11, 7, 11, 0))
        assertEquals(listOf(friday.copy(end = shabbos.start, endReason = EndReason.REST)), out)
    }

    @Test
    fun aDayWhollyInsideIsDropped_andOneJustAfterIsGoing() {
        val shabbos = blocks(LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 7)).single()
        val inside = window(at(2026, 11, 7, 8, 0), at(2026, 11, 7, 14, 0))
        val after = DayWindow(at(2026, 11, 7, 14, 30), null, StartReason.WOKE_UP, null)
        val out = RestDays.clip(listOf(inside, after), listOf(shabbos), now = at(2026, 11, 7, 19, 0))
        assertEquals(listOf(DayWindow(shabbos.end, null, StartReason.AFTER_REST, null)), out)
    }
}
