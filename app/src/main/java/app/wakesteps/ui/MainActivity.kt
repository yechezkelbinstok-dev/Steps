package app.wakesteps.ui

import android.content.Intent
import androidx.core.net.toUri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.res.stringResource
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wakesteps.R
import app.wakesteps.core.DayWindow
import app.wakesteps.data.HealthSource
import app.wakesteps.ui.theme.WakeStepsTheme

private enum class Tab(val label: Int, val icon: ImageVector) {
    TODAY(R.string.tab_today, Icons.Default.Home),
    HISTORY(R.string.tab_history, Icons.Default.DateRange),
    SETTINGS(R.string.tab_settings, Icons.Default.Settings),
}

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { WakeStepsTheme { App(vm) } }
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
        setLiveMode = { on -> vm.updateSettings { it.copy(liveMode = on) } },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = vm::reload) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(stringResource(t.label)) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                Tab.TODAY -> TodayScreen(state, settings, actions, onAdjust = { adjusting = it })
                Tab.HISTORY -> HistoryScreen(state, settings, overrides, onAdjust = { adjusting = it })
                Tab.SETTINGS -> SettingsScreen(state, settings, vm::updateSettings, actions, onShowSyncLog = { showSyncLog = true })
            }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        adjusting?.let { day -> AdjustDaySheet(day, overrides, vm, onDone = { adjusting = null }) }
        if (showSyncLog) SyncLogSheet(syncLog, onDone = { showSyncLog = false })
    }
}

class Actions(
    val openHealthConnect: () -> Unit,
    val installHealthConnect: () -> Unit,
    val requestCore: () -> Unit,
    val requestBackground: () -> Unit,
    val setLiveMode: (Boolean) -> Unit,
)
