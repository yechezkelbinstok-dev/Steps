package app.wakesteps.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.wakesteps.R
import app.wakesteps.core.Format
import app.wakesteps.core.PlaceFix
import app.wakesteps.core.RestBlock
import app.wakesteps.core.SleepNight
import app.wakesteps.data.DaySummary
import app.wakesteps.ui.theme.AppType
import app.wakesteps.ui.theme.Palette
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Candlelight for Shabbos and Yom Tov: a deep night with a warm glow from the top. */
private val RestFill = Brush.verticalGradient(listOf(Color(0xF0241B3A), Color(0xEB0D0F2A)))

private val CandleGlow = Color(0xFFFFC861)

/**
 * "Motzei Shabbos" for a day that began at [start] as Shabbos ended (on a Saturday night, Yom Tov
 * or not), else "Motzei Yom Tov".
 */
@Composable
fun motzei(start: Instant): String = stringResource(
    if (start.atZone(ZoneId.systemDefault()).dayOfWeek == DayOfWeek.SATURDAY) R.string.rest_motzei_shabbos else R.string.rest_motzei_yom_tov
)

/** "Fri 5:31 PM – Sat 6:12 PM". */
fun restTimes(block: RestBlock): String {
    val day = DateTimeFormatter.ofPattern("EEE")
    val zone = ZoneId.systemDefault()
    return "${day.format(block.start.atZone(zone))} ${Format.time(block.start)} – ${day.format(block.end.atZone(zone))} ${Format.time(block.end)}"
}

/** Two lit candles in a soft round badge. */
@Composable
fun CandlesBadge(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(CandleGlow.copy(alpha = 0.22f), Color.White.copy(alpha = 0.04f))))
            .border(1.dp, glassEdge(), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.ic_candles), contentDescription = null, modifier = Modifier.size(size * 0.56f))
    }
}

/**
 * Two small lit candles standing on [baseline] at [x], [height] tall, in [wax]: Shabbos or Yom
 * Tov in a chart, where a bar would be.
 */
fun DrawScope.drawCandles(x: Float, baseline: Float, height: Float, wax: Color, alpha: Float = 1f) {
    val stemW = height * 0.16f
    val stemH = height * 0.58f
    val gap = height * 0.36f
    val flameH = height * 0.34f
    val flameW = stemW * 1.5f
    for (cx in listOf(x - gap / 2, x + gap / 2)) {
        drawRoundRect(
            wax.copy(alpha = 0.75f * alpha),
            Offset(cx - stemW / 2, baseline - stemH),
            Size(stemW, stemH),
            CornerRadius(stemW / 3),
        )
        val bottom = baseline - stemH - height * 0.06f
        val top = bottom - flameH
        // A glow, then the flame: round at the bottom, drawn up to a point.
        drawCircle(CandleGlow.copy(alpha = 0.22f * alpha), flameH * 0.75f, Offset(cx, bottom - flameH * 0.35f))
        val flame = Path().apply {
            moveTo(cx, top)
            cubicTo(cx + flameW * 0.9f, top + flameH * 0.45f, cx + flameW * 0.7f, bottom, cx, bottom)
            cubicTo(cx - flameW * 0.7f, bottom, cx - flameW * 0.9f, top + flameH * 0.45f, cx, top)
            close()
        }
        drawPath(flame, CandleGlow.copy(alpha = alpha))
    }
}

/** On the day it begins: "Shabbos at 5:31 PM" (or "tomorrow at"), quietly, under the ring. */
@Composable
fun RestSoonNote(block: RestBlock, now: Instant, modifier: Modifier = Modifier) {
    val zone = ZoneId.systemDefault()
    val today = block.start.atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate()
    val name = block.days.first().name
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.ic_candles), contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Caption(
            stringResource(if (today) R.string.rest_soon else R.string.rest_soon_tomorrow, name, Format.time(block.start)),
            color = Palette.muted,
        )
    }
}

/**
 * Shabbos or Yom Tov in a list of days or nights: its name and its times, and in History, motzei
 * Shabbos with it (its steps, opening its page), not as a day of its own.
 */
