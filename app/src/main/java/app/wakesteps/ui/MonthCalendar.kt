package app.wakesteps.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.wakesteps.R
import app.wakesteps.core.Format
import app.wakesteps.core.HebrewDate
import app.wakesteps.core.HebrewMonth
import app.wakesteps.core.RestBlock
import app.wakesteps.data.CalendarDay
import app.wakesteps.data.DaySummary
import app.wakesteps.ui.theme.AppType
import app.wakesteps.ui.theme.Palette
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.abs

/** What the calendar shows: a month of the everyday calendar, or of the Hebrew one. */
class CalendarPeriod private constructor(
    val dates: List<LocalDate>,
    /** "November 2026", or "חשון תשפ״ז". */
    val title: String,
    /** Under a Hebrew month, the everyday dates it runs over: "Oct 12 – Nov 9". */
    val span: String?,
    /** Each day's number: "12", or "י״ב". */
    val labels: List<String>,
    val weekStart: DayOfWeek,
    /** The weekdays' initials from [weekStart]: S M T…, or א ב ג… */
    val weekdays: List<String>,
    /** A date in the month before it, and in the one after, to move to. */
    val previous: LocalDate,
    val next: LocalDate,
    val hebrew: Boolean,
) {
    val first: LocalDate get() = dates.first()
    val last: LocalDate get() = dates.last()

    /** The everyday months it runs over, to read. */
    val months: List<YearMonth> get() = listOf(YearMonth.from(first), YearMonth.from(last)).distinct()

    companion object {
        /** The everyday month [date] is in, its weeks as the phone starts them. */
        fun everyday(date: LocalDate, locale: Locale = Locale.getDefault()): CalendarPeriod {
            val month = YearMonth.from(date)
            val week = WeekFields.of(locale).firstDayOfWeek
            return CalendarPeriod(
                (1..month.lengthOfMonth()).map { month.atDay(it) },
                "${month.month.getDisplayName(TextStyle.FULL, locale)} ${month.year}",
                null,
                (1..month.lengthOfMonth()).map { it.toString() },
                week,
                (0L until 7L).map { week.plus(it).getDisplayName(TextStyle.NARROW, locale) },
                month.minusMonths(1).atDay(15),
                month.plusMonths(1).atDay(15),
                hebrew = false,
            )
        }

        /** The Hebrew month [date] is in, in Hebrew, its weeks from Sunday to Shabbos. */
        fun hebrew(date: LocalDate): CalendarPeriod {
            val month = HebrewMonth.of(date)
            val short = DateTimeFormatter.ofPattern("MMM d")
            return CalendarPeriod(
                month.dates,
                month.title,
                "${short.format(month.first)} – ${short.format(month.last)}",
                (1..month.length).map { HebrewDate.numeral(it) },
                DayOfWeek.SUNDAY,
                listOf("א", "ב", "ג", "ד", "ה", "ו", "ש"),
                month.previous().first.plusDays(14),
                month.next().first.plusDays(14),
                hebrew = true,
            )
        }
    }
}

