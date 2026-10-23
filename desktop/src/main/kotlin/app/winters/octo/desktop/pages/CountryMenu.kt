package app.winters.octo.desktop.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.winters.octo.design.GlassField
import app.winters.octo.design.MenuRow
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

// A menu of countries: a field that narrows them by name or code, which
// stays put while the countries under it scroll, then the lines at the top
// (the chosen one, ticked, and any others the picker puts first, with
// their line about them), a hairline, and the rest. Open it with
// `scrollsItself`.
@Composable
fun ColumnScope.CountryMenu(
    rows: List<CountryRow>,
    pick: (String?) -> Unit,
    close: () -> Unit,
    typed: MutableState<String> = remember { mutableStateOf("") },
) {
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
    Column(Modifier.weight(1f, fill = false).scrollbar(scroll).scrollFades(scroll).verticalScroll(scroll)) {
        val top = shown.filter { it.top }
        val rest = shown.filterNot { it.top }
        top.forEach { row -> CountryLine(row, pick, close) }
        if (top.isNotEmpty() && rest.isNotEmpty()) MenuSeparator()
        rest.forEach { row -> CountryLine(row, pick, close) }
        if (shown.isEmpty()) {
            Txt("No countries match.", OctoType.caption, OctoColors.TextMuted, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
    }
}

@Composable
private fun CountryLine(row: CountryRow, pick: (String?) -> Unit, close: () -> Unit) {
    MenuRow(
        row.label,
        {
            pick(row.code)
            close()
        },
        detail = row.detail,
        selected = row.chosen,
    )
}

// The lines of a plain country menu: the chosen country first, ticked,
// then every other one in `codes`' order.
fun countryRows(chosen: String, codes: List<String>, name: (String) -> String): List<CountryRow> =
    listOf(CountryRow(chosen, name(chosen), null, chosen = true, top = true)) +
        codes.filter { it != chosen }.map { CountryRow(it, name(it), null, chosen = false, top = false) }
