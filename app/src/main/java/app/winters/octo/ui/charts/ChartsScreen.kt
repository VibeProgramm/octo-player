package app.winters.octo.ui.charts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import app.winters.octo.design.OctoColors
import app.winters.octo.design.OctoIcons
import app.winters.octo.design.OctoType
import app.winters.octo.discovery.ChartList
import app.winters.octo.discovery.CountryPick
import app.winters.octo.discovery.chartCountryRows
import app.winters.octo.discovery.countryChoices
import app.winters.octo.discovery.playsText
import app.winters.octo.discovery.rankedBy
import app.winters.octo.ui.common.COUNTRY_SEARCH_HINT
import app.winters.octo.ui.common.Choice
import app.winters.octo.ui.common.ChoiceRequest
import app.winters.octo.ui.common.ChoiceSheet
import app.winters.octo.ui.common.LocalChoiceSheet
import app.winters.octo.ui.common.LocalHeaderCompact
import app.winters.octo.ui.common.NO_COUNTRY_MATCHES
import app.winters.octo.ui.common.DROPDOWN_CHROME_DP
import app.winters.octo.ui.common.DROPDOWN_ICON_DP
import app.winters.octo.ui.common.HeaderDropdown
import app.winters.octo.ui.common.PageHeader
import app.winters.octo.ui.common.PlayShuffleButtons
import app.winters.octo.ui.common.showDropdown
import app.winters.octo.ui.common.SongLead
import app.winters.octo.ui.common.SongRow
import app.winters.octo.ui.common.countryChoiceMatches
import app.winters.octo.ui.common.rowAlbum
import app.winters.octo.ui.common.screenPadding
import java.util.Locale

// The charts and new songs an Octo server reads from Apple Music, one at a
// time, picked from the chart button's list: a country's most played
// songs, Best New Songs, Trending Songs and a chart per genre. A song the
// library has plays from it; any other has the add button at the end of
// its row. The header holds it all on two lines: back and the title, then
// the country button, the chart button, and Play and Shuffle. On a server
// that saves it, a country picked in the country button's list asks what
// to do with it.
@Composable
fun ChartsScreen(onBack: () -> Unit, vm: ChartsViewModel = hiltViewModel()) {
    val sheet = LocalChoiceSheet.current
    // A save that did not happen asks its question again, the reason first.
    LaunchedEffect(vm.problem, vm.problemFor) {
        val asked = vm.problemFor ?: return@LaunchedEffect
        val problem = vm.problem ?: return@LaunchedEffect
        askCountry(sheet, vm, asked, sheet.anchor, problem)
        vm.problemSeen()
    }
    val list = vm.list
    val songs = list?.songs.orEmpty()
    val country = vm.country?.takeIf { vm.canPickCountry }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = screenPadding()) {
            item(key = "header") {
                val chart = vm.choices.firstOrNull { it.id == vm.selected } ?: vm.choices.firstOrNull()
                val form = rememberCountryForm(country, chart?.label)
                PageHeader(
                    "Charts",
                    onBack,
                    control = if (country == null && chart == null) null else {
                        {
                            Row(horizontalArrangement = Arrangement.spacedBy(ControlGap), verticalAlignment = Alignment.CenterVertically) {
                                if (country != null) CountryButton(country, form, enabled = !vm.saving, onClick = { pickCountry(sheet, vm) })
                                if (chart != null) {
                                    HeaderDropdown(chart.label, onClick = { pickChart(sheet, vm) }, clickLabel = "Choose a chart")
                                }
                            }
                        }
                    },
                    actions = {
                        PlayShuffleButtons({ vm.play(0) }, vm::shuffle, enabled = songs.isNotEmpty())
                    },
                )
            }
            when {
                vm.none -> item(key = "none") { Quiet("This server doesn't have charts.") }
                vm.refusal != null -> item(key = "refused") { Quiet(vm.refusal.orEmpty()) }
                list == null -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = OctoColors.TextSecondary)
                    }
                }
                songs.isEmpty() -> item(key = "empty") { Quiet("Nothing in this chart right now. Try another.") }
                else -> itemsIndexed(songs, key = { _, ranked -> "chart:${list.country.orEmpty()}:${list.chart}:${ranked.track.id}" }) { index, ranked ->
                    SongRow(
                        ranked.track,
                        SongLead.Ranked(ranked.rank),
                        subtitle = { shown ->
                            listOfNotNull(
                                shown.artist.ifEmpty { null },
                                rowAlbum(shown).ifEmpty { null },
                                ranked.plays?.let(::playsText),
                            ).joinToString(" • ")
                        },
                        offerAdd = true,
                    ) { vm.play(index) }
                }
            }
        }
    }
}

// The room between the country and chart buttons.
private val ControlGap = 8.dp

