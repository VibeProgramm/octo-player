package app.winters.octo.playback

import kotlin.test.Test
import kotlin.test.assertEquals

class QueueAddWordsTest {
    @Test
    fun oneSong() {
        assertEquals("Playing next", queueAddWords(1, next = true))
        assertEquals("Added to queue", queueAddWords(1, next = false))
    }

    @Test
    fun severalSongs() {
        assertEquals("12 songs playing next", queueAddWords(12, next = true))
        assertEquals("12 songs added to queue", queueAddWords(12, next = false))
    }
}
