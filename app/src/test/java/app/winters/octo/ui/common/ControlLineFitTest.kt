package app.winters.octo.ui.common

import app.winters.octo.ui.favourites.favouriteSegmentsRoom
import app.winters.octo.ui.history.historySegmentsRoom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// What fits on a control line: segments only where their words fit whole,
// facts lines that break only between facts, and room kept for the letter
// rail. Widths are in dp at the header's sizes.
class ControlLineFitTest {
    @Test
    fun favoritesKeepsItsSegmentsWhereTheirWordsFit() {
        // "Artists", the widest of the three at 11sp, about 42dp.
        assertEquals(116, favouriteSegmentsRoom(360))
        assertFalse(segmentsFit(favouriteSegmentsRoom(360), count = 3, widest = 42))
        assertTrue(segmentsFit(favouriteSegmentsRoom(411), count = 3, widest = 42))
    }

    @Test
    fun historyKeepsItsSegmentsBesideTheTimeRangeWhereTheyFit() {
        // "Recently played" about 85dp at 11sp; "Last 6 months" makes a
        // 134dp range button.
        assertEquals(190, historySegmentsRoom(360, range = 134))
        assertFalse(segmentsFit(historySegmentsRoom(360, range = 134), count = 2, widest = 85))
        assertTrue(segmentsFit(historySegmentsRoom(411, range = 134), count = 2, widest = 85))
    }

    @Test
    fun noRoomIsNeverAFit() {
        assertFalse(segmentsFit(room = 0, count = 2, widest = 10))
        assertFalse(segmentsFit(room = 300, count = 0, widest = 10))
    }

    @Test
    fun aFactsLineBreaksOnlyBetweenFacts() {
        val space = ' '
        assertEquals("15${space}songs", keepTogether("15 songs"))
        assertEquals("1${space}hr${space}12${space}min", keepTogether("1 hr 12 min"))
        assertEquals("Rated${space}4.3", keepTogether("Rated 4.3"))
        assertEquals("2020 • 15${space}songs • 56${space}min", factsLine(listOf("2020", "15 songs", "56 min")))
        // Words with no number beside them still wrap as usual.
        assertEquals("3${space}songs only on this phone", keepTogether("3 songs only on this phone"))
        assertEquals("12${space}songs · Not in your library", factsLine(listOf("12 songs", "Not in your library"), " · "))
    }

    @Test
    fun theLetterRailShowsOnLongListsByName() {
        assertTrue(railShows(byName = true, count = RAIL_MIN_ITEMS))
        assertFalse(railShows(byName = true, count = RAIL_MIN_ITEMS - 1))
        assertFalse(railShows(byName = false, count = 500))
    }
}
