package app.wakesteps.ui

import android.app.Application
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.wakesteps.app
import app.wakesteps.data.AppSettings
import app.wakesteps.data.DaySummary
import app.wakesteps.data.HealthSource
import app.wakesteps.data.TodaySummary
import app.wakesteps.sync.LiveModeService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

sealed interface Access {
    data object Checking : Access
    data class Unavailable(val updateRequired: Boolean) : Access
    data object NeedsPermission : Access
    data class Ready(
        val backgroundSupported: Boolean,
        val backgroundGranted: Boolean,
        /** None of calories/distance granted yet (e.g. upgraded from a version without them). */
        val extrasMissing: Boolean,
    ) : Access
}

data class UiState(
    val access: Access = Access.Checking,
    val loading: Boolean = false,
    val today: TodaySummary? = null,
    val history: List<DaySummary> = emptyList(),
    val sources: List<String> = emptyList(),
    val error: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.app
    private val health = app.health

    val settings: StateFlow<AppSettings> =
        app.prefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var reloadJob: Job? = null

    fun reload() {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val access = checkAccess()
            _state.update { it.copy(access = access) }
            if (access !is Access.Ready) {
                _state.update { it.copy(loading = false) }
                return@launch
            }
            try {
                val s = app.prefs.currentSettings()
                // The system may have killed the live service; we're in the foreground, so restart it.
                if (s.liveMode) LiveModeService.start(app)
                val today = app.engine.today(s, includeHourly = true)
                _state.update { it.copy(today = today) }
                // We're in the foreground, so this read always works: push it to the widget too.
                app.engine.refresh(today)
                val history = app.engine.history(s)
                val now = Instant.now()
                val sources = runCatching { health.recentStepSources(now.minus(Duration.ofDays(2)), now) }
                    .getOrDefault(emptyList())
                _state.update { it.copy(history = history, sources = sources, loading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    private suspend fun checkAccess(): Access {
        return when (health.sdkStatus()) {
            HealthConnectClient.SDK_AVAILABLE -> {
                val granted = health.grantedPermissions()
                if (!granted.containsAll(HealthSource.CORE_PERMISSIONS)) return Access.NeedsPermission
                Access.Ready(
                    backgroundSupported = runCatching { health.backgroundReadSupported() }.getOrDefault(false),
                    backgroundGranted = HealthSource.BACKGROUND_PERMISSION in granted,
                    extrasMissing = HealthSource.EXTRA_PERMISSIONS.none { it in granted },
                )
            }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Access.Unavailable(updateRequired = true)
            else -> Access.Unavailable(updateRequired = false)
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            val before = app.prefs.currentSettings()
            app.prefs.update(transform)
            val after = app.prefs.currentSettings()
            if (after.liveMode != before.liveMode) {
                if (after.liveMode) LiveModeService.start(app) else LiveModeService.stop(app)
            }
            if (after.copy(liveMode = before.liveMode) != before) reload()
        }
    }

    fun permissionsToRequest(): Set<String> = HealthSource.CORE_PERMISSIONS + HealthSource.EXTRA_PERMISSIONS
    fun backgroundPermission(): Set<String> = setOf(HealthSource.BACKGROUND_PERMISSION)
}
