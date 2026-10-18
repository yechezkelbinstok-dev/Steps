package app.wakesteps.ui

import android.app.TimePickerDialog
import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.wakesteps.R
import app.wakesteps.core.DayWindow
import app.wakesteps.core.Format
import app.wakesteps.core.Overrides
import app.wakesteps.core.SleepSpan
import app.wakesteps.core.StartReason
import app.wakesteps.ui.theme.Palette
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Whether the user has made any judgement call that shapes [day]. */
fun Overrides.touches(day: DayWindow, now: Instant): Boolean {
    val end = day.endOr(now)
    return day.startReason == StartReason.MANUAL ||
        day.wokeFrom?.let { callFor(it) } != null ||
        day.naps.any { callFor(it) != null } ||
        skippedResets.any { it > day.start && it < end }
}

/**
 * Lets the user overrule the automatic rules for one day: call a sleep a nap or vice versa,
 * keep going through an all-nighter reset, or start a new day at a time they choose.
 * Each action closes the sheet; the day list then reflects the change.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdjustDaySheet(day: DayWindow, overrides: Overrides, vm: MainViewModel, onDone: () -> Unit) {
    val context = LocalContext.current
    val now = Instant.now()
    val end = day.endOr(now)
    fun act(change: () -> Unit) {
        change()
        onDone()
    }

    ModalBottomSheet(onDismissRequest = onDone, containerColor = Palette.cardHigh) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text(
                    stringResource(R.string.adjust_title, Format.day(day.start)),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Palette.text,
                )
                Text(
                    "${Format.time(day.start)} – ${if (day.isOngoing) stringResource(R.string.now) else Format.time(end)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.muted,
                )
            }

            Section(stringResource(R.string.adjust_start)) {
                when (day.startReason) {
                    StartReason.WOKE_UP -> {
                        val sleep = day.wokeFrom
                        if (sleep != null) {
                            val yours = overrides.callFor(sleep) == true
                            Text(
                                stringResource(
                                    R.string.adjust_woke_up,
                                    Format.time(day.start),
                                    Format.time(sleep.start),
                                    Format.time(sleep.end),
                                ) + if (yours) " " + stringResource(R.string.adjust_your_call_sleep) else "",
                            )
                            if (yours) {
                                SoftButton(stringResource(R.string.adjust_undo), onClick = { act { vm.clearSleepCall(sleep) } })
                            } else {
                                SoftButton(stringResource(R.string.adjust_was_nap), onClick = { act { vm.setSleepCall(sleep, main = false) } })
                            }
                        }
                    }
                    StartReason.FALLBACK -> {
                        Text(stringResource(R.string.adjust_fallback, Format.time(day.start)))
                        SoftButton(stringResource(R.string.adjust_skip_reset), onClick = { act { vm.setResetSkipped(day.start, skipped = true) } })
                    }
                    StartReason.MANUAL -> {
                        Text(stringResource(R.string.adjust_manual, Format.time(day.start)))
                        SoftButton(stringResource(R.string.adjust_remove_start), onClick = { act { vm.setManualStart(day.start, present = false) } })
                    }
                }
            }

            if (day.naps.isNotEmpty()) {
                Section(stringResource(R.string.adjust_naps)) {
                    day.naps.forEach { nap -> NapRow(nap, overrides.callFor(nap) == false, vm, ::act) }
                }
            }

            val skipped = overrides.skippedResets.filter { it > day.start && it < end }.sorted()
            if (skipped.isNotEmpty()) {
                Section(stringResource(R.string.adjust_kept_going)) {
                    skipped.forEach { at ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.adjust_kept_going_item, Format.time(at), Format.day(at)),
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { act { vm.setResetSkipped(at, skipped = false) } }) {
                                Text(stringResource(R.string.adjust_undo))
                            }
                        }
                    }
                }
            }

            Section(stringResource(R.string.adjust_manual_title)) {
                SoftButton(stringResource(R.string.adjust_manual_button), onClick = {
                    val zone = ZoneId.systemDefault()
                    val start = day.start.atZone(zone)
                    TimePickerDialog(
                        context,
                        { _, h, m ->
                            var pick = start.toLocalDate().atTime(h, m).atZone(zone).toInstant()
                            if (!pick.isAfter(day.start)) pick = pick.plus(Duration.ofDays(1))
                            if (pick.isBefore(end)) {
                                act { vm.setManualStart(pick, present = true) }
                            } else {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.adjust_manual_out_of_range, Format.time(day.start), Format.time(end)),
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        },
                        start.hour, start.minute, DateFormat.is24HourFormat(context),
                    ).show()
                })
            }

            if (overrides.touches(day, now)) {
                HorizontalDivider(color = Palette.outline)
                TextButton(onClick = { act { vm.resetDay(day) } }) {
                    Text(stringResource(R.string.adjust_reset_day))
                }
            }
        }
    }
}

@Composable
private fun NapRow(nap: SleepSpan, yours: Boolean, vm: MainViewModel, act: (() -> Unit) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("${Format.time(nap.start)} – ${Format.time(nap.end)}")
            Text(
                Format.duration(nap.duration) + if (yours) " · " + stringResource(R.string.adjust_your_call) else "",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.muted,
            )
        }
        if (yours) {
            TextButton(onClick = { act { vm.clearSleepCall(nap) } }) { Text(stringResource(R.string.adjust_undo)) }
        } else {
            TextButton(onClick = { act { vm.setSleepCall(nap, main = true) } }) {
                Text(stringResource(R.string.adjust_count_as_sleep))
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = Palette.muted)
        content()
    }
}
