package app.wakesteps.data

import app.wakesteps.core.Place
import app.wakesteps.core.PlaceFix
import app.wakesteps.core.RestDays
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class PlacesTest {

    private val eastern = ZoneId.of("America/New_York")
    private val cincinnati = Place(39.19, -84.45, eastern, "Cincinnati")
    private val amberley = Place(39.20, -84.42, eastern, null)
    private val newYork = Place(40.71, -74.0, eastern, "New York")

    private fun at(s: String) = Instant.parse(s)

    @Test
    fun theSamePlaceAgainIsntAddedButCanNameIt() {
        val fixes = listOf(PlaceFix(at("2026-10-20T12:00:00Z"), cincinnati))
        assertEquals(fixes, withPlace(fixes, PlaceFix(at("2026-10-28T12:00:00Z"), amberley)))
        val unnamed = listOf(PlaceFix(at("2026-10-20T12:00:00Z"), amberley))
        assertEquals(
            listOf(PlaceFix(at("2026-10-20T12:00:00Z"), amberley.copy(name = "Cincinnati"))),
            withPlace(unnamed, PlaceFix(at("2026-10-28T12:00:00Z"), cincinnati)),
        )
    }

    @Test
    fun aNewPlaceGoesFirst() {
        val fixes = listOf(PlaceFix(at("2026-10-20T12:00:00Z"), cincinnati))
        val travelled = withPlace(fixes, PlaceFix(at("2026-10-29T15:00:00Z"), newYork))
        assertEquals(listOf(newYork, cincinnati), travelled.map { it.place })
    }

    @Test
    fun eachShabbosIsTimedWhereTheUserWasThatFriday() {
        // In New York from Thursday Oct 29, back in Cincinnati on Sunday Nov 1.
        val fixes = listOf(
            PlaceFix(at("2026-11-01T20:00:00Z"), cincinnati),
            PlaceFix(at("2026-10-29T15:00:00Z"), newYork),
            PlaceFix(at("2026-09-01T12:00:00Z"), cincinnati),
        )
        assertEquals(newYork, RestDays.placeOn(fixes, LocalDate.of(2026, 10, 30)))
        assertEquals(cincinnati, RestDays.placeOn(fixes, LocalDate.of(2026, 10, 23)))
        assertEquals(cincinnati, RestDays.placeOn(fixes, LocalDate.of(2026, 11, 6)))
        // Before the first place found: that one. With none: Cincinnati.
        assertEquals(cincinnati, RestDays.placeOn(fixes, LocalDate.of(2026, 8, 1)))
        assertEquals(Place.CINCINNATI, RestDays.placeOn(emptyList(), LocalDate.of(2026, 10, 30)))
    }
}
