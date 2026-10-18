package app.wakesteps.widget

import android.content.Context
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.wakesteps.R
import app.wakesteps.app
import app.wakesteps.core.Format
import app.wakesteps.data.AppSettings
import app.wakesteps.data.CalorieMode
import app.wakesteps.data.Snapshot
import app.wakesteps.data.WidgetStyle
import app.wakesteps.ui.MainActivity

/**
 * Home-screen and lock-screen widget. The goal ring carries the progress; inside it sits the
 * footprints icon over the step count, and nothing else. One layout per shape:
 *  - compact (2×1): small ring, step count
 *  - medium (2×2): ring with icon + count, calories and distance underneath
 *  - wide (4×1): ring, step count, calories and distance on the right
 *  - split (4×2): ring on the left, calories and distance on the right
 *  - tower (4×3, the lock-screen size): big ring on top, calories and distance below
 * Everything is sized from the widget's exact dimensions.
 *
 * The widget refreshes itself, so it carries no refresh button; tapping it opens the app.
 */
class StepsWidget : GlanceAppWidget() {

    // Exact: lay out for the real on-screen size so the ring and type scale with the widget.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.app
        val initialSnapshot = app.prefs.currentSnapshot()
        val initialSettings = app.prefs.currentSettings()
        provideContent {
            val snapshot by app.prefs.snapshot.collectAsState(initialSnapshot)
            val settings by app.prefs.settings.collectAsState(initialSettings)
            CompositionLocalProvider(LocalPalette provides WidgetPalette(context, settings.widgetStyle)) {
                WidgetBody(snapshot, settings)
            }
        }
    }
}

/** Narrower than this is a 2-column widget. */
private val WIDE_WIDTH = 240.dp
/** Shorter than this is a 1-row widget. */
private val TALL_HEIGHT = 110.dp
/** At least this tall (4×3, the lock-screen size) stacks the stats under a big ring. */
private val TOWER_HEIGHT = 240.dp

/**
 * A deep card in a dark tone of the wallpaper colour (Android 12+, teal before that), the same
 * in light and dark mode, with the ring and icons in a bright tone of it until the goal is met,
 * then gold. See widget_* in values/colors.xml and values-v31. Resolved on every render, so a new
 * wallpaper shows up on the next refresh.
 */
private class WidgetPalette(private val context: Context, val style: WidgetStyle) {
    private fun of(@ColorRes id: Int) = ColorProvider(Color(context.getColor(id)))
    val background = of(R.color.widget_bg)
    val track = of(R.color.widget_track)
    val text = of(R.color.widget_text)
    val muted = of(R.color.widget_muted)
    val error = of(R.color.widget_error)
    /** Footprints, ring glow, stat icons and the ring itself before the goal. */
    val accent = of(R.color.widget_accent)
    val goal = of(R.color.widget_gold)
}

private val LocalPalette = staticCompositionLocalOf<WidgetPalette> { error("No palette") }

/** Shorthand for the current palette inside widget composables. */
private val Palette: WidgetPalette
    @Composable get() = LocalPalette.current

/** Google Sans on Pixels; any other device quietly falls back to its default sans-serif. */
private val Display = FontFamily("google-sans")
private val Body = FontFamily("google-sans-text")

@Composable
private fun WidgetBody(snapshot: Snapshot?, settings: AppSettings) {
    val size = LocalSize.current
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(
                ImageProvider(R.drawable.widget_bg),
                contentScale = ContentScale.FillBounds,
                colorFilter = ColorFilter.tint(Palette.background),
            )
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        // Soft light from the top-left so the card has depth instead of a flat fill.
        Image(
            provider = ImageProvider(R.drawable.widget_sheen),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = GlanceModifier.fillMaxSize(),
        )
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Content(snapshot, settings, size)
        }
    }
}

@Composable
private fun Content(snapshot: Snapshot?, settings: AppSettings, size: DpSize) {
    val context = LocalContext.current
    if (snapshot == null) {
        Text(
            context.getString(R.string.widget_setup),
            style = label(14.sp),
            modifier = GlanceModifier.padding(16.dp),
        )
        return
    }
    val m = WidgetModel(context, snapshot, settings, Palette)
    val wide = size.width >= WIDE_WIDTH
    val tall = size.height >= TALL_HEIGHT
    when {
        wide && size.height >= TOWER_HEIGHT -> TowerLayout(m, size)
        wide && tall -> SplitLayout(m, size)
        wide -> WideLayout(m, size)
        tall -> MediumLayout(m, size)
        else -> CompactLayout(m, size)
    }
}

