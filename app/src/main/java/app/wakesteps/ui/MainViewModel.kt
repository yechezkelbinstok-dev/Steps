package app.wakesteps.ui

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.wakesteps.R
import app.wakesteps.app
import app.wakesteps.core.DayWindow
import app.wakesteps.core.NightScore
import app.wakesteps.core.Overrides
import app.wakesteps.core.SleepNight
import app.wakesteps.core.SleepScore
import app.wakesteps.core.SleepSpan
import app.wakesteps.core.SoundRules
import app.wakesteps.data.AppSettings
import app.wakesteps.data.CalendarDay
import app.wakesteps.data.DaySummary
import app.wakesteps.data.GoogleHealth
import app.wakesteps.data.HealthSource
import app.wakesteps.data.NightScores
import app.wakesteps.data.SyncEvent
import app.wakesteps.data.TodaySummary
import app.wakesteps.data.wakeDate
import app.wakesteps.sync.LiveModeService
import app.wakesteps.widget.WidgetPusher
import com.google.android.gms.auth.api.identity.AuthorizationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

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
    /** The night each day in [history] began with, newest first (days after an all-nighter have none). */
    val nights: List<SleepNight> = emptyList(),
    /** Whether [nights] has been read at least once (until then, the Sleep tab is loading). */
    val nightsLoaded: Boolean = false,
    val error: String? = null,
    /** Offer the phone's step counter: it has one, and the user hasn't answered yet. */
    val liveStepsPrompt: Boolean = false,
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

    /** Each loaded night's sleep score (entered, or estimated), by the morning it ended. */
    val scores: StateFlow<Map<LocalDate, NightScore>> =
        combine(_state.map { it.nights }.distinctUntilChanged(), app.prefs.sleepScores) { nights, entries -> NightScores.of(nights, entries) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Saves the score Google Health gave [night] (or with null, removes it, back to the estimate). */
    fun setScore(night: SleepNight, score: Int?) {
        viewModelScope.launch { app.prefs.setSleepScore(night.wakeDate(), score, SleepScore.estimate(night)) }
    }

    /** Steps the phone counted since the tracker's newest data, shown on top of its total. */
    val liveSteps: StateFlow<Long> =
        combine(app.prefs.snapshot, app.phoneSteps.latest) { snapshot, _ -> app.phoneSteps.extraFor(snapshot) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

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

    /** Whether this run of the app has looked up the older nights that scores were entered for. */
    private var olderNightsRead = false

    fun reload() {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val access = checkAccess()
            _state.update { it.copy(access = access, liveStepsPrompt = liveStepsPrompt()) }
            if (access !is Access.Ready) {
                _state.update { it.copy(loading = false) }
                return@launch
            }
            val s = showingErrors {
                app.prefs.currentSettings().also {
                    // The system may have killed the live service; we're in the foreground, so restart it.
                    if (it.liveMode) LiveModeService.start(app)
                }
            }
            if (s == null) {
                _state.update { it.copy(loading = false) }
                return@launch
            }
            // Today, the days and the nights load side by side, each showing as soon as it's in. The
            // nights only need the days' sleep, not their steps, so they don't wait for those.
            val today = launch {
                showingErrors {
                    val today = app.engine.today(s, includeHourly = true)
                    _state.update { it.copy(today = today) }
                    // We're in the foreground, so this read always works: push it to the widget too.
                    app.engine.refresh(today)
                }
            }
            val windows = showingErrors { app.engine.dayWindows(s) }
            launch {
                if (windows != null) showingErrors { loadNights(s, windows) }
                _state.update { it.copy(nightsLoaded = true) } // even if that failed: show what there is
            }
            if (windows != null) {
                showingErrors {
                    val history = app.engine.history(s, windows)
                    val calendar = runCatching { app.engine.calendarDays(s) }.getOrDefault(emptyList())
                    _state.update { it.copy(history = history, calendar = calendar) }
                }
            }
            today.join()
            _state.update { it.copy(loading = false) }
        }
    }

    /** Runs [block]; a failure (but not a newer reload replacing this one) shows as the error, and gives null. */
    private suspend fun <T> showingErrors(block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(error = e.message ?: e.javaClass.simpleName) }
            null
        }

    /** The nights [windows] began with, then the sleep scores learn from them. */
    private suspend fun loadNights(s: AppSettings, windows: List<DayWindow>) {
        app.prefs.seedSleepScores()
        app.prefs.forgetEstimatesUnless(SleepScore.MODEL)
        val read = app.engine.nights(s, windows)
        _state.update { it.copy(nights = read, nightsLoaded = true) }
        // Sound sleep from heart rate fills in once it's read (a read for each night not yet worked out).
        _googleHeart.value = app.engine.heartAllowed(s)
        // What counts as sound is learnt from Google's own figures, once.
        val rules = if (_googleHeart.value) app.engine.soundRules(s, read.associateBy { it.wakeDate() }) else SoundRules.DEFAULT
        val nights = app.engine.withSoundSleep(s, read, rules)
        if (nights != read) _state.update { it.copy(nights = nights) }
        // Keep each estimate beside any score entered for it, so the learning outlives the 2 weeks shown.
        app.prefs.noteEstimates(nights.mapNotNull { n -> SleepScore.estimate(n)?.let { n.wakeDate() to it } }.toMap())
        // Scores entered for older nights (sent over from Google Health): read those nights once to learn from.
        if (!olderNightsRead) {
            val shown = nights.map { it.wakeDate() }.toSet()
            val older = app.prefs.sleepScores.first().values.filter { it.estimate == null && it.date !in shown }.map { it.date }.toSet()
            if (older.isNotEmpty()) {
                val found = app.engine.nightsEndingOn(s, older).entries.toList()
                val measured = app.engine.withSoundSleep(s, found.map { it.value }, rules)
                app.prefs.noteEstimates(
                    found.zip(measured).mapNotNull { (e, n) -> SleepScore.estimate(n)?.let { e.key to it } }.toMap()
                )
            }
            olderNightsRead = true
        }
    }

    /** Starts the phone's step counter if allowed; otherwise whether to offer it. */
    private suspend fun liveStepsPrompt(): Boolean {
        val phone = app.phoneSteps
        if (phone.hasPermission()) {
            phone.start()
            return false
        }
        return phone.available && !app.prefs.liveStepsDeclined()
    }

    /** The answer to Android's "physical activity" question. */
    fun liveStepsAnswered(granted: Boolean) {
        viewModelScope.launch {
            if (granted) app.phoneSteps.start() else app.prefs.declineLiveSteps()
            _state.update { it.copy(liveStepsPrompt = false) }
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
            // Redraw the widget right away (new goal or style), without waiting on a data read.
            WidgetPusher.pushAll(app)
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

    // ---------------------------------------------------------------- Google Health

    /** Why the last Google Health read failed, from the background refreshes. */
    val googleHealthError: StateFlow<String?> =
        app.prefs.googleHealthError.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _googleMessage = MutableStateFlow<String?>(null)
    /** What went wrong connecting just now, if anything. */
    val googleMessage: StateFlow<String?> = _googleMessage.asStateFlow()

    private val _googleBusy = MutableStateFlow(false)
    val googleBusy: StateFlow<Boolean> = _googleBusy.asStateFlow()

    private val _googleHeart = MutableStateFlow(false)
    /** Whether heart rate can be read, so sound sleep is measured the way Google measures it. */
    val googleHeart: StateFlow<Boolean> = _googleHeart.asStateFlow()

    /** Asks Google for heart rate; [showConsent] launches Google's consent screen when one is needed. */
    fun allowHeartRate(showConsent: (PendingIntent) -> Unit) {
        viewModelScope.launch {
            _googleBusy.value = true
            _googleMessage.value = null
            try {
                val result = app.googleHealth.authorizeHeart()
                val consent = result.pendingIntent
                if (result.hasResolution() && consent != null) showConsent(consent) else finishHeartRate(result)
            } catch (e: Exception) {
                _googleMessage.value = e.message
            } finally {
                _googleBusy.value = false
            }
        }
    }

    /** Back from Google's consent screen for heart rate. */
    fun heartConsentResult(data: Intent?) {
        viewModelScope.launch {
            val result = runCatching { app.googleHealth.resultFrom(data) }.getOrNull()
            if (result == null) _googleMessage.value = app.getString(R.string.gh_cancelled) else finishHeartRate(result)
        }
    }

    private fun finishHeartRate(result: AuthorizationResult) {
        if (result.accessToken == null || result.grantedScopes.none { it == GoogleHealth.SCOPE_HEALTH }) {
            _googleMessage.value = app.getString(R.string.gh_no_heart)
            return
        }
        app.googleHealth.keepHeartToken(result)
        _googleHeart.value = true
        _googleMessage.value = null
        reload()
    }

    /** Asks Google for access; [showConsent] launches Google's consent screen when one is needed. */
    fun connectGoogleHealth(showConsent: (PendingIntent) -> Unit) {
        viewModelScope.launch {
            _googleBusy.value = true
            _googleMessage.value = null
            try {
                val result = app.googleHealth.authorize()
                val consent = result.pendingIntent
                if (result.hasResolution() && consent != null) showConsent(consent) else finishGoogleHealth(result)
            } catch (e: Exception) {
                _googleMessage.value = e.message
            } finally {
                _googleBusy.value = false
            }
        }
    }

    /** Back from Google's consent screen. */
    fun googleConsentResult(data: Intent?) {
        viewModelScope.launch {
            val result = runCatching { app.googleHealth.resultFrom(data) }.getOrNull()
            if (result == null) _googleMessage.value = app.getString(R.string.gh_cancelled) else finishGoogleHealth(result)
        }
    }

    private suspend fun finishGoogleHealth(result: AuthorizationResult) {
        val token = result.accessToken
        if (token == null || result.grantedScopes.none { it == GoogleHealth.SCOPE_ACTIVITY }) {
            _googleMessage.value = app.getString(R.string.gh_no_access)
            return
        }
        try {
            app.googleHealth.checkLinked(token)
        } catch (e: Exception) {
            _googleMessage.value = e.message
            return
        }
        app.prefs.update { it.copy(googleHealth = true) }
        app.prefs.setGoogleHealthError(null)
        _googleMessage.value = null
        reload()
    }

    fun disconnectGoogleHealth() {
        viewModelScope.launch {
            app.prefs.update { it.copy(googleHealth = false) }
            app.prefs.setGoogleHealthError(null)
            app.googleHealth.forgetToken()
            reload()
        }
    }

    /** A day's steps minute by minute, for zooming into. */
    suspend fun minuteSteps(window: DayWindow): List<Pair<Instant, Long>> = app.engine.minuteSteps(window, settings.value)

    /** Hour-by-hour steps for a day opened from History. */
    suspend fun hourly(window: DayWindow): List<Pair<Instant, Long>> = app.engine.hourly(window, settings.value)

    fun permissionsToRequest(): Set<String> = HealthSource.CORE_PERMISSIONS + HealthSource.EXTRA_PERMISSIONS
    fun backgroundPermission(): Set<String> = setOf(HealthSource.BACKGROUND_PERMISSION)
}
