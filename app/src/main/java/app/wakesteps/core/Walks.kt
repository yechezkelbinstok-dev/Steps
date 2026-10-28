package app.wakesteps.core

/** A walk in a day: minutes [first] to [last] (counted from the day's start), and the steps across them. */
data class Walk(val first: Int, val last: Int, val steps: Long) {
    val minutes: Int get() = last - first + 1

    /** Steps a minute, on average. */
    val pace: Long get() = steps / minutes
}

/**
 * The walks in a day's steps minute by minute: stretches at walking pace ([PACE] steps a minute
 * or more) lasting [SHORTEST] minutes or more. A walk carries on through pauses of up to [PAUSE]
 * minutes (a crossing, a door), and takes in the minute either side when it began or ended partway
 * through one.
 */
object Walks {
    const val PACE = 60L
    const val PAUSE = 2
    const val SHORTEST = 5

    /** A minute next to a walk with this many steps is the walk starting or ending partway through it. */
    private const val EDGE = 20L

    fun of(minutes: LongArray): List<Walk> {
        val out = mutableListOf<Walk>()
        var first = -1
        var last = -1
        fun close() {
            if (first < 0 || last - first + 1 < SHORTEST) return
            val a = if (first > 0 && minutes[first - 1] >= EDGE) first - 1 else first
            val b = if (last < minutes.size - 1 && minutes[last + 1] >= EDGE) last + 1 else last
            out += Walk(a, b, (a..b).sumOf { minutes[it] })
        }
        for (m in minutes.indices) {
            if (minutes[m] < PACE) continue
            if (first >= 0 && m - last - 1 > PAUSE) {
                close()
                first = -1
            }
            if (first < 0) first = m
            last = m
        }
        close()
        return out
    }
}
