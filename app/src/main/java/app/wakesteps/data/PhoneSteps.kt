package app.wakesteps.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import app.wakesteps.core.StepHistory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

/**
 * The phone's own step counter, used only to fill the gap between tracker syncs: the number shown
 * is the tracker's total plus the steps the phone counted since the tracker's newest data. When
 * the tracker's next batch arrives the gap shrinks to what it still hasn't reported, so the count
 * corrects itself to the tracker's.
 *
 * The counter is a low-power chip that counts whether anyone listens or not. Listening all the
 * time (from the live service) costs next to nothing: the chip holds readings while the screen is
 * off and hands them over, with the time of each, when the phone next wakes.
 */
class PhoneSteps(private val context: Context) {

    private val sensors: SensorManager? = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val history = StepHistory()

    private val _latest = MutableStateFlow<Long?>(null)
    /** The counter's newest reading (its running total since boot); changes as steps come in. */
    val latest: StateFlow<Long?> = _latest.asStateFlow()

    @Volatile var listening = false
        private set

    /** Whether this phone has a step counter at all. */
    val available: Boolean get() = sensor != null

    /** Android 10+ asks the user before an app may read the step counter ("physical activity"). */
    fun hasPermission(): Boolean = Build.VERSION.SDK_INT < 29 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    /** Starts listening when there's a counter and permission to read it. Safe to call again. */
    @Synchronized
    fun start(): Boolean {
        if (listening) return true
        val s = sensor ?: return false
        if (!hasPermission()) return false
        listening = sensors?.registerListener(listener, s, SensorManager.SENSOR_DELAY_NORMAL) == true
        return listening
    }

    /** Asks for the readings the chip is holding back, e.g. as the screen turns on. */
    fun flush() {
        if (listening) sensors?.flush(listener)
    }

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            // Event times count from boot; turn them into wall-clock time.
            val age = (SystemClock.elapsedRealtimeNanos() - event.timestamp).coerceAtLeast(0)
            record(Instant.now().minusNanos(age), event.values[0].toLong())
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    /** One reading: the counter stood at [count] at [at]. */
    internal fun record(at: Instant, count: Long) {
        history.record(at, count)
        _latest.value = count
    }

    /** Steps the phone counted since [since] (from its oldest reading, if that's later), or null with none yet. */
    fun stepsSince(since: Instant): Long? = history.stepsSince(since)

    /** How many readings are kept, and the newest one (for diagnostics). */
    fun readings(): Int = history.size()
    fun newestReading(): StepHistory.Reading? = history.newest()

    /**
     * The steps to add to [snapshot]'s total: what the phone counted since the tracker's newest
     * data, or since waking if the tracker has nothing newer. None once the day has ended.
     */
    fun extraFor(snapshot: Snapshot?): Long {
        if (snapshot == null || snapshot.asleep) return 0
        val anchor = snapshot.latestDataAt?.let { maxOf(it, snapshot.dayStart) } ?: snapshot.dayStart
        return stepsSince(anchor) ?: 0
    }
}
