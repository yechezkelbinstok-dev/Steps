package app.wakesteps.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class SeedScoresTest {

    private fun date(month: Int, day: Int) = LocalDate.of(2026, month, day)

    @Test
    fun aFreshInstallGetsEveryScoreSentOver() {
        val (entries, added) = withSeedScores(emptyMap(), added = null, firstBatchAdded = false)!!
        assertEquals(13, entries.size)
        assertEquals(77, entries.getValue(date(9, 14)).score)
        assertEquals(85, entries.getValue(date(10, 9)).score)
        assertEquals(71, entries.getValue(date(10, 18)).score)
        assertNull(entries.getValue(date(10, 18)).estimate)
        assertEquals(entries.keys, added)
        // Once added, there's nothing more to do.
        assertNull(withSeedScores(entries, added, firstBatchAdded = false))
    }

    @Test
    fun after112OnlyTheSecondBatchIsAdded() {
        // 1.12 added the first four; since then Oct 13 went back to the estimate and Oct 14 was changed.
        val before = mapOf(
            date(10, 18) to ScoreEntry(date(10, 18), 71, 74),
            date(10, 15) to ScoreEntry(date(10, 15), 52, 60),
            date(10, 14) to ScoreEntry(date(10, 14), 90, 80),
        )
        val (entries, added) = withSeedScores(before, added = null, firstBatchAdded = true)!!
        assertFalse("removed by the user, so not put back", date(10, 13) in entries)
        assertEquals(ScoreEntry(date(10, 14), 90, 80), entries.getValue(date(10, 14)))
        assertEquals(ScoreEntry(date(10, 18), 71, 74), entries.getValue(date(10, 18)))
        assertEquals(61, entries.getValue(date(10, 12)).score)
        assertEquals(66, entries.getValue(date(9, 15)).score)
        assertEquals(3 + 9, entries.size)
        assertEquals(13, added.size)
    }

    @Test
    fun aScoreAlreadyEnteredWins() {
        val mine = mapOf(date(10, 9) to ScoreEntry(date(10, 9), 80, null))
        val (entries, _) = withSeedScores(mine, added = null, firstBatchAdded = false)!!
        assertEquals(80, entries.getValue(date(10, 9)).score)
    }
}
