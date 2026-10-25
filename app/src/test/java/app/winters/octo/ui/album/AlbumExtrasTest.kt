package app.winters.octo.ui.album

import app.winters.octo.catalog.TrackEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class AlbumExtrasTest {
    private fun track(id: String, genre: String = "", rating: Int = 0) = TrackEntity(
        id = id, sourceId = "device", nativeId = id, title = id, searchKey = id, sortKey = id,
        artist = "", artistId = "", album = "", albumId = "", trackNo = null, discNo = null, year = null,
        durationMs = 0, addedAt = 0, mimeType = null, sizeBytes = null, artwork = null, uri = null,
        genre = genre, rating = rating,
    )

    // With "Library songs only" (or the album's own toggle) the page lists
    // only its library songs, and a row's place in that list is the song
    // that plays.
    @Test
    fun onlyLibrarySongsAreListedAndEachPlaysItself() {
        val album = listOf(track("1"), track("find:2"), track("3"), track("find:4"), track("5"))
        val shown = albumSongsShown(album, libraryOnly = true)
        assertEquals(listOf("1", "3", "5"), shown.map { it.id })
        assertEquals("5", shown[shown.indexOf(album[4])].id)
        assertEquals(2, shown.indexOf(album[4]))
        assertEquals(album, albumSongsShown(album, libraryOnly = false))
    }

    @Test
    fun theGenreIsTheOneMostSongsHave() {
        assertEquals("Synthpop", albumGenre(listOf(track("1", "Synthpop"), track("2", "Rock"), track("3", "synthpop"))))
        // A tie goes to the genre met first; songs with none do not count.
        assertEquals("Rock", albumGenre(listOf(track("1"), track("2", "Rock"), track("3", "Jazz"))))
        assertNull(albumGenre(listOf(track("1"), track("2", " "))))
        assertNull(albumGenre(emptyList()))
    }

    @Test
    fun theAverageLeavesOutSongsWithNoRating() {
        assertEquals(4.5, averageRating(listOf(track("1", rating = 4), track("2"), track("3", rating = 5)))!!, 0.0001)
        assertNull(averageRating(listOf(track("1"), track("2"))))
    }

    @Test
    fun theAverageReadsToOnePlace() {
        assertEquals("4.3", starsText(13 / 3.0, Locale.US))
        assertEquals("4", starsText(4.0, Locale.US))
        assertEquals("5", starsText(4.96, Locale.US))
        assertEquals("Songs rated 1 star on average", averageRatingSpoken(1.0, Locale.US))
        assertEquals("Songs rated 3.5 stars on average", averageRatingSpoken(3.5, Locale.US))
    }

    // Everything that used to sit in rows under Play and Shuffle is in More,
    // in this order, and only when it applies.
    @Test
    fun theMoreMenuHoldsTheAlbumsLesserActions() {
        assertEquals(
            listOf(AlbumMore.Share, AlbumMore.Download, AlbumMore.AddMissing, AlbumMore.OnlyMine, AlbumMore.Genre),
            albumMore(share = true, download = true, missing = true, offerOnlyMine = true, genre = true),
        )
        // Only my songs comes with missing songs, and not while the setting
        // already leaves them out everywhere.
        assertEquals(listOf(AlbumMore.AddMissing), albumMore(share = false, download = false, missing = true, offerOnlyMine = false, genre = false))
        assertEquals(listOf(AlbumMore.Genre), albumMore(share = false, download = false, missing = false, offerOnlyMine = true, genre = true))
        assertEquals(emptyList<AlbumMore>(), albumMore(share = false, download = false, missing = false, offerOnlyMine = true, genre = false))
    }

    @Test
    fun addMissingSaysWhatIsLeftThenFollowsTheDownloads() {
        assertEquals("Add the 3 missing songs" to true, addMissingAction(askable = 3, follows = false))
        assertEquals("Add the missing song" to true, addMissingAction(askable = 1, follows = true))
        assertEquals("Adding, show downloads" to true, addMissingAction(askable = 0, follows = true))
        assertEquals("Adding" to false, addMissingAction(askable = 0, follows = false))
    }

    // The genre, the rating and the library note that had lines of their
    // own are now facts on the one line under the title.
    @Test
    fun theFactsLineKeepsTheGenreRatingAndLibraryNote() {
        val before = Locale.getDefault()
        Locale.setDefault(Locale.US)
        try {
            assertEquals(
                listOf("2020", "12 songs", "45 min", "Rock", "Rated 4.3", "8 of 12 in your library"),
                albumFacts(2020, "12 songs", "45 min", "Rock", 4.26, "8 of 12 in your library"),
            )
            assertEquals(listOf("12 songs"), albumFacts(null, "12 songs", null, null, null, null))
        } finally {
            Locale.setDefault(before)
        }
    }
}
