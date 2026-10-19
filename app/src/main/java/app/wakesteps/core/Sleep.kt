package app.wakesteps.core

import java.time.Duration
import java.time.Instant

/** Sleep stages, in the order the night chart stacks them, top to bottom. */
enum class SleepStage {
    AWAKE,

    /** Brief stirs: moving or half-waking without really waking up, under [SleepNights.BRIEF]. */
    RESTLESS,
    REM, LIGHT, DEEP,

    /** Asleep, from a session the tracker didn't work out stages for. */
    ASLEEP;

    /** Restless counts as asleep, as Google counts it: time you'd feel you were asleep, though stirring. */
    val asleep: Boolean get() = this != AWAKE
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
    /** Brief stirs, overlapping the stages they interrupted (Google Health only). */
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
    /** Stage by stage, contiguous; a session without stages is [SleepStage.ASLEEP]. */
    val stages: List<StageSpan>,
    /** Wake-ups long enough to remember (over [SleepNights.BRIEF]), not counting falling asleep or getting up. */
    val fullAwakenings: Int,
    /** Brief stirs: [SleepStage.RESTLESS] stretches. */
    val restlessMoments: Int,
    /** Time spent in those full awakenings. */
    val interruptions: Duration,
    /** From lying down to falling asleep, when known. */
    val fellAsleepIn: Duration?,
    /** Sound sleep measured from heart rate, once it's been read; see [soundSleep]. */
    val sound: SoundSleep? = null,
) {
    val inBed: Duration get() = Duration.between(start, end)

    fun time(stage: SleepStage): Duration = stages.filter { it.stage == stage }.total()

    val asleep: Duration get() = if (stages.isEmpty()) inBed else stages.filter { it.stage.asleep }.total()
    val awake: Duration get() = inBed.minus(asleep)

    /** Share of the time in bed spent asleep. */
    val efficiency: Float get() = if (inBed.isZero) 0f else asleep.toMillis().toFloat() / inBed.toMillis()

    /** Whether the tracker told deep, light and REM apart this night. */
    val staged: Boolean get() = stages.any { it.stage == SleepStage.DEEP || it.stage == SleepStage.LIGHT || it.stage == SleepStage.REM }

    /** Whether [soundSleep] and [timeToSoundSleep] were measured from heart rate, the way Google measures them. */
    val soundMeasured: Boolean get() = sound != null

    /**
     * Steady, undisturbed sleep: measured from heart rate when it could be ([sound]); until then
     * deep and REM stand in for it, which is in the same range.
     */
    val soundSleep: Duration get() = sound?.total ?: (time(SleepStage.DEEP) + time(SleepStage.REM))

    /** From lying down to the first sound sleep: measured from heart rate when it could be; until then, the first ten minutes of uninterrupted sleep. */
    val timeToSoundSleep: Duration?
        get() {
            sound?.let { return it.timeTo }
            if (!staged) return null
            var runStart: Instant? = null
            for (s in stages) {
                val steady = s.stage == SleepStage.LIGHT || s.stage == SleepStage.DEEP || s.stage == SleepStage.REM
                if (!steady) {
                    runStart = null
                    continue
                }
                val from = runStart ?: s.start.also { runStart = it }
                if (Duration.between(from, s.end) >= Duration.ofMinutes(10)) return Duration.between(start, from)
            }
            return null
        }

    /** The chart's rows, top to bottom. */
    val lanes: List<SleepStage>
        get() {
            val restless = SleepStage.RESTLESS.takeIf { time(it) > Duration.ZERO }
            return if (staged) listOfNotNull(SleepStage.AWAKE, restless, SleepStage.REM, SleepStage.LIGHT, SleepStage.DEEP)
            else listOfNotNull(SleepStage.AWAKE, restless, SleepStage.ASLEEP)
        }

    /** The stage at [t], if it's within the night. */
    fun stageAt(t: Instant): SleepStage? = stages.firstOrNull { !t.isBefore(it.start) && t.isBefore(it.end) }?.stage

    /** Just the part of the night from [from] to [to], as a night of its own: for its time in each stage. */
    fun within(from: Instant, to: Instant): SleepNight = copy(
        start = from,
        end = to,
        stages = stages.mapNotNull { s ->
            val a = maxOf(s.start, from)
            val b = minOf(s.end, to)
            if (b.isAfter(a)) s.copy(start = a, end = b) else null
        },
    )

    /**
     * The same night with restless moments counted as awake (a setting): they join the awake
     * time, and each one counts as waking up.
     */
    fun restlessAsAwake(): SleepNight {
        val folded = SleepNights.joined(stages.map { if (it.stage == SleepStage.RESTLESS) it.copy(stage = SleepStage.AWAKE) else it })
        val wakeUps = folded.drop(1).dropLast(1).filter { it.stage == SleepStage.AWAKE }
        return copy(stages = folded, fullAwakenings = wakeUps.size, restlessMoments = 0, interruptions = wakeUps.total())
    }
}

