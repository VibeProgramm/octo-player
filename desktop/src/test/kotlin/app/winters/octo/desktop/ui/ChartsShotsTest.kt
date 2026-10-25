package app.winters.octo.desktop.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.roundToIntRect
import app.winters.octo.design.LocalTyping
import app.winters.octo.design.TypingState
import app.winters.octo.desktop.AppState
import app.winters.octo.desktop.FakeServer
import app.winters.octo.desktop.nav.Page
import app.winters.octo.desktop.pages.askCountry
import app.winters.octo.desktop.pages.countryName
import app.winters.octo.desktop.pages.showCountryMenu
import app.winters.octo.desktop.player.SilentPlayer
import app.winters.octo.desktop.secrets.SessionOnlySecrets
import app.winters.octo.desktop.server.Accounts
import app.winters.octo.desktop.server.SignInOutcome
import app.winters.octo.desktop.server.SignInRequest
import app.winters.octo.desktop.settings.DesktopOs
import app.winters.octo.desktop.settings.SettingsStore
import app.winters.octo.desktop.songJson
import app.winters.octo.lyrics.OnlineLyrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import javax.swing.SwingUtilities

// Pictures of the charts against a pretend Octo server: Home's Charts row,
// the Charts page on Popular right now, Best New Songs and a genre, Japan's
// charts looked at, the country menu, whole, scrolled and searched, and the
// choice a picked country asks. Only when asked:
// OCTO_SHOTS=1 ./gradlew :desktop:test --tests '*ChartsShotsTest*'.
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class ChartsShotsTest {
    @get:Rule val folder = TemporaryFolder()

    private fun song(id: String, title: String, artist: String, album: String, seconds: Int, explicit: Boolean = false, owned: Boolean = false) =
        if (owned) {
            songJson(id, title, artist = artist, album = album, albumId = "al-$id", duration = seconds).dropLast(1) + ""","coverArt":"al-$id","isExternal":false}"""
        } else {
            """{"id":"$id","title":"$title","artist":"$artist","album":"$album","duration":$seconds,"coverArt":"$id","isExternal":true,"suffix":"m4a"${if (explicit) ""","explicitStatus":"explicit"""" else ""}}"""
        }

    private val charts = mapOf(
        "34" to ("Popular right now" to listOf(
            "Solar Eclipse|Drake|HABIBTI (FOMO)|218|e", "Quebec|Drake|HABIBTI (FOMO)|130|eo", "Cold Shoulder|Drake|HABIBTI (FOMO)|235|",
            "Choosin' Texas|Ella Langley|Choosin' Texas - Single|232|", "BbY WOW|KAROL G|NO ME ARREPIENTO DE SENTIR TANTO|226|",
            "Patient Zero|Taylor Swift|The Life of a Showgirl: The Encore|226|", "Dracula|Tame Impala|Deadbeat|185|o",
            "Golden|HUNTR/X|KPop Demon Hunters|194|", "Ordinary|Alex Warren|You'll Be Alright, Kid|187|", "Manchild|Sabrina Carpenter|Man's Best Friend|213|e",
            "Abracadabra|Lady Gaga|Mayhem|223|", "APT.|ROSÉ|rosie|170|",
        )),
        "new" to ("Best New Songs" to listOf(
            "Solar Eclipse|Drake|HABIBTI (FOMO)|218|e", "Chelsea Boots (Bonus Track)|Paul McCartney|The Boys of Dungeon Lane|178|",
            "Uncertain, TX (Spanish Version)|Kacey Musgraves|Uncertain, TX - Single|213|", "See You In My Dreams (feat. SZA)|Victoria Monét|Frequency Of Love|158|",
            "Draw You Out|Noah Kahan|The Hunger Games: Sunrise on the Reaping|256|",
        )),
        "27" to ("Top J-Pop" to listOf(
            "Plazma|Kenshi Yonezu|Plazma - Single|181|", "Bling-Bang-Bang-Born|Creepy Nuts|Bling-Bang-Bang-Born - Single|169|",
            "Idol|YOASOBI|Idol - Single|213|", "Lemon|Kenshi Yonezu|Lemon - Single|255|o", "Overdose|natori|Overdose - Single|208|",
        )),
        "18" to ("Top Hip-Hop/Rap" to listOf(
            "Janice STFU|Drake|ICEMAN|237|e", "WAIT FOR U (feat. Drake & Tems)|Future|I NEVER LIKED YOU|190|e", "In A Minute|Lil Baby|It's Only Me|200|e",
            "Freestyle|Lil Baby|Too Hard|162|eo", "Dead Fresh|Lil Baby|Dead Fresh - Single|157|e",
        )),
    )

    private companion object {
        // A good part of the countries Apple Music has charts for, enough
        // for the menu to scroll, with the longest names among them.
        val COUNTRIES = listOf(
            "ae", "ar", "at", "au", "ba", "be", "bg", "br", "ca", "ch", "ci", "cl", "co", "cz", "de", "dk", "do", "eg", "es", "fi",
            "fr", "gb", "gr", "hk", "hu", "id", "ie", "il", "in", "it", "jp", "kn", "kr", "mx", "my", "nl", "no", "nz", "ph", "pl",
            "pt", "ro", "sa", "se", "sg", "tc", "th", "tr", "tw", "us", "vc", "vn", "za",
        )
        const val HOME = """
            {"id":"34","name":"Popular right now","label":"Top songs","kind":"overall","on":true},
            {"id":"new","name":"Best New Songs","label":"Best New Songs","kind":"new","on":true},
            {"id":"trending","name":"Trending Songs","label":"Trending Songs","kind":"trending","on":true},
            {"id":"20","name":"Top Alternative","label":"Alternative","kind":"genre","on":false},
            {"id":"6","name":"Top Country","label":"Country","kind":"genre","on":true},
            {"id":"18","name":"Top Hip-Hop/Rap","label":"Hip-Hop/Rap","kind":"genre","on":true},
            {"id":"51","name":"Top K-Pop","label":"K-Pop","kind":"genre","on":false},
            {"id":"14","name":"Top Pop","label":"Pop","kind":"genre","on":true},
            {"id":"15","name":"Top R&B/Soul","label":"R&B/Soul","kind":"genre","on":false},
            {"id":"21","name":"Top Rock","label":"Rock","kind":"genre","on":false},
            {"id":"10","name":"Top Singer/Songwriter","label":"Singer/Songwriter","kind":"genre","on":false}"""
        const val JAPAN = """
            {"id":"34","name":"Popular right now","label":"Top songs","kind":"overall","on":true},
            {"id":"new","name":"Best New Songs","label":"Best New Songs","kind":"new","on":true},
            {"id":"27","name":"Top J-Pop","label":"J-Pop","kind":"genre","on":true},
            {"id":"14","name":"Top Pop","label":"Pop","kind":"genre","on":true},
            {"id":"51","name":"Top K-Pop","label":"K-Pop","kind":"genre","on":false},
            {"id":"21","name":"Top Rock","label":"Rock","kind":"genre","on":false}"""
    }

    // What the pretend server says of the listener: their country ("auto"
    // follows the server's), their pins and the server's own country.
    @Volatile private var setting = "auto"
    @Volatile private var serverCountry = "us"

    private fun own(): String {
        val codes = COUNTRIES.joinToString(",") { "\"$it\"" }
        return """"setting":"$setting","pinned":["br","kr"],"countries":[$codes],"maxPinned":3,"serverCountry":"$serverCountry""""
    }

    private fun ownCountry() = if (setting == "auto") serverCountry else setting

    private fun serve(server: FakeServer) {
        server.answer("ping", type = "octo")
        server.answer(
            "getOpenSubsonicExtensions",
            """"openSubsonicExtensions":[{"name":"octoAcquisitions","versions":[1]},{"name":"octoTopSongs","versions":[1,2,3]}]""",
            type = "octo",
        )
        server.answer("getAlbumList2", """"albumList2":{"album":[]}""")
        server.answer("getArtists", """"artists":{"index":[]}""")
        server.answerBy("search3") { server.ok(""""searchResult3":{}""", type = "octo") }
        server.answer("getPlaylists", """"playlists":{"playlist":[]}""")
        server.answerBy("getCharts") { request ->
            val country = request.url.queryParameter("country") ?: ownCountry()
            server.ok(""""charts":{"country":"$country",${own()},"chart":[${if (country == "jp") JAPAN else HOME}]}""", type = "octo")
        }
        server.answerBy("getTopChart") { request ->
            val id = request.url.queryParameter("chart") ?: "34"
            val country = request.url.queryParameter("country") ?: ownCountry()
            val (name, rows) = charts[if (country == "jp" && id == "34") "27" else id] ?: charts.getValue("34")
            val entries = rows.mapIndexed { index, line ->
                val (title, artist, album, seconds, marks) = line.split("|")
                val owned = "o" in marks
                """{"rank":${index + 1},"inLibrary":$owned,"song":${song("$id-$index", title, artist, album, seconds.toInt(), explicit = "e" in marks, owned = owned)}}"""
            }.joinToString(",")
            server.ok(""""topSongs":{"artist":null,"source":"apple","chart":"$id","name":"$name","country":"$country","entry":[$entries]}""", type = "octo")
        }
        server.answer("getAcquisitions", """"acquisitions":{"acquisition":[]}""", type = "octo")
        server.fileBy("getCoverArt") { madeUpCover(it.url.queryParameter("id").orEmpty()) }
    }

    @Test
    fun drawTheCharts() {
        assumeTrue(System.getenv("OCTO_SHOTS") == "1")
        val out = File("build/shots").apply { mkdirs() }
        FakeServer().use { server ->
            serve(server)
            val settings = SettingsStore(File(folder.root, "settings.json"))
            settings.update { it.copy(lyrics = it.lyrics.copy(online = false)) }
            val http = OkHttpClient()
            val accounts = Accounts(settings, SessionOnlySecrets(), http)
            lateinit var app: AppState
            runBlocking { accounts.signIn(SignInRequest(server.address, "winters", "pw")) } as SignInOutcome.Done
            SwingUtilities.invokeAndWait {
                app = AppState(settings, accounts, http, CoroutineScope(SupervisorJob() + Dispatchers.Main), DesktopOs.Windows, SilentPlayer(), OnlineLyrics(http, server.address.toHttpUrl()), restored = accounts.restore(), listeningRoot = File(folder.root, "listening"))
            }
            val scene = ImageComposeScene(1440, 900, Density(1f)) {
                CompositionLocalProvider(LocalTyping provides TypingState()) { Shell(app, null) {} }
            }
            fun shot(name: String?, settleMs: Long = 2_500) {
                val begin = System.currentTimeMillis()
                var t = 0L
                while (System.currentTimeMillis() < begin + settleMs) {
                    SwingUtilities.invokeAndWait { scene.render(t) }
                    Thread.sleep(30)
                    t = (System.currentTimeMillis() - begin) * 1_000_000
                }
                val image = scene.render(t)
                if (name != null) File(out, "$name.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            }
            fun capsule(name: String) = scene.semanticsOwners.firstNotNullOf { find(it.unmergedRootSemanticsNode, name) }.boundsInWindow
            val charts = app.search!!.charts
            shot("home-charts", 3_500)
            SwingUtilities.invokeAndWait { app.navigator.go(Page.Charts) }
            shot("charts-page")
            SwingUtilities.invokeAndWait { charts.selected = "new" }
            shot("charts-new-songs")
            SwingUtilities.invokeAndWait { charts.selected = "18" }
            shot("charts-genre")
            SwingUtilities.invokeAndWait { charts.selected = "34" }
            shot("charts-own-country", 1_500)
            // The country capsule, clicked as a person would: the menu opens under it.
            val home = capsule("United States")
            SwingUtilities.invokeAndWait {
                scene.sendPointerEvent(PointerEventType.Press, home.center, buttons = PointerButtons(isPrimaryPressed = true))
                scene.sendPointerEvent(PointerEventType.Release, home.center)
            }
            shot("charts-country-menu", 1_500)
            // The same menu scrolled part way, both its edges fading.
            val row = scene.semanticsOwners.firstNotNullOf { findText(it.unmergedRootSemanticsNode, "Canada") }.boundsInWindow
            repeat(4) { SwingUtilities.invokeAndWait { scene.sendPointerEvent(PointerEventType.Scroll, row.center, scrollDelta = Offset(0f, 1f)) } }
            shot("charts-country-menu-scrolled", 1_500)
            SwingUtilities.invokeAndWait { app.popups.close() }
            // The listener chose the United Kingdom, then looks at Japan.
            setting = "gb"
            SwingUtilities.invokeAndWait { charts.opened() }
            shot(null, 1_000)
            SwingUtilities.invokeAndWait { charts.pickCountry("jp") }
            shot("charts-japan")
            val japan = capsule("Japan").roundToIntRect()
            SwingUtilities.invokeAndWait { showCountryMenu(app, charts, japan) }
            shot("charts-country-menu-japan", 1_500)
            SwingUtilities.invokeAndWait { app.popups.close() }
            // A country picked asks what to do with it; South Korea is pinned already.
            SwingUtilities.invokeAndWait { askCountry(app, charts, japan, "kr") }
            shot("charts-country-choice", 1_500)
            SwingUtilities.invokeAndWait { app.popups.close() }
            SwingUtilities.invokeAndWait { showCountryMenu(app, charts, japan, mutableStateOf("ko")) }
            shot("charts-country-menu-search", 1_500)
            SwingUtilities.invokeAndWait { app.popups.close() }
            SwingUtilities.invokeAndWait { showCountryMenu(app, charts, japan, mutableStateOf("zz")) }
            shot("charts-country-menu-none", 1_500)
            SwingUtilities.invokeAndWait { app.popups.close() }
            // A server in the country with the longest name, followed by the listener.
            setting = "auto"
            serverCountry = COUNTRIES.maxBy { countryName(it).length }
            SwingUtilities.invokeAndWait {
                charts.showOwn()
                charts.opened()
            }
            shot(null, 1_500)
            SwingUtilities.invokeAndWait { showCountryMenu(app, charts, capsule(countryName(serverCountry)).roundToIntRect()) }
            shot("charts-country-menu-longest", 1_500)
            scene.close()
        }
    }

    private fun find(node: SemanticsNode, name: String): SemanticsNode? {
        if (node.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(name) == true) return node
        return node.children.firstNotNullOfOrNull { find(it, name) }
    }

    private fun findText(node: SemanticsNode, words: String): SemanticsNode? {
        if (node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == words } == true) return node
        return node.children.firstNotNullOfOrNull { findText(it, words) }
    }

    // A cover of soft coloured shapes, in colours of its own for each id.
    private fun madeUpCover(id: String): ByteArray {
        val palette = listOf(0xFF1B2A4A, 0xFFE0703A, 0xFF3AA6A0, 0xFFF2D06B, 0xFF6B3A7A, 0xFFB8C4C9, 0xFF2F5D3A, 0xFFC0463F).map { it.toInt() }
        val seed = id.hashCode() and 0x7fffffff
        fun colour(n: Int) = palette[(seed / (n + 1) + n) % palette.size]
        val surface = org.jetbrains.skia.Surface.makeRasterN32Premul(300, 300)
        val canvas = surface.canvas
        canvas.clear(colour(0))
        val paint = org.jetbrains.skia.Paint()
        paint.color = colour(1)
        canvas.drawCircle(60f + seed % 90, 100f, 90f, paint)
        paint.color = colour(2)
        canvas.drawCircle(220f, 140f + seed % 80, 110f, paint)
        return surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes
    }
}
