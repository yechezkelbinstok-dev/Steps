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
import androidx.compose.runtime.Stable
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
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Heart rate read closer than a minute at a time, once zoomed in to [within] minutes across or
 * fewer: [seconds] at a time, in stretches of [chunk] minutes.
 */
private enum class Fine(val seconds: Int, val within: Float, val chunk: Int) {
    /** From half an hour across, five seconds at a time. */
    FIVE(5, 30f, 15),

    /** From five minutes across, a second at a time: every reading Google has (it keeps heart rate to the second). */
    ONE(1, 5f, 5),
}

/** As close as this many seconds across: a second a few dots wide. */
private const val CLOSEST_SECONDS = 30f

/** How many seconds each grey bar can stand for, finest first. */
private val HEART_BAR_SECONDS = listOf(5, 10, 15, 30, 60, 120, 180, 300, 600, 900, 1200, 1800, 3600)

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
 * average, and how many seconds it stands for ([window]: a minute, five seconds or one, or more
 * when averaged down to fit the screen).
 */
private class HeartLine(val at: FloatArray, val bpm: FloatArray, val window: IntArray) {
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
    fun joined(i: Int): Boolean {
        val w = maxOf(window[i], window[i + 1])
        return at[i + 1] - at[i] <= if (w < 60) 20f else maxOf(330f, w * 1.6f)
    }

    /** How far from [second] point [i] can be and still be what was there then. */
    fun reach(i: Int): Float = if (window[i] < 60) 15f else maxOf(150f, window[i].toFloat())

    /** When point [i]'s reading began, in seconds from midnight. */
    fun began(i: Int): Float = at[i] - window[i] / 2f

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
        return HeartLine(at.toFloatArray(), bpm.toFloatArray(), IntArray(at.size) { seconds })
    }
}

/** How many seconds each point of the line can stand for when averaged to fit, finest first. */
private val LINE_SECONDS = listOf(120, 180, 300, 600)

/** Readings, oldest first, that each stand for [window] seconds. */
private class HeartReadings(val window: Int, val points: List<HeartPoint>)

/**
 * The day's heart rate at the finest there is of it: the day's minutes, but where a stretch was
 * read closer ([fine], by level and stretch, for the levels [using]), those readings instead,
 * and a second at a time over five seconds at a time.
 */
private fun heartReadings(day: HeartDay, fine: Map<Fine, Map<Int, List<HeartPoint>>>, using: List<Fine>): List<HeartReadings> {
    fun covered(f: Fine, p: HeartPoint): Boolean {
        val minute = Duration.between(day.start, p.at).seconds / 60f
        return fine[f]?.get((minute / f.chunk).toInt())?.isNotEmpty() == true
    }
    val out = mutableListOf(HeartReadings(60, day.points.filter { p -> using.none { covered(it, p) } }))
    for (f in using) {
        val finer = using.filter { it.seconds < f.seconds }
        val points = fine[f].orEmpty().values.flatten().filter { p -> finer.none { covered(it, p) } }.sortedBy { it.at }
        out += HeartReadings(f.seconds, points)
    }
    return out
}

/** [readings] as one line, each reading at its middle. */
private fun heartLine(day: HeartDay, readings: List<HeartReadings>): HeartLine {
    val count = readings.sumOf { it.points.size }
    val order = ArrayList<Pair<Float, Int>>(count)
    val bpm = FloatArray(count)
    val window = IntArray(count)
    var k = 0
    for (r in readings) for (p in r.points) {
        order += Duration.between(day.start, p.at).toMillis() / 1000f + r.window / 2f to k
        bpm[k] = p.avg
        window[k] = r.window
        k++
    }
    order.sortBy { it.first }
    return HeartLine(
        FloatArray(count) { order[it].first },
        FloatArray(count) { bpm[order[it].second] },
        IntArray(count) { window[order[it].second] },
    )
}

/** What's been read of one [Fine] level: its readings by stretch (empty where there were none), and whether reading last failed. */
@Stable
private class FineReadings {
    val byStretch = mutableStateMapOf<Int, List<HeartPoint>>()
    var failed by mutableStateOf(false)
}