/** One day in the calendar. */
class MonthCell(
    val date: LocalDate,
    /** Its number as the calendar writes it: "12", or "י״ב". */
    val label: String,
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
 * [period]'s days from the awake [days] (each on the date it began): Shabbos and Yom Tov show as
 * such, with motzei Shabbos (the evening after) folded into its last day, as in History.
 */
fun awakeMonthCells(period: CalendarPeriod, days: List<DaySummary>, blocks: List<RestBlock>, today: LocalDate, zone: ZoneId): List<MonthCell> {
    val now = Instant.now()
    return period.dates.mapIndexed { i, date ->
        val rest = blocks.firstOrNull { b -> b.days.any { it.date == date } }
        if (rest != null) {
            val motzei = if (date == rest.days.last().date) days.firstOrNull { it.window.start == rest.end } else null
            MonthCell(date, period.labels[i], motzei?.totals?.steps, motzei, rest, date == today, date.isAfter(today))
        } else {
            // Rarely two days begin on one date (a day started by hand): the longer one.
            val day = days.filter { it.window.start.atZone(zone).toLocalDate() == date }
                .maxByOrNull { Duration.between(it.window.start, it.window.endOr(now)) }
            MonthCell(date, period.labels[i], day?.totals?.steps, day, null, date == today, date.isAfter(today))
        }
    }
}

/** [period]'s midnight-to-midnight days, Shabbos and Yom Tov showing as such. */
fun calendarMonthCells(period: CalendarPeriod, calendar: List<CalendarDay>, blocks: List<RestBlock>, today: LocalDate): List<MonthCell> =
    period.dates.mapIndexed { i, date ->
        val rest = blocks.firstOrNull { b -> b.days.any { it.date == date } }
        val steps = calendar.firstOrNull { it.date == date }?.totals?.steps?.takeUnless { date.isAfter(today) }
        MonthCell(date, period.labels[i], steps, null, rest, date == today, date.isAfter(today))
    }

/**
 * A month at a glance, of the everyday calendar or the Hebrew one: a ring for each day, filling
 * toward the goal and gold once it's made, candles for Shabbos and Yom Tov. The steps a day and
 * the days at goal (Shabbos, Yom Tov and today left out) above it. Arrows or a swipe move between
 * months, up to a year back; a day opens its page.
 */
@Composable
fun MonthPanel(
    period: CalendarPeriod,
    cells: List<MonthCell>,
    goal: Int,
    onGo: (LocalDate) -> Unit,
    onHebrew: (Boolean) -> Unit,
    onOpenDay: (DaySummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = remember { LocalDate.now() }
    val counted = cells.filter { it.rest == null && !it.today && it.steps != null }
    val canGoBack = period.first.isAfter(today.minusMonths(12))
    val canGoOn = period.last.isBefore(today)
    Panel(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(period.title, style = MaterialTheme.typography.titleMedium, color = Palette.text)
                period.span?.let { Caption(it, style = MaterialTheme.typography.bodySmall, color = Palette.faint) }
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
            MonthArrow(Icons.AutoMirrored.Filled.KeyboardArrowLeft, enabled = canGoBack) { onGo(period.previous) }
            Spacer(Modifier.width(8.dp))
            MonthArrow(Icons.AutoMirrored.Filled.KeyboardArrowRight, enabled = canGoOn) { onGo(period.next) }
        }
        Spacer(Modifier.height(12.dp))
        CalendarSwitch(period.hebrew, onHebrew)
        Spacer(Modifier.height(16.dp))
        // A Hebrew month reads right to left, as a luach does: Sunday on the right, Shabbos on the left.
        val direction = if (period.hebrew) LayoutDirection.Rtl else LocalLayoutDirection.current
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
            MonthDays(period, cells, goal, today, onGo, onOpenDay)
        }
    }
}

/** The weekdays' initials, then the days under them, sliding from month to month. */
@Composable
private fun MonthDays(
    period: CalendarPeriod,
    cells: List<MonthCell>,
    goal: Int,
    today: LocalDate,
    onGo: (LocalDate) -> Unit,
    onOpenDay: (DaySummary) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            for (initial in period.weekdays) {
                Text(
                    initial,
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.faint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        AnimatedContent(
            targetState = period to cells,
            transitionSpec = {
                val forward = targetState.first.first > initialState.first.first
                (fadeIn(tween(240)) + slideInHorizontally(tween(320)) { if (forward) it / 6 else -it / 6 })
                    .togetherWith(fadeOut(tween(160)) + slideOutHorizontally(tween(320)) { if (forward) -it / 6 else it / 6 })
            },
            contentKey = { it.first.first to it.first.hebrew },
            label = "month",
        ) { (shown, shownCells) ->
            MonthGrid(shown, shownCells, goal, onOpenDay, Modifier.pointerInput(shown.first, shown.hebrew) {
                var dragged = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = {
                        if (abs(dragged) > 60.dp.toPx()) {
                            if (dragged < 0 && shown.last.isBefore(today)) onGo(shown.next)
                            if (dragged > 0 && shown.first.isAfter(today.minusMonths(12))) onGo(shown.previous)
                        }
                    },
                ) { _, amount -> dragged += amount }
            })
        }
    }
}

/** English or Hebrew months: a small two-way switch. */
@Composable
private fun CalendarSwitch(hebrew: Boolean, onHebrew: (Boolean) -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, glassEdge(), CircleShape)
            .padding(3.dp),
    ) {
        for ((label, value) in listOf(stringResource(R.string.calendar_english) to false, stringResource(R.string.calendar_hebrew) to true)) {
            val selected = value == hebrew
            val source = remember { MutableInteractionSource() }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) Palette.text else Palette.muted,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (selected) Palette.cardHigh else Color.Transparent)
                    .clickable(source, indication = null) { if (!selected) onHebrew(value) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
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
private fun MonthGrid(period: CalendarPeriod, cells: List<MonthCell>, goal: Int, onOpenDay: (DaySummary) -> Unit, modifier: Modifier) {
    // Blank places before the first day, so it falls under its weekday.
    val lead = (period.first.dayOfWeek.value - period.weekStart.value + 7) % 7
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
            cell.label,
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