/** Pre-formatted values so the layouts stay purely declarative. */
private class WidgetModel(context: Context, s: Snapshot, settings: AppSettings, palette: WidgetPalette) {
    private val pct = (s.totals.steps * 100 / settings.goal.coerceAtLeast(1)).toInt()
    val ringPercent = pct.coerceIn(0, 100)
    val goalReached = pct >= 100
    /** Past the goal, how far round the current extra lap is (1..100), drawn over the full ring. */
    val lapPercent = if (pct > 100) (pct - 1) % 100 + 1 else 0
    val steps = Format.number(s.totals.steps)
    val error = s.error
    val calories: Stat? = s.totals.calories?.let {
        Stat(
            R.drawable.ic_fire, palette.accent, Format.number(Math.round(it)), unit = "cal",
            label = context.getString(
                if (settings.calorieMode == CalorieMode.ACTIVE) R.string.widget_label_active_calories
                else R.string.widget_label_calories
            ),
            unitWithLabel = false, // "1,284 / calories", not "1,284 cal / calories"
        )
    }
    val distance: Stat? = s.totals.distanceMeters?.let {
        val text = Format.distance(it, settings.distanceUnit) // "3.8 mi"
        Stat(
            R.drawable.ic_distance, palette.accent, text.substringBeforeLast(' '), unit = text.substringAfterLast(' '),
            label = context.getString(R.string.widget_label_distance),
            unitWithLabel = true,
        )
    }
    val stats = listOfNotNull(calories, distance)
}

/**
 * One secondary number. Compact layouts show "value unit" ("1,284 cal"); the roomy ones show the
 * value over a word ("1,284" / "calories"), keeping the unit only where the word doesn't say it.
 */
private class Stat(
    @DrawableRes val icon: Int,
    val tint: ColorProvider,
    val value: String,
    val unit: String,
    val label: String,
    val unitWithLabel: Boolean,
)

// ---------------------------------------------------------------- layouts

@Composable
private fun CompactLayout(m: WidgetModel, size: DpSize) {
    Row(
        modifier = GlanceModifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ring(m, (size.height - 16.dp).coerceIn(32.dp, 46.dp)) { StepsIcon(m, 16.dp) }
        Spacer(GlanceModifier.width(12.dp))
        Column {
            Text(m.steps, style = number(24.sp), maxLines = 1)
            m.error?.let { Text(it, style = TextStyle(color = Palette.error, fontSize = 11.sp, fontFamily = Body), maxLines = 1) }
        }
    }
}

@Composable
private fun MediumLayout(m: WidgetModel, size: DpSize) {
    val ring = minOf(size.width - 28.dp, size.height - 52.dp).coerceIn(72.dp, 140.dp)
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ring(m, ring) { RingCenter(m, ring) }
        Spacer(GlanceModifier.height(8.dp))
        if (m.error != null) ErrorText(m.error, 1) else InlineStats(m)
    }
}

@Composable
private fun WideLayout(m: WidgetModel, size: DpSize) {
    Row(
        modifier = GlanceModifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ring(m, (size.height - 22.dp).coerceIn(40.dp, 64.dp)) { StepsIcon(m, 22.dp) }
        Spacer(GlanceModifier.width(14.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(m.steps, style = number(32.sp), maxLines = 1)
            m.error?.let { ErrorText(it, 1) }
        }
        Column(horizontalAlignment = Alignment.End) {
            m.stats.forEachIndexed { i, stat ->
                if (i > 0) Spacer(GlanceModifier.height(4.dp))
                InlineStat(stat)
            }
        }
    }
}

/** 4×2: ring with the count on the left, a column of stats on the right. */
@Composable
private fun SplitLayout(m: WidgetModel, size: DpSize) {
    val half = (size.width - 40.dp - 16.dp) / 2
    val ring = minOf(half, size.height - 36.dp).coerceIn(80.dp, 168.dp)
    val value = (ring.value * 0.16f).coerceIn(17f, 24f).sp
    Row(
        modifier = GlanceModifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
            Ring(m, ring) { RingCenter(m, ring) }
        }
        Spacer(GlanceModifier.width(16.dp))
        Column(modifier = GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
            if (m.error != null) {
                ErrorText(m.error, 3)
            } else {
                m.stats.forEachIndexed { i, stat ->
                    if (i > 0) Spacer(GlanceModifier.height(14.dp))
                    LabeledStat(stat, value)
                }
            }
        }
    }
}

/** 4×3 and taller: a big centered ring with the stats in columns underneath. */
@Composable
private fun TowerLayout(m: WidgetModel, size: DpSize) {
    val ring = minOf(size.width - 40.dp, size.height - 124.dp).coerceIn(110.dp, 190.dp)
    val value = (size.width.value / 16f).coerceIn(17f, 23f).sp
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ring(m, ring) { RingCenter(m, ring) }
        Spacer(GlanceModifier.height(18.dp))
        if (m.error != null) {
            ErrorText(m.error, 2)
        } else {
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                m.stats.forEach { StatColumn(it, value) }
            }
        }
    }
}

// ---------------------------------------------------------------- pieces

/**
 * Goal ring: a soft glow and a track in the card's tone, the arc on top, [content] inside. The arc
 * is the wallpaper's bright tone (even or fading in, by style) until the goal, then a gold ring;
 * past the goal the next gold lap runs over it, casting a shadow ahead of its tip.
 */