@Composable
fun RestRow(block: RestBlock, modifier: Modifier = Modifier, motzei: DaySummary? = null, onOpen: (() -> Unit)? = null) {
    Panel(modifier.fillMaxWidth(), onClick = onOpen, padding = PaddingValues(16.dp), fill = RestFill) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CandlesBadge(46.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(block.title, style = MaterialTheme.typography.titleMedium, color = Palette.text, maxLines = 1)
                Caption(restTimes(block), style = MaterialTheme.typography.bodySmall)
            }
            if (motzei != null && motzei.totals.steps > 0) {
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(Format.number(motzei.totals.steps), style = AppType.number(24f), color = Palette.text)
                    Caption(motzei(motzei.window.start), color = Palette.faint, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/**
 * Settings: the next (or current) Shabbos or Yom Tov and its times, where they're worked out for,
 * and how the app finds out.
 */
@Composable
fun RestPanel(
    next: RestBlock?,
    now: Instant,
    place: PlaceFix?,
    access: LocationAccess,
    onAllow: () -> Unit,
    onAllowAlways: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Panel(modifier.fillMaxWidth()) {
        Eyebrow(stringResource(R.string.set_rest))
        if (next != null) {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CandlesBadge(44.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Eyebrow(stringResource(if (now in next) R.string.rest_now else R.string.rest_next), color = Palette.faint)
                    Text(next.title, style = MaterialTheme.typography.titleMedium, color = Palette.text, maxLines = 1)
                    Caption(restTimes(next), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            place?.place?.name?.let { stringResource(R.string.rest_place, it) }
                ?: if (place != null) stringResource(R.string.rest_place_here) else stringResource(R.string.rest_place, "Cincinnati"),
            style = MaterialTheme.typography.bodyLarge,
            color = Palette.text,
        )
        Caption(
            place?.let { stringResource(R.string.rest_place_found, Format.ago(it.at, now)) } ?: stringResource(R.string.rest_place_default),
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(
                when (access) {
                    LocationAccess.NONE -> R.string.rest_location_off
                    LocationAccess.WHILE_OPEN -> R.string.rest_location_while
                    LocationAccess.ALWAYS -> R.string.rest_location_always
                }
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.muted,
        )
        when (access) {
            LocationAccess.NONE -> {
                Spacer(Modifier.height(12.dp))
                SoftButton(stringResource(R.string.rest_allow), onAllow)
            }
            LocationAccess.WHILE_OPEN -> {
                Spacer(Modifier.height(12.dp))
                SoftButton(stringResource(R.string.rest_allow_always), onAllowAlways)
            }
            LocationAccess.ALWAYS -> Unit
        }
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.rest_note), style = MaterialTheme.typography.bodySmall, color = Palette.faint)
    }
}

/** The days in History with each Shabbos and Yom Tov in its place. */
sealed interface DayEntry {
    class Day(val day: DaySummary) : DayEntry
    /** [motzei] is the evening after it (motzei Shabbos), if the app counted it. */
    class Rest(val block: RestBlock, val motzei: DaySummary?) : DayEntry
}

/**
 * [history] (newest first) with each Shabbos and Yom Tov from [blocks] that's begun put in among
 * the days, motzei Shabbos folded into it: one entry, left out of goals and averages.
 */
fun withRest(history: List<DaySummary>, blocks: List<RestBlock>, now: Instant): List<DayEntry> {
    if (history.isEmpty()) return emptyList()
    val oldest = history.last().window.start
    val shown = blocks.filter { it.start <= now && it.end > oldest }
    val motzei = shown.associateWith { b -> history.firstOrNull { it.window.start == b.end } }
    val folded = motzei.values.filterNotNull().toSet()
    val entries = history.filter { it !in folded }.map { it.window.start to DayEntry.Day(it) } +
        shown.map { it.start to DayEntry.Rest(it, motzei[it]) }
    return entries.sortedByDescending { it.first }.map { it.second }
}


/** A night in the Sleep tab, or a Shabbos or Yom Tov ([rest]) when the band was off. */
class NightSlot(val night: SleepNight?, val rest: RestBlock?)

/** [nights] (newest first) with each Shabbos and Yom Tov from [blocks] that's begun put in among them. */
fun withRestNights(nights: List<SleepNight>, blocks: List<RestBlock>, now: Instant): List<NightSlot> {
    if (nights.isEmpty()) return emptyList()
    val oldest = nights.last().start
    val entries = nights.map { it.end to NightSlot(it, null) } +
        blocks.filter { it.start <= now && it.end > oldest }.map { it.end to NightSlot(null, it) }
    return entries.sortedByDescending { it.first }.map { it.second }
}
