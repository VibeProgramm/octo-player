package app.winters.octo.discovery

import app.winters.octo.catalog.TrackEntity
import app.winters.octo.data.Session
import app.winters.octo.data.SessionRepository
import app.winters.octo.data.SessionState
import app.winters.octo.data.runsOcto
import app.winters.octo.subsonic.ChartChoices
import app.winters.octo.subsonic.OCTO_TOP_SONGS
import app.winters.octo.subsonic.OCTO_TOP_SONGS_CHARTS
import app.winters.octo.subsonic.OCTO_TOP_SONGS_COUNTRIES
import app.winters.octo.subsonic.Song
import app.winters.octo.subsonic.SubsonicClient
import app.winters.octo.subsonic.SubsonicException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

// How long a chart is kept before it is asked for again, and the list of
// charts a country has.
private const val CHART_KEPT_MS = 60 * 60 * 1000L
private const val CHOICES_KEPT_MS = 24 * 60 * 60 * 1000L

// A chart as the app shows it: which chart, its name, who ranked it, the
// country it is for, and its songs.
data class ChartList(
    val chart: String,
    val name: String?,
    val source: String,
    val country: String?,
    val songs: List<RankedTrack>,
)

// What asking for a chart came to: its list, no charts on the server, or
// no answer (the server could not be reached, or another is in use now).
sealed interface ChartAnswer {
    data class Got(val list: ChartList) : ChartAnswer
    data object None : ChartAnswer
    data object Failed : ChartAnswer
}

// The Charts page's server side: which charts a country has (Popular right
// now, Best New Songs, Trending Songs, the genres) and each chart's songs,
// kept per server, country and chart. A country of null is the listener's
// own. Only a server that lists octoTopSongs version 2 has charts, and only
// one that lists version 3 shows other countries and saves the listener's
// own country and pins; one signed in to before it did is asked once, live.
@Singleton
class ChartLists internal constructor(
    private val signedIn: () -> Session?,
    private val resolve: suspend (List<Song>) -> List<TrackEntity?>,
) {
    @Inject constructor(sessions: SessionRepository, discovery: Discovery) : this(
        { (sessions.state.value as? SessionState.SignedIn)?.session },
        { songs -> discovery.resolveInPlace(songs) },
    )

    private val offered = ConcurrentHashMap<String, Boolean>()
    private val countriesOffered = ConcurrentHashMap<String, Boolean>()
    private val lists = ConcurrentHashMap<String, Pair<Long, ChartList>>()
    private val choices = ConcurrentHashMap<String, Pair<Long, ChartChoices>>()

    // Whether the server in use has charts.
    suspend fun offered(): Boolean {
        val session = session() ?: return false
        return offers(session)
    }

    // Whether the server in use shows other countries' charts and saves the
    // listener's own country and pins.
    suspend fun countriesOffered(): Boolean {
        val session = session() ?: return false
        return offersIfKnown(session, OCTO_TOP_SONGS_COUNTRIES, countriesOffered) ?: false
    }

    // The charts a country has, kept a day per server and country. `fresh`
    // asks the server again even so, for the listener's own choices, which
    // another device or the dashboard may have changed; the kept copy is the
    // answer when the server gives none.
    suspend fun choices(country: String? = null, fresh: Boolean = false): ChartChoices? {
        val session = session() ?: return null
        val key = choicesKey(session, country)
        val now = System.currentTimeMillis()
        val kept = choices[key]?.takeIf { (at, _) -> now - at < CHOICES_KEPT_MS }?.second
        if (kept != null && !fresh) return kept
        if (!offers(session)) return null
        val got = answerOf { session.client.charts(country) } ?: return kept
        if (session()?.id != session.id) return null
        choices[key] = now to got
        return got
    }

    // The charts a country has as kept, without asking: what the page shows
    // while the server is asked again.
    fun keptChoices(country: String? = null): ChartChoices? {
        val session = session() ?: return null
        return choices[choicesKey(session, country)]?.second
    }

    // A chart's songs, kept an hour per server, country and chart: its songs
    // are matched to that server's library. Null when there is none to give.
    suspend fun chart(chart: String, country: String? = null): ChartList? = (answer(chart, country) as? ChartAnswer.Got)?.list

    // The same, telling a server without charts from one that did not answer.
    suspend fun answer(chart: String, country: String? = null): ChartAnswer {
        val session = session() ?: return ChartAnswer.Failed
        val key = "${serverKey(session)}|${country.orEmpty()}|$chart"
        val now = System.currentTimeMillis()
        lists[key]?.let { (at, kept) -> if (now - at < CHART_KEPT_MS) return ChartAnswer.Got(kept) }
        when (offersIfKnown(session)) {
            null -> return ChartAnswer.Failed
            false -> return ChartAnswer.None
            true -> Unit
        }
        val list = answerOf { session.client.topChart(chartSongs(chart), chart, country) } ?: return ChartAnswer.Failed
        // Another server in use by the time it came: not this one's chart.
        if (session()?.id != session.id) return ChartAnswer.Failed
        val entries = list.entry.filter { it.song != null }
        val songs = rankTracks(entries, resolve(entries.map { it.song!! }))
        return ChartAnswer.Got(ChartList(chart, list.name, list.source, list.country, songs).also { lists[key] = now to it })
    }

    // Saves the listener's own chart country (CHART_COUNTRY_AUTO follows the
    // server's) and answers their charts; null when it was not saved.
    suspend fun saveCountry(code: String): ChartChoices? = saved { client -> client.setChartCountry(code) }

    // Pins a country or unpins it, and answers the listener's charts; null
    // when it was not saved.
    suspend fun pin(code: String, pinned: Boolean): ChartChoices? = saved { client -> client.pinChartCountry(code, pinned) }

    // A save changes the listener's own lists, so every chart and list of
    // charts kept for the server is let go, and the answer is kept as the
    // listener's own.
    private suspend fun saved(call: suspend (SubsonicClient) -> ChartChoices): ChartChoices? {
        val session = session() ?: return null
        val got = answerOf { call(session.client) } ?: return null
        if (session()?.id != session.id) return null
        val server = "${serverKey(session)}|"
        lists.keys.removeAll { it.startsWith(server) }
        choices.keys.removeAll { it.startsWith(server) }
        choices[choicesKey(session, null)] = System.currentTimeMillis() to got
        return got
    }

    private suspend fun offers(session: Session): Boolean = offersIfKnown(session) ?: false

    // Whether the server lists octoTopSongs at `version`, kept in `known`
    // once asked; null when it could not say.
    private suspend fun offersIfKnown(
        session: Session,
        version: Int = OCTO_TOP_SONGS_CHARTS,
        known: ConcurrentHashMap<String, Boolean> = offered,
    ): Boolean? {
        if ("$OCTO_TOP_SONGS:$version" in session.extensions) return true
        if (!session.runsOcto) return false
        known[session.sourceId]?.let { return it }
        val yes = session.client.supportsIfKnown(OCTO_TOP_SONGS, version) ?: return null
        known[session.sourceId] = yes
        return yes
    }

    private fun serverKey(session: Session): String = session.id.ifEmpty { session.sourceId }

    private fun choicesKey(session: Session, country: String?): String = "${serverKey(session)}|${country.orEmpty()}"

    private fun session(): Session? = signedIn()

    private suspend fun <T> answerOf(call: suspend () -> T): T? =
        try {
            call()
        } catch (e: SubsonicException) {
            null
        }
}
