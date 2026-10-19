package app.wakesteps.widget

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant

/**
 * The widget shows the tracker's total plus the steps the phone counted since the tracker's newest
 * data, and corrects to the tracker's count when its next batch arrives.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = RenderTestApp::class)
class LiveStepsTest {

    private val app = ApplicationProvider.getApplicationContext<WakeStepsApp>()
    private val now = Instant.now()
    private fun ago(minutes: Long) = now.minus(Duration.ofMinutes(minutes))

    private fun snapshot(steps: Long, latest: Instant, asleep: Boolean = false) = Snapshot(
        totals = DayTotals(steps, 900.0, 3000.0),
        dayStart = ago(4 * 60),
        startReason = StartReason.WOKE_UP,
        asleep = asleep,
        updatedAt = now,
        latestDataAt = latest,
        error = null,
    )

    /**
     * A steady walk as the phone counted it, a reading every 10 s at 100 steps a minute, from 20
     * minutes ago to a minute ago: 2,000 on the counter 10 minutes ago, 2,900 at the end.
     */
    private fun walk() {
        for (s in 0L..19 * 60L step 10) app.phoneSteps.record(ago(20).plusSeconds(s), 1000 + s * 100 / 60)
    }

    @Test
    fun addsThePhonesStepsSinceTheTrackersNewestData() = runBlocking {
        walk()
        app.prefs.saveSnapshot(snapshot(steps = 4321, latest = ago(10)))
        assertEquals(900L, app.phoneSteps.extraFor(app.prefs.currentSnapshot()))
        assertTrue(shows("5,221"))
    }

    @Test
    fun correctsToTheTrackerWhenItsDataArrives() = runBlocking {
        walk()
        app.prefs.saveSnapshot(snapshot(steps = 4321, latest = ago(10)))
        assertTrue(shows("5,221"))
        // The tracker synced: it counted 880 of those steps (the phone said 900), up to a minute ago.
        app.prefs.saveSnapshot(snapshot(steps = 5201, latest = ago(1)))
        assertEquals(0L, app.phoneSteps.extraFor(app.prefs.currentSnapshot()))
        assertTrue(shows("5,201"))
    }

    @Test
    fun addsNothingOnceTheDayHasEnded() = runBlocking {
        walk()
        app.prefs.saveSnapshot(snapshot(steps = 4321, latest = ago(10), asleep = true))
        assertTrue(shows("4,321"))
    }

    @Test
    fun aFewStepsAroundTheHouseAddNothing() = runBlocking {
        // The band last synced half an hour ago; then 14 steps to the bathroom, just now.
        for (s in 0L..14) app.phoneSteps.record(ago(1).plusSeconds(s), 500 + s)
        app.prefs.saveSnapshot(snapshot(steps = 4321, latest = ago(30)))
        assertEquals(0L, app.phoneSteps.extraFor(app.prefs.currentSnapshot()))
        assertTrue(shows("4,321"))
    }

    @Test
    fun aRealWalkKicksInAndStaysUntilTheBandSyncs() = runBlocking {
        // 40 steps over half a minute: clearly walking.
        for (s in 0L..40) app.phoneSteps.record(ago(1).plusSeconds(s * 30 / 40), 500 + s)
        app.prefs.saveSnapshot(snapshot(steps = 4321, latest = ago(30)))
        assertEquals(40L, app.phoneSteps.extraFor(app.prefs.currentSnapshot()))
        assertTrue(shows("4,361"))
        // Stopped a while ago: the walk's steps stay up rather than vanishing before the sync.
        assertEquals(40L, app.phoneSteps.extraFor(app.prefs.currentSnapshot(), now.plus(Duration.ofMinutes(10))))
    }

    @Test
    fun withoutReadingsItShowsTheTrackerAsIs() = runBlocking {
        app.prefs.saveSnapshot(snapshot(steps = 4321, latest = ago(10)))
        assertTrue(shows("4,321"))
    }

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    private suspend fun shows(text: String): Boolean {
        val view = StepsWidget().compose(app, size = DpSize(167.dp, 190.dp)).apply(app, FrameLayout(app))
        return texts(view).any { it == text }
    }

    private fun texts(v: View): List<String> = when (v) {
        is TextView -> listOf(v.text.toString())
        is ViewGroup -> (0 until v.childCount).flatMap { texts(v.getChildAt(it)) }
        else -> emptyList()
    }
}
