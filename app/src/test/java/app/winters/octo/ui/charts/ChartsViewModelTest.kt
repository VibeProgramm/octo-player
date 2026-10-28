package app.winters.octo.ui.charts

import app.winters.octo.data.Session
import app.winters.octo.discovery.ChartLists
import app.winters.octo.discovery.CountryAction
import app.winters.octo.subsonic.Credentials
import app.winters.octo.subsonic.SubsonicClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import javax.inject.Provider

// The Charts page against a pretend Octo server: the listener's own charts
// asked again each time the page opens, another country looked at, made
// the listener's own or pinned, and a server that shows no other country.
class ChartsViewModelTest {
    private companion object {
        const val TOO_MANY = "Still loading your other countries. Try again in a moment."
    }

    private val server = MockWebServer()
    private val asked = CopyOnWriteArrayList<RecordedRequest>()
    private lateinit var main: ExecutorCoroutineDispatcher

    // What the pretend server keeps for the listener.
    @Volatile private var setting = "auto"
    @Volatile private var pinned = listOf<String>()
    @Volatile private var versions = "[1,2,3]"
    @Volatile private var refuse = false
    @Volatile private var refuseCode = 0
    @Volatile private var refuseReads = false

    // While set, the listener's own chart is answered only once it opens,
    // with what the server said when the read came in.
    @Volatile private var holdOwnChart: CountDownLatch? = null

    // While set, the listener's own charts are answered only once it opens,
    // with what the server says then.
    @Volatile private var holdOwnChoices: CountDownLatch? = null

    private val countryCharts = mapOf(
        "us" to listOf("34" to "Top songs", "18" to "Hip-Hop/Rap", "14" to "Pop"),
        "jp" to listOf("34" to "Top songs", "14" to "Pop", "27" to "J-Pop"),
        "br" to listOf("34" to "Top songs", "14" to "Pop"),
        "kr" to listOf("34" to "Top songs", "51" to "K-Pop"),
    )

    private fun charts(country: String): String {
        val chips = countryCharts.getValue(country).joinToString(",") { (id, label) ->
            """{"id":"$id","name":"Top $label","label":"$label","kind":"${if (id == "34") "overall" else "genre"}","on":true}"""
        }
        val own = if (versions.contains("3")) {
            val pins = pinned.joinToString(",") { "\"$it\"" }
            ""","setting":"$setting","pinned":[$pins],"countries":["br","jp","kr","us"],"maxPinned":3,"serverCountry":"us""""
        } else {
            ""
        }
        return """"charts":{"country":"$country","chart":[$chips]$own}"""
    }

    private fun ownCountry() = if (setting == "auto") "us" else setting

    private fun ok(inner: String) = MockResponse.Builder()
        .body("""{"subsonic-response":{"status":"ok","version":"1.16.1","type":"octo","openSubsonic":true,$inner}}""")
        .build()

    private fun failed(code: Int = refuseCode) = MockResponse.Builder()
        .body("""{"subsonic-response":{"status":"failed","version":"1.16.1","error":{"code":$code,"message":"${if (code == 0) TOO_MANY else "Something broke."}"}}}""")
        .build()

