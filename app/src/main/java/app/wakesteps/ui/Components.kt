package app.wakesteps.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.wakesteps.core.Format
import app.wakesteps.data.WidgetStyle
import app.wakesteps.ui.theme.AppColors
import app.wakesteps.ui.theme.AppType
import app.wakesteps.ui.theme.Palette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin

val PanelShape = RoundedCornerShape(28.dp)

/** A light top edge fading out down the sides: the glass look of every panel. */
@Composable
fun glassEdge(): Brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.03f)))

/** The app's panel: the widget's tone over the backdrop, a glass edge, soft corners. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(20.dp),
    shape: Shape = PanelShape,
    content: @Composable ColumnScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    Column(
        modifier
            .then(if (onClick != null) Modifier.pressScale(source) else Modifier)
            .clip(shape)
            .background(Palette.card)
            .border(1.dp, glassEdge(), shape)
            .then(if (onClick != null) Modifier.clickable(source, indication = null, onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** Small spaced capitals naming a section. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = Palette.muted) {
    Text(text.uppercase(), style = AppType.eyebrow, color = color, modifier = modifier, maxLines = 1)
}

/** A number that counts up when it first appears and glides to each new value. */
@Composable
fun CountUp(
    value: Double,
    size: Float,
    modifier: Modifier = Modifier,
    color: Color = Palette.text,
    durationMillis: Int = 1200,
    delayMillis: Int = 0,
    format: (Double) -> String = { Format.number(it.roundToLong()) },
) {
    val shown by animateOnShow(value.toFloat(), durationMillis = durationMillis, delayMillis = delayMillis)
    Text(format(shown.toDouble()), style = AppType.number(size), color = color, modifier = modifier, maxLines = 1)
}

/** A big value with a smaller unit after it: "3.8 mi". */
@Composable
fun ValueWithUnit(
    value: Double,
    size: Float,
    unit: String?,
    decimals: Int = 0,
    delayMillis: Int = 0,
) {
    Row(verticalAlignment = Alignment.Bottom) {
        CountUp(
            value, size, delayMillis = delayMillis,
            format = { if (decimals == 0) Format.number(it.roundToLong()) else "%.${decimals}f".format(it) },
        )
        if (unit != null) {
            Spacer(Modifier.width(4.dp))
            Text(
                unit,
                style = MaterialTheme.typography.titleMedium,
                color = Palette.muted,
                modifier = Modifier.padding(bottom = (size * 0.13f).dp),
            )
        }
    }
}

/** An icon sitting in a soft disc of its own colour. */
@Composable
fun IconDisc(@DrawableRes icon: Int, tint: Color, size: Dp = 34.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.52f))
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    PillButton(text, onClick, modifier, Palette.accent, Palette.background)
}

@Composable
fun SoftButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    PillButton(text, onClick, modifier, Color.White.copy(alpha = 0.08f), Palette.text)
}

@Composable
private fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier, fill: Color, content: Color) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressScale(source, pressed = 0.95f)
            .clip(CircleShape)
            .background(fill)
            .clickable(source, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = content)
    }
}

/** A prompt that needs the user to act (permissions, adding the widget). */
@Composable
fun ActionCard(title: String, body: String, button: String, onClick: () -> Unit) {
    Panel(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.text)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Palette.muted)
        Spacer(Modifier.height(16.dp))
        PrimaryButton(button, onClick)
    }
}

@Composable
fun ErrorNote(message: String) {
    Text(
        message,
        style = MaterialTheme.typography.bodyMedium,
        color = Palette.error,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    )
}

