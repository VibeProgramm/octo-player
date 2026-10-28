package app.winters.octo.subsonic

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// Octo's ranked lists: an artist's top songs and the chart, each song in the
// library or found online, as Octo sends them.
class TopSongsTest {
    private val server = MockWebServer()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun client() = SubsonicClient(server.url("/"), Credentials("winters", "secret"), OkHttpClient())

    private fun answer(body: String) = server.enqueue(MockResponse.Builder().body(body).build())

    private fun ok(inner: String) = """{"subsonic-response":{"status":"ok","version":"1.16.1","type":"octo","openSubsonic":true,$inner}}"""

    private val list = """"topSongs":{"artist":"Daft Punk","source":"lastfm","entry":[
        {"rank":1,"plays":2536925,"listeners":220546,"inLibrary":true,"song":{"id":"nd-1","title":"Harder, Better, Faster, Stronger","artist":"Daft Punk","album":"Discovery","isExternal":false,"starred":"2026-01-01T00:00:00Z"}},
        {"rank":2,"plays":null,"listeners":null,"inLibrary":false,"song":{"id":"Xy12","title":"Aerodynamic","artist":"Daft Punk","album":"Discovery","isExternal":true,"duration":212},"later":1}
    ]}"""

    @Test
    fun asksForTheArtistByNameAndIdAndReadsTheRanks() = runTest {
        answer(ok(list))
        val top = client().artistTopSongs("Daft Punk", artistId = "ar-27", count = 20)

        val request = server.takeRequest()
        assertEquals("/rest/getArtistTopSongs", request.url.encodedPath)
        assertEquals("Daft Punk", request.url.queryParameter("artist"))
        assertEquals("ar-27", request.url.queryParameter("id"))
        assertEquals("20", request.url.queryParameter("count"))

        assertEquals("Daft Punk", top.artist)
        assertEquals(TOP_SONGS_LASTFM, top.source)
        val (first, second) = top.entry
        assertEquals(1, first.rank)
        assertEquals(2_536_925L, first.plays)
        assertTrue(first.inLibrary)
        assertEquals("nd-1", first.song?.id)
        assertFalse(first.song!!.isExternal)
        assertEquals(2, second.rank)
        assertNull(second.plays)
        assertFalse(second.inLibrary)
        assertTrue(second.song!!.isExternal)
    }

    @Test
    fun theChartNamesNoArtist() = runTest {
        answer(ok(""""topSongs":{"artist":null,"source":"deezer","entry":[]}"""))
        val chart = client().topChart(50)

        val request = server.takeRequest()
        assertEquals("/rest/getTopChart", request.url.encodedPath)
        assertEquals("50", request.url.queryParameter("count"))
        assertNull(chart.artist)
        assertEquals(TOP_SONGS_DEEZER, chart.source)
        assertTrue(chart.entry.isEmpty())
    }

    @Test
    fun aServerThatSendsNoListHasNone() = runTest {
        answer(ok(""""x":1"""))
        assertTrue(client().topChart().entry.isEmpty())
    }

    @Test
    fun aChartIsAskedForByItsIdAndSaysWhichItIs() = runTest {
        answer(ok(""""topSongs":{"artist":null,"source":"apple","chart":"18","name":"Top Hip-Hop/Rap","country":"us","entry":[
            {"rank":1,"plays":null,"listeners":null,"inLibrary":false,"song":{"id":"Ab1","title":"Janice STFU","artist":"Drake","isExternal":true}}
        ]}"""))

        val top = client().topChart(count = 50, chart = "18")

        val request = server.takeRequest()
        assertEquals("/rest/getTopChart", request.url.encodedPath)
        assertEquals("18", request.url.queryParameter("chart"))
        assertEquals("50", request.url.queryParameter("count"))
        assertEquals("18", top.chart)
        assertEquals("Top Hip-Hop/Rap", top.name)
        assertEquals("us", top.country)
        assertEquals(TOP_SONGS_APPLE, top.source)
        assertEquals("Janice STFU", top.entry.single().song?.title)
    }

    @Test
    fun withoutAChartNoneIsSentAndAnOlderAnswerStillReads() = runTest {
        answer(ok(""""topSongs":{"artist":null,"source":"deezer","entry":[]}"""))

        val top = client().topChart()

        assertNull(server.takeRequest().url.queryParameter("chart"))
        assertNull(top.chart)
        assertNull(top.name)
        assertEquals(TOP_SONGS_DEEZER, top.source)
    }

