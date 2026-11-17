package app.wakesteps.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wakesteps.R
import app.wakesteps.core.DayWindow
import app.wakesteps.core.Format
import app.wakesteps.core.Walk
import app.wakesteps.core.Walks
import app.wakesteps.ui.theme.AppType
import app.wakesteps.ui.theme.Palette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln

/** How many minutes each bar can stand for, finest first. */
private val BAR_MINUTES = listOf(1, 2, 3, 5, 10, 15, 20, 30, 60)

/** Minutes across the chart from which it's minute by minute. */
private const val MINUTE_BARS_WITHIN = 40.0

/**
 * How many minutes each bar stands for with [visible] minutes of a [day]-minute day in view: an
 * hour each with the whole day in view, as on Hour by hour, then finer a step at a time as you
 * zoom in (30, 20, 15, 10, 5, 3, 2) until it's minute by minute, with 40 minutes or less across.
 * Steps are only kept a minute at a time, so a minute is as fine as it goes.
 */
internal fun barMinutes(visible: Double, day: Int): Int {
    if (day <= MINUTE_BARS_WITHIN) return 1
    // How far from minute bars (0) to hour bars (1), evenly by zoom.
    val t = (ln(visible / MINUTE_BARS_WITHIN) / ln(day / MINUTE_BARS_WITHIN)).coerceIn(0.0, 1.0)
    val ideal = t * ln(60.0)
    return BAR_MINUTES.minBy { abs(ln(it.toDouble()) - ideal) }
}

/** The day's steps by the end of each minute: [minutes] added up as they go. */
internal fun runningTotals(minutes: LongArray): LongArray {
    var sum = 0L
    return LongArray(minutes.size) { i -> sum += minutes[i]; sum }
}

/** Steps in each minute from [start] to [end]; minutes without any are 0. */
private fun perMinute(start: Instant, end: Instant, steps: List<Pair<Instant, Long>>): LongArray {
    val out = LongArray(Duration.between(start, end).toMinutes().toInt().coerceAtLeast(1))
    for ((at, count) in steps) {
        val m = Math.floorDiv(Duration.between(start, at).seconds, 60L).toInt()
        if (m in out.indices) out[m] += count
    }
    return out
}

/**
 * The day's minutes in bars of [size] minutes, lined up with the clock (a 15-minute bar runs from
 * :00, :15, :30 or :45), each as the minute it starts from (may be before the day's first) and
 * its steps. [offset] is how many minutes past midnight the day's first minute is.
 */
private fun bars(minutes: LongArray, offset: Int, size: Int): List<Pair<Int, Long>> {
    val out = mutableListOf<Pair<Int, Long>>()
    var key = -1
    var sum = 0L
    for (i in minutes.indices) {
        val k = Math.floorDiv(offset + i, size)
        if (k != key) {
            if (key >= 0) out += (key * size - offset) to sum
            key = k
            sum = 0
        }
        sum += minutes[i]
    }
    if (key >= 0) out += (key * size - offset) to sum
    return out
}

/**
 * One day's steps opened up: hour by hour across the whole day, finer as you zoom in (pinch,
 * double-tap, or − and +) down to minute by minute, or with [everyMinute] every minute however far
 * out. The bar under your finger gives its steps and time and the day's total by then; the whole
 * day sits below with the part in view framed (drag along it to move, tap to jump). Bars from the
 * minute you passed your goal are gold. Under it, the day's walks: tap one to see it in the chart.
 */
