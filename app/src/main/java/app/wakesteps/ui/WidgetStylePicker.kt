package app.wakesteps.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import app.wakesteps.ui.theme.Palette
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.wakesteps.R
import app.wakesteps.data.WidgetStyle

/** The widget looks as tiny widgets, side by side; tap one to use it. */
@Composable
fun WidgetStylePicker(selected: WidgetStyle, onSelect: (WidgetStyle) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        WidgetStyle.entries.forEach { style ->
            val isSelected = style == selected
            val source = remember { MutableInteractionSource() }
            val scale by animateFloatAsState(
                if (isSelected) 1f else 0.94f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                label = "styleScale",
            )
            val labelColor by animateColorAsState(if (isSelected) Palette.text else Palette.faint, label = "styleLabel")
            Column(
                modifier = Modifier
                    .weight(1f)
                    .pressScale(source, pressed = 0.9f)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .selectable(
                        selected = isSelected,
                        interactionSource = source,
                        indication = null,
                        role = Role.RadioButton,
                        onClick = { onSelect(style) },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MiniWidget(style, isSelected)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(
                        when (style) {
                            WidgetStyle.SOFT -> R.string.widget_style_soft
                            WidgetStyle.FADE -> R.string.widget_style_fade
                            WidgetStyle.VIVID -> R.string.widget_style_vivid
                        }
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = labelColor,
                )
            }
        }
    }
}

/** A card and ring in the widget's own colours (see widget_* resources and StepsWidget). */
@Composable
private fun MiniWidget(style: WidgetStyle, selected: Boolean) {
    val context = LocalContext.current
    fun color(id: Int) = Color(context.getColor(id))
    val card = color(R.color.widget_bg)
    val track = color(R.color.widget_track)
    val accent = color(if (style == WidgetStyle.VIVID) R.color.widget_aqua else R.color.widget_accent)
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp)
            .clip(shape)
            .background(card)
            .background(
                // The widget's sheen: light from the top-left, a little shade bottom-right.
                Brush.linearGradient(listOf(Color.White.copy(alpha = 0.12f), Color.Transparent, Color.Black.copy(alpha = 0.22f)))
            )
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) SolidColor(accent) else glassEdge(),
                shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(64.dp)) {
            val stroke = size.minDimension * 0.11f
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawCircle(
                Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.22f), accent.copy(alpha = 0.08f), Color.Transparent),
                    radius = size.minDimension / 2,
                ),
            )
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            val sweep = 0.75f
            val brush = when (style) {
                WidgetStyle.SOFT -> SolidColor(accent)
                WidgetStyle.FADE -> Brush.sweepGradient(
                    0f to accent.copy(alpha = 0.42f), sweep to accent, 1f to accent.copy(alpha = 0.42f),
                )
                WidgetStyle.VIVID -> Brush.sweepGradient(
                    0f to accent, sweep to color(R.color.widget_ring_end), 1f to accent,
                )
            }
            // Sweep gradients start at 3 o'clock; turn the canvas so the arc starts at 12.
            rotate(-90f) {
                drawArc(brush, 0f, 360f * sweep, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Image(
            painter = painterResource(R.drawable.ic_steps),
            contentDescription = null,
            colorFilter = ColorFilter.tint(accent),
            modifier = Modifier.size(18.dp),
        )
    }
}
