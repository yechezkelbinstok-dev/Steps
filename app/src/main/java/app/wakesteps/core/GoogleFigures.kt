package app.wakesteps.core

import java.time.Duration
import java.time.LocalDate

/** Google Health's own sleep-quality figures for a night, as its app shows them. */
data class GoogleFigures(
    val timeToSound: Duration,
    val sound: Duration,
    val restlessness: Duration,
    val interruptions: Duration,
) {
    companion object {
        private fun of(timeToSound: Long, sound: Long, restlessness: Long, interruptions: Long) = GoogleFigures(
            Duration.ofMinutes(timeToSound), Duration.ofMinutes(sound), Duration.ofMinutes(restlessness), Duration.ofMinutes(interruptions),
        )

        /**
         * The figures the user sent over from Google Health's Coach (Oct 19, 2026), by the morning
         * each night ended: shown as they are for those nights, and what the app's sound sleep is
         * tuned to match.
         */
        val KNOWN: Map<LocalDate, GoogleFigures> = mapOf(
            LocalDate.of(2026, 10, 19) to of(24, 164, 20, 11),
            LocalDate.of(2026, 10, 18) to of(8, 148, 35, 10),
            LocalDate.of(2026, 10, 15) to of(19, 104, 14, 9),
            LocalDate.of(2026, 10, 14) to of(45, 201, 31, 5),
            LocalDate.of(2026, 10, 13) to of(25, 79, 9, 0),
            LocalDate.of(2026, 10, 12) to of(17, 144, 19, 13),
            LocalDate.of(2026, 10, 11) to of(13, 119, 26, 18),
            LocalDate.of(2026, 10, 9) to of(12, 171, 25, 5),
            LocalDate.of(2026, 9, 18) to of(14, 146, 12, 7),
            LocalDate.of(2026, 9, 17) to of(13, 130, 11, 9),
            LocalDate.of(2026, 9, 16) to of(20, 158, 13, 0),
            LocalDate.of(2026, 9, 15) to of(25, 118, 12, 0),
            LocalDate.of(2026, 9, 14) to of(16, 137, 52, 22),
        )
    }
}
