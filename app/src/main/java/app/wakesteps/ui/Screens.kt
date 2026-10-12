package app.wakesteps.ui

import android.app.TimePickerDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.wakesteps.R
import app.wakesteps.core.DayWindow
import app.wakesteps.core.EndReason
import app.wakesteps.core.Format
import app.wakesteps.core.NightWindow
import app.wakesteps.core.Overrides
import app.wakesteps.core.StartReason
import app.wakesteps.data.AppSettings
import app.wakesteps.data.CalorieMode
import app.wakesteps.data.DistanceUnit
import app.wakesteps.data.TodaySummary
import app.wakesteps.widget.StepsWidgetReceiver
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

private const val FITBIT_PACKAGE = "com.fitbit.FitbitMobile"

// ---------------------------------------------------------------- Today

@Composable
fun TodayScreen(state: UiState, settings: AppSettings, actions: Actions, onAdjust: (DayWindow) -> Unit) {
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AccessCards(state.access, actions)
        state.error?.let { ErrorCard(it) }
        state.today?.let { TodayContent(it, settings, onAdjust) }
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
        is Access.Ready -> {
            if (access.extrasMissing) {
                InfoCard(
                    title = stringResource(R.string.extras_title),
                    body = stringResource(R.string.extras_body),
                    button = stringResource(R.string.perm_grant),
                    onClick = actions.requestCore,
                )
            }
            if (access.backgroundSupported && !access.backgroundGranted) {
                InfoCard(
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

@Composable
private fun TodayContent(t: TodaySummary, settings: AppSettings, onAdjust: (DayWindow) -> Unit) {
    val w = t.window
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        StepsRing(t.totals.steps, settings.goal)
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val start = Format.time(w.start)
            val title = when {
                !w.isOngoing -> stringResource(R.string.today_asleep, start, Format.time(w.endOr(t.now)))
                w.startReason == StartReason.FALLBACK -> stringResource(R.string.today_since_reset, start)
                w.startReason == StartReason.MANUAL -> stringResource(R.string.today_since_manual, start)
                else -> stringResource(R.string.today_since_wake, start)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { onAdjust(w) }) { Text(stringResource(R.string.adjust)) }
            }
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
        StatCard(
            label = stringResource(
                if (settings.calorieMode == CalorieMode.ACTIVE) R.string.stat_active_calories else R.string.stat_calories
            ),
            value = Format.calories(t.totals.calories),
            modifier = Modifier.weight(1f),
        )
        StatCard(
            label = stringResource(R.string.stat_distance),
            value = Format.distance(t.totals.distanceMeters, settings.distanceUnit),
            modifier = Modifier.weight(1f),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val hours = Duration.between(w.start, w.endOr(t.now)).toMinutes() / 60.0
        StatCard(
            label = stringResource(R.string.stat_per_hour),
            value = if (hours >= 0.25) Format.number((t.totals.steps / hours).toLong()) else "–",
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

    AddWidgetCard()

    Text(
        stringResource(R.string.sync_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Offers to pin the widget straight onto the home screen if none is placed yet. */
@Composable
private fun AddWidgetCard() {
    val context = LocalContext.current
    val manager = AppWidgetManager.getInstance(context)
    val provider = ComponentName(context, StepsWidgetReceiver::class.java)
    if (manager.getAppWidgetIds(provider).isNotEmpty() || !manager.isRequestPinAppWidgetSupported) return
    InfoCard(
        title = stringResource(R.string.add_widget_title),
        body = stringResource(R.string.add_widget_body),
        button = stringResource(R.string.add_widget_button),
        onClick = { manager.requestPinAppWidget(provider, null, null) },
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
fun HistoryScreen(state: UiState, settings: AppSettings, overrides: Overrides, onAdjust: (DayWindow) -> Unit) {
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
            val progress = if (settings.goal > 0) (day.totals.steps.toFloat() / settings.goal).coerceIn(0f, 1f) else 0f
            Card(onClick = { onAdjust(w) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            Format.day(w.start),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            Format.number(day.totals.steps),
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
                    val extras = listOfNotNull(
                        day.totals.calories?.let { Format.calories(it) },
                        day.totals.distanceMeters?.let { Format.distance(it, settings.distanceUnit) },
                    )
                    if (extras.isNotEmpty()) {
                        Text(
                            extras.joinToString("  ·  "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        strokeCap = StrokeCap.Round,
                    )
                    val tags = buildList {
                        if (overrides.touches(w, now)) add(stringResource(R.string.tag_edited))
                        if (w.startReason == StartReason.FALLBACK) add(stringResource(R.string.tag_fallback_start))
                        if (w.endReason == EndReason.FALLBACK) add(stringResource(R.string.tag_no_sleep))
                        if (w.naps.isNotEmpty()) add(pluralStringResource(R.plurals.tag_naps, w.naps.size, w.naps.size))
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

        Section(stringResource(R.string.set_naps), stringResource(R.string.set_naps_help)) {
            val night = state.today?.night ?: NightWindow.DEFAULT
            val range = "${Format.clock(night.start)} – ${Format.clock(night.end)}"
            Text(
                stringResource(if (night.learned) R.string.set_night_learned else R.string.set_night_default, range),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
            MinutesSlider(
                label = stringResource(R.string.set_night_min),
                minutes = settings.minNightSleepMinutes,
                range = 30f..180f,
                step = 15,
                onChange = { m -> update { it.copy(minNightSleepMinutes = m) } },
            )
            MinutesSlider(
                label = stringResource(R.string.set_day_min),
                minutes = settings.minDaySleepMinutes,
                range = 180f..480f,
                step = 30,
                onChange = { m -> update { it.copy(minDaySleepMinutes = m) } },
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
                    Format.clock(LocalTime.of(t.hour, t.minute)),
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
            Choice(
                options = options.map { it to sourceLabel(it) },
                selected = settings.sourcePackage,
                onSelect = { pkg -> update { it.copy(sourcePackage = pkg) } },
            )
        }

        Section(stringResource(R.string.set_calories), stringResource(R.string.set_calories_help)) {
            Choice(
                options = listOf(
                    CalorieMode.TOTAL to stringResource(R.string.calories_total),
                    CalorieMode.ACTIVE to stringResource(R.string.calories_active),
                ),
                selected = settings.calorieMode,
                onSelect = { mode -> update { it.copy(calorieMode = mode) } },
            )
        }

        Section(stringResource(R.string.set_distance)) {
            Choice(
                options = listOf(
                    DistanceUnit.AUTO to stringResource(R.string.distance_auto),
                    DistanceUnit.KILOMETERS to stringResource(R.string.distance_km),
                    DistanceUnit.MILES to stringResource(R.string.distance_mi),
                ),
                selected = settings.distanceUnit,
                onSelect = { unit -> update { it.copy(distanceUnit = unit) } },
            )
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
        val version = remember {
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        }
        Text(
            stringResource(R.string.app_version, version ?: "?"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

/** A labelled slider over a duration, snapping to [step] minutes; saves when released. */
@Composable
private fun MinutesSlider(
    label: String,
    minutes: Int,
    range: ClosedFloatingPointRange<Float>,
    step: Int,
    onChange: (Int) -> Unit,
) {
    var value by remember(minutes) { mutableFloatStateOf(minutes.toFloat()) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(Format.duration(Duration.ofMinutes(value.toLong())), style = MaterialTheme.typography.titleMedium)
    }
    Slider(
        value = value,
        onValueChange = { value = (it / step).toInt() * step.toFloat() },
        onValueChangeFinished = { onChange(value.toInt()) },
        valueRange = range,
    )
}

@Composable
private fun <T> Choice(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column {
        options.forEach { (value, label) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(selected = value == selected) { onSelect(value) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = value == selected, onClick = null)
                Spacer(Modifier.size(8.dp))
                Text(label)
            }
        }
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
