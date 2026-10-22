package app.winters.octo.ui.charts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.winters.octo.discovery.ChartList
import app.winters.octo.discovery.CHART_SAVE_FAILED
import app.winters.octo.discovery.ChartLists
import app.winters.octo.playback.PlaybackConnection
import app.winters.octo.subsonic.CHART_COUNTRY_AUTO
import app.winters.octo.subsonic.CHART_OVERALL
import app.winters.octo.subsonic.ChartChoice
import app.winters.octo.subsonic.ChartChoices
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// The Charts page: the charts a country has, as chips, and the chosen
// chart's songs. The country is the listener's own until another is
// picked; on a server that saves it, any country Apple Music has can be
// looked at, made the listener's own, or pinned so its charts come as
// playlists too.
@HiltViewModel
class ChartsViewModel @Inject constructor(
    private val charts: ChartLists,
    // Taken only to play, so the page works without the player connected.
    private val playback: Provider<PlaybackConnection>,
) : ViewModel() {
    var choices by mutableStateOf<List<ChartChoice>>(emptyList())
        private set

    var selected by mutableStateOf(CHART_OVERALL)
        private set

    // The chosen chart's songs, or null while they are on their way.
    var list by mutableStateOf<ChartList?>(null)
        private set

    // Whether the server answered with nothing it can show.
    var none by mutableStateOf(false)
        private set

    // What the server says of the listener's own charts: their country,
    // pins and every country there is. Null on a server that shows only its
    // own country.
    var own by mutableStateOf<ChartChoices?>(null)
        private set

    // The country in view when it is not the listener's own.
    var browsing by mutableStateOf<String?>(null)
        private set

    var saving by mutableStateOf(false)
        private set

    // Why the last save did not happen, until the next one.
    var problem by mutableStateOf<String?>(null)
        private set

    private var loading: Job? = null
    private var choosing: Job? = null

    // Whether the page offers other countries.
    val canPickCountry: Boolean get() = own?.countries?.isNotEmpty() == true

    // The country the charts are for: the one looked at, else the listener's own.
    val country: String? get() = browsing ?: own?.country

    val pinned: List<String> get() = own?.pinned.orEmpty()

    val maxPinned: Int get() = own?.maxPinned ?: 0

    // Whether the country looked at is pinned, and whether it may be: one
    // already pinned can always be unpinned.
    val isPinned: Boolean get() = browsing != null && browsing in pinned
    val canPin: Boolean get() = isPinned || pinned.size < maxPinned

    init {
        // The listener's own choices may have changed on another device, so
        // a server that saves them is asked again each time the page opens;
        // what was kept shows until it answers.
        viewModelScope.launch {
            val countries = charts.countriesOffered()
            charts.keptChoices()?.let { showOwn(it, countries) }
            charts.choices(fresh = countries)?.let { showOwn(it, countries) }
            if (choices.isEmpty() && !charts.offered()) none = true
        }
        load()
    }

    private fun showOwn(got: ChartChoices, countries: Boolean) {
        if (countries) own = got
        if (browsing == null) {
            choices = got.chart
            keepSelected()
        }
    }

    fun pick(chart: String) {
        if (chart == selected) return
        selected = chart
        load()
    }

    // Looks at a country's charts; the listener's own country stops looking
    // at another.
    fun pickCountry(code: String) {
        val target = code.takeIf { it != own?.country }
        if (target == browsing) return
        browsing = target
        problem = null
        loading?.cancel()
        list = null
        choosing?.cancel()
        choosing = viewModelScope.launch {
            val got = if (target == null) charts.keptChoices() ?: charts.choices() else charts.choices(target)
            if (target != browsing) return@launch
            choices = got?.chart.orEmpty()
            keepSelected()
            load()
        }
    }

    // Makes the country looked at the listener's own.
    fun useAsMine() {
        val code = browsing ?: return
        save({ charts.saveCountry(code) }) { got -> backToOwn(got) }
    }

    // Follows the server's country again: saved when the listener had
    // chosen one, else only back to their own.
    fun followServer() {
        val mine = own ?: return
        if (mine.setting.equals(CHART_COUNTRY_AUTO, ignoreCase = true)) {
            mine.country?.let(::pickCountry)
            return
        }
        save({ charts.saveCountry(CHART_COUNTRY_AUTO) }) { got -> backToOwn(got) }
    }

    private fun backToOwn(got: ChartChoices) {
        choosing?.cancel()
        browsing = null
        choices = got.chart
        keepSelected()
        load()
    }

    // Pins the country looked at, or unpins it.
    fun togglePin() {
        val code = browsing ?: return
        val pin = !isPinned
        if (pin && !canPin) return
        save({ charts.pin(code, pin) }) {
            // Its charts now name the playlists the pin made.
            viewModelScope.launch {
                val got = charts.choices(code) ?: return@launch
                if (browsing == code) choices = got.chart
            }
        }
    }

    private fun save(call: suspend () -> ChartChoices?, then: (ChartChoices) -> Unit) {
        if (saving) return
        saving = true
        problem = null
        viewModelScope.launch {
            val got = call()
            saving = false
            if (got == null) {
                problem = CHART_SAVE_FAILED
                return@launch
            }
            own = got
            then(got)
        }
    }

    // The chart in view stays when the new charts have it.
    private fun keepSelected() {
        if (choices.isNotEmpty() && choices.none { it.id == selected }) selected = CHART_OVERALL
    }

    private fun load() {
        loading?.cancel()
        list = null
        val chart = selected
        val country = browsing
        loading = viewModelScope.launch {
            val got = charts.chart(chart, country)
            if (chart != selected || country != browsing) return@launch
            list = got ?: ChartList(chart, null, "", country, emptyList())
        }
    }

    fun play(index: Int) {
        val songs = list?.songs ?: return
        playback.get().playTracks(songs.map { it.track.id }, index.coerceAtLeast(0))
    }
}
