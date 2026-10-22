package app.winters.octo.ui.common

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import app.winters.octo.design.GlassInput
import app.winters.octo.design.GlassPopup
import app.winters.octo.design.MenuRowHeight
import app.winters.octo.design.OctoColors
import app.winters.octo.design.OctoIcons
import app.winters.octo.discovery.countryMatches
import app.winters.octo.sort.SortOption
import app.winters.octo.sort.SortOrder

// One answer to a choice: its name, and a line about it.
// `enabled` false shows the answer dimmed, with its detail saying why, and
// it cannot be picked. `leading` draws a small picture of it before its name.
// `key` is more for a search to match, such as a country's code.
data class Choice(
    val label: String,
    val detail: String? = null,
    val enabled: Boolean = true,
    val leading: (@Composable () -> Unit)? = null,
    val key: String? = null,
)

// What the sheet can ask.
sealed interface SheetRequest

// A question with a few answers, and what to do with the one picked. With
// a `searchHint`, a field above the answers narrows them as words are
// typed: `match` says whether an answer is still one (by default, the words
// are inside its name), and `emptyText` is the line when none is.
class ChoiceRequest(
    val title: String,
    val choices: List<Choice>,
    val selected: Int,
    val searchHint: String? = null,
    val match: ((Choice, String) -> Boolean)? = null,
    val emptyText: String = "Nothing matches.",
    val onPick: (Int) -> Unit,
) : SheetRequest

// A country picker's search: its hint, its line when nothing is left, and
// its rule, the words inside a country's name, accents aside, or its whole
// two-letter code (the answer's `key`).
const val COUNTRY_SEARCH_HINT = "Search countries"
const val NO_COUNTRY_MATCHES = "No countries match."

fun countryChoiceMatches(choice: Choice, typed: String): Boolean = countryMatches(choice.label, choice.key.orEmpty(), typed)

// The answers left after what was typed, as their places in the full list,
// in its order.
internal fun shownChoices(request: ChoiceRequest, typed: String): List<Int> {
    if (request.searchHint == null || typed.isBlank()) return request.choices.indices.toList()
    val match = request.match ?: { choice, words -> choice.label.contains(words.trim(), ignoreCase = true) }
    return request.choices.indices.filter { match(request.choices[it], typed) }
}

// How to order a list: the options it offers, the order it is in, and what
// to do with a new one.
class SortRequest(
    val options: List<SortOption>,
    val order: SortOrder,
    val onChange: (SortOrder) -> Unit,
) : SheetRequest

// A question asked over everything, the bar included, so any page can ask
// one. It pops up as a floating glass list beside the control that asked,
// or as a card in the middle of the screen when no control did.
class ChoiceSheet {
    var open by mutableStateOf<SheetRequest?>(null)
        private set

    // The last question shown, kept after closing so it can fade away.
    var last by mutableStateOf<SheetRequest?>(null)
        private set

    // Where the control that asked sits in the window, when known.
    var anchor by mutableStateOf<IntRect?>(null)
        private set

    // The control last pressed that can ask a question, and when.
    private var pressed: IntRect? = null
    private var pressedAt = 0L

    // `anchor` places the list beside a control. Without one, the control
    // just pressed is used, if it was marked with `choiceAnchor`.
    fun show(request: SheetRequest, anchor: IntRect? = null) {
        val recent = pressed.takeIf { SystemClock.uptimeMillis() - pressedAt < PRESS_MS }
        this.anchor = anchor ?: recent
        pressed = null
        open = request
        last = request
    }

    fun close() {
        open = null
    }

    internal fun pressed(bounds: IntRect) {
        pressed = bounds
        pressedAt = SystemClock.uptimeMillis()
    }
}

// How long a press still counts as the control that asked: long enough for
// a slow tap, short enough that a question asked later does not open beside it.
private const val PRESS_MS = 2_000L

val LocalChoiceSheet = staticCompositionLocalOf<ChoiceSheet> { error("No choice sheet") }

