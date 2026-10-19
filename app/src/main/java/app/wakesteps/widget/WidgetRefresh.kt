package app.wakesteps.widget

import android.content.Context
import android.os.SystemClock
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import app.wakesteps.app
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The widget's refresh button: re-reads today's numbers right away (from Google Health when it's
 * connected, otherwise Health Connect) instead of waiting for the next automatic refresh. The
 * button turns into a spinner until they're in. It can only bring in what the tracker has
 * already synced; the band itself syncs on its own schedule.
 */
object WidgetRefresh {

    /** Shortest time the spinner shows, so even an instant refresh visibly happens. */
    private const val MIN_SPIN_MS = 800L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** True while a tap's refresh runs; the widget shows a spinner in place of the button. */
    val running = MutableStateFlow(false)

    /** Starts a refresh unless one from the button is already running. */
    fun start(context: Context): Job? {
        if (!running.compareAndSet(expect = false, update = true)) return null
        return scope.launch {
            try {
                WidgetPusher.pushAll(context)
                val started = SystemClock.elapsedRealtime()
                context.app.engine.refresh()
                delay(MIN_SPIN_MS - (SystemClock.elapsedRealtime() - started))
            } finally {
                running.value = false
                withContext(NonCancellable) { WidgetPusher.pushAll(context) }
            }
        }
    }
}

/** What a tap on the widget's refresh button runs (through Glance's broadcast receiver). */
class RefreshAction : ActionCallback {

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val refresh = WidgetRefresh.start(context.applicationContext) ?: return
        // Keep the tap's broadcast open while the refresh runs, so Android keeps the app awake
        // for it, but give up waiting well before the broadcast would time out.
        withTimeoutOrNull(WAIT_MS) { refresh.join() }
    }

    private companion object {
        const val WAIT_MS = 25_000L
    }
}
