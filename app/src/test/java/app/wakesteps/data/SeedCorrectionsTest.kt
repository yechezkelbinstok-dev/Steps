package app.wakesteps.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class SeedCorrectionsTest {

    private val zone = ZoneId.of("America/New_York")

    private fun at(kind: String): LocalDateTime {
        val entry = SEED_CORRECTIONS.single { it.startsWith("$kind|") }
        return Instant.ofEpochSecond(entry.substringAfter('|').toLong()).atZone(zone).toLocalDateTime()
    }

    @Test
    fun theJoinFallsBetweenNov19sTwoSleeps() {
        // Between 12:15 PM and 8:32 PM.
        assertEquals(LocalDateTime.parse("2026-11-19T16:00"), at("J"))
    }

    @Test
    fun theDroppedSleepIsTheOneOnFridayMorning() {
        // Inside 3:59 to 8:24 AM.
        assertEquals(LocalDateTime.parse("2026-11-20T06:00"), at("I"))
    }
}
