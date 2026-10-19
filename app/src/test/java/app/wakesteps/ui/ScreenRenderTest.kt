package app.wakesteps.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.wakesteps.core.DayWindow
import app.wakesteps.core.NightScore
import app.wakesteps.core.SleepScore
import app.wakesteps.core.EndReason
import app.wakesteps.core.NightWindow
import app.wakesteps.core.Overrides
import app.wakesteps.core.SleepNight
import app.wakesteps.core.SleepNights
import app.wakesteps.core.SleepSession
import app.wakesteps.core.SleepSpan
import app.wakesteps.core.SleepStage
import app.wakesteps.core.StageSpan
import app.wakesteps.core.StartReason
import app.wakesteps.data.AppSettings
import app.wakesteps.data.DaySummary
import app.wakesteps.data.DayTotals
import app.wakesteps.data.NightScores
import app.wakesteps.data.ScoreEntry
import app.wakesteps.data.wakeDate
import app.wakesteps.data.TodaySummary
import app.wakesteps.data.WidgetStyle
import app.wakesteps.ui.theme.Palette
import app.wakesteps.ui.theme.WakeStepsTheme
import app.wakesteps.widget.RenderTestApp
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Renders the app's screens with made-up data to PNGs under build/screen-renders, so the design
 * can be checked without a phone. Not an assertion test.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = RenderTestApp::class, qualifiers = "w411dp-h891dp-420dpi")
class ScreenRenderTest {
    private val now = Instant.parse("2026-10-17T15:42:00Z")
    private val actions = Actions({}, {}, {}, {})
    private val bottom = 104.dp

    private fun hours(h: Double) = Duration.ofMinutes((h * 60).toLong())

    private fun today(steps: Long, startHoursAgo: Double = 8.5) = TodaySummary(
        window = DayWindow(
            start = now.minus(hours(startHoursAgo)),
            end = null,
            startReason = StartReason.WOKE_UP,
            endReason = null,
            wokeFrom = SleepSpan(now.minus(hours(startHoursAgo + 7.5)), now.minus(hours(startHoursAgo))),
        ),
        night = NightWindow(LocalTime.of(3, 0), learned = true),
        totals = DayTotals(steps, calories = 1684.0, distanceMeters = steps * 0.78),
        hourly = listOf(120L, 640, 1210, 380, 90, 0, 820, 1460, 980, 732)
            .mapIndexed { i, v -> now.minus(hours(startHoursAgo)).plus(Duration.ofHours(i.toLong())) to v * steps / 6432 },
        latestDataAt = now.minus(Duration.ofMinutes(6)),
        now = now,
    )

    private fun history(): List<DaySummary> {
        val steps = listOf(6432L, 11250, 8120, 14800, 4300, 10400, 9050, 7600, 12100, 5200)
        return steps.mapIndexed { i, s ->
            val start = now.minus(hours(8.5)).minus(Duration.ofDays(i.toLong()))
            val end = if (i == 0) null else start.plus(hours(16.5))
            DaySummary(
                DayWindow(
                    start = start,
                    end = end,
                    startReason = if (i == 4) StartReason.FALLBACK else StartReason.WOKE_UP,
                    endReason = if (i == 0) null else EndReason.WENT_TO_SLEEP,
                    naps = if (i == 2) listOf(SleepSpan(start.plus(hours(7.0)), start.plus(hours(7.6)))) else emptyList(),
                ),
                DayTotals(s, calories = 1500.0 + s / 10.0, distanceMeters = s * 0.78),
            )
        }
    }

