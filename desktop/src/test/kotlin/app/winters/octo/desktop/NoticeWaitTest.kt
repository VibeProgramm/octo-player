package app.winters.octo.desktop

import app.winters.octo.desktop.player.SilentPlayer
import app.winters.octo.desktop.secrets.SessionOnlySecrets
import app.winters.octo.desktop.server.Accounts
import app.winters.octo.desktop.settings.DesktopOs
import app.winters.octo.desktop.settings.SettingsStore
import app.winters.octo.lyrics.OnlineLyrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

// How long a passing notice stays: its time, then while the pointer is on it, and never
// taking a newer notice of the same words away.
class NoticeWaitTest {
    @get:Rule val temp = TemporaryFolder()

    private val server = FakeServer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After
    fun stop() {
        scope.cancel()
        server.close()
    }

    private fun app(): AppState {
        val settings = SettingsStore(File(temp.root, "settings.json"), 0)
        val http = OkHttpClient()
        return AppState(
            settings,
            Accounts(settings, SessionOnlySecrets(), http),
            http,
            scope,
            DesktopOs.Windows,
            SilentPlayer(),
            OnlineLyrics(http, server.address.toHttpUrl()),
            restored = server.connection(),
        )
    }

    private fun waitFor(ms: Long = 2_000, what: () -> Boolean) {
        val until = System.currentTimeMillis() + ms
        while (!what() && System.currentTimeMillis() < until) Thread.sleep(5)
        assertTrue("waited too long", what())
    }

    @Test
    fun aPassingNoticeLeavesAfterItsTime() {
        val app = app()
        app.passingNotice("Added to queue", showFor = 50, poll = 10)
        assertEquals("Added to queue", app.notice)
        Thread.sleep(20)
        assertEquals("Added to queue", app.notice)
        waitFor { app.notice == null }
    }

    @Test
    fun theNoticeStaysWhileThePointerIsOnItAndLeavesSoonAfter() {
        val app = app()
        app.noticeHovered = true
        app.passingNotice("Added to queue", showFor = 50, poll = 10)
        Thread.sleep(200)
        assertEquals("Added to queue", app.notice)
        app.noticeHovered = false
        waitFor(100) { app.notice == null }
    }

    @Test
    fun anOlderTimerNeverTakesANewerNoticeOfTheSameWordsAway() {
        val app = app()
        app.passingNotice("Added to queue", showFor = 50, poll = 10)
        Thread.sleep(30)
        app.passingNotice("Added to queue", showFor = 50, poll = 10)
        // 60 ms after the first one began is past its time, yet the second one has 20 ms to go.
        Thread.sleep(30)
        assertEquals("Added to queue", app.notice)
        waitFor { app.notice == null }
        assertNull(app.notice)
    }
}
