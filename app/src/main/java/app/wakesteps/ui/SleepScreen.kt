package app.wakesteps.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wakesteps.R
import app.wakesteps.core.Format
import app.wakesteps.core.SleepNight
import app.wakesteps.core.SleepStage
import app.wakesteps.core.StageSpan
import app.wakesteps.ui.theme.AppType
import app.wakesteps.ui.theme.Palette
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlin.math.roundToLong

// ---------------------------------------------------------------- stages

/** Each stage's colour: a night sky from deep indigo up to aqua, with time awake in warm coral. */
fun stageColor(stage: SleepStage): Color = when (stage) {
    SleepStage.DEEP -> Color(0xFF6C5CFF)
    SleepStage.LIGHT, SleepStage.ASLEEP -> Color(0xFF8AB4FF)
    SleepStage.REM -> Color(0xFF63DCE6)
    SleepStage.AWAKE -> Color(0xFFFF9E80)
    SleepStage.RESTLESS -> Color(0xFFFFC46E)
}

/** A stage's colour lifted for text, so a name in deep indigo still reads on the dark panels. */
fun stageTextColor(stage: SleepStage): Color = lerp(stageColor(stage), Color.White, 0.35f)

@Composable
fun stageName(stage: SleepStage): String = stringResource(
    when (stage) {
        SleepStage.AWAKE -> R.string.stage_awake
        SleepStage.REM -> R.string.stage_rem
        SleepStage.LIGHT -> R.string.stage_light
        SleepStage.DEEP -> R.string.stage_deep
        SleepStage.RESTLESS -> R.string.stage_restless
        SleepStage.ASLEEP -> R.string.stage_asleep
    }
)

/** Deepest first: the order of the ring and of the stacked nights. */
private val DEEPEST_FIRST = listOf(SleepStage.DEEP, SleepStage.LIGHT, SleepStage.ASLEEP, SleepStage.REM, SleepStage.RESTLESS, SleepStage.AWAKE)

/** Each stage's share of the night, deepest first; a night without stages is all asleep. */
private fun shares(night: SleepNight): List<Pair<SleepStage, Float>> {
    if (night.stages.isEmpty()) return listOf(SleepStage.ASLEEP to 1f)
    val total = night.inBed.toMillis().coerceAtLeast(1).toFloat()
    return DEEPEST_FIRST.mapNotNull { s -> night.time(s).toMillis().takeIf { it > 0 }?.let { s to it / total } }
}

private fun stagesOrWhole(night: SleepNight): List<StageSpan> =
    night.stages.ifEmpty { listOf(StageSpan(night.start, night.end, SleepStage.ASLEEP)) }

// ---------------------------------------------------------------- numbers

/** "7h 05m" with the digits in display type and the letters small and quiet. */
@Composable
fun durationText(minutes: Long, size: Float): AnnotatedString {
    val unit = SpanStyle(fontSize = (size * 0.45f).sp, color = Palette.muted)
    return buildAnnotatedString {
        if (minutes >= 60) {
            append("${minutes / 60}")
            withStyle(unit) { append("h") }
            append("\u2009") // a thin space at full size, so the minutes don't crowd the h
            append("%02d".format(minutes % 60))
        } else {
            append("$minutes")
        }
        withStyle(unit) { append("m") }
    }
}

/** A duration that counts up when it first appears and glides to each new value. */
@Composable
fun DurationNumber(minutes: Long, size: Float, modifier: Modifier = Modifier, delayMillis: Int = 0) {
    val shown by animateOnShow(minutes.toFloat(), durationMillis = 1500, delayMillis = delayMillis)
    Text(
        durationText(shown.roundToLong().coerceAtLeast(0), size),
        style = AppType.number(size), color = Palette.text, modifier = modifier, maxLines = 1,
    )
}

private fun times(night: SleepNight) = "${Format.time(night.start)} – ${Format.time(night.end)}"

// ---------------------------------------------------------------- ring and strip

/**
 * The night as a ring: each stage's share of the time in bed, deepest first, clockwise from 12,
 * with a little air between them. It sweeps round when it first appears. [content] sits inside.
 */
