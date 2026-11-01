package app.wakesteps.core

import com.kosherjava.zmanim.ZmanimCalendar
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.kosherjava.zmanim.util.GeoLocation
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.GregorianCalendar
import java.util.TimeZone

/** Where the user is, for Shabbos times. Roughly is plenty: a mile or two moves sunset by seconds. */
data class Place(val latitude: Double, val longitude: Double, val zone: ZoneId, val name: String? = null) {
    /** In Israel, Yom Tov is one day instead of two. */
    val inIsrael: Boolean get() = zone.id == "Asia/Jerusalem" || zone.id == "Asia/Tel_Aviv" || zone.id == "Israel"

    companion object {
        /** Until the phone says where the user is: Cincinnati, where Chabad.org's times for 45237 are worked out. */
        val CINCINNATI = Place(39.19, -84.45, ZoneId.of("America/New_York"), "Cincinnati")
    }
}

/** Where the phone found the user, and when. */
data class PlaceFix(val at: Instant, val place: Place)

/** A day of Shabbos or Yom Tov: its date and its name, and the week's parsha on a plain Shabbos. */
data class RestDay(val date: LocalDate, val name: String, val parsha: String? = null)

/**
 * Shabbos or Yom Tov, or several of them in a row taken together: from sunset (shkiah) the evening
 * before the first day to nightfall after the last, as Chabad times them (the sun 8.5° below the
 * horizon), each to the nearest minute as Chabad.org prints them.
 */
data class RestBlock(val start: Instant, val end: Instant, val days: List<RestDay>) {
    /** "Shabbos · Noach", "Rosh Hashanah", "Sukkos · Shabbos". */
    val title: String
        get() {
            val names = days.map { it.name }.distinct()
            val parsha = days.singleOrNull()?.parsha
            return if (parsha != null) "${names.single()} · $parsha" else names.joinToString(" · ")
        }

    /** Just Shabbos (not Yom Tov). */
    val justShabbos: Boolean get() = days.all { it.name == SHABBOS }

    operator fun contains(t: Instant): Boolean = t >= start && t < end

    companion object {
        const val SHABBOS = "Shabbos"
    }
}

/**
 * When Shabbos and Yom Tov are: every Shabbos, and the days of Yom Tov on which work is forbidden
 * (Rosh Hashanah, Yom Kippur, the first days of Sukkos, Shemini Atzeres and Simchas Torah, the
 * first and last days of Pesach, Shavuos), two days of each outside Israel. Times are worked out
 * where the user was the day before each one began.
 */
object RestDays {

    private val formatter = HebrewDateFormatter()

    /**
     * Where the user was by the end of [date], from [fixes] (newest first): the latest place found
     * by then, else the first one ever found, else Cincinnati.
     */
    fun placeOn(fixes: List<PlaceFix>, date: LocalDate): Place =
        fixes.firstOrNull { it.at.isBefore(date.plusDays(1).atStartOfDay(it.place.zone).toInstant()) }?.place
            ?: fixes.lastOrNull()?.place
            ?: Place.CINCINNATI

    /** [date] as a day of Shabbos or Yom Tov, or null if it's neither. */
    fun day(date: LocalDate, inIsrael: Boolean): RestDay? {
        val cal = JewishCalendar(date).apply { this.inIsrael = inIsrael }
        if (!cal.isAssurBemelacha) return null
        val yomTov = when (cal.yomTovIndex) {
            JewishCalendar.ROSH_HASHANA -> "Rosh Hashanah"
            JewishCalendar.YOM_KIPPUR -> "Yom Kippur"
            JewishCalendar.SUCCOS -> "Sukkos"
            JewishCalendar.SHEMINI_ATZERES -> "Shemini Atzeres"
            JewishCalendar.SIMCHAS_TORAH -> "Simchas Torah"
            JewishCalendar.PESACH -> "Pesach"
            JewishCalendar.SHAVUOS -> "Shavuos"
            else -> null
        }
        if (yomTov != null) return RestDay(date, yomTov)
        val parsha = cal.parshah.takeIf { it != JewishCalendar.Parsha.NONE }?.let { formatter.formatParsha(cal) }
        return RestDay(date, RestBlock.SHABBOS, parsha?.takeIf { it.isNotBlank() })
    }

