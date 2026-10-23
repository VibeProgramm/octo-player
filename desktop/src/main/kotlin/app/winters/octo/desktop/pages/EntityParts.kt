package app.winters.octo.desktop.pages

import app.winters.octo.design.TitleAndActions
import app.winters.octo.design.WholeTxt
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.Layout
import app.winters.octo.design.MenuRow
import app.winters.octo.design.MenuTitle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import app.winters.octo.design.ControlHeight
import app.winters.octo.design.Corner
import app.winters.octo.design.CutTxt
import app.winters.octo.design.DesktopType
import app.winters.octo.design.GlazeCapsule
import app.winters.octo.design.GlazeRound
import app.winters.octo.design.IconAction
import app.winters.octo.design.IconSize
import app.winters.octo.design.OctoColors
import app.winters.octo.design.OctoIcons
import app.winters.octo.design.PageSize
import app.winters.octo.design.Space
import app.winters.octo.design.TextAction
import app.winters.octo.design.Txt
import app.winters.octo.design.hoverLift
import app.winters.octo.desktop.AppState
import app.winters.octo.desktop.library.Cover
import app.winters.octo.desktop.library.LibraryIndex
import app.winters.octo.desktop.library.LibraryState
import app.winters.octo.desktop.library.gridColumns
import app.winters.octo.desktop.ui.PageSide
import app.winters.octo.desktop.ui.windowRect

// The parts the album, artist, genre and folder pages are made of: a
// compact header, links down a long page, section titles, rows of cards
// that fill the width, and what to do next when there is nothing to show.

// The library once it has been read, or null while it is still being read.
@Composable
fun rememberIndex(app: AppState): LibraryIndex? {
    val store = app.library ?: return null
    val state by store.state.collectAsState()
    return (state as? LibraryState.Ready)?.index
}

// The top of an album, artist, genre or live list page: its picture, and
// beside it its name, one quiet line under the name (an artist to open,
// then its facts, or what a live list picks), and its control line:
// `control` first (links down the page, a note on what the library has),
// the actions at the end (Play and Shuffle, the heart, More). `aside`
// sits beside the name (an artist's biography, on a wide page).
// Compact, since the page is for the list under it.
@Composable
fun EntityHeader(
    title: String,
    picture: @Composable (Modifier) -> Unit,
    subtitle: @Composable (() -> Unit)? = null,
    facts: List<Fact> = emptyList(),
    note: @Composable (() -> Unit)? = null,
    control: @Composable (() -> Unit)? = null,
    aside: @Composable (() -> Unit)? = null,
    titleContent: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val art = headerArt(maxWidth)
        Row(Modifier.fillMaxWidth().padding(bottom = Space.Xl), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.Page)) {
            picture(Modifier.size(art))
            Column(Modifier.weight(1f)) {
                // The aside follows the name closely rather than across the page.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.Section)) {
                    Column(Modifier.weight(1f, fill = aside == null), verticalArrangement = Arrangement.spacedBy(Space.Xs)) {
                        if (titleContent != null) titleContent() else WholeTxt(title, DesktopType.pageTitle)
                        MetaLine(subtitle, facts, note)
                    }
                    if (aside != null) Box(Modifier.weight(1f, fill = false).widthIn(max = PageSize.Reading)) { aside() }
                }
                HeaderLine(control, actions)
            }
        }
    }
}

// The quiet line under a header's name: the subtitle, then the facts, then
// the note, one after another with a dot between.
@Composable
private fun MetaLine(subtitle: @Composable (() -> Unit)?, facts: List<Fact>, note: @Composable (() -> Unit)?) {
    if (subtitle == null && facts.isEmpty() && note == null) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        subtitle?.let {
            Box(Modifier.weight(1f, fill = false)) { it() }
            if (facts.isNotEmpty() || note != null) Txt(" · ", DesktopType.meta, OctoColors.TextMuted)
        }
        if (facts.isNotEmpty()) FactLine(facts)
        note?.let {
            if (facts.isNotEmpty()) Txt(" · ", DesktopType.meta, OctoColors.TextMuted)
            Box(Modifier.weight(1f, fill = false)) { it() }
        }
    }
}