// Marks a control that asks a question through the sheet, so the answers
// pop up beside it rather than in the middle of the screen.
fun Modifier.choiceAnchor(sheet: ChoiceSheet): Modifier = this.then(ChoiceAnchorElement(sheet))

private data class ChoiceAnchorElement(val sheet: ChoiceSheet) : ModifierNodeElement<ChoiceAnchorNode>() {
    override fun create() = ChoiceAnchorNode(sheet)
    override fun update(node: ChoiceAnchorNode) {
        node.sheet = sheet
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "choiceAnchor"
    }
}

// Keeps the control's bounds, and hands them to the sheet as a finger lands
// on it, before the control's own click runs.
private class ChoiceAnchorNode(var sheet: ChoiceSheet) :
    Modifier.Node(),
    GlobalPositionAwareModifierNode,
    PointerInputModifierNode {
    private var bounds = Rect.Zero

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        bounds = coordinates.boundsInWindow()
    }

    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        if (pass == PointerEventPass.Initial && pointerEvent.changes.any { it.changedToDown() }) {
            sheet.pressed(this.bounds.roundToIntRect())
        }
    }

    override fun onCancelPointerInput() = Unit
}

@Composable
fun ChoiceSheetHost(sheet: ChoiceSheet) {
    val request = sheet.last ?: return
    GlassPopup(
        visible = sheet.open != null,
        anchor = sheet.anchor,
        onDismiss = sheet::close,
        backdrop = LocalHaze.current,
        title = when (request) {
            is ChoiceRequest -> request.title
            is SortRequest -> "Sort by"
        },
    ) {
        when (request) {
            is ChoiceRequest -> Choices(request, sheet::close)
            is SortRequest -> SortChoices(request, sheet::close)
        }
    }
}

// The answers under the question, the current one in the darker pill.
// Picking one closes the list. A question with a search field keeps one
// width, so the list does not change size as it narrows, and the field
// stays put while the answers under it scroll. What was typed is gone the
// next time the question is asked.
@Composable
private fun Choices(request: ChoiceRequest, close: () -> Unit) {
    var typed by remember(request) { mutableStateOf("") }
    val shown = remember(request, typed) { shownChoices(request, typed) }
    val size = if (request.searchHint != null) Modifier.width(MenuWidth) else Modifier.widthIn(min = 220.dp, max = 320.dp).width(IntrinsicSize.Max)
    Column(size.padding(6.dp)) {
        GlassMenuTitle(request.title)
        request.searchHint?.let { hint -> ChoiceSearch(typed, { typed = it }, hint) }
        if (shown.isEmpty()) GlassMenuNote(request.emptyText)
        // Scrolls when the answers are taller than the list may be.
        GlassMenuOptions(
            shown.map { request.choices[it] },
            shown.indexOf(request.selected),
            onPick = { index ->
                request.onPick(shown[index])
                close()
            },
            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            pickable = request.selected >= 0,
        )
    }
}

// The field that narrows a question's answers: as tall as an answer, its
// words where the answers' words start. Search only puts the keyboard
// away, since the answers narrow as the words are typed.
@Composable
private fun ChoiceSearch(typed: String, onChange: (String) -> Unit, hint: String) {
    val focus = LocalFocusManager.current
    GlassInput(
        value = typed,
        onValueChange = onChange,
        placeholder = hint,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        height = MenuRowHeight.Touch + 4.dp,
        trailing = {
            if (typed.isEmpty()) {
                Icon(painterResource(OctoIcons.Search), contentDescription = null, tint = OctoColors.TextMuted, modifier = Modifier.size(20.dp))
            } else {
                Icon(
                    painterResource(OctoIcons.Close),
                    contentDescription = "Clear search",
                    tint = OctoColors.TextMuted,
                    modifier = Modifier.size(20.dp).clickable(role = Role.Button) { onChange("") },
                )
            }
        },
    )
}