    /**
     * A believable night: drifting off, then 90-minute cycles with deep sleep early and REM late,
     * a wake-up or two, and a few brief restless moments.
     */
    private fun night(span: SleepSpan, variant: Int): SleepNight {
        val stages = mutableListOf<StageSpan>()
        var t = span.start
        fun add(minutes: Long, stage: SleepStage) {
            val end = minOf(t.plus(Duration.ofMinutes(minutes)), span.end)
            if (end > t) stages += StageSpan(t, end, stage)
            t = end
        }
        add(10L + variant * 4, SleepStage.AWAKE)
        val cycles = listOf(
            listOf(20L to SleepStage.LIGHT, 48L to SleepStage.DEEP, 14L to SleepStage.LIGHT, 10L to SleepStage.REM),
            listOf(24L to SleepStage.LIGHT, 36L to SleepStage.DEEP, 16L to SleepStage.LIGHT, 18L to SleepStage.REM),
            listOf(30L to SleepStage.LIGHT, 5L to SleepStage.AWAKE, 18L to SleepStage.DEEP, 20L to SleepStage.LIGHT, 24L to SleepStage.REM),
            listOf(36L to SleepStage.LIGHT, 8L to SleepStage.DEEP, 24L to SleepStage.LIGHT, 30L to SleepStage.REM),
            listOf(38L to SleepStage.LIGHT, (4L + variant * 3) to SleepStage.AWAKE, 26L to SleepStage.LIGHT, 34L to SleepStage.REM),
        )
        for (cycle in cycles) for ((minutes, stage) in cycle) add(minutes, stage)
        val rest = Duration.between(t, span.end).toMinutes()
        if (rest > 6) add(rest - 6, SleepStage.LIGHT)
        add(6, SleepStage.AWAKE)
        val restless = List(5 + variant * 2) { i ->
            val at = span.start.plus(Duration.ofMinutes(50L + i * 47))
            StageSpan(at, at.plusSeconds(70), SleepStage.AWAKE)
        }
        return SleepNights.build(span, listOf(SleepSession(span.start, span.end, stages, restless)))
    }

    /** The night before each day in [history], newest first; none before the all-nighter. */
    private fun nights(): List<SleepNight> =
        listOf(7.5, 6.6, 8.1, 7.0, null, 7.8, 6.2, 7.4, 8.4, 6.9).mapIndexedNotNull { i, h ->
            if (h == null) return@mapIndexedNotNull null
            val wake = now.minus(hours(8.5)).minus(Duration.ofDays(i.toLong()))
            night(SleepSpan(wake.minus(hours(h)), wake), i % 3)
        }

    /** Google Health's scores for two of the nights, as if entered; the rest are estimates. */
    private fun scores(nights: List<SleepNight>): Map<LocalDate, NightScore> {
        val entered = listOf(1 to 71, 3 to 84).associate { (i, score) ->
            val n = nights[i]
            n.wakeDate() to ScoreEntry(n.wakeDate(), score, SleepScore.estimate(n))
        }
        return NightScores.of(nights, entered)
    }

    /** Every screen, in the default style. */
    @Test
    fun renderScreens() {
        val settings = AppSettings()
        val state = UiState(access = Access.Ready(true, true, false), today = today(6432), history = history())
        val nights = nights()
        val scores = scores(nights)
        render("today", settings, Tab.TODAY) {
            TodayScreen(state, settings, actions, {}, {}, bottom, lastNight = nights.first(), lastNightScore = scores[nights.first().wakeDate()])
        }
        render("today_live", settings, Tab.TODAY) { TodayScreen(state, settings, actions, {}, {}, bottom, liveSteps = 312) }
        render("today_prompt", settings, Tab.TODAY) {
            TodayScreen(state.copy(liveStepsPrompt = true), settings, actions, {}, {}, bottom)
        }
        render("goal", settings, Tab.TODAY) { TodayScreen(state.copy(today = today(13400)), settings, actions, {}, {}, bottom) }
        render("scrub", settings, Tab.TODAY) { TodayScreen(state, settings, actions, {}, {}, bottom, chartSelection = 7) }
        render("history", settings, Tab.HISTORY) { HistoryScreen(state, settings, Overrides.NONE, {}, bottom, chartSelection = 3) }
        render("day", settings, Tab.HISTORY) {
            DayDetail(history()[2], settings, Overrides.NONE, { today(8120).hourly }, {}, {}, bottom)
        }
        render("settings", settings, Tab.SETTINGS) {
            SettingsScreen(settings, {}, actions, state.access, now.minus(Duration.ofMinutes(6)), {}, {}, {}, null, bottom)
        }
    }

