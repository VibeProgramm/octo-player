package app.winters.octo.playback

import org.junit.Assert.assertEquals
import org.junit.Test

// Where Add to queue puts songs, and that undo then finds the listener's
// own copy rather than one Autoplay added.
class AddToQueueUndoTest {
    @Test
    fun undoTakesOutTheListenersSongNotAutoplays() {
        // a, b, c with a on, then Autoplay's x and y; the listener adds x.
        val at = ownSongsAt(5, upcoming = listOf(1, 2, 3, 4), autoplay = listOf(false, false, false, true, true))
        assertEquals(3, at)
        assertEquals(3, findInserted(listOf("a", "b", "c", "x", "x", "y"), listOf("x"), at))
    }

    @Test
    fun undoTakesOutOnlyWhatWentIn() {
        // Three asked: a song, a find that plays as library song "f-lib", and one the app no
        // longer knows. Two go in, under the ids the player reads them by, so undo looks for those.
        val played = listOf<String?>("a", "f-lib", null).filterNotNull()
        assertEquals(2, played.size)
        val queue = listOf("p", "a", "f-lib", "q")
        val start = findInserted(queue, played, 1)
        assertEquals(1, start)
        assertEquals(listOf("p", "q"), queue.filterIndexed { i, _ -> i < start!! || i >= start + played.size })
        // Looking for the ids as asked would find nothing to take out.
        assertEquals(null, findInserted(queue, listOf("a", "find-1", "gone"), 1))
    }

    @Test
    fun withNoAutoplaySongsTheyGoAtTheEnd() {
        assertEquals(3, ownSongsAt(3, upcoming = listOf(1, 2), autoplay = listOf(false, false, false)))
    }

    @Test
    fun anEmptyQueueTakesThemFirst() {
        assertEquals(0, ownSongsAt(0, upcoming = emptyList(), autoplay = emptyList()))
    }
}
