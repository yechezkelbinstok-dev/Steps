package app.wakesteps.core

import android.icu.util.HebrewCalendar
import android.icu.util.TimeZone
import app.wakesteps.widget.RenderTestApp
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Robolectric, for ICU's Hebrew calendar. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = RenderTestApp::class)
class HebrewDateTest {

    private fun date(year: Int, month: Int, day: Int) = LocalDate.of(year, month, day)

    /** The everyday date of a Hebrew one, by ICU. */
    private fun civil(year: Int, month: Int, day: Int): LocalDate {
        val calendar = HebrewCalendar(TimeZone.GMT_ZONE).apply {
            clear()
            set(year, month, day)
        }
        return LocalDate.ofEpochDay(Math.floorDiv(calendar.timeInMillis, 86_400_000L))
    }

    @Test
    fun theHolidaysAndThisWeek() {
        assertEquals("א׳ תשרי", HebrewDate.of(date(2026, 9, 12)))
        assertEquals("י׳ תשרי", HebrewDate.of(date(2026, 9, 21)))
        assertEquals("ט״ו תשרי", HebrewDate.of(date(2026, 9, 26)))
        assertEquals("ל׳ תשרי", HebrewDate.of(date(2026, 10, 11)))
        assertEquals("ח׳ חשון", HebrewDate.of(date(2026, 10, 19)))
        assertEquals("כ״ה כסלו", HebrewDate.of(date(2026, 12, 5)))
    }

    @Test
    fun adarIsAdarIIInALeapYear() {
        // 5786 isn't a leap year; 5787 is.
        assertEquals("י״ד אדר", HebrewDate.of(date(2026, 3, 3)))
        assertEquals("י״ד אדר", HebrewDate.of(civil(5786, HebrewCalendar.ADAR, 14)))
        assertEquals("א׳ אדר א׳", HebrewDate.of(civil(5787, HebrewCalendar.ADAR_1, 1)))
        assertEquals("י״ד אדר ב׳", HebrewDate.of(civil(5787, HebrewCalendar.ADAR, 14)))
        assertEquals("ט״ו ניסן", HebrewDate.of(civil(5787, HebrewCalendar.NISAN, 15)))
    }

    @Test
    fun numerals() {
        assertEquals("ח׳", HebrewDate.numeral(8))
        assertEquals("י״א", HebrewDate.numeral(11))
        assertEquals("ט״ו", HebrewDate.numeral(15))
        assertEquals("ט״ז", HebrewDate.numeral(16))
        assertEquals("כ׳", HebrewDate.numeral(20))
        assertEquals("כ״ט", HebrewDate.numeral(29))
        assertEquals("תשפ״ז", HebrewDate.numeral(787))
        assertEquals("תשט״ו", HebrewDate.numeral(715))
    }

    @Test
    fun hebrewMonths() {
        // Cheshvan 5787 began on Oct 12, 2026 (Oct 17 is ו׳ חשון).
        val cheshvan = HebrewMonth.of(date(2026, 10, 17))
        assertEquals(date(2026, 10, 12), cheshvan.first)
        assertEquals("חשון תשפ״ז", cheshvan.title)
        assertEquals(cheshvan.last.plusDays(1), cheshvan.next().first)
        assertEquals("כסלו תשפ״ז", cheshvan.next().title)
        assertEquals("תשרי תשפ״ז", cheshvan.previous().title)
        assertEquals(30, cheshvan.previous().length)
        // 5787 is a leap year: Adar I and Adar II, one after the other.
        val adarI = HebrewMonth.of(civil(5787, HebrewCalendar.ADAR_1, 10))
        assertEquals("אדר א׳ תשפ״ז", adarI.title)
        assertEquals("אדר ב׳ תשפ״ז", adarI.next().title)
    }

    @Test
    fun theCalendarInHebrewMonths() {
        val period = app.wakesteps.ui.CalendarPeriod.hebrew(date(2026, 10, 17))
        assertEquals("חשון תשפ״ז", period.title)
        assertEquals(date(2026, 10, 12), period.first)
        assertEquals("א׳", period.labels.first())
        assertEquals("ט״ו", period.labels[14])
        assertEquals(java.time.DayOfWeek.SUNDAY, period.weekStart)
        assertEquals(listOf(java.time.YearMonth.of(2026, 10), java.time.YearMonth.of(2026, 11)), period.months)
        assertEquals("כסלו תשפ״ז", app.wakesteps.ui.CalendarPeriod.hebrew(period.next).title)
    }
}
