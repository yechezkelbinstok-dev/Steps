package app.wakesteps.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import app.wakesteps.widget.accentColor
import app.wakesteps.widget.gradient

/** The ring styles as tiny widgets in a row you can swipe through; tap one to use it. */
@Composable
fun WidgetStylePicker(selected: WidgetStyle, onSelect: (WidgetStyle) -> Unit) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (selected.ordinal - 1).coerceAtLeast(0))
    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(WidgetStyle.entries) { style ->
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
                    .width(88.dp)
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
                            WidgetStyle.PLATINUM -> R.string.widget_style_platinum
                            WidgetStyle.AMETHYST -> R.string.widget_style_amethyst
                            WidgetStyle.ROSE -> R.string.widget_style_rose
                            WidgetStyle.EMERALD -> R.string.widget_style_emerald
                        }
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = labelColor,
                    maxLines = 1,
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
    val accent = color(style.accentColor)
    val blend = style.gradient
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
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
        Canvas(Modifier.size(62.dp)) {
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
            val brush = when {
                blend != null -> color(blend.start).let { start ->
                    Brush.sweepGradient(0f to start, sweep to color(blend.end), 1f to start)
                }
                style == WidgetStyle.FADE -> Brush.sweepGradient(
                    0f to accent.copy(alpha = 0.42f), sweep to accent, 1f to accent.copy(alpha = 0.42f),
                )
                else -> SolidColor(accent)
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
