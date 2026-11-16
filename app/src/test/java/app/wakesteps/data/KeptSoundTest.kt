package app.wakesteps.data

import app.wakesteps.core.SoundSleep
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

/** A night's sound sleep is worked out again only when the band has sent more of the night. */
class KeptSoundTest {

    private val sound = SoundSleep(Duration.ofMinutes(150), Duration.ofMinutes(20))

    @Test
    fun theSameReadingsKeepTheFigure() {
        val kept = KeptSound(sound, settled = false, samples = 480)
        assertFalse(kept.outdatedBy(480))
        // a read that came back short (part of the night missing) doesn't replace it either
        assertFalse(kept.outdatedBy(300))
    }

    @Test
    fun moreReadingsWorkItOutAgain() {
        assertTrue(KeptSound(sound, settled = false, samples = 300).outdatedBy(480))
    }

    @Test
    fun oneKeptBeforeTheCountWasKeptIsWorkedOutOnceMore() {
        assertTrue(KeptSound(sound, settled = false).outdatedBy(480))
    }
}
