package app.wakesteps.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.wakesteps.R
import app.wakesteps.core.DayWindow
import app.wakesteps.core.EndReason
import app.wakesteps.core.Format
import app.wakesteps.core.NightScore
import app.wakesteps.core.Overrides
import app.wakesteps.core.SleepNight
import app.wakesteps.core.StartReason
import app.wakesteps.data.AppSettings
import app.wakesteps.data.DaySummary
import app.wakesteps.ui.theme.AppType
import app.wakesteps.ui.theme.Palette
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * One day in full: its ring, calories and distance, the hour-by-hour chart, a timeline from
 * waking to sleep with any naps on it, and the way into adjusting it.
 */
@Composable
fun DayDetail(
    day: DaySummary,
    settings: AppSettings,
    overrides: Overrides,
    loadHourly: suspend (DayWindow) -> List<Pair<Instant, Long>>,
    onAdjust: (DayWindow) -> Unit,
    onBack: () -> Unit,
    bottomPadding: Dp,
    /** The night this day began with, if there was one. */
    night: SleepNight? = null,
    nightScore: NightScore? = null,
    /** Opens the night to zoom into. */
    onExploreNight: (SleepNight) -> Unit = {},
) {
    BackHandler(onBack = onBack)
    val now = remember { Instant.now() }
    val w = day.window
    val hourly by produceState<List<Pair<Instant, Long>>?>(null, w) {
        value = runCatching { loadHourly(w) }.getOrDefault(emptyList())
    }
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.entrance(0)) {
            RoundIconButton(onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Palette.text, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                DateEyebrow(
                    if (w.isOngoing) stringResource(R.string.history_today) else stringResource(R.string.detail_eyebrow),
                    w.start.atZone(ZoneId.systemDefault()).toLocalDate(),
                )
                Text(Format.day(w.start), style = AppType.title.copy(fontSize = AppType.title.fontSize * 0.82f), color = Palette.text)
            }
        }
        Hero(day.totals.steps, settings.goal, w, now, onAdjust = null, modifier = Modifier.entrance(1), ringSize = 236.dp)
        Metrics(day.totals, settings, Modifier.entrance(2))
        val buckets = hourly
        if (buckets == null || buckets.isEmpty()) {
            Panel(Modifier.fillMaxWidth().height(if (buckets == null) 290.dp else 120.dp).entrance(3)) {
                Eyebrow(stringResource(R.string.hourly_title))
                Spacer(Modifier.height(12.dp))
                Caption(stringResource(if (buckets == null) R.string.loading else R.string.hourly_none), color = Palette.faint)
            }
        } else {
            HourlyPanel(buckets, settings.goal, ongoing = w.isOngoing, Modifier.entrance(3))
        }
        TimelinePanel(day, overrides, now, Modifier.entrance(4))
        night?.let {
            LastNightCard(it, nightScore, settings.restlessAsAwake, stringResource(R.string.sleep_night_before), onOpen = null, Modifier.entrance(5))
            if (it.stages.size > 1) NightChart(it.counted(settings.restlessAsAwake), Modifier.entrance(6), onOpen = { onExploreNight(it) })
        }
        PrimaryButton(stringResource(R.string.detail_adjust), { onAdjust(w) }, Modifier.align(Alignment.CenterHorizontally).entrance(7))
    }
}

@Composable
fun RoundIconButton(onClick: () -> Unit, size: Dp = 44.dp, content: @Composable () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier
            .pressScale(source, pressed = 0.92f)
            .size(size)
            .clip(CircleShape)
            .background(Palette.card)
            .border(1.dp, glassEdge(), CircleShape)
            .clickable(source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** From waking to sleep as a line, naps marked on it, with the times at each end. */
@Composable
private fun TimelinePanel(day: DaySummary, overrides: Overrides, now: Instant, modifier: Modifier) {
    val w = day.window
    val end = w.endOr(now)
    val colors = Palette
    Panel(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(stringResource(R.string.detail_timeline), Modifier.weight(1f))
            Eyebrow(stringResource(R.string.detail_awake, Format.duration(Duration.between(w.start, end))))
        }
        Spacer(Modifier.height(22.dp))
        val total = Duration.between(w.start, end).toMillis().coerceAtLeast(1).toFloat()
        val reveal by animateOnShow(1f, durationMillis = 1100, delayMillis = 400)
        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            val y = size.height / 2
            val stroke = 4.dp.toPx()
            drawLine(colors.track, Offset(0f, y), Offset(size.width, y), stroke, StrokeCap.Round)
            drawLine(
                Brush.horizontalGradient(listOf(colors.accent.copy(alpha = 0.5f), colors.accent)),
                Offset(0f, y), Offset(size.width * reveal, y), stroke, StrokeCap.Round,
            )
            for (nap in w.naps) {
                val a = Duration.between(w.start, nap.start).toMillis() / total * size.width
                val b = Duration.between(w.start, nap.end).toMillis() / total * size.width
                drawRoundRect(
                    colors.cardHigh,
                    Offset(a, 0f), Size((b - a).coerceAtLeast(size.height), size.height),
                    CornerRadius(size.height / 2),
                )
                drawRoundRect(
                    colors.muted.copy(alpha = 0.6f),
                    Offset(a, 0f), Size((b - a).coerceAtLeast(size.height), size.height),
                    CornerRadius(size.height / 2),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
                )
            }
            drawCircle(colors.accent, size.height / 2, Offset(size.height / 2, y))
            drawCircle(if (w.isOngoing) colors.text else colors.muted, size.height / 2, Offset(size.width - size.height / 2, y))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Eyebrow(
                    stringResource(
                        when (w.startReason) {
                            StartReason.WOKE_UP -> R.string.detail_woke
                            StartReason.FALLBACK -> R.string.detail_new_day
                            StartReason.MANUAL -> R.string.detail_started
                        }
                    ),
                    color = Palette.faint,
                )
                Text(Format.time(w.start), style = MaterialTheme.typography.titleMedium, color = Palette.text)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Eyebrow(
                    stringResource(
                        when {
                            w.isOngoing -> R.string.now_label
                            w.endReason == EndReason.FALLBACK -> R.string.detail_new_day
                            w.endReason == EndReason.MANUAL -> R.string.detail_ended
                            else -> R.string.detail_slept
                        }
                    ),
                    color = Palette.faint,
                )
                Text(
                    if (w.isOngoing) Format.time(now) else Format.time(end),
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.text,
                    textAlign = TextAlign.End,
                )
            }
        }
        dayTags(day, overrides, now)?.let {
            Spacer(Modifier.height(14.dp))
            Caption(it, color = Palette.accent, style = MaterialTheme.typography.labelMedium)
        }
    }
}
