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

// The forms of the country button, widest first: its name, its code, and
// its globe alone.
enum class CountryForm { Name, Code, Globe }
