package app.wakesteps.ui

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.wakesteps.R
import app.wakesteps.core.EndReason
import app.wakesteps.core.Format
import app.wakesteps.core.StartReason
import app.wakesteps.data.AppSettings
import app.wakesteps.data.TodaySummary
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

private const val FITBIT_PACKAGE = "com.fitbit.FitbitMobile"

// ---------------------------------------------------------------- Today

@Composable
fun TodayScreen(state: UiState, settings: AppSettings, actions: Actions) {
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AccessCards(state.access, actions)
        state.error?.let { ErrorCard(it) }
        state.today?.let { TodayContent(it, settings) }
    }
}

@Composable
private fun AccessCards(access: Access, actions: Actions) {
    when (access) {
        is Access.Unavailable -> InfoCard(
            title = stringResource(R.string.hc_missing_title),
            body = stringResource(R.string.hc_missing_body),
            button = stringResource(R.string.hc_install),
            onClick = actions.installHealthConnect,
        )
        Access.NeedsPermission -> InfoCard(
            title = stringResource(R.string.perm_title),
            body = stringResource(R.string.perm_body),
            button = stringResource(R.string.perm_grant),
            onClick = actions.requestCore,
        )
        is Access.Ready -> if (access.backgroundSupported && !access.backgroundGranted) {
            InfoCard(
                title = stringResource(R.string.bg_title),
                body = stringResource(R.string.bg_body),
                button = stringResource(R.string.perm_grant),
                onClick = actions.requestBackground,
            )
        }
        Access.Checking -> Unit
    }
}

