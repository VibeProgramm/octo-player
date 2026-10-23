package app.winters.octo.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Facts lines that break only between facts, and room kept for the letter
// rail.
class ControlLineFitTest {
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
