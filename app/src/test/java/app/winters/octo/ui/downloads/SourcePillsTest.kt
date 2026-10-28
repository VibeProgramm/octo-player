package app.winters.octo.ui.downloads

import app.winters.octo.subsonic.FIND_OFF
import app.winters.octo.subsonic.FIND_SEARCHING
import org.junit.Assert.assertEquals
import org.junit.Test

// Find songs shows its sources as one line of pills. Why a source could
// not search stays on screen, as the quiet word on its pill.
class SourcePillsTest {
    @Test
    fun aFailedSourceSaysWhyOnItsPill() {
        assertEquals("Soulseek" to "Not signed in", sourcePill("Soulseek", "failed", "Not signed in"))
        assertEquals("Soulseek" to "could not search", sourcePill("Soulseek", "failed", null))
    }

    @Test
    fun theOtherStatesSayWhereTheyStand() {
        assertEquals("YouTube" to "searching", sourcePill("YouTube", FIND_SEARCHING, null))
        assertEquals("Qobuz" to "off", sourcePill("Qobuz", FIND_OFF, null))
        assertEquals("Deezer" to "4 found", sourcePill("Deezer", "done", "4 found"))
        assertEquals("Deezer" to null, sourcePill("Deezer", "done", null))
    }
}
