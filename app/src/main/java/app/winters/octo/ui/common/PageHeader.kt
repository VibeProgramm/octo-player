package app.winters.octo.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.winters.octo.design.GlazedIconButton
import app.winters.octo.design.OctoColors
import app.winters.octo.design.OctoIcons
import app.winters.octo.design.OctoInk
import app.winters.octo.design.OctoType
import app.winters.octo.player.capMiddle
import app.winters.octo.player.rememberCapHeightPx
import kotlin.math.max
import kotlin.math.roundToInt

// The title of a page with a header: the big page title.
val HeaderTitle = OctoType.display

// How big the header's round buttons are: the back button and a page's
// actions, like Play and Shuffle.
val HeaderButtonSize = 40.dp

// Whether the page's control is drawn as its small form (an icon, or a
// short code, its words kept for a screen reader) because its words do not
// fit on the control line. A control that has a small form reads it.
val LocalHeaderCompact = staticCompositionLocalOf { false }

// The least room the filters keep on the control line beside a control
// with its words; with less, the control takes its small form.
private val FiltersMin = 96.dp

// The room between the parts of the control line.
private val Gap = 10.dp

// Half the height of the title's line, which holds a 48dp touch target
// centered on the title's capitals.
private val LineHalf = 24.dp

// Whether the page's control takes its small form on the control line,
// all in pixels: the line's width, the control's width with its words, the
// actions at the end, and whether the line also holds filters, which keep
// at least `filtersMin` beside the control.
internal fun controlCompact(width: Int, control: Int, actions: Int, hasFilters: Boolean, gap: Int, filtersMin: Int): Boolean {
    if (control <= 0) return false
    val actionsPart = if (actions > 0) actions + gap else 0
    val needed = control + actionsPart + (if (hasFilters) gap + filtersMin else 0)
    return needed > width
}

// Where the title's line puts the title, in pixels: after the back button
// (`back` wide, 0 without one) and its gap, at the title's own one-line
// width, whatever the line's width. The title is never shortened, broken
// or wrapped, and nothing else shares its line.
internal fun titlePlace(back: Int, title: Int, gap: Int): Pair<Int, Int> = (if (back > 0) back + gap else 0) to title

// The top of a page that is not a tab, on two lines at most. The first
// holds the back button and the big page title alone, the button on the
// middle of the title's capitals. The second, the control line, holds
// everything else: the page's own control first (a country or sort
// button), then its filters (chips that scroll sideways), then its
// actions (Play, Shuffle, More) at the end. A page with none of those has
// the first line only.
@Composable
fun PageHeader(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    control: (@Composable () -> Unit)? = null,
    filters: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val hasLine = control != null || filters != null || actions != null
    Column(modifier.fillMaxWidth()) {
        TitleLine(title, onBack, Modifier.padding(start = if (onBack != null) 12.dp else 20.dp, end = 16.dp, top = 8.dp, bottom = if (hasLine) 4.dp else 12.dp))
        if (hasLine) ControlLine(control, filters, actions, Modifier.padding(start = 12.dp, end = 16.dp, bottom = 12.dp))
    }
}

@Composable
private fun TitleLine(title: String, onBack: (() -> Unit)?, modifier: Modifier) {
    val capHeight = rememberCapHeightPx(HeaderTitle)
    Layout(
        content = {
            if (onBack != null) HeaderButton(OctoIcons.Back, "Back", onBack)
            Text(title, style = HeaderTitle, color = OctoColors.TextPrimary, maxLines = 1, softWrap = false)
        },
        modifier = modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val back = if (onBack != null) measurables[0].measure(Constraints()) else null
        val name = measurables.last().measure(Constraints())
        val (x, _) = titlePlace(back?.width ?: 0, name.width, Gap.roundToPx())
        // The line is as tall on every page: the title where a 48dp target
        // centered on its capitals fits.
        val half = LineHalf.roundToPx()
        val capitals = capMiddle(name[FirstBaseline].toFloat(), capHeight)
        val lift = max(0, (half - capitals).roundToInt())
        val middle = capitals + lift
        val height = max(name.height + lift, (middle + half).roundToInt())
        layout(width, height) {
            back?.place(0, (middle - back.height / 2f).roundToInt())
            name.place(x, lift)
        }
    }
}

private enum class Slot { Actions, Control, CompactControl, Filters }

@Composable
private fun ControlLine(
    control: (@Composable () -> Unit)?,
    filters: (@Composable () -> Unit)?,
    actions: (@Composable RowScope.() -> Unit)?,
    modifier: Modifier,
) {
    SubcomposeLayout(modifier.fillMaxWidth()) { constraints ->
        val width = constraints.maxWidth
        val gap = Gap.roundToPx()
        val acts = actions?.let { content ->
            subcompose(Slot.Actions) { Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { content() } }
                .first().measure(Constraints(maxWidth = width))
        }
        val actionsPart = acts?.let { it.width + gap } ?: 0
        val room = (width - actionsPart).coerceAtLeast(0)
        val full = control?.let { subcompose(Slot.Control, it).firstOrNull() }
        val compact = full != null &&
            controlCompact(width, full.maxIntrinsicWidth(Constraints.Infinity), acts?.width ?: 0, filters != null, gap, FiltersMin.roundToPx())
        val chosen = if (compact) {
            subcompose(Slot.CompactControl) { CompositionLocalProvider(LocalHeaderCompact provides true) { control?.invoke() } }.firstOrNull()
        } else {
            full
        }
        val ctrl = chosen?.measure(Constraints(maxWidth = room))
        val controlPart = ctrl?.let { it.width + gap } ?: 0
        val chips = filters?.let { content ->
            subcompose(Slot.Filters, content).firstOrNull()?.measure(Constraints.fixedWidth((room - controlPart).coerceAtLeast(0)))
        }
        val height = listOfNotNull(acts, ctrl, chips).maxOfOrNull { it.height } ?: 0
        layout(width, height) {
            ctrl?.place(0, (height - ctrl.height) / 2)
            chips?.place(controlPart, (height - chips.height) / 2)
            acts?.place(width - acts.width, (height - acts.height) / 2)
        }
    }
}

// One of the header's round glass buttons: the back button, or a page's
// action like Play or Shuffle. One not `enabled` is dimmed and does nothing.
@Composable
fun HeaderButton(@DrawableRes icon: Int, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    GlazedIconButton(
        backdrop = LocalHaze.current,
        icon = ImageVector.vectorResource(icon),
        contentDescription = description,
        onClick = { if (enabled) onClick() },
        modifier = modifier.alpha(if (enabled) 1f else OctoInk.DisabledAlpha),
        size = HeaderButtonSize,
    )
}

// Soft 16dp fades at the ends of a control line's filters that scroll
// sideways, each only while there is more that way, so a chip running
// under the actions melts away instead of being cut.
fun Modifier.filterFades(state: LazyListState): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val edge = 16.dp.toPx()
        if (state.canScrollBackward) {
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = edge), size = Size(edge, size.height), blendMode = BlendMode.DstIn)
        }
        if (state.canScrollForward) {
            drawRect(
                Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - edge, endX = size.width),
                topLeft = Offset(size.width - edge, 0f),
                size = Size(edge, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
    }
