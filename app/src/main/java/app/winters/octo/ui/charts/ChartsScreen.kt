package app.winters.octo.ui.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import app.winters.octo.design.OctoColors
import app.winters.octo.design.OctoIcons
import app.winters.octo.design.OctoInk
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
import app.winters.octo.ui.common.HeaderButton
import app.winters.octo.ui.common.LocalChoiceSheet
import app.winters.octo.ui.common.LocalHeaderCompact
import app.winters.octo.ui.common.NO_COUNTRY_MATCHES
import app.winters.octo.ui.common.PageHeader
import app.winters.octo.ui.common.SongLead
import app.winters.octo.ui.common.SongRow
import app.winters.octo.ui.common.choiceAnchor
import app.winters.octo.ui.common.filterFades
import app.winters.octo.ui.common.countryChoiceMatches
import app.winters.octo.ui.common.rowAlbum
import app.winters.octo.ui.common.screenPadding
import app.winters.octo.ui.library.FilterChip
import java.util.Locale

// The charts and new songs an Octo server reads from Apple Music, one at a
// time, picked with the chips: a country's most played songs, Best New
// Songs, Trending Songs and a chart per genre. A song the library has
// plays from it; any other has the add button at the end of its row. The
// header holds it all on two lines: back and the title, then the country
// button, the chips, and Play and Shuffle. On a server that saves it, a
// country picked in the country button's list asks what to do with it.
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
                PageHeader(
                    "Charts",
                    onBack,
                    control = if (country == null) null else {
                        { CountryButton(country, enabled = !vm.saving, onClick = { pickCountry(sheet, vm) }, modifier = Modifier.choiceAnchor(sheet)) }
                    },
                    filters = if (vm.choices.isEmpty()) null else {
                        {
                            val chips = rememberLazyListState()
                            LazyRow(
                                state = chips,
                                modifier = Modifier.filterFades(chips),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                items(vm.choices, key = { it.id }) { choice ->
                                    FilterChip(choice.label, choice.id == vm.selected) { vm.pick(choice.id) }
                                }
                            }
                        }
                    },
                    actions = {
                        HeaderButton(OctoIcons.Play, "Play", { vm.play(0) }, enabled = songs.isNotEmpty())
                        HeaderButton(OctoIcons.Shuffle, "Shuffle", vm::shuffle, enabled = songs.isNotEmpty())
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

// The country in view, as a quiet capsule that opens every country: the
// chips' resting fill, softer words, and a small chevron saying it opens.
// In a header too short for its name it shows its two-letter code ("US"),
// the name kept for a screen reader. It is dimmed and out of reach while a
// save is on its way.
@Composable
private fun CountryButton(code: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val name = countryName(code)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val ink = if (pressed) OctoColors.TextPrimary else OctoColors.TextSecondary
    val compact = LocalHeaderCompact.current
    Row(
        modifier
            .height(32.dp)
            .alpha(if (enabled) 1f else OctoInk.DisabledAlpha)
            .clip(CircleShape)
            .background(OctoColors.TextPrimary.copy(alpha = if (pressed) 0.12f else 0.08f))
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClickLabel = "Choose a country", onClick = onClick)
            .padding(start = 10.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(painterResource(OctoIcons.Globe), contentDescription = null, tint = ink, modifier = Modifier.size(16.dp))
        Text(
            if (compact) code.uppercase() else name,
            style = OctoType.label,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).then(if (compact) Modifier.semantics { contentDescription = name } else Modifier),
        )
        Icon(painterResource(OctoIcons.Chevron), contentDescription = null, tint = OctoColors.TextMuted, modifier = Modifier.size(12.dp).rotate(90f))
    }
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
