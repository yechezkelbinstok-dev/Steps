package app.wakesteps.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import app.wakesteps.data.DaySummary
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
import app.wakesteps.core.SleepNight
import app.wakesteps.data.HealthSource
import app.wakesteps.data.wakeDate
import app.wakesteps.ui.theme.Palette
import app.wakesteps.ui.theme.WakeStepsTheme

internal enum class Tab(val label: Int) {
    TODAY(R.string.tab_today),
    SLEEP(R.string.tab_sleep),
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
    var showGoogleHealth by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    val googleError by vm.googleHealthError.collectAsStateWithLifecycle()
    val googleMessage by vm.googleMessage.collectAsStateWithLifecycle()
    val googleBusy by vm.googleBusy.collectAsStateWithLifecycle()
    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        vm.googleConsentResult(it.data)
    }
    val syncLog by vm.syncLog.collectAsStateWithLifecycle()
    val liveSteps by vm.liveSteps.collectAsStateWithLifecycle()
    val scores by vm.scores.collectAsStateWithLifecycle()
    var editingScore by remember { mutableStateOf<SleepNight?>(null) }
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    // Only show the pull-to-refresh spinner for a pull, not for the automatic reloads.
    var pulled by remember { mutableStateOf(false) }
    val refreshState = rememberPullToRefreshState()
    LaunchedEffect(state.loading) { if (!state.loading) pulled = false }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.reload() }

    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { vm.reload() }
    val activityLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.liveStepsAnswered(it)
    }

    val activity = LocalActivity.current as MainActivity
    val actions = Actions(
        openHealthConnect = activity::openHealthConnect,
        installHealthConnect = activity::installHealthConnect,
        requestCore = { permissionLauncher.launch(vm.permissionsToRequest()) },
        requestBackground = { permissionLauncher.launch(vm.backgroundPermission()) },
        requestLiveSteps = {
            if (Build.VERSION.SDK_INT >= 29) activityLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        },
    )

    var openDay by remember { mutableStateOf<DaySummary?>(null) }
    var openNight by remember { mutableStateOf<SleepNight?>(null) }
    // A night opened up to zoom into, from any night's chart.
    var exploring by remember { mutableStateOf<SleepNight?>(null) }
    // The night today began with, and the one before each day in History.
    fun nightBefore(day: DayWindow?): SleepNight? = day?.wokeFrom?.let { span -> state.nights.firstOrNull { it.span == span } }
    // Keep an open day in step with fresh data (e.g. after adjusting it).
    LaunchedEffect(state.history) {
        openDay = openDay?.let { open -> state.history.firstOrNull { it.window.start == open.window.start } ?: open }
    }

    // Room under the content for the floating tab bar.
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 104.dp
    Box(Modifier.fillMaxSize()) {
        Backdrop()
        PullToRefreshBox(
            isRefreshing = pulled && state.loading,
            onRefresh = { pulled = true; vm.reload() },
            modifier = Modifier.fillMaxSize(),
            state = refreshState,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = refreshState,
                    isRefreshing = pulled && state.loading,
                    containerColor = Palette.cardHigh,
                    color = Palette.accent,
                    modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding(),
                )
            },
        ) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (fadeIn(tween(320, delayMillis = 60)) + slideInHorizontally(tween(420, easing = Glide)) { it / 10 * direction })
                        .togetherWith(fadeOut(tween(160)) + slideOutHorizontally(tween(320, easing = Glide)) { -it / 14 * direction })
                },
                label = "tabs",
            ) { t ->
                when (t) {
                    Tab.TODAY -> TodayScreen(
                        state, settings, actions,
                        onAdjust = { adjusting = it }, onShowSyncLog = { showSyncLog = true }, bottomPadding = bottom,
                        liveSteps = liveSteps,
                        lastNight = nightBefore(state.today?.window),
                        lastNightScore = nightBefore(state.today?.window)?.let { scores[it.wakeDate()] },
                        onOpenSleep = { tab = Tab.SLEEP },
                    )
                    Tab.SLEEP -> SleepScreen(
                        state, scores, settings.restlessAsAwake,
                        onOpenNight = { openNight = it }, onEditScore = { editingScore = it }, onExplore = { exploring = it },
                        bottomPadding = bottom,
                    )
                    Tab.HISTORY -> HistoryScreen(state, settings, overrides, onOpenDay = { openDay = it }, bottomPadding = bottom)
                    Tab.SETTINGS -> SettingsScreen(
                        settings, vm::updateSettings, actions, state.access, state.today?.latestDataAt,
                        onShowSyncLog = { showSyncLog = true },
                        onOpenGoogleHealth = { showGoogleHealth = true },
                        onOpenDiagnostics = { showDiagnostics = true },
                        googleError = googleError,
                        bottomPadding = bottom,
                    )
                }
            }
        }
        // A day opened from History slides in over everything but the tab bar.
        AnimatedVisibility(
            visible = openDay != null,
            enter = slideInHorizontally(tween(420, easing = Glide)) { it } + fadeIn(tween(250)),
            exit = slideOutHorizontally(tween(320, easing = Glide)) { it } + fadeOut(tween(200)),
        ) {
            // Hold on to the last day while it slides away.
            val day = remember { mutableStateOf(openDay) }.apply { if (openDay != null) value = openDay }.value
            Box(Modifier.fillMaxSize()) {
                Backdrop()
                if (day != null) {
                    DayDetail(
                        day, settings, overrides,
                        loadHourly = vm::hourly,
                        onAdjust = { adjusting = it },
                        onBack = { openDay = null },
                        bottomPadding = bottom,
                        night = nightBefore(day.window),
                        nightScore = nightBefore(day.window)?.let { scores[it.wakeDate()] },
                        onExploreNight = { exploring = it },
                    )
                }
            }
        }
        // A night opened from the Sleep tab slides in the same way.
        AnimatedVisibility(
            visible = openNight != null,
            enter = slideInHorizontally(tween(420, easing = Glide)) { it } + fadeIn(tween(250)),
            exit = slideOutHorizontally(tween(320, easing = Glide)) { it } + fadeOut(tween(200)),
        ) {
            val night = remember { mutableStateOf(openNight) }.apply { if (openNight != null) value = openNight }.value
            Box(Modifier.fillMaxSize()) {
                Backdrop()
                if (night != null) {
                    NightDetail(
                        night, scores[night.wakeDate()], settings.restlessAsAwake,
                        onEditScore = { editingScore = night }, onExplore = { exploring = it }, onBack = { openNight = null },
                        bottomPadding = bottom,
                    )
                }
            }
        }
        // A night opened up grows out of its chart, over whatever it was opened from.
        AnimatedVisibility(
            visible = exploring != null,
            enter = fadeIn(tween(260)) + scaleIn(tween(420, easing = Glide), initialScale = 0.92f),
            exit = fadeOut(tween(200)) + scaleOut(tween(260, easing = Glide), targetScale = 0.96f),
        ) {
            val night = remember { mutableStateOf(exploring) }.apply { if (exploring != null) value = exploring }.value
            Box(Modifier.fillMaxSize()) {
                Backdrop()
                if (night != null) NightExplorer(night, settings.restlessAsAwake, onBack = { exploring = null }, bottomPadding = bottom)
            }
        }
        // Content fades out under the tab bar instead of running into it.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(bottom)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Palette.background.copy(alpha = 0.94f)))),
        )
        TabBar(tab, onSelect = { openDay = null; openNight = null; exploring = null; tab = it }, modifier = Modifier.align(Alignment.BottomCenter))
        adjusting?.let { day -> AdjustDaySheet(day, overrides, vm, onDone = { adjusting = null }) }
        if (showSyncLog) SyncLogSheet(syncLog, onDone = { showSyncLog = false })
        if (showDiagnostics) DiagnosticsSheet(onDone = { showDiagnostics = false })
        editingScore?.let { night ->
            ScoreSheet(
                night, scores[night.wakeDate()],
                onSave = { vm.setScore(night, it) },
                onClear = { vm.setScore(night, null) },
                onDone = { editingScore = null },
            )
        }
        if (showGoogleHealth) {
            GoogleHealthSheet(
                connected = settings.googleHealth,
                problem = googleMessage ?: googleError.takeIf { settings.googleHealth },
                busy = googleBusy,
                onConnect = {
                    vm.connectGoogleHealth { consentLauncher.launch(IntentSenderRequest.Builder(it).build()) }
                },
                onDisconnect = vm::disconnectGoogleHealth,
                onDone = { showGoogleHealth = false },
            )
        }
    }
}