/** A choice of a few options in one pill, with a lit segment that slides to the chosen one. */
@Composable
fun PillToggle(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier
            .clip(CircleShape)
            .background(Palette.card)
            .border(1.dp, glassEdge(), CircleShape)
            .padding(4.dp),
    ) {
        val segment = maxWidth / options.size
        val x by animateDpAsState(
            segment * selected,
            spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "toggle",
        )
        Box(
            Modifier
                .offset { IntOffset(x.roundToPx(), 0) }
                .width(segment)
                .height(40.dp)
                .clip(CircleShape)
                .background(Palette.cardHigh)
                .border(1.dp, glassEdge(), CircleShape),
        )
        Row {
            options.forEachIndexed { i, label ->
                val color by animateColorAsState(if (i == selected) Palette.text else Palette.muted, label = "toggleText")
                Box(
                    Modifier
                        .width(segment)
                        .height(40.dp)
                        .clip(CircleShape)
                        .clickable(remember { MutableInteractionSource() }, indication = null) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- backdrop

/**
 * Behind every screen: the page deepens to the wallpaper's colour at the top, with slow drifting
 * glows in the widget's tones. Moves too slowly to notice, but keeps the app from feeling flat.
 */
@Composable
fun Backdrop(modifier: Modifier = Modifier) {
    val c = Palette
    val drift = if (LocalSnapAnimations.current) 0f else {
        val t by rememberInfiniteTransition(label = "backdrop").animateFloat(
            0f, 1f, infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "drift",
        )
        t
    }
    Canvas(modifier.fillMaxSize()) {
        drawRect(c.background)
        drawRect(Brush.verticalGradient(listOf(c.backgroundTop, c.background), endY = size.height * 0.55f))
        val w = size.width
        val a = drift * 2 * PI.toFloat()
        fun glow(x: Float, y: Float, r: Float, color: Color) =
            drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), Offset(x, y), r), r, Offset(x, y))
        glow(w * (0.3f + 0.08f * sin(a)), w * (0.42f + 0.05f * cos(a)), w * 0.8f, c.glow.copy(alpha = 0.6f))
        glow(w * (0.85f + 0.06f * cos(a)), w * (0.12f + 0.06f * sin(a)), w * 0.62f, c.accent.copy(alpha = 0.12f))
        glow(w * (0.1f + 0.08f * sin(a + 2f)), w * (0.95f + 0.06f * cos(a + 1f)), w * 0.55f, c.accentEnd.copy(alpha = 0.07f))
    }
}

// ---------------------------------------------------------------- ring

/** How far round the ring is: the first lap up to the goal, then any extra lap on top. */
class RingProgress(val ratio: Float) {
    val base = ratio.coerceIn(0f, 1f)
    val goalReached = ratio >= 1f
    val lap = if (ratio > 1f) ((ratio - 1f) % 1f).let { if (it == 0f) 1f else it } else 0f

    companion object {
        fun of(steps: Long, goal: Int) = if (goal > 0) steps.toFloat() / goal else 0f
    }
}

/**
 * The goal ring, drawn like the widget's: a glow and track in the wallpaper's tone, the arc on
 * top in the user's chosen style until the goal, then gold, and past the goal another gold lap
 * over the full ring with a shadow ahead of its tip. It sweeps round to its value when it first
 * appears; a met goal gets a slow light passing round it. [content] sits in the middle.
 */
@Composable
fun StepsRing(
    ratio: Float,
    size: Dp,
    modifier: Modifier = Modifier,
    strokeFraction: Float = 0.1f,
    glow: Boolean = true,
    animate: Boolean = true,
    delayMillis: Int = 0,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val colors = Palette
    val shown by animateOnShow(ratio, durationMillis = if (animate) 1500 else 0, delayMillis = delayMillis)
    val progress = RingProgress(shown)
    val sheen = if (progress.goalReached && glow && !LocalSnapAnimations.current) {
        val t by rememberInfiniteTransition(label = "sheen").animateFloat(
            0f, 1f, infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart), label = "sheenAngle",
        )
        t
    } else null
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) { drawRing(colors, progress, size.toPx() * strokeFraction, glow, sheen) }
        content()
    }
}

private fun DrawScope.drawRing(colors: AppColors, p: RingProgress, stroke: Float, glow: Boolean, sheen: Float?) {
    val inset = stroke / 2
    val arcSize = Size(size.width - stroke, size.height - stroke)
    val topLeft = Offset(inset, inset)
    if (glow) {
        val tone = if (p.goalReached) colors.goal else colors.accent
        drawCircle(
            Brush.radialGradient(
                listOf(tone.copy(alpha = 0.22f), tone.copy(alpha = 0.06f), Color.Transparent),
                radius = size.minDimension / 2,
            ),
        )
    }
    drawArc(colors.track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))

    // Sweep gradients start at 3 o'clock; turn the canvas so arcs start at 12.
    rotate(-90f) {
        if (p.base > 0.002f) {
            val brush = when {
                p.goalReached -> fade(1f, colors.goalStart, colors.goalEnd)
                colors.style == WidgetStyle.FADE -> fade(p.base, colors.accent.copy(alpha = 0.42f), colors.accent)
                colors.gradient -> fade(p.base, colors.ringStart, colors.ringEnd)
                else -> SolidColor(colors.accent)
            }
            drawArc(brush, 0f, 360f * p.base, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        if (p.lap > 0f) {
            val radius = arcSize.width / 2
            val tip = Math.toRadians(360.0 * p.lap) + (stroke / 4) / radius
            val at = center + Offset(radius * cos(tip).toFloat(), radius * sin(tip).toFloat())
            clipPath(ringPath(radius, stroke)) {
                drawCircle(
                    Brush.radialGradient(
                        0f to Color.Black.copy(alpha = 0.45f), 0.55f to Color.Black.copy(alpha = 0.2f), 1f to Color.Transparent,
                        center = at, radius = stroke,
                    ),
                    radius = stroke, center = at,
                )
            }
            drawArc(
                fade(p.lap, colors.goalStart, colors.lapEnd), 0f, 360f * p.lap, false, topLeft, arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        if (sheen != null) {
            // A soft highlight travelling round the gold ring.
            rotate(360f * sheen) {
                clipPath(ringPath(arcSize.width / 2, stroke)) {
                    drawCircle(
                        Brush.sweepGradient(
                            0f to Color.Transparent, 0.05f to Color.White.copy(alpha = 0.32f),
                            0.1f to Color.Transparent, 1f to Color.Transparent,
                        ),
                    )
                }
            }
        }
    }
}

private fun DrawScope.ringPath(radius: Float, stroke: Float) = Path().apply {
    fillType = PathFillType.EvenOdd
    addOval(Rect(center, radius + stroke / 2))
    addOval(Rect(center, radius - stroke / 2))
}

/** Sweep from [from] at 12 o'clock to [to] at the tip; a full ring blends out and back. */
private fun fade(fraction: Float, from: Color, to: Color): Brush =
    if (fraction >= 1f) Brush.sweepGradient(0f to from, 0.5f to to, 1f to from)
    else Brush.sweepGradient(0f to from, fraction to to, 1f to from)

/** A thin vertical rule between side-by-side values. */
@Composable
fun VerticalRule(modifier: Modifier = Modifier) {
    Box(modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.07f)))
}

/** Plain text in the app's body style, for small labels next to numbers. */
@Composable
fun Caption(text: String, modifier: Modifier = Modifier, color: Color = Palette.muted, style: TextStyle = MaterialTheme.typography.bodyMedium) {
    Text(text, style = style, color = color, modifier = modifier, maxLines = 1)
}
