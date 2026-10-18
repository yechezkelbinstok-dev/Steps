package app.wakesteps.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.wakesteps.R
import app.wakesteps.core.Format
import app.wakesteps.data.SyncEvent
import app.wakesteps.ui.theme.Palette
import java.time.Duration

/**
 * Every time new tracker data showed up in Health Connect, so the user can see how often
 * Fitbit actually writes it there and how far behind it is.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncLogSheet(events: List<SyncEvent>, onDone: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDone, containerColor = Palette.card) {
        LazyColumn(
            Modifier.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.sync_log_title), style = MaterialTheme.typography.headlineSmall, color = Palette.text)
                    val gaps = events.zipWithNext { newer, older -> Duration.between(older.seenAt, newer.seenAt) }
                    val lags = events.map { Duration.between(it.dataUpTo, it.seenAt) }
                    if (gaps.size >= 2) {
                        Text(
                            stringResource(
                                R.string.sync_log_summary,
                                Format.duration(median(gaps)),
                                Format.duration(median(lags)),
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Palette.text,
                        )
                    }
                    Text(
                        stringResource(R.string.sync_log_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.muted,
                    )
                }
            }
            if (events.isEmpty()) {
                item { Text(stringResource(R.string.sync_log_empty)) }
            }
            items(events, key = { it.seenAt.epochSecond }) { e ->
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("${Format.day(e.seenAt)}, ${Format.time(e.seenAt)}", style = MaterialTheme.typography.bodyLarge, color = Palette.text)
                        Text(
                            stringResource(R.string.sync_log_up_to, Format.time(e.dataUpTo)),
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.muted,
                        )
                    }
                    if (e.stepsDelta > 0) {
                        Text("+${Format.number(e.stepsDelta)}", style = MaterialTheme.typography.titleMedium, color = Palette.accent)
                    }
                }
            }
            item { Text("", Modifier.padding(bottom = 24.dp)) }
        }
    }
}

private fun median(values: List<Duration>): Duration = values.sorted()[values.size / 2]
