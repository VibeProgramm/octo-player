package app.winters.octo.desktop.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import app.winters.octo.design.ControlHeight
import app.winters.octo.design.DesktopType
import app.winters.octo.design.GlazeCapsule
import app.winters.octo.design.GlazeRound
import app.winters.octo.design.MenuTitle
import app.winters.octo.design.OctoColors
import app.winters.octo.design.OctoIcons
import app.winters.octo.design.OctoType
import app.winters.octo.design.Space
import app.winters.octo.design.Txt
import app.winters.octo.design.WholeTxt
import app.winters.octo.design.hoverLift
import app.winters.octo.design.scrollbar
import app.winters.octo.desktop.AppState
import app.winters.octo.desktop.home.HomeStore
import app.winters.octo.desktop.nav.Page
import app.winters.octo.desktop.nav.Visit
import app.winters.octo.desktop.search.Charts
import app.winters.octo.desktop.ui.ChoiceButton
import app.winters.octo.desktop.ui.ControlLine
import app.winters.octo.desktop.ui.PageTitle
import app.winters.octo.desktop.ui.TitleToLine
import app.winters.octo.desktop.ui.LoadingLine
import app.winters.octo.desktop.ui.LocalBottomRoom
import app.winters.octo.desktop.ui.SectionTitle
import app.winters.octo.desktop.ui.pagePadding
import app.winters.octo.desktop.ui.rememberListState
import app.winters.octo.discovery.CHARTS_ROW
import app.winters.octo.discovery.CountryAction
import app.winters.octo.discovery.CountryPick
import app.winters.octo.discovery.CountryRow
import app.winters.octo.discovery.chartCountryRows
import app.winters.octo.discovery.countryChoices
import app.winters.octo.subsonic.CHART_OVERALL
import app.winters.octo.subsonic.TopSongs

// The charts and new songs an Octo server reads from Apple Music: a
// country's most played songs, Best New Songs, Trending Songs and a chart
// per genre, one at a time, picked in the chart button's menu. Each song plays from
// the library when the library has it and is added with its "+"
// otherwise, as on the search page. The title has its line; the line
// under it holds the country button, the chart button, and Play and
// Shuffle. On a
// server that saves it, a country picked in the country button's list asks
// what to do with it; a country only looked at is let go with the page.
@Composable
fun ChartsPage(app: AppState, visit: Visit) {
    val charts = app.search?.charts ?: return
    LaunchedEffect(charts) { charts.opened() }
    DisposableEffect(charts) { onDispose { charts.showOwn() } }
    LaunchedEffect(charts, charts.country) { charts.loadChoices(charts.country) }
    LaunchedEffect(charts, charts.selected, charts.country) { charts.load(charts.selected, charts.country) }
    val list = rememberListState(app.navigator, visit)
    val choices = charts.choices?.chart.orEmpty()
    val top = charts.lists[charts.key(charts.selected, charts.country)]
    val failed = charts.failures[charts.key(charts.selected, charts.country)] ?: charts.country?.let { charts.choiceFailures[it] }
    val songs = top?.entry.orEmpty().mapNotNull { it.song }
    LazyColumn(Modifier.scrollbar(list, LocalBottomRoom.current), state = list, contentPadding = pagePadding(LocalBottomRoom.current)) {
        item(key = "title") { PageTitle("Charts", bottom = TitleToLine) }
        item(key = "controls") {
            var anchor by remember { mutableStateOf(IntRect.Zero) }
            val country = charts.shownCountry?.takeIf { charts.canPickCountry }
            ControlLine(
                control = {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.M), verticalAlignment = Alignment.CenterVertically) {
                        if (country != null) {
                            GlazeCapsule(
                                OctoIcons.Globe,
                                countryName(country),
                                { showCountryMenu(app, charts, anchor) },
                                Modifier.widthIn(max = CountryButtonMax).onGloballyPositioned { anchor = it.boundsInWindow().roundToIntRect() },
                                height = ControlHeight.L,
                                quiet = true,
                                enabled = !charts.saving,
                            )
                        }
                        // The chart shown, picked from its menu.
                        if (choices.isNotEmpty()) {
                            ChoiceButton(
                                choices.map { it.id },
                                charts.selected,
                                label = { id -> choices.firstOrNull { it.id == id }?.label ?: id },
                                onSelect = { charts.selected = it },
                                // Apple's own lists first, then the genres.
                                group = { id -> choices.firstOrNull { it.id == id }?.kind == "genre" },
                            )
                        }
                    }
                },
                actions = {
                    GlazeRound(OctoIcons.Play, "Play", { app.play(songs, 0) }, enabled = songs.isNotEmpty(), lit = true)
                    GlazeRound(OctoIcons.Shuffle, "Shuffle", { app.play(songs, shuffle = true) }, enabled = songs.isNotEmpty())
                },
            )
        }
        when {
            charts.offered == false -> item(key = "none") { NothingHere("No charts here", "This server doesn't have charts.") }
            top == null && failed != null -> item(key = "failed") { NothingHere("Couldn't get this chart", failed) }
            top == null -> item(key = "loading") { LoadingLine() }
            top.entry.none { it.song != null } -> item(key = "empty") {
                NothingHere("Nothing in this chart right now", "Apple Music has no songs for it at the moment. Try another.")
            }
            else -> chartSongs(app, top, shown = null)
        }
    }
}