@Composable
private fun Ring(m: WidgetModel, size: Dp, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier = GlanceModifier.size(size), contentAlignment = Alignment.Center) {
        RingLayer(R.drawable.ring_glow, ColorFilter.tint(if (m.goalReached) Palette.goal else Palette.accent))
        RingLayer(R.drawable.ring_track, ColorFilter.tint(Palette.track))
        when {
            // Gold drawables carry their own colours.
            m.goalReached -> RingLayer(R.drawable.ring_goal, null)
            m.ringPercent > 0 -> {
                val arcs = if (Palette.style == WidgetStyle.FADE) FADE_ARCS else RING_ARCS
                RingLayer(arcs[m.ringPercent], ColorFilter.tint(Palette.accent))
            }
        }
        if (m.lapPercent > 0) {
            RingLayer(LAP_SHADOWS[m.lapPercent], null)
            RingLayer(LAP_ARCS[m.lapPercent], null)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}

@Composable
private fun RingLayer(@DrawableRes drawable: Int, colorFilter: ColorFilter?) {
    Image(
        provider = ImageProvider(drawable),
        contentDescription = null,
        colorFilter = colorFilter,
        modifier = GlanceModifier.fillMaxSize(),
    )
}

/**
 * Footprints over the count, centred as one unit: the gap under the number balances the icon
 * above it, so the pair sits in the optical middle of the ring.
 */
@Composable
private fun RingCenter(m: WidgetModel, ring: Dp) {
    val icon = (ring.value * 0.15f).coerceIn(18f, 28f).dp
    StepsIcon(m, icon)
    Spacer(GlanceModifier.height(3.dp))
    Text(m.steps, style = number(numberSize(ring, m.steps)), maxLines = 1)
    Spacer(GlanceModifier.height(icon * 0.55f))
}

@Composable
private fun StepsIcon(m: WidgetModel, size: Dp) {
    Image(
        provider = ImageProvider(R.drawable.ic_steps),
        contentDescription = null,
        colorFilter = ColorFilter.tint(if (m.goalReached) Palette.goal else Palette.accent),
        modifier = GlanceModifier.size(size),
    )
}

/** "🔥 1,284 cal   📍 3.8 mi" */
@Composable
private fun InlineStats(m: WidgetModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        m.stats.forEachIndexed { i, stat ->
            if (i > 0) Spacer(GlanceModifier.width(14.dp))
            InlineStat(stat)
        }
    }
}

/** Quiet one-liner: the step count stays the star, so these sit back in soft gray. */
@Composable
private fun InlineStat(stat: Stat) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatIcon(stat, 14.dp)
        Spacer(GlanceModifier.width(5.dp))
        Text("${stat.value} ${stat.unit}", style = label(12.sp), maxLines = 1)
    }
}

/** Icon beside a big value, with the word underneath (4×2). */
@Composable
private fun LabeledStat(stat: Stat, valueSize: TextUnit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatIcon(stat, 18.dp)
        Spacer(GlanceModifier.width(10.dp))
        Column {
            ValueWithUnit(stat, valueSize)
            Text(stat.label, style = label(11.sp), maxLines = 1)
        }
    }
}

@Composable
private fun ValueWithUnit(stat: Stat, valueSize: TextUnit) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(stat.value, style = number(valueSize), maxLines = 1)
        if (stat.unitWithLabel) {
            Spacer(GlanceModifier.width(4.dp))
            Text(stat.unit, style = label(12.sp), maxLines = 1)
        }
    }
}

/** Icon over value over word, centred: one column of the 4×3 stats row. */
@Composable
private fun RowScope.StatColumn(stat: Stat, valueSize: TextUnit) {
    Column(modifier = GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
        StatIcon(stat, 18.dp)
        Spacer(GlanceModifier.height(4.dp))
        ValueWithUnit(stat, valueSize)
        Text(stat.label, style = label(11.sp), maxLines = 1)
    }
}

@Composable
private fun StatIcon(stat: Stat, size: Dp) {
    Image(
        provider = ImageProvider(stat.icon),
        contentDescription = null,
        colorFilter = ColorFilter.tint(stat.tint),
        modifier = GlanceModifier.size(size),
    )
}

@Composable
private fun ErrorText(text: String, lines: Int) {
    Text(text, style = TextStyle(color = Palette.error, fontSize = 12.sp, fontFamily = Body), maxLines = lines)
}

/** Step count sized to sit comfortably inside a ring of the given diameter. */
private fun numberSize(ring: Dp, steps: String): TextUnit {
    val perChar = if (steps.length > 5) 0.034f else 0.04f
    return (ring.value * perChar * 5.5f).coerceIn(16f, 34f).sp
}

/** Google Sans at its regular weight: light and roomy, the way the big numbers read best. */
@Composable
private fun number(size: TextUnit) =
    TextStyle(color = Palette.text, fontSize = size, fontFamily = Display)

@Composable
private fun label(size: TextUnit) = TextStyle(color = Palette.muted, fontSize = size, fontFamily = Body)
