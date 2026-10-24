package app.winters.octo.ui.settings.rows

import org.junit.Assert.assertEquals
import org.junit.Test

// How a settings row picks its value width: the first narrower step that
// takes a line off the title's side with the row no taller.
class NarrowValueTest {
    // Discovery-like: four answers, the title side loses its second line
    // only once the value is as narrow as the third widest answer.
    @Test
    fun takesTheFirstStepThatSavesALine() {
        val ones = listOf(150, 140, 100, 80)
        val twos = listOf(90, 80, 60, 50)
        val label = { w: Int -> if (w <= 100) 40 else 60 }
        val row = { w: Int -> maxOf(label(w), if (w < 150) 40 else 20) }
        assertEquals(100, narrowValue(150, ones, twos, label, row))
    }

    @Test
    fun keepsTheWideWidthWhenNoStepSavesALine() {
        val label = { _: Int -> 60 }
        assertEquals(150, narrowValue(150, listOf(150, 140, 100), listOf(90, 80, 60), label) { 60 })
    }

    @Test
    fun neverTakesAStepThatMakesTheRowTaller() {
        val label = { w: Int -> if (w <= 140) 20 else 40 }
        val row = { w: Int -> if (w <= 140) 60 else 40 }
        assertEquals(150, narrowValue(150, listOf(150, 140), listOf(90, 80), label, row))
    }

    @Test
    fun measuresEachDistinctStepOnce() {
        val seen = mutableListOf<Int>()
        val label = { w: Int -> seen += w; 60 }
        // Steps 100, 100, 100: the same width three times.
        narrowValue(150, listOf(150, 100, 100, 100), listOf(100, 90, 80, 70), label) { 60 }
        assertEquals(listOf(150, 100), seen)
    }
}
