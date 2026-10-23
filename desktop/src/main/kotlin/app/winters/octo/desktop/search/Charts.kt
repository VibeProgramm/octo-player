package app.winters.octo.desktop.search

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.winters.octo.desktop.server.Connection
import app.winters.octo.discovery.CHART_SAVE_FAILED
import app.winters.octo.discovery.chartRefusal
import app.winters.octo.discovery.chartSongs
import app.winters.octo.subsonic.CHART_COUNTRY_AUTO
import app.winters.octo.subsonic.CHART_OVERALL
import app.winters.octo.subsonic.ChartChoices
import app.winters.octo.subsonic.OCTO_TOP_SONGS
import app.winters.octo.subsonic.OCTO_TOP_SONGS_CHARTS
import app.winters.octo.subsonic.OCTO_TOP_SONGS_COUNTRIES
import app.winters.octo.subsonic.SubsonicClient
import app.winters.octo.subsonic.SubsonicException
import app.winters.octo.subsonic.TopSongs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// How long a chart is kept before it is asked for again, and the list of
// charts a country has.
private const val CHART_KEPT_MS = 60 * 60 * 1000L
private const val CHOICES_KEPT_MS = 24 * 60 * 60 * 1000L

// What a chart that did not come says, when the server gave no words of
// its own for it.
const val CHART_NOT_READ = "Octo didn't answer. Try again in a moment."

// The Charts page's server side: which charts a country has (Popular right
// now, Best New Songs, Trending Songs and the genres) and each chart's
// songs, each kept an hour, for the listener's own country or another one
// looked at. Only a server that lists octoTopSongs version 2 has charts,
// and only one that lists version 3 shows other countries and saves the
// listener's own country and pins. One signed in to before it did is asked
// once, live, since the extensions saved at sign-in may be older than the
// server.
@Stable
class Charts(private val connection: Connection, private val scope: CoroutineScope) {
    // Whether this server has charts, once it has said.
    var offered by mutableStateOf<Boolean?>(null)
        private set

    // Whether this server shows other countries and saves the listener's
    // own, once it has said.
    var countriesOffered by mutableStateOf(false)
        private set

    // The country the page shows when it is not the listener's own.
    var country by mutableStateOf<String?>(null)
        private set

    // The chart the page shows.
    var selected by mutableStateOf(CHART_OVERALL)

    // The charts each country has, by country, "" for the listener's own.
    private val choicesBy = mutableStateMapOf<String, ChartChoices>()

    // Each chart asked for, by `key`; an empty list when it had nothing.
    val lists = mutableStateMapOf<String, TopSongs>()

    // Why a chart (by `key`) or a country's charts (by country) did not
    // come: the server's words when it refused, until it is asked again.
    val failures = mutableStateMapOf<String, String>()
    val choiceFailures = mutableStateMapOf<String, String>()

    var saving by mutableStateOf(false)
        private set

    // Why the last save did not happen, until the next one.
    var problem by mutableStateOf<String?>(null)
        private set

    private val listsAt = mutableMapOf<String, Long>()
    private val jobs = mutableMapOf<String, Job>()
    private val choicesAt = mutableMapOf<String, Long>()
    private val choicesJobs = mutableMapOf<String, Job>()
    private var countriesKnown: Boolean? = null

    // The charts of the country in view.
    val choices: ChartChoices? get() = choicesBy[country.orEmpty()]

    // What the server says of the listener's own charts: their country,
    // pins and every country there is.
    val own: ChartChoices? get() = choicesBy[""]

    // Whether the page offers other countries: the server saves them and
    // has said which there are.
    val canPickCountry: Boolean get() = countriesOffered && own?.countries?.isNotEmpty() == true

    // The country the charts are for: the one looked at, else the listener's own.
    val shownCountry: String? get() = country ?: own?.country

    val pinned: List<String> get() = own?.pinned.orEmpty()

    val maxPinned: Int get() = own?.maxPinned ?: 0

    // Whether the country looked at is pinned, and whether it may be: one
    // already pinned can always be unpinned.
    val isPinned: Boolean get() = country != null && country in pinned
    val canPin: Boolean get() = isPinned || pinned.size < maxPinned

    // A chart's place in `lists`: its country ("" for the listener's own)
    // and its id.
    fun key(chart: String, country: String? = null): String = "${country.orEmpty()}|$chart"

    private suspend fun offered(): Boolean {
        offered?.let { return it }
        val yes = when {
            connection.supports(OCTO_TOP_SONGS, OCTO_TOP_SONGS_CHARTS) -> true
            !connection.isOcto -> false
            else -> connection.client.supportsIfKnown(OCTO_TOP_SONGS, OCTO_TOP_SONGS_CHARTS) ?: return false
        }
        offered = yes
        return yes
    }

    private suspend fun countries(): Boolean {
        countriesKnown?.let { return it }
        val yes = when {
            connection.supports(OCTO_TOP_SONGS, OCTO_TOP_SONGS_COUNTRIES) -> true
            !connection.isOcto -> false
            else -> connection.client.supportsIfKnown(OCTO_TOP_SONGS, OCTO_TOP_SONGS_COUNTRIES) ?: return false
        }
        countriesKnown = yes
        countriesOffered = yes
        return yes
    }

    // Asks whether this server has charts, for the sidebar and Home.
    fun check() {
        if (offered != null) return
        scope.launch { offered() }
    }

