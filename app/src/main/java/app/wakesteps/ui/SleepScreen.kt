package app.wakesteps.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wakesteps.R
import app.wakesteps.core.Format
import app.wakesteps.core.GoogleFigures
import app.wakesteps.core.NightScore
import app.wakesteps.core.ScoreFactor
import app.wakesteps.core.ScorePart
import app.wakesteps.core.SleepNight
import app.wakesteps.core.SleepNights
import app.wakesteps.core.SleepRating
import app.wakesteps.core.SleepScore
import app.wakesteps.core.SleepStage
import app.wakesteps.core.StageSpan
import app.wakesteps.data.wakeDate
import app.wakesteps.ui.theme.AppColors
import app.wakesteps.ui.theme.AppType
import app.wakesteps.ui.theme.Palette
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.random.Random

// ---------------------------------------------------------------- colours and names

/** Each stage's colour: a night sky from deep indigo up to aqua, restless in amber and awake in warm coral. */
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
        SleepStage.RESTLESS -> R.string.stage_restless
        SleepStage.REM -> R.string.stage_rem
        SleepStage.LIGHT -> R.string.stage_light
        SleepStage.DEEP -> R.string.stage_deep
        SleepStage.ASLEEP -> R.string.stage_asleep
    }
)

/** A score's colour: gold for excellent (like a met step goal), then aqua, amber and coral. */
@Composable
fun ratingColor(rating: SleepRating): Color = when (rating) {
    SleepRating.EXCELLENT -> Palette.goal
    SleepRating.GOOD -> stageTextColor(SleepStage.REM)
    SleepRating.FAIR -> stageColor(SleepStage.RESTLESS)
    SleepRating.POOR -> stageColor(SleepStage.AWAKE)
}

@Composable
fun ratingName(rating: SleepRating): String = stringResource(
    when (rating) {
        SleepRating.EXCELLENT -> R.string.rating_excellent
        SleepRating.GOOD -> R.string.rating_good
        SleepRating.FAIR -> R.string.rating_fair
        SleepRating.POOR -> R.string.rating_poor
    }
)

/** Deepest first: the order of the stage ring and of the stacked nights. */
private val DEEPEST_FIRST = listOf(SleepStage.DEEP, SleepStage.LIGHT, SleepStage.ASLEEP, SleepStage.REM, SleepStage.RESTLESS, SleepStage.AWAKE)

/** Each stage's share of the night, deepest first; a night without stages is all asleep. */
private fun shares(night: SleepNight): List<Pair<SleepStage, Float>> {
    if (night.stages.isEmpty()) return listOf(SleepStage.ASLEEP to 1f)
    val total = night.inBed.toMillis().coerceAtLeast(1).toFloat()
    return DEEPEST_FIRST.mapNotNull { s -> night.time(s).toMillis().takeIf { it > 0 }?.let { s to it / total } }
}

private fun stagesOrWhole(night: SleepNight): List<StageSpan> =
    night.stages.ifEmpty { listOf(StageSpan(night.start, night.end, SleepStage.ASLEEP)) }

/**
 * The night as the chart draws it: brief stirs (restless moments, or short wake-ups when those
 * count as awake) as marks of their own, with the sleep they interrupted carried on through them,
 * so a one-minute stir doesn't break a stretch of deep sleep in two.
 */
private class ChartNight(val bars: List<StageSpan>, val marks: List<StageSpan>)

private val SLEEPING = setOf(SleepStage.LIGHT, SleepStage.DEEP, SleepStage.REM, SleepStage.ASLEEP)

private fun chartNight(night: SleepNight): ChartNight {
    val stages = stagesOrWhole(night)
    val bars = mutableListOf<StageSpan>()
    val marks = mutableListOf<StageSpan>()
    stages.forEachIndexed { i, s ->
        val stir = s.stage in setOf(SleepStage.RESTLESS, SleepStage.AWAKE) && s.duration <= SleepNights.BRIEF &&
            stages.getOrNull(i - 1)?.stage in SLEEPING && stages.getOrNull(i + 1)?.stage in SLEEPING
        val last = bars.lastOrNull()
        when {
            stir -> {
                marks += s
                bars[bars.lastIndex] = last!!.copy(end = s.end)
            }
            last != null && last.stage == s.stage && last.end == s.start -> bars[bars.lastIndex] = last.copy(end = s.end)
            else -> bars += s
        }
    }
    return ChartNight(bars, marks)
}

/** The night as the user wants restless moments counted. */
fun SleepNight.counted(restlessAsAwake: Boolean): SleepNight = if (restlessAsAwake) restlessAsAwake() else this

// ---------------------------------------------------------------- numbers

