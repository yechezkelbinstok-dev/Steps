package app.wakesteps.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.wakesteps.R
import app.wakesteps.core.Format
import app.wakesteps.core.HeartBar
import app.wakesteps.core.HeartDay
import app.wakesteps.core.HeartPoint
import app.wakesteps.core.HeartZone
import app.wakesteps.core.HeartZones
import app.wakesteps.core.heartBars
import app.wakesteps.core.heartRange
import app.wakesteps.ui.theme.AppType
import app.wakesteps.ui.theme.Palette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** Zoomed in to this many minutes across or fewer, heart rate is read five seconds at a time. */
private const val DETAIL_WITHIN = 30f

/** Heart rate read five seconds at a time comes in stretches of this many minutes. */
private const val CHUNK = 15

/** How many minutes each grey bar can stand for, finest first. */
private val HEART_BAR_MINUTES = listOf(1, 2, 3, 5, 10, 15, 20, 30, 60)

/** The strip to the right of the chart for the zones' heart rates. */
private val SCALE = 34.dp

private val SECOND_TIME = DateTimeFormatter.ofPattern("h:mm:ss a")

/** Google's colours for its heart-rate zones. */
internal fun zoneColor(zone: HeartZone): Color = when (zone) {
    HeartZone.LIGHT -> Color(0xFF3CC8F0)
    HeartZone.MODERATE -> Color(0xFF4CD787)
    HeartZone.VIGOROUS -> Color(0xFFFFB547)
    HeartZone.PEAK -> Color(0xFFFF6464)
}

@Composable
internal fun zoneName(zone: HeartZone): String = stringResource(
    when (zone) {
        HeartZone.LIGHT -> R.string.heart_zone_light
        HeartZone.MODERATE -> R.string.heart_zone_moderate
        HeartZone.VIGOROUS -> R.string.heart_zone_vigorous
        HeartZone.PEAK -> R.string.heart_zone_peak
    },
)

/**
 * The heart rate to draw, oldest first: each point's middle in seconds from midnight, its
 * average, and whether it was read five seconds at a time (a minute at a time otherwise, or
 * [step] seconds at a time when averaged down to fit the screen).
 */
private class HeartLine(val at: FloatArray, val bpm: FloatArray, val fine: BooleanArray, val step: Float = 60f) {
    val size: Int get() = at.size

