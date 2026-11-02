package app.wakesteps.core

import android.icu.util.Calendar
import android.icu.util.HebrewCalendar
import android.icu.util.TimeZone
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * A day's Hebrew date, in Hebrew: "ח׳ חשון". It's the date of the day itself (which began the
 * evening before), as a calendar shows it beside the ordinary date.
 */
object HebrewDate {

    private const val GERESH = '׳'
    private const val GERSHAYIM = '״'

    /** By ICU's month numbers: Adar I (5) only comes in a leap year, when Adar (6) is Adar II. */
    private val MONTHS = listOf(
        "תשרי", "חשון", "כסלו", "טבת", "שבט", "אדר א$GERESH", "אדר",
        "ניסן", "אייר", "סיון", "תמוז", "אב", "אלול",
    )

    fun of(date: LocalDate): String {
        val calendar = calendarOn(date)
        return "${numeral(calendar.get(Calendar.DAY_OF_MONTH))} ${monthName(calendar)}"
    }

    /** ICU's Hebrew calendar on [date] (at midday, so the date is the day's own). */
    internal fun calendarOn(date: LocalDate): HebrewCalendar = HebrewCalendar(TimeZone.GMT_ZONE).apply {
        timeInMillis = date.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    }

    /** "חשון", or in a leap year "אדר א׳" and "אדר ב׳". */
    internal fun monthName(calendar: HebrewCalendar): String {
        val year = calendar.get(Calendar.EXTENDED_YEAR)
        val month = calendar.get(Calendar.MONTH)
        val leap = (7 * year + 1) % 19 < 7
        return if (month == HebrewCalendar.ADAR && leap) "אדר ב$GERESH" else MONTHS[month]
    }

    /**
     * [n] (1 to 999) in Hebrew letters, marked as a number: a geresh after a single letter, or
     * gershayim before the last one. 15 and 16 are ט״ו and ט״ז, as they always are.
     */
    fun numeral(n: Int): String {
        require(n in 1..999) { "$n" }
        val letters = buildString {
            var hundreds = n / 100
            while (hundreds >= 4) {
                append('ת')
                hundreds -= 4
            }
            if (hundreds > 0) append("קרש"[hundreds - 1])
            when (val rest = n % 100) {
                15 -> append("טו")
                16 -> append("טז")
                else -> {
                    if (rest >= 10) append("יכלמנסעפצ"[rest / 10 - 1])
                    if (rest % 10 > 0) append("אבגדהוזחט"[rest % 10 - 1])
                }
            }
        }
        return if (letters.length == 1) "$letters$GERESH" else "${letters.dropLast(1)}$GERSHAYIM${letters.last()}"
    }
}

/**
 * A month of the Hebrew calendar, from its first day for [length] days: "חשון תשפ״ז" ([title]),
 * for the calendar in History.
 */
class HebrewMonth private constructor(val first: LocalDate, val length: Int, val title: String) {
    val last: LocalDate get() = first.plusDays(length - 1L)
    val dates: List<LocalDate> get() = (0 until length).map { first.plusDays(it.toLong()) }

    fun previous(): HebrewMonth = of(first.minusDays(1))

    fun next(): HebrewMonth = of(last.plusDays(1))

    override fun equals(other: Any?) = other is HebrewMonth && other.first == first

    override fun hashCode() = first.hashCode()

    companion object {
        /** The Hebrew month [date] falls in. */
        fun of(date: LocalDate): HebrewMonth {
            val calendar = HebrewDate.calendarOn(date)
            val day = calendar.get(Calendar.DAY_OF_MONTH)
            val length = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
            val year = calendar.get(Calendar.EXTENDED_YEAR)
            // The year without its thousands, as it's written: 5787 is תשפ״ז.
            return HebrewMonth(date.minusDays(day - 1L), length, "${HebrewDate.monthName(calendar)} ${HebrewDate.numeral(year % 1000)}")
        }
    }
}
