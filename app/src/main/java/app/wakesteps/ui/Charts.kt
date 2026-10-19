package app.wakesteps.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.wakesteps.R
import app.wakesteps.core.Format
import app.wakesteps.ui.theme.AppType
import app.wakesteps.ui.theme.Palette
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import kotlin.math.abs

/** One bar: its value, and whether it's past the goal (drawn gold). */
class BarSpec(val value: Long, val gold: Boolean = false)

/**
 * Selection shared by a chart and its readout: tap a bar, or drag across the bars, to pick one.
 * It clears itself a moment after the finger lifts.
 */
class ChartSelection(initial: Int?) {
    var index by mutableStateOf(initial)
    var releasedAt by mutableLongStateOf(0L)
}

@Composable
fun rememberChartSelection(initial: Int? = null): ChartSelection {
    val selection = remember { ChartSelection(initial) }
    LaunchedEffect(selection.releasedAt) {
        if (selection.releasedAt == 0L) return@LaunchedEffect
        delay(2600)
        selection.index = null
    }
    return selection
}

/**
 * Picking a point on a chart with a finger: a sideways drag reports where it is as it moves
 * ([onScrub], as a fraction of the width), while an up-and-down drag is left to scroll the page;
 * a tap reports where it landed ([onTap]), and [onEnd] follows a drag once the finger lifts.
 */
fun Modifier.scrubbable(key: Any?, onTap: (Float) -> Unit, onScrub: (Float) -> Unit, onEnd: () -> Unit): Modifier =
    pointerInput(key) {
        fun fraction(x: Float) = (x / size.width).coerceIn(0f, 0.9999f)
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val slop = viewConfiguration.touchSlop
            var dx = 0f
            var dy = 0f
            var scrubbing = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    if (!scrubbing && abs(dx) < slop && abs(dy) < slop) onTap(fraction(change.position.x))
                    break
                }
                if (change.isConsumed) break // the page took it as a scroll
                val d = change.positionChange()
                dx += d.x
                dy += d.y
                if (!scrubbing) {
                    if (abs(dx) > slop && abs(dx) > abs(dy)) scrubbing = true
                    else if (abs(dy) > slop) break
                }
                if (scrubbing) {
                    change.consume()
                    onScrub(fraction(change.position.x))
                }
            }
            if (scrubbing) onEnd()
        }
    }

/**
 * Rounded bars that grow in when first shown and can be scrubbed: a horizontal drag picks the bar
 * under the finger (vertical drags still scroll the page), a tap picks one, a tap on the picked
 * bar lets go. Each new bar gives a light haptic tick. [line] draws a dashed reference line
 * labelled [lineLabel].
 */
