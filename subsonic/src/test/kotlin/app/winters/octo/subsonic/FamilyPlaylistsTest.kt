package app.winters.octo.subsonic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// What a listener may do with their own, the family's and others' playlists.
class FamilyPlaylistsTest {
    private val everything =
        PlaylistAccess(add = true, remove = true, reorder = true, rename = true, describe = true, visibility = true, delete = true)

    private fun me(abilities: FamilyAbilities) = FamilyMe(abilities = abilities)

    @Test
    fun familyOwnerIsRecognized() {
        assertTrue(isFamilyPlaylist("Family", "alex"))
        assertTrue(isFamilyPlaylist("family", "alex"))
        assertTrue(isFamilyPlaylist(" Family ", "alex"))
    }

    @Test
    fun otherOwnersAreNotFamily() {
        assertFalse(isFamilyPlaylist("Family", "Family"))
        assertFalse(isFamilyPlaylist("alex", "alex"))
        assertFalse(isFamilyPlaylist(null, "alex"))
    }

    @Test
    fun ownPlaylistAllowsEverything() {
        assertEquals(everything, playlistAccess("alex", false, "alex", null))
    }

    @Test
    fun ownPlaylistIgnoresCase() {
        assertEquals(everything, playlistAccess("Alex", false, "alex", null))
    }

    @Test
    fun playlistWithNoOwnerIsOwn() {
        assertEquals(everything, playlistAccess(null, false, "alex", null))
    }

    @Test
    fun someoneElsesPlaylistAllowsNothing() {
        assertEquals(PlaylistAccess(), playlistAccess("sam", false, "alex", null))
    }

    @Test
    fun familyMemberMayAddRemoveAndMaybeRename() {
        val member = me(FamilyAbilities(familyPlaylistsEdit = true, manageFamily = false))
        assertEquals(
            PlaylistAccess(add = true, remove = true, mayRename = true, family = true),
            playlistAccess("Family", false, "alex", member),
        )
    }

    @Test
    fun familyManagerMayRenameAndDelete() {
        val manager = me(FamilyAbilities(familyPlaylistsEdit = true, manageFamily = true))
        assertEquals(
            PlaylistAccess(add = true, remove = true, rename = true, delete = true, family = true),
            playlistAccess("Family", false, "alex", manager),
        )
    }

    @Test
    fun familyPlaylistWithoutAccountInfoLeavesRenameToServer() {
        assertEquals(
            PlaylistAccess(add = true, remove = true, mayRename = true, family = true),
            playlistAccess("Family", false, "alex", null),
        )
    }

    @Test
    fun cleanOnlyListenerCannotRemoveFromFamilyPlaylist() {
        val access = playlistAccess("Family", false, "alex", me(FamilyAbilities(cleanOnly = true)))
        assertTrue(access.add)
        assertFalse(access.remove)
    }

    @Test
    fun readonlyFamilyPlaylistAllowsNothingButIsFamily() {
        assertEquals(PlaylistAccess(family = true), playlistAccess("Family", true, "alex", null))
    }

    @Test
    fun blankUsernameCountsAsOwnOrFamily() {
        assertEquals(everything, playlistAccess("alex", false, "", null))
        val family = playlistAccess("Family", false, "", null)
        assertTrue(family.family)
        assertFalse(family.reorder)
    }
}