/** "7h 05m" with the digits in display type and the letters small and quiet. */
@Composable
fun durationText(minutes: Long, size: Float): AnnotatedString {
    val unit = SpanStyle(fontSize = (size * 0.45f).sp, color = Palette.muted)
    return buildAnnotatedString {
        if (minutes >= 60) {
            append("${minutes / 60}")
            withStyle(unit) { append("h") }
            append(" ") // a thin space at full size, so the minutes don't crowd the h
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

// ---------------------------------------------------------------- the night sky

private class Star(val x: Float, val y: Float, val radius: Float, val alpha: Float, val twinkle: Boolean, val speed: Float, val phase: Float)

/** Fixed, so the sky is the same every night: more stars, and brighter ones, towards the top. */
private val STARS = Random(11).let { r ->
    List(90) {
        val y = r.nextFloat() * r.nextFloat() // gathered towards the top
        Star(r.nextFloat(), y, 0.45f + r.nextFloat() * 0.9f, (0.2f + r.nextFloat() * 0.6f) * (1f - y * 0.8f), r.nextFloat() < 0.35f, 0.6f + r.nextFloat(), r.nextFloat())
    }
}

/** The Sleep tab's own backdrop: a deep night sky, a violet glow, and faint stars, some slowly twinkling. */
@Composable
fun NightSky(modifier: Modifier = Modifier) {
    val t = if (LocalSnapAnimations.current) 0f else {
        val v by rememberInfiniteTransition(label = "stars").animateFloat(
            0f, 1f, infiniteRepeatable(tween(9_000, easing = LinearEasing), RepeatMode.Restart), label = "twinkle",
        )
        v
    }
    Canvas(modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF0D1036), Color(0xFF070A1F), Color(0xFF04060F)), endY = size.height))
        val w = size.width
        fun glow(x: Float, y: Float, r: Float, color: Color) =
            drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), Offset(x, y), r), r, Offset(x, y))
        glow(w * 0.82f, w * 0.12f, w * 0.75f, stageColor(SleepStage.DEEP).copy(alpha = 0.20f))
        glow(w * 0.1f, w * 0.7f, w * 0.85f, Color(0xFF2E5BFF).copy(alpha = 0.09f))
        for (s in STARS) {
            val shimmer = if (s.twinkle) 0.55f + 0.45f * sin(2 * PI.toFloat() * (t * s.speed + s.phase)) else 1f
            drawCircle(Color.White.copy(alpha = s.alpha * shimmer), s.radius.dp.toPx(), Offset(s.x * w, s.y * size.height * 0.8f))
        }
    }
}

/** Last night's card is a little night sky among the day's panels: indigo, a violet glow, a few stars. */
private val NightCardFill = Brush.linearGradient(listOf(Color(0xF01C1F5E), Color(0xE60B0E2E)), Offset.Zero, Offset.Infinite)

private val NightCardSky: DrawScope.() -> Unit = {
    val at = Offset(size.width * 0.86f, 0f)
    val r = size.width * 0.55f
    drawCircle(Brush.radialGradient(listOf(stageColor(SleepStage.DEEP).copy(alpha = 0.22f), Color.Transparent), at, r), r, at)
    for (s in STARS.take(28)) {
        drawCircle(Color.White.copy(alpha = s.alpha * 0.6f), s.radius.dp.toPx() * 0.8f, Offset(s.x * size.width, s.y * size.height))
    }
}

// ---------------------------------------------------------------- rings and strips

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

/**
 * The score as a ring that fills to it, deep indigo turning aqua towards the tip, or gold for an
 * excellent night (like a met step goal). [content] sits inside.
 */
@Composable
fun ScoreRing(
    score: NightScore,
    size: Dp,
    modifier: Modifier = Modifier,
    strokeFraction: Float = 0.082f,
    glow: Boolean = true,
    delayMillis: Int = 0,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val shown by animateOnShow(score.value / 100f, durationMillis = 1500, delayMillis = delayMillis)
    val colors = Palette
    val excellent = score.rating == SleepRating.EXCELLENT
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = this.size.minDimension * strokeFraction
            val arc = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(stroke / 2, stroke / 2)
            val (from, to) = if (excellent) colors.goalStart to colors.goalEnd else stageColor(SleepStage.DEEP) to stageColor(SleepStage.REM)
            if (glow) {
                drawCircle(
                    Brush.radialGradient(listOf(from.copy(alpha = 0.26f), from.copy(alpha = 0.06f), Color.Transparent), radius = this.size.minDimension / 2),
                )
            }
            drawArc(colors.track, 0f, 360f, false, topLeft, arc, style = Stroke(stroke))
            if (shown > 0.002f) {
                rotate(-90f) {
                    val brush = Brush.sweepGradient(0f to from, shown.coerceAtMost(0.999f) to to, 1f to from)
                    drawArc(brush, 0f, 360f * shown, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
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

/** A score in a small ring, its number inside: for lists and cards. */
@Composable
private fun SmallScore(night: SleepNight, score: NightScore?, size: Dp, delayMillis: Int = 0) {
    if (score == null) {
        SleepRing(night, size, strokeFraction = 0.14f, glow = false, delayMillis = delayMillis) {
            Icon(painterResource(R.drawable.ic_moon), contentDescription = null, tint = stageColor(SleepStage.LIGHT), modifier = Modifier.size(size * 0.32f))
        }
    } else {
        ScoreRing(score, size, strokeFraction = 0.12f, glow = false, delayMillis = delayMillis) {
            Text("${score.value}", style = AppType.number(size.value * 0.36f), color = Palette.text)
        }
    }
}

// ---------------------------------------------------------------- a night in full

/**
 * The night's score, big, in its ring with how it rates, and where it came from (tap to enter
 * Google Health's own); then how long you slept and when. A night without a score shows its
 * stages in the ring instead.
 */
@Composable
private fun SleepHero(night: SleepNight, score: NightScore?, onEditScore: () -> Unit, modifier: Modifier, ringSize: Dp) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (score != null) {
            ScoreRing(score, ringSize, delayMillis = 150) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painterResource(R.drawable.ic_moon),
                        contentDescription = null,
                        tint = stageColor(SleepStage.LIGHT),
                        modifier = Modifier.size(ringSize * 0.08f),
                    )
                    CountUp(score.value.toDouble(), ringSize.value * 0.30f, delayMillis = 150)
                    Text(ratingName(score.rating), style = MaterialTheme.typography.titleMedium, color = ratingColor(score.rating))
                    Spacer(Modifier.height(ringSize * 0.05f))
                }
            }
            Spacer(Modifier.height(16.dp))
            SourceChip(score.fromGoogle, onEditScore)
        } else {
            SleepRing(night, ringSize, delayMillis = 150) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(painterResource(R.drawable.ic_moon), contentDescription = null, tint = stageColor(SleepStage.LIGHT), modifier = Modifier.size(ringSize * 0.09f))
                    Spacer(Modifier.height(4.dp))
                    DurationNumber(night.asleep.toMinutes(), ringSize.value * 0.19f, delayMillis = 150)
                    Eyebrow(stringResource(R.string.sleep_asleep))
                    Spacer(Modifier.height(ringSize * 0.04f))
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            if (score != null) {
                DurationNumber(night.asleep.toMinutes(), 26f, delayMillis = 150)
                Spacer(Modifier.width(8.dp))
            }
            Caption(
                if (score != null) stringResource(R.string.sleep_asleep_times, times(night)) else times(night),
                Modifier.padding(bottom = 3.dp),
            )
        }
    }
}

