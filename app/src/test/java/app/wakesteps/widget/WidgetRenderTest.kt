package app.wakesteps.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.compose
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import app.wakesteps.WakeStepsApp
import app.wakesteps.core.StartReason
import app.wakesteps.data.DayTotals
import app.wakesteps.data.Snapshot
import app.wakesteps.data.WidgetStyle
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration
import java.time.Instant

/** Skips background scheduling so no real refresh overwrites the fake snapshot. */
class RenderTestApp : WakeStepsApp() {
    override fun onCreate() {
        WorkManagerTestInitHelper.initializeTestWorkManager(this)
    }
}

/**
 * Renders the real widget in each style at every size to PNGs under build/widget-renders, so
 * layouts can be eyeballed without a device. Not an assertion test.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = RenderTestApp::class, qualifiers = "w411dp-h891dp-420dpi")
class WidgetRenderTest {

    @Test
    fun renderAllSizes() = WidgetStyle.entries.forEach { renderAll(it.name.lowercase(), it, steps = 8432) }

    @Test
    @Config(qualifiers = "+night")
    fun renderAllSizesDark() = renderAll("dark", WidgetStyle.SOFT, steps = 8432)

    @Test
    fun renderPastGoal() = WidgetStyle.entries.forEach { renderAll("lap_${it.name.lowercase()}", it, steps = 13400) }

    private fun renderAll(tag: String, style: WidgetStyle, steps: Long) = runBlocking {
        val app = ApplicationProvider.getApplicationContext<WakeStepsApp>()
        val now = Instant.now()
        app.prefs.update { it.copy(widgetStyle = style) }
        app.prefs.saveSnapshot(
            Snapshot(
                totals = DayTotals(steps = steps, calories = 1284.0, distanceMeters = 6120.0),
                dayStart = now.minus(Duration.ofMinutes(10 * 60 + 3)),
                startReason = StartReason.WOKE_UP,
                asleep = false,
                updatedAt = now,
                latestDataAt = now.minus(Duration.ofMinutes(3)),
                error = null,
            )
        )
        val out = File("build/widget-renders").apply { mkdirs() }
        // Roughly Pixel launcher cell sizes.
        listOf(
            "2x1" to DpSize(170.dp, 80.dp),
            "2x2" to DpSize(167.dp, 190.dp),
            "4x1" to DpSize(350.dp, 90.dp),
            "4x2" to DpSize(350.dp, 190.dp),
            "4x3" to DpSize(350.dp, 290.dp),
            "4x2_narrow" to DpSize(290.dp, 160.dp),
            "4x3_narrow" to DpSize(300.dp, 250.dp),
        ).forEach { (name, size) -> render(app, size, File(out, "${tag}_$name.png")) }
    }

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    private suspend fun render(app: WakeStepsApp, size: DpSize, file: File) {
        // The Pixel launcher reports a widget about 14dp smaller than the card it draws, and the
        // layout is composed for the reported size, then stretched to the real one.
        val rv = StepsWidget().compose(app, size = DpSize(size.width - 14.dp, size.height - 13.dp))
        val parent = FrameLayout(app)
        val view: View = rv.apply(app, parent)
        val d = app.resources.displayMetrics.density
        val w = (size.width.value * d).toInt()
        val h = (size.height.value * d).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, w, h)
        // Transparent, so the renders can be laid over a real wallpaper.
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.TRANSPARENT)
        view.draw(Canvas(bmp))
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Log.i("WidgetRender", "wrote ${file.absolutePath}")
    }
}