    @Test
    fun readsTheChartsTheCountryHas() = runTest {
        answer(ok(""""charts":{"country":"us","chart":[
            {"id":"34","name":"Popular right now","label":"Top songs","kind":"overall","playlist":"og1","on":true},
            {"id":"new","name":"Best New Songs","label":"Best New Songs","kind":"new","on":false},
            {"id":"18","name":"Top Hip-Hop/Rap","label":"Hip-Hop/Rap","kind":"genre","on":true,"later":1}
        ]}"""))

        val charts = client().charts()

        assertEquals("/rest/getCharts", server.takeRequest().url.encodedPath)
        assertEquals("us", charts.country)
        assertEquals(listOf("34", "new", "18"), charts.chart.map { it.id })
        assertEquals("og1", charts.chart[0].playlist)
        assertTrue(charts.chart[0].on)
        assertNull(charts.chart[1].playlist)
        assertEquals("Hip-Hop/Rap", charts.chart[2].label)
        assertEquals("genre", charts.chart[2].kind)
    }

    private val v3Charts = """"charts":{"country":"jp","setting":"br","pinned":["jp","kr"],"countries":["br","jp","kr","us"],"maxPinned":3,"serverCountry":"us","chart":[
        {"id":"34","name":"Popular right now","label":"Top songs","kind":"overall","playlist":"og9","on":true}
    ]}"""

    @Test
    fun aCountryIsSentOnBothChartCalls() = runTest {
        answer(ok(v3Charts))
        answer(ok(""""topSongs":{"artist":null,"source":"apple","chart":"34","country":"jp","entry":[]}"""))

        client().charts(country = "jp")
        client().topChart(count = 50, chart = "34", country = "jp")

        val charts = server.takeRequest().url
        assertEquals("/rest/getCharts", charts.encodedPath)
        assertEquals("jp", charts.queryParameter("country"))
        val top = server.takeRequest().url
        assertEquals("/rest/getTopChart", top.encodedPath)
        assertEquals("jp", top.queryParameter("country"))
        assertEquals("34", top.queryParameter("chart"))
    }

    @Test
    fun withoutACountryNoneIsSent() = runTest {
        answer(ok(v3Charts))
        answer(ok(""""topSongs":{"artist":null,"source":"apple","entry":[]}"""))

        client().charts()
        client().topChart(50, "34")

        assertNull(server.takeRequest().url.queryParameter("country"))
        assertNull(server.takeRequest().url.queryParameter("country"))
    }

    @Test
    fun theListenersOwnChoicesAreRead() = runTest {
        answer(ok(v3Charts))

        val charts = client().charts(country = "jp")

        assertEquals("jp", charts.country)
        assertEquals("br", charts.setting)
        assertEquals(listOf("jp", "kr"), charts.pinned)
        assertEquals(listOf("br", "jp", "kr", "us"), charts.countries)
        assertEquals(3, charts.maxPinned)
        assertEquals("us", charts.serverCountry)
        assertEquals("og9", charts.chart.single().playlist)
    }

    @Test
    fun anOlderAnswerReadsWithNoChoices() = runTest {
        answer(ok(""""charts":{"country":"us","chart":[{"id":"34","name":"Popular right now","label":"Top songs","kind":"overall","on":true}]}"""))

        val charts = client().charts()

        assertNull(charts.setting)
        assertTrue(charts.pinned.isEmpty())
        assertTrue(charts.countries.isEmpty())
        assertEquals(0, charts.maxPinned)
        assertNull(charts.serverCountry)
    }

    @Test
    fun savingTheCountrySendsItAndReadsTheCharts() = runTest {
        answer(ok(v3Charts))

        val charts = client().setChartCountry(CHART_COUNTRY_AUTO)

        val request = server.takeRequest().url
        assertEquals("/rest/setChartCountry", request.encodedPath)
        assertEquals("auto", request.queryParameter("country"))
        assertEquals("br", charts.setting)
    }

    @Test
    fun pinningSendsOneCountryAndWhetherItIsPinned() = runTest {
        answer(ok(v3Charts))
        answer(ok(v3Charts))

        client().pinChartCountry("jp", pinned = true)
        client().pinChartCountry("kr", pinned = false)

        val pin = server.takeRequest().url
        assertEquals("/rest/pinChartCountry", pin.encodedPath)
        assertEquals("jp", pin.queryParameter("country"))
        assertEquals("true", pin.queryParameter("pinned"))
        val unpin = server.takeRequest().url
        assertEquals("kr", unpin.queryParameter("country"))
        assertEquals("false", unpin.queryParameter("pinned"))
    }

    @Test
    fun aSaveTheServerRefusedThrows() = runTest {
        answer("""{"subsonic-response":{"status":"failed","version":"1.16.1","error":{"code":10,"message":"You can pin at most 3 countries."}}}""")

        val refused = runCatching { client().pinChartCountry("de", pinned = true) }.exceptionOrNull()

        assertTrue(refused is SubsonicException.Server)
        assertEquals("You can pin at most 3 countries.", refused?.message)
    }

    @Test
    fun aSaveAnsweredWithoutTheChartsThrows() = runTest {
        answer(ok(""""x":1"""))

        val missing = runCatching { client().setChartCountry("jp") }.exceptionOrNull()

        assertTrue(missing is SubsonicException.Server)
    }
}
