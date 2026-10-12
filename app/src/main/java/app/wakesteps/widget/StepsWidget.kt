package app.wakesteps.widget

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
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
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.wakesteps.R
import app.wakesteps.app
import app.wakesteps.core.Format
import app.wakesteps.data.AppSettings
import app.wakesteps.data.Snapshot
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
            WidgetBody(snapshot, settings)
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
 * The widget's signature look: smoked glass that lets the wallpaper through, white type, an
 * aqua-to-blue goal ring (gold once the goal is met) and a colour per stat. Fixed rather than
 * wallpaper-derived, so it looks the same, and vivid, on any home or lock screen.
 */
private object Palette {
    // Card and ring track are translucent, so they're baked into widget_glass / ring_track.
    val text = ColorProvider(Color(0xFFFFFFFF))
    val muted = ColorProvider(Color(0xB3FFFFFF))
    val aqua = ColorProvider(Color(0xFF2BE7C8))
    val gold = ColorProvider(Color(0xFFFFC94D))
    val calories = ColorProvider(Color(0xFFFF7A59))
    val distance = ColorProvider(Color(0xFF5CB8FF))
    val error = ColorProvider(Color(0xFFFFB4AB))
}

@Composable
private fun WidgetBody(snapshot: Snapshot?, settings: AppSettings) {
    val size = LocalSize.current
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(ImageProvider(R.drawable.widget_glass), contentScale = ContentScale.FillBounds)
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
    val m = WidgetModel(context, snapshot, settings)
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
private class WidgetModel(context: Context, s: Snapshot, settings: AppSettings) {
    private val pct = (s.totals.steps * 100 / settings.goal.coerceAtLeast(1)).toInt()
    val ringPercent = pct.coerceIn(0, 100)
    val goalReached = pct >= 100
    val steps = Format.number(s.totals.steps)
    val error = s.error
    val calories: Stat? = s.totals.calories?.let {
        Stat(R.drawable.ic_fire, Palette.calories, Format.number(Math.round(it)), "cal")
    }
    val distance: Stat? = s.totals.distanceMeters?.let {
        val text = Format.distance(it, settings.distanceUnit) // "3.8 mi"
        Stat(R.drawable.ic_distance, Palette.distance, text.substringBeforeLast(' '), text.substringAfterLast(' '))
    }
    val stats = listOfNotNull(calories, distance)
}

/** One secondary number: icon, value and unit ("cal", "mi"). */
private class Stat(@DrawableRes val icon: Int, val tint: ColorProvider, val value: String, val unit: String)

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
            m.error?.let { Text(it, style = TextStyle(color = Palette.error, fontSize = 11.sp), maxLines = 1) }
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
                    if (i > 0) Spacer(GlanceModifier.height(16.dp))
                    LabeledStat(stat, value)
                }
            }
        }
    }
}

/** 4×3 and taller: a big centered ring with the stats in columns underneath. */
@Composable
private fun TowerLayout(m: WidgetModel, size: DpSize) {
    val ring = minOf(size.width - 40.dp, size.height - 104.dp).coerceIn(110.dp, 190.dp)
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

/** Goal ring: a faint track with the gradient arc on top, [content] centered inside. */
@Composable
private fun Ring(m: WidgetModel, size: Dp, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier = GlanceModifier.size(size), contentAlignment = Alignment.Center) {
        Image(
            provider = ImageProvider(R.drawable.ring_track),
            contentDescription = null,
            modifier = GlanceModifier.fillMaxSize(),
        )
        val arc = when {
            m.goalReached -> R.drawable.ring_goal
            m.ringPercent > 0 -> RING_ARCS[m.ringPercent]
            else -> null
        }
        if (arc != null) {
            Image(provider = ImageProvider(arc), contentDescription = null, modifier = GlanceModifier.fillMaxSize())
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}

/**
 * Footprints over the count, centred as one unit: the gap under the number balances the icon
 * above it, so the pair sits in the optical middle of the ring.
 */
@Composable
private fun RingCenter(m: WidgetModel, ring: Dp) {
    val icon = (ring.value * 0.13f).coerceIn(16f, 24f).dp
    StepsIcon(m, icon)
    Spacer(GlanceModifier.height(3.dp))
    Text(m.steps, style = number(numberSize(ring, m.steps) * 1.08f), maxLines = 1)
    Spacer(GlanceModifier.height(icon * 0.55f))
}

@Composable
private fun StepsIcon(m: WidgetModel, size: Dp) {
    Image(
        provider = ImageProvider(R.drawable.ic_steps),
        contentDescription = null,
        colorFilter = ColorFilter.tint(if (m.goalReached) Palette.gold else Palette.aqua),
        modifier = GlanceModifier.size(size),
    )
}

/** "🔥 1,284 cal   📍 3.8 mi": value in white, unit quiet. */
@Composable
private fun InlineStats(m: WidgetModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        m.stats.forEachIndexed { i, stat ->
            if (i > 0) Spacer(GlanceModifier.width(14.dp))
            InlineStat(stat)
        }
    }
}

@Composable
private fun InlineStat(stat: Stat) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatIcon(stat, 14.dp)
        Spacer(GlanceModifier.width(4.dp))
        Text(stat.value, style = number(13.sp), maxLines = 1)
        Spacer(GlanceModifier.width(3.dp))
        Text(stat.unit, style = label(11.sp), maxLines = 1)
    }
}

/** Larger icon + value + unit (4×2). */
@Composable
private fun LabeledStat(stat: Stat, valueSize: TextUnit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatIcon(stat, 18.dp)
        Spacer(GlanceModifier.width(10.dp))
        Text(stat.value, style = number(valueSize), maxLines = 1)
        Spacer(GlanceModifier.width(4.dp))
        Text(stat.unit, style = label(13.sp), maxLines = 1)
    }
}

/** Icon over value + unit, centred: one column of the 4×3 stats row. */
@Composable
private fun RowScope.StatColumn(stat: Stat, valueSize: TextUnit) {
    Column(modifier = GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
        StatIcon(stat, 18.dp)
        Spacer(GlanceModifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(stat.value, style = number(valueSize), maxLines = 1)
            Spacer(GlanceModifier.width(4.dp))
            Text(stat.unit, style = label(12.sp), maxLines = 1)
        }
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
    Text(text, style = TextStyle(color = Palette.error, fontSize = 12.sp), maxLines = lines)
}

/** Step count sized to sit comfortably inside a ring of the given diameter. */
private fun numberSize(ring: Dp, steps: String): TextUnit {
    val perChar = if (steps.length > 5) 0.034f else 0.04f
    return (ring.value * perChar * 5.5f).coerceIn(16f, 34f).sp
}

/** Numbers: the device's own sans, so it feels native on Pixel and Samsung alike. */
private fun number(size: TextUnit) = TextStyle(color = Palette.text, fontSize = size)

private fun label(size: TextUnit) = TextStyle(color = Palette.muted, fontSize = size)
