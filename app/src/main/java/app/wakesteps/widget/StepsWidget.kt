package app.wakesteps.widget

import android.content.Context
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
import androidx.glance.GlanceTheme
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
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.wakesteps.R
import app.wakesteps.app
import app.wakesteps.core.Format
import app.wakesteps.core.StartReason
import app.wakesteps.data.AppSettings
import app.wakesteps.data.CalorieMode
import app.wakesteps.data.Snapshot
import app.wakesteps.ui.MainActivity

/**
 * Home-screen and lock-screen widget. One layout per shape:
 *  - compact (2×1): mini ring, steps, day start
 *  - medium (2×2): ring with steps inside, calories and distance under it
 *  - wide (4×1): ring, steps, calories and distance
 *  - split (4×2): ring with steps on the left, calories / distance / wake time on the right
 *  - tower (4×3, the lock-screen size): big ring on top, the three metrics in a row below
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
            // Material You colors on Android 12+, the app's own teal palette before that.
            if (Build.VERSION.SDK_INT >= 31) {
                GlanceTheme { WidgetBody(snapshot, settings) }
            } else {
                GlanceTheme(colors = BrandColors) { WidgetBody(snapshot, settings) }
            }
        }
    }
}

/** Narrower than this is a 2-column widget. */
private val WIDE_WIDTH = 240.dp
/** Shorter than this is a 1-row widget. */
private val TALL_HEIGHT = 110.dp
/** At least this tall (4×3, the lock-screen size) stacks the metrics under a big ring. */
private val TOWER_HEIGHT = 240.dp

private val BrandColors = ColorProviders(
    light = lightColorScheme(
        primary = Color(0xFF00696E),
        secondaryContainer = Color(0xFFCCE8E9),
        onSecondaryContainer = Color(0xFF051F21),
        tertiary = Color(0xFF7A5900),
        surface = Color(0xFFF4FBFA),
        onSurface = Color(0xFF161D1D),
        onSurfaceVariant = Color(0xFF3F4949),
    ),
    dark = darkColorScheme(
        primary = Color(0xFF6FD6DC),
        secondaryContainer = Color(0xFF324B4C),
        onSecondaryContainer = Color(0xFFCCE8E9),
        tertiary = Color(0xFFF5BE48),
        surface = Color(0xFF0E1415),
        onSurface = Color(0xFFDDE4E3),
        onSurfaceVariant = Color(0xFFBEC8C8),
    ),
)

/** Google Sans on Pixels; any other device quietly falls back to its default sans-serif. */
private val Display = FontFamily("google-sans")
private val Body = FontFamily("google-sans-text")

