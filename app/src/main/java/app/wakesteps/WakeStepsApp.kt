package app.wakesteps

import android.app.Application
import android.content.Context
import app.wakesteps.data.HealthSource
import app.wakesteps.data.Prefs
import app.wakesteps.data.StepsEngine
import app.wakesteps.sync.LiveModeService
import app.wakesteps.sync.RefreshWorker

class WakeStepsApp : Application() {
    val prefs by lazy { Prefs(this) }
    val health by lazy { HealthSource(this) }
    val engine by lazy { StepsEngine(this, prefs, health) }

    override fun onCreate() {
        super.onCreate()
        LiveModeService.createChannel(this)
        RefreshWorker.schedulePeriodic(this)
    }
}

val Context.app: WakeStepsApp get() = applicationContext as WakeStepsApp
