package app.wakesteps.core

import java.time.Duration
import java.time.Instant

/**
 * Readings of the phone's step counter over time, so the steps taken between any recent moment
 * and now can be worked out. The counter is a running total that starts from 0 at every boot,
 * and it only reports when steps happen.
 *
 * While walking, readings are thinned to about one per [spacing] (the newest always current);
 * the first and last reading of each walk are kept, so it's known when it started and stopped.
 * Readings are kept for [keep].
 */
class StepHistory(
    private val spacing: Duration = Duration.ofSeconds(10),
    private val keep: Duration = Duration.ofHours(26),
) {
    data class Reading(val at: Instant, val count: Long)

    private val readings = ArrayDeque<Reading>()

    @Synchronized
    fun record(at: Instant, count: Long) {
        // The counter only goes down when the phone restarted: readings from before can't be compared.
        if (count < (readings.lastOrNull()?.count ?: 0)) readings.clear()
        val newest = readings.lastOrNull()
        // A reading can't be older than the one before it (the wall clock may have shifted a little).
        val reading = Reading(if (newest != null && at.isBefore(newest.at)) newest.at else at, count)
        val beforeNewest = readings.getOrNull(readings.size - 2)
        val mid = newest != null && beforeNewest != null &&
            Duration.between(beforeNewest.at, newest.at) < spacing &&
            Duration.between(newest.at, reading.at) < spacing
        // Mid-walk, move the newest reading along instead of adding one every step.
        if (mid) readings[readings.size - 1] = reading else readings.addLast(reading)
        val cutoff = reading.at.minus(keep)
        // Keep one reading from before the cutoff, so the cutoff itself can still be filled in.
        while (readings.size > 2 && readings[1].at.isBefore(cutoff)) readings.removeFirst()
    }

    /**
     * Steps counted since [since], or null with no readings yet. Before the oldest reading it
     * can only count from that reading on, so it never counts more than the phone saw.
     */
    @Synchronized
    fun stepsSince(since: Instant): Long? {
        val newest = readings.lastOrNull() ?: return null
        return (newest.count - countAt(since)).coerceAtLeast(0)
    }

    @Synchronized
    fun newest(): Reading? = readings.lastOrNull()

    @Synchronized
    fun size(): Int = readings.size

    /** The counter's value at [t], clamped to the readings it has. */
    private fun countAt(t: Instant): Long {
        val first = readings.first()
        val last = readings.last()
        if (!t.isAfter(first.at)) return first.count
        if (!t.isBefore(last.at)) return last.count
        val i = readings.binarySearchBy(t) { it.at }
        if (i >= 0) return readings[i].count
        val after = readings[-i - 1]
        val before = readings[-i - 2]
        val gap = Duration.between(before.at, after.at)
        // No readings for a while means no steps: they all came just before the next reading.
        if (gap > IDLE) return before.count
        val part = Duration.between(before.at, t).toMillis().toDouble() / gap.toMillis()
        return before.count + Math.round((after.count - before.count) * part)
    }

    private companion object {
        /** Readings this far apart mean the phone sat still in between. */
        val IDLE: Duration = Duration.ofSeconds(60)
    }
}
