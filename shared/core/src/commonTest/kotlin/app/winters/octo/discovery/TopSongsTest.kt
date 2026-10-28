package app.winters.octo.discovery

import app.winters.octo.subsonic.ChartChoices
import app.winters.octo.subsonic.TOP_SONGS_DEEZER
import app.winters.octo.subsonic.TOP_SONGS_LASTFM
import app.winters.octo.subsonic.SubsonicException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
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
    fun yoursIsTheFirstLineAndChosenWhileViewed() {
        val rows = chartCountryRows(own("auto"), viewing = null, name = name)

        assertEquals("Yours (United States)", rows.first().label)
        assertEquals(CountryPick.Yours, rows.first().pick)
        assertTrue(rows.first().chosen)
        // Following the server already: no line to follow it again.
        assertTrue(rows.none { it.pick == CountryPick.Server })
        assertEquals(listOf("br", "de", "jp", "kr", "us"), rows.filterNot { it.top }.map { it.code })
        assertEquals(1, rows.count { it.chosen })
    }

    @Test
    fun aChosenCountryOffersTheServersAndPinnedOnesSaySo() {
        val rows = chartCountryRows(own("br", pinned = listOf("kr")), viewing = null, name = name)

        assertEquals(listOf("Yours (Brazil)", "Same as the server (United States)"), rows.filter { it.top }.map { it.label })
        assertEquals(CountryPick.Server, rows[1].pick)
        assertEquals("us", rows[1].code)
        assertEquals("Pinned", rows.single { it.code == "kr" && !it.top }.detail)
        assertNull(rows.single { it.code == "jp" }.detail)
    }

    @Test
    fun theCountryInViewIsChosen() {
        val rows = chartCountryRows(own("br", pinned = listOf("kr")), viewing = "jp", name = name)

        assertEquals(listOf("jp"), rows.filter { it.chosen }.map { it.code })
        assertFalse(rows.first().chosen)
    }

    @Test
    fun aCountryOffersALookItsChartsAndItsPlaylists() {
        val choices = countryChoices(own("auto"), "jp", "Japan")

        assertEquals(listOf(CountryAction.Look, CountryAction.UseForMine, CountryAction.AddPlaylists), choices.map { it.action })
        assertEquals(listOf("Just look here", "Use Japan for my charts", "Also add Japan's playlists"), choices.map { it.label })
        assertEquals("Nothing is saved", choices[0].detail)
        assertTrue(choices.all { it.enabled })
    }

    @Test
    fun aPinnedCountryOffersToRemoveItsPlaylists() {
        val choices = countryChoices(own("auto", pinned = listOf("jp")), "jp", "Japan")

        assertEquals(CountryAction.RemovePlaylists, choices[2].action)
        assertEquals("Remove Japan's playlists", choices[2].label)
    }

    @Test
    fun aFullSetOfPinsCannotTakeAnother() {
        val choices = countryChoices(own("auto", pinned = listOf("br", "kr", "de")), "jp", "Japan")

        assertEquals("3 countries already have playlists", choices[2].label)
        assertFalse(choices[2].enabled)
        // One already pinned can still be removed.
        assertTrue(countryChoices(own("auto", pinned = listOf("br", "kr", "jp")), "jp", "Japan")[2].enabled)
    }

    @Test
    fun theServersCountryOffersALookOrFollowingItAgain() {
        val choices = countryChoices(own("br"), "us", "United States", server = true)

        assertEquals(listOf(CountryAction.Look, CountryAction.FollowServer), choices.map { it.action })
        assertEquals("Use the server's country for my charts", choices[1].label)
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

    @Test
    fun onlyRefusalsTheListenerCanActOnKeepTheServersWords() {
        val tooMany = app.winters.octo.subsonic.SubsonicException.Server(0, "Still loading your other countries. Try again in a moment.")
        val fourth = app.winters.octo.subsonic.SubsonicException.Server(10, "You can pin at most 3 countries.")
        assertEquals("Still loading your other countries. Try again in a moment.", chartRefusal(tooMany))
        assertEquals("You can pin at most 3 countries.", chartRefusal(fourth))
        assertNull(chartRefusal(app.winters.octo.subsonic.SubsonicException.Server(70, "Not found")))
        assertNull(chartRefusal(app.winters.octo.subsonic.SubsonicException.NotFound("gone")))
    }

    private val stillLoading = SubsonicException.Server(0, "Still loading your other countries. Try again in a moment.")

    @Test
    fun a_still_loading_refusal_is_asked_again_when_a_read_finishes() = runBlocking {
        val reads = CountryReads()
        val loading = AtomicInteger(0)
        val asked = AtomicInteger(0)
        // A server loading two countries refuses a third until one is done.
        suspend fun hold(country: String, gate: CompletableDeferred<Unit>): String {
            loading.incrementAndGet()
            try {
                gate.await()
            } finally {
                loading.decrementAndGet()
            }
            return country
        }
        val gateA = CompletableDeferred<Unit>()
        val gateB = CompletableDeferred<Unit>()
        val a = async(start = CoroutineStart.UNDISPATCHED) { reads.read("fr") { hold("fr", gateA) } }
        val b = async(start = CoroutineStart.UNDISPATCHED) { reads.read("de") { hold("de", gateB) } }
        val third = async(start = CoroutineStart.UNDISPATCHED) {
            reads.read("jp") {
                asked.incrementAndGet()
                if (loading.get() >= 2) throw stillLoading
                "jp"
            }
        }
        // Refused once, and now waiting for a read to finish.
        assertEquals(1, asked.get())
        assertFalse(third.isCompleted)
        gateA.complete(Unit)
        assertEquals("jp", third.await())
        assertEquals("fr", a.await())
        assertEquals(2, asked.get())
        gateB.complete(Unit)
        assertEquals("de", b.await())
    }

    @Test
    fun a_refusal_with_nothing_loading_is_asked_once_more_then_shown() = runBlocking {
        val reads = CountryReads()
        val asked = AtomicInteger(0)
        val refused = runCatching {
            reads.read("jp") {
                asked.incrementAndGet()
                throw stillLoading
            }
        }.exceptionOrNull()
        assertEquals(stillLoading, refused)
        assertEquals(2, asked.get())
        // The listener's own charts are never asked again.
        val own = AtomicInteger(0)
        val ownRefused = runCatching {
            reads.read(null) {
                own.incrementAndGet()
                throw stillLoading
            }
        }.exceptionOrNull()
        assertEquals(stillLoading, ownRefused)
        assertEquals(1, own.get())
    }
}