    // The page opened. The listener's own choices may have changed on
    // another device or the dashboard, so a server that saves them is asked
    // for them again; what was kept shows until it answers.
    fun opened() {
        scope.launch {
            if (offered() && countries()) loadChoices(null, fresh = true)
        }
    }

    // The charts a country has, the listener's own for null, kept a day;
    // `fresh` asks again even so.
    fun loadChoices(country: String? = this.country, fresh: Boolean = false) {
        val at = country.orEmpty()
        if (choicesJobs[at]?.isActive == true) return
        val since = choicesAt[at]
        if (!fresh && choicesBy[at] != null && since != null && System.currentTimeMillis() - since < CHOICES_KEPT_MS) return
        choicesJobs[at] = scope.launch {
            if (!offered()) return@launch
            if (country != null && !countries()) return@launch
            choiceFailures.remove(at)
            val got = try {
                connection.client.charts(country)
            } catch (e: SubsonicException) {
                if (country != null) choiceFailures[at] = chartRefusal(e) ?: CHART_NOT_READ
                return@launch
            }
            if (country == null) ownAnswered(got)
            choicesBy[at] = got
            choicesAt[at] = System.currentTimeMillis()
            // The chart in view stays when the country has it.
            if (country == this@Charts.country) keepSelected()
        }
    }

    // The listener's own charts as the server says them now. When their
    // country changed elsewhere (another device, the dashboard), the charts
    // kept for the old one are asked for again; a country looked at that
    // became their own is shown as their own.
    private fun ownAnswered(got: ChartChoices) {
        val now = got.country ?: return
        val before = choicesBy[""]?.country
        val ownKeys = lists.keys.filter { it.startsWith("|") }
        if ((before != null && !before.equals(now, ignoreCase = true)) || ownKeys.any { lists[it]?.country?.equals(now, ignoreCase = true) == false }) {
            ownKeys.forEach { listsAt.remove(it) }
            if (country == null) load(selected)
        }
        if (country.equals(now, ignoreCase = true)) country = null
    }

    // A chart's songs, kept an hour per country: the whole chart (50, or
    // 100 for Best New Songs and Trending Songs). Home asks for the
    // listener's own country, the page for the one it shows.
    fun load(chart: String, country: String? = null) {
        val key = key(chart, country)
        if (jobs[key]?.isActive == true) return
        val at = listsAt[key]
        if (at != null && System.currentTimeMillis() - at < CHART_KEPT_MS) return
        jobs[key] = scope.launch {
            if (!offered()) return@launch
            failures.remove(key)
            val got = try {
                connection.client.topChart(chartSongs(chart), chart, country)
            } catch (e: SubsonicException) {
                failures[key] = chartRefusal(e) ?: CHART_NOT_READ
                return@launch
            }
            lists[key] = got
            listsAt[key] = System.currentTimeMillis()
        }
    }

    // Shows the charts of a country; the listener's own stops looking at
    // another.
    // Picking the same country again asks again for what did not come.
    fun pickCountry(code: String) {
        val target = code.takeIf { it != own?.country }
        if (target == country) {
            if (target != null && choiceFailures.containsKey(target)) loadChoices(target)
            if (failures.containsKey(key(selected, target))) load(selected, target)
            return
        }
        country = target
        problem = null
        keepSelected()
    }

    // Back to the listener's own country, as Home's "See all" opens it.
    fun showOwn() {
        country = null
        keepSelected()
    }

    // Makes the country looked at the listener's own.
    fun useAsMine() {
        val code = country ?: return
        // Only back to their own when the country looked at is still the one saved.
        save({ it.setChartCountry(code) }) { if (country == code) country = null }
    }

    // Follows the server's country again: saved when the listener had
    // chosen one, else only back to their own.
    fun followServer() {
        val mine = own ?: return
        if (mine.setting.equals(CHART_COUNTRY_AUTO, ignoreCase = true)) {
            showOwn()
            return
        }
        save({ it.setChartCountry(CHART_COUNTRY_AUTO) }) { country = null }
    }

    // Pins the country looked at, or unpins it.
    fun togglePin() {
        val code = country ?: return
        val pin = !isPinned
        if (pin && !canPin) return
        save({ it.pinChartCountry(code, pin) }) {}
    }

    // A save changes the listener's own lists, so every chart and list of
    // charts kept is due again (still drawn until the new one comes) and
    // the answer is kept as the listener's own; the page's charts are then
    // asked for again. A refusal shows the server's words.
    private fun save(call: suspend (SubsonicClient) -> ChartChoices, then: () -> Unit) {
        if (saving) return
        saving = true
        problem = null
        scope.launch {
            val got = try {
                call(connection.client)
            } catch (e: SubsonicException) {
                saving = false
                problem = chartRefusal(e) ?: CHART_SAVE_FAILED
                return@launch
            }
            saving = false
            jobs.values.forEach { it.cancel() }
            jobs.clear()
            listsAt.clear()
            choicesJobs.values.forEach { it.cancel() }
            choicesJobs.clear()
            choicesAt.clear()
            choicesBy[""] = got
            choicesAt[""] = System.currentTimeMillis()
            then()
            loadChoices(country)
            keepSelected()
            load(selected, country)
        }
    }

    // The chart in view stays when the country in view has it.
    private fun keepSelected() {
        val chips = choices?.chart ?: return
        if (chips.isNotEmpty() && chips.none { it.id == selected }) selected = CHART_OVERALL
    }
}
