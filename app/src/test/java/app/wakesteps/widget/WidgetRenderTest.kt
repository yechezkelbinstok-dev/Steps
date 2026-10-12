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
 * Renders the real widget at its four sizes to PNGs under build/widget-renders so the layouts
 * can be eyeballed without a device. Not an assertion test.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = RenderTestApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class WidgetRenderTest {

    @Test
    fun renderAllSizes() = renderAll("light", steps = 8432)

    @Test
    @Config(qualifiers = "+night")
    fun renderAllSizesDark() = renderAll("dark", steps = 8432)

    @Test
    fun renderGoalReached() = renderAll("goal", steps = 11250)

    private fun renderAll(tag: String, steps: Long) = runBlocking {
        val app = ApplicationProvider.getApplicationContext<WakeStepsApp>()
        val now = Instant.now()
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
            "2x2" to DpSize(170.dp, 180.dp),
            "4x1" to DpSize(350.dp, 90.dp),
            "4x2" to DpSize(350.dp, 190.dp),
            "4x3" to DpSize(350.dp, 290.dp),
            "4x2_narrow" to DpSize(290.dp, 160.dp),
            "4x3_narrow" to DpSize(300.dp, 250.dp),
        ).forEach { (name, size) -> render(app, size, File(out, "${tag}_$name.png")) }
    }

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    private suspend fun render(app: WakeStepsApp, size: DpSize, file: File) {
        val rv = StepsWidget().compose(app, size = size)
        val parent = FrameLayout(app)
        val view: View = rv.apply(app, parent)
        val d = app.resources.displayMetrics.density
        val w = (size.width.value * d).toInt()
        val h = (size.height.value * d).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, w, h)
        val bmp = Bitmap.createBitmap(w + 40, h + 40, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.rgb(54, 62, 80)) // stand-in wallpaper
        canvas.translate(20f, 20f)
        view.draw(canvas)
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Log.i("WidgetRender", "wrote ${file.absolutePath}")
    }
}
