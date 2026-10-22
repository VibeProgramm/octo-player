package app.winters.octo.discovery

import app.winters.octo.subsonic.ChartChoices
import app.winters.octo.subsonic.TOP_SONGS_DEEZER
import app.winters.octo.subsonic.TOP_SONGS_LASTFM
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TopSongsTest {
    private val artists = listOf("Daft Punk Tribute Band", "Daft Punk", "The Weeknd", "Bob Marley & The Wailers")

    @Test
    fun theArtistSearchedForIsTheOneNamedWhole() {
        assertEquals("Daft Punk", searchedArtist("daft punk", artists) { it })
        assertEquals("Daft Punk", searchedArtist("  DAFT PUNK ", artists) { it })
        assertEquals("The Weeknd", searchedArtist("weeknd", artists) { it })
        assertEquals("The Weeknd", searchedArtist("the weeknd", artists) { it })
    }

    @Test
    fun aPartOfANameOrASongIsNoArtist() {
        assertNull(searchedArtist("daft", artists) { it })
        assertNull(searchedArtist("bob marley", artists) { it })
        assertNull(searchedArtist("get lucky", artists) { it })
        assertNull(searchedArtist("", artists) { it })
    }

    @Test
    fun playsAreShort() {
        assertEquals("1 play", playsText(1))
        assertEquals("940 plays", playsText(940))
        assertEquals("1K plays", playsText(1_000))
        assertEquals("1.5K plays", playsText(1_460))
        assertEquals("12K plays", playsText(12_345))
        assertEquals("13K plays", playsText(12_960))
        assertEquals("2.5M plays", playsText(2_536_925))
        assertEquals("1M plays", playsText(999_960))
        assertEquals("120M plays", playsText(119_800_000))
        assertEquals("1.2B plays", playsText(1_234_000_000))
    }

    @Test
    fun theRankingIsNamed() {
        assertEquals("Most played on Last.fm", rankedBy(TOP_SONGS_LASTFM))
        assertEquals("Most popular on Deezer", rankedBy(TOP_SONGS_DEEZER))
        assertNull(rankedBy(""))
    }

    @Test
    fun appleListsSayWhichKindTheyAre() {
        assertEquals("Most played on Apple Music", rankedBy("apple"))
        assertEquals("Most played on Apple Music", rankedBy("apple", "18"))
        assertEquals("Picked by Apple Music", rankedBy("apple", "new"))
        assertEquals("Trending on Apple Music", rankedBy("apple", "trending"))
        assertEquals("Most popular on Deezer", rankedBy("deezer", "34"))
    }

    @Test
    fun roomsHoldAHundredChartsFifty() {
        assertEquals(100, chartSongs("new"))
        assertEquals(100, chartSongs("trending"))
        assertEquals(50, chartSongs("34"))
        assertEquals(50, chartSongs("18"))
    }

    @Test
    fun theChartsPageNeedsTheSecondVersion() {
        assertFalse(chartsOffered(listOf(app.winters.octo.subsonic.Extension("octoTopSongs", listOf(1)))))
        assertTrue(chartsOffered(listOf(app.winters.octo.subsonic.Extension("octoTopSongs", listOf(1, 2)))))
        assertFalse(chartsOffered(emptyList()))
    }

    @Test
    fun chartCountriesNeedTheThirdVersion() {
        assertFalse(chartCountriesOffered(listOf(app.winters.octo.subsonic.Extension("octoTopSongs", listOf(1, 2)))))
        assertTrue(chartCountriesOffered(listOf(app.winters.octo.subsonic.Extension("octoTopSongs", listOf(1, 2, 3)))))
        assertFalse(chartCountriesOffered(emptyList()))
    }

    private val names = mapOf("br" to "Brazil", "jp" to "Japan", "kr" to "South Korea", "us" to "United States", "ax" to "Åland Islands", "de" to "Germany")
    private val name: (String) -> String = { names.getValue(it) }

    @Test
    fun theOwnCountryComesFirstThenThePinnedOnesThenTheRestByName() {
        val codes = listOf("us", "kr", "jp", "de", "br", "ax")

        assertEquals(listOf("br", "kr", "jp", "ax", "de", "us"), chartCountryOrder(codes, own = "br", pinned = listOf("kr", "jp"), name = name))
        assertEquals(listOf("ax", "br", "de", "jp", "kr", "us"), chartCountryOrder(codes, own = null, pinned = emptyList(), name = name))
        // The one in view follows the pinned ones.
        assertEquals(listOf("br", "kr", "de", "ax", "jp", "us"), chartCountryOrder(codes, own = "br", pinned = listOf("kr"), name = name, viewing = "de"))
    }

    private fun own(setting: String, pinned: List<String> = emptyList()) = ChartChoices(
        country = if (setting == "auto") "us" else setting,
        setting = setting,
        pinned = pinned,
        countries = listOf("br", "de", "jp", "kr", "us"),
        maxPinned = 3,
        serverCountry = "us",
    )

    @Test
    fun followingTheServerIsTheFirstLineAndChosenWhileFollowed() {
        val rows = chartCountryRows(own("auto"), viewing = null, name = name)

        assertEquals("Same as the server (United States)", rows.first().label)
        assertNull(rows.first().code)
        assertTrue(rows.first().chosen)
        // No country says "Yours" while the listener follows the server.
        assertTrue(rows.none { it.detail == "Yours" })
        assertEquals(1, rows.count { it.chosen })
    }

    @Test
    fun aChosenCountrySaysYoursAndPinnedOnesSayPinned() {
        val rows = chartCountryRows(own("br", pinned = listOf("kr")), viewing = null, name = name)

        assertEquals(listOf(null, "br", "kr"), rows.filter { it.top }.map { it.code })
        assertEquals("Yours", rows[1].detail)
        assertTrue(rows[1].chosen)
        assertEquals("Pinned", rows[2].detail)
        assertFalse(rows.first().chosen)
    }

    @Test
    fun theCountryInViewIsChosenAndJoinsTheTop() {
        val rows = chartCountryRows(own("br", pinned = listOf("kr")), viewing = "jp", name = name)

        assertEquals(listOf(null, "br", "kr", "jp"), rows.filter { it.top }.map { it.code })
        assertEquals(listOf("jp"), rows.filter { it.chosen }.map { it.code })
        assertEquals(listOf("de", "us"), rows.filterNot { it.top }.map { it.code })
    }

    @Test
    fun aCountryIsFoundByItsNameAccentsAsideOrItsWholeCode() {
        assertTrue(countryMatches("Côte d'Ivoire", "ci", "cote"))
        assertTrue(countryMatches("Japan", "jp", "JP"))
        assertTrue(countryMatches("Japan", "jp", "  pan "))
        // Codes match only whole.
        assertFalse(countryMatches("Germany", "de", "d"))
        assertTrue(countryMatches("Germany", "de", ""))
        assertTrue(countryMatches("Germany", "de", "  "))
    }
}
