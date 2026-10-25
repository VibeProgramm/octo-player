package app.winters.octo.design

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// The top edge fades out, so words scrolled up melt away under a title
// rather than being cut off. At rest nothing sits in it.
fun Modifier.topFade(height: Dp): Modifier = edgeFades(height, top = { 1f }, bottom = { 0f })

// Soft fades at the top and bottom edges of a list that scrolls, each only
// while there is more of the list that way, and coming in over the first
// stretch scrolled so it never pops.
fun Modifier.scrollFades(state: ScrollState, height: Dp = 12.dp): Modifier = edgeFades(
    height,
    top = { edge -> (state.value / edge).coerceIn(0f, 1f) },
    bottom = { edge -> ((state.maxValue - state.value) / edge).coerceIn(0f, 1f) },
)

// Fades the top and bottom `height` of what is drawn, `top` and `bottom` of
// the way (0 to 1), read while drawing from the fade's height in pixels.
private fun Modifier.edgeFades(height: Dp, top: (Float) -> Float, bottom: (Float) -> Float): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val edge = height.toPx()
        val above = top(edge)
        if (above > 0f) {
            drawRect(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 1f - above), Color.Black), startY = 0f, endY = edge),
                size = Size(size.width, edge),
                blendMode = BlendMode.DstIn,
            )
        }
        val below = bottom(edge)
        if (below > 0f) {
            drawRect(
                Brush.verticalGradient(listOf(Color.Black, Color.Black.copy(alpha = 1f - below)), startY = size.height - edge, endY = size.height),
                topLeft = Offset(0f, size.height - edge),
                size = Size(size.width, edge),
                blendMode = BlendMode.DstIn,
            )
        }
    }

// Soft fades at the two ends of a row that scrolls sideways, each only
// while there is more of the row that way, so a chip running under the
// next control melts away instead of being cut.
fun Modifier.rowFades(state: LazyListState, width: Dp = 16.dp): Modifier =
    sideFades(width, { state.canScrollBackward }, { state.canScrollForward })

// The same, for a row scrolled by a ScrollState.
fun Modifier.rowFades(state: ScrollState, width: Dp = 16.dp): Modifier =
    sideFades(width, { state.value > 0 }, { state.value < state.maxValue })

private fun Modifier.sideFades(width: Dp, before: () -> Boolean, after: () -> Boolean): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val edge = width.toPx()
        if (before()) {
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = edge), size = Size(edge, size.height), blendMode = BlendMode.DstIn)
        }
        if (after()) {
            drawRect(
                Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - edge, endX = size.width),
                topLeft = Offset(size.width - edge, 0f),
                size = Size(edge, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
    }
