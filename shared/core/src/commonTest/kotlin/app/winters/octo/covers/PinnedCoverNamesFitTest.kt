package app.winters.octo.covers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.math.max

// A chart cover's name is set whole, with no word broken or line cut, or the
// cover is its artwork alone.
class PinnedCoverNamesFitTest {
    private val setter = FakeTypesetter()
    private val book = CoverBook.Default
    private val lists = listOf("Popular", "Best New Songs", "Trending Songs", "Top Hip-Hop/Rap", "Top Singer/Songwriter", "Top Children's Music", "Top R&B/Soul")
    private val sides = listOf(300, 600, 1200)

    private fun countries() = Locale.getISOCountries().map { Locale("", it).getDisplayCountry(Locale.ENGLISH) }

    private fun words(name: String, side: Int) =
        coverWords(CoverSpec("chart-$name", name, CHART_COVER_LINE, "50 songs", seededPalette(name)), side, setter, book)

    @Test
    fun everyPinnedNameIsSetWholeOrTheCoverIsArtworkOnly() {
        var shownWhole = 0
        for (list in lists) for (country in countries()) for (side in sides) {
            val name = "$list · $country"
            val shown = words(name, side)
            if (shown.isEmpty()) continue
            val title = shown.single { it.role == CoverRole.Title }
            assertEquals(name, title.text)
            assertFalse("$name at $side is cut", title.measured.cut)
            assertTrue("$name at $side has ${title.measured.lines} lines", title.measured.lines <= book.layout.title.maxLines)
            val floor = max(book.layout.title.floorPx, side * book.layout.title.floorSize)
            assertTrue("$name at $side is under the floor", title.type.sizePx >= kotlin.math.floor(floor))
            for (run in unbreakableRuns(name)) {
                assertTrue("$name at $side breaks '$run'", setter.widthOf(run, title.type) <= title.width)
            }
            for (other in shown) {
                assertFalse("${other.role} of $name at $side is cut", other.measured.cut)
                assertTrue("${other.role} of $name at $side runs past the cover", other.top + other.height <= side)
            }
            shownWhole++
        }
        assertTrue(shownWhole > 0)
    }

    @Test
    fun popularJapanKeepsItsLightLineAndFooterAtSixHundred() {
        val shown = words("Popular · Japan", 600)
        assertEquals(listOf(CoverRole.Title, CoverRole.Line, CoverRole.Footer), shown.map { it.role })
    }
}
