package app.wakesteps.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.wakesteps.R
import app.wakesteps.app
import app.wakesteps.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Live refresh: re-reads Health Connect the moment the screen turns on (so the widget is current
 * when you look at it) and then every minute while it stays on. Does nothing while the screen
 * is off.
 *
 * Android requires a notification for any long-running service, but this app never asks for
 * notification permission, so on Android 13+ the notification is never shown. The service only
 * appears in the quick-settings "active apps" list.
 */
class LiveModeService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> startLoop()
                Intent.ACTION_SCREEN_OFF -> stopLoop()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        if (getSystemService(PowerManager::class.java).isInteractive) startLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun startLoop() {
        if (loop?.isActive == true) return
        loop = scope.launch {
            while (isActive) {
                app.engine.refresh()
                delay(INTERVAL_MS)
            }
        }
    }

    private fun stopLoop() {
        loop?.cancel()
        loop = null
    }

    private fun buildNotification(): android.app.Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_steps)
            .setContentTitle(getString(R.string.notif_live))
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    override fun onDestroy() {
        unregisterReceiver(screenReceiver)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "live_silent"
        private const val NOTIFICATION_ID = 1
        private const val INTERVAL_MS = 60_000L

        fun createChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.deleteNotificationChannel("live") // old, more visible channel from v1
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.channel_live),
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_SECRET
            }
            nm.createNotificationChannel(channel)
        }

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, LiveModeService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LiveModeService::class.java))
        }
    }
}
