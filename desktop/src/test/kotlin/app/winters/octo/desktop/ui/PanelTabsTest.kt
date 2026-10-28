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

    private val tabs = listOf("Queue", "Lyrics", "Info")
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
            // Beside two buttons the tabs are one button opening them.
            // (The player has a Lyrics button of its own, so the Info tab tells.)
            assertFalse("the tabs fold away: ${queue.keys}", "Info" in queue)
            // Beside Close alone they fit whole.
            s.onUi { s.app.showSidePanel(SidePanel.Info) }
            s.render(4)
            val info = s.shownNow()
            // (Folded, the button would name the Info tab alone.)
            assertTrue("the tabs show whole: ${info.keys}", "Queue" in info && "Info" in info)
            val close = info.getValue("Close the panel")
            assertTrue("Close has its size: $close", close.width >= 24f && close.right <= s.width)
        }
    }

    @Test
    fun besideCloseAloneTheTabsFitWholeAtThePanelsWidth() {
        val at = laidOut(panelWidth, tabs, listOf("Close"))
        assertTrue(at.keys.toString(), "Lyrics" in at && "Info" in at)
        assertEquals(ControlHeight.M.value, at.getValue("Close").width, 0.5f)
    }

    @Test
    fun besideTwoButtonsTheTabsAreOneButtonAndTheButtonsStayWhole() {
        val at = laidOut(panelWidth, tabs, listOf("Options", "Close"))
        assertFalse("the tabs fold away: ${at.keys}", "Lyrics" in at)
        assertTrue("the picker shows the chosen one: ${at.keys}", "Queue" in at)
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
