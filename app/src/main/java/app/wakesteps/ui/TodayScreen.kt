package app.wakesteps.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.wakesteps.R
import app.wakesteps.core.DayWindow
import app.wakesteps.core.Format
import app.wakesteps.data.AppSettings
import app.wakesteps.data.CalorieMode
import app.wakesteps.data.DayTotals
import app.wakesteps.ui.theme.AppType
import app.wakesteps.ui.theme.Palette
import app.wakesteps.widget.StepsWidgetReceiver
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun TodayScreen(
    state: UiState,
    settings: AppSettings,
    actions: Actions,
    onAdjust: (DayWindow) -> Unit,
    onShowSyncLog: () -> Unit,
    bottomPadding: Dp,
    chartSelection: Int? = null,
) {
    val today = state.today
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Header(today?.now ?: Instant.now(), today?.latestDataAt, onShowSyncLog, Modifier.entrance(0))
        AccessPrompts(state.access, actions)
        state.error?.let { ErrorNote(it) }
        if (today != null) {
            Hero(today.totals.steps, settings.goal, today.window, today.now, onAdjust, Modifier.entrance(1))
            Metrics(today.totals, settings, Modifier.entrance(2))
            if (today.hourly.isNotEmpty()) {
                HourlyPanel(today.hourly, settings.goal, ongoing = today.window.isOngoing, Modifier.entrance(3), chartSelection)
            }
            AddWidgetPrompt()
        }
    }
}

/** Today's date, the title, and how fresh the tracker data is (tap for the sync log). */
@Composable
private fun Header(now: Instant, latest: Instant?, onShowSyncLog: () -> Unit, modifier: Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Eyebrow(DateTimeFormatter.ofPattern("EEEE, MMM d").format(now.atZone(ZoneId.systemDefault())))
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.tab_today), style = AppType.title, color = Palette.text)
        }
        if (latest != null) SyncPill(latest, now, onShowSyncLog)
    }
}

/** "● 6 min ago": the dot breathes while the data is fresh. */
@Composable
private fun SyncPill(latest: Instant, now: Instant, onClick: () -> Unit) {
    val fresh = Duration.between(latest, now) < Duration.ofMinutes(30)
    val pulse = if (!fresh || LocalSnapAnimations.current) 1f else {
        val t by rememberInfiniteTransition(label = "sync").animateFloat(
            0.35f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "syncDot",
        )
        t
    }
    val source = remember { MutableInteractionSource() }
    Row(
        Modifier
            .padding(bottom = 6.dp)
            .pressScale(source)
            .clip(CircleShape)
            .background(Palette.card)
            .border(1.dp, glassEdge(), CircleShape)
            .clickable(source, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(7.dp)
                .graphicsLayer { alpha = if (fresh) pulse else 1f }
                .clip(CircleShape)
                .background(if (fresh) Palette.accent else Palette.faint),
        )
        Spacer(Modifier.width(8.dp))
        Text(Format.ago(latest, now), style = MaterialTheme.typography.labelMedium, color = Palette.muted)
    }
}

@Composable
private fun AccessPrompts(access: Access, actions: Actions) {
    when (access) {
        is Access.Unavailable -> ActionCard(
            title = stringResource(R.string.hc_missing_title),
            body = stringResource(R.string.hc_missing_body),
            button = stringResource(R.string.hc_install),
            onClick = actions.installHealthConnect,
        )
        Access.NeedsPermission -> ActionCard(
            title = stringResource(R.string.perm_title),
            body = stringResource(R.string.perm_body),
            button = stringResource(R.string.perm_grant),
            onClick = actions.requestCore,
        )
        is Access.Ready -> {
            if (access.extrasMissing) {
                ActionCard(
                    title = stringResource(R.string.extras_title),
                    body = stringResource(R.string.extras_body),
                    button = stringResource(R.string.perm_grant),
                    onClick = actions.requestCore,
                )
            }
            if (access.backgroundSupported && !access.backgroundGranted) {
                ActionCard(
                    title = stringResource(R.string.bg_title),
                    body = stringResource(R.string.bg_body),
                    button = stringResource(R.string.perm_grant),
                    onClick = actions.requestBackground,
                )
            }
        }
        Access.Checking -> Unit
    }
}

