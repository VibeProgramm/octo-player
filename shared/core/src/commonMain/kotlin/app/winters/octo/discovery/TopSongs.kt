package app.winters.octo.discovery

import app.winters.octo.catalog.SongIdentity
import app.winters.octo.catalog.searchKey
import app.winters.octo.subsonic.CHART_COUNTRY_AUTO
import app.winters.octo.subsonic.CHART_NEW_SONGS
import app.winters.octo.subsonic.CHART_TRENDING
import app.winters.octo.subsonic.ChartChoices
import app.winters.octo.subsonic.Extension
import app.winters.octo.subsonic.OCTO_TOP_SONGS
import app.winters.octo.subsonic.OCTO_TOP_SONGS_CHARTS
import app.winters.octo.subsonic.OCTO_TOP_SONGS_COUNTRIES
import app.winters.octo.subsonic.SubsonicException
import app.winters.octo.subsonic.TOP_SONGS_APPLE
import app.winters.octo.subsonic.TOP_SONGS_DEEZER
import app.winters.octo.subsonic.TOP_SONGS_LASTFM
import app.winters.octo.subsonic.lists
import kotlin.math.roundToLong

// How many of an artist's top songs a search asks the server for, and how
// many it shows before "Show all".
const val SEARCH_TOP_SONGS = 20
const val SEARCH_TOP_SHOWN = 5

// How many of the chart's songs an empty search asks for, and shows before
// "Show all".
const val TOP_CHART_SONGS = 50
const val TOP_CHART_SHOWN = 10

// Which of a search's artists the search was for: the first whose name is
// what was typed, case, accents and a leading "The" aside. None when no
// name is, so "daft" or a song's title shows no top songs.
fun <T> searchedArtist(query: String, artists: List<T>, name: (T) -> String): T? {
    if (query.isBlank()) return null
    return artists.firstOrNull { SongIdentity.sameArtistName(name(it), query.trim()) }
}

// Last.fm's play count, short: "940 plays", "12K plays", "2.5M plays".
fun playsText(plays: Long): String {
    if (plays == 1L) return "1 play"
    if (plays < 1_000) return "$plays plays"
    val units = listOf(1_000L to "K", 1_000_000L to "M", 1_000_000_000L to "B")
    var at = units.indexOfLast { plays >= it.first }
    var tenths = (plays * 10.0 / units[at].first).roundToLong()
    // 999,960 rounds to 1000K, which is 1M.
    if (tenths >= 10_000 && at < units.lastIndex) {
        at++
        tenths = (plays * 10.0 / units[at].first).roundToLong()
    }
    val number = when {
        tenths >= 100 -> "${(tenths + 5) / 10}"
        tenths % 10 == 0L -> "${tenths / 10}"
        else -> "${tenths / 10}.${tenths % 10}"
    }
    return "$number${units[at].second} plays"
}

// What a ranked list is ranked by, in a person's words, or null when the
// server did not say. `chart` is the chart's id, which tells Apple Music's
// picks of the week and its trending songs from its most played.
fun rankedBy(source: String, chart: String? = null): String? = when (source) {
    TOP_SONGS_LASTFM -> "Most played on Last.fm"
    TOP_SONGS_DEEZER -> "Most popular on Deezer"
    TOP_SONGS_APPLE -> when (chart) {
        CHART_NEW_SONGS -> "Picked by Apple Music"
        CHART_TRENDING -> "Trending on Apple Music"
        else -> "Most played on Apple Music"
    }
    else -> null
}

// How many of the overall chart's songs Home's Charts row shows.
const val CHARTS_ROW = 10

// How many songs a chart holds at most: Best New Songs and Trending Songs
// up to 100, every other chart 50.
fun chartSongs(chart: String): Int = if (chart == CHART_NEW_SONGS || chart == CHART_TRENDING) 100 else 50

// Whether a server's extensions offer the Charts page: octoTopSongs at the
// version whose getTopChart takes a chart.
fun chartsOffered(extensions: List<Extension>): Boolean = extensions.lists(OCTO_TOP_SONGS, OCTO_TOP_SONGS_CHARTS)

// The line under the buttons when a chart country or pin was not saved.
const val CHART_SAVE_FAILED = "Couldn't save. Try again."

// The server's own words when it refused a chart call for a reason the
// listener can act on: too many countries looked at in an hour (code 0) or
// a country or pin it does not take (code 10). Null for anything else.
fun chartRefusal(e: SubsonicException): String? =
    (e as? SubsonicException.Server)?.takeIf { it.code == 0 || it.code == 10 }?.message?.takeIf { it.isNotBlank() }

// Whether a server's extensions offer chart countries: octoTopSongs at the
// version whose chart calls take a country and that saves the listener's
// own country and pins.
fun chartCountriesOffered(extensions: List<Extension>): Boolean = extensions.lists(OCTO_TOP_SONGS, OCTO_TOP_SONGS_COUNTRIES)

// The countries in the picker's order: the listener's own, then their
// pinned ones, then the one in view when it is neither, then the rest by
// name, accents aside.
fun chartCountryOrder(
    codes: List<String>,
    own: String?,
    pinned: List<String>,
    name: (String) -> String,
    viewing: String? = null,
): List<String> {
    val first = (listOfNotNull(own) + pinned + listOfNotNull(viewing)).distinct()
    val rest = codes.filter { it !in first }.sortedBy { searchKey(name(it)) }
    return first + rest
}

// One line of a country picker: the country it picks (null for following
// the server's chart country), its words, a line about it, whether it is
// the one chosen, and whether it is among the lines at the top, above the
// rest of the countries.
data class CountryRow(
    val code: String?,
    val label: String,
    val detail: String?,
    val chosen: Boolean,
    val top: Boolean,
)

// The chart country picker's lines, from what getCharts said of the
// listener's own country: first "Same as the server (United States)",
// which saves following the server's country, then the countries in
// chartCountryOrder's order. `viewing` is the country in view when it is
// not the listener's own. "Yours" marks a country the listener chose, so
// it is left off while they follow the server's.
fun chartCountryRows(own: ChartChoices, viewing: String?, name: (String) -> String): List<CountryRow> {
    val follows = own.setting == null || own.setting.equals(CHART_COUNTRY_AUTO, ignoreCase = true)
    val mine = own.setting?.lowercase()?.takeUnless { follows }
    val order = chartCountryOrder(own.countries, mine, own.pinned, name, viewing)
    val top = (listOfNotNull(mine) + own.pinned + listOfNotNull(viewing)).toSet()
    val server = own.serverCountry?.let { "Same as the server (${name(it)})" } ?: "Same as the server"
    return listOf(CountryRow(null, server, null, chosen = viewing == null && follows, top = true)) +
        order.map { code ->
            CountryRow(
                code = code,
                label = name(code),
                detail = when {
                    code == mine -> "Yours"
                    code in own.pinned -> "Pinned"
                    else -> null
                },
                chosen = if (viewing == null) code == mine else code == viewing,
                top = code in top,
            )
        }
}

// Whether what was typed picks this country: inside its name, case and
// accents ignored ("cote" finds Côte d'Ivoire), or exactly its two-letter
// code. Nothing typed picks every country.
fun countryMatches(name: String, code: String, typed: String): Boolean {
    val words = searchKey(typed)
    if (words.isEmpty()) return true
    return words in searchKey(name) || code.equals(typed.trim(), ignoreCase = true)
}
