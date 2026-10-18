package app.wakesteps.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.wakesteps.core.DayWindow
import app.wakesteps.core.EndReason
import app.wakesteps.core.NightWindow
import app.wakesteps.core.Overrides
import app.wakesteps.core.SleepSpan
import app.wakesteps.core.StartReason
import app.wakesteps.data.AppSettings
import app.wakesteps.data.DaySummary
import app.wakesteps.data.DayTotals
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

    @Test
    fun renderScreens() {
        for (style in WidgetStyle.entries) {
            val settings = AppSettings(widgetStyle = style)
            val tag = style.name.lowercase()
            val state = UiState(access = Access.Ready(true, true, false), today = today(6432), history = history())
            render("today_$tag", settings, Tab.TODAY) {
                TodayScreen(state, settings, actions, {}, {}, bottom)
            }
            render("goal_$tag", settings, Tab.TODAY) {
                TodayScreen(state.copy(today = today(13400)), settings, actions, {}, {}, bottom)
            }
            render("history_$tag", settings, Tab.HISTORY) {
                HistoryScreen(state, settings, Overrides.NONE, {}, bottom)
            }
            render("settings_$tag", settings, Tab.SETTINGS) {
                SettingsScreen(settings, {}, actions, {}, bottom)
            }
        }
    }

    private fun render(name: String, settings: AppSettings, tab: Tab, screen: @Composable () -> Unit) {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            WakeStepsTheme(settings.widgetStyle) {
                Box(Modifier.fillMaxSize().background(Palette.background)) {
                    screen()
                    TabBar(tab, onSelect = {}, modifier = Modifier.align(Alignment.BottomCenter))
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