// How the country button fits beside the chart button on this screen: its
// name, its code, or the globe alone, measured with the header's own words.
@Composable
private fun rememberCountryForm(country: String?, chart: String?): CountryForm {
    if (country == null) return CountryForm.Name
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val screen = LocalConfiguration.current.screenWidthDp
    return remember(country, chart, screen, density) {
        fun words(text: String) = with(density) { measurer.measure(text, OctoType.label).size.width.toDp().value.toInt() }
        val chrome = DROPDOWN_CHROME_DP + DROPDOWN_ICON_DP
        countryForm(
            room = controlRoom(screen),
            chart = chart?.let { words(it) + DROPDOWN_CHROME_DP } ?: 0,
            name = words(countryName(country)) + chrome,
            code = words(country.uppercase()) + chrome,
            gap = ControlGap.value.toInt(),
        )
    }
}

// The country in view, as a quiet capsule that opens every country: the
// globe, the country, and a small chevron saying it opens. In `form` it is
// the country's name, its two-letter code ("US"), or the globe alone, the
// name always kept for a screen reader. It is dimmed and out of reach while
// a save is on its way.
@Composable
private fun CountryButton(code: String, form: CountryForm, enabled: Boolean, onClick: () -> Unit) {
    val name = countryName(code)
    // The header's own short form wins when it is shorter still.
    val shown = if (LocalHeaderCompact.current && form == CountryForm.Name) CountryForm.Code else form
    HeaderDropdown(
        if (shown == CountryForm.Code) code.uppercase() else name,
        onClick = onClick,
        icon = OctoIcons.Globe,
        showLabel = shown != CountryForm.Globe,
        description = name,
        enabled = enabled,
        clickLabel = "Choose a country",
    )
}

// Every chart the country has, Top songs, Best New Songs and Trending Songs
// first, then its genres, the one in view checked. Picking one shows it.
private fun pickChart(sheet: ChoiceSheet, vm: ChartsViewModel) {
    val menu = chartMenu(vm.choices)
    showDropdown(sheet, vm.list?.let(::caption) ?: "Chart", menu.map { it.label }, menu.indexOfFirst { it.id == vm.selected }) { vm.pick(menu[it].id) }
}

// Every country, under the chart's source line: the listener's own first,
// the server's while they chose one of their own, then every country by
// name, with a field that narrows them by name or code. Their own goes back
// to their charts; any other asks what to do with it.
private fun pickCountry(sheet: ChoiceSheet, vm: ChartsViewModel) {
    val own = vm.own ?: return
    val rows = chartCountryRows(own, vm.browsing, ::countryName)
    val anchor = sheet.anchor
    sheet.show(
        ChoiceRequest(
            vm.list?.let(::caption) ?: "Country",
            rows.map { Choice(it.label, it.detail, key = it.code) },
            selected = rows.indexOfFirst { it.chosen },
            searchHint = COUNTRY_SEARCH_HINT,
            match = ::countryChoiceMatches,
            emptyText = NO_COUNTRY_MATCHES,
        ) { index ->
            val row = rows[index]
            val code = row.code
            when {
                code == null || row.pick == CountryPick.Yours || (row.pick == CountryPick.Country && code == own.country) -> vm.backToYours()
                else -> askCountry(sheet, vm, CountryAsked(code, server = row.pick == CountryPick.Server), anchor ?: sheet.anchor)
            }
        },
    )
}

// What to do with a picked country, titled with its name: only look at it
// here, make it the listener's chart country, or add (or remove) its
// playlists. `problem`, when a save did not happen, is the first line.
// Putting the question away does nothing.
private fun askCountry(sheet: ChoiceSheet, vm: ChartsViewModel, asked: CountryAsked, anchor: IntRect?, problem: String? = null) {
    val own = vm.own ?: return
    val name = countryName(asked.code)
    val choices = countryChoices(own, asked.code, name, server = asked.server)
    val lead = listOfNotNull(problem?.let { Choice(it, enabled = false) })
    sheet.show(
        ChoiceRequest(
            name,
            lead + choices.map { Choice(it.label, it.detail, enabled = it.enabled) },
            selected = -1,
        ) { index ->
            choices.getOrNull(index - lead.size)?.let { vm.choose(asked, it.action) }
        },
        anchor,
    )
}

@Composable
private fun Quiet(text: String) {
    Text(
        text,
        style = OctoType.caption,
        color = OctoColors.TextMuted,
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
    )
}

// A country's name in the phone's language, from its two-letter code; the
// code itself when the phone has no name for it.
internal fun countryName(code: String): String =
    Locale("", code.uppercase()).displayCountry.takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) } ?: code.uppercase()

// Who ranked the chart and for which country: "Most played on Apple Music
// in United States".
internal fun caption(list: ChartList): String? {
    val by = rankedBy(list.source, list.chart) ?: return null
    val country = list.country?.takeIf { it.length == 2 }?.let { Locale("", it.uppercase()).displayCountry }
    return if (country.isNullOrBlank()) by else "$by in $country"
}