/** "Estimate ✎" or "Google Health ✓": where the score came from, and the way to enter or change it. */
@Composable
private fun SourceChip(fromGoogle: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Row(
        Modifier
            .pressScale(source)
            .clip(CircleShape)
            .background(Palette.card)
            .border(1.dp, glassEdge(), CircleShape)
            .clickable(source, indication = null, onClick = onClick)
            .padding(start = 12.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (fromGoogle) Icons.Default.Check else Icons.Default.Edit,
            contentDescription = null,
            tint = if (fromGoogle) stageTextColor(SleepStage.REM) else Palette.faint,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(if (fromGoogle) R.string.score_google else R.string.score_estimate),
            style = MaterialTheme.typography.labelMedium,
            color = Palette.muted,
        )
    }
}

/** Each factor's colour, shared by its part of the score bar and its tile: the night's own colours. */
private fun factorColor(factor: ScoreFactor): Color = when (factor) {
    ScoreFactor.DURATION -> stageColor(SleepStage.LIGHT)
    ScoreFactor.SOUND_SLEEP -> stageColor(SleepStage.DEEP)
    ScoreFactor.RESTLESSNESS -> stageColor(SleepStage.RESTLESS)
    ScoreFactor.FULL_AWAKENINGS -> stageColor(SleepStage.AWAKE)
    ScoreFactor.INTERRUPTIONS -> Color(0xFFFF7EB3)
    ScoreFactor.TIME_TO_SOUND_SLEEP -> stageColor(SleepStage.REM)
}

/**
 * What went into the score: one bar made of the six factors, each part as wide as that factor
 * counts and lit as far as it went well, then a tile for each, biggest first, with how it went
 * and a small ring for how well.
 */
@Composable
private fun ScoreFactors(parts: List<ScorePart>, modifier: Modifier) {
    val ordered = parts.sortedByDescending { it.factor.weight }
    Panel(modifier.fillMaxWidth()) {
        Eyebrow(stringResource(R.string.sleep_score_factors))
        Spacer(Modifier.height(16.dp))
        ScoreBar(ordered, Modifier.fillMaxWidth().height(12.dp))
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ordered.chunked(2).forEachIndexed { row, pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEachIndexed { i, part -> FactorTile(part, delayMillis = 300 + (row * 2 + i) * 70, modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** The score as the sum of its parts: each factor's share of the bar, filled as far as it went well. */
@Composable
private fun ScoreBar(parts: List<ScorePart>, modifier: Modifier) {
    val grow by animateOnShow(1f, durationMillis = 1300, delayMillis = 200)
    Canvas(modifier) {
        val gap = 4.dp.toPx()
        val total = parts.sumOf { it.factor.weight.toDouble() }.toFloat()
        val room = size.width - gap * (parts.size - 1)
        val radius = CornerRadius(size.height / 2)
        val stagger = 0.4f / parts.size
        var x = 0f
        parts.forEachIndexed { i, part ->
            val width = room * part.factor.weight / total
            val color = factorColor(part.factor)
            drawRoundRect(color.copy(alpha = 0.16f), Offset(x, 0f), Size(width, size.height), radius)
            val p = ((grow - i * stagger) / (1f - (parts.size - 1) * stagger)).coerceIn(0f, 1f)
            val lit = if (part.measured) width * part.quality * p else 0f
            if (lit > 0.5f) {
                drawRoundRect(
                    Brush.horizontalGradient(listOf(color.copy(alpha = 0.7f), color), startX = x, endX = x + lit),
                    Offset(x, 0f), Size(lit.coerceAtLeast(size.height), size.height), radius,
                )
            }
            x += width + gap
        }
    }
}

/** One factor: how it went, big, a small ring for how well, and its name. */
@Composable
private fun FactorTile(part: ScorePart, delayMillis: Int, modifier: Modifier) {
    val color = factorColor(part.factor)
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.10f), Color.White.copy(alpha = 0.02f))))
            .border(1.dp, Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), Color.White.copy(alpha = 0.03f))), shape)
            .padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Row(Modifier.height(30.dp), verticalAlignment = Alignment.CenterVertically) {
            if (part.measured) {
                val value = when (part.factor) {
                    ScoreFactor.FULL_AWAKENINGS -> AnnotatedString("${part.count}")
                    else -> durationText(part.minutes, 22f)
                }
                Text(value, style = AppType.number(22f), color = Palette.text, maxLines = 1, modifier = Modifier.weight(1f))
            } else {
                // Sound sleep is measured from heart rate; until that's allowed, say so rather than guess.
                Text(stringResource(R.string.score_needs_heart), style = MaterialTheme.typography.bodySmall, color = Palette.faint, maxLines = 1, modifier = Modifier.weight(1f))
            }
            QualityRing(if (part.measured) part.quality else 0f, color, 28.dp, delayMillis)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (part.factor == ScoreFactor.RESTLESSNESS) "${factorName(part.factor)} · ${part.count}×" else factorName(part.factor),
            style = MaterialTheme.typography.bodySmall,
            color = Palette.muted,
            maxLines = 1,
        )
    }
}