@Composable
fun StepsExplorer(
    window: DayWindow,
    goal: Int,
    loadMinutes: suspend (DayWindow) -> List<Pair<Instant, Long>>,
    onBack: () -> Unit,
    bottomPadding: Dp,
    initialView: ClosedFloatingPointRange<Float>? = null,
    initialMinute: Int? = null,
    now: Instant = Instant.now(),
    everyMinute: Boolean = false,
    onEveryMinute: (Boolean) -> Unit = {},
) {
    BackHandler(onBack = onBack)
    // A day still going runs up to when it was opened.
    val end = remember(window) { window.endOr(now) }
    // Null while loading; empty if they couldn't be read.
    val minutes by produceState<LongArray?>(null, window) {
        value = runCatching { perMinute(window.start, end, loadMinutes(window)) }.getOrDefault(LongArray(0))
    }
    val zone = ZoneId.systemDefault()
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
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
                DateEyebrow(stringResource(R.string.steps_eyebrow), window.start.atZone(zone).toLocalDate())
                Text(
                    if (window.isOngoing) stringResource(R.string.history_today) else Format.day(window.start),
                    style = AppType.title.copy(fontSize = AppType.title.fontSize * 0.82f),
                    color = Palette.text,
                )
            }
        }
        val loaded = minutes
        when {
            loaded == null -> Panel(Modifier.fillMaxWidth().height(320.dp).entrance(1)) {
                Caption(stringResource(R.string.loading), color = Palette.faint)
            }
            loaded.isEmpty() -> Panel(Modifier.fillMaxWidth().entrance(1)) {
                Caption(stringResource(R.string.steps_unavailable), color = Palette.faint)
            }
            loaded.all { it == 0L } -> Panel(Modifier.fillMaxWidth().entrance(1)) {
                Caption(stringResource(R.string.hourly_none), color = Palette.faint)
            }
            else -> {
                // As close as ten minutes across.
                val view = remember(loaded) { SpanView(initialView ?: 0f..1f, (10f / loaded.size).coerceAtMost(1f)) }
                val selection = rememberChartSelection(initialMinute)
                // The minute the day's running total reached the goal: bars from then on are gold.
                val goalMinute = remember(loaded, goal) {
                    var sum = 0L
                    loaded.indexOfFirst { sum += it; sum >= goal }.takeIf { it >= 0 }
                }
                val walks = remember(loaded) { Walks.of(loaded) }
                PillToggle(
                    listOf(stringResource(R.string.steps_grouped), stringResource(R.string.steps_every_minute)),
                    selected = if (everyMinute) 1 else 0,
                    onSelect = { onEveryMinute(it == 1) },
                    modifier = Modifier.fillMaxWidth().entrance(1),
                )
                ZoomableSteps(window.start, loaded, goalMinute, everyMinute, view, selection, Modifier.entrance(2))
                WalksPanel(walks, window.start, goalMinute, Modifier.entrance(3)) { walk ->
                    selection.index = null
                    view.glideTo(scope, walk.first.toFloat() / loaded.size, (walk.last + 1f) / loaded.size)
                    scope.launch { scroll.animateScrollTo(0) }
                }
            }
        }
    }
}

