package app.winters.octo.player

import org.junit.Assert.assertEquals
import org.junit.Test

// The queue's tools moved from a row of words into its More menu: Clear,
// Remove played and Save as playlist, each dimmed when there is nothing to do.
class QueueToolsTest {
    @Test
    fun everyToolIsInMoreInOrder() {
        assertEquals(
            listOf(QueueTool.Clear to true, QueueTool.RemovePlayed to true, QueueTool.Save to true),
            queueTools(upcoming = true, played = true, current = true),
        )
    }

    @Test
    fun toolsWithNothingToDoAreDimmed() {
        assertEquals(
            listOf(QueueTool.Clear to true, QueueTool.RemovePlayed to false, QueueTool.Save to true),
            queueTools(upcoming = true, played = false, current = true),
        )
        assertEquals(
            listOf(QueueTool.Clear to false, QueueTool.RemovePlayed to false, QueueTool.Save to false),
            queueTools(upcoming = false, played = false, current = false),
        )
    }
}
