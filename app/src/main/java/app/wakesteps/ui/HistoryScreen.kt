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
import androidx.compose.foundation.lazy.itemsIndexed
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
import app.wakesteps.core.EndReason
import app.wakesteps.core.Format
import app.wakesteps.core.Overrides
import app.wakesteps.core.StartReason
import app.wakesteps.data.AppSettings
import app.wakesteps.data.DaySummary
import app.wakesteps.data.DayTotals
import app.wakesteps.ui.theme.AppType
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
    val date: LocalDate,
    val onClick: (() -> Unit)?,
)

@Composable
fun HistoryScreen(
    state: UiState,
    settings: AppSettings,
    overrides: Overrides,
    onOpenDay: (DaySummary) -> Unit,
    bottomPadding: Dp,
    chartSelection: Int? = null,
) {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    val rows = if (mode == 0) awakeRows(state, overrides, onOpenDay) else calendarRows(state)
    LazyColumn(
        Modifier.statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.entrance(0)) {
                Eyebrow(stringResource(R.string.history_eyebrow))
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.tab_history), style = AppType.title, color = Palette.text)
            }
        }
        item {
            PillToggle(
                listOf(stringResource(R.string.history_awake_days), stringResource(R.string.history_calendar_days)),
                selected = mode,
                onSelect = { mode = it },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp).entrance(1),
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
            item {
                val week = rows.take(7).reversed().map {
                    WeekDay(
                        initial = DateTimeFormatter.ofPattern("EEEEE").format(it.date),
                        title = it.title,
                        steps = it.totals.steps,
                        partial = it.partial,
                    )
                }
                WeekPanel(week, settings.goal, Modifier.entrance(2), chartSelection)
            }
            item { Eyebrow(stringResource(R.string.history_days), Modifier.padding(start = 4.dp, top = 12.dp)) }
            itemsIndexed(rows, key = { _, it -> it.key }) { i, row ->
                DayRow(row, settings, Modifier.entrance(3 + minOf(i, 5)))
            }
        }
    }
}

@Composable
private fun awakeRows(state: UiState, overrides: Overrides, onOpenDay: (DaySummary) -> Unit): List<HistoryRow> {
    val now = Instant.now()
    return state.history.map { day ->
        val w = day.window
        val end = if (w.isOngoing) stringResource(R.string.now) else Format.time(w.endOr(now))
        HistoryRow(
            key = w.start.toEpochMilli(),
            title = if (w.isOngoing) stringResource(R.string.history_today) else Format.day(w.start),
            detail = "${Format.time(w.start)} – $end  ·  ${Format.duration(Duration.between(w.start, w.endOr(now)))}",
            tags = dayTags(day, overrides, now),
            totals = day.totals,
            partial = w.isOngoing,
            date = w.start.atZone(ZoneId.systemDefault()).toLocalDate(),
            onClick = { onOpenDay(day) },
        )
    }
}

/** "Edited · 1 nap", or null when nothing unusual happened. */
@Composable
fun dayTags(day: DaySummary, overrides: Overrides, now: Instant): String? {
    val w = day.window
    return buildList {
        if (overrides.touches(w, now)) add(stringResource(R.string.tag_edited))
        if (w.startReason == StartReason.FALLBACK) add(stringResource(R.string.tag_fallback_start))
        if (w.endReason == EndReason.FALLBACK) add(stringResource(R.string.tag_no_sleep))
        if (w.naps.isNotEmpty()) add(pluralStringResource(R.plurals.tag_naps, w.naps.size, w.naps.size))
    }.joinToString("  ·  ").ifEmpty { null }
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
            date = day.date,
            onClick = null,
        )
    }
}

@Composable
private fun DayRow(row: HistoryRow, settings: AppSettings, modifier: Modifier) {
    Panel(modifier.fillMaxWidth(), onClick = row.onClick, padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepsRing(RingProgress.of(row.totals.steps, settings.goal), 46.dp, strokeFraction = 0.15f, glow = false)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(row.title, style = MaterialTheme.typography.titleMedium, color = Palette.text, maxLines = 1)
                row.detail?.let { Caption(it, style = MaterialTheme.typography.bodySmall) }
                row.tags?.let { Caption(it, color = Palette.accent, style = MaterialTheme.typography.labelMedium) }
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(Format.number(row.totals.steps), style = AppType.number(24f), color = Palette.text)
                val extras = listOfNotNull(
                    row.totals.calories?.let { Format.calories(it) },
                    row.totals.distanceMeters?.let { Format.distance(it, settings.distanceUnit) },
                )
                if (extras.isNotEmpty()) {
                    Caption(extras.joinToString("  ·  "), color = Palette.faint, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
