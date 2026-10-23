package app.winters.octo.ui.favourites

import androidx.navigation3.runtime.NavKey
import app.winters.octo.ui.common.albums
import app.winters.octo.ui.common.artists
import app.winters.octo.ui.common.songs
import app.winters.octo.ui.nav.FavouritesRoute
import app.winters.octo.ui.nav.LikedRoute

// The parts of the Favourites page, in the order they show.
enum class FavouriteSegment(val label: String) {
    Songs("Songs"),
    Albums("Albums"),
    Artists("Artists"),
}

// Which part the page opens on, once all three are read: liked songs when
// there are any, since a heart on a song is the usual way to keep one;
// otherwise the first part that has something; Songs, with its note on how
// to add, when nothing does.
fun firstSegment(songs: Int, albums: Int, artists: Int): FavouriteSegment =
    when {
        songs > 0 -> FavouriteSegment.Songs
        albums > 0 -> FavouriteSegment.Albums
        artists > 0 -> FavouriteSegment.Artists
        else -> FavouriteSegment.Songs
    }

// The part a way in asks the page to open on. Hearted songs live on the
// Favourites page, so Liked songs opens it on the songs; the Favourite
// albums shelf opens it on the albums. Null leaves it to firstSegment.
fun openingSegment(route: NavKey): FavouriteSegment? = when (route) {
    LikedRoute -> FavouriteSegment.Songs
    is FavouritesRoute -> if (route.albums) FavouriteSegment.Albums else null
    else -> null
}

// What the title line counts for the part open: its songs, albums or
// artists. Nothing until that part is read.
fun favouriteCount(segment: FavouriteSegment, songCount: Int?, albumCount: Int?, artistCount: Int?): String? = when (segment) {
    FavouriteSegment.Songs -> songCount?.let { songs(it) }
    FavouriteSegment.Albums -> albumCount?.let { albums(it) }
    FavouriteSegment.Artists -> artistCount?.let { artists(it) }
}
