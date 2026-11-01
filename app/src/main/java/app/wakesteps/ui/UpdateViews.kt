package app.wakesteps.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.wakesteps.R
import app.wakesteps.ui.theme.Palette

/** A release's notes as their bullet points, each joined back onto one line. */
fun noteBullets(notes: String): List<String> {
    val out = mutableListOf<String>()
    for (line in notes.lines()) {
        val t = line.trim()
        when {
            t.startsWith("- ") -> out += t.removePrefix("- ")
            t.isNotEmpty() && out.isNotEmpty() -> out[out.lastIndex] = out.last() + " " + t
            t.isNotEmpty() -> out += t
        }
    }
    return out
}

/** How far a download is, as a thin bar. */
@Composable
private fun ProgressLine(progress: Float, modifier: Modifier = Modifier) {
    val shown by animateFloatAsState(progress, label = "download")
    Box(modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Palette.track)) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(shown.coerceIn(0f, 1f))
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(Palette.accent.copy(alpha = 0.6f), Palette.accent))),
        )
    }
}

/** The button, or how the update is getting on, or why it stopped. */
@Composable
private fun UpdateAction(state: UpdateState, onUpdate: () -> Unit, label: String) {
    when (state) {
        is UpdateState.Downloading -> {
            ProgressLine(state.progress)
            Spacer(Modifier.height(8.dp))
            Caption(stringResource(R.string.update_downloading, (state.progress * 100).toInt()), color = Palette.muted)
        }
        is UpdateState.Installing -> Caption(stringResource(R.string.update_installing), color = Palette.muted)
        is UpdateState.Failed -> {
            Caption(state.message, color = Palette.error)
            if (state.release != null) {
                Spacer(Modifier.height(10.dp))
                SoftButton(stringResource(R.string.update_try_again), onUpdate)
            }
        }
        else -> PrimaryButton(label, onUpdate)
    }
}

/**
 * On Today, when a newer version is out: which, the first thing that's new in it, and the button
 * (then the download, then Android's own install).
 */
@Composable
fun UpdateCard(state: UpdateState, onUpdate: () -> Unit, modifier: Modifier = Modifier) {
    val release = when (state) {
        is UpdateState.Available -> state.release
        is UpdateState.Downloading -> state.release
        is UpdateState.Installing -> state.release
        is UpdateState.Failed -> state.release ?: return
        else -> return
    }
    Panel(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Palette.accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_update), contentDescription = null, tint = Palette.accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Eyebrow(stringResource(R.string.update_ready, release.versionName), color = Palette.accent)
                noteBullets(release.notes).firstOrNull()?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.text, maxLines = 3)
                }
                Spacer(Modifier.height(14.dp))
                UpdateAction(state, onUpdate, stringResource(R.string.update_button))
            }
        }
    }
}

/** Settings: the version installed, whether there's a newer one and what's in it, and the way to get it. */
@Composable
fun UpdatesPanel(installed: String, state: UpdateState, onCheck: () -> Unit, onUpdate: () -> Unit, modifier: Modifier = Modifier) {
    val release = when (state) {
        is UpdateState.Available -> state.release
        is UpdateState.Downloading -> state.release
        is UpdateState.Installing -> state.release
        is UpdateState.Failed -> state.release
        else -> null
    }
    Panel(modifier.fillMaxWidth()) {
        Eyebrow(stringResource(R.string.set_updates))
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.update_current, installed), style = MaterialTheme.typography.bodyLarge, color = Palette.text)
        Caption(
            when {
                release != null -> stringResource(R.string.update_available, release.versionName)
                state is UpdateState.Checking -> stringResource(R.string.update_checking)
                state is UpdateState.UpToDate -> stringResource(R.string.update_up_to_date)
                state is UpdateState.Failed -> state.message
                else -> ""
            },
            color = if (release != null) Palette.accent else Palette.muted,
            style = MaterialTheme.typography.bodySmall,
        )
        if (release != null) {
            val bullets = noteBullets(release.notes)
            if (bullets.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Eyebrow(stringResource(R.string.update_whats_new), color = Palette.faint)
                bullets.forEach { bullet ->
                    Spacer(Modifier.height(8.dp))
                    Row {
                        Box(Modifier.padding(top = 8.dp).size(4.dp).clip(CircleShape).background(Palette.faint))
                        Spacer(Modifier.width(10.dp))
                        Text(bullet, style = MaterialTheme.typography.bodySmall, color = Palette.muted)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            UpdateAction(state, onUpdate, stringResource(R.string.update_to, release.versionName))
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.update_first_time), style = MaterialTheme.typography.bodySmall, color = Palette.faint)
        } else if (state !is UpdateState.Checking) {
            Spacer(Modifier.height(14.dp))
            SoftButton(stringResource(R.string.update_check), onCheck)
        }
    }
}