@Composable
private fun TodayContent(t: TodaySummary, settings: AppSettings) {
    val w = t.window
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        StepsRing(t.steps, settings.goal)
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val start = Format.time(w.start)
            val title = when {
                !w.isOngoing -> stringResource(R.string.today_asleep, start, Format.time(w.endOr(t.now)))
                w.startReason == StartReason.FALLBACK -> stringResource(R.string.today_since_reset, start)
                else -> stringResource(R.string.today_since_wake, start)
            }
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.today_awake_for, Format.duration(Duration.between(w.start, w.endOr(t.now)))),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (w.startReason == StartReason.FALLBACK) {
                Text(
                    stringResource(R.string.today_fallback_note, Format.time(w.start)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val hours = Duration.between(w.start, w.endOr(t.now)).toMinutes() / 60.0
        StatCard(
            label = stringResource(R.string.stat_per_hour),
            value = if (hours >= 0.25) Format.number((t.steps / hours).toLong()) else "–",
            modifier = Modifier.weight(1f),
        )
        StatCard(
            label = stringResource(R.string.stat_synced),
            value = t.latestDataAt?.let { Format.ago(it, t.now) } ?: "–",
            modifier = Modifier.weight(1f),
        )
    }

    if (t.hourly.isNotEmpty()) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.hourly_title), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(12.dp))
                HourlyBars(t.hourly)
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text(
                        Format.time(t.hourly.first().first),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(Format.time(t.hourly.last().first), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }

    Text(
        stringResource(R.string.sync_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StepsRing(steps: Long, goal: Int) {
    val progress = if (goal > 0) (steps.toFloat() / goal).coerceIn(0f, 1f) else 0f
    val track = MaterialTheme.colorScheme.secondaryContainer
    val fill = MaterialTheme.colorScheme.primary
    Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(240.dp)) {
            val stroke = Stroke(width = 22.dp.toPx(), cap = StrokeCap.Round)
            val inset = stroke.width / 2
            val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
            drawArc(track, 135f, 270f, false, Offset(inset, inset), arcSize, style = stroke)
            if (progress > 0f) {
                drawArc(fill, 135f, 270f * progress, false, Offset(inset, inset), arcSize, style = stroke)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                Format.number(steps),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.of_goal, Format.number(goal.toLong())),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HourlyBars(buckets: List<Pair<Instant, Long>>) {
    val max = (buckets.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
    val color = MaterialTheme.colorScheme.primary
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
    ) {
        val slot = size.width / buckets.size
        val barWidth = (slot * 0.7f).coerceAtLeast(2f)
        buckets.forEachIndexed { i, (_, steps) ->
            val h = size.height * steps / max
            drawRoundRect(
                color = color,
                topLeft = Offset(i * slot + (slot - barWidth) / 2, size.height - h),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String, button: String, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onClick) { Text(button) }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Text(message, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

// ---------------------------------------------------------------- History

@Composable
fun HistoryScreen(state: UiState, settings: AppSettings) {
    if (state.history.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                stringResource(if (state.loading) R.string.loading else R.string.history_empty),
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    val now = Instant.now()
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(state.history, key = { it.window.start.toEpochMilli() }) { day ->
            val w = day.window
            val progress = if (settings.goal > 0) (day.steps.toFloat() / settings.goal).coerceIn(0f, 1f) else 0f
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            Format.day(w.start),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            Format.number(day.steps),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    val end = if (w.isOngoing) stringResource(R.string.now) else Format.time(w.endOr(now))
                    Text(
                        "${Format.time(w.start)} – $end  ·  ${Format.duration(Duration.between(w.start, w.endOr(now)))}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        strokeCap = StrokeCap.Round,
                    )
                    val tags = buildList {
                        if (w.startReason == StartReason.FALLBACK) add(stringResource(R.string.tag_fallback_start))
                        if (w.endReason == EndReason.FALLBACK) add(stringResource(R.string.tag_no_sleep))
                    }
                    if (tags.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            tags.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Settings

@Composable
fun SettingsScreen(
    state: UiState,
    settings: AppSettings,
    update: ((AppSettings) -> AppSettings) -> Unit,
    actions: Actions,
) {
    val context = LocalContext.current
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Section(stringResource(R.string.set_goal)) {
            var goal by remember(settings.goal) { mutableFloatStateOf(settings.goal.toFloat()) }
            Text(Format.number(goal.toLong()), style = MaterialTheme.typography.headlineSmall)
            Slider(
                value = goal,
                onValueChange = { goal = (it / 500f).toInt() * 500f },
                onValueChangeFinished = { update { s -> s.copy(goal = goal.toInt()) } },
                valueRange = 1000f..30000f,
            )
        }

        Section(stringResource(R.string.set_min_sleep), stringResource(R.string.set_min_sleep_help)) {
            var minutes by remember(settings.minSleepMinutes) { mutableFloatStateOf(settings.minSleepMinutes.toFloat()) }
            Text(Format.duration(Duration.ofMinutes(minutes.toLong())), style = MaterialTheme.typography.headlineSmall)
            Slider(
                value = minutes,
                onValueChange = { minutes = (it / 15f).toInt() * 15f },
                onValueChangeFinished = { update { s -> s.copy(minSleepMinutes = minutes.toInt()) } },
                valueRange = 60f..360f,
            )
        }

        Section(stringResource(R.string.set_fallback), stringResource(R.string.set_fallback_help)) {
            val t = settings.fallbackTime
            OutlinedButton(onClick = {
                TimePickerDialog(
                    context,
                    { _, h, m -> update { s -> s.copy(fallbackMinuteOfDay = h * 60 + m) } },
                    t.hour, t.minute, DateFormat.is24HourFormat(context),
                ).show()
            }) {
                Text(
                    java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT)
                        .format(LocalTime.of(t.hour, t.minute)),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        Section(stringResource(R.string.set_source), stringResource(R.string.set_source_help)) {
            val options = buildList {
                add(null)
                addAll(state.sources)
                settings.sourcePackage?.let { if (it !in state.sources) add(it) }
            }
            options.forEach { pkg ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = settings.sourcePackage == pkg) { update { it.copy(sourcePackage = pkg) } }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = settings.sourcePackage == pkg, onClick = null)
                    Spacer(Modifier.size(8.dp))
                    Text(sourceLabel(pkg))
                }
            }
        }

        Section(stringResource(R.string.set_live), stringResource(R.string.set_live_help)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { actions.setLiveMode(!settings.liveMode) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.set_live_toggle), Modifier.weight(1f))
                Switch(checked = settings.liveMode, onCheckedChange = actions.setLiveMode)
            }
        }

        HorizontalDivider()
        OutlinedButton(onClick = actions.openHealthConnect) { Text(stringResource(R.string.set_open_hc)) }
    }
}

@Composable
private fun sourceLabel(pkg: String?): String = when (pkg) {
    null -> stringResource(R.string.source_all)
    FITBIT_PACKAGE -> "Fitbit"
    else -> {
        val pm = LocalContext.current.packageManager
        runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
    }
}

@Composable
private fun Section(title: String, help: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        help?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        content()
    }
}