/** The ring with footprints over the count, and which stretch of time is being counted. */
@Composable
fun Hero(
    steps: Long,
    goal: Int,
    window: DayWindow,
    now: Instant,
    onAdjust: ((DayWindow) -> Unit)?,
    modifier: Modifier = Modifier,
    ringSize: Dp = 276.dp,
) {
    val ratio = RingProgress.of(steps, goal)
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        StepsRing(ratio, ringSize, strokeFraction = 0.082f, delayMillis = 150) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painterResource(R.drawable.ic_steps),
                    contentDescription = null,
                    tint = if (ratio >= 1f) Palette.goal else Palette.accent,
                    modifier = Modifier.size(ringSize * 0.105f),
                )
                Spacer(Modifier.height(2.dp))
                CountUp(steps.toDouble(), ringSize.value * 0.23f, delayMillis = 150, durationMillis = 1500)
                Spacer(Modifier.height(ringSize * 0.07f))
            }
        }
        if (onAdjust != null) {
            Spacer(Modifier.height(18.dp))
            DayChip(window, now, onClick = { onAdjust(window) })
        }
    }
}

/** Which stretch of time is being counted; tap to adjust it. */
@Composable
private fun DayChip(w: DayWindow, now: Instant, onClick: () -> Unit) {
    val text = if (!w.isOngoing) stringResource(R.string.today_ended, Format.time(w.endOr(now))) else since(w.start, now)
    val source = remember { MutableInteractionSource() }
    Row(
        Modifier
            .pressScale(source)
            .clip(CircleShape)
            .background(Palette.card)
            .border(1.dp, glassEdge(), CircleShape)
            .clickable(source, indication = null, onClick = onClick)
            .padding(start = 16.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = Palette.muted)
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.adjust), tint = Palette.faint, modifier = Modifier.size(14.dp))
    }
}

/** "Since 7:12 AM", "Since yesterday, 5:00 AM", "Since Thu, 5:00 AM". */
@Composable
private fun since(start: Instant, now: Instant): String {
    val zone = ZoneId.systemDefault()
    val days = ChronoUnit.DAYS.between(start.atZone(zone).toLocalDate(), now.atZone(zone).toLocalDate())
    val time = Format.time(start)
    return when (days) {
        0L -> stringResource(R.string.today_since, time)
        1L -> stringResource(R.string.today_since_yesterday, time)
        else -> stringResource(R.string.today_since_day, DateTimeFormatter.ofPattern("EEE").format(start.atZone(zone)), time)
    }
}

/** Calories and distance side by side in one panel. */
@Composable
fun Metrics(totals: DayTotals, settings: AppSettings, modifier: Modifier = Modifier) {
    val calories = totals.calories
    val distance = totals.distanceMeters
    if (calories == null && distance == null) return
    Panel(modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            calories?.let {
                Metric(
                    R.drawable.ic_fire, Palette.calories,
                    stringResource(
                        if (settings.calorieMode == CalorieMode.ACTIVE) R.string.widget_label_active_calories
                        else R.string.widget_label_calories
                    ),
                    it, unit = null, decimals = 0, Modifier.weight(1f),
                )
            }
            if (calories != null && distance != null) VerticalRule(Modifier.padding(vertical = 18.dp))
            distance?.let {
                val text = Format.distance(it, settings.distanceUnit) // "3.8 mi"
                Metric(
                    R.drawable.ic_distance, Palette.distance, stringResource(R.string.widget_label_distance),
                    text.substringBeforeLast(' ').toDoubleOrNull() ?: 0.0, unit = text.substringAfterLast(' '), decimals = 1,
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Metric(icon: Int, tint: Color, label: String, value: Double, unit: String?, decimals: Int, modifier: Modifier) {
    Column(modifier.padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconDisc(icon, tint, 28.dp)
            Spacer(Modifier.width(10.dp))
            Eyebrow(label)
        }
        Spacer(Modifier.height(14.dp))
        ValueWithUnit(value, 34f, unit, decimals, delayMillis = 300)
    }
}

/** Offers to pin the widget straight onto the home screen if none is placed yet. */
@Composable
private fun AddWidgetPrompt() {
    val context = LocalContext.current
    val manager = AppWidgetManager.getInstance(context)
    val provider = ComponentName(context, StepsWidgetReceiver::class.java)
    if (manager.getAppWidgetIds(provider).isNotEmpty() || !manager.isRequestPinAppWidgetSupported) return
    ActionCard(
        title = stringResource(R.string.add_widget_title),
        body = stringResource(R.string.add_widget_body),
        button = stringResource(R.string.add_widget_button),
        onClick = { manager.requestPinAppWidget(provider, null, null) },
    )
}