@Composable
fun SleepRing(
    night: SleepNight,
    size: Dp,
    modifier: Modifier = Modifier,
    strokeFraction: Float = 0.082f,
    glow: Boolean = true,
    delayMillis: Int = 0,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val reveal by animateOnShow(1f, durationMillis = 1500, delayMillis = delayMillis)
    val track = Palette.track
    val parts = shares(night)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = this.size.minDimension * strokeFraction
            val arc = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(stroke / 2, stroke / 2)
            if (glow) {
                val deep = stageColor(SleepStage.DEEP)
                drawCircle(
                    Brush.radialGradient(
                        listOf(deep.copy(alpha = 0.26f), deep.copy(alpha = 0.06f), Color.Transparent),
                        radius = this.size.minDimension / 2,
                    ),
                )
            }
            drawArc(track, 0f, 360f, false, topLeft, arc, style = Stroke(stroke))
            // Round caps reach past an arc's ends; leave room so neighbours don't touch.
            val gap = if (parts.size > 1) Math.toDegrees(stroke * 1.25 / (arc.width / 2)).toFloat() else 0f
            val shown = 360f * reveal
            rotate(-90f) {
                var at = 0f
                for ((stage, share) in parts) {
                    val sweep = 360f * share
                    val start = at + gap / 2
                    val end = minOf(at + sweep - gap / 2, shown)
                    at += sweep
                    if (shown <= start) continue
                    // A sliver too thin for its gap still shows, as a dot.
                    drawArc(
                        stageColor(stage), start, (end - start).coerceAtLeast(0.01f), false, topLeft, arc,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
        }
        content()
    }
}

/** The night left to right as one slim bar, each stretch in its stage's colour. */
@Composable
fun NightStrip(night: SleepNight, modifier: Modifier = Modifier) {
    val reveal by animateOnShow(1f, durationMillis = 1100, delayMillis = 250)
    val track = Palette.track
    Canvas(modifier) {
        val total = night.inBed.toMillis().coerceAtLeast(1).toFloat()
        val bar = Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(size.height / 2))) }
        clipPath(bar) {
            drawRect(track)
            for (s in stagesOrWhole(night)) {
                val a = Duration.between(night.start, s.start).toMillis() / total * size.width
                val b = minOf(Duration.between(night.start, s.end).toMillis() / total * size.width, size.width * reveal)
                if (b > a) drawRect(stageColor(s.stage), Offset(a, 0f), Size(b - a, size.height))
            }
        }
    }
}

// ---------------------------------------------------------------- a night in full

/** The ring with the time asleep in it, and when the night began and ended. */
@Composable
private fun SleepHero(night: SleepNight, modifier: Modifier, ringSize: Dp) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        SleepRing(night, ringSize, delayMillis = 150) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painterResource(R.drawable.ic_moon),
                    contentDescription = null,
                    tint = stageColor(SleepStage.LIGHT),
                    modifier = Modifier.size(ringSize * 0.09f),
                )
                Spacer(Modifier.height(4.dp))
                DurationNumber(night.asleep.toMinutes(), ringSize.value * 0.19f, delayMillis = 150)
                Eyebrow(stringResource(R.string.sleep_asleep))
                Spacer(Modifier.height(ringSize * 0.04f))
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            times(night),
            style = MaterialTheme.typography.labelLarge,
            color = Palette.muted,
            modifier = Modifier
                .clip(CircleShape)
                .background(Palette.card)
                .border(1.dp, glassEdge(), CircleShape)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

/**
 * Two or three numbers a row: how well, how fast, how often woken, how restless. A night with no
 * awake time recorded at all (one session without stages) says nothing about efficiency or
 * waking up, rather than claiming a perfect night.
 */
