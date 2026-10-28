package app.wakesteps.core

import org.junit.Assert.assertEquals
import org.junit.Test

class WalksTest {

    /** A day's steps minute by minute, as runs of so many minutes with so many steps in each. */
    private fun day(vararg runs: Pair<Int, Long>): LongArray =
        runs.flatMap { (minutes, steps) -> List(minutes) { steps } }.toLongArray()

    @Test
    fun aStretchAtWalkingPaceIsAWalk() {
        val walks = Walks.of(day(10 to 0L, 12 to 104L, 10 to 0L))
        assertEquals(listOf(Walk(10, 21, 12 * 104L)), walks)
        assertEquals(12, walks[0].minutes)
        assertEquals(104L, walks[0].pace)
    }

    @Test
    fun potteringAboutAndShortWalksAreNot() {
        assertEquals(emptyList<Walk>(), Walks.of(day(30 to 35L)))
        assertEquals(emptyList<Walk>(), Walks.of(day(5 to 0L, 4 to 110L, 5 to 0L)))
    }

    @Test
    fun aShortPauseDoesNotEndAWalkButALongerOneDoes() {
        // Stopped for two minutes at a crossing: one walk, steps across the pause and all.
        val crossing = Walks.of(day(8 to 100L, 2 to 10L, 8 to 100L))
        assertEquals(listOf(Walk(0, 17, 16 * 100L + 2 * 10L)), crossing)
        // Three minutes still: two walks.
        val twoWalks = Walks.of(day(8 to 100L, 3 to 0L, 8 to 100L))
        assertEquals(listOf(Walk(0, 7, 800L), Walk(11, 18, 800L)), twoWalks)
    }

    @Test
    fun takesInTheMinutesItStartedAndEndedPartwayThrough() {
        val walks = Walks.of(day(5 to 0L, 1 to 40L, 6 to 100L, 1 to 25L, 1 to 10L, 5 to 0L))
        assertEquals(listOf(Walk(5, 12, 40L + 600L + 25L)), walks)
    }

    @Test
    fun aWalkRunningToTheEdgesOfTheDay() {
        assertEquals(listOf(Walk(0, 5, 600L)), Walks.of(day(6 to 100L)))
    }
}