/** A floating glass pill: the current tab grows to show its name, the others just their icon. */
@Composable
internal fun TabBar(current: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .navigationBarsPadding()
            .padding(bottom = 16.dp)
            .clip(CircleShape)
            .background(Palette.cardHigh.copy(alpha = 0.96f))
            .border(1.dp, glassEdge(), CircleShape)
            .padding(6.dp)
            .animateContentSize(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Tab.entries.forEach { t ->
            val on = t == current
            val color by animateColorAsState(if (on) Palette.accent else Palette.muted, label = "tabColor")
            val fill by animateColorAsState(if (on) Palette.accent.copy(alpha = 0.15f) else Color.Transparent, label = "tabFill")
            val source = remember { MutableInteractionSource() }
            Row(
                Modifier
                    .pressScale(source, pressed = 0.9f)
                    .clip(CircleShape)
                    .background(fill)
                    .clickable(source, indication = null) { onSelect(t) }
                    .padding(horizontal = if (on) 18.dp else 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val icon = when (t) {
                    Tab.TODAY -> painterResource(R.drawable.ic_steps)
                    Tab.SLEEP -> painterResource(R.drawable.ic_moon)
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
    /** Asks for the phone's step counter ("physical activity"). */
    val requestLiveSteps: () -> Unit = {},
)