/**
 * [fine]'s readings of [day], read once zoomed in to [Fine.within] minutes across: a moment after
 * the view settles, the stretches in view and one either side. A stretch that couldn't be read is
 * tried again when the view moves on to another.
 */
@Composable
private fun rememberFineReadings(
    day: HeartDay,
    fine: Fine,
    view: SpanView,
    load: suspend (Instant, Instant, Duration) -> List<HeartPoint>,
): FineReadings {
    val n = day.minutes
    val readings = remember(day) { FineReadings() }
    val wanted by remember(day) {
        derivedStateOf {
            if (view.width * n > fine.within) {
                null
            } else {
                val a = (view.left * n / fine.chunk).toInt()
                val b = ((view.right * n - 0.01f) / fine.chunk).toInt()
                (a - 1).coerceAtLeast(0)..(b + 1).coerceAtMost((n - 1) / fine.chunk)
            }
        }
    }
    LaunchedEffect(wanted) {
        val range = wanted ?: return@LaunchedEffect
        delay(250)
        val missing = range.filter { it !in readings.byStretch }
        if (missing.isEmpty()) return@LaunchedEffect
        val from = day.start.plus(Duration.ofMinutes(missing.first() * fine.chunk.toLong()))
        val to = minOf(day.start.plus(Duration.ofMinutes((missing.last() + 1) * fine.chunk.toLong())), day.end, Instant.now())
        if (!to.isAfter(from)) return@LaunchedEffect
        val points = try {
            load(from, to, Duration.ofSeconds(fine.seconds.toLong()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            readings.failed = true
            return@LaunchedEffect
        }
        readings.failed = false
        val byStretch = points.groupBy { (Duration.between(day.start, it.at).seconds / 60 / fine.chunk).toInt() }
        for (c in missing.first()..missing.last()) readings.byStretch[c] = byStretch[c].orEmpty()
    }
    return readings
}

/**
 * A day's heart rate as Google's heart rate page shows it, midnight to midnight, opened up: the
 * day's range and its high, each zone's line, grey bars from each stretch's lowest to its highest
 * and the minute-by-minute line through them, coloured by zone. Zoom in (pinch, double-tap, or −
 * and +) and it closes in, down to half a minute across: read five seconds at a time from 30
 * minutes across, and a second at a time (every reading, a dot each) from five; the scale closes
 * in with it. Drag along it to read the heart rate under your finger, to the second close in.
 * The whole day sits below with the part in view framed. ‹ and › go a day back and forward.
 */
@Composable
fun HeartExplorer(
    date: LocalDate,
    loadDay: suspend (LocalDate) -> HeartDay?,
    /** Heart rate from one time to another in windows of the given size (five seconds, or one). */
    loadDetail: suspend (Instant, Instant, Duration) -> List<HeartPoint>,
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
    loadDetail: suspend (Instant, Instant, Duration) -> List<HeartPoint>,
    initialView: ClosedFloatingPointRange<Float>?,
    initialSecond: Int?,
    modifier: Modifier,
) {
    val n = day.minutes
    val zones = day.zones
    // As close as half a minute across.
    val view = remember(day) { SpanView(initialView ?: 0f..1f, (CLOSEST_SECONDS / 60f / n).coerceAtMost(1f)) }
    val selection = rememberChartSelection(initialSecond)
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val zone = ZoneId.systemDefault()
    val colors = Palette

    // Closer than a minute at a time, read once zoomed in close enough for each.
    val fine = Fine.entries.associateWith { key(it) { rememberFineReadings(day, it, view, loadDetail) } }
    val using = Fine.entries.filter { view.width * n <= it.within }
    val readings = remember(day, using, fine.values.map { it.byStretch.size }) {
        heartReadings(day, fine.mapValues { it.value.byStretch }, using)
    }
    val full = remember(readings) { heartLine(day, readings) }
    // With more minutes in view than fit a point every 1.5dp, the line is averaged to fit, as
    // Google draws a whole day; the grey bars still show each stretch's lowest and highest.
    // The chart's width: the screen less the page's and the panel's margins and the scale.
    val chartWidth = LocalConfiguration.current.screenWidthDp.dp - 80.dp - SCALE
    val perPoint = view.width * n * 60f / (chartWidth / 1.5.dp)
    val lineSeconds = if (perPoint <= 90f) null else LINE_SECONDS.lastOrNull { it <= perPoint } ?: LINE_SECONDS.first()
    val line = remember(full, lineSeconds) { lineSeconds?.let { full.averaged(it) } ?: full }
    // The finest readings in the middle of the view, and whether finer ones are still being read.
    val middle = (view.left + view.right) / 2f * n
    fun has(f: Fine) = fine.getValue(f).byStretch[(middle / f.chunk).toInt()]?.isNotEmpty() == true
    val shownFine = using.lastOrNull { has(it) }
    val readingCloser = using.lastOrNull()?.let { f ->
        f != shownFine && (middle / f.chunk).toInt() !in fine.getValue(f).byStretch && !fine.getValue(f).failed
    } == true
    // Down to the second: times to the second, and a dot for each reading once they're apart.
    val toTheSecond = using.isNotEmpty()

    val from = day.start.plusMillis((view.left * n * 60_000L).toLong())
    val to = day.start.plusMillis((view.right * n * 60_000L).toLong())
    val range = readings
        .mapNotNull { heartRange(it.points, from, to, Duration.ofSeconds(it.window.toLong())) }
        .reduceOrNull { a, b -> minOf(a.first, b.first) to maxOf(a.second, b.second) }
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

    // The selection is the second under the finger; it's looked up at that second's middle.
    val picked = selection.index?.let { s ->
        val at = s + 0.5f
        line.nearest(at).takeIf { it >= 0 && abs(line.at[it] - at) <= line.reach(it) }
    }

    fun pick(across: Float) {
        val s = (view.left + across.coerceIn(0f, 0.9999f) * view.width) * n * 60f
        val i = line.nearest(s)
        val before = selection.index?.let { line.nearest(it + 0.5f) }
        val moved = when {
            before == null || i < 0 -> true
            // Close in, a tick each reading; further out, each quarter hour crossed or zone changed.
            toTheSecond || view.width * n <= 60f -> before != i
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
                // To the nearest second, so a time a few milliseconds short of one isn't a second early.
                fun time(at: Instant, seconds: Boolean): String {
                    val t = at.plusMillis(500).truncatedTo(ChronoUnit.SECONDS)
                    return if (seconds) SECOND_TIME.format(t.atZone(zone)) else Format.time(t)
                }
                when {
                    picked != null -> Row {
                        val start = day.start.plusMillis((line.began(picked) * 1000).toLong())
                        Caption(time(start, line.window[picked] < 60), color = colors.text)
                        Caption("  ·  ", color = colors.faint)
                        val z = zones.of(line.bpm[picked])
                        Caption(zoneName(z), color = zoneColor(z))
                    }
                    s != null -> Caption("${time(day.start.plusSeconds(s.toLong()), toTheSecond)}  ·  ${stringResource(R.string.heart_no_reading)}")
                    // Under a couple of minutes across, to the second, or both ends could read the same.
                    view.zoomed -> {
                        val seconds = view.width * n < 2f
                        Caption("${time(from, seconds)} – ${time(to, seconds)}")
                    }
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
                readingCloser -> stringResource(R.string.heart_reading_closer)
                // A reading every second, or (resting, when the band reads less often) each one it took.
                shownFine == Fine.ONE -> {
                    val l = view.left * n * 60f
                    val r = view.right * n * 60f
                    val count = line.from(r) - line.from(l)
                    stringResource(if (count >= (r - l) * 0.6f) R.string.heart_every_second else R.string.heart_every_reading)
                }
                shownFine == Fine.FIVE -> stringResource(R.string.heart_every_5s)
                lineSeconds != null -> stringResource(R.string.steps_every, lineSeconds / 60)
                else -> stringResource(R.string.steps_minute_by_minute)
            },
            color = if (shownFine != null) colors.accent else colors.muted,
        )
        Spacer(Modifier.height(12.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val chartSpace = maxWidth - SCALE
            val perSecond = chartSpace / (view.width * n * 60f)
            val barSeconds = HEART_BAR_SECONDS.firstOrNull { perSecond * it >= 7.dp } ?: 3600
            // Each reading in a bar at least as long as it.
            val bars = remember(readings, barSeconds) {
                readings.flatMap { heartBars(it.points, day.start, maxOf(barSeconds, it.window)) }
            }
            val marks = ticks(from, to, zone, maxCount = (chartSpace / 64.dp).toInt().coerceAtLeast(2), finest = 5)
            // Close in, with the readings well apart, a dot for each: every second there is.
            val inView = line.from(view.right * n * 60f) - line.from(view.left * n * 60f)
            val dots = toTheSecond && chartSpace / inView.coerceAtLeast(1) >= 5.dp
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
                        drawHeart(day, line, bars, view, yLo, yHi, marks, picked, dots)
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
 * coloured by its zone, and the [picked] reading lit. With [dots], each reading closer than a
 * minute has a dot, and the bars are left out: they'd only repeat the readings.
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
    dots: Boolean,
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
    val dotted = PathEffect.dashPathEffect(floatArrayOf(0.1f, 7.dp.toPx()))
    fun dotted(bpm: Float, color: Color) {
        val yy = y(bpm)
        if (yy < top - 1f || yy > bottom + 1f) return
        drawLine(color, Offset(2.dp.toPx(), yy), Offset(size.width, yy), 2.6.dp.toPx(), cap = StrokeCap.Round, pathEffect = dotted)
    }
    if (yLo <= 30.5f) dotted(30f, zoneColor(HeartZone.LIGHT).copy(alpha = 0.8f))
    for ((bpm, z) in zoneLines(day.zones)) dotted(bpm.toFloat(), zoneColor(z).copy(alpha = 0.85f))

    clipRect {
        // Each stretch's lowest to highest, grey, behind the line.
        val perSecond = size.width / (r - l)
        if (!dots) for (b in bars) {
            val mid = b.from + b.seconds / 2f
            if (mid < l - b.seconds || mid > r + b.seconds) continue
            val w = (b.seconds * perSecond * 0.5f).coerceIn(2.dp.toPx(), 9.dp.toPx())
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
            // A dot for each reading closer than a minute, with [dots]; otherwise only those standing alone have one.
            fun dot(i: Int) = dots && line.window[i] < 60
            for (i in loose) if (!dot(i)) drawCircle(zoneColor(day.zones.of(line.bpm[i])), 1.6.dp.toPx(), Offset(x(line.at[i]), y(line.bpm[i])))
            if (dots) {
                for (i in first..last) if (dot(i)) drawCircle(zoneColor(day.zones.of(line.bpm[i])), 2.6.dp.toPx(), Offset(x(line.at[i]), y(line.bpm[i])))
            }
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
    val bars = remember(day) { heartBars(day.points, day.start, 10 * 60) }
    val colors = Palette
    Canvas(modifier.overviewGestures(view, scope)) {
        drawRoundRect(Color.White.copy(alpha = 0.04f), cornerRadius = CornerRadius(10.dp.toPx()))
        val (lo, hi) = day.range ?: return@Canvas
        val n = day.minutes * 60f
        val inset = 6.dp.toPx()
        val room = size.height - inset * 2
        val span = (hi - lo).coerceAtLeast(1).toFloat()
        fun y(bpm: Float) = size.height - inset - (bpm - lo) / span * room
        for (b in bars) {
            val left = b.from / n * size.width
            val right = (b.from + b.seconds) / n * size.width
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
    val bars = remember(day) { heartBars(day.points, day.start, 15 * 60) }
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
            val n = day.minutes * 60f
            val span = (range.second - range.first).coerceAtLeast(1).toFloat()
            fun y(bpm: Float) = size.height - (bpm - range.first) / span * size.height
            for (b in bars) {
                val cx = (b.from + b.seconds / 2f) / n * size.width
                val w = (b.seconds / n * size.width * 0.55f).coerceAtLeast(1.5.dp.toPx())
                val yTop = y(b.max)
                val h = (y(b.min) - yTop).coerceAtLeast(2.dp.toPx())
                drawRoundRect(zoneColor(day.zones.of(b.max)), Offset(cx - w / 2, yTop), Size(w, h), CornerRadius(w / 2))
            }
        }
    }
}