    /**
     * Every block with a day from [from] to [to], timed at [placeOn] the evening it began (no one
     * travels during one). Blocks running past either end are whole.
     */
    fun blocks(from: LocalDate, to: LocalDate, placeOn: (LocalDate) -> Place): List<RestBlock> {
        val out = mutableListOf<RestBlock>()
        var run = mutableListOf<RestDay>()
        // Yom Tov and Shabbos together are at most three days in a row.
        var d = from.minusDays(3)
        val last = to.plusDays(3)
        while (!d.isAfter(last)) {
            val day = day(d, placeOn(d.minusDays(1)).inIsrael)
            if (day != null) {
                run += day
            } else if (run.isNotEmpty()) {
                out += block(run, placeOn(run.first().date.minusDays(1)))
                run = mutableListOf()
            }
            d = d.plusDays(1)
        }
        if (run.isNotEmpty()) out += block(run, placeOn(run.first().date.minusDays(1)))
        return out.filter { !it.days.last().date.isBefore(from) && !it.days.first().date.isAfter(to) }
    }

    private fun block(days: List<RestDay>, place: Place): RestBlock {
        val eve = days.first().date.minusDays(1)
        val last = days.last().date
        val start = zmanim(eve, place).seaLevelSunset?.toInstant()
            ?: eve.atTime(18, 0).atZone(place.zone).toInstant()
        val end = zmanim(last, place).tzais?.toInstant()
            ?: last.atTime(20, 0).atZone(place.zone).toInstant()
        return RestBlock(start.toNearestMinute(), end.toNearestMinute(), days.toList())
    }

    private fun Instant.toNearestMinute(): Instant = plusSeconds(30).truncatedTo(ChronoUnit.MINUTES)

    /** KosherJava's times for [date] at [place]: sea-level sunset, and tzais at 8.5°. */
    private fun zmanim(date: LocalDate, place: Place): ZmanimCalendar {
        val zone = TimeZone.getTimeZone(place.zone)
        return ZmanimCalendar(GeoLocation(place.name ?: "", place.latitude, place.longitude, zone)).apply {
            calendar = GregorianCalendar(zone).apply {
                clear()
                set(date.year, date.monthValue - 1, date.dayOfMonth, 12, 0)
            }
        }
    }

    /**
     * [windows] (oldest first) with Shabbos and Yom Tov cut out of them: a day running into one
     * ends as it begins ([EndReason.REST]), what's left after it starts as it ends
     * ([StartReason.AFTER_REST]), and days wholly inside one are dropped.
     */
    fun clip(windows: List<DayWindow>, blocks: List<RestBlock>, now: Instant): List<DayWindow> {
        if (blocks.isEmpty()) return windows
        return windows.flatMap { w -> blocks.fold(listOf(w)) { pieces, b -> pieces.flatMap { cut(it, b, now) } } }
    }

    private fun cut(w: DayWindow, b: RestBlock, now: Instant): List<DayWindow> {
        val end = w.endOr(now)
        if (!b.end.isAfter(w.start) || !b.start.isBefore(end)) return listOf(w)
        val out = mutableListOf<DayWindow>()
        if (b.start.isAfter(w.start)) {
            out += w.copy(end = b.start, endReason = EndReason.REST, naps = w.naps.filter { it.start.isBefore(b.start) })
        }
        if (b.end.isBefore(end)) {
            out += w.copy(start = b.end, startReason = StartReason.AFTER_REST, wokeFrom = null, naps = w.naps.filter { !it.start.isBefore(b.end) })
        }
        return out
    }
}
