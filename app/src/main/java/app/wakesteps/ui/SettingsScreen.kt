package app.wakesteps.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.wakesteps.R
import app.wakesteps.core.Format
import app.wakesteps.data.AppSettings
import app.wakesteps.ui.theme.Palette
import kotlinx.coroutines.delay

private const val GOAL_STEP = 500
private val GOAL_RANGE = 1_000..50_000

@Composable
fun SettingsScreen(
    settings: AppSettings,
    update: ((AppSettings) -> AppSettings) -> Unit,
    actions: Actions,
    onShowSyncLog: () -> Unit,
    bottomPadding: Dp,
) {
    val context = LocalContext.current
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle(stringResource(R.string.tab_settings))

        AppCard(Modifier.fillMaxWidth()) {
            CardLabel(stringResource(R.string.set_goal))
            var goal by remember(settings.goal) { mutableIntStateOf(settings.goal) }
            // Save once the taps settle, not on every one: each save redraws the widget.
            LaunchedEffect(goal) {
                if (goal == settings.goal) return@LaunchedEffect
                delay(700)
                update { it.copy(goal = goal) }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundButton(painterResource(R.drawable.ic_minus), stringResource(R.string.goal_less), enabled = goal > GOAL_RANGE.first) {
                    goal = (goal - GOAL_STEP).coerceIn(GOAL_RANGE)
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    BigValue(Format.number(goal.toLong()), size = 44)
                }
                RoundButton(rememberVectorPainter(Icons.Default.Add), stringResource(R.string.goal_more), enabled = goal < GOAL_RANGE.last) {
                    goal = (goal + GOAL_STEP).coerceIn(GOAL_RANGE)
                }
            }
        }

        AppCard(Modifier.fillMaxWidth()) {
            CardLabel(stringResource(R.string.set_widget))
            Spacer(Modifier.height(14.dp))
            WidgetStylePicker(settings.widgetStyle) { style -> update { s -> s.copy(widgetStyle = style) } }
        }

        AppCard(Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
            LinkRow(stringResource(R.string.set_open_hc), actions.openHealthConnect)
            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = Palette.outline)
            LinkRow(stringResource(R.string.sync_log_open), onShowSyncLog)
        }

        val version = remember {
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        }
        Text(
            stringResource(R.string.app_version, version ?: "?"),
            style = MaterialTheme.typography.bodySmall,
            color = Palette.faint,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun RoundButton(icon: Painter, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Palette.cardHigh)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = if (enabled) Palette.text else Palette.faint, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun LinkRow(text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = Palette.text, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Palette.faint)
    }
}
