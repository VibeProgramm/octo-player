package app.winters.octo.ui.charts

import app.winters.octo.subsonic.CHART_NEW_SONGS
import app.winters.octo.subsonic.CHART_OVERALL
import app.winters.octo.subsonic.CHART_TRENDING
import app.winters.octo.subsonic.ChartChoice
import org.junit.Assert.assertEquals
import org.junit.Test

// The chart chips became one dropdown beside the country button: its list
// leads with the three main charts, and the country gives way before the
// chart's name is ever cut.
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

    // A 360dp phone leaves 234dp for the two buttons.
    private val room = controlRoom(360)

    @Test
    fun theControlLineLeavesRoomForPlayAndShuffle() {
        assertEquals(234, room)
    }

    @Test
    fun theCountryGivesWayToTheChartsName() {
        // "Japan" beside "Top songs".
        assertEquals(CountryForm.Name, countryForm(room, chart = 100, name = 78, code = 76, gap = 8))
        // "United States" beside "Best New Songs": the code.
        assertEquals(CountryForm.Code, countryForm(room, chart = 140, name = 150, code = 76, gap = 8))
        // "Christian & Gospel" leaves room for the globe alone.
        assertEquals(CountryForm.Globe, countryForm(room, chart = 170, name = 150, code = 76, gap = 8))
    }
}
