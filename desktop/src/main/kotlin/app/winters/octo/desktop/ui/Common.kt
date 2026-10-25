package app.winters.octo.desktop.ui

import app.winters.octo.design.TitleAndActions
import app.winters.octo.desktop.AppState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntRect
import app.winters.octo.design.FrameSize
import app.winters.octo.design.LocalPopups
import app.winters.octo.design.MenuRow
import app.winters.octo.design.MenuSeparator
import app.winters.octo.design.MenuTitle
import app.winters.octo.design.PageSize
import app.winters.octo.design.scrollFades
import app.winters.octo.design.scrollbar
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import app.winters.octo.design.ControlHeight
import app.winters.octo.design.WholeTxt
import androidx.compose.ui.graphics.Shape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.focus.onFocusChanged
import app.winters.octo.design.LocalFocusVisibility
import app.winters.octo.design.LocalKeyboardHere
import app.winters.octo.design.FocusRing
import app.winters.octo.design.menuKey
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.winters.octo.design.CutTxt
import app.winters.octo.design.DesktopType
import app.winters.octo.design.GlazeCapsule
import app.winters.octo.design.OctoColors
import app.winters.octo.design.OctoIcons
import app.winters.octo.design.OctoType
import app.winters.octo.design.Space
import app.winters.octo.design.Spinner
import app.winters.octo.design.TextAction
import app.winters.octo.design.Txt
import app.winters.octo.design.hoverLift
import app.winters.octo.desktop.library.Cover
import app.winters.octo.desktop.nav.Navigator
import app.winters.octo.desktop.nav.ScrollSpot
import app.winters.octo.desktop.nav.Visit
import app.winters.octo.desktop.server.userMessage
import app.winters.octo.subsonic.SubsonicException
import dev.chrisbanes.haze.HazeState

// What a page's data has come to.
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ready<T>(val data: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

// A page's data, loaded when the page opens (and again when a key
// changes), with a way to try again after a failure.
class Loaded<T>(val state: Load<T>, val retry: () -> Unit)

@Composable
fun <T> rememberLoad(vararg keys: Any?, load: suspend () -> T): Loaded<T> {
    var round by remember(*keys) { mutableIntStateOf(0) }
    val state by produceState<Load<T>>(Load.Loading, *keys, round) {
        value = Load.Loading
        value = try {
            Load.Ready(load())
        } catch (e: SubsonicException) {
            Load.Failed(e.userMessage())
        }
    }
    return Loaded(state) { round++ }
}

// A list's scroll kept with the visit it belongs to, so going back returns
// to the same spot.
@Composable
fun rememberListState(navigator: Navigator, visit: Visit): LazyListState {
    val state = remember(visit.id) { navigator.scrollOf(visit).let { LazyListState(it.index, it.offset) } }
    DisposableEffect(visit.id) {
        onDispose { navigator.keepScroll(visit, ScrollSpot(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)) }
    }
    return state
}

// A grid's is kept apart from a list's, for a page that has both.
@Composable
fun rememberGridState(navigator: Navigator, visit: Visit): LazyGridState {
    val state = remember(visit.id) { navigator.scrollOf(visit, GRID).let { LazyGridState(it.index, it.offset) } }
    DisposableEffect(visit.id) {
        onDispose { navigator.keepScroll(visit, ScrollSpot(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset), GRID) }
    }
    return state
}

private const val GRID = "grid"

// Room a page leaves at its foot for the now-playing bar floating over it.
val LocalBottomRoom = staticCompositionLocalOf { 0.dp }

// The window's colours the frame's glass frosts, for glass floating over a
// page (the bar of picked songs) to be made of the player's material.
val LocalFrameBackdrop = staticCompositionLocalOf<HazeState?> { null }

// The side margin every page keeps.
val PageSide = 28.dp

fun pagePadding(bottom: Dp, top: Dp = 20.dp) = PaddingValues(start = PageSide, end = PageSide, top = top, bottom = bottom + 16.dp)

// Where the pointer last was in the window, for opening a menu where a
// right click landed.
@Stable
class PointerSpot {
    var position: Offset = Offset.Zero

    val point: IntOffset get() = IntOffset(position.x.toInt(), position.y.toInt())
}

val LocalPointer = staticCompositionLocalOf { PointerSpot() }

// Calls `action` on a right click, before anything under it hears the press.
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.onRightClick(action: () -> Unit): Modifier =
    onPointerEvent(PointerEventType.Press, PointerEventPass.Initial) { event ->
        if (event.buttons.isSecondaryPressed) action()
    }

// A page's title line: the title, whole however long (it wraps between
// words rather than being cut), and a quiet `detail` (how many it holds,
// what the page is) after it on the same baseline, shown only while the
// whole of it fits beside the title on one line. `trailing` is a quiet
// action at the end of the line, set on the title's cap-height middle (or
// under the title, when the two would not fit on one line).
// `bottom` is the room under it: less when a control line follows.
@Composable
fun PageTitle(
    text: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    bottom: Dp = Space.Xl,
    trailing: (@Composable () -> Unit)? = null,
) {
    val density = LocalDensity.current
    // The middle of the title's capitals under its first baseline.
    val capMiddle = with(density) { (DesktopType.pageTitle.fontSize * CapShare / 2).roundToPx() }
    Layout(
        contents = listOf(
            { WholeTxt(text, DesktopType.pageTitle) },
            { if (detail != null) Txt(detail, DesktopType.meta, OctoColors.TextMuted) },
            { trailing?.invoke() },
        ),
        modifier = modifier.fillMaxWidth().padding(bottom = bottom),
    ) { (titles, details, ends), constraints ->
        val width = constraints.maxWidth
        val gap = Space.L.roundToPx()
        val end = ends.map { it.measure(Constraints()) }
        // The title is never squeezed: an action that leaves it too little
        // room for one line goes under it instead.
        val wholeLine = titles.maxOfOrNull { it.maxIntrinsicWidth(Constraints.Infinity) } ?: 0
        val beside = end.isEmpty() || wholeLine + gap + end.maxOf { it.width } <= width
        val endWidth = if (beside) end.maxOfOrNull { it.width }?.let { it + gap } ?: 0 else 0
        val title = titles.map { it.measure(Constraints(maxWidth = (width - endWidth).coerceAtLeast(0))) }
        val titleWidth = title.maxOfOrNull { it.width } ?: 0
        val titleHeight = title.maxOfOrNull { it.height } ?: 0
        val baseline = title.firstOrNull()?.get(FirstBaseline)?.takeIf { it != AlignmentLine.Unspecified } ?: titleHeight
        val oneLine = title.firstOrNull()?.let { it[FirstBaseline] == it[LastBaseline] } ?: true
        val shown = details.map { it.measure(Constraints()) }.takeIf { placed ->
            oneLine && placed.isNotEmpty() && titleWidth + gap + placed.maxOf { it.width } + endWidth <= width
        }.orEmpty()
        // The quiet action is centred on the capitals; a tall one pushes the line down.
        val endTop = if (beside) end.maxOfOrNull { baseline - capMiddle - it.height / 2 } ?: 0 else titleHeight
        val lift = (-endTop).coerceAtLeast(0)
        val height = maxOf(titleHeight + lift, end.maxOfOrNull { endTop + lift + it.height } ?: 0)
        layout(width, height) {
            title.forEach { it.place(0, lift) }
            shown.forEach { line ->
                val own = line[FirstBaseline].takeIf { it != AlignmentLine.Unspecified } ?: line.height
                line.place(titleWidth + gap, lift + baseline - own)
            }
            end.forEach { it.place(width - it.width, endTop + lift) }
        }
    }
}

// How tall a capital stands, as a share of the type's size.
private const val CapShare = 0.72f

// A page's header: its title line, and under it its control line when it
// has anything to put there (see ControlLine), nothing else.
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    control: (@Composable () -> Unit)? = null,
    filters: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val line = control != null || filters != null || actions != null
    Column(modifier.fillMaxWidth()) {
        PageTitle(title, detail = detail, bottom = if (line) TitleToLine else Space.Xl, trailing = trailing)
        if (line) ControlLine(control = control, filters = filters, actions = actions)
    }
}

