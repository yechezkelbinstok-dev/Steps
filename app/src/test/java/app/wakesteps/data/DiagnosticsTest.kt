package app.wakesteps.data

import androidx.test.core.app.ApplicationProvider
import app.wakesteps.WakeStepsApp
import app.wakesteps.core.StartReason
import app.wakesteps.widget.RenderTestApp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant

/**
 * The diagnostics report gathers without crashing even when a source is unreachable (as Health
 * Connect and Google Play are under Robolectric), and carries the sections we rely on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = RenderTestApp::class)
class DiagnosticsTest {

    private val app = ApplicationProvider.getApplicationContext<WakeStepsApp>()

    @Test
    fun reportHasEverySectionAndCurrentState() = runBlocking {
        app.prefs.saveSnapshot(
            Snapshot(
                totals = DayTotals(4321, 900.0, 3000.0),
                dayStart = Instant.now().minusSeconds(4 * 3600),
                startReason = StartReason.WOKE_UP,
                asleep = false,
                updatedAt = Instant.now(),
                latestDataAt = Instant.now().minusSeconds(180),
                error = null,
            )
        )
        app.prefs.logSync(SyncEvent(Instant.now(), Instant.now().minusSeconds(120), stepsDelta = 50))

        val report = Diagnostics.collect(app)
        // Kept as a sample of the format, like the widget renders.
        File("build/diagnostics").apply { mkdirs() }.resolve("sample.txt").writeText(report)

        for (section in listOf("== App ==", "== Settings ==", "== Health Connect ==", "== Google Health ==", "== Sync log")) {
            assertTrue("has $section", report.contains(section))
        }
        assertTrue("the widget's current steps", report.contains("4,321"))
        assertTrue("the sync delta", report.contains("+50"))
        // Google Health is off by default, so that path is reported rather than attempted.
        assertTrue("google off noted", report.contains("Google Health: off"))
    }

    @Test
    fun googleSectionReadsLiveWhenOn() = runBlocking {
        app.prefs.update { it.copy(googleHealth = true) }
        val report = Diagnostics.collect(app)
        // On, but not signed in under Robolectric: it says so instead of claiming a read.
        assertTrue(report.contains("Signed in:"))
    }
}
