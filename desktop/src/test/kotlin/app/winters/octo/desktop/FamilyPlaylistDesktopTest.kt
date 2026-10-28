package app.winters.octo.desktop

import app.winters.octo.desktop.pages.playlistDetails
import app.winters.octo.desktop.pages.songsLine
import app.winters.octo.subsonic.Playlist
import app.winters.octo.subsonic.SubsonicException
import app.winters.octo.subsonic.access
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// The lines a family playlist shows and the words a refused change gives.
class FamilyPlaylistDesktopTest {
    @Test
    fun aFamilyPlaylistsDetailsLeaveOutPublicOrPrivate() {
        assertEquals("By Family · " + songsLine(3, 600), playlistDetails("Family", 3, 600, null))
        assertTrue(playlistDetails("alex", 3, 600, true).endsWith(" · Public"))
    }

    @Test
    fun aMemberMayRenameButNotDeleteWhereOctoSaysSo() {
        val row = Playlist(
            "f1", "Road trip", owner = "Family", octoFamily = true,
            octoFamilyMayRename = true, octoFamilyMayDelete = false,
        )
        val access = row.access("winters", null, removesById = true)
        assertTrue(access.family)
        assertTrue(access.rename)
        assertFalse(access.mayRename)
        assertFalse(access.delete)
    }

    @Test
    fun aNotAllowedAnswerIsShownInTheServersWords() {
        val sentence = "Only the person who made this playlist or a family manager can change it."
        assertEquals(sentence, playlistFailure(SubsonicException.Server(50, sentence)))
        assertEquals("The server said: x", playlistFailure(SubsonicException.Server(0, "x")))
    }
}
