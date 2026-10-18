package app.wakesteps.widget

import androidx.annotation.ColorRes
import app.wakesteps.R
import app.wakesteps.data.WidgetStyle

/** A style whose ring blends from [start] at 12 o'clock to [end] at the tip, drawn with [arcs]. */
internal class RingGradient(@param:ColorRes val start: Int, @param:ColorRes val end: Int, val arcs: IntArray)

/**
 * The blend a style's ring uses before the goal, or null for the wallpaper-toned styles (soft,
 * fade), whose plain arcs are tinted instead. Colours are ring_* / widget_* in values/colors.xml;
 * the widget's arcs bake the same ones in (tools/gen_rings.py).
 */
internal val WidgetStyle.gradient: RingGradient?
    get() = when (this) {
        WidgetStyle.SOFT, WidgetStyle.FADE -> null
        WidgetStyle.VIVID -> RingGradient(R.color.widget_aqua, R.color.widget_ring_end, VIVID_ARCS)
        WidgetStyle.PLATINUM -> RingGradient(R.color.ring_platinum_start, R.color.ring_platinum_end, PLATINUM_ARCS)
        WidgetStyle.AMETHYST -> RingGradient(R.color.ring_amethyst_start, R.color.ring_amethyst_end, AMETHYST_ARCS)
        WidgetStyle.ROSE -> RingGradient(R.color.ring_rose_start, R.color.ring_rose_end, ROSE_ARCS)
        WidgetStyle.EMERALD -> RingGradient(R.color.ring_emerald_start, R.color.ring_emerald_end, EMERALD_ARCS)
    }

/** Footprints, glow and stat icons before the goal: the ring's brightest colour. */
@get:ColorRes
internal val WidgetStyle.accentColor: Int
    get() = when (this) {
        WidgetStyle.SOFT, WidgetStyle.FADE -> R.color.widget_accent
        WidgetStyle.VIVID -> R.color.widget_aqua
        else -> gradient!!.end
    }
