package app.winters.octo.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.winters.octo.design.ControlHeight
import app.winters.octo.design.FrameSize
import app.winters.octo.design.GlazeSegments
import app.winters.octo.design.ProvideWindowLook
import app.winters.octo.design.Space
import app.winters.octo.desktop.SidePanel
import app.winters.octo.desktop.a11y.A11yScene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.swing.SwingUtilities

// The side panel's header line: its own buttons keep their size and stay
// on screen, and its tabs become one button when they would not fit whole.
class PanelTabsTest {
    @get:Rule val folder = TemporaryFolder()

    // Every text and description that is shown, with where it landed. The
    // form measured but not shown stays composed, unplaced, so it is left
    // out with all under it.
    private fun shown(owners: Collection<SemanticsOwner>): Map<String, Rect> {
        val found = mutableMapOf<String, Rect>()
        fun walk(node: SemanticsNode) {
            if (!node.layoutInfo.isPlaced) return
            node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }?.let { found[it] = node.boundsInRoot }
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()?.let { found[it] = node.boundsInRoot }
            node.children.forEach(::walk)
        }
        owners.forEach { walk(it.unmergedRootSemanticsNode) }
        return found
    }

    // The tabs on a header line `width` wide, laid out as the panel lays
    // out its own, beside `buttons` buttons of the panel's size.
    private fun laidOut(width: Int, labels: List<String>, buttons: List<String>): Map<String, Rect> {
        lateinit var found: Map<String, Rect>
        SwingUtilities.invokeAndWait {
            val scene = ImageComposeScene(width + 40, 120, Density(1f)) {
                ProvideWindowLook(reduceMotion = true) {
                    Row(Modifier.width(width.dp).padding(start = Space.L, end = Space.S), horizontalArrangement = Arrangement.spacedBy(Space.Xs)) {
                        PanelTabs(labels, labels.first(), { it }, {}, Modifier.weight(1f))
                        buttons.forEach { Box(Modifier.size(ControlHeight.M).semantics { contentDescription = it }) }
                    }
                }
            }
            try {
                repeat(3) { scene.render() }
                found = shown(scene.semanticsOwners)
            } finally {
                scene.close()
            }
        }
        return found
    }

    // Every text a screen reader can reach, placed or not.
    private fun allTexts(owners: Collection<SemanticsOwner>): List<String> {
        val found = mutableListOf<String>()
        fun walk(node: SemanticsNode) {
            node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }?.let { found += it }
            node.children.forEach(::walk)
        }
        owners.forEach { walk(it.rootSemanticsNode) }
        return found
    }

    // The tabs alone in `width`: every text in the tree, and the strip's size.
    private fun alone(width: Int, labels: List<String>, selected: String): Pair<List<String>, Rect> {
        lateinit var texts: List<String>
        lateinit var strip: Rect
        SwingUtilities.invokeAndWait {
            val scene = ImageComposeScene(width + 40, 120, Density(1f)) {
                ProvideWindowLook(reduceMotion = true) {
                    Box(Modifier.width(width.dp)) {
                        Box(Modifier.semantics { contentDescription = "strip" }) { PanelTabs(labels, selected, { it }, {}) }
                    }
                }
            }
            try {
                repeat(3) { scene.render() }
                texts = allTexts(scene.semanticsOwners)
                strip = shown(scene.semanticsOwners).getValue("strip")
            } finally {
                scene.close()
            }
        }
        return texts to strip
    }

    private val tabs = listOf("Queue", "Lyrics", "Info")

    @Test
    fun theStripKeepsItsWidthWhicheverTabIsChosen() {
        val widths = tabs.map { alone(panelWidth, tabs, it).second.width }
        println("STRIP widths $widths")
        assertTrue("one width for every choice: $widths", widths.all { Math.abs(it - widths.first()) < 0.5f })
        assertTrue("the strip is about 228 px: $widths", widths.first() in 215f..240f)
    }

    // The widths of the segments of a row of choices, by `labelWidth`.
    private fun segmentWidths(selected: String, labelWidth: Boolean): List<Float> {
        lateinit var widths: List<Float>
        SwingUtilities.invokeAndWait {
            val scene = ImageComposeScene(600, 120, Density(1f)) {
                ProvideWindowLook(reduceMotion = true) {
                    GlazeSegments(tabs, selected, { it }, {}, labelWidth = labelWidth, tabPadding = 10.dp)
                }
            }
            try {
                repeat(3) { scene.render() }
                val found = mutableListOf<Float>()
                fun walk(node: SemanticsNode) {
                    if (node.config.getOrNull(SemanticsProperties.Role) == Role.Tab) found += node.boundsInRoot.width
                    node.children.forEach(::walk)
                }
                scene.semanticsOwners.forEach { walk(it.unmergedRootSemanticsNode) }
                widths = found
            } finally {
                scene.close()
            }
        }
        return widths
    }

    @Test
    fun segmentsShareOneWidthUnlessTheyHugTheirWords() {
        for (choice in tabs) {
            val equal = segmentWidths(choice, labelWidth = false)
            assertEquals(3, equal.size)
            assertTrue("equal cells: $equal", equal.all { Math.abs(it - equal.first()) < 1.5f })
            val own = segmentWidths(choice, labelWidth = true)
            assertTrue("one width per label: $own", own[0] > own[1] - 0.5f && own[1] > own[2] + 5f)
            assertEquals("widths do not change with the choice", segmentWidths("Queue", labelWidth = true), own)
        }
    }

    @Test
    fun theHiddenFormIsNotInTheSemanticsTree() {
        val whole = alone(panelWidth, tabs, "Lyrics").first
        for (label in tabs) assertEquals("$label once in $whole", 1, whole.count { it == label })
        val folded = alone(150, tabs, "Lyrics").first
        assertEquals("only the picker's word: $folded", listOf("Lyrics"), folded)
    }
    private val panelWidth = FrameSize.Panel.value.toInt()

    private fun A11yScene.shownNow(): Map<String, Rect> {
        lateinit var found: Map<String, Rect>
        onUi { found = shown(scene.semanticsOwners) }
        return found
    }

    @Test(timeout = 180_000)
    fun atItsWidthThePanelKeepsCloseAndItsOptionsOnScreen() {
        A11yScene(folder.root).use { s ->
            s.onUi {
                s.app.play(s.app.library!!.index!!.songs, 0)
                s.app.toggleSidePanel(SidePanel.Queue)
            }
            s.render(4)
            val panelLeft = s.width - FrameSize.Panel.value - 1
            val queue = s.shownNow()
            for (wanted in listOf("Close the panel", "Queue options")) {
                val box = queue[wanted]
                assertTrue("$wanted is shown: ${queue.keys}", box != null)
                assertTrue("$wanted has its size: $box", box!!.width >= 24f && box.height >= 24f)
                assertTrue("$wanted is on screen in the panel: $box", box.left >= panelLeft && box.right <= s.width)
            }
            // The same tabs show in every view, whole, at the same place.
            // (The player has a Lyrics button of its own, so the Info tab tells.)
            assertTrue("the tabs show whole in the queue: ${queue.keys}", "Info" in queue)
            s.onUi { s.app.showSidePanel(SidePanel.Lyrics) }
            s.render(4)
            val lyrics = s.shownNow()
            assertTrue("the tabs show whole in the lyrics: ${lyrics.keys}", "Info" in lyrics)
            s.onUi { s.app.showSidePanel(SidePanel.Info) }
            s.render(4)
            val info = s.shownNow()
            assertTrue("the tabs show whole in the info: ${info.keys}", "Queue" in info && "Info" in info)
            val close = info.getValue("Close the panel")
            assertTrue("Close has its size: $close", close.width >= 24f && close.right <= s.width)
            assertEquals("Info stays put between the queue and the lyrics", queue.getValue("Info").left, lyrics.getValue("Info").left, 0.5f)
            assertEquals("Queue stays put between the lyrics and the info", lyrics.getValue("Queue").left, info.getValue("Queue").left, 0.5f)
            val lyricsClose = lyrics.getValue("Close the panel")
            assertTrue("Close has its size in the lyrics: $lyricsClose", lyricsClose.width >= 24f && lyricsClose.right <= s.width)
        }
    }

    @Test
    fun besideCloseAloneTheTabsFitWholeAtThePanelsWidth() {
        val at = laidOut(panelWidth, tabs, listOf("Close"))
        assertTrue(at.keys.toString(), "Lyrics" in at && "Info" in at)
        assertEquals(ControlHeight.M.value, at.getValue("Close").width, 0.5f)
    }

    @Test
    fun besideTwoButtonsTheTabsFitWholeAndTheButtonsStayWhole() {
        val at = laidOut(panelWidth, tabs, listOf("Options", "Close"))
        assertTrue("the tabs show whole: ${at.keys}", "Lyrics" in at && "Info" in at)
        assertTrue("clear of the buttons by more than 8 px", at.getValue("Options").left - at.getValue("Info").right > 8f)
        assertEquals(ControlHeight.M.value, at.getValue("Options").width, 0.5f)
        assertEquals(ControlHeight.M.value, at.getValue("Close").width, 0.5f)
        assertTrue(at.getValue("Close").right <= panelWidth)
    }

    @Test
    fun aNarrowerLineShowsOneButtonAndKeepsCloseWhole() {
        val at = laidOut(200, tabs, listOf("Close"))
        assertFalse("the tabs fold away: ${at.keys}", "Lyrics" in at)
        assertEquals(ControlHeight.M.value, at.getValue("Close").width, 0.5f)
        assertTrue(at.getValue("Close").right <= 200f)
    }

    @Test
    fun aWiderLabelShowsOneButtonAtThePanelsWidth() {
        val at = laidOut(panelWidth, listOf("Queue", "Lyrics as they are sung", "Info"), listOf("Close"))
        assertFalse("the tabs fold away: ${at.keys}", "Info" in at)
        assertEquals(ControlHeight.M.value, at.getValue("Close").width, 0.5f)
    }
}
