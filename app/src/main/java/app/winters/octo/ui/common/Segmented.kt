package app.winters.octo.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import app.winters.octo.design.GlazeTabs
import app.winters.octo.design.OctoColors
import app.winters.octo.design.OctoType

// A few choices in one glaze, the chosen one in the darker pill that glides
// between them, like the tabs in the bar. `fit` lets the words step down
// toward SegmentMinText rather than be cut, for segments on a control line.
@Composable
internal fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    fit: Boolean = false,
) {
    GlazeTabs(options.size, selected, onSelect, modifier.fillMaxWidth()) { index, chosen ->
        val tint by animateColorAsState(if (chosen) OctoColors.Accent else OctoColors.TextPrimary, label = "segment tint")
        if (fit) {
            BasicText(
                options[index],
                style = OctoType.label,
                color = { tint },
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = SegmentMinText, maxFontSize = OctoType.label.fontSize, stepSize = 0.5.sp),
            )
        } else {
            Text(options[index], style = OctoType.label, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// The smallest the words of a control line's segments get. Smaller than
// this, the choice is a dropdown instead (see planLine).
val SegmentMinText = 11.sp

// How wide the widest of `words` is, in dp, in `style` (by default a
// segment's words at SegmentMinText).
@Composable
fun rememberWidestWords(words: List<String>, style: TextStyle = OctoType.label.copy(fontSize = SegmentMinText)): Int {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(words, style, density) {
        words.maxOfOrNull { with(density) { measurer.measure(it, style).size.width.toDp().value }.toInt() + 1 } ?: 0
    }
}
