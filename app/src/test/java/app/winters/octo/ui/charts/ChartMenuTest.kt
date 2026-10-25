package app.winters.octo.ui.charts

import app.winters.octo.subsonic.CHART_NEW_SONGS
import app.winters.octo.subsonic.CHART_OVERALL
import app.winters.octo.subsonic.CHART_TRENDING
import app.winters.octo.subsonic.ChartChoice
import org.junit.Assert.assertEquals
import org.junit.Test

// The chart chips became one dropdown beside the country button: its list
// leads with the three main charts.
class ChartMenuTest {
    private fun chart(id: String, label: String) = ChartChoice(id = id, label = label)

    @Test
    fun theMainChartsLeadThenTheGenresAsTheServerGaveThem() {
        val server = listOf(
            chart("14", "Pop"),
            chart(CHART_TRENDING, "Trending Songs"),
            chart(CHART_OVERALL, "Top songs"),
            chart("18", "Hip-Hop/Rap"),
            chart(CHART_NEW_SONGS, "Best New Songs"),
        )
        assertEquals(
            listOf("Top songs", "Best New Songs", "Trending Songs", "Pop", "Hip-Hop/Rap"),
            chartMenu(server).map { it.label },
        )
    }

    @Test
    fun aCountryWithoutTheMainChartsListsItsGenres() {
        assertEquals(listOf("Pop"), chartMenu(listOf(chart("14", "Pop"))).map { it.label })
    }
}
