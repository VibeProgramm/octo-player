package app.winters.octo.desktop.search

import app.winters.octo.desktop.FakeServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// The Charts page's server side: the charts a country has and each chart's
// songs, only from a server that lists octoTopSongs version 2, and other
// countries, the listener's own and pins from one that lists version 3.
class ChartsTest {
    private val server = FakeServer()

    @After fun stop() = server.close()

    private val choices = """"charts":{"country":"us","chart":[
        {"id":"34","name":"Popular right now","label":"Top songs","kind":"overall","on":true},
        {"id":"new","name":"Best New Songs","label":"Best New Songs","kind":"new","on":true},
        {"id":"18","name":"Top Hip-Hop/Rap","label":"Hip-Hop/Rap","kind":"genre","on":false}
    ]}"""

    private fun chart(id: String, name: String) = """"topSongs":{"artist":null,"source":"apple","chart":"$id","name":"$name","country":"us","entry":[
        {"rank":1,"inLibrary":false,"song":{"id":"ext1","title":"Solar Eclipse","artist":"Drake","isExternal":true}}
    ]}"""

    private fun charts(extensions: List<String>, scope: CoroutineScope) = Charts(server.connection(extensions), scope)

    private suspend fun until(check: () -> Boolean) = withTimeout(5_000) { while (!check()) delay(20) }

    @Test
    fun aServerWithoutTheSecondVersionIsAskedNothing() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val charts = charts(listOf("octoTopSongs:1", "octoAcquisitions:1"), scope)
        server.answer("getOpenSubsonicExtensions", """"openSubsonicExtensions":[{"name":"octoTopSongs","versions":[1]}]""", type = "octo")

        charts.check()
        until { charts.offered != null }
        charts.load("34")
        delay(200)

