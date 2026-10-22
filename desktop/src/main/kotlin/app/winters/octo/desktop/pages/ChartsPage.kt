package app.winters.octo.desktop.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import app.winters.octo.design.ChipRow
import app.winters.octo.design.DesktopType
import app.winters.octo.design.GlazeCapsule
import app.winters.octo.design.OctoColors
import app.winters.octo.design.OctoIcons
import app.winters.octo.design.OctoType
import app.winters.octo.design.Space
import app.winters.octo.design.TextAction
import app.winters.octo.design.Txt
import app.winters.octo.design.WholeTxt
import app.winters.octo.design.scrollbar
import app.winters.octo.desktop.AppState
import app.winters.octo.desktop.home.HomeStore
import app.winters.octo.desktop.nav.Page
import app.winters.octo.desktop.nav.Visit
import app.winters.octo.desktop.search.Charts
import app.winters.octo.desktop.ui.LoadingLine
import app.winters.octo.desktop.ui.LocalBottomRoom
import app.winters.octo.desktop.ui.OnTitleCapitals
import app.winters.octo.desktop.ui.SectionTitle
import app.winters.octo.desktop.ui.pagePadding
import app.winters.octo.desktop.ui.rememberListState
import app.winters.octo.discovery.CHARTS_ROW
import app.winters.octo.discovery.chartCountryRows
import app.winters.octo.discovery.rankedBy
import app.winters.octo.subsonic.CHART_OVERALL
import app.winters.octo.subsonic.TopSongs
import java.util.Locale

// The charts and new songs an Octo server reads from Apple Music: a
// country's most played songs, Best New Songs, Trending Songs and a chart
// per genre, one at a time, picked with the chips. Each song plays from
// the library when the library has it and is added with its "+"
// otherwise, as on the search page. On a server that saves it, the country
// beside the title opens every country: one looked at can be made the
// listener's own or pinned.
@Composable
fun ChartsPage(app: AppState, visit: Visit) {
    val charts = app.search?.charts ?: return
    LaunchedEffect(charts) { charts.opened() }
    LaunchedEffect(charts, charts.country) { charts.loadChoices(charts.country) }
    LaunchedEffect(charts, charts.selected, charts.country) { charts.load(charts.selected, charts.country) }
    val list = rememberListState(app.navigator, visit)
    val choices = charts.choices?.chart.orEmpty()
    val top = charts.lists[charts.key(charts.selected, charts.country)]
    // The line under the title keeps the last chart's words for the same
    // country while the next chart comes, so nothing under it moves.
    var shownCaption by remember(charts.country) { mutableStateOf<String?>(null) }
    LaunchedEffect(top) { top?.let(::caption)?.let { shownCaption = it } }
    LazyColumn(Modifier.scrollbar(list, LocalBottomRoom.current), state = list, contentPadding = pagePadding(LocalBottomRoom.current)) {
        item(key = "title") { ChartsTitle(app, charts, top?.let(::caption) ?: shownCaption) }
        if (choices.isNotEmpty()) {
            item(key = "chips") {
                ChipRow(
                    choices.map { it.id },
                    charts.selected,
                    label = { id -> choices.firstOrNull { it.id == id }?.label ?: id },
                    onSelect = { charts.selected = it },
                    modifier = Modifier.padding(top = Space.M, bottom = Space.M),
                )
            }
        }
        when {
            charts.offered == false -> item(key = "none") { NothingHere("No charts here", "This server doesn't have charts.") }
            top == null -> item(key = "loading") { LoadingLine() }
            top.entry.none { it.song != null } -> item(key = "empty") {
                NothingHere("Nothing in this chart right now", "Apple Music has no songs for it at the moment. Try another.")
            }
            else -> chartSongs(app, top, shown = null)
        }
    }
}

// The page's title, and on a server that shows other countries, the
// country in view beside it: a quiet capsule on the middle of the title's
// capitals that opens every country, then, while another country is
// looked at, the words that make it the listener's own or pin it. All of
// it sits on the title's line and the line under it is always held, so
// nothing below moves between the listener's own country and another.
@Composable
private fun ChartsTitle(app: AppState, charts: Charts, detail: String?) {
    val country = charts.shownCountry?.takeIf { charts.canPickCountry }
    var anchor by remember { mutableStateOf(IntRect.Zero) }
    Column(Modifier.padding(bottom = Space.Xl)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Space.L)) {
            WholeTxt("Charts", DesktopType.pageTitle)
            OnTitleCapitals(DesktopType.pageTitle) {
                if (country != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlazeCapsule(
                            OctoIcons.Globe,
                            countryName(country),
                            { showCountryMenu(app, charts, anchor) },
                            Modifier.onGloballyPositioned { anchor = it.boundsInWindow().roundToIntRect() },
                            height = 28.dp,
                            quiet = true,
                        )
                        Spacer(Modifier.width(Space.S))
                        if (charts.country != null) CountryActions(charts)
                        charts.problem?.let { Txt(it, OctoType.caption, OctoColors.TextMuted, Modifier.padding(start = Space.S)) }
                    }
                }
            }
        }
        Box(Modifier.padding(top = Space.Xs).height(CaptionHeight)) {
            if (detail != null) Txt(detail, DesktopType.meta, OctoColors.TextMuted)
        }
    }
}

