package app.winters.octo.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// The page header on a 360dp phone: the title keeps its whole one-line
// width on a line of its own, however long, and the control line gives
// the page's control its small form only when its words leave the filters
// too little room. Widths are in dp at the header's sizes.
class HeaderFitTest {
    private val gap = 10

    @Test
    fun theLongestTitlesKeepTheirWholeWidthAfterTheBackButton() {
        // "Recently removed", "Library health", "Server downloads" and a
        // German length one, "Zuletzt entfernte Titel": never cut to the line.
        for (title in listOf(270, 230, 280, 360)) {
            assertEquals(50 to title, titlePlace(back = 40, title = title, gap = gap))
        }
        assertEquals(0 to 200, titlePlace(back = 0, title = 200, gap = gap))
    }

    // The control line between the page's 16dp edges.
    private val line = 360 - 16 - 16
    private val playShuffle = 40 + 8 + 40

    @Test
    fun aSortKeepsItsWordsBesidePlayAndShuffle() {
        // "Recently changed" with Play and Shuffle, no chips.
        assertFalse(controlCompact(line, control = 190, actions = playShuffle, hasFilters = false, gap = gap, filtersMin = 96))
    }

    @Test
    fun aCountryTakesItsShortFormWhenTheChipsWouldHaveTooLittleRoom() {
        // "United States" beside the chips and Play and Shuffle.
        assertTrue(controlCompact(line, control = 150, actions = playShuffle, hasFilters = true, gap = gap, filtersMin = 96))
        // "Japan" leaves the chips enough.
        assertFalse(controlCompact(line, control = 100, actions = playShuffle, hasFilters = true, gap = gap, filtersMin = 96))
    }

    @Test
    fun noControlHasNoShortForm() {
        assertFalse(controlCompact(line, control = 0, actions = playShuffle, hasFilters = true, gap = gap, filtersMin = 96))
    }
}
