package app.wakesteps.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import app.wakesteps.data.TodaySummary
import app.wakesteps.ui.theme.Palette
import app.wakesteps.widget.StepsWidgetReceiver
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
) {
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle(stringResource(R.string.tab_today))
        AccessPrompts(state.access, actions)
        state.error?.let { ErrorNote(it) }
        val today = state.today
        if (today != null) {
            Hero(today, settings, onAdjust)
            Stats(today, settings)
            if (today.hourly.isNotEmpty()) Hourly(today)
            today.latestDataAt?.let { latest ->
                Text(
                    stringResource(R.string.synced_ago, Format.ago(latest, today.now)),
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.faint,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clip(CircleShape)
                        .clickable(onClick = onShowSyncLog)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            AddWidgetPrompt()
        }
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

/** The ring with footprints over the count, on a soft halo of the widget's colour. */
@Composable
private fun Hero(t: TodaySummary, settings: AppSettings, onAdjust: (DayWindow) -> Unit) {
    val progress = RingProgress(t.totals.steps, settings.goal)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(330.dp)
                .background(Brush.radialGradient(listOf(Palette.glow.copy(alpha = 0.55f), Color.Transparent))),
            contentAlignment = Alignment.Center,
        ) {
            StepsRing(progress, 264.dp, strokeFraction = 0.085f) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painterResource(R.drawable.ic_steps),
                        contentDescription = null,
                        tint = if (progress.goalReached) Palette.goal else Palette.accent,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    BigValue(Format.number(t.totals.steps), size = 60)
                    Spacer(Modifier.height(18.dp))
                }
            }
        }
        DayChip(t.window, t.now, onClick = { onAdjust(t.window) })
    }
}

/** Which stretch of time is being counted; tap to adjust it. */
@Composable
private fun DayChip(w: DayWindow, now: Instant, onClick: () -> Unit) {
    val text = if (!w.isOngoing) stringResource(R.string.today_ended, Format.time(w.endOr(now)))
        else since(w.start, now)
    Row(
        Modifier
            .clip(CircleShape)
            .background(Palette.card)
            .border(1.dp, Palette.outline, CircleShape)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 14.dp, top = 9.dp, bottom = 9.dp),
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

@Composable
private fun Stats(t: TodaySummary, settings: AppSettings) {
    val calories = t.totals.calories
    val distance = t.totals.distanceMeters
    if (calories == null && distance == null) return
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        calories?.let {
            StatTile(
                icon = R.drawable.ic_fire,
                tint = Palette.calories,
                value = Format.number(Math.round(it)),
                unit = null,
                label = stringResource(
                    if (settings.calorieMode == CalorieMode.ACTIVE) R.string.widget_label_active_calories
                    else R.string.widget_label_calories
                ),
                modifier = Modifier.weight(1f),
            )
        }
        distance?.let {
            val text = Format.distance(it, settings.distanceUnit) // "3.8 mi"
            StatTile(
                icon = R.drawable.ic_distance,
                tint = Palette.distance,
                value = text.substringBeforeLast(' '),
                unit = text.substringAfterLast(' '),
                label = stringResource(R.string.widget_label_distance),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatTile(icon: Int, tint: Color, value: String, unit: String?, label: String, modifier: Modifier) {
    AppCard(modifier, padding = PaddingValues(18.dp)) {
        IconDisc(icon, tint)
        Spacer(Modifier.height(14.dp))
        BigValue(value, size = 30, unit = unit)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Palette.muted)
    }
}

@Composable
private fun Hourly(t: TodaySummary) {
    AppCard(Modifier.fillMaxWidth()) {
        CardLabel(stringResource(R.string.hourly_title))
        Spacer(Modifier.height(16.dp))
        Bars(t.hourly.map { it.second }, Modifier.fillMaxWidth().height(110.dp))
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(
                Format.time(t.hourly.first().first),
                style = MaterialTheme.typography.labelSmall,
                color = Palette.faint,
                modifier = Modifier.weight(1f),
            )
            Text(stringResource(R.string.now_label), style = MaterialTheme.typography.labelSmall, color = Palette.faint)
        }
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