        assertFalse(charts.offered!!)
        assertFalse(server.endpoints().contains("getTopChart"))
        assertFalse(server.endpoints().contains("getCharts"))
        scope.cancel()
    }

    @Test
    fun theChoicesAndAChartAreAskedOnceAndKept() = runBlocking {
        server.answer("getCharts", choices, type = "octo")
        server.answer("getTopChart", chart("new", "Best New Songs"), type = "octo")
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val charts = charts(listOf("octoTopSongs:1", "octoTopSongs:2"), scope)

        charts.loadChoices()
        until { charts.choices != null }
        charts.loadChoices()
        charts.load("new")
        until { charts.lists["|new"] != null }
        charts.load("new")
        delay(200)

        assertEquals(listOf("34", "new", "18"), charts.choices!!.chart.map { it.id })
        assertEquals(1, server.calls.count { it.url.pathSegments.last() == "getCharts" })
        val asked = server.calls.filter { it.url.pathSegments.last() == "getTopChart" }
        assertEquals(1, asked.size)
        assertEquals("new", asked.single().url.queryParameter("chart"))
        assertEquals("100", asked.single().url.queryParameter("count"))
        assertEquals("Best New Songs", charts.lists["|new"]!!.name)
        scope.cancel()
    }

    @Test
    fun aGenreIsAskedForFiftySongs() = runBlocking {
        server.answer("getTopChart", chart("18", "Top Hip-Hop/Rap"), type = "octo")
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val charts = charts(listOf("octoTopSongs:2"), scope)

        charts.load("18")
        until { charts.lists["|18"] != null }

        val asked = server.calls.single { it.url.pathSegments.last() == "getTopChart" }.url
        assertEquals("18", asked.queryParameter("chart"))
        assertEquals("50", asked.queryParameter("count"))
        assertTrue(charts.offered!!)
        scope.cancel()
    }

    // A server on octoTopSongs version 3: it keeps the listener's country
    // and pins, and answers each country's charts.
    @Volatile private var setting = "auto"
    @Volatile private var pinned = listOf<String>()

    private fun ownCountry() = if (setting == "auto") "us" else setting

    private fun v3Charts(country: String): String {
        val chips = when (country) {
            "jp" -> """{"id":"34","name":"Popular right now","label":"Top songs","kind":"overall","on":true},{"id":"27","name":"Top J-Pop","label":"J-Pop","kind":"genre","on":true}"""
            else -> """{"id":"34","name":"Popular right now","label":"Top songs","kind":"overall","on":true},{"id":"18","name":"Top Hip-Hop/Rap","label":"Hip-Hop/Rap","kind":"genre","on":true}"""
        }
        val pins = pinned.joinToString(",") { "\"$it\"" }
        return """"charts":{"country":"$country","setting":"$setting","pinned":[$pins],"countries":["br","jp","kr","us"],"maxPinned":3,"serverCountry":"us","chart":[$chips]}"""
    }

    private fun serveV3() {
        server.answerBy("getCharts") { server.ok(v3Charts(it.url.queryParameter("country") ?: ownCountry()), type = "octo") }
        server.answerBy("getTopChart") { request ->
            val id = request.url.queryParameter("chart")
            val country = request.url.queryParameter("country") ?: ownCountry()
            server.ok(""""topSongs":{"artist":null,"source":"apple","chart":"$id","country":"$country","entry":[]}""", type = "octo")
        }
        server.answerBy("setChartCountry") {
            setting = it.url.queryParameter("country")!!
            server.ok(v3Charts(ownCountry()), type = "octo")
        }
        server.answerBy("pinChartCountry") {
            val code = it.url.queryParameter("country")!!
            pinned = if (it.url.queryParameter("pinned") == "true") pinned + code else pinned - code
            server.ok(v3Charts(ownCountry()), type = "octo")
        }
    }

    private val v3 = listOf("octoTopSongs:1", "octoTopSongs:2", "octoTopSongs:3")

    private fun asked(endpoint: String) = server.calls.filter { it.url.pathSegments.last() == endpoint }

    @Test
    fun eachCountrysChartIsAskedForAndKeptApart() = runBlocking {
        serveV3()
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val charts = charts(v3, scope)

        charts.load("34")
        charts.load("34", "jp")
        until { charts.lists[charts.key("34")] != null && charts.lists[charts.key("34", "jp")] != null }

        assertEquals("us", charts.lists["|34"]!!.country)
        assertEquals("jp", charts.lists["jp|34"]!!.country)
        val countries = asked("getTopChart").map { it.url.queryParameter("country") }
        assertEquals(setOf(null, "jp"), countries.toSet())
        assertEquals(2, countries.size)
        scope.cancel()
    }

    @Test
    fun theCountryLookedAtHasItsOwnChoices() = runBlocking {
        serveV3()
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val charts = charts(v3, scope)
        charts.loadChoices()
        until { charts.own != null }
        charts.selected = "18"

        charts.pickCountry("jp")
        charts.loadChoices()
        until { charts.choices?.country == "jp" }

        assertEquals("jp", charts.country)
        assertEquals("jp", asked("getCharts").last().url.queryParameter("country"))
        assertEquals(listOf("34", "27"), charts.choices!!.chart.map { it.id })
        // Hip-Hop/Rap is not one of Japan's, so Popular right now shows.
        assertEquals("34", charts.selected)
        assertTrue(charts.canPickCountry)
        assertEquals("us", charts.own!!.country)

        // The listener's own country stops looking at another.
        charts.pickCountry("us")
        assertNull(charts.country)
        scope.cancel()
    }

    @Test
    fun openingThePageAsksForTheOwnChoicesAgain() = runBlocking {
        serveV3()
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val charts = charts(v3, scope)
        charts.loadChoices()
        until { charts.own != null }

        setting = "br"
        charts.opened()
        until { charts.own?.setting == "br" }

        assertEquals(2, asked("getCharts").size)
        assertTrue(asked("getCharts").all { it.url.queryParameter("country") == null })
        scope.cancel()
    }

    @Test
    fun usingACountryAsMineSavesItAndAsksForTheOwnChartsAgain() = runBlocking {
        serveV3()
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val charts = charts(v3, scope)
        charts.loadChoices()
        charts.load("34")
        until { charts.own != null && charts.lists["|34"] != null }
        charts.pickCountry("jp")

        charts.useAsMine()
        until { charts.own?.setting == "jp" && charts.lists["|34"]?.country == "jp" }

        assertNull(charts.country)
        assertEquals("jp", asked("setChartCountry").single().url.queryParameter("country"))
        // What was kept for the old country was let go and asked for again.
        assertEquals(2, asked("getTopChart").count { it.url.queryParameter("country") == null })
        scope.cancel()
    }

    @Test
    fun followingTheServerAgainSavesAuto() = runBlocking {
        serveV3()
        setting = "br"
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val charts = charts(v3, scope)
        charts.loadChoices()
        until { charts.own != null }

        charts.followServer()
        until { charts.own?.setting == "auto" }

        assertEquals("auto", asked("setChartCountry").single().url.queryParameter("country"))
        assertEquals("us", charts.shownCountry)
        scope.cancel()
    }

    @Test
    fun aPinIsJustThatCountry() = runBlocking {
        serveV3()
        pinned = listOf("kr")
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val charts = charts(v3, scope)
        charts.loadChoices()
        until { charts.own != null }

        charts.pickCountry("jp")
        charts.togglePin()
        until { charts.pinned == listOf("kr", "jp") }

        val pin = asked("pinChartCountry").single().url
        assertEquals("jp", pin.queryParameter("country"))
        assertEquals("true", pin.queryParameter("pinned"))
        assertTrue(charts.isPinned)
        assertEquals("jp", charts.country)
        scope.cancel()
    }

    @Test
    fun aFullSetOfPinsTakesNoMoreButLetsOneGo() = runBlocking {
        serveV3()
        pinned = listOf("br", "kr", "de")
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val charts = charts(v3, scope)
        charts.loadChoices()
        until { charts.own != null }

        charts.pickCountry("jp")
        assertFalse(charts.canPin)
        charts.togglePin()
        delay(200)
        assertTrue(asked("pinChartCountry").isEmpty())

        charts.pickCountry("kr")
        assertTrue(charts.isPinned)
        assertTrue(charts.canPin)
        charts.togglePin()
        until { charts.pinned == listOf("br", "de") }
        assertEquals("false", asked("pinChartCountry").single().url.queryParameter("pinned"))
        scope.cancel()
    }

    @Test
    fun aSaveThatDidNotHappenSaysSo() = runBlocking {
        serveV3()
        server.answerBy("setChartCountry") { server.failed(0, "Too many countries in an hour. Try again later.") }
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val charts = charts(v3, scope)
        charts.loadChoices()
        until { charts.own != null }
        charts.pickCountry("jp")

        charts.useAsMine()
        until { charts.problem != null }

        // The server's own words when it refused for a reason the listener can act on.
        assertEquals("Too many countries in an hour. Try again later.", charts.problem)
        assertEquals("jp", charts.country)
        assertFalse(charts.saving)
        scope.cancel()
    }

    @Test
    fun anyOtherFailedSaveSaysThePlainLine() = runBlocking {
        serveV3()
        server.answerBy("pinChartCountry") { server.failed(70, "Something broke.") }
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val charts = charts(v3, scope)
        charts.loadChoices()
        until { charts.own != null }
        charts.pickCountry("jp")

        charts.togglePin()
        until { charts.problem != null }

        assertEquals("Couldn't save. Try again.", charts.problem)
        scope.cancel()
    }

    @Test
    fun aCountryChangedElsewhereAsksForTheOwnChartAgain() = runBlocking {
        serveV3()
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val charts = charts(v3, scope)
        charts.loadChoices()
        charts.load("34")
        until { charts.own != null && charts.lists["|34"] != null }

        // Another device made Brazil the listener's own.
        setting = "br"
        charts.opened()
        until { charts.lists["|34"]?.country == "br" }

        assertEquals(2, asked("getTopChart").count { it.url.queryParameter("country") == null })
        scope.cancel()
    }

    @Test
    fun aRefusedCountrySaysWhyAndIsAskedAgainWhenPickedAgain() = runBlocking {
        serveV3()
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val charts = charts(v3, scope)
        charts.loadChoices()
        until { charts.own != null }
        server.answerBy("getTopChart") { server.failed(0, "Too many countries in an hour. Try again later.") }

        charts.pickCountry("jp")
        charts.load("34", "jp")
        until { charts.failures[charts.key("34", "jp")] != null }
        assertEquals("Too many countries in an hour. Try again later.", charts.failures[charts.key("34", "jp")])

        serveV3()
        charts.pickCountry("jp")
        until { charts.lists[charts.key("34", "jp")] != null }
        assertNull(charts.failures[charts.key("34", "jp")])
        scope.cancel()
    }

    @Test
    fun aServerOnTheSecondVersionIsAskedForNoCountry() = runBlocking {
        server.answer("getCharts", choices, type = "octo")
        server.answer("getOpenSubsonicExtensions", """"openSubsonicExtensions":[{"name":"octoTopSongs","versions":[1,2]}]""", type = "octo")
        server.answer("getTopChart", chart("34", "Popular right now"), type = "octo")
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val charts = charts(listOf("octoTopSongs:1", "octoTopSongs:2"), scope)

        charts.opened()
        charts.loadChoices()
        charts.load("34")
        until { charts.choices != null && charts.lists["|34"] != null }
        delay(200)

        assertFalse(charts.canPickCountry)
        assertFalse(charts.countriesOffered)
        assertEquals(1, asked("getCharts").size)
        assertTrue(server.calls.none { it.url.queryParameter("country") != null })
        scope.cancel()
    }
}
