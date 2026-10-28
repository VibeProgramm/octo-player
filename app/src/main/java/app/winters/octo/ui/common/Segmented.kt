package app.winters.octo.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.winters.octo.design.GlazeTabs
import app.winters.octo.design.OctoColors
import app.winters.octo.design.OctoIcons
import app.winters.octo.design.OctoType

// A few choices in one glaze, the chosen one in an accent pill that glides
// between them, like the tabs in the bar, with a check before its words.
// `fit` lets the words step down toward SegmentMinText rather than be cut,
// for segments on a control line.
@Composable
internal fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    fit: Boolean = false,
    title: String = "Choose",
) {
    if (fit) {
        SegmentRow(options, selected, onSelect, modifier, fit = true)
        return
    }
    // Segments that cannot show every word whole, with the check, become a
    // dropdown rather than cut a word.
    val widest = rememberWidestWords(options, OctoType.label)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (segmentsFitIn(options.size, widest, maxWidth.value.toInt())) {
            SegmentRow(options, selected, onSelect, Modifier, fit = false)
        } else {
            val sheet = LocalChoiceSheet.current
            HeaderDropdown(options[selected], onClick = { showDropdown(sheet, title, options, selected, onSelect) }, clickLabel = "Choose what to show")
        }
    }
}

@Composable
private fun SegmentRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier, fit: Boolean) {
    GlazeTabs(options.size, selected, onSelect, modifier.fillMaxWidth()) { index, chosen ->
        val tint by animateColorAsState(if (chosen) OctoColors.OnAccent else OctoColors.TextPrimary, label = "segment tint")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (chosen) {
                Icon(
                    painterResource(OctoIcons.Check),
                    contentDescription = null,
                    tint = OctoColors.OnAccent,
                    modifier = Modifier.size(SegmentCheck),
                )
                Spacer(Modifier.width(SegmentCheckGap))
            }
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
                Text(options[index], style = OctoType.label, color = tint, maxLines = 1, softWrap = false)
            }
        }
    }
}

// The check shown before the chosen segment's words, and the gap after it.
val SegmentCheck = 14.dp
val SegmentCheckGap = 4.dp

// What the check takes from a segment's room, in dp.
val SegmentCheckRoom: Int = (SegmentCheck + SegmentCheckGap).value.toInt()

// Whether `count` segments fit in `room` dp, `widest` being the room their
// widest words need with the check (see rememberWidestWords): every cell
// keeps 4dp either side, and the glaze 4dp inside each end.
fun segmentsFitIn(count: Int, widest: Int, room: Int): Boolean =
    segmentsWidth(count, widest, SegmentSides, SegmentSides) <= room

// The air round a segment's words (4dp each side), and the glaze's inside
// each end.
const val SegmentSides = 8

// The smallest the words of a control line's segments get. Smaller than
// this, the choice is a dropdown instead (see planLine).
val SegmentMinText = 11.sp

// How much room the widest of `words` needs as a segment, in dp, in `style`
// (by default a segment's words at SegmentMinText): its width and the check
// that sits before it when it is the chosen one.
@Composable
fun rememberWidestWords(words: List<String>, style: TextStyle = OctoType.label.copy(fontSize = SegmentMinText)): Int {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(words, style, density) {
        words.maxOfOrNull { with(density) { measurer.measure(it, style).size.width.toDp().value }.toInt() + 1 + SegmentCheckRoom } ?: 0
    }
}