    /** The first point at or after [second] (or [size]). */
    fun from(second: Float): Int {
        var lo = 0
        var hi = size
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (at[mid] < second) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /** The point nearest [second], or -1 with none. */
    fun nearest(second: Float): Int {
        if (size == 0) return -1
        val i = from(second)
        return when {
            i == 0 -> 0
            i == size -> size - 1
            second - at[i - 1] <= at[i] - second -> i - 1
            else -> i
        }
    }

    /** Whether points [i] and [i] + 1 are close enough to draw joined (no gap where the band was off). */
    fun joined(i: Int): Boolean = at[i + 1] - at[i] <= if (fine[i] && fine[i + 1]) 20f else maxOf(330f, step * 1.6f)

    /** How far from [second] point [i] can be and still be what was there then. */
    fun reach(i: Int): Float = if (fine[i]) 15f else maxOf(150f, step)

    /**
     * Averaged [seconds] at a time (lined up with the clock), for more minutes in view than the
     * screen can show apart: each stretch's readings as one point, where they were on average.
     */
    fun averaged(seconds: Int): HeartLine {
        val at = ArrayList<Float>()
        val bpm = ArrayList<Float>()
        var key = Int.MIN_VALUE
        var sumAt = 0f
        var sumBpm = 0f
        var count = 0
        fun flush() {
            if (count == 0) return
            at += sumAt / count
            bpm += sumBpm / count
        }
        for (i in 0 until size) {
            val k = (this.at[i] / seconds).toInt()
            if (k != key) {
                flush()
                key = k
                sumAt = 0f
                sumBpm = 0f
                count = 0
            }
            sumAt += this.at[i]
            sumBpm += this.bpm[i]
            count++
        }
        flush()
        return HeartLine(at.toFloatArray(), bpm.toFloatArray(), BooleanArray(at.size), seconds.toFloat())
    }
}

/** How many seconds each point of the line can stand for when averaged to fit, finest first. */
private val LINE_SECONDS = listOf(120, 180, 300, 600)

/** The day's minutes, but where a stretch was read five seconds at a time ([detail], by stretch), those instead. */
private fun heartLine(day: HeartDay, detail: Map<Int, List<HeartPoint>>, useDetail: Boolean): HeartLine {
    fun second(i: Instant) = Duration.between(day.start, i).toMillis() / 1000f
    fun covered(s: Float) = useDetail && detail[(s / 60f / CHUNK).toInt()]?.isNotEmpty() == true
    val points = ArrayList<Triple<Float, Float, Boolean>>(day.points.size)
    for (p in day.points) {
        val s = second(p.at)
        if (!covered(s)) points += Triple(s + 30f, p.avg, false)
    }
    if (useDetail) for (list in detail.values) for (p in list) points += Triple(second(p.at) + 2.5f, p.avg, true)
    points.sortBy { it.first }
    return HeartLine(
        FloatArray(points.size) { points[it].first },
        FloatArray(points.size) { points[it].second },
        BooleanArray(points.size) { points[it].third },
    )
}

/**
 * A day's heart rate as Google's heart rate page shows it, midnight to midnight, opened up: the
 * day's range and its high, each zone's line, grey bars from each stretch's lowest to its highest
 * and the minute-by-minute line through them, coloured by zone. Zoom in (pinch, double-tap, or −
 * and +) and it closes in, down to a couple of minutes across, read five seconds at a time from 30
 * minutes in; the scale closes in with it. Drag along it to read the heart rate under your finger.
 * The whole day sits below with the part in view framed. ‹ and › go a day back and forward.
 */
@Composable
fun HeartExplorer(
    date: LocalDate,
    loadDay: suspend (LocalDate) -> HeartDay?,
    loadDetail: suspend (Instant, Instant) -> List<HeartPoint>,
    onBack: () -> Unit,
    onSetUp: () -> Unit,
    bottomPadding: Dp,
    /** Whether heart rate can be read; the day is read again when this changes. */
    allowed: Boolean = true,
    today: LocalDate = LocalDate.now(),
    initialView: ClosedFloatingPointRange<Float>? = null,
    initialSecond: Int? = null,
) {
    BackHandler(onBack = onBack)
    var shown by remember(date) { mutableStateOf(date) }
    var attempt by remember { mutableIntStateOf(0) }
    // Null while loading; a failure when Google Health couldn't be reached; null inside when heart
    // rate can't be read at all.
    val loaded by produceState<Result<HeartDay?>?>(null, shown, allowed, attempt) {
        value = null
        value = try {
            Result.success(loadDay(shown))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    val zone = ZoneId.systemDefault()
    Column(
        Modifier
            .fillMaxSize()
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
            Column(Modifier.weight(1f)) {
                DateEyebrow(stringResource(R.string.heart_title), shown)
                Text(
                    if (shown == today) stringResource(R.string.history_today) else Format.day(shown.atStartOfDay(zone).toInstant()),
                    style = AppType.title.copy(fontSize = AppType.title.fontSize * 0.82f),
                    color = Palette.text,
                    maxLines = 1,
                )
            }
            RoundIconButton({ shown = shown.minusDays(1) }, size = 40.dp) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.heart_day_before), tint = Palette.text, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(8.dp))
            val later = shown < today
            RoundIconButton({ if (later) shown = shown.plusDays(1) }, size = 40.dp) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.heart_day_after),
                    tint = if (later) Palette.text else Palette.faint.copy(alpha = 0.4f), modifier = Modifier.size(22.dp),
                )
            }
        }
        val result = loaded
        val day = result?.getOrNull()
        when {
            result == null -> Panel(Modifier.fillMaxWidth().height(440.dp).entrance(1)) {
                Caption(stringResource(R.string.loading), color = Palette.faint)
            }
            result.isFailure -> Panel(Modifier.fillMaxWidth().entrance(1)) {
                Caption(stringResource(R.string.heart_unavailable), color = Palette.faint)
                Spacer(Modifier.height(14.dp))
                SoftButton(stringResource(R.string.heart_try_again), { attempt++ })
            }
            day == null -> Panel(Modifier.fillMaxWidth().entrance(1)) {
                Eyebrow(stringResource(R.string.heart_title))
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.heart_needs_access), style = MaterialTheme.typography.bodyMedium, color = Palette.muted)
                Spacer(Modifier.height(16.dp))
                PrimaryButton(stringResource(R.string.heart_set_up), onSetUp)
            }
            day.points.isEmpty() -> Panel(Modifier.fillMaxWidth().entrance(1)) {
                Caption(stringResource(R.string.heart_none), color = Palette.faint)
            }
            else -> key(day.date) {
                val first = day.date == date
                HeartChartPanel(day, loadDetail, if (first) initialView else null, if (first) initialSecond else null, Modifier.entrance(1))
            }
        }
    }
}