/** The day's steps in view, big: the readout and zoom buttons, the bars, the times along them, and the whole day below. */
@Composable
private fun ZoomableSteps(
    start: Instant,
    minutes: LongArray,
    goalMinute: Int?,
    everyMinute: Boolean,
    view: SpanView,
    selection: ChartSelection,
    modifier: Modifier,
) {
    val n = minutes.size
    val totals = remember(minutes) { runningTotals(minutes) }
    val zone = ZoneId.systemDefault()
    val offset = remember(start) { start.atZone(zone).let { it.hour * 60 + it.minute } }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val colors = Palette
    fun at(minute: Int): Instant = start.plus(Duration.ofMinutes(minute.toLong()))
    val first = (view.left * n).toInt().coerceIn(0, n - 1)
    val last = (view.right * n).toInt().coerceIn(first + 1, n)
    val from = start.plusMillis((view.left * n * 60_000L).toLong())
    val to = start.plusMillis((view.right * n * 60_000L).toLong())

    Panel(modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val visible = view.width * n.toDouble()
            // Never more bars than fit 3dp apart, on however narrow a screen; every minute a bar of
            // its own when asked for, squeezed into slivers when more are in view than fit apart.
            val barMinutes = if (everyMinute) 1 else barMinutes(visible, n).let { ideal ->
                BAR_MINUTES.firstOrNull { it >= ideal && visible / it <= maxWidth / 3.dp } ?: 60
            }
            val squeezed = barMinutes == 1 && maxWidth / visible.toFloat() < 3.dp
            val all = remember(minutes, barMinutes) { bars(minutes, offset, barMinutes) }
            val top by animateFloatAsState(all.maxOf { it.second }.coerceAtLeast(1L).toFloat(), label = "scale")
            // When the bars change size, the old ones fade out as the new ones grow in.
            var shownMinutes by remember { mutableIntStateOf(barMinutes) }
            var outgoing by remember { mutableStateOf<Pair<Int, List<Pair<Int, Long>>>?>(null) }
            val swap = remember { Animatable(1f) }
            LaunchedEffect(barMinutes) {
                if (barMinutes == shownMinutes) return@LaunchedEffect
                outgoing = shownMinutes to bars(minutes, offset, shownMinutes)
                shownMinutes = barMinutes
                swap.snapTo(0f)
                swap.animateTo(1f, tween(300))
                outgoing = null
            }
            val pickedBar = selection.index?.let { m -> all.lastOrNull { it.first <= m } }
            val marks = ticks(from, to, zone, maxCount = (maxWidth / 64.dp).toInt().coerceAtLeast(2))

            fun pick(across: Float) {
                val m = ((view.left + across * view.width) * n).toInt().coerceIn(0, n - 1)
                val bar = all.lastOrNull { it.first <= m }
                // Squeezed, a tick each quarter hour crossed, not each of the minutes flying by.
                val moved = if (squeezed) selection.index?.let { (offset + it) / 15 != (offset + m) / 15 } ?: true else bar != pickedBar
                if (selection.index == null || moved) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                selection.index = m
            }

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            if (pickedBar != null) {
                                val (barStart, steps) = pickedBar
                                Text(Format.number(steps), style = AppType.number(30f), color = colors.text, maxLines = 1)
                                Spacer(Modifier.width(10.dp))
                                val a = at(maxOf(barStart, 0))
                                Caption(
                                    if (barMinutes == 1) Format.time(a) else "${Format.time(a)} – ${Format.time(at(minOf(barStart + barMinutes, n)))}",
                                    Modifier.padding(bottom = 5.dp),
                                    color = colors.text,
                                )
                            } else {
                                Text(Format.number((first until last).sumOf { minutes[it] }), style = AppType.number(30f), color = colors.text, maxLines = 1)
                                Spacer(Modifier.width(10.dp))
                                Caption("${Format.time(from)} – ${Format.time(to)}", Modifier.padding(bottom = 5.dp))
                            }
                        }
                        // The day's running total: by the end of the bar under your finger, or the whole day.
                        val dayTotal = pickedBar?.let { (barStart, _) -> totals[(minOf(barStart + barMinutes, n) - 1).coerceIn(0, n - 1)] } ?: totals[n - 1]
                        Caption(
                            stringResource(if (pickedBar != null) R.string.steps_day_total_by_then else R.string.steps_day_total, Format.number(dayTotal)),
                            color = colors.muted,
                        )
                    }
                    ZoomButton("−", enabled = view.zoomed) { view.zoom(scope, 0.5f) }
                    Spacer(Modifier.width(8.dp))
                    ZoomButton("+", enabled = !view.atClosest) { view.zoom(scope, 2f) }
                }
                Spacer(Modifier.height(16.dp))
                Eyebrow(
                    when (barMinutes) {
                        1 -> stringResource(R.string.steps_minute_by_minute)
                        60 -> stringResource(R.string.hourly_title)
                        else -> stringResource(R.string.steps_every, barMinutes)
                    },
                    color = if (barMinutes == 1) colors.accent else colors.muted,
                )
                Spacer(Modifier.height(12.dp))
                val measurer = rememberTextMeasurer()
                val scaleStyle = AppType.eyebrow.copy(fontSize = 10.sp, color = colors.faint)
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .zoomable(
                            view,
                            onTap = { pick(it); selection.releasedAt = System.currentTimeMillis() },
                            onDoubleTap = { if (view.atClosest) view.glideTo(scope, 0f, 1f) else view.zoom(scope, 3f, it) },
                            onScrub = { selection.releasedAt = 0L; pick(it) },
                            onScrubEnd = { selection.releasedAt = System.currentTimeMillis() },
                            onPinch = { focus, factor, pan -> view.pinch(focus, factor, pan) },
                        ),
                ) {
                    val scale = size.width / (view.width * n)
                    fun x(minute: Float) = (minute - view.left * n) * scale
                    val head = 18.dp.toPx()
                    val chart = size.height - head
                    // The scale: a faint line at the busiest bar, labelled.
                    drawLine(Color.White.copy(alpha = 0.05f), Offset(0f, head), Offset(size.width, head), 1.dp.toPx())
                    drawText(measurer.measure(Format.number(top.toLong()), scaleStyle), topLeft = Offset(0f, 0f))
                    for (k in 1..2) {
                        val y = head + chart * k / 3f
                        drawLine(Color.White.copy(alpha = 0.035f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                    }
                    for (t in marks) {
                        val at = x(Duration.between(start, t).toMillis() / 60_000f)
                        drawLine(Color.White.copy(alpha = 0.05f), Offset(at, head), Offset(at, size.height), 1.dp.toPx())
                    }
                    // Every minute with more in view than fit side by side: each sliver of the chart
                    // as tall as the busiest minute in it, so no burst or lull is averaged away.
                    fun drawSlivers(alpha: Float, picked: Pair<Int, Long>?) {
                        val sliver = maxOf(1f, scale)
                        val plain = Path()
                        val gold = Path()
                        var column = Int.MIN_VALUE
                        var peak = 0L
                        var golden = false
                        fun flush() {
                            if (column == Int.MIN_VALUE || peak == 0L) return
                            val h = (chart * peak / top).coerceAtLeast(1.dp.toPx())
                            val cx = column * sliver + sliver / 2
                            (if (golden) gold else plain).apply { moveTo(cx, size.height); lineTo(cx, size.height - h) }
                        }
                        val firstShown = floor(view.left * n).toInt().coerceIn(0, n - 1)
                        val lastShown = ceil(view.right * n).toInt().coerceIn(firstShown, n - 1)
                        for (m in firstShown..lastShown) {
                            val c = floor(x(m + 0.5f) / sliver).toInt()
                            if (c != column) {
                                flush()
                                column = c
                                peak = 0L
                                golden = false
                            }
                            peak = maxOf(peak, minutes[m])
                            if (goalMinute != null && m >= goalMinute) golden = true
                        }
                        flush()
                        val (hi, lo) = if (colors.gradient) colors.accent to colors.accentEnd else colors.accent to colors.accent.copy(alpha = 0.5f)
                        val dim = if (picked != null) 0.45f else 1f
                        drawPath(plain, Brush.verticalGradient(listOf(hi, lo), startY = head, endY = size.height), alpha = alpha * dim, style = Stroke(sliver))
                        drawPath(gold, Brush.verticalGradient(listOf(colors.goalStart, colors.goalEnd), startY = head, endY = size.height), alpha = alpha * dim, style = Stroke(sliver))
                        // The minute under your finger, lit, with a dot at its count (on the floor for none).
                        if (picked != null) {
                            val (m, steps) = picked
                            val cx = x(m + 0.5f)
                            val h = chart * steps / top
                            val lit = if (goalMinute != null && m >= goalMinute) colors.goal else hi
                            drawLine(
                                colors.text.copy(alpha = 0.35f * alpha), Offset(cx, head), Offset(cx, size.height - h - 6.dp.toPx()), 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
                            )
                            if (h > 0f) drawLine(lit.copy(alpha = alpha), Offset(cx, size.height), Offset(cx, size.height - h), maxOf(sliver, 2.dp.toPx()))
                            drawCircle(colors.text.copy(alpha = alpha), 3.dp.toPx(), Offset(cx, size.height - h))
                        }
                    }

                    // [set]'s bars of [each] minutes, those at least partly in view, at [alpha].
                    fun drawBars(set: List<Pair<Int, Long>>, each: Int, alpha: Float, picked: Pair<Int, Long>?) {
                        val pitch = each * scale
                        if (each == 1 && pitch < 3.dp.toPx()) return drawSlivers(alpha, picked)
                        val w = (pitch * 0.62f).coerceIn(1.5.dp.toPx(), 26.dp.toPx())
                        val shownFrom = view.left * n - each
                        val shownTo = view.right * n
                        for ((barStart, steps) in set) {
                            if (steps == 0L || barStart < shownFrom || barStart > shownTo) continue
                            val h = (chart * steps / top).coerceAtLeast(2.dp.toPx())
                            val bx = x(barStart.toFloat()) + (pitch - w) / 2
                            val gold = goalMinute != null && barStart + each > goalMinute
                            val (hi, lo) = when {
                                gold -> colors.goalStart to colors.goalEnd
                                colors.gradient -> colors.accent to colors.accentEnd
                                else -> colors.accent to colors.accent.copy(alpha = 0.5f)
                            }
                            val dim = picked != null && picked.first != barStart
                            if (!dim && picked != null) {
                                drawRoundRect(
                                    hi.copy(alpha = 0.18f * alpha), Offset(bx - w * 0.35f, size.height - h - w * 0.35f),
                                    Size(w * 1.7f, h + w * 0.35f), CornerRadius(w),
                                )
                                drawLine(
                                    colors.text.copy(alpha = 0.35f * alpha), Offset(bx + w / 2, head), Offset(bx + w / 2, size.height - h - 6.dp.toPx()), 1.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
                                )
                            }
                            drawRoundRect(
                                Brush.verticalGradient(listOf(hi, lo), startY = size.height - h, endY = size.height),
                                Offset(bx, size.height - h), Size(w, h), CornerRadius(minOf(w / 2, h / 2)),
                                alpha = alpha * if (dim) 0.3f else 1f,
                            )
                        }
                    }
                    clipRect {
                        outgoing?.let { (each, set) -> drawBars(set, each, 1f - swap.value, null) }
                        drawBars(all, barMinutes, if (outgoing != null) swap.value else 1f, pickedBar)
                    }
                }
                Spacer(Modifier.height(8.dp))
                TickLabels(marks, from, to, Modifier.fillMaxWidth().height(16.dp))
                Spacer(Modifier.height(16.dp))
                WholeDay(minutes, offset, goalMinute, view, scope, Modifier.fillMaxWidth().height(44.dp))
            }
        }
    }
}

