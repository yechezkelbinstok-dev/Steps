package app.wakesteps

import android.app.Application
import android.content.Context
import app.wakesteps.data.GoogleHealth
import app.wakesteps.data.HealthSource
import app.wakesteps.data.PhoneSteps
import app.wakesteps.data.Prefs
import app.wakesteps.data.StepsEngine
import app.wakesteps.sync.LiveModeService
import app.wakesteps.sync.RefreshWorker

open class WakeStepsApp : Application() {
    val prefs by lazy { Prefs(this) }
    val health by lazy { HealthSource(this) }
    val googleHealth by lazy { GoogleHealth(this) }
    val phoneSteps by lazy { PhoneSteps(this) }
    val engine by lazy { StepsEngine(this, prefs, health, googleHealth) }

    override fun onCreate() {
        super.onCreate()
        LiveModeService.createChannel(this)
        RefreshWorker.schedulePeriodic(this)
    }
}

val Context.app: WakeStepsApp get() = applicationContext as WakeStepsApp
