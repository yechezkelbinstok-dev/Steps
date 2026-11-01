package app.wakesteps.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.wakesteps.R
import app.wakesteps.core.Format
import app.wakesteps.core.RestBlock
import app.wakesteps.data.CalendarDay
import app.wakesteps.data.DaySummary
import app.wakesteps.ui.theme.AppType
import app.wakesteps.ui.theme.Palette
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.abs

/** One day of a month in its calendar. */
class MonthCell(
    val date: LocalDate,
    /** Its steps; null for days still to come, or with nothing read. */
    val steps: Long?,
    /** The awake day it opens, if there is one. */
    val open: DaySummary?,
    /** Shabbos or Yom Tov: candles instead of a ring, and not counted. */
    val rest: RestBlock?,
    /** Today, still going: not counted yet. */
    val today: Boolean,
    /** Still to come: just its number. */
    val future: Boolean = false,
)

/**
 * [month]'s days from the awake [days] (each on the date it began): Shabbos and Yom Tov show as
 * such, with motzei Shabbos (the evening after) folded into its last day, as in History.
 */
fun awakeMonthCells(month: YearMonth, days: List<DaySummary>, blocks: List<RestBlock>, today: LocalDate, zone: ZoneId): List<MonthCell> {
    val now = Instant.now()
    return (1..month.lengthOfMonth()).map { d ->
        val date = month.atDay(d)
        val rest = blocks.firstOrNull { b -> b.days.any { it.date == date } }
        if (rest != null) {
            val motzei = if (date == rest.days.last().date) days.firstOrNull { it.window.start == rest.end } else null
            MonthCell(date, motzei?.totals?.steps, motzei, rest, date == today, date.isAfter(today))
        } else {
            // Rarely two days begin on one date (a day started by hand): the longer one.
            val day = days.filter { it.window.start.atZone(zone).toLocalDate() == date }
                .maxByOrNull { Duration.between(it.window.start, it.window.endOr(now)) }
            MonthCell(date, day?.totals?.steps, day, null, date == today, date.isAfter(today))
        }
    }
}

/** [month]'s midnight-to-midnight days, Shabbos and Yom Tov showing as such. */
fun calendarMonthCells(month: YearMonth, calendar: List<CalendarDay>, blocks: List<RestBlock>, today: LocalDate): List<MonthCell> =
    (1..month.lengthOfMonth()).map { d ->
        val date = month.atDay(d)
        val rest = blocks.firstOrNull { b -> b.days.any { it.date == date } }
        val steps = calendar.firstOrNull { it.date == date }?.totals?.steps?.takeUnless { date.isAfter(today) }
        MonthCell(date, steps, null, rest, date == today, date.isAfter(today))
    }

/**
 * A month at a glance: a ring for each day, filling toward the goal and gold once it's made,
 * candles for Shabbos and Yom Tov. The steps a day and the days at goal (Shabbos, Yom Tov and
 * today left out) above it. Arrows or a swipe move between months; a day opens its page.
 */