@Composable
fun InteractiveBars(
    bars: List<BarSpec>,
    selection: ChartSelection,
    modifier: Modifier = Modifier,
    barFraction: Float = 0.58f,
    line: Long? = null,
    lineLabel: String? = null,
) {
    val colors = Palette
    val haptics = LocalHapticFeedback.current
    val measurer = rememberTextMeasurer()
    val labelStyle = AppType.eyebrow.copy(color = colors.muted)
    val grow by animateOnShow(1f, durationMillis = 1000, delayMillis = 150)
    val n = bars.size

    fun select(i: Int?) {
        if (i != selection.index) {
            selection.index = i
            if (i != null) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
    }

    fun pick(fraction: Float) = (fraction * n).toInt().coerceIn(0, n - 1)
    Canvas(
        modifier.scrubbable(
            n,
            onTap = { f ->
                if (n > 0) {
                    val i = pick(f)
                    select(if (i == selection.index) null else i)
                    selection.releasedAt = System.currentTimeMillis()
                }
            },
            onScrub = { f ->
                if (n > 0) {
                    selection.releasedAt = 0L
                    select(pick(f))
                }
            },
            onEnd = { selection.releasedAt = System.currentTimeMillis() },
        ),
    ) {
        if (n == 0) return@Canvas
        val max = maxOf(bars.maxOf { it.value }, line ?: 0L).coerceAtLeast(1L).toFloat() * 1.06f
        val slot = size.width / n
        val w = (slot * barFraction).coerceIn(3.dp.toPx(), 26.dp.toPx())
        val radius = CornerRadius(w / 2)
        val dot = 4.dp.toPx()
        val chosen = selection.index

        // Faint guides at thirds of the scale.
        for (k in 1..2) {
            val y = size.height * k / 3f
            drawLine(Color.White.copy(alpha = 0.04f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }

        bars.forEachIndexed { i, bar ->
            // Bars rise left to right.
            val stagger = 0.45f / n
            val p = ((grow - i * stagger) / (1f - (n - 1) * stagger)).coerceIn(0f, 1f)
            val full = size.height * bar.value / max
            val h = (full * p).coerceAtLeast(if (bar.value > 0) dot else 0f)
            val x = i * slot + (slot - w) / 2
            val dim = chosen != null && chosen != i
            val alpha = if (dim) 0.28f else 1f
            if (bar.value == 0L) {
                drawCircle(colors.faint.copy(alpha = 0.4f * alpha), dot / 2.2f, Offset(x + w / 2, size.height - dot / 2))
                return@forEachIndexed
            }
            val (top, bottom) = when {
                bar.gold -> colors.goalStart to colors.goalEnd
                colors.gradient -> colors.accent to colors.accentEnd
                else -> colors.accent to colors.accent.copy(alpha = 0.5f)
            }
            if (chosen == i) {
                // A soft glow behind the picked bar and a guide up to the top.
                drawRoundRect(
                    top.copy(alpha = 0.18f),
                    Offset(x - w * 0.35f, size.height - h - w * 0.35f),
                    Size(w * 1.7f, h + w * 0.35f),
                    CornerRadius(w),
                )
                drawLine(
                    colors.text.copy(alpha = 0.35f),
                    Offset(x + w / 2, 0f),
                    Offset(x + w / 2, size.height - h - 6.dp.toPx()),
                    1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
                )
            }
            drawRoundRect(
                Brush.verticalGradient(listOf(top, bottom), startY = size.height - h, endY = size.height),
                Offset(x, size.height - h),
                Size(w, h),
                radius,
                alpha = alpha,
            )
        }

        line?.let {
            val y = size.height - size.height * it / max
            drawLine(
                colors.goal.copy(alpha = 0.55f),
                Offset(0f, y),
                Offset(size.width, y),
                1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
            )
            lineLabel?.let { label ->
                val text = measurer.measure(label, labelStyle.copy(color = colors.goal))
                drawText(text, topLeft = Offset(size.width - text.size.width, y - text.size.height - 3.dp.toPx()))
            }
        }
    }
}

// ---------------------------------------------------------------- hour by hour

/**
 * The day hour by hour. The readout shows the busiest hour, or the hour you're touching; hours
 * from the one where you passed your goal are gold.
 */
@Composable
fun HourlyPanel(
    buckets: List<Pair<Instant, Long>>,
    goal: Int,
    ongoing: Boolean,
    modifier: Modifier = Modifier,
    initialSelection: Int? = null,
) {
    val context = LocalContext.current
    val h24 = android.text.format.DateFormat.is24HourFormat(context)
    val values = buckets.map { it.second }
    // The hour the running total passed the goal, if it did.
    val goalHour = run {
        var sum = 0L
        values.indexOfFirst { sum += it; sum >= goal }.takeIf { values.sum() >= goal }
    }
    val bars = values.mapIndexed { i, v -> BarSpec(v, gold = goalHour != null && i >= goalHour) }
    val peak = values.indices.maxByOrNull { values[it] }
    val selection = rememberChartSelection(initialSelection)
    val shown = selection.index ?: peak

    fun range(i: Int): String {
        val start = buckets[i].first
        return "${Format.hour(start, h24)} – ${Format.hour(start.plus(Duration.ofHours(1)), h24)}"
    }

    Panel(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(stringResource(R.string.hourly_title), Modifier.weight(1f))
            goalHour?.let {
                Eyebrow(stringResource(R.string.hourly_goal_at, Format.hour(buckets[it].first, h24)), color = Palette.goal)
            }
        }
        Spacer(Modifier.height(10.dp))
        if (shown != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(Format.number(values[shown]), style = AppType.number(34f), color = Palette.text)
                Spacer(Modifier.width(10.dp))
                val caption = if (selection.index != null) range(shown) else stringResource(R.string.hourly_peak, range(shown))
                val captionColor by animateColorAsState(
                    if (selection.index != null) Palette.text else Palette.muted, label = "caption",
                )
                Caption(caption, Modifier.padding(bottom = 6.dp), color = captionColor)
            }
        }
        Spacer(Modifier.height(18.dp))
        InteractiveBars(bars, selection, Modifier.fillMaxWidth().height(150.dp))
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val labels = listOfNotNull(
                buckets.firstOrNull()?.first?.let { Format.hour(it, h24) },
                buckets.getOrNull(buckets.size / 2)?.first?.takeIf { buckets.size >= 5 }?.let { Format.hour(it, h24) },
                if (ongoing) stringResource(R.string.now_label) else buckets.lastOrNull()?.first?.let { Format.hour(it, h24) },
            )
            labels.forEach { Text(it, style = AppType.eyebrow, color = Palette.faint) }
        }
    }
}

// ---------------------------------------------------------------- week

/** One day in the week chart. */
class WeekDay(val initial: String, val title: String, val steps: Long, val partial: Boolean)

/**
 * The last seven days against a dashed goal line, days that made it in gold. The readout shows
 * the average of finished days, or the day you're touching.
 */
@Composable
fun WeekPanel(days: List<WeekDay>, goal: Int, modifier: Modifier = Modifier, initialSelection: Int? = null) {
    val selection = rememberChartSelection(initialSelection)
    val done = days.filterNot { it.partial }.ifEmpty { days }
    val average = if (done.isEmpty()) 0L else done.sumOf { it.steps } / done.size
    val met = done.count { it.steps >= goal }
    val picked = selection.index?.let { days.getOrNull(it) }

    Panel(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(stringResource(R.string.history_week), Modifier.weight(1f))
            Eyebrow(stringResource(R.string.history_goal_days, met, done.size), color = Palette.goal)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            if (picked != null) {
                Text(Format.number(picked.steps), style = AppType.number(34f), color = Palette.text)
            } else {
                CountUp(average.toDouble(), 34f)
            }
            Spacer(Modifier.width(10.dp))
            Caption(
                picked?.title ?: stringResource(R.string.history_per_day),
                Modifier.padding(bottom = 6.dp),
                color = if (picked != null) Palette.text else Palette.muted,
            )
        }
        Spacer(Modifier.height(18.dp))
        InteractiveBars(
            days.map { BarSpec(it.steps, gold = it.steps >= goal) },
            selection,
            Modifier.fillMaxWidth().height(130.dp),
            barFraction = 0.44f,
            line = goal.toLong(),
            lineLabel = Format.compact(goal.toLong()),
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            days.forEachIndexed { i, day ->
                val color by animateColorAsState(
                    if (selection.index == i) Palette.text else Palette.faint, label = "dayInitial",
                )
                Text(
                    day.initial,
                    style = MaterialTheme.typography.labelMedium,
                    color = color,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