/** The day's heart rate in view, big: the readout and zoom buttons, the chart, the times along it, the whole day, the zones. */
@Composable
private fun HeartChartPanel(
    day: HeartDay,
    loadDetail: suspend (Instant, Instant) -> List<HeartPoint>,
    initialView: ClosedFloatingPointRange<Float>?,
    initialSecond: Int?,
    modifier: Modifier,
) {
    val n = day.minutes
    val zones = day.zones
    // As close as two minutes across.
    val view = remember(day) { SpanView(initialView ?: 0f..1f, (2f / n).coerceAtMost(1f)) }
    val selection = rememberChartSelection(initialSecond)
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val zone = ZoneId.systemDefault()
    val colors = Palette
    val useDetail = view.width * n <= DETAIL_WITHIN

    // Five seconds at a time, by 15-minute stretch: read once you're zoomed in close, a moment
    // after the view settles, the stretches in view and one either side.
    val detail = remember(day) { mutableStateMapOf<Int, List<HeartPoint>>() }
    val wanted by remember(day) {
        derivedStateOf {
            if (view.width * n > DETAIL_WITHIN) {
                null
            } else {
                val a = (view.left * n / CHUNK).toInt()
                val b = ((view.right * n - 0.01f) / CHUNK).toInt()
                (a - 1).coerceAtLeast(0)..(b + 1).coerceAtMost((n - 1) / CHUNK)
            }
        }
    }
    LaunchedEffect(wanted) {
        val range = wanted ?: return@LaunchedEffect
        delay(250)
        val missing = range.filter { it !in detail }
        if (missing.isEmpty()) return@LaunchedEffect
        val from = day.start.plus(Duration.ofMinutes(missing.first() * CHUNK.toLong()))
        val to = minOf(day.start.plus(Duration.ofMinutes((missing.last() + 1) * CHUNK.toLong())), day.end, Instant.now())
        if (!to.isAfter(from)) return@LaunchedEffect
        val points = try {
            loadDetail(from, to)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@LaunchedEffect
        }
        val byStretch = points.groupBy { (Duration.between(day.start, it.at).seconds / 60 / CHUNK).toInt() }
        for (c in missing.first()..missing.last()) detail[c] = byStretch[c].orEmpty()
    }
    val full = remember(day, detail.size, useDetail) { heartLine(day, detail, useDetail) }
    // With more minutes in view than fit a point every 1.5dp, the line is averaged to fit, as
    // Google draws a whole day; the grey bars still show each stretch's lowest and highest.
    // The chart's width: the screen less the page's and the panel's margins and the scale.
    val chartWidth = LocalConfiguration.current.screenWidthDp.dp - 80.dp - SCALE
    val perPoint = view.width * n * 60f / (chartWidth / 1.5.dp)
    val lineSeconds = if (perPoint <= 90f) null else LINE_SECONDS.lastOrNull { it <= perPoint } ?: LINE_SECONDS.first()
    val line = remember(full, lineSeconds) { lineSeconds?.let { full.averaged(it) } ?: full }
    val inView = useDetail && (view.left * n / CHUNK).toInt().let { detail[it]?.isNotEmpty() == true }

    val from = day.start.plusMillis((view.left * n * 60_000L).toLong())
    val to = day.start.plusMillis((view.right * n * 60_000L).toLong())
    val range = heartRange(day.points, from, to)
    val dayRange = day.range ?: (60 to 100)

    // The whole day on Google's scale, from 30 to above the peak zone; zoomed in, the scale closes
    // in on what's in view, so its rises and falls show.
    val fullLo = minOf(30f, dayRange.first - 6f)
    val fullHi = maxOf(zones.peak + 12f, dayRange.second + 8f)
    val (fitLo, fitHi) = range?.let { (lo, hi) ->
        var a = floor((lo - 8f) / 10f) * 10f
        var b = ceil((hi + 8f) / 10f) * 10f
        if (b - a < 40f) {
            val mid = (a + b) / 2
            a = mid - 20f
            b = mid + 20f
        }
        a.coerceAtLeast(fullLo) to b.coerceAtMost(fullHi)
    } ?: (fullLo to fullHi)
    val closeIn = ((1f - view.width) / 0.6f).coerceIn(0f, 1f)
    val yLo by animateFloatAsState(fullLo + (fitLo - fullLo) * closeIn, tween(320), label = "yLo")
    val yHi by animateFloatAsState(fullHi + (fitHi - fullHi) * closeIn, tween(320), label = "yHi")

    val picked = selection.index?.let { s -> line.nearest(s.toFloat()).takeIf { it >= 0 && abs(line.at[it] - s) <= line.reach(it) } }

    fun pick(across: Float) {
        val s = (view.left + across.coerceIn(0f, 0.9999f) * view.width) * n * 60f
        val i = line.nearest(s)
        val before = selection.index?.let { line.nearest(it.toFloat()) }
        val moved = when {
            before == null || i < 0 -> true
            // Close in, a tick each reading; further out, each quarter hour crossed or zone changed.
            useDetail || view.width * n <= 60f -> before != i
            else -> (line.at[before] / 900f).toInt() != (line.at[i] / 900f).toInt() || zones.of(line.bpm[before]) != zones.of(line.bpm[i])
        }
        if (moved) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        selection.index = s.toInt()
    }

    Panel(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    val big = when {
                        picked != null -> line.bpm[picked].roundToInt().toString()
                        selection.index != null -> "–"
                        range != null -> "${range.first}–${range.second}"
                        else -> "–"
                    }
                    Text(big, style = AppType.number(30f), color = colors.text, maxLines = 1)
                    Spacer(Modifier.width(6.dp))
                    Caption(stringResource(R.string.heart_bpm), Modifier.padding(bottom = 5.dp))
                }
                val s = selection.index
                when {
                    picked != null -> Row {
                        val start = day.start.plusMillis(((line.at[picked] - if (line.fine[picked]) 2.5f else 30f) * 1000).toLong())
                        Caption(if (line.fine[picked]) SECOND_TIME.format(start.atZone(zone)) else Format.time(start), color = colors.text)
                        Caption("  ·  ", color = colors.faint)
                        val z = zones.of(line.bpm[picked])
                        Caption(zoneName(z), color = zoneColor(z))
                    }
                    s != null -> Caption("${Format.time(day.start.plusSeconds(s.toLong()))}  ·  ${stringResource(R.string.heart_no_reading)}")
                    view.zoomed -> Caption("${Format.time(from)} – ${Format.time(to)}")
                    range != null -> Caption(stringResource(R.string.heart_high, range.second, zoneName(zones.of(range.second.toFloat())).lowercase()))
                }
            }
            ZoomButton("−", enabled = view.zoomed) { view.zoom(scope, 0.5f) }
            Spacer(Modifier.width(8.dp))
            ZoomButton("+", enabled = !view.atClosest) { view.zoom(scope, 2f) }
        }
        Spacer(Modifier.height(16.dp))
        Eyebrow(
            when {
                inView -> stringResource(R.string.heart_every_5s)
                useDetail -> stringResource(R.string.heart_reading_closer)
                lineSeconds != null -> stringResource(R.string.steps_every, lineSeconds / 60)
                else -> stringResource(R.string.steps_minute_by_minute)
            },
            color = if (inView) colors.accent else colors.muted,
        )
        Spacer(Modifier.height(12.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val chartSpace = maxWidth - SCALE
            val perMinute = chartSpace / (view.width * n)
            val barMinutes = HEART_BAR_MINUTES.firstOrNull { perMinute * it >= 7.dp } ?: 60
            val bars = remember(day, barMinutes) { heartBars(day.points, day.start, barMinutes) }
            val marks = ticks(from, to, zone, maxCount = (chartSpace / 64.dp).toInt().coerceAtLeast(2))
            val measurer = rememberTextMeasurer()
            val scaleStyle = AppType.number(11f).copy(color = colors.faint)
            Column {
                Row {
                    Canvas(
                        Modifier
                            .weight(1f)
                            .height(240.dp)
                            .zoomable(
                                view,
                                onTap = { pick(it); selection.releasedAt = System.currentTimeMillis() },
                                onDoubleTap = { if (view.atClosest) view.glideTo(scope, 0f, 1f) else view.zoom(scope, 3f, it) },
                                onScrub = { selection.releasedAt = 0L; pick(it) },
                                onScrubEnd = { selection.releasedAt = System.currentTimeMillis() },
                                onPinch = { focus, factor, pan -> view.pinch(focus, factor, pan) },
                            ),
                    ) {
                        drawHeart(day, line, bars, view, yLo, yHi, marks, picked)
                    }
                    // Each zone's heart rate beside its line, and the scale's bottom.
                    Canvas(Modifier.width(SCALE).height(240.dp)) {
                        val top = 8.dp.toPx()
                        val bottom = size.height - 6.dp.toPx()
                        fun y(bpm: Float) = bottom - (bpm - yLo) / (yHi - yLo) * (bottom - top)
                        val labels = mutableListOf<Pair<Float, Color>>()
                        for ((bpm, z) in zoneLines(zones)) labels += bpm.toFloat() to zoneColor(z).copy(alpha = 0.95f)
                        if (yLo <= 30.5f) labels += 30f to colors.faint
                        // Zoomed in, a few more along the scale.
                        if (closeIn > 0.5f) {
                            val step = if (yHi - yLo > 80f) 20f else 10f
                            var v = ceil(yLo / step) * step
                            while (v <= yHi) {
                                labels += v to colors.faint
                                v += step
                            }
                        }
                        val room = 16.dp.toPx()
                        // The zones first: they keep their places, the plain numbers fit round them.
                        val placed = mutableListOf<Float>()
                        for ((bpm, color) in labels) {
                            val yy = y(bpm)
                            if (yy < top - 2f || yy > bottom + 2f) continue
                            if (placed.any { abs(it - yy) < room }) continue
                            placed += yy
                            val m = measurer.measure("${bpm.roundToInt()}", scaleStyle.copy(color = color))
                            drawText(m, topLeft = Offset(size.width - m.size.width, (yy - m.size.height / 2f).coerceIn(0f, size.height - m.size.height)))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TickLabels(marks, from, to, Modifier.fillMaxWidth().padding(end = SCALE).height(16.dp))
                Spacer(Modifier.height(16.dp))
                WholeHeartDay(day, view, scope, Modifier.fillMaxWidth().padding(end = SCALE).height(44.dp))
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)) {
            HeartZone.entries.forEach { z ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(zoneColor(z)))
                    Spacer(Modifier.width(6.dp))
                    Text(zoneName(z), style = MaterialTheme.typography.labelMedium, color = colors.muted, maxLines = 1)
                }
            }
        }
        day.resting?.let {
            Spacer(Modifier.height(10.dp))
            Caption(
                stringResource(R.string.heart_resting, it.roundToInt()),
                Modifier.align(Alignment.CenterHorizontally),
                color = colors.faint,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Where each zone above light begins, lowest first. */
private fun zoneLines(zones: HeartZones): List<Pair<Int, HeartZone>> = listOf(
    zones.moderate to HeartZone.MODERATE,
    zones.vigorous to HeartZone.VIGOROUS,
    zones.peak to HeartZone.PEAK,
)

/**
 * The part of the day in [view]: faint lines at the [marks], each zone's dotted line (and the
 * scale's bottom at 30), the grey [bars] from lowest to highest, the line through each reading
 * coloured by its zone, and the [picked] reading lit.
 */
private fun DrawScope.drawHeart(
    day: HeartDay,
    line: HeartLine,
    bars: List<HeartBar>,
    view: SpanView,
    yLo: Float,
    yHi: Float,
    marks: List<Instant>,
    picked: Int?,
) {
    val n = day.minutes
    val l = view.left * n * 60f
    val r = view.right * n * 60f
    fun x(second: Float) = (second - l) / (r - l) * size.width
    val top = 8.dp.toPx()
    val bottom = size.height - 6.dp.toPx()
    fun y(bpm: Float) = bottom - (bpm - yLo) / (yHi - yLo) * (bottom - top)
    val faintLine = Color.White.copy(alpha = 0.05f)
    for (t in marks) {
        val tx = x(Duration.between(day.start, t).seconds.toFloat())
        drawLine(faintLine, Offset(tx, top), Offset(tx, bottom), 1.dp.toPx())
    }
    val dots = PathEffect.dashPathEffect(floatArrayOf(0.1f, 7.dp.toPx()))
    fun dotted(bpm: Float, color: Color) {
        val yy = y(bpm)
        if (yy < top - 1f || yy > bottom + 1f) return
        drawLine(color, Offset(2.dp.toPx(), yy), Offset(size.width, yy), 2.6.dp.toPx(), cap = StrokeCap.Round, pathEffect = dots)
    }
    if (yLo <= 30.5f) dotted(30f, zoneColor(HeartZone.LIGHT).copy(alpha = 0.8f))
    for ((bpm, z) in zoneLines(day.zones)) dotted(bpm.toFloat(), zoneColor(z).copy(alpha = 0.85f))

    clipRect {
        // Each stretch's lowest to highest, grey, behind the line.
        val perSecond = size.width / (r - l)
        for (b in bars) {
            val mid = (b.from + b.minutes / 2f) * 60f
            if (mid < l - b.minutes * 60f || mid > r + b.minutes * 60f) continue
            val w = (b.minutes * 60f * perSecond * 0.5f).coerceIn(2.dp.toPx(), 9.dp.toPx())
            val yTop = y(b.max)
            val h = (y(b.min) - yTop).coerceAtLeast(2.dp.toPx())
            drawRoundRect(
                Color.White.copy(alpha = 0.11f), Offset(x(mid) - w / 2, yTop), Size(w, h), CornerRadius(w / 2),
            )
        }
        // The line, a path for each zone, so each stretch takes the colour of where it got to.
        if (line.size > 0) {
            val first = (line.from(l) - 1).coerceAtLeast(0)
            val last = line.from(r).coerceAtMost(line.size - 1)
            val paths = HeartZone.entries.associateWith { Path() }
            val ends = HashMap<HeartZone, Offset>()
            val loose = mutableListOf<Int>()
            for (i in first..last) {
                val joinedBefore = i > 0 && line.joined(i - 1)
                val joinedAfter = i < line.size - 1 && line.joined(i)
                if (!joinedBefore && !joinedAfter) loose += i
                if (i == last || !joinedAfter) continue
                val a = Offset(x(line.at[i]), y(line.bpm[i]))
                val b = Offset(x(line.at[i + 1]), y(line.bpm[i + 1]))
                val z = day.zones.of(maxOf(line.bpm[i], line.bpm[i + 1]))
                val path = paths.getValue(z)
                if (ends[z] != a) path.moveTo(a.x, a.y)
                path.lineTo(b.x, b.y)
                ends[z] = b
            }
            val stroke = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            for ((z, path) in paths) drawPath(path, zoneColor(z), style = stroke)
            for (i in loose) drawCircle(zoneColor(day.zones.of(line.bpm[i])), 1.6.dp.toPx(), Offset(x(line.at[i]), y(line.bpm[i])))
        }
        // The reading under your finger.
        if (picked != null) {
            val px = x(line.at[picked])
            val py = y(line.bpm[picked])
            val color = zoneColor(day.zones.of(line.bpm[picked]))
            drawLine(
                Color.White.copy(alpha = 0.35f), Offset(px, top), Offset(px, bottom), 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
            )
            drawCircle(color.copy(alpha = 0.25f), 9.dp.toPx(), Offset(px, py))
            drawCircle(Color.White, 3.5.dp.toPx(), Offset(px, py))
        }
    }
}

/** The whole day small, in ten-minute bars from lowest to highest, with the part in view framed: drag along it to move, tap to jump. */
@Composable
private fun WholeHeartDay(day: HeartDay, view: SpanView, scope: CoroutineScope, modifier: Modifier) {
    val bars = remember(day) { heartBars(day.points, day.start, 10) }
    val colors = Palette
    Canvas(modifier.overviewGestures(view, scope)) {
        drawRoundRect(Color.White.copy(alpha = 0.04f), cornerRadius = CornerRadius(10.dp.toPx()))
        val (lo, hi) = day.range ?: return@Canvas
        val n = day.minutes.toFloat()
        val inset = 6.dp.toPx()
        val room = size.height - inset * 2
        val span = (hi - lo).coerceAtLeast(1).toFloat()
        fun y(bpm: Float) = size.height - inset - (bpm - lo) / span * room
        for (b in bars) {
            val left = b.from / n * size.width
            val right = (b.from + b.minutes) / n * size.width
            val yTop = y(b.max)
            val h = (y(b.min) - yTop).coerceAtLeast(1.5.dp.toPx())
            drawRoundRect(
                zoneColor(day.zones.of(b.max)).copy(alpha = 0.75f),
                Offset(left + 0.5.dp.toPx(), yTop),
                Size((right - left - 1.dp.toPx()).coerceAtLeast(1.dp.toPx()), h),
                CornerRadius(1.5.dp.toPx()),
            )
        }
        drawViewFrame(view, colors.text)
    }
}

/**
 * Today's heart rate on Today (or a day's on its page): the day's range, the resting rate, and
 * the day small, coloured by zone. Tap to open it up.
 */
@Composable
fun HeartCard(day: HeartDay, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val range = day.range ?: return
    val bars = remember(day) { heartBars(day.points, day.start, 15) }
    Panel(modifier.fillMaxWidth(), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(stringResource(R.string.heart_title), Modifier.weight(1f))
            day.resting?.let { Eyebrow(stringResource(R.string.heart_resting, it.roundToInt()), color = Palette.faint) }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Palette.faint)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${range.first}–${range.second}", style = AppType.number(30f), color = Palette.text, maxLines = 1)
            Spacer(Modifier.width(6.dp))
            Caption(stringResource(R.string.heart_bpm), Modifier.padding(bottom = 5.dp))
        }
        Spacer(Modifier.height(12.dp))
        Canvas(Modifier.fillMaxWidth().height(40.dp)) {
            val n = day.minutes.toFloat()
            val span = (range.second - range.first).coerceAtLeast(1).toFloat()
            fun y(bpm: Float) = size.height - (bpm - range.first) / span * size.height
            for (b in bars) {
                val cx = (b.from + b.minutes / 2f) / n * size.width
                val w = (b.minutes / n * size.width * 0.55f).coerceAtLeast(1.5.dp.toPx())
                val yTop = y(b.max)
                val h = (y(b.min) - yTop).coerceAtLeast(2.dp.toPx())
                drawRoundRect(zoneColor(day.zones.of(b.max)), Offset(cx - w / 2, yTop), Size(w, h), CornerRadius(w / 2))
            }
        }
    }
}
