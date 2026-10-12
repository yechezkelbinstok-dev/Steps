package app.wakesteps.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.wakesteps.app
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Restarts live mode after a reboot or app update, and kicks off a fresh refresh. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (context.app.prefs.currentSettings().liveMode) LiveModeService.start(context)
                RefreshWorker.refreshNow(context)
            } finally {
                pending.finish()
            }
        }
    }
}
