package app.wakesteps.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wakesteps.R
import app.wakesteps.core.DayWindow
import app.wakesteps.data.HealthSource
import app.wakesteps.ui.theme.Palette
import app.wakesteps.ui.theme.WakeStepsTheme

internal enum class Tab(val label: Int) {
    TODAY(R.string.tab_today),
    HISTORY(R.string.tab_history),
    SETTINGS(R.string.tab_settings),
}

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            WakeStepsTheme(settings.widgetStyle) { App(vm) }
        }
    }

    fun openHealthConnect() {
        startActivity(HealthConnectClient.getHealthConnectManageDataIntent(this))
    }

    fun installHealthConnect() {
        val uri = "market://details?id=${HealthSource.HEALTH_CONNECT_PACKAGE}&url=healthconnect%3A%2F%2Fonboarding"
            .toUri()
        startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.android.vending"))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val overrides by vm.overrides.collectAsStateWithLifecycle()
    var adjusting by remember { mutableStateOf<DayWindow?>(null) }
    var showSyncLog by remember { mutableStateOf(false) }
    val syncLog by vm.syncLog.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    // Only show the pull-to-refresh spinner for a pull, not for the automatic reloads.
    var pulled by remember { mutableStateOf(false) }
    LaunchedEffect(state.loading) { if (!state.loading) pulled = false }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.reload() }

    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { vm.reload() }

    val activity = LocalActivity.current as MainActivity
    val actions = Actions(
        openHealthConnect = activity::openHealthConnect,
        installHealthConnect = activity::installHealthConnect,
        requestCore = { permissionLauncher.launch(vm.permissionsToRequest()) },
        requestBackground = { permissionLauncher.launch(vm.backgroundPermission()) },
    )

    // Room under the content for the floating tab bar.
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 104.dp
    Box(Modifier.fillMaxSize().background(Palette.background)) {
        PullToRefreshBox(
            isRefreshing = pulled && state.loading,
            onRefresh = { pulled = true; vm.reload() },
            modifier = Modifier.fillMaxSize(),
        ) {
            when (tab) {
                Tab.TODAY -> TodayScreen(
                    state, settings, actions,
                    onAdjust = { adjusting = it }, onShowSyncLog = { showSyncLog = true }, bottomPadding = bottom,
                )
                Tab.HISTORY -> HistoryScreen(state, settings, overrides, onAdjust = { adjusting = it }, bottomPadding = bottom)
                Tab.SETTINGS -> SettingsScreen(
                    settings, vm::updateSettings, actions, onShowSyncLog = { showSyncLog = true }, bottomPadding = bottom,
                )
            }
        }
        // Content fades out under the tab bar instead of running into it.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(bottom)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Palette.background.copy(alpha = 0.92f)))),
        )
        TabBar(tab, onSelect = { tab = it }, modifier = Modifier.align(Alignment.BottomCenter))
        adjusting?.let { day -> AdjustDaySheet(day, overrides, vm, onDone = { adjusting = null }) }
        if (showSyncLog) SyncLogSheet(syncLog, onDone = { showSyncLog = false })
    }
}

/** A floating pill: the current tab shows its name, the others just their icon. */
@Composable
internal fun TabBar(current: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .navigationBarsPadding()
            .padding(bottom = 16.dp)
            .clip(CircleShape)
            .background(Palette.cardHigh)
            .border(1.dp, Palette.outline, CircleShape)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Tab.entries.forEach { t ->
            val on = t == current
            val color = if (on) Palette.accent else Palette.muted
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(if (on) Palette.accent.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable { onSelect(t) }
                    .padding(horizontal = if (on) 18.dp else 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val icon = when (t) {
                    Tab.TODAY -> painterResource(R.drawable.ic_steps)
                    Tab.HISTORY -> painterResource(R.drawable.ic_bars)
                    Tab.SETTINGS -> rememberVectorPainter(Icons.Default.Settings)
                }
                Icon(icon, contentDescription = stringResource(t.label), tint = color, modifier = Modifier.size(22.dp))
                if (on) {
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(t.label), style = MaterialTheme.typography.labelLarge, color = color)
                }
            }
        }
    }
}

class Actions(
    val openHealthConnect: () -> Unit,
    val installHealthConnect: () -> Unit,
    val requestCore: () -> Unit,
    val requestBackground: () -> Unit,
)
