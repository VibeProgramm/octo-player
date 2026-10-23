package app.winters.octo.ui.charts

import app.winters.octo.subsonic.CHART_NEW_SONGS
import app.winters.octo.subsonic.CHART_OVERALL
import app.winters.octo.subsonic.CHART_TRENDING
import app.winters.octo.subsonic.ChartChoice

// The chart button's list: Top songs, Best New Songs and Trending Songs
// first, whatever order the server gave, then the genres as it gave them.
fun chartMenu(choices: List<ChartChoice>): List<ChartChoice> {
    val lead = listOf(CHART_OVERALL, CHART_NEW_SONGS, CHART_TRENDING)
    return lead.mapNotNull { id -> choices.firstOrNull { it.id == id } } + choices.filter { it.id !in lead }
}

// How the country button is drawn beside the chart button.
enum class CountryForm { Name, Code, Globe }

// The widest form of the country button that fits beside the chart button,
// all in dp: the room the two have, the chart button's width, the country
// button's with its name and with its code, and the room between them. The
// chart button always keeps its words whole; the country gives way first.
fun countryForm(room: Int, chart: Int, name: Int, code: Int, gap: Int): CountryForm = when {
    chart + gap + name <= room -> CountryForm.Name
    chart + gap + code <= room -> CountryForm.Code
    else -> CountryForm.Globe
}

// The room the control line leaves the country and chart buttons on a
// screen `screen` dp wide: its 12dp and 16dp edges, round Play and Shuffle
// and the room before them.
fun controlRoom(screen: Int): Int = screen - 12 - 16 - (40 + 8 + 40) - 10
