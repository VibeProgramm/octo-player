package app.winters.octo.ui.common

import androidx.compose.ui.graphics.Color
import app.winters.octo.design.OctoColors
import app.winters.octo.theme.FixedAccents
import kotlin.math.pow
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test

// The chosen state of every choice control reads against what is behind
// it, in every accent the app offers.
class ChoiceContrastTest {
    private fun channel(value: Float): Double {
        val v = value.toDouble()
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(c: Color): Double =
        0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    // WCAG contrast ratio of two opaque colors.
    private fun contrast(a: Color, b: Color): Double {
        val high = maxOf(luminance(a), luminance(b))
        val low = minOf(luminance(a), luminance(b))
        return (high + 0.05) / (low + 0.05)
    }

    @After
    fun backToDefault() {
        OctoColors.useAccent(Color(0xFF97B1B9))
    }

    private fun eachAccent(check: (name: String) -> Unit) {
        assertTrue(FixedAccents.isNotEmpty())
        for (choice in FixedAccents) {
            OctoColors.useAccent(Color(choice.argb!!))
            check(choice.label)
        }
    }

    @Test
    fun theChosenSegmentStandsOutFromTheTray() = eachAccent { name ->
        val ratio = contrast(OctoColors.Accent, OctoColors.BackgroundTertiary)
        assertTrue("$name pill on the tray is $ratio", ratio >= 3.0)
    }

    @Test
    fun theChosenSegmentsWordsReadOnTheAccent() = eachAccent { name ->
        val ratio = contrast(OctoColors.OnAccent, OctoColors.Accent)
        assertTrue("$name words on the pill are $ratio", ratio >= 4.5)
    }

    @Test
    fun theChosenMenuRowsWordsReadOnItsTint() = eachAccent { name ->
        val ratio = contrast(OctoColors.TextPrimary, OctoColors.AccentSelected)
        assertTrue("$name words on the row are $ratio", ratio >= 4.5)
    }
}
