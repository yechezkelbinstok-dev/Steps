package app.wakesteps.widget

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.compose
import androidx.test.core.app.ApplicationProvider
import app.wakesteps.WakeStepsApp
import app.wakesteps.core.StartReason
import app.wakesteps.data.DayTotals
import app.wakesteps.data.Snapshot
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import kotlin.math.roundToInt

/** The widget's refresh button: where it shows, the spinner while it works, and the refresh itself. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = RenderTestApp::class)
class WidgetRefreshTest {

    private val app = ApplicationProvider.getApplicationContext<WakeStepsApp>()

    @Before
    fun snapshot() = runBlocking {
        app.prefs.saveSnapshot(
            Snapshot(
                totals = DayTotals(4321, 900.0, 3000.0),
                dayStart = Instant.now().minusSeconds(4 * 3600),
                startReason = StartReason.WOKE_UP,
                asleep = false,
                updatedAt = Instant.now(),
                latestDataAt = Instant.now(),
                error = null,
            )
        )
    }

    @After
    fun reset() {
        WidgetRefresh.running.value = false
    }

    @Test
    fun buttonTurnsIntoSpinnerWhileRefreshing() = runBlocking {
        var view = render(DpSize(167.dp, 190.dp))
        assertNotNull("refresh button", find(view) { it.contentDescription == "Refresh" })
        assertNull("no spinner", find(view) { it is ProgressBar })

        WidgetRefresh.running.value = true
        view = render(DpSize(167.dp, 190.dp))
        val spinner = find(view) { it is ProgressBar } as ProgressBar?
        assertNotNull("spinner", spinner)
        assertTrue("it spins", spinner!!.isIndeterminate)
        val density = app.resources.displayMetrics.density
        assertEquals("the button's size", (16 * density).roundToInt(), spinner.width)
        assertNotNull(find(view) { it.contentDescription == "Refreshing" })
        assertTrue("the numbers stay up meanwhile", texts(view).any { it.contains("4,321") })
    }

    @Test
    fun buttonOnlyWhereItFits() = runBlocking {
        for (size in listOf(DpSize(167.dp, 190.dp), DpSize(350.dp, 190.dp), DpSize(350.dp, 290.dp), DpSize(350.dp, 90.dp))) {
            assertNotNull("button at $size", find(render(size)) { it.contentDescription == "Refresh" })
        }
        // The 2×1 strip, and a 4×1 squeezed narrow, have no room for it.
        for (size in listOf(DpSize(170.dp, 80.dp), DpSize(270.dp, 90.dp))) {
            assertNull("no button at $size", find(render(size)) { it.contentDescription == "Refresh" })
        }
    }

    @Test(timeout = 30_000)
    fun refreshRunsOnceAndPutsTheButtonBack() = runBlocking {
        val job = WidgetRefresh.start(app)
        assertNotNull(job)
        assertTrue(WidgetRefresh.running.value)
        assertNull("a second tap while it runs does nothing", WidgetRefresh.start(app))
        job!!.join()

        assertFalse(WidgetRefresh.running.value)
        // It really refreshed: Health Connect isn't there under Robolectric, and it says so.
        assertNotNull(app.prefs.currentSnapshot()?.error)
        val view = render(DpSize(167.dp, 190.dp))
        assertNotNull(find(view) { it.contentDescription == "Refresh" })
        assertNull(find(view) { it is ProgressBar })
    }

    /** The widget as composed for [size] (what the launcher reports), inflated and laid out. */
    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    private suspend fun render(size: DpSize): View {
        val view = StepsWidget().compose(app, size = size).apply(app, FrameLayout(app))
        val density = app.resources.displayMetrics.density
        val w = (size.width.value * density).roundToInt()
        val h = (size.height.value * density).roundToInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, w, h)
        return view
    }

    private fun find(v: View, test: (View) -> Boolean): View? = when {
        test(v) -> v
        v is ViewGroup -> (0 until v.childCount).firstNotNullOfOrNull { find(v.getChildAt(it), test) }
        else -> null
    }

    private fun texts(v: View): List<String> = when (v) {
        is TextView -> listOf(v.text.toString())
        is ViewGroup -> (0 until v.childCount).flatMap { texts(v.getChildAt(it)) }
        else -> emptyList()
    }
}
