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

// What picking a line of the chart country picker does: go back to the
// listener's own charts, ask what to do with the server's country, or ask
// what to do with another country.
enum class CountryPick { Yours, Server, Country }

// One line of a country picker: the country it picks (null when there is
// none to name), its words, a line about it, whether it is the one chosen,
// whether it is among the lines at the top, above the rest of the
// countries, and what picking it does.
data class CountryRow(
    val code: String?,
    val label: String,
    val detail: String?,
    val chosen: Boolean,
    val top: Boolean,
    val pick: CountryPick = CountryPick.Country,
)

// The chart country picker's lines, from what getCharts said of the
// listener's own charts: first "Yours (United States)", their own chart
// country, then "Same as the server (Canada)" while they chose a country of
// their own, then every country by name, a pinned one saying so. `viewing`
// is the country in view when it is not the listener's own.
fun chartCountryRows(own: ChartChoices, viewing: String?, name: (String) -> String): List<CountryRow> {
    val follows = own.setting == null || own.setting.equals(CHART_COUNTRY_AUTO, ignoreCase = true)
    val yours = own.country?.let { "Yours (${name(it)})" } ?: "Yours"
    val top = buildList {
        add(CountryRow(own.country, yours, null, chosen = viewing == null, top = true, pick = CountryPick.Yours))
        val server = own.serverCountry
        if (!follows && server != null) {
            add(CountryRow(server, "Same as the server (${name(server)})", null, chosen = false, top = true, pick = CountryPick.Server))
        }
    }
    val pinned = own.pinned.map { it.lowercase() }.toSet()
    val rest = chartCountryOrder(own.countries, null, emptyList(), name).map { code ->
        CountryRow(code, name(code), "Pinned".takeIf { code.lowercase() in pinned }, chosen = code == viewing, top = false)
    }
    return top + rest
}

// What the listener can do with a country picked in the chart country
// picker: only look at it on this page, make it their chart country, or
// have its charts as playlists too (or no longer).
enum class CountryAction { Look, UseForMine, AddPlaylists, RemovePlaylists, FollowServer }

// One answer in the choice that opens when a country is picked: what it
// does, its words, a line about it, and whether it can be picked now.
data class CountryChoice(val action: CountryAction, val label: String, val detail: String, val enabled: Boolean = true)

// The choice for a country picked in the chart country picker, `name`
// being its name. `server` is the server's own country picked as "Same as
// the server", which offers only a look or following the server again.
// A full set of pinned countries cannot take one more.
fun countryChoices(own: ChartChoices, code: String, name: String, server: Boolean = false): List<CountryChoice> {
    val look = CountryChoice(CountryAction.Look, "Just look here", "Nothing is saved")
    val everywhere = "Your chart playlists and Home switch to $name, in every app"
    if (server) return listOf(look, CountryChoice(CountryAction.FollowServer, "Use the server's country for my charts", everywhere))
    val most = own.maxPinned.takeIf { it > 0 } ?: DEFAULT_MAX_PINNED
    val pinned = own.pinned.any { it.equals(code, ignoreCase = true) }
    val playlists = when {
        pinned -> CountryChoice(CountryAction.RemovePlaylists, "Remove ${possessive(name)} playlists", "Popular right now · $name and the rest go away; yours stay")
        own.pinned.size >= most -> CountryChoice(CountryAction.AddPlaylists, "$most countries already have playlists", "Remove one to add $name", enabled = false)
        else -> CountryChoice(CountryAction.AddPlaylists, "Also add ${possessive(name)} playlists", "Keeps yours and adds Popular right now · $name and the rest")
    }
    return listOf(look, CountryChoice(CountryAction.UseForMine, "Use $name for my charts", everywhere), playlists)
}

// How many countries a listener may pin when the server does not say.
private const val DEFAULT_MAX_PINNED = 3

// A name that owns something: "Japan's", "Bahamas'".
private fun possessive(name: String): String = if (name.endsWith("s")) "$name'" else "$name's"

// Whether what was typed picks this country: inside its name, case and
// accents ignored ("cote" finds Côte d'Ivoire), or exactly its two-letter
// code. Nothing typed picks every country.
fun countryMatches(name: String, code: String, typed: String): Boolean {
    val words = searchKey(typed)
    if (words.isEmpty()) return true
    return words in searchKey(name) || code.equals(typed.trim(), ignoreCase = true)
}
