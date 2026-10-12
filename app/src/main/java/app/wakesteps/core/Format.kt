package app.wakesteps.core

import android.icu.util.LocaleData
import android.icu.util.ULocale
import app.wakesteps.data.DistanceUnit
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

object Format {
    private val timeFmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    private val dayFmt = DateTimeFormatter.ofPattern("EEE, MMM d")

    fun number(n: Long): String = NumberFormat.getIntegerInstance().format(n)

    fun time(i: Instant): String = timeFmt.format(i.atZone(ZoneId.systemDefault()))

    fun day(i: Instant): String = dayFmt.format(i.atZone(ZoneId.systemDefault()))

    fun duration(d: Duration): String {
        val m = d.toMinutes().coerceAtLeast(0)
        return if (m < 60) "${m}m" else "${m / 60}h ${"%02d".format(m % 60)}m"
    }

    /** "just now", "5 min ago", "2 h ago". */
    fun ago(i: Instant, now: Instant = Instant.now()): String {
        val m = Duration.between(i, now).toMinutes()
        return when {
            m < 1 -> "just now"
            m < 60 -> "$m min ago"
            m < 48 * 60 -> "${m / 60} h ago"
            else -> "${m / 1440} d ago"
        }
    }

    fun calories(kcal: Double?): String = kcal?.let { "${number(Math.round(it))} cal" } ?: "–"

    fun distance(meters: Double?, unit: DistanceUnit): String {
        if (meters == null) return "–"
        val miles = when (unit) {
            DistanceUnit.MILES -> true
            DistanceUnit.KILOMETERS -> false
            DistanceUnit.AUTO -> usesMiles()
        }
        return if (miles) "%.1f mi".format(meters / 1609.344) else "%.1f km".format(meters / 1000)
    }

    private fun usesMiles(): Boolean =
        LocaleData.getMeasurementSystem(ULocale.getDefault()) == LocaleData.MeasurementSystem.US
}