@Composable
fun MonthPanel(
    month: YearMonth,
    cells: List<MonthCell>,
    goal: Int,
    onMonth: (YearMonth) -> Unit,
    onOpenDay: (DaySummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val thisMonth = remember { YearMonth.now() }
    val counted = cells.filter { it.rest == null && !it.today && it.steps != null }
    Panel(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.text,
                )
                Caption(
                    if (counted.isEmpty()) " " else stringResource(
                        R.string.month_summary,
                        Format.number(counted.sumOf { it.steps!! } / counted.size),
                        counted.count { it.steps!! >= goal },
                        counted.size,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            MonthArrow(Icons.AutoMirrored.Filled.KeyboardArrowLeft, enabled = month > thisMonth.minusMonths(12)) { onMonth(month.minusMonths(1)) }
            Spacer(Modifier.width(8.dp))
            MonthArrow(Icons.AutoMirrored.Filled.KeyboardArrowRight, enabled = month < thisMonth) { onMonth(month.plusMonths(1)) }
        }
        Spacer(Modifier.height(16.dp))
        val week = WeekFields.of(Locale.getDefault())
        Row(Modifier.fillMaxWidth()) {
            for (i in 0 until 7) {
                Text(
                    week.firstDayOfWeek.plus(i.toLong()).getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.faint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        AnimatedContent(
            targetState = month to cells,
            transitionSpec = {
                val forward = targetState.first > initialState.first
                (fadeIn(tween(240)) + slideInHorizontally(tween(320)) { if (forward) it / 6 else -it / 6 })
                    .togetherWith(fadeOut(tween(160)) + slideOutHorizontally(tween(320)) { if (forward) -it / 6 else it / 6 })
            },
            contentKey = { it.first },
            label = "month",
        ) { (shown, shownCells) ->
            MonthGrid(shown, shownCells, goal, week, onOpenDay, Modifier.pointerInput(shown) {
                var dragged = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = {
                        if (abs(dragged) > 60.dp.toPx()) {
                            val next = if (dragged < 0) shown.plusMonths(1) else shown.minusMonths(1)
                            if (next <= thisMonth && next > thisMonth.minusMonths(13)) onMonth(next)
                        }
                    },
                ) { _, amount -> dragged += amount }
            })
        }
    }
}

@Composable
private fun MonthArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    RoundIconButton(onClick = { if (enabled) onClick() }, size = 36.dp) {
        Icon(icon, contentDescription = null, tint = if (enabled) Palette.text else Palette.faint.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
    }
}

/** The month's days in weeks, each day its number over its ring. */
@Composable
private fun MonthGrid(month: YearMonth, cells: List<MonthCell>, goal: Int, week: WeekFields, onOpenDay: (DaySummary) -> Unit, modifier: Modifier) {
    // Blank places before the 1st, so it falls under its weekday.
    val lead = (month.atDay(1).dayOfWeek.value - week.firstDayOfWeek.value + 7) % 7
    val places: List<MonthCell?> = List(lead) { null } + cells
    Column(modifier.fillMaxWidth()) {
        places.chunked(7).forEachIndexed { row, days ->
            if (row > 0) Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                for (i in 0 until 7) {
                    val cell = days.getOrNull(i)
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (cell != null) DayPlace(cell, goal, row * 7 + i, onOpenDay)
                    }
                }
            }
        }
    }
}

/** A day: its number, then its ring (or candles), tappable when it has a day to open. */
@Composable
private fun DayPlace(cell: MonthCell, goal: Int, index: Int, onOpenDay: (DaySummary) -> Unit) {
    val open = cell.open
    val source = remember { MutableInteractionSource() }
    Column(
        (if (open != null) Modifier.clickable(source, indication = null) { onOpenDay(open) } else Modifier).padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            cell.date.dayOfMonth.toString(),
            style = AppType.eyebrow.copy(
                fontWeight = if (cell.today) FontWeight.Bold else FontWeight.Medium,
                letterSpacing = AppType.eyebrow.letterSpacing * 0,
            ),
            color = when {
                cell.today -> Palette.accent
                cell.steps == null && cell.rest == null -> Palette.faint
                else -> Palette.muted
            },
        )
        Spacer(Modifier.height(4.dp))
        Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
            when {
                cell.future -> Unit
                cell.rest != null -> Image(painterResource(R.drawable.ic_candles), contentDescription = cell.rest.title, modifier = Modifier.size(22.dp))
                cell.steps != null -> StepsRing(
                    RingProgress.of(cell.steps, goal), 34.dp, strokeFraction = 0.16f, glow = false,
                    delayMillis = 60 + index * 12,
                )
                // Nothing yet, or nothing read: an empty ring, faintly.
                else -> StepsRing(0f, 34.dp, strokeFraction = 0.16f, glow = false, animate = false)
            }
        }
    }
}
