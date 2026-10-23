package app.winters.octo.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

// The control line on phones from 320dp to 600dp wide: everything shows
// while it fits; then shorter forms, then items into More by priority (the
// sort first), and only then the segments' words step down, a dropdown last.
// Widths are dp, measured roughly from the app's words at their sizes; the
// line is the screen less its 12dp and 16dp edges.
class ControlLinePlanTest {
    private val gap = 8
    private val button = 40
    private fun line(screen: Int) = screen - 12 - 16

    private val playShuffle = listOf(PlanItem(listOf(button), null), PlanItem(listOf(button), null))

    // Favorites on Liked songs: the sort ("Date liked"), the three parts,
    // then Play, Shuffle and More with Keep downloaded.
    private fun favorites(screen: Int) = planLine(
        width = line(screen),
        gap = gap,
        lead = listOf(PlanItem(listOf(150), overflow = 0)),
        // "Artists" about 52dp at full size and 42dp at 11sp.
        segments = PlanSegments(full = segmentsWidth(3, 52, 8, 8), small = segmentsWidth(3, 42, 8, 8), dropdown = 95),
        tail = playShuffle,
        moreButton = button,
        pageMore = true,
    )

    @Test
    fun favoritesShowsItsSegmentsWithTheSortInMoreAt360() {
        assertEquals(LinePlan(listOf(null, 0, 0), SegmentsForm.Full, more = true), favorites(360))
    }

    @Test
    fun favoritesAcrossPhones() {
        assertEquals(LinePlan(listOf(0, 0, 0), SegmentsForm.Full, more = true), favorites(600))
        assertEquals(LinePlan(listOf(null, 0, 0), SegmentsForm.Full, more = true), favorites(412))
        assertEquals(LinePlan(listOf(null, 0, 0), SegmentsForm.Full, more = true), favorites(393))
        // At 320 even the smaller words do not fit: the parts are a dropdown.
        assertEquals(LinePlan(listOf(null, 0, 0), SegmentsForm.Dropdown, more = true), favorites(320))
    }

    // History on Most played: the two views, then the time range.
    private fun history(screen: Int, mostPlayed: Boolean = true) = planLine(
        width = line(screen),
        gap = gap,
        lead = emptyList(),
        // "Recently played" about 110dp at full size and 90dp at 11sp.
        segments = PlanSegments(full = segmentsWidth(2, 110, 8, 8), small = segmentsWidth(2, 90, 8, 8), dropdown = 140),
        tail = if (mostPlayed) listOf(PlanItem(listOf(130), overflow = 0)) else emptyList(),
        moreButton = button,
        pageMore = false,
    )

    @Test
    fun historyAcrossPhones() {
        assertEquals(LinePlan(listOf(0), SegmentsForm.Full, more = false), history(600))
        assertEquals(LinePlan(listOf(0), SegmentsForm.Full, more = false), history(412))
        // The range moves into More, which shows for it.
        assertEquals(LinePlan(listOf(null), SegmentsForm.Full, more = true), history(393))
        assertEquals(LinePlan(listOf(null), SegmentsForm.Full, more = true), history(360))
        assertEquals(LinePlan(listOf(null), SegmentsForm.Full, more = true), history(320))
        // Recently played has no range: the views fit everywhere.
        listOf(320, 360, 393, 412, 600).forEach { assertEquals(LinePlan(emptyList(), SegmentsForm.Full, more = false), history(it, mostPlayed = false)) }
    }

    // Charts: the country (by name, code or globe), the chart, Play and
    // Shuffle.
    private fun charts(screen: Int, chart: Int) = planLine(
        width = line(screen),
        gap = gap,
        lead = listOf(PlanItem(listOf(150, 76, 54), overflow = 0), PlanItem(listOf(chart), null)),
        segments = null,
        tail = playShuffle,
        moreButton = button,
        pageMore = false,
    )

    @Test
    fun chartsShortensTheCountryBeforeItMoves() {
        // "United States" beside "Top songs".
        assertEquals(LinePlan(listOf(0, 0, 0, 0), SegmentsForm.Full, more = false), charts(600, chart = 105))
        assertEquals(LinePlan(listOf(0, 0, 0, 0), SegmentsForm.Full, more = false), charts(412, chart = 105))
        assertEquals(LinePlan(listOf(0, 0, 0, 0), SegmentsForm.Full, more = false), charts(393, chart = 105))
        assertEquals(LinePlan(listOf(1, 0, 0, 0), SegmentsForm.Full, more = false), charts(360, chart = 105))
        assertEquals(LinePlan(listOf(1, 0, 0, 0), SegmentsForm.Full, more = false), charts(320, chart = 105))
    }

    @Test
    fun aLongChartNameTakesTheCountryDownToItsGlobeThenIntoMore() {
        // "Christian & Gospel", about 170dp as a button.
        assertEquals(LinePlan(listOf(0, 0, 0, 0), SegmentsForm.Full, more = false), charts(600, chart = 170))
        assertEquals(LinePlan(listOf(1, 0, 0, 0), SegmentsForm.Full, more = false), charts(412, chart = 170))
        assertEquals(LinePlan(listOf(1, 0, 0, 0), SegmentsForm.Full, more = false), charts(393, chart = 170))
        assertEquals(LinePlan(listOf(2, 0, 0, 0), SegmentsForm.Full, more = false), charts(360, chart = 170))
        assertEquals(LinePlan(listOf(null, 0, 0, 0), SegmentsForm.Full, more = true), charts(320, chart = 170))
    }

    @Test
    fun itemsMoveByPriorityAndOnlyThoseWithAMoreRow() {
        val plan = planLine(
            width = 200,
            gap = gap,
            lead = listOf(PlanItem(listOf(100), overflow = 1), PlanItem(listOf(100), overflow = 0), PlanItem(listOf(60), overflow = null)),
            segments = null,
            tail = emptyList(),
            moreButton = button,
            pageMore = false,
        )
        // The sort (priority 0) goes first, then the next; the fixed one stays.
        assertEquals(LinePlan(listOf(null, null, 0), SegmentsForm.Full, more = true), plan)
    }
}