@Composable
private fun SleepStats(night: SleepNight, modifier: Modifier) {
    val knowsAwake = night.stages.any { it.stage != SleepStage.ASLEEP }
    val tiles = buildList<@Composable (Modifier) -> Unit> {
        if (knowsAwake) add { NumberTile(stringResource(R.string.sleep_efficiency), (night.efficiency * 100).roundToInt().toDouble(), "%", it) }
        night.fellAsleepIn?.let { d -> add { NumberTile(stringResource(R.string.sleep_fell_asleep), d.toMinutes().toDouble(), "min", it) } }
        if (knowsAwake) add { NumberTile(stringResource(R.string.sleep_woke_up), night.wakeUps.toDouble(), "×", it) }
        if (night.shortAwakenings > 0) {
            add { NumberTile(stringResource(R.string.sleep_restless), night.shortAwakenings.toDouble(), "×", it) }
        } else {
            add { DurationTile(stringResource(R.string.sleep_in_bed), night.inBed.toMinutes(), it) }
        }
    }.take(4)
    if (tiles.size < 2) return // just the time in bed, which the ring already tells
    Panel(modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
        tiles.chunked(if (tiles.size == 3) 3 else 2).forEachIndexed { row, chunk ->
            if (row > 0) Box(Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.07f)))
            Row(Modifier.height(IntrinsicSize.Min)) {
                chunk.forEachIndexed { i, tile ->
                    if (i > 0) VerticalRule(Modifier.padding(vertical = 18.dp))
                    tile(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun NumberTile(label: String, value: Double, unit: String, modifier: Modifier) {
    Column(modifier.padding(20.dp)) {
        Eyebrow(label)
        Spacer(Modifier.height(12.dp))
        ValueWithUnit(value, 30f, unit, delayMillis = 300)
    }
}

@Composable
private fun DurationTile(label: String, minutes: Long, modifier: Modifier) {
    Column(modifier.padding(20.dp)) {
        Eyebrow(label)
        Spacer(Modifier.height(12.dp))
        DurationNumber(minutes, 30f, delayMillis = 300)
    }
}

/** Time in each stage, with its share of the night as a bar that fills in. */
@Composable
private fun StageBreakdown(night: SleepNight, modifier: Modifier) {
    val grow by animateOnShow(1f, durationMillis = 1100, delayMillis = 250)
    val total = night.inBed.toMillis().coerceAtLeast(1).toFloat()
    val track = Color.White.copy(alpha = 0.06f)
    Panel(modifier.fillMaxWidth()) {
        Eyebrow(stringResource(R.string.sleep_stages))
        night.lanes.forEach { stage ->
            val time = night.time(stage)
            val share = time.toMillis() / total
            val color = stageColor(stage)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(10.dp))
                Text(stageName(stage), style = MaterialTheme.typography.bodyLarge, color = Palette.text, modifier = Modifier.weight(1f))
                Text(durationText(time.toMinutes(), 20f), style = AppType.number(20f), color = Palette.text)
                Text(
                    "${(share * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = Palette.muted,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(48.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                val r = CornerRadius(size.height / 2)
                drawRoundRect(track, cornerRadius = r)
                val w = size.width * share * grow
                if (w > 0f) {
                    drawRoundRect(
                        Brush.horizontalGradient(listOf(color.copy(alpha = 0.6f), color), endX = w.coerceAtLeast(1f)),
                        size = Size(w.coerceAtLeast(size.height), size.height),
                        cornerRadius = r,
                    )
                }
            }
        }
    }
}

/**
 * The night stage by stage, in rows from awake down to deep, sweeping in from bedtime. Touch or
 * drag along it to read the time and stage under your finger, with a tick each time the stage
 * changes; otherwise it shows the night's longest stretch of deep sleep.
 */
@Composable
fun NightChart(night: SleepNight, modifier: Modifier = Modifier, initialMinute: Int? = null) {
    val lanes = night.lanes
    val minutes = night.inBed.toMinutes().toInt().coerceAtLeast(1)
    val selection = rememberChartSelection(initialMinute)
    val haptics = LocalHapticFeedback.current
    val reveal by animateOnShow(1f, durationMillis = 1400, delayMillis = 300)
    val colors = Palette
    fun minuteAt(m: Int): Instant = night.start.plus(Duration.ofMinutes(m.toLong()))
    val picked = selection.index?.let(::minuteAt)
    val pickedStage = picked?.let { night.stageAt(it) }
    val anchor = if (night.staged) SleepStage.DEEP else SleepStage.ASLEEP
    val longest = night.stages.filter { it.stage == anchor }.maxByOrNull { it.duration }

    fun pick(fraction: Float) {
        val m = (fraction * minutes).toInt()
        val before = selection.index?.let { night.stageAt(minuteAt(it)) }
        if (selection.index == null || night.stageAt(minuteAt(m)) != before) {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
        selection.index = m
    }

    Panel(modifier.fillMaxWidth()) {
        Eyebrow(stringResource(R.string.sleep_through_night))
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            if (picked != null) {
                Text(Format.time(picked), style = AppType.number(34f), color = colors.text)
                Spacer(Modifier.width(10.dp))
                val tone by animateColorAsState(pickedStage?.let(::stageTextColor) ?: colors.muted, label = "stage")
                Caption(pickedStage?.let { stageName(it) } ?: "", Modifier.padding(bottom = 6.dp), color = tone)
            } else if (longest != null) {
                Text(durationText(longest.duration.toMinutes(), 34f), style = AppType.number(34f), color = colors.text)
                Spacer(Modifier.width(10.dp))
                Caption(
                    stringResource(
                        if (night.staged) R.string.sleep_longest_deep else R.string.sleep_longest,
                        Format.time(longest.start),
                    ),
                    Modifier.padding(bottom = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth().height((lanes.size * 40).dp)) {
            Column(Modifier.width(LANE_LABELS).fillMaxHeight()) {
                lanes.forEach { lane ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        val tone by animateColorAsState(if (lane == pickedStage) stageTextColor(lane) else colors.faint, label = "lane")
                        Text(stageName(lane).uppercase(), style = AppType.eyebrow.copy(fontSize = 10.sp), color = tone, maxLines = 1)
                    }
                }
            }
            Canvas(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .scrubbable(
                        night,
                        onTap = { pick(it); selection.releasedAt = System.currentTimeMillis() },
                        onScrub = { selection.releasedAt = 0L; pick(it) },
                        onEnd = { selection.releasedAt = System.currentTimeMillis() },
                    ),
            ) {
                val laneHeight = size.height / lanes.size
                fun laneY(stage: SleepStage) = (lanes.indexOf(stage).takeIf { it >= 0 } ?: lanes.lastIndex) * laneHeight + laneHeight / 2
                val total = night.inBed.toMillis().coerceAtLeast(1).toFloat()
                fun x(t: Instant) = Duration.between(night.start, t).toMillis() / total * size.width
                val barHeight = laneHeight * 0.54f
                for (i in lanes.indices) {
                    val y = i * laneHeight + laneHeight / 2
                    drawLine(Color.White.copy(alpha = 0.04f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                }
                val stages = stagesOrWhole(night)
                clipRect(right = size.width * reveal) {
                    // Steps between stages first, so the bars sit on top of them.
                    stages.zipWithNext { a, b ->
                        val at = x(b.start)
                        drawLine(Color.White.copy(alpha = 0.16f), Offset(at, laneY(a.stage)), Offset(at, laneY(b.stage)), 1.5.dp.toPx())
                    }
                    for (s in stages) {
                        val left = x(s.start)
                        val width = (x(s.end) - left).coerceAtLeast(2.dp.toPx())
                        val top = laneY(s.stage) - barHeight / 2
                        val color = stageColor(s.stage)
                        drawRoundRect(
                            Brush.verticalGradient(listOf(color, color.copy(alpha = 0.7f)), startY = top, endY = top + barHeight),
                            Offset(left, top), Size(width, barHeight), CornerRadius(barHeight / 2.6f),
                        )
                    }
                }
                if (picked != null) {
                    val at = x(picked)
                    drawLine(
                        colors.text.copy(alpha = 0.35f), Offset(at, 0f), Offset(at, size.height), 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
                    )
                    pickedStage?.let { s ->
                        drawCircle(stageColor(s).copy(alpha = 0.25f), 11.dp.toPx(), Offset(at, laneY(s)))
                        drawCircle(stageColor(s), 5.dp.toPx(), Offset(at, laneY(s)))
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().padding(start = LANE_LABELS), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(Format.time(night.start), style = AppType.eyebrow, color = colors.faint)
            Text(Format.time(night.end), style = AppType.eyebrow, color = colors.faint)
        }
    }
}

private val LANE_LABELS = 62.dp

/** The ring, the numbers, the stages and the chart through the night, entering in turn from [first]. */
@Composable
private fun NightContent(night: SleepNight, ringSize: Dp, first: Int, chartMinute: Int?) {
    SleepHero(night, Modifier.entrance(first), ringSize)
    SleepStats(night, Modifier.entrance(first + 1))
    if (night.staged) StageBreakdown(night, Modifier.entrance(first + 2))
    if (night.stages.size > 1) NightChart(night, Modifier.entrance(first + 3), chartMinute)
    if (!night.staged) {
        Text(
            stringResource(R.string.sleep_no_stages),
            style = MaterialTheme.typography.bodySmall,
            color = Palette.faint,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).entrance(first + 4),
        )
    }
}

// ---------------------------------------------------------------- the week

/**
 * The last week of nights as stacked bars, deep at the bottom up to awake at the top. The readout
 * shows the average time asleep, or the night you're touching.
 */
@Composable
private fun NightsPanel(nights: List<SleepNight>, modifier: Modifier, initialSelection: Int?) {
    val selection = rememberChartSelection(initialSelection)
    val average = nights.map { it.asleep.toMinutes() }.average().roundToLong()
    val picked = selection.index?.let { nights.getOrNull(it) }
    val zone = ZoneId.systemDefault()
    Panel(modifier.fillMaxWidth()) {
        Eyebrow(stringResource(R.string.sleep_week))
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            if (picked != null) {
                Text(durationText(picked.asleep.toMinutes(), 34f), style = AppType.number(34f), color = Palette.text)
            } else {
                DurationNumber(average, 34f)
            }
            Spacer(Modifier.width(10.dp))
            Caption(
                picked?.let { Format.day(it.end) } ?: stringResource(R.string.sleep_average),
                Modifier.padding(bottom = 6.dp),
                color = if (picked != null) Palette.text else Palette.muted,
            )
        }
        Spacer(Modifier.height(18.dp))
        StackedNights(nights, selection, Modifier.fillMaxWidth().height(130.dp))
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            nights.forEachIndexed { i, night ->
                val color by animateColorAsState(if (selection.index == i) Palette.text else Palette.faint, label = "nightInitial")
                Text(
                    DateTimeFormatter.ofPattern("EEEEE").format(night.end.atZone(zone)),
                    style = MaterialTheme.typography.labelMedium,
                    color = color,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** One stacked bar per night, growing in left to right, scrubbable like the other charts. */
@Composable
private fun StackedNights(nights: List<SleepNight>, selection: ChartSelection, modifier: Modifier) {
    val haptics = LocalHapticFeedback.current
    val grow by animateOnShow(1f, durationMillis = 1000, delayMillis = 150)
    val colors = Palette
    val n = nights.size

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
        val max = nights.maxOf { it.inBed.toMinutes() }.coerceAtLeast(1) * 1.06f
        val slot = size.width / n
        val w = (slot * 0.44f).coerceIn(3.dp.toPx(), 26.dp.toPx())
        val seam = 1.5.dp.toPx()
        for (k in 1..2) {
            val y = size.height * k / 3f
            drawLine(Color.White.copy(alpha = 0.04f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }
        nights.forEachIndexed { i, night ->
            val stagger = 0.45f / n
            val p = ((grow - i * stagger) / (1f - (n - 1) * stagger)).coerceIn(0f, 1f)
            val full = size.height * night.inBed.toMinutes() / max * p
            if (full <= 0f) return@forEachIndexed
            val x = i * slot + (slot - w) / 2
            val top = size.height - full
            val chosen = selection.index == i
            val alpha = if (selection.index != null && !chosen) 0.28f else 1f
            if (chosen) {
                drawRoundRect(
                    stageColor(SleepStage.LIGHT).copy(alpha = 0.18f),
                    Offset(x - w * 0.35f, top - w * 0.35f), Size(w * 1.7f, full + w * 0.35f), CornerRadius(w),
                )
                drawLine(
                    colors.text.copy(alpha = 0.35f), Offset(x + w / 2, 0f), Offset(x + w / 2, top - 6.dp.toPx()), 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
                )
            }
            val bar = Path().apply { addRoundRect(RoundRect(Rect(x, top, x + w, size.height), CornerRadius(w / 2))) }
            clipPath(bar) {
                var bottom = size.height
                for ((stage, share) in shares(night)) {
                    val h = full * share
                    drawRect(stageColor(stage), Offset(x, bottom - h + seam), Size(w, (h - seam).coerceAtLeast(0f)), alpha = alpha)
                    bottom -= h
                }
            }
        }
    }
}

/** An earlier night in the list: its ring, when it was, the time asleep, and the night as a strip. */
@Composable
private fun NightRow(night: SleepNight, onClick: () -> Unit, modifier: Modifier) {
    Panel(modifier.fillMaxWidth(), onClick = onClick, padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SleepRing(night, 46.dp, strokeFraction = 0.15f, glow = false)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(Format.day(night.end), style = MaterialTheme.typography.titleMedium, color = Palette.text, maxLines = 1)
                Caption(times(night), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(12.dp))
            Text(durationText(night.asleep.toMinutes(), 24f), style = AppType.number(24f), color = Palette.text)
        }
        Spacer(Modifier.height(14.dp))
        NightStrip(night, Modifier.fillMaxWidth().height(6.dp))
    }
}

// ---------------------------------------------------------------- screens

/**
 * Last night at a glance: the ring, the time asleep, bedtime to waking, and the night as a strip.
 * On Today it opens the Sleep tab; on a day's page it just shows the night before.
 */
@Composable
fun LastNightCard(night: SleepNight, title: String, onOpen: (() -> Unit)?, modifier: Modifier = Modifier) {
    Panel(modifier.fillMaxWidth(), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SleepRing(night, 62.dp, strokeFraction = 0.14f, glow = false, delayMillis = 200) {
                Icon(painterResource(R.drawable.ic_moon), contentDescription = null, tint = stageColor(SleepStage.LIGHT), modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Eyebrow(title)
                Spacer(Modifier.height(2.dp))
                DurationNumber(night.asleep.toMinutes(), 30f, delayMillis = 200)
                Caption(times(night), style = MaterialTheme.typography.bodySmall)
            }
            if (onOpen != null) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Palette.faint)
            }
        }
        Spacer(Modifier.height(16.dp))
        NightStrip(night, Modifier.fillMaxWidth().height(8.dp))
    }
}

/** The Sleep tab: the latest night in full, the week, and earlier nights to open. */
@Composable
fun SleepScreen(
    state: UiState,
    onOpenNight: (SleepNight) -> Unit,
    bottomPadding: Dp,
    chartSelection: Int? = null,
    chartMinute: Int? = null,
) {
    val nights = state.nights
    val latest = nights.firstOrNull()
    val lastNight = state.today?.window?.wokeFrom?.let { span -> nights.firstOrNull { it.span == span } }
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(Modifier.entrance(0)) {
            Eyebrow(
                when {
                    latest == null -> stringResource(R.string.sleep_eyebrow)
                    latest == lastNight -> stringResource(R.string.sleep_last_night)
                    else -> Format.day(latest.end)
                }
            )
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.tab_sleep), style = AppType.title, color = Palette.text)
        }
        if (latest == null) {
            Text(
                stringResource(if (state.loading) R.string.loading else R.string.sleep_none),
                style = MaterialTheme.typography.bodyLarge,
                color = Palette.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(32.dp),
            )
            return@Column
        }
        NightContent(latest, 276.dp, first = 1, chartMinute)
        if (nights.size > 1) {
            NightsPanel(nights.take(7).reversed(), Modifier.entrance(6), chartSelection)
            Eyebrow(stringResource(R.string.sleep_earlier), Modifier.padding(start = 4.dp, top = 4.dp))
            nights.drop(1).forEachIndexed { i, night ->
                NightRow(night, onClick = { onOpenNight(night) }, Modifier.entrance(7 + minOf(i, 5)))
            }
        }
    }
}

/** One night in full, opened from the Sleep tab. */
@Composable
fun NightDetail(night: SleepNight, onBack: () -> Unit, bottomPadding: Dp) {
    BackHandler(onBack = onBack)
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
                Eyebrow(stringResource(R.string.sleep_detail_eyebrow))
                Text(Format.day(night.end), style = AppType.title.copy(fontSize = AppType.title.fontSize * 0.82f), color = Palette.text)
            }
        }
        NightContent(night, 236.dp, first = 1, chartMinute = null)
    }
}
