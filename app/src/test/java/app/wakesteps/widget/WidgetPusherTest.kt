package app.wakesteps.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import app.wakesteps.R
import app.wakesteps.WakeStepsApp
import app.wakesteps.core.StartReason
import app.wakesteps.data.DayTotals
import app.wakesteps.data.Snapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant

/** The direct (no WorkManager) redraw path puts the current numbers on a placed widget. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = RenderTestApp::class)
class WidgetPusherTest {

    @Test
    fun pushDrawsLatestSnapshotOnPlacedWidget() = runBlocking {
        org.robolectric.shadows.ShadowLog.stream = System.out // so a failed redraw shows why
        val app = ApplicationProvider.getApplicationContext<WakeStepsApp>()
        val shadow = shadowOf(AppWidgetManager.getInstance(app))
        // Place a widget the way a launcher would, with its provider info bound.
        val info = AppWidgetProviderInfo().apply {
            provider = ComponentName(app, StepsWidgetReceiver::class.java)
            initialLayout = R.layout.widget_preview
        }
        shadow.addInstalledProvider(info)
        val id = shadow.createWidget(StepsWidgetReceiver::class.java, R.layout.widget_preview)
        shadow.addBoundWidget(id, info)

        app.prefs.saveSnapshot(snapshot(steps = 4321))
        WidgetPusher.pushAll(app)
        assertTrue("widget shows 4,321", texts(shadow.getViewFor(id)).any { it.contains("4,321") })

        // A second push with newer data replaces it straight away.
        app.prefs.saveSnapshot(snapshot(steps = 5000))
        WidgetPusher.pushAll(app)
        assertTrue("widget shows 5,000", texts(shadow.getViewFor(id)).any { it.contains("5,000") })
    }

    private fun snapshot(steps: Long) = Snapshot(
        totals = DayTotals(steps, 900.0, 3000.0),
        dayStart = Instant.now().minusSeconds(4 * 3600),
        startReason = StartReason.WOKE_UP,
        asleep = false,
        updatedAt = Instant.now(),
        latestDataAt = Instant.now(),
        error = null,
    )

    private fun texts(v: View): List<String> = when (v) {
        is TextView -> listOf(v.text.toString())
        is ViewGroup -> (0 until v.childCount).flatMap { texts(v.getChildAt(it)) }
        else -> emptyList()
    }
}
