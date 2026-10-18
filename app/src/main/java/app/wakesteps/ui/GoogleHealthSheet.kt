package app.wakesteps.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import app.wakesteps.R
import app.wakesteps.app
import app.wakesteps.data.GoogleHealth
import app.wakesteps.ui.theme.AppType
import app.wakesteps.ui.theme.Palette

/** One setup step: what to do, the console page to do it on, and values to paste there. */
private class SetupStep(val title: Int, val url: String?, val values: List<String> = emptyList())

private val STEPS = listOf(
    SetupStep(R.string.gh_step_project, "https://console.cloud.google.com/projectcreate"),
    SetupStep(R.string.gh_step_api, "https://console.cloud.google.com/apis/library/health.googleapis.com"),
    SetupStep(R.string.gh_step_branding, "https://console.cloud.google.com/auth/overview", listOf("Awake Steps")),
    SetupStep(
        R.string.gh_step_scopes, "https://console.cloud.google.com/auth/scopes",
        listOf(GoogleHealth.SCOPE_ACTIVITY, GoogleHealth.SCOPE_SLEEP),
    ),
    SetupStep(R.string.gh_step_audience, "https://console.cloud.google.com/auth/audience"),
    // Package name and fingerprint are filled in below, from the installed app itself.
    SetupStep(R.string.gh_step_client, "https://console.cloud.google.com/auth/clients/create"),
    SetupStep(R.string.gh_step_app, null),
)

/**
 * Connecting Google Health: a one-time setup in the user's own Google Cloud project (each step
 * opens the right console page and has its values ready to copy), then Connect.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleHealthSheet(
    connected: Boolean,
    problem: String?,
    busy: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val sha1 = remember { context.app.googleHealth.signingSha1() }
    ModalBottomSheet(onDismissRequest = onDone, containerColor = Palette.cardHigh) {
        GoogleHealthContent(connected, problem, busy, sha1, onConnect, onDisconnect)
    }
}

/** The sheet's content: status, Connect / Disconnect, and the setup steps. */
@Composable
fun GoogleHealthContent(
    connected: Boolean,
    problem: String?,
    busy: Boolean,
    sha1: String?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column {
            Eyebrow(stringResource(if (connected) R.string.hc_connected else R.string.gh_not_connected),
                color = if (connected) Palette.accent else Palette.muted)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.gh_title), style = AppType.title.copy(fontSize = 28.sp), color = Palette.text)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.gh_intro), style = MaterialTheme.typography.bodyMedium, color = Palette.muted)
        }
        problem?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.error) }
        if (connected) {
            SoftButton(stringResource(R.string.gh_disconnect), onDisconnect)
        } else {
            PrimaryButton(stringResource(if (busy) R.string.gh_connecting else R.string.gh_connect), { if (!busy) onConnect() })
        }
        Eyebrow(stringResource(R.string.gh_setup), Modifier.padding(top = 10.dp))
        STEPS.forEachIndexed { i, step ->
            val values = if (step.title == R.string.gh_step_client) listOfNotNull(context.packageName, sha1) else step.values
            StepRow(i + 1, stringResource(step.title), step.url, values)
        }
    }
}

@Composable
private fun StepRow(number: Int, title: String, url: String?, values: List<String>) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(Palette.accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", style = MaterialTheme.typography.labelMedium, color = Palette.accent)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = Palette.text)
            values.forEach { CopyChip(it) }
        }
        if (url == null) return@Row
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.gh_open),
            style = MaterialTheme.typography.labelLarge,
            color = Palette.accent,
            modifier = Modifier
                .clip(CircleShape)
                .clickable {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
                }
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** A value to paste into the console; tap to copy. */
@Composable
private fun CopyChip(value: String) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(12.dp)
    val copied = stringResource(R.string.gh_copied)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.card)
            .border(1.dp, glassEdge(), shape)
            .clickable {
                context.getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText(value, value))
                // Android 13+ confirms copies itself.
                if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = Palette.text,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Eyebrow(stringResource(R.string.gh_copy), color = Palette.accent)
    }
}
