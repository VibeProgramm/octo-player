package app.winters.octo.subsonic

// The owner Octo lists every family playlist under.
const val FAMILY_PLAYLIST_OWNER = "Family"

// The extension a server lists when its family playlist rows say who may
// rename or delete them and it takes removals by song (octoSongToRemove).
const val FAMILY_PLAYLISTS_EXTENSION = "octoFamilyPlaylists"

// Whether a playlist is one of the family's: listed under the family, and
// not under the signed-in account itself. A server that marks its family
// playlists (`octoFamily`) decides; the owner name is read only from older
// servers.
fun isFamilyPlaylist(owner: String?, username: String?, octoFamily: Boolean? = null): Boolean {
    if (octoFamily != null) return octoFamily
    val name = owner?.trim().orEmpty()
    return name.equals(FAMILY_PLAYLIST_OWNER, ignoreCase = true) &&
        !name.equals(username?.trim().orEmpty(), ignoreCase = true)
}

// What the signed-in listener may do with a playlist. `rename` is sure to
// work; `mayRename` is for the server to decide (a family playlist's maker
// may, and the app cannot tell who made it).
data class PlaylistAccess(
    val add: Boolean = false,
    val remove: Boolean = false,
    val reorder: Boolean = false,
    val rename: Boolean = false,
    val mayRename: Boolean = false,
    val describe: Boolean = false,
    val visibility: Boolean = false,
    val delete: Boolean = false,
    val family: Boolean = false,
)

// The listener's own playlist: everything. A family playlist the server
// lists as changeable: add songs; Octo's own answers (`mayRename`,
// `mayDelete`) decide rename and delete where the server sends them; a
// server that removes by song (`removesById`) lets every editor take songs
// out; on older servers a listener who hears only clean songs, or one whose
// family details are not known yet, takes nothing out, and rename is the
// server's call for a non-manager. Never reorder, describe, or make public
// or private (Octo refuses or ignores those). Any other playlist: nothing.
// A playlist with no owner named, or a listener the server did not name,
// counts as the listener's own.
fun playlistAccess(
    owner: String?,
    readonly: Boolean,
    username: String?,
    me: FamilyMe?,
    octoFamily: Boolean? = null,
    mayRename: Boolean? = null,
    mayDelete: Boolean? = null,
    removesById: Boolean = false,
): PlaylistAccess {
    val family = isFamilyPlaylist(owner, username, octoFamily)
    if (readonly) return PlaylistAccess(family = family)
    if (family) {
        val manager = me?.abilities?.manageFamily == true
        return PlaylistAccess(
            add = true,
            remove = removesById || me?.abilities?.cleanOnly == false,
            rename = mayRename ?: manager,
            mayRename = mayRename == null && !manager && me?.abilities?.familyPlaylistsEdit != false,
            delete = mayDelete ?: manager,
            family = true,
        )
    }
    val own = owner.isNullOrBlank() || username.isNullOrBlank() || owner.equals(username, ignoreCase = true)
    return if (own) {
        PlaylistAccess(add = true, remove = true, reorder = true, rename = true, describe = true, visibility = true, delete = true)
    } else {
        PlaylistAccess()
    }
}

// A playlist row's access for the signed-in listener.
fun Playlist.access(username: String?, me: FamilyMe?, removesById: Boolean): PlaylistAccess =
    playlistAccess(owner, readonly, username, me, octoFamily, octoFamilyMayRename, octoFamilyMayDelete, removesById)

fun PlaylistWithSongs.access(username: String?, me: FamilyMe?, removesById: Boolean): PlaylistAccess =
    playlistAccess(owner, readonly, username, me, octoFamily, octoFamilyMayRename, octoFamilyMayDelete, removesById)

// The songs at these places as Octo takes them out by song: each one's
// id and which copy of that id it is (1 for the first), in place order.
fun songsToRemove(ids: List<String>, positions: List<Int>): List<Pair<String, Int>> =
    positions.distinct().sorted().filter { it in ids.indices }.map { at ->
        val id = ids[at]
        id to (0..at).count { ids[it] == id }
    }
