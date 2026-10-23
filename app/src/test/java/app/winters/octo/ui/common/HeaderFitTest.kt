package app.winters.octo.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// The page header on a 360dp phone: the title keeps its whole one-line
// width on a line of its own, however long, and a count joins it only when
// it fits whole. Widths are in dp at the header's sizes.
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
    @Test
    fun aCountFitsWholeAfterTheTitleOrIsLeftOff() {
        // "Favorites" after the back button with "1,204 songs".
        assertTrue(countFits(titleEnd = 50 + 150, count = 80, gap = gap, width = line))
        // "Recently removed" leaves too little room: the count goes, never cut.
        assertFalse(countFits(titleEnd = 50 + 270, count = 80, gap = gap, width = line))
        // Exactly filling the line still fits.
        assertTrue(countFits(titleEnd = 238, count = 80, gap = gap, width = line))
        assertFalse(countFits(titleEnd = 50, count = 0, gap = gap, width = line))
    }
}