object SleepNights {

    /** Awake for this long or less is restless, not a wake-up (Google: only longer ones count as waking). */
    val BRIEF: Duration = Duration.ofMinutes(5)

    /**
     * The night spanning [span], put together from the tracker's [sessions] that overlap it:
     * stages clipped to the span, a gap between two sessions counted as awake, the tracker's
     * brief stirs cut into the stages they interrupted, each stretch of wakefulness in the middle
     * of the night restless if it lasted [BRIEF] or less and awake if longer, and neighbouring
     * stretches of the same stage joined. Where sessions overlap (two apps recorded the same night), the one that
     * started first wins.
     */
    fun build(span: SleepSpan, sessions: List<SleepSession>): SleepNight {
        val overlapping = sessions.filter { it.end > span.start && it.start < span.end }.sortedBy { it.start }
        if (overlapping.isEmpty()) return SleepNight(span, span.start, span.end, emptyList(), 0, 0, Duration.ZERO, null)
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
        val stirs = overlapping.flatMap { it.shortAwakenings }.filter { it.end > start && it.start < end }
        val stages = joined(byWakefulness(joined(withRestless(joined(pieces), stirs))))
        val wakeUps = stages.drop(1).dropLast(1).filter { it.stage == SleepStage.AWAKE }
        val anyStages = overlapping.any { it.stages.isNotEmpty() }
        val fellAsleepIn = if (anyStages) {
            stages.firstOrNull()?.takeIf { it.stage == SleepStage.AWAKE }?.duration ?: Duration.ZERO
        } else {
            overlapping.first().minutesToFallAsleep?.let(Duration::ofMinutes)
        }
        return SleepNight(
            span, start, end, stages,
            fullAwakenings = wakeUps.size,
            restlessMoments = stages.count { it.stage == SleepStage.RESTLESS },
            interruptions = wakeUps.total(),
            fellAsleepIn = fellAsleepIn,
        )
    }

    /**
     * Each stretch of wakefulness taken whole, the way Google counts it: awake and restless spans
     * back to back are one stretch, and a stretch over [BRIEF] is waking up (all of it awake), any
     * shorter one restless. So a 4-minute wake-up running into a 2-minute stir is a 6-minute
     * interruption, not two restless moments. The first and last stay awake: falling asleep and
     * getting up.
     */
    private fun byWakefulness(timeline: List<StageSpan>): List<StageSpan> {
        val out = timeline.toMutableList()
        var i = 0
        while (i < out.size) {
            if (out[i].stage !in WAKEFUL) {
                i++
                continue
            }
            var j = i
            while (j + 1 < out.size && out[j + 1].stage in WAKEFUL && out[j + 1].start == out[j].end) j++
            val edge = i == 0 || j == out.lastIndex
            val long = Duration.between(out[i].start, out[j].end) > BRIEF
            val stage = if (edge || long) SleepStage.AWAKE else SleepStage.RESTLESS
            for (k in i..j) out[k] = out[k].copy(stage = stage)
            i = j + 1
        }
        return out
    }

    private val WAKEFUL = setOf(SleepStage.AWAKE, SleepStage.RESTLESS)

    /** [timeline] with the [stirs] cut into whatever sleep they interrupted, as restless. */
    private fun withRestless(timeline: List<StageSpan>, stirs: List<StageSpan>): List<StageSpan> {
        if (stirs.isEmpty()) return timeline
        val marks = joined(stirs.sortedBy { it.start }.map { it.copy(stage = SleepStage.RESTLESS) })
        val out = mutableListOf<StageSpan>()
        for (piece in timeline) {
            if (piece.stage == SleepStage.AWAKE || piece.stage == SleepStage.RESTLESS) {
                out += piece
                continue
            }
            var cursor = piece.start
            for (mark in marks) {
                if (!mark.end.isAfter(cursor) || !mark.start.isBefore(piece.end)) continue
                val from = maxOf(mark.start, cursor)
                val to = minOf(mark.end, piece.end)
                if (from.isAfter(cursor)) out += StageSpan(cursor, from, piece.stage)
                out += StageSpan(from, to, SleepStage.RESTLESS)
                cursor = to
            }
            if (piece.end.isAfter(cursor)) out += StageSpan(cursor, piece.end, piece.stage)
        }
        return out
    }

    /** Neighbouring stretches of the same stage, joined into one. */
    fun joined(pieces: List<StageSpan>): List<StageSpan> {
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