// A header's control line, under the quiet line: `control` first, the
// actions at its end.
@Composable
fun HeaderLine(control: @Composable (() -> Unit)?, actions: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = Space.L).heightIn(min = ControlHeight.L),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.M),
    ) {
        Box(Modifier.weight(1f)) { control?.invoke() }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S), content = actions)
    }
}

// The heading picture's size on a page `width` wide.
fun headerArt(width: Dp): Dp = if (width < PageSize.HeaderNarrow) PageSize.HeaderArtSmall else PageSize.HeaderArt

// Play and Shuffle, a page's first two actions, round at the end of its
// control line.
@Composable
fun PlayAndShuffle(onPlay: () -> Unit, onShuffle: () -> Unit, enabled: Boolean = true) {
    GlazeRound(OctoIcons.Play, "Play", onPlay, enabled = enabled, lit = true)
    GlazeRound(OctoIcons.Shuffle, "Shuffle", onShuffle, enabled = enabled)
}

// An action as an icon beside them: the heart, Add to queue, a radio.
@Composable
fun HeaderIcon(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) =
    IconAction(icon, description, onClick, modifier, size = ControlHeight.L, iconSize = IconSize.Transport, enabled = enabled, tint = OctoColors.TextSecondary)

// More, opening the page's menu under itself.
@Composable
fun MoreButton(app: AppState, description: String, menu: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    var anchor by remember { mutableStateOf(IntRect.Zero) }
    HeaderIcon(OctoIcons.More, description, { app.popups.showUnder(anchor) { close -> menu(close) } }, Modifier.onGloballyPositioned { anchor = it.windowRect() })
}

// One fact in a header's line, which may open a page (a genre, say).
data class Fact(val text: String, val open: (() -> Unit)? = null)

@Composable
fun FactLine(facts: List<Fact>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        facts.forEachIndexed { index, fact ->
            if (index > 0) Txt(" · ", DesktopType.meta, OctoColors.TextMuted)
            val open = fact.open
            if (open == null) {
                Txt(fact.text, DesktopType.meta, OctoColors.TextMuted)
            } else {
                Txt(fact.text, DesktopType.meta, OctoColors.TextSecondary, Modifier.pointerHoverIcon(PointerIcon.Hand).clickable(role = Role.Button, onClick = open))
            }
        }
    }
}

// Links down a long page to its sections: "Artists · Albums · Songs". As
// many as fit the line show; when some do not, a last "All sections" opens
// them all, so the line never runs on past its room.
@Composable
fun JumpLinks(app: AppState, links: List<Pair<String, () -> Unit>>) {
    if (links.size < 2) return
    var anchor by remember { mutableStateOf(IntRect.Zero) }
    val more = @Composable {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt("·", DesktopType.meta, OctoColors.TextMuted)
            JumpLink("All sections", Modifier.onGloballyPositioned { anchor = it.windowRect() }) {
                app.popups.showUnder(anchor) { close ->
                    MenuTitle("Go to")
                    links.forEach { (label, go) -> MenuRow(label, { close(); go() }) }
                }
            }
        }
    }
    Layout(
        contents = listOf(
            {
                links.forEachIndexed { index, (label, go) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (index > 0) Txt("·", DesktopType.meta, OctoColors.TextMuted)
                        JumpLink(label, onClick = go)
                    }
                }
            },
            more,
        ),
        modifier = Modifier.offset(x = -Space.S),
    ) { (shown, extra), constraints ->
        val loose = constraints.copy(minWidth = 0)
        val each = shown.map { it.measure(loose) }
        val tail = extra.map { it.measure(loose) }
        val width = constraints.maxWidth
        val all = each.sumOf { it.width }
        // Every link when they all fit; else as many as fit beside More.
        val fits = if (all <= width) each.size else {
            val room = width - (tail.maxOfOrNull { it.width } ?: 0)
            var used = 0
            each.indexOfFirst { used += it.width; used > room }.let { if (it < 0) each.size else it }
        }
        val height = (each + tail).maxOfOrNull { it.height } ?: 0
        layout(width.coerceAtMost(maxOf(all, constraints.minWidth)), height) {
            var x = 0
            each.take(fits).forEach { it.place(x, (height - it.height) / 2); x += it.width }
            if (fits < each.size) tail.forEach { it.place(x, (height - it.height) / 2) }
        }
    }
}

