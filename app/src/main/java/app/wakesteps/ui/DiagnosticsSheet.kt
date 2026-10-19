package app.wakesteps.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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
import app.wakesteps.data.Diagnostics
import app.wakesteps.ui.theme.AppType
import app.wakesteps.ui.theme.Palette

/**
 * A copyable troubleshooting report: the app's state and a live read from Health Connect and
 * Google Health side by side. Gathered fresh each time the sheet opens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsSheet(onDone: () -> Unit) {
    val context = LocalContext.current
    val report by produceState<String?>(null) { value = Diagnostics.collect(context.app) }
    ModalBottomSheet(onDismissRequest = onDone, containerColor = Palette.cardHigh) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column {
                Eyebrow(stringResource(R.string.diag_eyebrow))
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.diag_title), style = AppType.title.copy(fontSize = 28.sp), color = Palette.text)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.diag_intro), style = MaterialTheme.typography.bodyMedium, color = Palette.muted)
            }

            val text = report
            if (text == null) {
                Text(stringResource(R.string.diag_gathering), style = MaterialTheme.typography.bodyMedium, color = Palette.muted)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(
                        stringResource(R.string.diag_copy),
                        onClick = {
                            context.getSystemService(ClipboardManager::class.java)
                                .setPrimaryClip(ClipData.newPlainText("Awake Steps diagnostics", text))
                            if (Build.VERSION.SDK_INT < 33) {
                                Toast.makeText(context, context.getString(R.string.gh_copied), Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                    SoftButton(
                        stringResource(R.string.diag_share),
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                            runCatching { context.startActivity(Intent.createChooser(send, null)) }
                        },
                    )
                }
                ReportBox(text)
            }
        }
    }
}

/** The report in a monospace, scrollable box, so the columns line up and long lines don't wrap oddly. */
@Composable
private fun ReportBox(text: String) {
    val shape = RoundedCornerShape(16.dp)
    Text(
        text,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
        color = Palette.text,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.card)
            .border(1.dp, glassEdge(), shape)
            .horizontalScroll(rememberScrollState())
            .padding(14.dp),
    )
}