// The room between a page's title and its control line.
val TitleToLine = Space.M

// A page's control line, under its title and above its content: the
// page's own control first (a country or sort button), then its filters
// (chips that scroll sideways, given the room left), then its actions
// (Play, Shuffle, More) at the end of the line, all on one line, as tall
// on every page.
@Composable
fun ControlLine(
    modifier: Modifier = Modifier,
    control: (@Composable () -> Unit)? = null,
    filters: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(bottom = Space.L).heightIn(min = ControlHeight.L),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.M),
    ) {
        control?.invoke()
        Box(Modifier.weight(1f)) { filters?.invoke() }
        if (actions != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S), content = actions)
    }
}

// A choice of several as one button on a control line, in place of a row
// of chips that would scroll sideways: the chosen one's words and a
// chevron, opening the choices under it (`title` over them), the chosen
// one marked. `detail` is a quiet word beside a choice (a count); a
// hairline parts the choices where `group` changes.
@Composable
fun <T> ChoiceButton(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = null,
    detail: ((T) -> String?)? = null,
    group: ((T) -> Any?)? = null,
    words: String = label(selected),
) {
    var anchor by remember { mutableStateOf(IntRect.Zero) }
    val popups = LocalPopups.current
    GlazeCapsule(
        icon,
        words,
        {
            popups.showUnder(anchor, width = FrameSize.Menu, scrollsItself = true) { close ->
                title?.let { MenuTitle(it) }
                val scroll = rememberScrollState()
                Column(Modifier.weight(1f, fill = false).scrollbar(scroll).scrollFades(scroll).verticalScroll(scroll)) {
                    options.forEachIndexed { index, option ->
                        if (group != null && index > 0 && group(option) != group(options[index - 1])) MenuSeparator()
                        MenuRow(label(option), { close(); onSelect(option) }, detail = detail?.invoke(option), selected = option == selected)
                    }
                }
            }
        },
        modifier.widthIn(max = PageSize.ChoiceButton).onGloballyPositioned { anchor = it.windowRect() },
        height = ControlHeight.L,
        quiet = true,
    )
}

