package app.wakesteps.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wakesteps.data.WidgetStyle
import app.wakesteps.ui.theme.AppColors
import app.wakesteps.ui.theme.Display
import app.wakesteps.ui.theme.Palette
import kotlin.math.cos
import kotlin.math.sin

val CardShape = RoundedCornerShape(24.dp)

/** The app's card: the widget's tone, a hairline edge, generous corners. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(CardShape)
            .background(Palette.card)
            .border(1.dp, Palette.outline, CardShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** Big screen title at the top of each tab. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.headlineMedium,
        color = Palette.text,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

/** Small quiet heading over a card's content. */
@Composable
fun CardLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = Palette.muted, modifier = modifier)
}

/** A number in the display cut, optionally with a smaller unit after it. */
@Composable
fun BigValue(value: String, size: Int, unit: String? = null, color: Color = Palette.text) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            value,
            style = TextStyle(fontFamily = Display, fontSize = size.sp, lineHeight = (size * 1.1f).sp),
            color = color,
            maxLines = 1,
        )
        if (unit != null) {
            Spacer(Modifier.width(4.dp))
            Text(
                unit,
                style = MaterialTheme.typography.titleMedium,
                color = Palette.muted,
                modifier = Modifier.padding(bottom = (size * 0.12f).dp),
            )
        }
    }
}

/** An icon sitting in a soft disc of its own colour. */
@Composable
fun IconDisc(@DrawableRes icon: Int, tint: Color, size: Dp = 36.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = Palette.accent, contentColor = Palette.background),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun SoftButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = Palette.cardHigh, contentColor = Palette.text),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/** A prompt that needs the user to act (permissions, adding the widget). */
@Composable
fun ActionCard(title: String, body: String, button: String, onClick: () -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
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

/** Two-way switch drawn as one pill, the chosen side lit. */
@Composable
fun PillToggle(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(CircleShape)
            .background(Palette.card)
            .border(1.dp, Palette.outline, CircleShape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(if (on) Palette.cardHigh else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) Palette.text else Palette.muted,
                    maxLines = 1,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- ring

/** How far round the ring is: the first lap up to the goal, then any extra lap on top. */
class RingProgress(steps: Long, goal: Int) {
    private val ratio = if (goal > 0) steps.toFloat() / goal else 0f
    val base = ratio.coerceIn(0f, 1f)
    val goalReached = ratio >= 1f
    val lap = if (ratio > 1f) ((ratio - 1f) % 1f).let { if (it == 0f) 1f else it } else 0f
}

/**
 * The goal ring, drawn like the widget's: a glow and track in the wallpaper's tone, the arc on
 * top in the user's chosen style until the goal, then gold, and past the goal another gold lap
 * over the full ring with a shadow ahead of its tip. [content] sits in the middle.
 */
@Composable
fun StepsRing(
    progress: RingProgress,
    size: Dp,
    modifier: Modifier = Modifier,
    strokeFraction: Float = 0.1f,
    glow: Boolean = true,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val colors = Palette
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) { drawRing(colors, progress, size.toPx() * strokeFraction, glow) }
        content()
    }
}

private fun DrawScope.drawRing(colors: AppColors, p: RingProgress, stroke: Float, glow: Boolean) {
    val inset = stroke / 2
    val arcSize = Size(size.width - stroke, size.height - stroke)
    val topLeft = Offset(inset, inset)
    if (glow) {
        val tone = if (p.goalReached) colors.goal else colors.accent
        drawCircle(
            Brush.radialGradient(
                listOf(tone.copy(alpha = 0.20f), tone.copy(alpha = 0.07f), Color.Transparent),
                radius = size.minDimension / 2,
            ),
        )
    }
    drawArc(colors.track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))

    // Sweep gradients start at 3 o'clock; turn the canvas so arcs start at 12.
    rotate(-90f) {
        if (p.base > 0f) {
            val brush = when {
                p.goalReached -> fade(1f, colors.goalStart, colors.goalEnd)
                colors.style == WidgetStyle.FADE -> fade(p.base, colors.accent.copy(alpha = 0.42f), colors.accent)
                else -> SolidColor(colors.accent)
            }
            drawArc(brush, 0f, 360f * p.base, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        if (p.lap > 0f) {
            val radius = arcSize.width / 2
            val tip = Math.toRadians(360.0 * p.lap) + (stroke / 4) / radius
            val at = center + Offset(radius * cos(tip).toFloat(), radius * sin(tip).toFloat())
            val ring = Path().apply {
                fillType = PathFillType.EvenOdd
                addOval(Rect(center, radius + stroke / 2))
                addOval(Rect(center, radius - stroke / 2))
            }
            clipPath(ring) {
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
    }
}

/** Sweep from [from] at 12 o'clock to [to] at the tip; a full ring blends out and back. */
private fun fade(fraction: Float, from: Color, to: Color): Brush =
    if (fraction >= 1f) Brush.sweepGradient(0f to from, 0.5f to to, 1f to from)
    else Brush.sweepGradient(0f to from, fraction to to, 1f to from)

// ---------------------------------------------------------------- bars

/**
 * Simple bar chart: one rounded bar per value, scaled to the largest. [highlight] picks bars to
 * draw in full colour (the rest are dimmer); [line] draws a dashed reference line at that value.
 */
@Composable
fun Bars(
    values: List<Long>,
    modifier: Modifier = Modifier,
    highlight: (Int) -> Boolean = { true },
    line: Long? = null,
    barFraction: Float = 0.62f,
) {
    val colors = Palette
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val max = maxOf(values.max(), line ?: 0L).coerceAtLeast(1L).toFloat()
        val slot = size.width / values.size
        val w = (slot * barFraction).coerceAtLeast(2.dp.toPx())
        val radius = CornerRadius(minOf(w / 2, 6.dp.toPx()))
        val minH = 3.dp.toPx()
        values.forEachIndexed { i, v ->
            val h = (size.height * v / max).coerceAtLeast(minH)
            val x = i * slot + (slot - w) / 2
            val lit = highlight(i)
            if (v == 0L) {
                drawRoundRect(colors.faint.copy(alpha = 0.35f), Offset(x, size.height - minH), Size(w, minH), radius)
            } else {
                drawRoundRect(
                    Brush.verticalGradient(
                        listOf(colors.accent, colors.accent.copy(alpha = 0.55f)),
                        startY = size.height - h, endY = size.height,
                    ),
                    Offset(x, size.height - h), Size(w, h), radius,
                    alpha = if (lit) 1f else 0.4f,
                )
            }
        }
        line?.let {
            val y = size.height - size.height * it / max
            val dash = 4.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawLine(colors.muted.copy(alpha = 0.5f), Offset(x, y), Offset(minOf(x + dash, size.width), y), 1.dp.toPx())
                x += dash * 2
            }
        }
    }
}