@Composable
private fun WidgetBody(snapshot: Snapshot?, settings: AppSettings) {
    val size = LocalSize.current
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(
                ImageProvider(R.drawable.widget_bg),
                contentScale = ContentScale.FillBounds,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.widgetBackground),
            )
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.Center,
    ) {
        if (snapshot == null) {
            Text(
                context.getString(R.string.widget_setup),
                style = body(14.sp, GlanceTheme.colors.onSurfaceVariant),
                modifier = GlanceModifier.padding(16.dp),
            )
            return@Box
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
}

/** Pre-formatted values so the layouts stay purely declarative. */
private class WidgetModel(context: Context, s: Snapshot, settings: AppSettings) {
    private val pct = (s.totals.steps * 100 / settings.goal.coerceAtLeast(1)).toInt()
    val ringPercent = pct.coerceIn(0, 100)
    val goalReached = pct >= 100
    val steps = Format.number(s.totals.steps)
    val calories = s.totals.calories?.let { Format.calories(it) }
    val distance = s.totals.distanceMeters?.let { Format.distance(it, settings.distanceUnit) }
    val error = s.error
    val since = when {
        s.asleep -> context.getString(R.string.asleep)
        s.startReason == StartReason.FALLBACK -> context.getString(R.string.since_reset, Format.time(s.dayStart))
        else -> context.getString(R.string.since_time, Format.time(s.dayStart))
    }
    /** Short caption under the number inside the big ring. */
    val ringCaption = if (pct >= 100) context.getString(R.string.widget_goal_met)
        else context.getString(R.string.of_goal, Format.number(settings.goal.toLong()))
    val caloriesValue = s.totals.calories?.let { Format.number(Math.round(it)) }
    val calorieLabel = context.getString(
        if (settings.calorieMode == CalorieMode.ACTIVE) R.string.widget_label_active_calories
        else R.string.widget_label_calories
    )
    val distanceLabel = context.getString(R.string.widget_label_distance)
    val wakeValue = Format.time(s.dayStart)
    val wakeLabel = context.getString(
        when {
            s.asleep -> R.string.widget_label_day_started
            s.startReason == StartReason.FALLBACK -> R.string.widget_label_day_reset
            else -> R.string.widget_label_woke_up
        }
    )
    val stepsSince = if (s.asleep || s.startReason == StartReason.FALLBACK) since
        else context.getString(R.string.widget_steps_since, Format.time(s.dayStart))
}

// ---------------------------------------------------------------- layouts

@Composable
private fun CompactLayout(m: WidgetModel, size: DpSize) {
    Row(
        modifier = GlanceModifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ring(m, (size.height - 16.dp).coerceIn(32.dp, 46.dp)) { StepsIcon(16.dp) }
        Spacer(GlanceModifier.width(12.dp))
        Column {
            Text(m.steps, style = display(22.sp), maxLines = 1)
            Subtitle(m, 12.sp)
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
        Ring(m, ring) {
            Text(m.steps, style = display(numberSize(ring, m.steps)), maxLines = 1)
            Text(
                LocalContext.current.getString(R.string.widget_steps),
                style = body(11.sp, GlanceTheme.colors.onSurfaceVariant),
            )
        }
        Spacer(GlanceModifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            m.calories?.let { InlineStat(R.drawable.ic_fire, it) }
            if (m.calories != null && m.distance != null) Spacer(GlanceModifier.width(10.dp))
            m.distance?.let { InlineStat(R.drawable.ic_distance, it) }
            if (m.calories == null && m.distance == null) Subtitle(m, 12.sp)
        }
    }
}

@Composable
private fun WideLayout(m: WidgetModel, size: DpSize) {
    Row(
        modifier = GlanceModifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ring(m, (size.height - 22.dp).coerceIn(40.dp, 64.dp)) { StepsIcon(22.dp) }
        Spacer(GlanceModifier.width(14.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(m.steps, style = display(30.sp), maxLines = 1)
            Subtitle(m, 12.sp, long = true)
        }
        Column(horizontalAlignment = Alignment.End) {
            m.calories?.let { InlineStat(R.drawable.ic_fire, it) }
            if (m.calories != null && m.distance != null) Spacer(GlanceModifier.height(4.dp))
            m.distance?.let { InlineStat(R.drawable.ic_distance, it) }
        }
    }
}

/**
 * The 4×2 / 4×3 layout: the goal ring with the step count on the left, and a column of
 * metrics on the right. The two halves share the width so the card reads as balanced.
 */
@Composable
private fun SplitLayout(m: WidgetModel, size: DpSize) {
    val half = (size.width - 40.dp - 16.dp) / 2
    val ring = minOf(half, size.height - 36.dp).coerceIn(80.dp, 168.dp)
    val value = (ring.value * 0.15f).coerceIn(16f, 22f).sp
    val gap = ((size.height.value - 190f) / 8f).coerceIn(6f, 16f).dp
    Row(
        modifier = GlanceModifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
            Ring(m, ring) {
                Text(m.steps, style = display(numberSize(ring, m.steps)), maxLines = 1)
                Text(m.ringCaption, style = body(11.sp, GlanceTheme.colors.onSurfaceVariant), maxLines = 1)
            }
        }
        Spacer(GlanceModifier.width(16.dp))
        Column(modifier = GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
            if (m.error != null) {
                Text(m.error, style = body(12.sp, GlanceTheme.colors.error), maxLines = 3)
                return@Column
            }
            m.caloriesValue?.let {
                Metric(R.drawable.ic_fire, it, m.calorieLabel, value)
                Spacer(GlanceModifier.height(gap))
            }
            m.distance?.let {
                Metric(R.drawable.ic_distance, it, m.distanceLabel, value)
                Spacer(GlanceModifier.height(gap))
            }
            Metric(R.drawable.ic_sunrise, m.wakeValue, m.wakeLabel, value)
        }
    }
}

/** 4×3 and taller: a big centered ring with the three metrics in columns underneath. */
@Composable
private fun TowerLayout(m: WidgetModel, size: DpSize) {
    val ring = minOf(size.width - 40.dp, size.height - 124.dp).coerceIn(110.dp, 190.dp)
    val value = (size.width.value / 18f).coerceIn(16f, 21f).sp
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ring(m, ring) {
            Text(m.steps, style = display(numberSize(ring, m.steps)), maxLines = 1)
            Text(m.ringCaption, style = body(12.sp, GlanceTheme.colors.onSurfaceVariant), maxLines = 1)
        }
        Spacer(GlanceModifier.height(18.dp))
        if (m.error != null) {
            Text(m.error, style = body(12.sp, GlanceTheme.colors.error), maxLines = 2)
            return@Column
        }
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            m.caloriesValue?.let {
                MetricColumn(R.drawable.ic_fire, it, m.calorieLabel, value, GlanceModifier.defaultWeight())
            }
            m.distance?.let {
                MetricColumn(R.drawable.ic_distance, it, m.distanceLabel, value, GlanceModifier.defaultWeight())
            }
            MetricColumn(R.drawable.ic_sunrise, m.wakeValue, m.wakeLabel, value, GlanceModifier.defaultWeight())
        }
    }
}

