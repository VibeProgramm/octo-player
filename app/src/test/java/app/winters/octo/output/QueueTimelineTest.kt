package app.winters.octo.output

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(UnstableApi::class)
class QueueTimelineTest {
    private fun song(id: String) = MediaItem.Builder().setMediaId(id).build()

    // Media3 keeps a timeline after the queue has moved on, so one stays
    // exactly as it was made, whatever happens to the lists it came from.
    @Test
    fun aTimelineStaysAsItWasMadeWhenItsListsChangeLater() {
        val items = mutableListOf(song("a"), song("b"), song("c"))
        val uids = mutableListOf<Any>("a", "b", "c")
        val lengths = mutableListOf<Long?>(200_000, null, 0)
        val order = mutableListOf(2, 0, 1)
        val timeline = QueueTimeline(items, uids, lengths, order)

        items += song("d")
        uids.removeAt(2)
        lengths += 1L
        order.reverse()

        val window = Timeline.Window()
        assertEquals(3, timeline.windowCount)
        assertEquals(3, timeline.periodCount)
        assertEquals(listOf("a", "b", "c"), List(3) { timeline.getWindow(it, window).mediaItem.mediaId })
        assertEquals(200_000_000L, timeline.getWindow(0, window).durationUs)
        assertEquals("c", timeline.getUidOfPeriod(2))
        assertEquals(2, timeline.getFirstWindowIndex(true))
        val longer = QueueTimeline(
            listOf(song("a"), song("b"), song("c"), song("d")),
            listOf("a", "b", "c", "d"),
            listOf(null, null, null, null),
            listOf(0, 1, 2, 3),
        )
        assertFalse(timeline == longer)
    }
}
