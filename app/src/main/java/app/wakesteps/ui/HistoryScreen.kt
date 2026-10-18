package app.wakesteps.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.wakesteps.R
import app.wakesteps.core.DayWindow
import app.wakesteps.core.EndReason
import app.wakesteps.core.Format
import app.wakesteps.core.Overrides
import app.wakesteps.core.StartReason
import app.wakesteps.data.AppSettings
import app.wakesteps.data.DayTotals
import app.wakesteps.ui.theme.Palette
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One row of history, whichever way days are counted. */
private class HistoryRow(
    val key: Long,
    val title: String,
    val detail: String?,
    val tags: String?,
    val totals: DayTotals,
    /** Still going (today), so left out of averages. */
    val partial: Boolean,
    /** Day-of-week letter for the week chart. */
    val initial: String,
    val onClick: (() -> Unit)?,
)

@Composable
fun HistoryScreen(
    state: UiState,
    settings: AppSettings,
    overrides: Overrides,
    onAdjust: (DayWindow) -> Unit,
    bottomPadding: Dp,
) {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    val rows = if (mode == 0) awakeRows(state, overrides, onAdjust) else calendarRows(state)
    LazyColumn(
        Modifier.statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenTitle(stringResource(R.string.tab_history)) }
        item {
            PillToggle(
                listOf(stringResource(R.string.history_awake_days), stringResource(R.string.history_calendar_days)),
                selected = mode,
                onSelect = { mode = it },
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            )
        }
        if (rows.isEmpty()) {
            item {
                Text(
                    stringResource(if (state.loading) R.string.loading else R.string.history_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Palette.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                )
            }
        } else {
            item { WeekCard(rows.take(7).reversed(), settings.goal) }
            items(rows, key = { it.key }) { DayRowCard(it, settings) }
        }
    }
}

@Composable
private fun awakeRows(state: UiState, overrides: Overrides, onAdjust: (DayWindow) -> Unit): List<HistoryRow> {
    val now = Instant.now()
    return state.history.map { day ->
        val w = day.window
        val end = if (w.isOngoing) stringResource(R.string.now) else Format.time(w.endOr(now))
        val tags = buildList {
            if (overrides.touches(w, now)) add(stringResource(R.string.tag_edited))
            if (w.startReason == StartReason.FALLBACK) add(stringResource(R.string.tag_fallback_start))
            if (w.endReason == EndReason.FALLBACK) add(stringResource(R.string.tag_no_sleep))
            if (w.naps.isNotEmpty()) add(pluralStringResource(R.plurals.tag_naps, w.naps.size, w.naps.size))
        }
        HistoryRow(
            key = w.start.toEpochMilli(),
            title = if (w.isOngoing) stringResource(R.string.history_today) else Format.day(w.start),
            detail = "${Format.time(w.start)} – $end  ·  ${Format.duration(Duration.between(w.start, w.endOr(now)))}",
            tags = tags.joinToString("  ·  ").ifEmpty { null },
            totals = day.totals,
            partial = w.isOngoing,
            initial = initial(w.start.atZone(ZoneId.systemDefault()).toLocalDate()),
            onClick = { onAdjust(w) },
        )
    }
}

@Composable
private fun calendarRows(state: UiState): List<HistoryRow> {
    val today = LocalDate.now()
    val fmt = DateTimeFormatter.ofPattern("EEE, MMM d")
    return state.calendar.map { day ->
        HistoryRow(
            key = day.date.toEpochDay(),
            title = if (day.date == today) stringResource(R.string.history_today_so_far) else day.date.format(fmt),
            detail = null,
            tags = null,
            totals = day.totals,
            partial = day.date == today,
            initial = initial(day.date),
            onClick = null,
        )
    }
}

private fun initial(date: LocalDate) = DateTimeFormatter.ofPattern("EEEEE").format(date)

/**
 * The last seven days as bars against a dashed goal line, days that hit it lit up, with the
 * average of the finished ones.
 */
@Composable
private fun WeekCard(week: List<HistoryRow>, goal: Int) {
    val done = week.filterNot { it.partial }.ifEmpty { week }
    val average = done.sumOf { it.totals.steps } / done.size
    AppCard(Modifier.fillMaxWidth()) {
        CardLabel(stringResource(R.string.history_week))
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            BigValue(Format.number(average), size = 34)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.history_per_day),
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.muted,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        Bars(
            week.map { it.totals.steps },
            Modifier.fillMaxWidth().height(96.dp),
            highlight = { week[it].totals.steps >= goal },
            line = goal.toLong(),
            barFraction = 0.42f,
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            week.forEach {
                Text(
                    it.initial,
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.faint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DayRowCard(row: HistoryRow, settings: AppSettings) {
    AppCard(Modifier.fillMaxWidth(), onClick = row.onClick, padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepsRing(RingProgress(row.totals.steps, settings.goal), 44.dp, strokeFraction = 0.15f, glow = false)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(row.title, style = MaterialTheme.typography.titleMedium, color = Palette.text, maxLines = 1)
                row.detail?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.muted, maxLines = 1)
                }
                row.tags?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = Palette.accent, maxLines = 1)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                BigValue(Format.number(row.totals.steps), size = 24)
                val extras = listOfNotNull(
                    row.totals.calories?.let { Format.calories(it) },
                    row.totals.distanceMeters?.let { Format.distance(it, settings.distanceUnit) },
                )
                if (extras.isNotEmpty()) {
                    Text(extras.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall, color = Palette.faint, maxLines = 1)
                }
            }
        }
    }
}
