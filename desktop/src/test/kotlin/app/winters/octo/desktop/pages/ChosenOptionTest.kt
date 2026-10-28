package app.winters.octo.desktop.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.winters.octo.design.OctoColors
import app.winters.octo.design.ProvideWindowLook
import org.jetbrains.skia.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.swing.SwingUtilities

// The chosen option of a choice drawn on the desktop: the accent fill and a
// check for the chosen one, nothing for the others.
class ChosenOptionTest {
    private val width = 200
    private val height = 36

    // Draws `content` in a 200 by 36 window and returns the picture.
    private fun picture(content: @Composable () -> Unit): Bitmap {
        var bitmap: Bitmap? = null
        SwingUtilities.invokeAndWait {
            val scene = ImageComposeScene(width, height, Density(1f)) {
                ProvideWindowLook(reduceMotion = true) {
                    Box(Modifier.fillMaxSize().background(OctoColors.Background)) {
                        Box(Modifier.width(width.dp).height(height.dp)) { content() }
                    }
                }
            }
            try {
                repeat(4) { scene.render(System.nanoTime() + it * 50_000_000L).close() }
                bitmap = Bitmap.makeFromImage(scene.render(System.nanoTime() + 400_000_000L))
            } finally {
                scene.close()
            }
        }
        return bitmap!!
    }

    private fun near(expected: Int, actual: Int, within: Int = 6): Boolean {
        fun channel(color: Int, shift: Int) = (color shr shift) and 0xFF
        return listOf(16, 8, 0).all { kotlin.math.abs(channel(expected, it) - channel(actual, it)) <= within }
    }

    // Whether any pixel in the right end of the row is light, as a check is.
    private fun checkShown(bitmap: Bitmap): Boolean =
        (150 until 195).any { x -> (0 until height).any { y -> (bitmap.getColor(x, y) and 0xFF) > 200 && ((bitmap.getColor(x, y) shr 8) and 0xFF) > 200 } }

    private fun section(chosen: Boolean) = picture {
        SectionPick(PageSection("look", "Look") {}, chosen, {}, Modifier.width(width.dp))
    }

    @Test
    fun theChosenSectionIsTheAccentFillWithACheck() {
        val bitmap = section(chosen = true)
        assertTrue(near(OctoColors.AccentSelected.toArgb(), bitmap.getColor(100, height / 2)))
        assertTrue(checkShown(bitmap))
    }

    @Test
    fun anotherSectionHasNeitherFillNorCheck() {
        val bitmap = section(chosen = false)
        assertFalse(near(OctoColors.AccentSelected.toArgb(), bitmap.getColor(100, height / 2)))
        assertFalse(checkShown(bitmap))
    }

    @Test
    fun theServerInUseIsTheAccentFillAndOthersAreNot() {
        val used = picture { ChosenFill(true, Modifier.fillMaxSize()) }
        val other = picture { ChosenFill(false, Modifier.fillMaxSize()) }
        assertTrue(near(OctoColors.AccentSelected.toArgb(), used.getColor(100, height / 2)))
        assertFalse(near(OctoColors.AccentSelected.toArgb(), other.getColor(100, height / 2)))
    }

    @Test
    fun theChosenWordsAreMediumWeight() {
        assertEquals(androidx.compose.ui.text.font.FontWeight.Medium, ChosenWords.fontWeight)
    }
}
