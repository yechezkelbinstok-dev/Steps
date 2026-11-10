package app.wakesteps.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.wakesteps.R
import app.wakesteps.core.Format
import app.wakesteps.data.wakeDate
import app.wakesteps.core.SleepNight
import app.wakesteps.core.PlaceFix
import app.wakesteps.core.RestBlock
import app.wakesteps.data.AppSettings
import app.wakesteps.ui.theme.AppType
import app.wakesteps.ui.theme.Palette
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private const val GOAL_STEP = 500
private val GOAL_RANGE = 1_000..50_000

@Composable
fun SettingsScreen(
    settings: AppSettings,
    update: ((AppSettings) -> AppSettings) -> Unit,
    actions: Actions,
    access: Access,
    latestSync: Instant?,
    onShowSyncLog: () -> Unit,
    onOpenGoogleHealth: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    googleError: String?,
    bottomPadding: Dp,
    /** Shabbos and Yom Tov to a week ahead, oldest first. */
    restBlocks: List<RestBlock> = emptyList(),
    places: List<PlaceFix> = emptyList(),
    locationAccess: LocationAccess = LocationAccess.NONE,
    /** A newer version of the app, if there is one. */
    appUpdate: UpdateState = UpdateState.Unknown,
    onCheckUpdate: () -> Unit = {},
    onUpdate: () -> Unit = {},
    /** Every night read, newest first, and which are left out (by the morning they ended). */
    nights: List<SleepNight> = emptyList(),
    removedNights: Set<LocalDate> = emptySet(),
    onNightRemoved: (SleepNight, Boolean) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(Modifier.entrance(0)) {
            Eyebrow(stringResource(R.string.settings_eyebrow))
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.tab_settings), style = AppType.title, color = Palette.text)
        }

        Panel(Modifier.fillMaxWidth().entrance(1)) {
            Eyebrow(stringResource(R.string.set_goal))
            var goal by remember(settings.goal) { mutableIntStateOf(settings.goal) }
            // Save once the dial settles, not on every tick: each save redraws the widget.
            LaunchedEffect(goal) {
                if (goal == settings.goal) return@LaunchedEffect
                delay(700)
                update { it.copy(goal = goal) }
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(Format.number(goal.toLong()), style = AppType.number(56f), color = Palette.text)
            }
            Spacer(Modifier.height(8.dp))
            RulerDial(
                goal, GOAL_RANGE, step = GOAL_STEP, mid = 1_000, major = 5_000,
                label = { Format.compact(it.toLong()) },
                onChange = { goal = it },
                modifier = Modifier.fillMaxWidth().height(76.dp),
            )
        }

        Panel(Modifier.fillMaxWidth().entrance(2)) {
            Eyebrow(stringResource(R.string.set_widget))
            Spacer(Modifier.height(16.dp))
            WidgetStylePicker(settings.widgetStyle) { style -> update { s -> s.copy(widgetStyle = style) } }
        }

        Panel(Modifier.fillMaxWidth().entrance(3)) {
            Eyebrow(stringResource(R.string.set_restless))
            Spacer(Modifier.height(14.dp))
            PillToggle(
                listOf(stringResource(R.string.restless_own), stringResource(R.string.restless_awake)),
                selected = if (settings.restlessAsAwake) 1 else 0,
                onSelect = { i -> update { it.copy(restlessAsAwake = i == 1) } },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        val now = remember { Instant.now() }
        RestPanel(
            next = restBlocks.firstOrNull { it.end > now },
            now = now,
            place = places.firstOrNull(),
            access = locationAccess,
            onAllow = actions.requestLocation,
            onAllowAlways = actions.requestLocationAlways,
            modifier = Modifier.entrance(4),
        )

        Panel(Modifier.fillMaxWidth().entrance(5), padding = PaddingValues(vertical = 6.dp)) {
            val connected = access is Access.Ready
            LinkRow(stringResource(R.string.set_open_hc), actions.openHealthConnect) {
                StatusDot(if (connected) Palette.accent else Palette.error)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(if (connected) R.string.hc_connected else R.string.hc_needs_access),
                    style = MaterialTheme.typography.labelLarge,
                    color = Palette.muted,
                )
            }
            Divider()
            LinkRow(stringResource(R.string.gh_row), onOpenGoogleHealth) {
                if (settings.googleHealth) StatusDot(if (googleError == null) Palette.accent else Palette.error)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(
                        when {
                            !settings.googleHealth -> R.string.gh_off
                            googleError == null -> R.string.hc_connected
                            else -> R.string.hc_needs_access
                        }
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = Palette.muted,
                )
            }
            Divider()
            LinkRow(stringResource(R.string.sync_log_open), onShowSyncLog) {
                latestSync?.let { Text(Format.ago(it), style = MaterialTheme.typography.labelLarge, color = Palette.muted) }
            }
            Divider()
            LinkRow(stringResource(R.string.diag_row), onOpenDiagnostics)
        }

        val version = remember {
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        }
        LeftOutNights(nights, removedNights, onNightRemoved, Modifier.entrance(6))
        UpdatesPanel(version ?: "?", appUpdate, onCheckUpdate, onUpdate, Modifier.entrance(6))
        Text(
            stringResource(R.string.app_version, version ?: "?"),
            style = MaterialTheme.typography.bodySmall,
            color = Palette.faint,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

/**
 * Leaving out a night the band didn't record whole (a flat battery, the band off): it goes from
 * the Sleep tab, Today and a day's page, its score and the averages. Tucked away: the nights only
 * show once opened, each to leave out or bring back.
 */
@Composable
private fun LeftOutNights(nights: List<SleepNight>, removed: Set<LocalDate>, onRemoved: (SleepNight, Boolean) -> Unit, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    Panel(modifier.fillMaxWidth(), padding = PaddingValues(vertical = 6.dp)) {
        LinkRow(stringResource(R.string.nights_left_out), onClick = { open = !open }) {
            if (removed.isNotEmpty()) {
                Text(removed.size.toString(), style = MaterialTheme.typography.labelLarge, color = Palette.muted)
            }
        }
        if (open) {
            Text(
                stringResource(R.string.nights_left_out_note),
                style = MaterialTheme.typography.bodySmall,
                color = Palette.faint,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp),
            )
            nights.forEach { night ->
                val out = night.wakeDate() in removed
                Divider()
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(Format.day(night.end), style = MaterialTheme.typography.bodyLarge, color = if (out) Palette.faint else Palette.text)
                        Caption("${Format.time(night.start)} – ${Format.time(night.end)}", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { onRemoved(night, !out) }) {
                        Text(stringResource(if (out) R.string.nights_bring_back else R.string.nights_leave_out))
                    }
                }
            }
        }
    }
}

@Composable
private fun Divider() {
    Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)))
}

@Composable
private fun StatusDot(color: Color) {
    Box(Modifier.size(7.dp).clip(CircleShape).background(color))
}

@Composable
private fun LinkRow(text: String, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = Palette.text, modifier = Modifier.weight(1f))
        trailing()
        Spacer(Modifier.width(6.dp))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Palette.faint)
    }
}
