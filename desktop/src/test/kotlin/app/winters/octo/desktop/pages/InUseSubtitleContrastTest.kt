package app.winters.octo.desktop.pages

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import app.winters.octo.design.OctoColors
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

// The lines under the server in use are read on the chosen fill.
class InUseSubtitleContrastTest {
    private fun luminance(c: Color): Double {
        fun ch(v: Float): Double = if (v <= 0.04045f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
    }

    private fun contrast(ink: Color, fill: Color): Double {
        val (hi, lo) = listOf(luminance(ink.compositeOver(fill)), luminance(fill)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test
    fun theSubtitleReadsOnTheChosenFill() {
        val fill = OctoColors.AccentSelected.compositeOver(OctoColors.BackgroundTertiary)
        val ratio = contrast(InUseSubtitle, fill)
        val name = contrast(OctoColors.TextPrimary, fill)
        println("CONTRAST in-use subtitle and mark ${"%.2f".format(ratio)} name ${"%.2f".format(name)} muted ${"%.2f".format(contrast(OctoColors.TextMuted, fill))} ink ${InUseSubtitle.compositeOver(fill)}")
        assertTrue("the subtitle and the mark are $ratio:1", ratio >= 4.5)
        assertTrue("quieter than the name: $ratio vs $name", ratio < name)
    }
}
