package app.winters.octo.desktop.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import app.winters.octo.design.ScrollbarGutter
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt
import app.winters.octo.design.GlassField
import app.winters.octo.design.MenuRow
import app.winters.octo.design.MenuTitle
import app.winters.octo.design.MenuSeparator
import app.winters.octo.design.OctoColors
import app.winters.octo.design.OctoIcons
import app.winters.octo.design.OctoType
import app.winters.octo.design.Txt
import app.winters.octo.design.scrollFades
import app.winters.octo.design.scrollbar
import app.winters.octo.discovery.CountryRow
import app.winters.octo.discovery.countryMatches

// How wide a country menu is, so it keeps its size as the search narrows it.
val CountryMenuWidth = 392.dp

// A menu of countries: an optional `title` line, a field that narrows them
// by name or code, which stays put while the countries under it scroll,
// then the lines at the top (any the picker puts first, with their line
// about them), a hairline, and the rest, the chosen one marked. Picking a
// line closes the menu before `pick` runs, so it may open another in its
// place. Open it with `scrollsItself`.
@Composable
fun ColumnScope.CountryMenu(
    rows: List<CountryRow>,
    pick: (CountryRow) -> Unit,
    close: () -> Unit,
    typed: MutableState<String> = remember { mutableStateOf("") },
    title: String? = null,
) {
    title?.let { MenuTitle(it) }
    GlassField(
        typed.value,
        { typed.value = it },
        // Its edges line up with the rows' pills, an even gutter all round.
        Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, bottom = 6.dp),
        placeholder = "Search countries",
        icon = OctoIcons.Search,
    )
    val shown = rows.filter { countryMatches(it.label, it.code.orEmpty(), typed.value) }
    val scroll = rememberScrollState()
    // The list opens with the chosen country in view, centered when it can be.
    val view = remember { ChosenView() }
    LaunchedEffect(view, scroll) {
        // Words typed before it settles mean the list is a search's, not the whole.
        snapshotFlow { if (typed.value.isEmpty()) view.target(scroll.maxValue) else NO_SCROLL }
            .first { it != null }
            ?.takeIf { it != NO_SCROLL }
            ?.let { scroll.scrollTo(it) }
    }
    Column(
        Modifier
            .weight(1f, fill = false)
            .onSizeChanged { view.viewport = it.height }
            .scrollbar(scroll)
            .scrollFades(scroll)
            .verticalScroll(scroll)
            .padding(end = if (scroll.maxValue > 0) ScrollbarGutter else 0.dp),
    ) {
        val top = shown.filter { it.top }
        val rest = shown.filterNot { it.top }
        // The last chosen line: a listener's own country is chosen at the top
        // too, and the list is meant to open on the one that took them there.
        val chosen = shown.lastOrNull { it.chosen }
        top.forEach { row -> CountryLine(row, pick, close, view.takeIf { row === chosen }) }
        if (top.isNotEmpty() && rest.isNotEmpty()) MenuSeparator()
        rest.forEach { row -> CountryLine(row, pick, close, view.takeIf { row === chosen }) }
        if (shown.isEmpty()) {
            Txt("No countries match.", OctoType.caption, OctoColors.TextMuted, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
    }
}

private const val NO_SCROLL = -1

// Where the chosen line sits in the list and how tall the list's window is,
// for opening it scrolled to the line. `target` is the scroll position that
// puts the line in the middle of the window (null until both are known, and
// until the list is long enough to scroll); it is asked until it answers.
@Stable
internal class ChosenView {
    var top by mutableIntStateOf(-1)
    var height by mutableIntStateOf(0)
    var viewport by mutableIntStateOf(0)

    fun target(max: Int): Int? {
        if (top < 0 || viewport <= 0 || height <= 0 || max <= 0) return null
        return (top + height / 2 - viewport / 2).coerceIn(0, max)
    }
}

@Composable
private fun CountryLine(row: CountryRow, pick: (CountryRow) -> Unit, close: () -> Unit, place: ChosenView?) {
    Box(
        if (place == null) {
            Modifier
        } else {
            Modifier.onGloballyPositioned {
                place.top = it.positionInParent().y.roundToInt()
                place.height = it.size.height
            }
        },
    ) {
        MenuRow(
            row.label,
            {
                close()
                pick(row)
            },
            detail = row.detail,
            selected = row.chosen,
        )
    }
}

// The lines of a plain country menu: the chosen country first, ticked,
// then every other one in `codes`' order.
fun countryRows(chosen: String, codes: List<String>, name: (String) -> String): List<CountryRow> =
    listOf(CountryRow(chosen, name(chosen), null, chosen = true, top = true)) +
        codes.filter { it != chosen }.map { CountryRow(it, name(it), null, chosen = false, top = false) }