/** The whole day small, in 15-minute bars, with the part in view framed: drag along it to move, tap to jump. */
@Composable
private fun WholeDay(minutes: LongArray, offset: Int, goalMinute: Int?, view: SpanView, scope: CoroutineScope, modifier: Modifier) {
    val all = remember(minutes) { bars(minutes, offset, 15) }
    val colors = Palette
    Canvas(modifier.overviewGestures(view, scope)) {
        drawRoundRect(Color.White.copy(alpha = 0.04f), cornerRadius = CornerRadius(10.dp.toPx()))
        val n = minutes.size.toFloat()
        val top = all.maxOf { it.second }.coerceAtLeast(1L).toFloat()
        val inset = 6.dp.toPx()
        val room = size.height - inset * 2
        for ((barStart, steps) in all) {
            if (steps == 0L) continue
            val left = barStart.coerceAtLeast(0) / n * size.width
            val right = minOf(barStart + 15f, n) / n * size.width
            val h = (room * steps / top).coerceAtLeast(1.5.dp.toPx())
            val gold = goalMinute != null && barStart + 15 > goalMinute
            drawRoundRect(
                if (gold) colors.goal else colors.accent,
                Offset(left + 0.5.dp.toPx(), size.height - inset - h),
                Size((right - left - 1.dp.toPx()).coerceAtLeast(1.dp.toPx()), h),
                CornerRadius(1.5.dp.toPx()),
            )
        }
        drawViewFrame(view, colors.text)
    }
}

