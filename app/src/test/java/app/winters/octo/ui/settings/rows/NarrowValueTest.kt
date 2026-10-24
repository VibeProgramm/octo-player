package app.winters.octo.ui.settings.rows

import org.junit.Assert.assertEquals
import org.junit.Test

// How a settings row picks its value width: the first narrower step that
// takes a line off the title's side with the row no taller, the wider
// answers still fitting on two lines.
class NarrowValueTest {
    // Discovery-like: the title side loses its second line only at 120, the
    // narrowest width where the two widest answers still fit on two lines.
    @Test
    fun takesTheFirstStepThatSavesALine() {
        val label = { w: Int -> if (w <= 120) 40 else 60 }
        val value = { w: Int -> if (w < 150) 40 else 20 }
        assertEquals(120, narrowValue(150, listOf(150, 140, 100, 80), listOf(120, 80, 60, 50), label, value))
    }

    @Test
    fun keepsTheWideWidthWhenNoStepSavesALine() {
        assertEquals(150, narrowValue(150, listOf(150, 140, 100), listOf(90, 80, 60), { 60 }) { 20 })
    }

    @Test
    fun neverTakesAStepThatMakesTheRowTaller() {
        val label = { w: Int -> if (w <= 140) 20 else 40 }
        val value = { w: Int -> if (w <= 140) 60 else 20 }
        assertEquals(150, narrowValue(150, listOf(150, 140), listOf(90, 80), label, value))
    }

    @Test
    fun measuresEachDistinctStepOnce() {
        val seen = mutableListOf<Int>()
        val label = { w: Int -> seen += w; 60 }
        // Steps 100, 100, 100: the same width three times.
        narrowValue(150, listOf(150, 100, 100, 100), listOf(100, 90, 80, 70), label) { 20 }
        assertEquals(listOf(150, 100), seen)
    }

    @Test
    fun neverTriesAStepAsWideAsTheWideWidth() {
        val seen = mutableListOf<Int>()
        val label = { w: Int -> seen += w; 60 }
        narrowValue(150, listOf(150, 150), listOf(100, 90), label) { 20 }
        assertEquals(listOf(150), seen)
    }

    @Test
    fun aOnePixelShorterTitleSideCounts() {
        assertEquals(100, narrowValue(150, listOf(150, 100), listOf(90, 80), { w -> if (w <= 100) 59 else 60 }) { 20 })
    }
}
