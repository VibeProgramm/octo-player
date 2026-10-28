package app.winters.octo.ui.settings

import app.winters.octo.ui.family.appQualityNote
import org.junit.Assert.assertEquals
import org.junit.Test

// The note over this app's own quality names the answer that hands the
// choice to this app only when that answer is on the page.
class AppQualityNoteTest {
    @Test
    fun namesTheAnswerWhenThisPhoneFollowsIsShown() {
        assertEquals("Your account's choice applies now. Choose \"Let this app decide\" above to use these.", appQualityNote(deviceShown = true))
    }

    @Test
    fun saysOnlyWhatAppliesWhenItIsNot() {
        assertEquals("Your account's choice applies now.", appQualityNote(deviceShown = false))
    }
}
