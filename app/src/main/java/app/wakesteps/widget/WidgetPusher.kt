package app.wakesteps.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.compose

/**
 * Draws every placed widget right now.
 *
 * Glance's own update()/updateAll() runs the redraw as a WorkManager job, which Android can
 * hold back for minutes while the app is in the background. Rendering the RemoteViews here and
 * handing them straight to AppWidgetManager skips that queue, so the widget changes the moment
 * new data arrives.
 */
object WidgetPusher {

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    suspend fun pushAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, StepsWidgetReceiver::class.java))
        if (ids.isEmpty()) return
        val glanceIds = GlanceAppWidgetManager(context)
        val widget = StepsWidget()
        for (id in ids) {
            runCatching {
                val views = widget.compose(context, glanceIds.getGlanceIdBy(id), options = manager.getAppWidgetOptions(id))
                manager.updateAppWidget(id, views)
            }
        }
    }
}