/** A small ring filled as far as [quality] (0 to 1) goes, sweeping round when it first shows. */
@Composable
private fun QualityRing(quality: Float, color: Color, size: Dp, delayMillis: Int) {
    val shown by animateOnShow(quality, durationMillis = 1100, delayMillis = delayMillis)
    Canvas(Modifier.size(size)) {
        val stroke = this.size.minDimension * 0.15f
        val arc = Size(this.size.width - stroke, this.size.height - stroke)
        val topLeft = Offset(stroke / 2, stroke / 2)
        drawArc(Color.White.copy(alpha = 0.08f), 0f, 360f, false, topLeft, arc, style = Stroke(stroke))
        if (shown > 0.005f) {
            drawArc(color, -90f, 360f * shown, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun factorName(factor: ScoreFactor): String = stringResource(
    when (factor) {
        ScoreFactor.DURATION -> R.string.factor_duration
        ScoreFactor.TIME_TO_SOUND_SLEEP -> R.string.factor_time_to_sound
        ScoreFactor.SOUND_SLEEP -> R.string.factor_sound
        ScoreFactor.RESTLESSNESS -> R.string.factor_restlessness
        ScoreFactor.FULL_AWAKENINGS -> R.string.factor_awakenings
        ScoreFactor.INTERRUPTIONS -> R.string.factor_interruptions
    }
)

/** The stages as a ring beside their times and shares, with how much of the night was asleep. */
@Composable
private fun StagesPanel(night: SleepNight, modifier: Modifier, title: String = stringResource(R.string.sleep_stages)) {
    val total = night.inBed.toMillis().coerceAtLeast(1).toFloat()
    Panel(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(title, Modifier.weight(1f))
            Eyebrow(stringResource(R.string.sleep_asleep_share, (night.efficiency * 100).roundToInt()))
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SleepRing(night, 118.dp, strokeFraction = 0.12f, glow = false, delayMillis = 200) {
                DurationNumber(night.asleep.toMinutes(), 19f, delayMillis = 200)
            }
            Spacer(Modifier.width(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                night.lanes.forEach { stage ->
                    val time = night.time(stage)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(stageColor(stage)))
                        Spacer(Modifier.width(9.dp))
                        Text(stageName(stage), style = MaterialTheme.typography.bodyMedium, color = Palette.text, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(durationText(time.toMinutes(), 16f), style = AppType.number(16f), color = Palette.text)
                        Text(
                            "${(time.toMillis() / total * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = Palette.faint,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(38.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The night stage by stage, its stages and the chart in one: a row for each stage from awake down
 * to deep, named with its time that night, the night sweeping in from bedtime. Drag along it to
 * read the time and stage under your finger, with a tick each time the stage changes; otherwise it
 * shows the night's longest stretch of deep sleep. With [onOpen], a tap opens the night to zoom
 * into; without, a tap picks a moment.
 */
@Composable
fun NightChart(night: SleepNight, modifier: Modifier = Modifier, initialMinute: Int? = null, onOpen: (() -> Unit)? = null) {
    val lanes = night.lanes
    val minutes = night.inBed.toMinutes().toInt().coerceAtLeast(1)
    val selection = rememberChartSelection(initialMinute)
    val haptics = LocalHapticFeedback.current
    val reveal by animateOnShow(1f, durationMillis = 1400, delayMillis = 300)
    val colors = Palette
    fun minuteAt(m: Int): Instant = night.start.plus(Duration.ofMinutes(m.toLong()))
    val picked = selection.index?.let(::minuteAt)
    val pickedStage = picked?.let { night.stageAt(it) }
    val chart = remember(night) { chartNight(night) }
    val anchor = if (night.staged) SleepStage.DEEP else SleepStage.ASLEEP
    val longest = chart.bars.filter { it.stage == anchor }.maxByOrNull { it.duration }

    fun pick(fraction: Float) {
        val m = (fraction * minutes).toInt()
        val before = selection.index?.let { night.stageAt(minuteAt(it)) }
        if (selection.index == null || night.stageAt(minuteAt(m)) != before) {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
        selection.index = m
    }

    Panel(modifier.fillMaxWidth(), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(stringResource(R.string.sleep_through_night), Modifier.weight(1f))
            if (night.staged) Eyebrow(stringResource(R.string.sleep_asleep_share, (night.efficiency * 100).roundToInt()))
            if (onOpen != null) {
                Spacer(Modifier.width(2.dp))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.faint, modifier = Modifier.size(20.dp))
            }
        }
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
        Spacer(Modifier.height(14.dp))
        val captions = laneCaptions(lanes, night)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height((lanes.size * LANE_HEIGHT).dp)
                .scrubbable(
                    night,
                    // With somewhere to open, the panel takes the tap.
                    onTap = { if (onOpen == null) { pick(it); selection.releasedAt = System.currentTimeMillis() } },
                    onScrub = { selection.releasedAt = 0L; pick(it) },
                    onEnd = { selection.releasedAt = System.currentTimeMillis() },
                ),
        ) {
            drawNight(night, chart, lanes, 0f, 1f, picked, pickedStage, colors, captions, reveal)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(Format.time(night.start), style = AppType.eyebrow, color = colors.faint)
            Text(Format.time(night.end), style = AppType.eyebrow, color = colors.faint)
        }
    }
}

private const val LANE_HEIGHT = 48
/** The strip at the top of each row for its name and time; the bars sit below it. */
private val CAPTION = 15.dp

/** A row's name and its time, measured to draw small above its bars. */
private class LaneCaption(val name: TextLayoutResult, val time: TextLayoutResult)

/**
 * Each row's name and its time in [night], small, with a soft dark halo so they stay clear where
 * a line between stages runs past.
 */
@Composable
private fun laneCaptions(lanes: List<SleepStage>, night: SleepNight): List<LaneCaption> {
    val measurer = rememberTextMeasurer()
    val halo = Shadow(Color(0xFF070A1C), blurRadius = 12f)
    val nameStyle = AppType.eyebrow.copy(fontSize = 9.5.sp, shadow = halo)
    val timeStyle = AppType.number(12.5f).copy(shadow = halo)
    val names = lanes.map { stageName(it).uppercase() }
    return lanes.mapIndexed { i, lane ->
        LaneCaption(measurer.measure(names[i], nameStyle), measurer.measure(plainDuration(night.time(lane).toMinutes()), timeStyle))
    }
}

/** "4h 06m" or "45m", all one size, for small labels. */
private fun plainDuration(minutes: Long): String = if (minutes >= 60) "${minutes / 60}h %02dm".format(minutes % 60) else "${minutes}m"

/**
 * The part of [night] from [from] to [to] (fractions of it) across this canvas, a row per lane:
 * faint [ticks], the steps between stages, the bars, the stirs as marks, and the picked moment's
 * line and dot. [reveal] wipes it in from the left.
 */
private fun DrawScope.drawNight(
    night: SleepNight,
    chart: ChartNight,
    lanes: List<SleepStage>,
    from: Float,
    to: Float,
    picked: Instant?,
    pickedStage: SleepStage?,
    colors: AppColors,
    captions: List<LaneCaption>,
    reveal: Float = 1f,
    ticks: List<Instant> = emptyList(),
) {
    val laneHeight = size.height / lanes.size
    val caption = CAPTION.toPx()
    fun laneTop(i: Int) = i * laneHeight
    fun laneY(stage: SleepStage) = laneTop(lanes.indexOf(stage).takeIf { it >= 0 } ?: lanes.lastIndex) + caption + (laneHeight - caption) / 2
    val total = night.inBed.toMillis().coerceAtLeast(1).toFloat()
    val scale = size.width / (to - from)
    fun x(t: Instant) = (Duration.between(night.start, t).toMillis() / total - from) * scale
    val barHeight = (laneHeight - caption) * 0.68f
    val lineColor = colors.text
    for (lane in lanes) {
        val y = laneY(lane)
        drawLine(Color.White.copy(alpha = 0.04f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
    }
    for (t in ticks) {
        val at = x(t)
        drawLine(Color.White.copy(alpha = 0.06f), Offset(at, 0f), Offset(at, size.height), 1.dp.toPx())
    }
    clipRect(right = size.width * reveal) {
        // Steps between stages first, so the bars sit on top of them.
        chart.bars.zipWithNext { a, b ->
            val at = x(b.start)
            if (at in 0f..size.width) {
                drawLine(Color.White.copy(alpha = 0.16f), Offset(at, laneY(a.stage)), Offset(at, laneY(b.stage)), 1.5.dp.toPx())
            }
        }
        for (s in chart.bars) {
            val left = x(s.start)
            val right = x(s.end)
            if (right < 0f || left > size.width) continue
            val width = (right - left).coerceAtLeast(2.dp.toPx())
            val top = laneY(s.stage) - barHeight / 2
            val color = stageColor(s.stage)
            drawRoundRect(
                Brush.verticalGradient(listOf(color, color.copy(alpha = 0.7f)), startY = top, endY = top + barHeight),
                Offset(left, top), Size(width, barHeight), CornerRadius(minOf(barHeight / 2.6f, width / 2)),
            )
        }
        // Each stir as a slim tick on its own row (as wide as it lasted, once zoomed in that far),
        // so even a night of dozens stays a row of fine marks rather than a solid band.
        val markWidth = 2.dp.toPx()
        for (m in chart.marks) {
            val center = (x(m.start) + x(m.end)) / 2
            if (center < -markWidth || center > size.width + markWidth) continue
            val width = maxOf(x(m.end) - x(m.start), markWidth)
            val y = laneY(m.stage)
            drawRoundRect(
                stageColor(m.stage).copy(alpha = 0.9f),
                Offset(center - width / 2, y - barHeight * 0.38f), Size(width, barHeight * 0.76f),
                CornerRadius(minOf(width / 2, barHeight * 0.38f)),
            )
        }
    }
    // Each row's name at its start and its time at its end, above its bars; the picked row lit.
    captions.forEachIndexed { i, c ->
        val lit = lanes.getOrNull(i) == pickedStage
        val name = if (lit) stageTextColor(lanes[i]) else colors.faint
        val time = if (lit) colors.text else colors.muted
        drawText(c.name, color = name, topLeft = Offset(0f, laneTop(i) + (caption - c.name.size.height) / 2))
        drawText(c.time, color = time, topLeft = Offset(size.width - c.time.size.width, laneTop(i) + (caption - c.time.size.height) / 2))
    }
    val at = picked?.let(::x)
    if (at != null && at in 0f..size.width) {
        drawLine(
            lineColor.copy(alpha = 0.35f), Offset(at, 0f), Offset(at, size.height), 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
        )
        pickedStage?.let { s ->
            drawCircle(stageColor(s).copy(alpha = 0.25f), 11.dp.toPx(), Offset(at, laneY(s)))
            drawCircle(stageColor(s), 5.dp.toPx(), Offset(at, laneY(s)))
        }
    }
}

// ---------------------------------------------------------------- zooming into a night

/**
 * One night opened up to explore: the night stage by stage, big, to pinch and zoom into (or
 * double-tap, or use − and +), with the whole night below it showing which part is in view (drag
 * along it to move), and the stages in view.
 */
@Composable
fun NightExplorer(
    night: SleepNight,
    restlessAsAwake: Boolean,
    onBack: () -> Unit,
    bottomPadding: Dp,
    initialView: ClosedFloatingPointRange<Float>? = null,
    initialMinute: Int? = null,
) {
    BackHandler(onBack = onBack)
    val display = remember(night, restlessAsAwake) { night.counted(restlessAsAwake) }
    // Close enough to see each stir: a quarter of an hour across.
    val view = remember(display) { SpanView(initialView ?: 0f..1f, (15f / display.inBed.toMinutes().coerceAtLeast(15)).coerceAtMost(1f)) }
    val total = display.inBed.toMillis()
    fun at(fraction: Float): Instant = display.start.plusMillis((fraction * total).toLong())
    Box(Modifier.fillMaxSize()) {
        NightSky()
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
                    DateEyebrow(stringResource(R.string.sleep_through_night), night.wakeDate())
                    Text(Format.day(night.end), style = AppType.title.copy(fontSize = AppType.title.fontSize * 0.82f), color = Palette.text)
                }
            }
            ZoomableNight(display, view, Modifier.entrance(1), initialMinute)
            val from = at(view.left)
            val to = at(view.right)
            StagesPanel(
                display.within(from, to),
                Modifier.entrance(2),
                title = if (view.zoomed) "${Format.time(from)} – ${Format.time(to)}" else stringResource(R.string.sleep_stages),
            )
        }
    }
}

/** The night in view, big: its readout and zoom buttons, the rows, the times along it, and the whole night below. */
@Composable
private fun ZoomableNight(night: SleepNight, view: SpanView, modifier: Modifier, initialMinute: Int?) {
    val lanes = night.lanes
    val chart = remember(night) { chartNight(night) }
    val total = night.inBed.toMillis().coerceAtLeast(1)
    val minutes = night.inBed.toMinutes().toInt().coerceAtLeast(1)
    val selection = rememberChartSelection(initialMinute)
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val colors = Palette
    val zone = ZoneId.systemDefault()
    fun at(fraction: Float): Instant = night.start.plusMillis((fraction * total).toLong())
    val from = at(view.left)
    val to = at(view.right)
    val picked = selection.index?.let { night.start.plus(Duration.ofMinutes(it.toLong())) }
    val pickedStage = picked?.let { night.stageAt(it) }
    fun pick(across: Float) {
        val m = ((view.left + across * view.width) * minutes).toInt().coerceIn(0, minutes - 1)
        val before = selection.index?.let { night.stageAt(night.start.plus(Duration.ofMinutes(it.toLong()))) }
        if (selection.index == null || night.stageAt(night.start.plus(Duration.ofMinutes(m.toLong()))) != before) {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
        selection.index = m
    }

    Panel(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                if (picked != null) {
                    Text(Format.time(picked), style = AppType.number(30f), color = colors.text)
                    Spacer(Modifier.width(10.dp))
                    val tone by animateColorAsState(pickedStage?.let(::stageTextColor) ?: colors.muted, label = "stage")
                    Caption(pickedStage?.let { stageName(it) } ?: "", Modifier.padding(bottom = 5.dp), color = tone)
                } else {
                    Text(durationText(Duration.between(from, to).toMinutes(), 30f), style = AppType.number(30f), color = colors.text, maxLines = 1)
                    Spacer(Modifier.width(10.dp))
                    Caption("${Format.time(from)} – ${Format.time(to)}", Modifier.padding(bottom = 5.dp))
                }
            }
            ZoomButton("−", enabled = view.zoomed) { view.zoom(scope, 0.5f) }
            Spacer(Modifier.width(8.dp))
            ZoomButton("+", enabled = !view.atClosest) { view.zoom(scope, 2f) }
        }
        Spacer(Modifier.height(16.dp))
        val captions = laneCaptions(lanes, night.within(from, to))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Room for a time label every 72dp or so.
            val marks = ticks(from, to, zone, maxCount = (maxWidth / 72.dp).toInt().coerceAtLeast(2))
            Column {
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height((lanes.size * 60).dp)
                        .zoomable(
                            view,
                            onTap = { pick(it); selection.releasedAt = System.currentTimeMillis() },
                            onDoubleTap = { if (view.atClosest) view.glideTo(scope, 0f, 1f) else view.zoom(scope, 2.5f, it) },
                            onScrub = { selection.releasedAt = 0L; pick(it) },
                            onScrubEnd = { selection.releasedAt = System.currentTimeMillis() },
                            onPinch = { focus, factor, pan -> view.pinch(focus, factor, pan) },
                        ),
                ) {
                    drawNight(night, chart, lanes, view.left, view.right, picked, pickedStage, colors, captions, ticks = marks)
                }
                Spacer(Modifier.height(8.dp))
                TickLabels(marks, from, to, Modifier.fillMaxWidth().height(16.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        WholeNight(night, chart, lanes, view, scope, Modifier.fillMaxWidth().height(44.dp))
    }
}

/**
 * The whole night, small, with the part in view lit and framed: drag along it to move the view,
 * tap to centre it there.
 */
@Composable
private fun WholeNight(night: SleepNight, chart: ChartNight, lanes: List<SleepStage>, view: SpanView, scope: CoroutineScope, modifier: Modifier) {
    val frame = Palette.text
    Canvas(modifier.overviewGestures(view, scope)) {
        drawRoundRect(Color.White.copy(alpha = 0.04f), cornerRadius = CornerRadius(10.dp.toPx()))
        val laneHeight = size.height / lanes.size
        val total = night.inBed.toMillis().coerceAtLeast(1).toFloat()
        fun x(t: Instant) = Duration.between(night.start, t).toMillis() / total * size.width
        for (s in chart.bars) {
            val i = lanes.indexOf(s.stage).takeIf { it >= 0 } ?: lanes.lastIndex
            val h = laneHeight * 0.6f
            drawRoundRect(
                stageColor(s.stage), Offset(x(s.start), i * laneHeight + (laneHeight - h) / 2),
                Size((x(s.end) - x(s.start)).coerceAtLeast(1.dp.toPx()), h), CornerRadius(h / 2),
            )
        }
        drawViewFrame(view, frame)
    }
}

/**
 * A night in full, entering in turn from [first]: the score (or the stages) as the hero, the
 * night stage by stage (tap to open it up and zoom in), and what went into the score.
 */
@Composable
private fun NightContent(
    night: SleepNight,
    score: NightScore?,
    restlessAsAwake: Boolean,
    onEditScore: () -> Unit,
    onExplore: (SleepNight) -> Unit,
    ringSize: Dp,
    first: Int,
    chartMinute: Int?,
) {
    val display = night.counted(restlessAsAwake)
    SleepHero(display, score, onEditScore, Modifier.entrance(first), ringSize)
    if (display.stages.size > 1) NightChart(display, Modifier.entrance(first + 1), chartMinute, onOpen = { onExplore(night) })
    SleepScore.parts(night, GoogleFigures.KNOWN[night.wakeDate()])?.let { ScoreFactors(it, Modifier.entrance(first + 2)) }
    if (!display.staged) {
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
 * The last week of nights as stacked bars, deep at the bottom up to awake at the top, each with
 * its score above it. The readout shows the average time asleep and score, or the night you're
 * touching.
 */
@Composable
private fun NightsPanel(nights: List<SleepNight>, scores: List<NightScore?>, modifier: Modifier, initialSelection: Int?) {
    val selection = rememberChartSelection(initialSelection)
    val average = nights.map { it.asleep.toMinutes() }.average().roundToLong()
    val known = scores.filterNotNull()
    val picked = selection.index?.let { nights.getOrNull(it) }
    val zone = ZoneId.systemDefault()
    Panel(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(stringResource(R.string.sleep_week), Modifier.weight(1f))
            if (known.isNotEmpty()) {
                val avgScore = known.map { it.value }.average().roundToInt()
                Eyebrow(stringResource(R.string.sleep_avg_score, avgScore), color = ratingColor(SleepRating.of(avgScore)))
            }
        }
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
        StackedNights(nights, scores, selection, Modifier.fillMaxWidth().height(150.dp))
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

/** One stacked bar per night with its score on top, growing in left to right, scrubbable like the other charts. */
@Composable
private fun StackedNights(nights: List<SleepNight>, scores: List<NightScore?>, selection: ChartSelection, modifier: Modifier) {
    val haptics = LocalHapticFeedback.current
    val grow by animateOnShow(1f, durationMillis = 1000, delayMillis = 150)
    val colors = Palette
    val measurer = rememberTextMeasurer()
    val labels = scores.map { score -> score?.let { it.value.toString() to ratingColor(it.rating) } }
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
        // Room above the tallest bar for its score.
        val headroom = 22.dp.toPx()
        val max = nights.maxOf { it.inBed.toMinutes() }.coerceAtLeast(1).toFloat()
        val chart = size.height - headroom
        val slot = size.width / n
        val w = (slot * 0.44f).coerceIn(3.dp.toPx(), 26.dp.toPx())
        val seam = 1.5.dp.toPx()
        for (k in 1..2) {
            val y = headroom + chart * k / 3f
            drawLine(Color.White.copy(alpha = 0.04f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }
        nights.forEachIndexed { i, night ->
            val stagger = 0.45f / n
            val p = ((grow - i * stagger) / (1f - (n - 1) * stagger)).coerceIn(0f, 1f)
            val full = chart * night.inBed.toMinutes() / max * p
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
            labels.getOrNull(i)?.let { (text, color) ->
                val measured = measurer.measure(text, AppType.eyebrow.copy(color = color.copy(alpha = alpha * p), fontSize = 11.sp))
                drawText(measured, topLeft = Offset(x + w / 2 - measured.size.width / 2f, top - measured.size.height - 4.dp.toPx()))
            }
        }
    }
}

/** An earlier night in the list: its score, when it was, the time asleep, and the night as a strip. */
@Composable
private fun NightRow(night: SleepNight, score: NightScore?, onClick: () -> Unit, modifier: Modifier) {
    Panel(modifier.fillMaxWidth(), onClick = onClick, padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SmallScore(night, score, 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(Format.day(night.end), style = MaterialTheme.typography.titleMedium, color = Palette.text, maxLines = 1)
                Caption(
                    score?.let { "${ratingName(it.rating)}  ·  ${times(night)}" } ?: times(night),
                    style = MaterialTheme.typography.bodySmall,
                )
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
 * Last night at a glance: its score (or stages) in a small ring, the time asleep, bedtime to
 * waking, and the night as a strip. On Today it opens the Sleep tab; on a day's page it just
 * shows the night before.
 */
@Composable
fun LastNightCard(
    night: SleepNight,
    score: NightScore?,
    restlessAsAwake: Boolean,
    title: String,
    onOpen: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val display = night.counted(restlessAsAwake)
    Panel(modifier.fillMaxWidth(), onClick = onOpen, fill = NightCardFill, decoration = NightCardSky) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SmallScore(display, score, 62.dp, delayMillis = 200)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Eyebrow(title)
                    score?.let {
                        Eyebrow("  ·  ")
                        Eyebrow(ratingName(it.rating), color = ratingColor(it.rating))
                    }
                }
                Spacer(Modifier.height(2.dp))
                DurationNumber(display.asleep.toMinutes(), 30f, delayMillis = 200)
                Caption(times(display), style = MaterialTheme.typography.bodySmall)
            }
            if (onOpen != null) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Palette.faint)
            }
        }
        Spacer(Modifier.height(16.dp))
        NightStrip(display, Modifier.fillMaxWidth().height(8.dp))
    }
}

/** The Sleep tab, under its own night sky: the latest night in full, the week, and earlier nights to open. */
@Composable
fun SleepScreen(
    state: UiState,
    scores: Map<LocalDate, NightScore>,
    restlessAsAwake: Boolean,
    onOpenNight: (SleepNight) -> Unit,
    onEditScore: (SleepNight) -> Unit,
    onExplore: (SleepNight) -> Unit,
    bottomPadding: Dp,
    chartSelection: Int? = null,
    chartMinute: Int? = null,
) {
    val nights = state.nights
    val latest = nights.firstOrNull()
    val lastNight = state.today?.window?.wokeFrom?.let { span -> nights.firstOrNull { it.span == span } }
    Box(Modifier.fillMaxSize()) {
        NightSky()
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
                    stringResource(if (state.nightsLoaded) R.string.sleep_none else R.string.loading),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Palette.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                )
                return@Column
            }
            NightContent(latest, scores[latest.wakeDate()], restlessAsAwake, { onEditScore(latest) }, onExplore, 268.dp, first = 1, chartMinute)
            if (nights.size > 1) {
                val week = nights.take(7).reversed()
                NightsPanel(
                    week.map { it.counted(restlessAsAwake) }, week.map { scores[it.wakeDate()] },
                    Modifier.entrance(6), chartSelection,
                )
                Eyebrow(stringResource(R.string.sleep_earlier), Modifier.padding(start = 4.dp, top = 4.dp))
                nights.drop(1).forEachIndexed { i, night ->
                    NightRow(night.counted(restlessAsAwake), scores[night.wakeDate()], onClick = { onOpenNight(night) }, Modifier.entrance(7 + minOf(i, 5)))
                }
            }
        }
    }
}

/** One night in full, opened from the Sleep tab. */
@Composable
fun NightDetail(
    night: SleepNight,
    score: NightScore?,
    restlessAsAwake: Boolean,
    onEditScore: () -> Unit,
    onExplore: (SleepNight) -> Unit,
    onBack: () -> Unit,
    bottomPadding: Dp,
) {
    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize()) {
        NightSky()
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
                    DateEyebrow(stringResource(R.string.sleep_detail_eyebrow), night.wakeDate())
                    Text(Format.day(night.end), style = AppType.title.copy(fontSize = AppType.title.fontSize * 0.82f), color = Palette.text)
                }
            }
            NightContent(night, score, restlessAsAwake, onEditScore, onExplore, 236.dp, first = 1, chartMinute = null)
        }
    }
}

/**
 * Entering the score Google Health gave a night: a ruler that starts on the score shown now, to
 * nudge to Google's number; or back to the estimate.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoreSheet(night: SleepNight, score: NightScore?, onSave: (Int) -> Unit, onClear: () -> Unit, onDone: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDone, containerColor = Palette.cardHigh) {
        ScoreContent(night, score, onSave = { onSave(it); onDone() }, onClear = { onClear(); onDone() })
    }
}

/** The score sheet's content, on its own so it can be rendered without a sheet. */
@Composable
internal fun ScoreContent(night: SleepNight, score: NightScore?, onSave: (Int) -> Unit, onClear: () -> Unit) {
    var value by remember { mutableIntStateOf(score?.value ?: SleepScore.estimate(night) ?: 75) }
    Column(
        Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column {
            Eyebrow(Format.day(night.end))
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.score_sheet_title), style = AppType.title.copy(fontSize = 28.sp), color = Palette.text)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.score_sheet_body), style = MaterialTheme.typography.bodyMedium, color = Palette.muted)
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$value", style = AppType.number(64f), color = Palette.text)
            val rating = SleepRating.of(value)
            Text(ratingName(rating), style = MaterialTheme.typography.titleMedium, color = ratingColor(rating))
        }
        RulerDial(
            value, 0..100, step = 1, mid = 5, major = 10,
            label = { "$it" },
            onChange = { value = it },
            modifier = Modifier.fillMaxWidth().height(76.dp),
            tickSpacing = 11.dp,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
            if (score?.fromGoogle == true) SoftButton(stringResource(R.string.score_use_estimate), onClick = onClear)
            PrimaryButton(stringResource(R.string.score_save), onClick = { onSave(value) })
        }
    }
}