// One link on the line.
@Composable
private fun JumpLink(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.hoverLift(Corner.RowShape).clickable(role = Role.Button, onClick = onClick).padding(horizontal = Space.S, vertical = Space.Xs)) {
        Txt(label, DesktopType.meta, OctoColors.TextSecondary)
    }
}

// A section's title, how many it holds, and an action at the far end.
// The title is whole however long ("More by" a long credit, say), and the
// action moves under it when the two do not fit on one line.
@Composable
fun GroupTitle(text: String, count: Int? = null, action: String? = null, onAction: () -> Unit = {}) {
    val heading = @Composable {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WholeTxt(text, DesktopType.section, modifier = Modifier.weight(1f, fill = false))
            if (count != null) Txt("$count", DesktopType.meta, OctoColors.TextMuted, Modifier.padding(start = Space.M))
        }
    }
    val spaced = Modifier.fillMaxWidth().padding(top = Space.Page, bottom = Space.S)
    if (action == null) {
        Box(spaced) { heading() }
    } else {
        // The count is measured with the title, so the two keep their line.
        TitleAndActions(if (count != null) "$text   $count" else text, DesktopType.section, spaced, Space.M, heading) { TextAction(action, onAction) }
    }
}

// What a page cannot show, what happened, and what to do next. The first
// step is lit.
@Composable
fun NextStep(title: String, detail: String? = null, vararg steps: Pair<String, () -> Unit>) {
    Column(Modifier.padding(vertical = Space.Xxl), verticalArrangement = Arrangement.spacedBy(Space.S)) {
        Txt(title, DesktopType.section)
        if (detail != null) Txt(detail, DesktopType.body, OctoColors.TextMuted, maxLines = 4)
        if (steps.isNotEmpty()) {
            Row(Modifier.padding(top = Space.S), horizontalArrangement = Arrangement.spacedBy(Space.M)) {
                steps.forEachIndexed { index, (label, go) -> GlazeCapsule(null, label, go, lit = index == 0, height = ControlHeight.M) }
            }
        }
    }
}

// A quiet line above a list, with one action: "Showing Airbag · Play it".
@Composable
fun QuietLine(text: String, action: String? = null, onAction: () -> Unit = {}, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = PageSide, vertical = Space.Xs), verticalAlignment = Alignment.CenterVertically) {
        Txt(text, DesktopType.meta, OctoColors.TextSecondary)
        if (action != null) TextAction(action, onAction)
    }
}

// A genre's picture: four covers in a square when it has four different
// ones, else one.
@Composable
fun Mosaic(covers: List<String>, modifier: Modifier = Modifier) {
    if (covers.size >= 4) {
        Column(modifier.clip(Corner.ArtMShape)) {
            for (row in 0..1) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    for (column in 0..1) Cover(covers[row * 2 + column], Modifier.weight(1f).fillMaxHeight(), shape = RectangleShape)
                }
            }
        }
    } else {
        Cover(covers.firstOrNull(), modifier, shape = Corner.ArtMShape, placeholder = OctoIcons.Genres)
    }
}

// How many cards fit across the page at `width`, each at least `card` wide.
fun cardColumns(width: Dp, card: Dp = PageSize.Card): Int = gridColumns((width - PageSide * 2).value, card.value)

// A page's items, counted as they are added, so a link can scroll straight
// to a section: `spots` gets each key's place in the list.
class PageItems(private val scope: LazyListScope, private val spots: MutableMap<String, Int>) {
    private var next = 0

    fun item(key: String, content: @Composable () -> Unit) {
        spots[key] = next++
        scope.item(key = key) { content() }
    }

    // Cards in rows that fill the width, `columns` to a row, each row an
    // item of its own so a long shelf stays light.
    fun <T> cards(key: String, items: List<T>, columns: Int, card: @Composable (T) -> Unit) {
        items.chunked(columns.coerceAtLeast(1)).forEachIndexed { index, row ->
            item("$key:$index") {
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { Box(Modifier.weight(1f)) { card(it) } }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
