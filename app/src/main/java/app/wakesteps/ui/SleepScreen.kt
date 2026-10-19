package app.wakesteps.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
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
import app.wakesteps.ui.theme.AppType
import app.wakesteps.ui.theme.Palette
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.PI
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

/** Google's six factors, each with how it went and a bar for how well. */
@Composable
private fun ScoreFactors(parts: List<ScorePart>, modifier: Modifier) {
    val grow by animateOnShow(1f, durationMillis = 1100, delayMillis = 250)
    val track = Color.White.copy(alpha = 0.06f)
    Panel(modifier.fillMaxWidth()) {
        Eyebrow(stringResource(R.string.sleep_score_factors))
        parts.forEach { part ->
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(factorName(part.factor), style = MaterialTheme.typography.bodyLarge, color = Palette.text, modifier = Modifier.weight(1f))
                when (part.factor) {
                    ScoreFactor.FULL_AWAKENINGS -> Text("${part.count}", style = AppType.number(20f), color = Palette.text)
                    ScoreFactor.RESTLESSNESS -> Row(verticalAlignment = Alignment.Bottom) {
                        Text(durationText(part.minutes, 20f), style = AppType.number(20f), color = Palette.text)
                        Caption("  ·  ${part.count}×", Modifier.padding(bottom = 2.dp))
                    }
                    else -> Text(durationText(part.minutes, 20f), style = AppType.number(20f), color = Palette.text)
                }
            }
            Spacer(Modifier.height(8.dp))
            val color = when {
                part.quality >= 0.75f -> stageColor(SleepStage.REM)
                part.quality >= 0.45f -> stageColor(SleepStage.LIGHT)
                else -> stageColor(SleepStage.AWAKE)
            }
            Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                val r = CornerRadius(size.height / 2)
                drawRoundRect(track, cornerRadius = r)
                val w = size.width * part.quality * grow
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
private fun StagesPanel(night: SleepNight, modifier: Modifier) {
    val total = night.inBed.toMillis().coerceAtLeast(1).toFloat()
    Panel(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(stringResource(R.string.sleep_stages), Modifier.weight(1f))
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
        Row(Modifier.fillMaxWidth().height((lanes.size * 38).dp)) {
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
                clipRect(right = size.width * reveal) {
                    // Steps between stages first, so the bars sit on top of them.
                    chart.bars.zipWithNext { a, b ->
                        val at = x(b.start)
                        drawLine(Color.White.copy(alpha = 0.16f), Offset(at, laneY(a.stage)), Offset(at, laneY(b.stage)), 1.5.dp.toPx())
                    }
                    for (s in chart.bars) {
                        val left = x(s.start)
                        val width = (x(s.end) - left).coerceAtLeast(2.dp.toPx())
                        val top = laneY(s.stage) - barHeight / 2
                        val color = stageColor(s.stage)
                        drawRoundRect(
                            Brush.verticalGradient(listOf(color, color.copy(alpha = 0.7f)), startY = top, endY = top + barHeight),
                            Offset(left, top), Size(width, barHeight), CornerRadius(minOf(barHeight / 2.6f, width / 2)),
                        )
                    }
                    // Each stir as a slim mark with a soft glow, on its own row.
                    val markWidth = 3.dp.toPx()
                    for (m in chart.marks) {
                        val color = stageColor(m.stage)
                        val center = (x(m.start) + x(m.end)) / 2
                        val width = maxOf(x(m.end) - x(m.start), markWidth)
                        val y = laneY(m.stage)
                        drawRoundRect(
                            color.copy(alpha = 0.13f),
                            Offset(center - width / 2 - markWidth, y - barHeight * 0.55f),
                            Size(width + markWidth * 2, barHeight * 1.1f),
                            CornerRadius(width / 2 + markWidth),
                        )
                        drawRoundRect(color, Offset(center - width / 2, y - barHeight * 0.4f), Size(width, barHeight * 0.8f), CornerRadius(width / 2))
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

private val LANE_LABELS = 66.dp

/**
 * A night in full, entering in turn from [first]: the score (or the stages) as the hero, what
 * shaped the score, the stages, and the chart through the night.
 */
@Composable
private fun NightContent(
    night: SleepNight,
    score: NightScore?,
    restlessAsAwake: Boolean,
    onEditScore: () -> Unit,
    ringSize: Dp,
    first: Int,
    chartMinute: Int?,
) {
    val display = night.counted(restlessAsAwake)
    SleepHero(display, score, onEditScore, Modifier.entrance(first), ringSize)
    SleepScore.parts(night)?.let { ScoreFactors(it, Modifier.entrance(first + 1)) }
    if (display.staged) StagesPanel(display, Modifier.entrance(first + 2))
    if (display.stages.size > 1) NightChart(display, Modifier.entrance(first + 3), chartMinute)
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
                    stringResource(if (state.loading) R.string.loading else R.string.sleep_none),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Palette.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                )
                return@Column
            }
            NightContent(latest, scores[latest.wakeDate()], restlessAsAwake, { onEditScore(latest) }, 268.dp, first = 1, chartMinute)
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
                    Eyebrow(stringResource(R.string.sleep_detail_eyebrow))
                    Text(Format.day(night.end), style = AppType.title.copy(fontSize = AppType.title.fontSize * 0.82f), color = Palette.text)
                }
            }
            NightContent(night, score, restlessAsAwake, onEditScore, 236.dp, first = 1, chartMinute = null)
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
