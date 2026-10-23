package app.winters.octo.subsonic

// The owner Octo lists every family playlist under.
const val FAMILY_PLAYLIST_OWNER = "Family"

// Whether a playlist is one of the family's: listed under the family, and
// not under the signed-in account itself.
fun isFamilyPlaylist(owner: String?, username: String?): Boolean {
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
// lists as changeable: add songs, and take them out unless the listener
// hears only clean songs (the server hides explicit ones from them, so
// places in their list are not the playlist's); rename and delete for a
// family manager, rename for the server to decide otherwise; never reorder,
// describe, or make public or private (Octo refuses or ignores those). Any
// other playlist: nothing. A playlist with no owner named, or a listener
// the server did not name, counts as the listener's own.
fun playlistAccess(owner: String?, readonly: Boolean, username: String?, me: FamilyMe?): PlaylistAccess {
    val family = isFamilyPlaylist(owner, username)
    if (readonly) return PlaylistAccess(family = family)
    if (family) {
        val manager = me?.abilities?.manageFamily == true
        return PlaylistAccess(
            add = true,
            remove = me?.abilities?.cleanOnly != true,
            rename = manager,
            mayRename = !manager && me?.abilities?.familyPlaylistsEdit != false,
            delete = manager,
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
