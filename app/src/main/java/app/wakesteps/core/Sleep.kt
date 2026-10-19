package app.wakesteps.core

import java.time.Duration
import java.time.Instant

/** Sleep stages, in the order the night chart stacks them, top to bottom. */
enum class SleepStage {
    AWAKE, REM, LIGHT, DEEP,

    /** Older trackers, and nights without stages ("classic" sleep), only tell these apart. */
    RESTLESS, ASLEEP;

    val asleep: Boolean get() = this != AWAKE && this != RESTLESS
}

data class StageSpan(val start: Instant, val end: Instant, val stage: SleepStage) {
    val duration: Duration get() = Duration.between(start, end)
}

/** One sleep session as the tracker recorded it, with as much detail as it gave. */
data class SleepSession(
    val start: Instant,
    val end: Instant,
    /** Contiguous stages covering the session, when the tracker worked them out. */
    val stages: List<StageSpan> = emptyList(),
    /** Wake-ups too brief to count as awake time (Google Health only). */
    val shortAwakenings: List<StageSpan> = emptyList(),
    val minutesToFallAsleep: Long? = null,
)

/**
 * A night's sleep as the app counts it: the sleep the user woke from to start a day (a [span]
 * the day logic merged, possibly from several sessions), stage by stage.
 */
data class SleepNight(
    val span: SleepSpan,
    val start: Instant,
    val end: Instant,
    /** Stage by stage; a gap between sessions is awake, a session without stages is [SleepStage.ASLEEP]. */
    val stages: List<StageSpan>,
    val shortAwakenings: Int,
    /** From lying down to falling asleep, when known. */
    val fellAsleepIn: Duration?,
) {
    val inBed: Duration get() = Duration.between(start, end)

    fun time(stage: SleepStage): Duration = stages.filter { it.stage == stage }.total()

    val asleep: Duration get() = if (stages.isEmpty()) inBed else stages.filter { it.stage.asleep }.total()
    val awake: Duration get() = inBed.minus(asleep)

    /** Share of the time in bed spent asleep. */
    val efficiency: Float get() = if (inBed.isZero) 0f else asleep.toMillis().toFloat() / inBed.toMillis()

    /** Whether the tracker told deep, light and REM apart this night. */
    val staged: Boolean get() = stages.any { it.stage == SleepStage.DEEP || it.stage == SleepStage.LIGHT || it.stage == SleepStage.REM }

    /** The chart's rows, top to bottom. */
    val lanes: List<SleepStage>
        get() = if (staged) listOf(SleepStage.AWAKE, SleepStage.REM, SleepStage.LIGHT, SleepStage.DEEP)
        else listOfNotNull(SleepStage.AWAKE, SleepStage.RESTLESS.takeIf { time(it) > Duration.ZERO }, SleepStage.ASLEEP)

    /** Times awake in the middle of the night (not before falling asleep or after waking up). */
    val wakeUps: Int get() = stages.drop(1).dropLast(1).count { it.stage == SleepStage.AWAKE }

    /** The stage at [t], if it's within the night. */
    fun stageAt(t: Instant): SleepStage? = stages.firstOrNull { !t.isBefore(it.start) && t.isBefore(it.end) }?.stage
}

object SleepNights {

    /**
     * The night spanning [span], put together from the tracker's [sessions] that overlap it:
     * stages clipped to the span, a gap between two sessions counted as awake, and neighbouring
     * stretches of the same stage joined. Where sessions overlap (two apps recorded the same
     * night), the one that started first wins.
     */
    fun build(span: SleepSpan, sessions: List<SleepSession>): SleepNight {
        val overlapping = sessions.filter { it.end > span.start && it.start < span.end }.sortedBy { it.start }
        if (overlapping.isEmpty()) return SleepNight(span, span.start, span.end, emptyList(), 0, null)
        val start = maxOf(span.start, overlapping.first().start)
        val end = minOf(span.end, overlapping.maxOf { it.end })
        val pieces = mutableListOf<StageSpan>()
        var covered = start
        for (session in overlapping) {
            val from = maxOf(session.start, start)
            val to = minOf(session.end, end)
            if (!to.isAfter(covered)) continue
            if (from.isAfter(covered) && pieces.isNotEmpty()) pieces += StageSpan(covered, from, SleepStage.AWAKE)
            val stages = session.stages.ifEmpty { listOf(StageSpan(session.start, session.end, SleepStage.ASLEEP)) }
            for (stage in stages.sortedBy { it.start }) {
                val a = maxOf(stage.start, from, covered)
                val b = minOf(stage.end, to)
                if (b.isAfter(a)) pieces += StageSpan(a, b, stage.stage)
            }
            covered = maxOf(covered, to)
        }
        val stages = joined(pieces)
        val anyStages = overlapping.any { it.stages.isNotEmpty() }
        val fellAsleepIn = if (anyStages) {
            stages.firstOrNull()?.takeIf { it.stage == SleepStage.AWAKE }?.duration ?: Duration.ZERO
        } else {
            overlapping.first().minutesToFallAsleep?.let(Duration::ofMinutes)
        }
        val shortAwakenings = overlapping.sumOf { s -> s.shortAwakenings.count { it.start >= start && it.start < end } }
        return SleepNight(span, start, end, stages, shortAwakenings, fellAsleepIn)
    }

    private fun joined(pieces: List<StageSpan>): List<StageSpan> {
        val out = mutableListOf<StageSpan>()
        for (p in pieces) {
            val last = out.lastOrNull()
            if (last != null && last.stage == p.stage && !p.start.isAfter(last.end)) {
                out[out.lastIndex] = last.copy(end = maxOf(last.end, p.end))
            } else {
                out += p
            }
        }
        return out
    }
}

private fun List<StageSpan>.total(): Duration = fold(Duration.ZERO) { sum, s -> sum.plus(s.duration) }