/** The day's walks, each with its times, how long, its pace and steps: tap one to see it in the chart. */
@Composable
private fun WalksPanel(walks: List<Walk>, start: Instant, goalMinute: Int?, modifier: Modifier, onShow: (Walk) -> Unit) {
    fun at(minute: Int): Instant = start.plus(Duration.ofMinutes(minute.toLong()))
    Panel(modifier.fillMaxWidth(), padding = PaddingValues(top = 20.dp, bottom = 6.dp)) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(stringResource(R.string.walks_title), Modifier.weight(1f))
            if (walks.isNotEmpty()) {
                val total = Duration.ofMinutes(walks.sumOf { it.minutes }.toLong())
                Eyebrow(stringResource(R.string.walks_total, Format.duration(total)), color = Palette.faint)
            }
        }
        if (walks.isEmpty()) {
            Caption(
                pluralStringResource(R.plurals.walks_none, Walks.SHORTEST, Walks.SHORTEST),
                Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 14.dp),
                color = Palette.faint,
            )
        }
        Spacer(Modifier.height(4.dp))
        walks.forEachIndexed { i, walk ->
            if (i > 0) Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onShow(walk) }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${Format.time(at(walk.first))} – ${Format.time(at(walk.last + 1))}",
                        style = MaterialTheme.typography.titleMedium, color = Palette.text, maxLines = 1,
                    )
                    Caption(
                        "${Format.duration(Duration.ofMinutes(walk.minutes.toLong()))}  ·  " +
                            stringResource(R.string.walk_pace, Format.number(walk.pace)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (goalMinute != null && goalMinute in walk.first..walk.last) {
                    Eyebrow(stringResource(R.string.walk_goal), color = Palette.goal)
                    Spacer(Modifier.width(10.dp))
                }
                Text(Format.number(walk.steps), style = AppType.number(22f), color = Palette.text)
            }
        }
    }
}
