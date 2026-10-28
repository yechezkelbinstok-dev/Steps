package app.wakesteps.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wakesteps.ui.theme.AppType
import app.wakesteps.ui.theme.Palette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs

// Zooming into a span of time (a night, a day): what's in view, the gestures, the times along it,
// and the whole span small below with the part in view framed.

/** The part of a span (a night, a day) in view, [left] to [right] as fractions of it, never narrower than [minWidth]. */
@Stable
internal class SpanView(initial: ClosedFloatingPointRange<Float>, val minWidth: Float) {
    var left by mutableFloatStateOf(0f)
        private set
    var right by mutableFloatStateOf(1f)
        private set
    val width: Float get() = right - left
    val zoomed: Boolean get() = width < 0.999f
    val atClosest: Boolean get() = width <= minWidth * 1.01f
    private var glide: Job? = null

    init {
        set(initial.start, initial.endInclusive)
    }

    /** [l]..[r], made wide enough and kept within the night. */
    private fun fit(l: Float, r: Float): Pair<Float, Float> {
        val w = (r - l).coerceIn(minWidth, 1f)
        val fl = ((l + r) / 2 - w / 2).coerceIn(0f, 1f - w)
        return fl to fl + w
    }

    private fun set(l: Float, r: Float) {
        val (a, b) = fit(l, r)
        left = a
        right = b
    }

    /** Straight to [l]..[r], as fingers move. */
    fun snap(l: Float, r: Float) {
        glide?.cancel()
        set(l, r)
    }

    /** Glides to [l]..[r], both edges together. */
    fun glideTo(scope: CoroutineScope, l: Float, r: Float) {
        glide?.cancel()
        val (a, b) = fit(l, r)
        val l0 = left
        val r0 = right
        glide = scope.launch {
            animate(0f, 1f, animationSpec = tween(460, easing = FastOutSlowInEasing)) { t, _ ->
                left = l0 + (a - l0) * t
                right = r0 + (b - r0) * t
            }
        }
    }

    /** A pinch: zoom by [factor] about [focus] (a fraction across the view), moved by [pan] (a fraction of its width). */
    fun pinch(focus: Float, factor: Float, pan: Float) {
        val at = left + focus * width
        val w = (width / factor).coerceIn(minWidth, 1f)
        val l = at - focus * w - pan * w
        snap(l, l + w)
    }

    /** Zooms in (or out, below 1) by [factor], about [focus] across the view. */
    fun zoom(scope: CoroutineScope, factor: Float, focus: Float = 0.5f) {
        val at = left + focus * width
        val w = (width / factor).coerceIn(minWidth, 1f)
        glideTo(scope, at - focus * w, at - focus * w + w)
    }

    fun moveBy(d: Float) = snap(left + d, right + d)

    fun centerOn(scope: CoroutineScope, at: Float) = glideTo(scope, at - width / 2, at + width / 2)
}

/**
 * Pinch (and two-finger drag) to zoom and move, one finger along to read the moment under it,
 * tap to pick a moment, double-tap to zoom in there (or, all the way in, back out). Up-and-down
 * drags are left to scroll the page. Positions are fractions across the view.
 */
internal fun Modifier.zoomable(
    key: Any?,
    onTap: (Float) -> Unit,
    onDoubleTap: (Float) -> Unit,
    onScrub: (Float) -> Unit,
    onScrubEnd: () -> Unit,
    onPinch: (focus: Float, factor: Float, pan: Float) -> Unit,
): Modifier = this
    .pointerInput(key) {
        detectTapGestures(
            onTap = { onTap((it.x / size.width).coerceIn(0f, 0.9999f)) },
            onDoubleTap = { onDoubleTap((it.x / size.width).coerceIn(0f, 1f)) },
        )
    }
    .pointerInput(key) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var scrubbing = false
            var pinching = false
            var leftAlone = false
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break
                if (pressed.size >= 2) {
                    if (scrubbing) {
                        scrubbing = false
                        onScrubEnd()
                    }
                    pinching = true
                    val focus = event.calculateCentroid(useCurrent = true).x / size.width
                    onPinch(focus, event.calculateZoom(), event.calculatePan().x / size.width)
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                } else if (!pinching && !leftAlone) {
                    val change = pressed.first()
                    if (change.isConsumed) break // the page took it as a scroll
                    if (!scrubbing) {
                        val d = change.position - down.position
                        if (d.getDistance() > viewConfiguration.touchSlop) {
                            if (abs(d.x) > abs(d.y)) scrubbing = true else leftAlone = true
                        }
                    }
                    if (scrubbing) {
                        change.consume()
                        onScrub((change.position.x / size.width).coerceIn(0f, 0.9999f))
                    }
                }
            }
            if (scrubbing) onScrubEnd()
        }
    }

