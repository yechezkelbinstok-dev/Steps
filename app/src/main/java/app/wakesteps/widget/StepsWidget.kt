package app.wakesteps.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
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
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.wakesteps.R
import app.wakesteps.app
import app.wakesteps.core.Format
import app.wakesteps.core.StartReason
import app.wakesteps.data.Snapshot
import app.wakesteps.ui.MainActivity
import app.wakesteps.ui.RefreshActivity

class StepsWidget : GlanceAppWidget() {

    companion object {
        private val SMALL = DpSize(100.dp, 40.dp)
        private val MEDIUM = DpSize(150.dp, 100.dp)
        private val WIDE = DpSize(250.dp, 100.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.app
        val initialSnapshot = app.prefs.currentSnapshot()
        val initialSettings = app.prefs.currentSettings()
        // Without background read access Health Connect refuses reads from a widget tap, so in
        // that case the refresh button briefly opens an invisible activity to do the read.
        val canReadInBackground = runCatching {
            app.health.isAvailable && app.health.hasBackgroundPermission()
        }.getOrDefault(false)
        val refresh: Action = if (canReadInBackground) {
            actionRunCallback<RefreshCallback>()
        } else {
            actionStartActivity<RefreshActivity>()
        }

        provideContent {
            val snapshot by app.prefs.snapshot.collectAsState(initialSnapshot)
            val settings by app.prefs.settings.collectAsState(initialSettings)
            GlanceTheme {
                WidgetBody(snapshot, settings.goal, refresh)
            }
        }
    }
}

@Composable
private fun WidgetBody(snapshot: Snapshot?, goal: Int, refresh: Action) {
    val size = LocalSize.current
    val context = LocalContext.current
    val compact = size.height < 90.dp
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(24.dp)
            .padding(horizontal = 14.dp, vertical = if (compact) 6.dp else 12.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (snapshot == null) {
            Text(
                text = context.getString(R.string.widget_setup),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
            )
            return@Column
        }
        if (compact) {
            CompactBody(snapshot, refresh)
        } else {
            FullBody(snapshot, goal, refresh, wide = size.width >= 250.dp)
        }
    }
}

@Composable
private fun CompactBody(snapshot: Snapshot, refresh: Action) {
    val context = LocalContext.current
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = Format.number(snapshot.steps),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            Text(
                text = sinceLabel(context, snapshot),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                maxLines = 1,
            )
        }
        RefreshButton(refresh)
    }
}

@Composable
private fun FullBody(snapshot: Snapshot, goal: Int, refresh: Action, wide: Boolean) {
    val context = LocalContext.current
    val progress = if (goal > 0) (snapshot.steps.toFloat() / goal).coerceIn(0f, 1f) else 0f

    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(
            provider = ImageProvider(R.drawable.ic_steps),
            contentDescription = null,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
            modifier = GlanceModifier.size(16.dp),
        )
        Spacer(GlanceModifier.width(6.dp))
        Text(
            text = sinceLabel(context, snapshot),
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        RefreshButton(refresh)
    }
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = Format.number(snapshot.steps),
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = if (wide) 40.sp else 34.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
        if (wide) {
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = context.getString(R.string.widget_of_goal, Format.number(goal.toLong())),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp),
                modifier = GlanceModifier.padding(bottom = 8.dp),
            )
        }
    }
    LinearProgressIndicator(
        progress = progress,
        modifier = GlanceModifier.fillMaxWidth().height(8.dp).cornerRadius(4.dp),
        color = GlanceTheme.colors.primary,
        backgroundColor = GlanceTheme.colors.secondaryContainer,
    )
    Spacer(GlanceModifier.height(6.dp))
    Text(
        text = footer(context, snapshot, progress),
        style = TextStyle(
            color = if (snapshot.error != null) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant,
            fontSize = 11.sp,
        ),
        maxLines = 1,
    )
}

@Composable
private fun RefreshButton(refresh: Action) {
    Image(
        provider = ImageProvider(R.drawable.ic_refresh),
        contentDescription = LocalContext.current.getString(R.string.refresh),
        colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
        modifier = GlanceModifier.size(32.dp).padding(6.dp).cornerRadius(16.dp).clickable(refresh),
    )
}

private fun sinceLabel(context: Context, s: Snapshot): String = when {
    s.asleep -> context.getString(R.string.asleep)
    s.startReason == StartReason.FALLBACK -> context.getString(R.string.since_reset, Format.time(s.dayStart))
    else -> context.getString(R.string.since_time, Format.time(s.dayStart))
}

private fun footer(context: Context, s: Snapshot, progress: Float): String {
    if (s.error != null) return s.error
    val pct = "${(progress * 100).toInt()}%"
    val synced = s.latestDataAt?.let { context.getString(R.string.synced_at, Format.time(it)) }
        ?: context.getString(R.string.updated_at, Format.time(s.updatedAt))
    return "$pct · $synced"
}