    /** Today in every ring style. */
    @Test
    fun renderStyles() {
        val state = UiState(access = Access.Ready(true, true, false), today = today(6432), history = history())
        val night = nights().first()
        for (style in WidgetStyle.entries) {
            val settings = AppSettings(widgetStyle = style)
            render("today_${style.name.lowercase()}", settings, Tab.TODAY) {
                TodayScreen(state, settings, actions, {}, {}, bottom, lastNight = night, lastNightScore = NightScore(91, fromGoogle = true))
            }
        }
    }

    /** The Sleep tab, a night's own page, and a day's page with its night, tall enough to see whole. */
    @Test
    @Config(qualifiers = "w411dp-h2400dp-420dpi")
    fun renderSleep() {
        val settings = AppSettings()
        val nights = nights()
        val scores = scores(nights)
        val state = UiState(access = Access.Ready(true, true, false), today = today(6432), history = history(), nights = nights, nightsLoaded = true)
        render("sleep", settings, Tab.SLEEP) { SleepScreen(state, scores, false, {}, {}, bottom) }
        render("sleep_touch", settings, Tab.SLEEP) { SleepScreen(state, scores, false, {}, {}, bottom, chartSelection = 4, chartMinute = 140) }
        render("sleep_restless_awake", settings, Tab.SLEEP) { SleepScreen(state, scores, true, {}, {}, bottom) }
        render("night", settings, Tab.SLEEP) { NightDetail(nights[3], scores[nights[3].wakeDate()], false, {}, {}, bottom) }
        render("day_night", settings, Tab.HISTORY) {
            DayDetail(
                history()[2], settings, Overrides.NONE, { today(8120).hourly }, {}, {}, bottom,
                night = nights[2], nightScore = scores[nights[2].wakeDate()],
            )
        }
        // A night the band recorded without stages.
        val plainSpan = SleepSpan(now.minus(hours(16.0)), now.minus(hours(8.5)))
        render("night_plain", settings, Tab.SLEEP) {
            NightDetail(SleepNights.build(plainSpan, listOf(SleepSession(plainSpan.start, plainSpan.end))), null, false, {}, {}, bottom)
        }
        render("sleep_empty", settings, Tab.SLEEP) { SleepScreen(state.copy(nights = emptyList()), emptyMap(), false, {}, {}, bottom) }
        render("score_sheet", settings, Tab.SLEEP) {
            Box(Modifier.fillMaxSize().background(Palette.cardHigh)) {
                Column(Modifier.padding(top = 48.dp)) { ScoreContent(nights[1], scores[nights[1].wakeDate()], {}, {}) }
            }
        }
    }

    /** A tall screen, to see a whole day page at once. */
    @Test
    @Config(qualifiers = "w411dp-h1500dp-420dpi")
    fun renderDayPage() {
        val settings = AppSettings()
        render("day_full", settings, Tab.HISTORY) {
            DayDetail(history()[2], settings, Overrides.NONE, { today(8120).hourly }, {}, {}, bottom)
        }
    }

    /** The Google Health setup sheet's content. */
    @Test
    @Config(qualifiers = "w411dp-h1300dp-420dpi")
    fun renderGoogleHealth() {
        render("google_health", AppSettings(), Tab.SETTINGS) {
            GoogleHealthContent(
                connected = false, problem = null, busy = false,
                sha1 = "B0:26:35:22:6D:29:49:EE:FA:30:07:C2:5B:2F:AC:1A:0F:37:87:D3",
                onConnect = {}, onDisconnect = {},
            )
        }
    }

    private fun render(name: String, settings: AppSettings, tab: Tab, screen: @Composable () -> Unit) {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            CompositionLocalProvider(LocalSnapAnimations provides true) {
                WakeStepsTheme(settings.widgetStyle) {
                    Box(Modifier.fillMaxSize()) {
                        Backdrop()
                        screen()
                        TabBar(tab, onSelect = {}, modifier = Modifier.align(Alignment.BottomCenter))
                    }
                }
            }
        }
        shadowOf(Looper.getMainLooper()).idle()
        val view: View = activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val out = File("build/screen-renders").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
