package app.wakesteps.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.compose
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Draws every placed widget right now.
 *
 * Glance's own update()/updateAll() runs the redraw as a WorkManager job, which Android can
 * hold back for minutes while the app is in the background. Rendering the RemoteViews here and
 * handing them straight to AppWidgetManager skips that queue, so the widget changes the moment
 * new data arrives.
 */
object WidgetPusher {

    /**
     * One redraw at a time. Glance won't compose the same widget twice at once (the earlier one
     * fails with "Another session ... has started"), and redraws come from several places (live
     * steps, refreshes, the refresh button). Each reads the numbers as they are when it runs, so
     * the last one in line always shows the latest.
     */
    private val lock = Mutex()

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    suspend fun pushAll(context: Context) = lock.withLock {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, StepsWidgetReceiver::class.java))
        if (ids.isEmpty()) return@withLock
        val glanceIds = GlanceAppWidgetManager(context)
        val widget = StepsWidget()
        for (id in ids) {
            try {
                val views = widget.compose(context, glanceIds.getGlanceIdBy(id), options = manager.getAppWidgetOptions(id))
                manager.updateAppWidget(id, views)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // E.g. a widget removed mid-redraw; the others still get drawn.
                Log.w("WidgetPusher", "Couldn't redraw widget $id", e)
            }
        }
    }
}
