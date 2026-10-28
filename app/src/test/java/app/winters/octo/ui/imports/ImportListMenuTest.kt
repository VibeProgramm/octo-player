package app.winters.octo.ui.imports

import org.junit.Assert.assertEquals
import org.junit.Test

// A list's row on Import is compact: its switches and buttons are in its
// menu, in this order, Read again only where the server reads it again.
class ImportListMenuTest {
    @Test
    fun theMenuHoldsWhatTheRowUsedTo() {
        assertEquals(
            listOf(ListMenu.Open, ListMenu.KeepAsPlaylist, ListMenu.GetMissing, ListMenu.ReadAgain, ListMenu.Remove),
            listMenu(readsAgain = true),
        )
        assertEquals(listOf(ListMenu.Open, ListMenu.KeepAsPlaylist, ListMenu.GetMissing, ListMenu.Remove), listMenu(readsAgain = false))
    }

    @Test
    fun theOpenedListsButtonCountsWhatItGets() {
        assertEquals("Get 1 song", getNowLabel(1))
        assertEquals("Get 12 songs", getNowLabel(12))
    }
}
