package app.wakesteps.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** True when animations should jump straight to their end state (screen renders in tests). */
val LocalSnapAnimations = staticCompositionLocalOf { false }

/** Fast out, long soft landing: the app's standard motion curve. */
val Glide = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * Animates from [from] to [target] when first shown, then from wherever it is to each new
 * target. [delayMillis] only applies to the first run.
 */
@Composable
fun animateOnShow(target: Float, from: Float = 0f, durationMillis: Int = 1100, delayMillis: Int = 0): State<Float> {
    val snap = LocalSnapAnimations.current
    val anim = remember { Animatable(if (snap) target else from) }
    val first = remember { booleanArrayOf(true) }
    LaunchedEffect(target) {
        if (snap) {
            anim.snapTo(target)
        } else {
            if (first[0] && delayMillis > 0) delay(delayMillis.toLong())
            first[0] = false
            anim.animateTo(target, tween(durationMillis, easing = Glide))
        }
    }
    return anim.asState()
}

/** Sections drift up and fade in, one after another, the first time a screen appears. */
@Composable
fun Modifier.entrance(index: Int): Modifier {
    val p by animateOnShow(1f, durationMillis = 650, delayMillis = 70 * index)
    val rise = with(LocalDensity.current) { 18.dp.toPx() }
    return graphicsLayer {
        alpha = p
        translationY = (1f - p) * rise
    }
}

/** Gives a pressed element a slight sink, so taps feel physical. */
@Composable
fun Modifier.pressScale(source: MutableInteractionSource, pressed: Float = 0.97f): Modifier {
    val isPressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (isPressed) pressed else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