    @Before
    fun start() {
        main = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        Dispatchers.setMain(main)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                asked += request
                val country = request.url.queryParameter("country")
                return when (request.url.pathSegments.last()) {
                    "getOpenSubsonicExtensions" -> ok(""""openSubsonicExtensions":[{"name":"octoTopSongs","versions":$versions}]""")
                    "getCharts" -> if (refuseReads && country != null) failed(0) else {
                        if (country == null) holdOwnChoices?.await(5, TimeUnit.SECONDS)
                        ok(charts(country ?: ownCountry()))
                    }
                    "getTopChart" -> if (refuseReads && country != null) failed(0) else {
                        val answer = ok(
                            """"topSongs":{"artist":null,"source":"apple","chart":"${request.url.queryParameter("chart")}","country":"${country ?: ownCountry()}","entry":[]}""",
                        )
                        if (country == null) holdOwnChart?.await(5, TimeUnit.SECONDS)
                        answer
                    }
                    "setChartCountry" -> if (refuse) failed() else {
                        setting = country!!
                        pinned = pinned - ownCountry()
                        ok(charts(ownCountry()))
                    }
                    "pinChartCountry" -> if (refuse) failed() else {
                        pinned = if (request.url.queryParameter("pinned") == "true") pinned + country!! else pinned - country!!
                        ok(charts(ownCountry()))
                    }
                    else -> ok(""""x":1""")
                }
            }
        }
        server.start()
    }

    @After
    fun stop() {
        server.close()
        Dispatchers.resetMain()
        main.close()
    }

    private fun session(extensions: Set<String> = setOf("octoTopSongs:1", "octoTopSongs:2", "octoTopSongs:3")) = Session(
        client = SubsonicClient(server.url("/"), Credentials("anna", "secret"), OkHttpClient()),
        serverType = "octo",
        serverVersion = "2026.10.22",
        isOcto = true,
        adminReachable = false,
        extensions = extensions,
        id = "home",
    )

    private fun lists(session: Session = session()) = ChartLists({ session }, { songs -> songs.map { null } })

    private fun page(lists: ChartLists) = ChartsViewModel(lists, Provider { error("Nothing plays here") })

    private suspend fun until(check: () -> Boolean) = withTimeout(5_000) { while (!check()) delay(10) }

    private fun calls(endpoint: String) = asked.filter { it.url.pathSegments.last() == endpoint }

    @Test
    fun theListenersOwnChartsAreAskedEachTimeThePageOpens() = runBlocking {
        val lists = lists()
        val first = page(lists)
        until { first.own != null && first.list != null }
        val second = page(lists)
        until { second.own != null && calls("getCharts").size == 2 }

        assertTrue(calls("getCharts").all { it.url.queryParameter("country") == null })
        assertEquals("us", second.country)
        assertTrue(second.canPickCountry)
        assertNull(second.browsing)
    }

    @Test
    fun lookingAtACountryAsksForItsChartsAndKeepsTheChartWhenItHasIt() = runBlocking {
        val vm = page(lists())
        until { vm.own != null && vm.list != null }
        vm.pick("14")
        until { vm.list?.chart == "14" }

        vm.pickCountry("jp")
        until { vm.list?.country == "jp" }

        assertEquals("jp", vm.browsing)
        assertEquals("14", vm.selected)
        assertEquals(listOf("34", "14", "27"), vm.choices.map { it.id })
        assertEquals("jp", calls("getCharts").last().url.queryParameter("country"))
        val chart = calls("getTopChart").last().url
        assertEquals("jp", chart.queryParameter("country"))
        assertEquals("14", chart.queryParameter("chart"))

        // A chart the country lacks goes back to Popular right now.
        vm.pickCountry("us")
        until { vm.browsing == null && vm.list != null }
        vm.pick("18")
        until { vm.list?.chart == "18" }
        vm.pickCountry("kr")
        until { vm.list?.country == "kr" }
        assertEquals("34", vm.selected)
    }

    // The listener's own choices landing while their chart is on its way
    // leave it to land: it is not asked for twice.
    @Test
    fun theOwnChartOnItsWayIsNotAskedAgainWhenTheChoicesLand() = runBlocking {
        val hold = CountDownLatch(1)
        holdOwnChart = hold
        val vm = page(lists())
        until { vm.own != null && calls("getTopChart").isNotEmpty() }
        hold.countDown()
        until { vm.list != null }
        assertEquals("us", vm.list?.country)
        assertEquals(1, calls("getTopChart").size)
    }

    // A chart read for the old country, when the listener's own changed
    // while it was on its way, is asked for once more and shows the new one.
    @Test
    fun anOwnChartForTheOldCountryIsAskedAgain() = runBlocking {
        val hold = CountDownLatch(1)
        val choicesHeld = CountDownLatch(1)
        holdOwnChart = hold
        holdOwnChoices = choicesHeld
        val vm = page(lists())
        until { calls("getTopChart").isNotEmpty() && calls("getCharts").isNotEmpty() }
        // Changed on another device: the fresh choices say Japan, while the
        // chart already asked for answers for the United States.
        setting = "jp"
        holdOwnChoices = null
        choicesHeld.countDown()
        until { vm.own != null }
        holdOwnChart = null
        hold.countDown()
        until { vm.list != null }
        assertEquals("jp", vm.list?.country)
        assertEquals(2, calls("getTopChart").size)
    }

    @Test
    fun theOwnCountryStopsLookingAtAnother() = runBlocking {
        val vm = page(lists())
        until { vm.own != null && vm.list != null }
        vm.pickCountry("jp")
        until { vm.list?.country == "jp" }

        vm.pickCountry("us")
        until { vm.browsing == null && vm.list != null }

        // The listener's own chart, as kept from when the page opened.
        assertEquals("us", vm.list?.country)
        assertEquals(1, calls("getTopChart").count { it.url.queryParameter("country") == null })
    }

    @Test
    fun justLookingSavesNothing() = runBlocking {
        val vm = page(lists())
        until { vm.own != null && vm.list != null }

        vm.choose(CountryAsked("jp"), CountryAction.Look)
        until { vm.list?.country == "jp" }

        assertEquals("jp", vm.browsing)
        assertTrue(calls("setChartCountry").isEmpty())
        assertTrue(calls("pinChartCountry").isEmpty())
    }

    @Test
    fun aCountryLookedAtIsForgottenWithThePage() = runBlocking {
        val lists = lists()
        val vm = page(lists)
        until { vm.own != null && vm.list != null }
        vm.choose(CountryAsked("jp"), CountryAction.Look)
        until { vm.list?.country == "jp" }

        // The page opened again starts on the listener's own country.
        val again = page(lists)
        until { again.own != null && again.list != null }
        assertNull(again.browsing)
        assertEquals("us", again.country)
    }

    @Test
    fun usingACountryForMyChartsSavesItAndShowsItAsYours() = runBlocking {
        val vm = page(lists())
        until { vm.own != null && vm.list != null }

        vm.choose(CountryAsked("jp"), CountryAction.UseForMine)
        until { vm.browsing == null && vm.own?.setting == "jp" && vm.list?.country == "jp" }

        assertEquals("jp", calls("setChartCountry").single().url.queryParameter("country"))
        assertTrue(calls("pinChartCountry").isEmpty())
        assertEquals("jp", vm.country)
        // The listener's own chart is asked again, for their new country.
        assertNull(calls("getTopChart").last().url.queryParameter("country"))
    }

    @Test
    fun theServersCountryAgainSavesAuto() = runBlocking {
        setting = "br"
        val vm = page(lists())
        until { vm.own?.setting == "br" }

        vm.choose(CountryAsked("us", server = true), CountryAction.FollowServer)
        until { vm.own?.setting == "auto" }

        assertEquals("auto", calls("setChartCountry").single().url.queryParameter("country"))
        assertEquals("us", vm.country)
        assertNull(vm.browsing)
    }

    @Test
    fun addingACountrysPlaylistsPinsItAndLooksAtIt() = runBlocking {
        val vm = page(lists())
        until { vm.own != null && vm.list != null }

        vm.choose(CountryAsked("jp"), CountryAction.AddPlaylists)
        until { vm.pinned == listOf("jp") && vm.list?.country == "jp" }

        val pin = calls("pinChartCountry").single().url
        assertEquals("jp", pin.queryParameter("country"))
        assertEquals("true", pin.queryParameter("pinned"))
        assertTrue(calls("setChartCountry").isEmpty())
        assertEquals("jp", vm.browsing)
    }

    @Test
    fun removingACountrysPlaylistsUnpinsIt() = runBlocking {
        pinned = listOf("br", "kr", "de")
        val vm = page(lists())
        until { vm.own != null && vm.list != null }

        vm.choose(CountryAsked("kr"), CountryAction.RemovePlaylists)
        until { vm.pinned == listOf("br", "de") }

        val unpin = calls("pinChartCountry").single().url
        assertEquals("kr", unpin.queryParameter("country"))
        assertEquals("false", unpin.queryParameter("pinned"))
    }

    @Test
    fun aSaveThatDidNotHappenSaysSoForThatCountry() = runBlocking {
        val vm = page(lists())
        until { vm.own != null && vm.list != null }
        refuse = true

        vm.choose(CountryAsked("jp"), CountryAction.UseForMine)
        until { vm.problem != null }

        // The server's own words when it refused for a reason the listener can act on.
        assertEquals(TOO_MANY, vm.problem)
        assertEquals(CountryAsked("jp"), vm.problemFor)
        assertNull(vm.browsing)
        assertEquals("auto", vm.own?.setting)
        assertFalse(vm.saving)

        vm.problemSeen()
        assertNull(vm.problem)
        assertNull(vm.problemFor)
    }

    @Test
    fun anyOtherFailedSaveSaysThePlainLine() = runBlocking {
        val vm = page(lists())
        until { vm.own != null && vm.list != null }
        refuse = true
        refuseCode = 70

        vm.choose(CountryAsked("jp"), CountryAction.AddPlaylists)
        until { vm.problem != null }

        assertEquals("Couldn't save. Try again.", vm.problem)
    }

    @Test
    fun backToYoursLeavesTheCountryLookedAt() = runBlocking {
        val vm = page(lists())
        until { vm.own != null && vm.list != null }
        vm.choose(CountryAsked("jp"), CountryAction.Look)
        until { vm.list?.country == "jp" }

        vm.backToYours()
        until { vm.browsing == null && vm.list?.country == "us" }
        assertTrue(calls("setChartCountry").isEmpty())
    }

    @Test
    fun aCountryChangedElsewhereAsksForTheOwnChartAgain() = runBlocking {
        val lists = lists()
        val first = page(lists)
        until { first.own != null && first.list?.country == "us" }

        // Another device made Brazil the listener's own.
        setting = "br"
        val second = page(lists)
        until { second.own?.setting == "br" && second.list?.country == "br" }

        // The chart kept for the old country was not the answer: it was asked again
        // (a read already on its way when the new country came may be asked twice).
        assertTrue(calls("getTopChart").count { it.url.queryParameter("country") == null } >= 2)
        assertEquals("br", second.list?.country)
    }

    @Test
    fun aRefusedCountrySaysWhyAndCanBeAskedAgain() = runBlocking {
        val vm = page(lists())
        until { vm.own != null && vm.list != null }
        refuseReads = true

        vm.pickCountry("jp")
        until { vm.refusal != null }
        assertEquals(TOO_MANY, vm.refusal)
        assertTrue(vm.choices.isEmpty())

        // The same country picked again asks again.
        refuseReads = false
        vm.pickCountry("jp")
        until { vm.list?.country == "jp" && vm.choices.isNotEmpty() }
        assertNull(vm.refusal)
    }

    @Test
    fun aCountryLookedAtThatBecameTheOwnIsShownAsTheOwn() = runBlocking {
        val lists = lists()
        val vm = page(lists)
        until { vm.own != null && vm.list != null }
        vm.pickCountry("jp")
        until { vm.list?.country == "jp" }

        // Another device made Japan the listener's own; adding its playlists here answers with that.
        setting = "jp"
        vm.choose(CountryAsked("jp"), CountryAction.AddPlaylists)
        until { vm.browsing == null && vm.own?.setting == "jp" }
        assertEquals("jp", vm.country)
    }

    @Test
    fun aServerWithoutCountriesOffersNoneAndIsAskedForNone() = runBlocking {
        versions = "[1,2]"
        val vm = page(lists(session(setOf("octoTopSongs:1", "octoTopSongs:2"))))
        until { vm.choices.isNotEmpty() && vm.list != null }
        delay(100)

        assertFalse(vm.canPickCountry)
        assertNull(vm.own)
        assertTrue(asked.none { it.url.queryParameter("country") != null })
    }
}
