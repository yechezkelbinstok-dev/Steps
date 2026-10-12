package app.wakesteps.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import app.wakesteps.sync.RefreshWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Plain AppWidgetProvider rather than GlanceAppWidgetReceiver: we draw with [WidgetPusher]
 * so updates never wait in Glance's WorkManager queue.
 */
class StepsWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        push(context)
        RefreshWorker.refreshNow(context)
    }

    /** Resized: lay it out again for the new size. */
    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        push(context)
    }

    private fun push(context: Context) {
        // Null when called outside onReceive (e.g. directly in tests).
        val pending: PendingResult? = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                WidgetPusher.pushAll(context.applicationContext)
            } finally {
                pending?.finish()
            }
        }
    }
}
