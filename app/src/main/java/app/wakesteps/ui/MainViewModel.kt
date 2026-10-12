package app.wakesteps.ui

import android.app.Application
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.wakesteps.app
import app.wakesteps.core.DayWindow
import app.wakesteps.core.Overrides
import app.wakesteps.core.SleepSpan
import app.wakesteps.data.AppSettings
import app.wakesteps.data.CalendarDay
import app.wakesteps.data.DaySummary
import app.wakesteps.data.HealthSource
import app.wakesteps.data.SyncEvent
import app.wakesteps.data.TodaySummary
import app.wakesteps.sync.LiveModeService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    /** Midnight-to-midnight totals, for comparing with the Fitbit / Google app. */
    val calendar: List<CalendarDay> = emptyList(),
    val error: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.app
    private val health = app.health

    val settings: StateFlow<AppSettings> =
        app.prefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val overrides: StateFlow<Overrides> =
        app.prefs.overrides.stateIn(viewModelScope, SharingStarted.Eagerly, Overrides.NONE)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val syncLog: StateFlow<List<SyncEvent>> =
        app.prefs.syncLog.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var reloadJob: Job? = null

    init {
        // The live service refreshes the widget in the background; when it finds new data
        // while the app is open, update Today too (without redoing history).
        viewModelScope.launch {
            app.prefs.snapshot
                .map { it?.updatedAt }
                .distinctUntilChanged()
                .drop(1)
                .collect { refreshToday() }
        }
    }

    private fun refreshToday() {
        if (reloadJob?.isActive == true || _state.value.access !is Access.Ready) return
        viewModelScope.launch {
            runCatching { app.engine.today(app.prefs.currentSettings(), includeHourly = true) }
                .onSuccess { today -> _state.update { it.copy(today = today) } }
        }
    }

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
                val calendar = runCatching { app.engine.calendarDays(s) }.getOrDefault(emptyList())
                _state.update { it.copy(history = history, calendar = calendar, loading = false) }
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
            app.prefs.update(transform)
            reload()
        }
    }

    // ---------------------------------------------------------------- judgement calls

    /** Count [sleep] as main sleep (starts a new day) or as a nap. */
    fun setSleepCall(sleep: SleepSpan, main: Boolean) = adjust { app.prefs.setSleepCall(sleep, main) }

    fun clearSleepCall(sleep: SleepSpan) = adjust { app.prefs.clearSleepCall(sleep) }

    /** Keep going past an all-nighter reset instead of starting a new day there. */
    fun setResetSkipped(at: Instant, skipped: Boolean) = adjust { app.prefs.setResetSkipped(at, skipped) }

    fun setManualStart(at: Instant, present: Boolean) = adjust { app.prefs.setManualStart(at, present) }

    /** Undo every call made within [day], including on the sleep that started it. */
    fun resetDay(day: DayWindow) = adjust {
        val from = day.wokeFrom?.start ?: day.start
        app.prefs.clearOverrides(from, day.end ?: Instant.now())
    }

    private fun adjust(change: suspend () -> Unit) {
        viewModelScope.launch {
            change()
            reload()
        }
    }

    fun permissionsToRequest(): Set<String> = HealthSource.CORE_PERMISSIONS + HealthSource.EXTRA_PERMISSIONS
    fun backgroundPermission(): Set<String> = setOf(HealthSource.BACKGROUND_PERMISSION)
}