/**
 * Times worth marking across [from]..[to], no more than [maxCount]: every few hours, on the hour,
 * or every half, quarter, 10 or 5 minutes as it narrows.
 */
internal fun ticks(from: Instant, to: Instant, zone: ZoneId, maxCount: Int): List<Instant> {
    val span = Duration.between(from, to).toMinutes().coerceAtLeast(1)
    val step = listOf(5L, 10L, 15L, 30L, 60L, 120L, 180L, 240L).firstOrNull { span / it <= maxCount - 1 } ?: 360L
    val day = from.atZone(zone).truncatedTo(ChronoUnit.DAYS)
    val first = Duration.between(day.toInstant(), from).toMinutes().let { (it + step - 1) / step * step }
    return generateSequence(day.plusMinutes(first).toInstant()) { it.plus(Duration.ofMinutes(step)) }
        .takeWhile { it.isBefore(to) }
        .filter { !it.isBefore(from) }
        .toList()
}

private val HOUR_TICK = DateTimeFormatter.ofPattern("h a")
private val MINUTE_TICK = DateTimeFormatter.ofPattern("h:mm")

/** The tick times under the chart, each centred under its line (kept inside the edges). */
@Composable
internal fun TickLabels(ticks: List<Instant>, from: Instant, to: Instant, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val style = AppType.eyebrow.copy(color = Palette.faint, fontSize = 10.5.sp)
    val zone = ZoneId.systemDefault()
    Canvas(modifier) {
        val span = Duration.between(from, to).toMillis().coerceAtLeast(1).toFloat()
        var free = 0f
        for (t in ticks) {
            val local = t.atZone(zone)
            val text = (if (local.minute == 0) HOUR_TICK else MINUTE_TICK).format(local)
            val m = measurer.measure(text, style)
            val x = Duration.between(from, t).toMillis() / span * size.width
            val left = (x - m.size.width / 2f).coerceIn(0f, size.width - m.size.width)
            if (left < free) continue // never let two run into each other
            drawText(m, topLeft = Offset(left, 0f))
            free = left + m.size.width + 10.dp.toPx()
        }
    }
}

@Composable
internal fun ZoomButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    val tone by animateColorAsState(if (enabled) Palette.text else Palette.faint.copy(alpha = 0.4f), label = "zoomButton")
    RoundIconButton(onClick = { if (enabled) onClick() }, size = 40.dp) {
        Text(symbol, style = AppType.number(22f), color = tone)
    }
}

/** The whole span, small: drag along it to move the view, tap to centre it there. */
internal fun Modifier.overviewGestures(view: SpanView, scope: CoroutineScope): Modifier = this
    .pointerInput(view) { detectTapGestures { view.centerOn(scope, it.x / size.width) } }
    .pointerInput(view) {
        detectHorizontalDragGestures { change, amount ->
            change.consume()
            view.moveBy(amount / size.width)
        }
    }

/** Over the whole span drawn small: the part out of view shaded, the part in view framed. */
internal fun DrawScope.drawViewFrame(view: SpanView, frame: Color) {
    if (!view.zoomed) return
    val shade = Color(0xFF04060F)
    val radius = CornerRadius(10.dp.toPx())
    val l = view.left * size.width
    val r = view.right * size.width
    drawRect(shade.copy(alpha = 0.62f), Offset.Zero, Size(l, size.height))
    drawRect(shade.copy(alpha = 0.62f), Offset(r, 0f), Size(size.width - r, size.height))
    drawRoundRect(frame.copy(alpha = 0.08f), Offset(l, 0f), Size(r - l, size.height), radius)
    drawRoundRect(frame.copy(alpha = 0.8f), Offset(l, 0f), Size(r - l, size.height), radius, style = Stroke(1.5.dp.toPx()))
}
