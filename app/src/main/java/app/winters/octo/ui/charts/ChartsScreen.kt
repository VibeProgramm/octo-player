package app.winters.octo.ui.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import app.winters.octo.design.GlazeButton
import app.winters.octo.design.OctoColors
import app.winters.octo.design.OctoIcons
import app.winters.octo.design.OctoInk
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
import app.winters.octo.ui.common.QuietButton
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
    // The line about the chart keeps the last chart's words for the same
    // country while the next chart comes, so nothing under it moves.
    var shownCaption by remember(vm.country) { mutableStateOf<String?>(null) }
    LaunchedEffect(vm.list) { vm.list?.let(::caption)?.let { shownCaption = it } }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = screenPadding(extraTop = DetailTopGap)) {
            item(key = "title") {
                val country = vm.country?.takeIf { vm.canPickCountry }
                ChartsTitle(country?.let(::countryName), sheet, onPick = { pickCountry(sheet, vm) }) { CountryActions(vm) }
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
            val list = vm.list
            if (!vm.none) item(key = "about") { Quiet(list?.let(::caption) ?: shownCaption.orEmpty()) }
            when {
                vm.none -> item(key = "none") { Quiet("This server doesn't have charts.") }
                list == null -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = OctoColors.TextSecondary)
                    }
                }
                list.songs.isEmpty() -> item(key = "empty") { Quiet("Nothing in this chart right now. Try another.") }
                else -> {
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
// in view beside it: a quiet capsule whose middle sits on the middle of the
// title's capitals, then the quiet words that act on a country looked at.
// The words take their room whether or not they show, so nothing under
// the title moves between the listener's own country and another: beside
// the capsule when all of it fits on the title's line, else on a line of
// their own under it. A capsule that does not fit beside the title goes
// under it too, and the title always keeps its whole width.
@Composable
private fun ChartsTitle(country: String?, sheet: ChoiceSheet, onPick: () -> Unit, actions: @Composable () -> Unit) {
    val capHeight = rememberCapHeightPx(OctoType.display)
    Layout(
        content = {
            Text("Charts", style = OctoType.display, color = OctoColors.TextPrimary)
            if (country != null) {
                CountryCapsule(country, onPick, Modifier.choiceAnchor(sheet))
                Row(verticalAlignment = Alignment.CenterVertically) { actions() }
            }
        },
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val title = measurables[0].measure(loose)
        val width = constraints.maxWidth
        if (measurables.size < 3) return@Layout layout(width, title.height) { title.place(0, 0) }
        val capsule = measurables[1].measure(loose)
        val words = measurables[2].measure(loose)
        val gap = 12.dp.roundToPx()
        val middle = capMiddle(title[FirstBaseline].toFloat(), capHeight)
        val besideTitle = title.width + gap + capsule.width <= width
        val allBeside = besideTitle && title.width + gap + capsule.width + words.width <= width
        // The first line: the title, and what fits beside it on its capitals.
        val onFirst = if (besideTitle) capsule.height else 0
        val firstTop = (middle - onFirst / 2f).roundToInt()
        val lift = max(0, -firstTop)
        var bottom = max(title.height + lift, firstTop + lift + onFirst)
        val capsuleAt: IntOffset
        val wordsAt: IntOffset
        if (besideTitle) {
            capsuleAt = IntOffset(title.width + gap, firstTop + lift)
            wordsAt = if (allBeside) {
                IntOffset(capsuleAt.x + capsule.width, capsuleAt.y + (capsule.height - words.height) / 2)
            } else {
                IntOffset(0, bottom + gap / 2).also { bottom = it.y + words.height }
            }
        } else {
            capsuleAt = IntOffset(0, bottom + gap / 2)
            bottom = capsuleAt.y + capsule.height
            wordsAt = IntOffset(0, bottom + gap / 2)
            bottom = wordsAt.y + words.height
        }
        layout(width, bottom) {
            title.place(0, lift)
            capsule.place(capsuleAt)
            words.place(wordsAt)
        }
    }
}

// The country in view, as a quiet capsule that opens every country: the
// chips' resting fill, softer words, and a small chevron saying it opens.
@Composable
private fun CountryCapsule(name: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val ink = if (pressed) OctoColors.TextPrimary else OctoColors.TextSecondary
    Row(
        modifier
            .height(30.dp)
            .clip(CircleShape)
            .background(OctoColors.TextPrimary.copy(alpha = if (pressed) 0.12f else 0.08f))
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = "Choose a country", onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(painterResource(OctoIcons.Globe), contentDescription = null, tint = ink, modifier = Modifier.size(16.dp))
        Text(name, style = OctoType.label, color = ink, maxLines = 1)
        Icon(painterResource(OctoIcons.Chevron), contentDescription = null, tint = OctoColors.TextMuted, modifier = Modifier.size(12.dp).rotate(90f))
    }
}

// The quiet words for the country in view: make it the listener's own, or
// pin it so its charts come as playlists too; a full set of pins says so,
// and a save that did not happen says that. While the listener's own
// country shows they keep their room, unseen and out of reach.
@Composable
private fun CountryActions(vm: ChartsViewModel) {
    val shown = vm.browsing != null
    Row(
        (if (shown) Modifier else Modifier.alpha(0f).clearAndSetSemantics {}).padding(start = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Action("Use as my country", shown && !vm.saving, vm::useAsMine)
        Action(if (vm.isPinned) "Unpin" else "Pin", shown && !vm.saving && vm.canPin, vm::togglePin)
    }
    val note = vm.problem ?: "${vm.maxPinned} pinned".takeIf { shown && !vm.canPin }
    if (note != null) Text(note, style = OctoType.caption, color = OctoColors.TextMuted, maxLines = 1, modifier = Modifier.padding(start = 4.dp))
}

// A quiet word button, dimmed and doing nothing while not `enabled`.
@Composable
private fun Action(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.alpha(if (enabled) 1f else OctoInk.DisabledAlpha)) { QuietButton(text) { if (enabled) onClick() } }
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
