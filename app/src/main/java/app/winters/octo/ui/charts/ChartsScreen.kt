package app.winters.octo.ui.charts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import app.winters.octo.design.ButtonSize
import app.winters.octo.design.GlazeButton
import app.winters.octo.design.OctoColors
import app.winters.octo.design.OctoIcons
import app.winters.octo.design.OctoType
import app.winters.octo.discovery.ChartList
import app.winters.octo.discovery.chartCountryRows
import app.winters.octo.discovery.playsText
import app.winters.octo.discovery.rankedBy
import app.winters.octo.player.capMiddle
import app.winters.octo.player.rememberCapHeightPx
import app.winters.octo.ui.common.BackButton
import app.winters.octo.ui.common.COUNTRY_SEARCH_HINT
import app.winters.octo.ui.common.Choice
import app.winters.octo.ui.common.ChoiceRequest
import app.winters.octo.ui.common.ChoiceSheet
import app.winters.octo.ui.common.DetailTopGap
import app.winters.octo.ui.common.LocalChoiceSheet
import app.winters.octo.ui.common.NO_COUNTRY_MATCHES
import app.winters.octo.ui.common.SongLead
import app.winters.octo.ui.common.SongRow
import app.winters.octo.ui.common.choiceAnchor
import app.winters.octo.ui.common.countryChoiceMatches
import app.winters.octo.ui.common.rowAlbum
import app.winters.octo.ui.common.screenPadding
import app.winters.octo.ui.library.FilterChip
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

// The charts and new songs an Octo server reads from Apple Music, one at a
// time, picked with the chips: a country's most played songs, Best New
// Songs, Trending Songs and a chart per genre. A song the library has
// plays from it; any other has the add button at the end of its row. On a
// server that saves it, the country beside the title opens every country:
// one looked at can be made the listener's own or pinned.
@Composable
fun ChartsScreen(onBack: () -> Unit, vm: ChartsViewModel = hiltViewModel()) {
    val sheet = LocalChoiceSheet.current
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = screenPadding(extraTop = DetailTopGap)) {
            item(key = "title") {
                val country = vm.country?.takeIf { vm.canPickCountry }
                ChartsTitle(country?.let(::countryName), sheet) { pickCountry(sheet, vm) }
            }
            if (vm.choices.isNotEmpty()) {
                item(key = "chips") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                    ) {
                        items(vm.choices, key = { it.id }) { choice ->
                            FilterChip(choice.label, choice.id == vm.selected) { vm.pick(choice.id) }
                        }
                    }
                }
            }
            if (vm.browsing != null) item(key = "country") { CountryActions(vm) }
            vm.problem?.let { problem -> item(key = "problem") { Quiet(problem) } }
            val list = vm.list
            when {
                vm.none -> item(key = "none") { Quiet("This server doesn't have charts.") }
                list == null -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = OctoColors.TextSecondary)
                    }
                }
                list.songs.isEmpty() -> item(key = "empty") { Quiet("Nothing in this chart right now. Try another.") }
                else -> {
                    caption(list)?.let { item(key = "about") { Quiet(it) } }
                    item(key = "play") {
                        GlazeButton("Play all", onClick = { vm.play(0) }, modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))
                    }
                    itemsIndexed(list.songs, key = { _, ranked -> "chart:${list.country.orEmpty()}:${list.chart}:${ranked.track.id}" }) { index, ranked ->
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
        BackButton(onBack)
    }
}

// The page's title and, when other countries can be looked at, the country
// in view beside it: a quieter capsule whose middle sits on the middle of
// the title's capitals. When the two do not fit side by side, the capsule
// goes under the title, which always keeps its whole width.
@Composable
private fun ChartsTitle(country: String?, sheet: ChoiceSheet, onPick: () -> Unit) {
    val capHeight = rememberCapHeightPx(OctoType.display)
    Layout(
        content = {
            Text("Charts", style = OctoType.display, color = OctoColors.TextPrimary)
            if (country != null) {
                GlazeButton(
                    country,
                    onClick = onPick,
                    size = ButtonSize.Small,
                    icon = painterResource(OctoIcons.Globe),
                    modifier = Modifier.choiceAnchor(sheet),
                )
            }
        },
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val title = measurables[0].measure(loose)
        val button = measurables.getOrNull(1)?.measure(loose)
        val width = constraints.maxWidth
        val gap = 12.dp.roundToPx()
        when {
            button == null -> layout(width, title.height) { title.place(0, 0) }
            title.width + gap + button.width <= width -> {
                val middle = capMiddle(title[FirstBaseline].toFloat(), capHeight)
                val top = (middle - button.height / 2f).roundToInt()
                // The capsule may reach above the title's line; the row grows to hold it.
                val lift = max(0, -top)
                layout(width, max(title.height + lift, top + lift + button.height)) {
                    title.place(0, lift)
                    button.place(title.width + gap, top + lift)
                }
            }
            else -> layout(width, title.height + gap / 2 + button.height) {
                title.place(0, 0)
                button.place(0, title.height + gap / 2)
            }
        }
    }
}

// While another country is looked at: make it the listener's own, or pin
// it so its charts come as playlists too. A full set of pins says so.
@Composable
private fun CountryActions(vm: ChartsViewModel) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlazeButton("Use as my country", onClick = vm::useAsMine, size = ButtonSize.Small, enabled = !vm.saving)
        GlazeButton(
            if (vm.isPinned) "Unpin" else "Pin",
            onClick = vm::togglePin,
            size = ButtonSize.Small,
            enabled = !vm.saving && vm.canPin,
            icon = painterResource(if (vm.isPinned) OctoIcons.Pinned else OctoIcons.Pin),
        )
        if (!vm.canPin) Text("${vm.maxPinned} pinned", style = OctoType.caption, color = OctoColors.TextMuted)
    }
}

// Every country, the server's own and the listener's own and pinned ones
// first, with a field that narrows them by name or code.
private fun pickCountry(sheet: ChoiceSheet, vm: ChartsViewModel) {
    val own = vm.own ?: return
    val rows = chartCountryRows(own, vm.browsing, ::countryName)
    sheet.show(
        ChoiceRequest(
            "Country",
            rows.map { Choice(it.label, it.detail, key = it.code) },
            selected = rows.indexOfFirst { it.chosen },
            onPick = { index ->
                val code = rows[index].code
                if (code == null) vm.followServer() else vm.pickCountry(code)
            },
            searchHint = COUNTRY_SEARCH_HINT,
            match = ::countryChoiceMatches,
            emptyText = NO_COUNTRY_MATCHES,
        ),
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