// A section's title, whole, and its action at the end of the line, or
// under the title when the two do not fit side by side. `bottom` is the
// room under it, less when a line about the section follows.
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: String? = null, bottom: Dp = 10.dp, onAction: () -> Unit = {}) {
    val spaced = modifier.fillMaxWidth().padding(top = 22.dp, bottom = bottom)
    if (action == null) {
        WholeTxt(text, OctoType.headline, modifier = spaced)
    } else {
        TitleAndActions(text, OctoType.headline, spaced, Space.M, heading = { WholeTxt(text, OctoType.headline) }) { TextAction(action, onAction) }
    }
}

// A page that is still loading, or failed, or has nothing to show.
@Composable
fun LoadingLine(text: String = "Loading", modifier: Modifier = Modifier) {
    Row(modifier.padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Spinner(size = 16.dp, color = OctoColors.TextSecondary)
        Txt(text, OctoType.bodySmall, OctoColors.TextSecondary)
    }
}

// The same, on its own on a page, in from the page's side as its words are.
@Composable
fun PageLoadingLine(text: String = "Loading") = LoadingLine(text, Modifier.padding(horizontal = PageSide))

@Composable
fun FailedLine(message: String, retry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Txt(message, OctoType.bodySmall, OctoColors.TextSecondary, maxLines = 3)
        GlazeCapsule(null, "Try again", retry)
    }
}

@Composable
fun EmptyLine(title: String, detail: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 32.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Txt(title, OctoType.headline)
        if (detail != null) Txt(detail, OctoType.bodySmall, OctoColors.TextMuted, maxLines = 3)
    }
}

// Shows a load's data, or the loading or failed line in its place.
@Composable
fun <T> Loaded<T>.show(modifier: Modifier = Modifier, loadingText: String = "Loading", content: @Composable (T) -> Unit) {
    when (val s = state) {
        Load.Loading -> LoadingLine(loadingText, modifier)
        is Load.Failed -> FailedLine(s.message, retry, modifier)
        is Load.Ready -> content(s.data)
    }
}

// An album, artist or station as a card: the picture and a line or two
// under it. It lifts under the pointer; a right click opens `onMenu`.
@Composable
fun MediaCard(
    title: String,
    subtitle: String?,
    coverId: String?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    round: Boolean = false,
    online: Boolean = false,
    onMenu: (() -> Unit)? = null,
    badge: @Composable (() -> Unit)? = null,
    // Draws the picture instead of the cover, like a playlist's designed one.
    picture: (@Composable (Modifier, Shape) -> Unit)? = null,
    // A quiet third line, like how much of an album found online the library holds.
    note: String? = null,
) {
    val shape = if (round) CircleShape else RoundedCornerShape(8.dp)
    val pointer = LocalPointer.current
    var here by remember { mutableStateOf(false) }
    val keyboard = LocalFocusVisibility.current.keyboard
    Column(
        modifier
            .onFocusChanged { here = it.isFocused }
            .hoverLift(RoundedCornerShape(12.dp))
            .then(if (onMenu != null) Modifier.onRightClick(onMenu).menuKey { at -> pointer.position = androidx.compose.ui.geometry.Offset(at.left.toFloat(), at.bottom.toFloat()); onMenu() } else Modifier)
            .clickable(interactionSource = null, indication = FocusRing(RoundedCornerShape(12.dp)), role = Role.Button, onClick = onOpen)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box {
            if (picture != null) {
                picture(Modifier.fillMaxWidth().aspectRatio(1f), shape)
            } else {
                Cover(
                    coverId,
                    Modifier.fillMaxWidth().aspectRatio(1f),
                    shape = shape,
                    online = online,
                    placeholder = if (round) OctoIcons.Artist else OctoIcons.Album,
                )
            }
            if (badge != null) Box(Modifier.align(Alignment.BottomEnd).padding(6.dp)) { badge() }
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = if (round) Alignment.CenterHorizontally else Alignment.Start) {
            // With the keyboard on the card, a cut title shows whole.
            CompositionLocalProvider(LocalKeyboardHere provides (here && keyboard)) { CutTxt(title, OctoType.label) }
            if (subtitle != null) CutTxt(subtitle, OctoType.caption, OctoColors.TextMuted)
            if (note != null) CutTxt(note, OctoType.caption, OctoColors.TextMuted)
        }
    }
}

// A card's width in a shelf that scrolls sideways.
val ShelfCardWidth = 176.dp

// A blank that swallows clicks and the wheel, so a click or scroll on empty
// space does nothing, and nothing lying under it hears them.
fun Modifier.swallowClicks(): Modifier = pointerInput(Unit) {
    awaitEachGesture { awaitFirstDown(requireUnconsumed = false) }
}

@Composable
fun Gap(width: Dp) = Box(Modifier.width(width))

// Whether a list (a song table) has the keyboard, so the window leaves the
// arrow keys and Enter to it.
@Stable
class ListFocus {
    var active by mutableStateOf(false)
}

val LocalListFocus = staticCompositionLocalOf { ListFocus() }