// The line under the title, held whether or not it has words.
private val CaptionHeight = 18.dp

// Every country, under the capsule that opened it. `typed` starts the
// search with words already in it.
internal fun showCountryMenu(app: AppState, charts: Charts, anchor: IntRect, typed: MutableState<String>? = null) {
    app.popups.showUnder(anchor, width = CountryMenuWidth, scrollsItself = true) { close -> ChartCountryMenu(charts, close, typed) }
}

@Composable
private fun ColumnScope.ChartCountryMenu(charts: Charts, close: () -> Unit, typed: MutableState<String>?) {
    val own = charts.own ?: return
    val rows = chartCountryRows(own, charts.country, ::countryName)
    val pick = { code: String? -> if (code == null) charts.followServer() else charts.pickCountry(code) }
    if (typed != null) CountryMenu(rows, pick, close, typed) else CountryMenu(rows, pick, close)
}

// While another country is looked at, quiet words beside its capsule:
// make it the listener's own, or pin it so its charts come as playlists
// too. A full set of pins says so.
@Composable
private fun CountryActions(charts: Charts) {
    TextAction("Use as my country", charts::useAsMine, enabled = !charts.saving)
    TextAction(if (charts.isPinned) "Unpin" else "Pin", charts::togglePin, enabled = !charts.saving && charts.canPin)
    if (!charts.canPin) Txt("${charts.maxPinned} pinned", OctoType.caption, OctoColors.TextMuted, Modifier.padding(start = Space.Xs))
}

// Who ranked the chart and for which country: "Most played on Apple Music
// in United States".
private fun caption(top: TopSongs): String? {
    val by = rankedBy(top.source, top.chart) ?: return null
    val country = top.country?.takeIf { it.length == 2 }?.let { Locale("", it.uppercase()).displayCountry }
    return if (country.isNullOrBlank()) by else "$by in $country"
}

// A chart's songs with Play all above them; `shown` keeps only the first
// few (Home's row).
private fun LazyListScope.chartSongs(app: AppState, top: TopSongs, shown: Int?) {
    val entries = top.entry.filter { it.song != null }.let { if (shown != null) it.take(shown) else it }
    val songs = entries.map { it.song!! }
    if (shown == null) {
        item(key = "play") {
            GlazeCapsule(OctoIcons.Play, "Play all", { app.play(songs, 0) }, Modifier.padding(bottom = Space.M))
        }
    }
    itemsIndexed(entries, key = { i, e -> "chart:${top.country.orEmpty()}:${top.chart}:$i:${e.song!!.id}" }) { index, entry ->
        Box(Modifier.animateItem()) { RankedSong(app, songs, index, entry, withArtist = true) }
    }
}

// Home's Charts row: the most played songs right now in the listener's own
// country, the first ten, with "See all" opening the Charts page on it.
// Only from a server that has charts. The row shows the chart as the
// server last gave it (HomeStore) until the server answers again; a server
// without charts empties it.
internal fun LazyListScope.chartsRow(app: AppState, charts: Charts, home: HomeStore) {
    moving(key = "charts-ask") {
        LaunchedEffect(charts, home) {
            charts.check()
            charts.load(CHART_OVERALL)
            snapshotFlow { charts.offered to charts.lists[charts.key(CHART_OVERALL)] }.collect { (offered, top) ->
                when {
                    offered == false -> home.chartAnswered(null)
                    top != null -> home.chartAnswered(top)
                }
            }
        }
    }
    val top = home.shelves.chart ?: return
    if (top.entry.none { it.song != null }) return
    moving(key = "charts-title") {
        Column(verticalArrangement = Arrangement.spacedBy(Space.Xs)) {
            SectionTitle(top.name ?: "Popular right now", action = "See all", bottom = 0.dp) {
                charts.showOwn()
                charts.selected = CHART_OVERALL
                app.navigator.go(Page.Charts)
            }
            caption(top)?.let { Txt(it, OctoType.caption, OctoColors.TextMuted, Modifier.padding(bottom = Space.L)) }
        }
    }
    chartSongs(app, top, shown = CHARTS_ROW)
}