// The widest the country button grows; a longer name shortens inside it.
private val CountryButtonMax = 220.dp

// What the country menu's first line says, in every state.
internal const val COUNTRY_MENU_TITLE = "Choose a country"

// How wide the choice for a picked country is: a little wider than the
// country list, so none of its lines ends on a word alone.
private val CountryChoiceWidth = CountryMenuWidth + 24.dp

// Every country, under the button that opened it, the chart's source line
// at its top: the listener's own first, the server's while they chose one
// of their own, then every country by name. Their own goes back to their
// charts; any other asks what to do with it. `typed` starts the search
// with words already in it.
internal fun showCountryMenu(app: AppState, charts: Charts, anchor: IntRect, typed: MutableState<String>? = null) {
    app.popups.showUnder(anchor, width = CountryMenuWidth, scrollsItself = true) { close ->
        val own = charts.own ?: return@showUnder
        val rows = chartCountryRows(own, charts.country, ::countryName)
        val pick = { row: CountryRow ->
            val code = row.code
            when {
                code == null || row.pick == CountryPick.Yours || (row.pick == CountryPick.Country && code == own.country) -> charts.showOwn()
                else -> askCountry(app, charts, anchor, code, server = row.pick == CountryPick.Server)
            }
        }
        // The line always shows and always says what the menu is for, so
        // the field under it never moves.
        if (typed != null) CountryMenu(rows, pick, close, typed, title = COUNTRY_MENU_TITLE) else CountryMenu(rows, pick, close, title = COUNTRY_MENU_TITLE)
    }
}

// What to do with a picked country, under the country button, titled with
// its name: only look at it here, make it the listener's chart country, or
// add (or remove) its playlists. It stays open while a save is on its way
// and says so when one did not happen; putting it away does nothing.
internal fun askCountry(app: AppState, charts: Charts, anchor: IntRect, code: String, server: Boolean = false) {
    charts.problemSeen()
    app.popups.showUnder(anchor, width = CountryChoiceWidth) { close ->
        val own = charts.own ?: return@showUnder
        val name = countryName(code)
        MenuTitle(name)
        charts.problem?.let { Txt(it, OctoType.caption, OctoColors.TextMuted, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), maxLines = 3) }
        countryChoices(own, code, name, server).forEach { choice ->
            ChoiceLine(choice.label, choice.detail, enabled = choice.enabled && !charts.saving) {
                if (choice.action == CountryAction.Look) {
                    close()
                    charts.choose(code, choice.action)
                } else {
                    charts.choose(code, choice.action, saved = close)
                }
            }
        }
    }
}

// One answer in the country choice: what it does, and a line about it.
@Composable
private fun ChoiceLine(label: String, detail: String, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .hoverLift(RoundedCornerShape(8.dp), clickable = enabled)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Txt(label, OctoType.bodySmall, OctoColors.TextPrimary, maxLines = 2)
        Txt(detail, OctoType.caption, OctoColors.TextMuted, maxLines = 2)
    }
}

// A chart's songs; `shown` keeps only the first few (Home's row).
private fun LazyListScope.chartSongs(app: AppState, top: TopSongs, shown: Int?) {
    val entries = top.entry.filter { it.song != null }.let { if (shown != null) it.take(shown) else it }
    val songs = entries.map { it.song!! }
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
    // Its title as every shelf's; who ranks it is said in the Charts page's country menu.
    moving(key = "charts-title") {
        ShelfTitle(top.name ?: "Popular right now") {
            charts.showOwn()
            charts.selected = CHART_OVERALL
            app.navigator.go(Page.Charts)
        }
    }
    chartSongs(app, top, shown = CHARTS_ROW)
}
