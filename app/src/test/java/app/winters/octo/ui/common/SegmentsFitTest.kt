package app.winters.octo.ui.common

import app.winters.octo.sound.CrossfeedPreset
import app.winters.octo.sound.label
import app.winters.octo.ui.favourites.FavouriteSegment
import app.winters.octo.ui.history.HistoryViews
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ceil

// Every row of segments in the app, with the check before the chosen one's
// words, either has room for its longest words at 320dp or is a dropdown;
// never words cut. Widths are dp, estimated from the words at the label
// size (13.5sp semibold, about 7.5dp a letter) and 11sp (6dp a letter).
class SegmentsFitTest {
    private fun full(word: String) = ceil(word.length * 7.5).toInt()
    private fun small(word: String) = word.length * 6

    // The rows laid out by themselves, inside a card on a 320dp phone: the
    // screen less 20dp page edges and 16dp card edges.
    private val cardRoom = 320 - 2 * 20 - 2 * 16

    private val standalone = mapOf(
        "Equalizer mode" to listOf("Graphic", "Parametric"),
        "Filter type" to listOf("Peak", "Low shelf", "High shelf"),
        "Loudness" to listOf("Off", "Song", "Album", "Smart"),
        "Crossfeed" to CrossfeedPreset.entries.map { it.label },
    )

    private fun fits(words: List<String>, room: Int) =
        segmentsFitIn(words.size, words.maxOf { full(it) } + SegmentCheckRoom, room)

    @Test
    fun theCheckTakesRoomFromEverySegment() {
        val need = segmentsWidth(3, 50 + SegmentCheckRoom, SegmentSides, SegmentSides)
        assertTrue(segmentsFitIn(3, 50 + SegmentCheckRoom, need))
        assertFalse(segmentsFitIn(3, 50 + SegmentCheckRoom, need - 1))
        assertFalse(segmentsFitIn(3, 50 + SegmentCheckRoom + 1, need))
    }

    @Test
    fun aStandaloneRowShowsAllItsWordsWholeOrIsADropdown() {
        for ((name, words) in standalone) {
            val shown = fits(words, cardRoom)
            if (shown) {
                // Each cell holds the longest words and the check.
                val cell = (cardRoom - SegmentSides) / words.size - SegmentSides
                assertTrue("$name cell $cell", cell >= words.maxOf { full(it) } + SegmentCheckRoom)
            }
        }
        // The rows that cannot hold their longest words with the check at
        // 320dp are dropdowns, the short ones stay segments.
        assertTrue(fits(standalone.getValue("Equalizer mode"), cardRoom))
        assertFalse(fits(standalone.getValue("Loudness"), cardRoom))
        assertFalse(fits(standalone.getValue("Filter type"), cardRoom))
        assertFalse(fits(standalone.getValue("Crossfeed"), cardRoom))
    }

    @Test
    fun everyStandaloneRowIsSegmentsOnATablet() {
        for ((name, words) in standalone) assertTrue(name, fits(words, 600 - 2 * 20 - 2 * 16))
    }

    private val button = 40
    private val gap = 8

    private fun plan(words: List<String>, tail: List<PlanItem>, more: Boolean, screen: Int) = planLine(
        width = screen - 12 - 16,
        gap = gap,
        lead = emptyList(),
        segments = PlanSegments(
            full = segmentsWidth(words.size, words.maxOf { full(it) } + SegmentCheckRoom, SegmentSides, SegmentSides),
            small = segmentsWidth(words.size, words.maxOf { small(it) } + SegmentCheckRoom, SegmentSides, SegmentSides),
            dropdown = words.maxOf { full(it) } + 40,
        ),
        tail = tail,
        moreButton = button,
        pageMore = more,
    )

    // A control line's segments are shown in the form the plan gives only
    // when that form, the other things on the line and the gaps fit.
    private fun assertFitsOrDropdown(name: String, words: List<String>, tail: List<PlanItem>, more: Boolean, screen: Int) {
        val line = plan(words, tail, more, screen)
        if (line.segments == SegmentsForm.Dropdown) return
        val own = when (line.segments) {
            SegmentsForm.Full -> segmentsWidth(words.size, words.maxOf { full(it) } + SegmentCheckRoom, SegmentSides, SegmentSides)
            else -> segmentsWidth(words.size, words.maxOf { small(it) } + SegmentCheckRoom, SegmentSides, SegmentSides)
        }
        val shown = tail.indices.filter { line.forms[it] != null }
        val parts = 1 + shown.size + (if (line.more) 1 else 0)
        val total = own + shown.sumOf { tail[it].widths[line.forms[it]!!] } + (if (line.more) button else 0) + gap * (parts - 1)
        assertTrue("$name at $screen: $total over ${screen - 12 - 16}", total <= screen - 12 - 16)
    }

    @Test
    fun theControlLineSegmentsFitWithTheCheckOrBecomeADropdown() {
        val favourites = FavouriteSegment.entries.map { it.label }
        val playShuffle = listOf(PlanItem(listOf(button), null), PlanItem(listOf(button), null))
        val mostPlayed = listOf(PlanItem(listOf(130), overflow = 0))
        for (screen in listOf(320, 360, 393, 412, 600)) {
            assertFitsOrDropdown("Favorites", favourites, playShuffle, true, screen)
            assertFitsOrDropdown("History", HistoryViews, mostPlayed, false, screen)
        }
        // Three segments beside Play and Shuffle with the check do not fit
        // at 320dp and become a dropdown; the two History views beside one
        // button step down to the small size.
        assertEquals(SegmentsForm.Dropdown, plan(favourites, playShuffle, true, 320).segments)
        assertEquals(SegmentsForm.Small, plan(HistoryViews, mostPlayed, false, 320).segments)
    }
}