/** Icon over value over label, centered: one column of the tower layout's metric row. */
@Composable
private fun MetricColumn(
    @DrawableRes icon: Int,
    value: String,
    label: String,
    valueSize: TextUnit,
    modifier: GlanceModifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
            modifier = GlanceModifier.size(18.dp),
        )
        Spacer(GlanceModifier.height(4.dp))
        Text(value, style = display(valueSize), maxLines = 1)
        Text(label, style = body(11.sp, GlanceTheme.colors.onSurfaceVariant), maxLines = 1)
    }
}

/** Step count sized to sit comfortably inside a ring of the given diameter. */
private fun numberSize(ring: Dp, steps: String): TextUnit {
    val perChar = if (steps.length > 5) 0.034f else 0.04f
    return (ring.value * perChar * 5.5f).coerceIn(16f, 34f).sp
}

/** Icon beside a value with a quiet label underneath. */
@Composable
private fun Metric(@DrawableRes icon: Int, value: String, label: String, valueSize: TextUnit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
            modifier = GlanceModifier.size(18.dp),
        )
        Spacer(GlanceModifier.width(10.dp))
        Column {
            Text(value, style = display(valueSize), maxLines = 1)
            Text(label, style = body(11.sp, GlanceTheme.colors.onSurfaceVariant), maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------- pieces

/** Goal ring: a tinted track with a tinted arc on top, [content] centered inside. */
@Composable
private fun Ring(m: WidgetModel, size: Dp, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier = GlanceModifier.size(size), contentAlignment = Alignment.Center) {
        Image(
            provider = ImageProvider(R.drawable.ring_track),
            contentDescription = null,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.primaryContainer),
            modifier = GlanceModifier.fillMaxSize(),
        )
        if (m.ringPercent > 0) {
            Image(
                provider = ImageProvider(RING_ARCS[m.ringPercent]),
                contentDescription = null,
                colorFilter = ColorFilter.tint(accent(m)),
                modifier = GlanceModifier.fillMaxSize(),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}

@Composable
private fun StepsIcon(size: Dp) {
    Image(
        provider = ImageProvider(R.drawable.ic_steps),
        contentDescription = null,
        colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
        modifier = GlanceModifier.size(size),
    )
}

@Composable
private fun Subtitle(m: WidgetModel, size: TextUnit, long: Boolean = false) {
    Text(
        m.error ?: if (long) m.stepsSince else m.since,
        style = body(size, if (m.error != null) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant),
        maxLines = 1,
    )
}

/** Small icon + value for tight layouts. */
@Composable
private fun InlineStat(@DrawableRes icon: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
            modifier = GlanceModifier.size(14.dp),
        )
        Spacer(GlanceModifier.width(4.dp))
        Text(text, style = body(12.sp, GlanceTheme.colors.onSurfaceVariant), maxLines = 1)
    }
}

@Composable
private fun accent(m: WidgetModel): ColorProvider =
    if (m.goalReached) GlanceTheme.colors.tertiary else GlanceTheme.colors.primary

@Composable
private fun display(size: TextUnit) = TextStyle(
    color = GlanceTheme.colors.onSurface,
    fontSize = size,
    fontWeight = FontWeight.Medium,
    fontFamily = Display,
)

private fun body(size: TextUnit, color: ColorProvider) =
    TextStyle(color = color, fontSize = size, fontFamily = Body)
