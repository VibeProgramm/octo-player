package app.winters.octo.desktop.ui

import app.winters.octo.desktop.FakeServer
import app.winters.octo.desktop.nav.Page
import app.winters.octo.desktop.pages.showSection
import app.winters.octo.ui.imports.IMPORT
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.swing.SwingUtilities

// Import's trickle while MusicBrainz is out, once it answers again, after the
// list whose songs waited is removed, and for a state this app does not know
// yet. Made-up lists, from a pretend Octo server.
// Only when asked: OCTO_SHOTS=1 ./gradlew :desktop:test --tests '*ImportHoldShotsTest*'.
// Saved under build/shots/polish/imports-hold/.
class ImportHoldShotsTest {
    @get:Rule val folder = TemporaryFolder()

    private fun track(key: String, title: String, artist: String, album: String, state: String, detail: String? = null, progress: Double? = null) =
        """{"key":"$key","title":"$title","artist":"$artist","album":"$album","seconds":240,"state":"$state",
        "detail":${detail?.let { "\"$it\"" } ?: "null"},"progress":${progress ?: "null"}}"""

    private fun overview(lists: String, trickle: String) = """"imports":{"lists":[$lists],"trickle":$trickle}"""

    @Test
    fun drawTheMusicBrainzHold() {
        assumeTrue(System.getenv("OCTO_SHOTS") == "1")
        FakeServer().use { server ->
            PolishShotsTest.Rig(folder, PolishData.library(100), server).use { rig ->
                server.answer(
                    "getOpenSubsonicExtensions",
                    """"openSubsonicExtensions":[{"name":"octoImports","versions":[1,2]}]""",
                    type = "octo",
                )
                server.answer("getImportServices", """"importServices":{"services":[]}""", type = "octo")
                val held = "Waiting for MusicBrainz to answer"
                val fetched = listOf(
                    track("s:1", "Teardrop", "Massive Attack", "Mezzanine", "done", "Fetched from Soulseek"),
                    track("s:2", "Halo", "Beyoncé", "I Am... Sasha Fierce", "done", "Fetched from Soulseek"),
                ).joinToString(",")
                val road = """{"id":"link-road","name":"Road trip","source":"link","total":12,"have":9,"missing":3,"queued":3,"done":2,"getMissing":true,"canRefresh":true}"""
                val liked = """{"id":"spotify-liked","name":"Liked Songs","source":"spotifyLiked","total":40,"have":40}"""
                val states = listOf(
                    "waiting" to overview(
                        "$road,$liked",
                        """{"state":"waitingForMusicDatabase","perHour":20,"queued":3,"done":2,"recent":[$fetched],"next":[
                        ${track("s:3", "Roads", "Portishead", "Dummy", "queued", held)},
                        ${track("s:4", "Linger", "The Cranberries", "Everybody Else Is Doing It", "queued", held)},
                        ${track("s:5", "No Surprises", "Radiohead", "OK Computer", "queued", held)}]}""",
                    ),
                    "recovered" to overview(
                        "$road,$liked",
                        """{"state":"running","perHour":20,"queued":2,"downloading":1,"done":2,"recent":[$fetched],
                        "current":${track("s:3", "Roads", "Portishead", "Dummy", "downloading", "Downloading from Soulseek", 0.42)},"next":[
                        ${track("s:4", "Linger", "The Cranberries", "Everybody Else Is Doing It", "queued")},
                        ${track("s:5", "No Surprises", "Radiohead", "OK Computer", "queued")}]}""",
                    ),
                    "list-removed" to overview(
                        liked,
                        """{"state":"idle","perHour":20,"done":2,"recent":[$fetched]}""",
                    ),
                    "unknown-state" to overview(
                        "$road,$liked",
                        """{"state":"somethingNew","perHour":20,"queued":3,"done":2,"recent":[$fetched]}""",
                    ),
                )
                rig.signIn()
                val app = rig.app
                for (size in listOf(PolishShotsTest.Size.Hd, PolishShotsTest.Size.Min)) {
                    rig.scene(size) { scene ->
                        for ((name, answer) in states) {
                            server.answer("getImports", answer, type = "octo")
                            runBlocking { app.imports.refresh() }
                            rig.reset(scene)
                            SwingUtilities.invokeAndWait {
                                showSection(IMPORT, "trickle")
                                app.navigator.go(Page.Imports)
                            }
                            rig.shot(scene, "imports-hold/${size.label}/$name", 2_000)
                        }
                    }
                }
            }
        }
    }
}
