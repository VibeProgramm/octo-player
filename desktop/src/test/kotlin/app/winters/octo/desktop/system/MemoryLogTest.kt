package app.winters.octo.desktop.system

import app.winters.octo.desktop.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

// The memory log: its line says "?" for what could not be read, its file
// rolls over at a cap, and a live reading holds the counters.
class MemoryLogTest {
    @get:Rule val temp = TemporaryFolder()

    private val unknown = MemoryReading(null, null, null, null, null, null, null, null, 120, 256, null)

    @Test
    fun aLineWithNothingReadableShowsQuestionMarksAndTheHeap() {
        val line = memoryLine("2026-01-01T10:00:00", 11, unknown, "tray", false, 3, null)
        assertEquals(
            "2026-01-01T10:00:00 min=11 private=? ws=? handles=? threads=? gpu=?/? cpu=? io=? heap=120/256 reopens=? trims=3 trimFreed=? window=tray playing=no",
            line,
        )
    }

    @Test
    fun theLogRollsOverAtItsCapAndKeepsTheNewestLines() {
        val dir = File(temp.root, "logs")
        val log = MemoryLog(dir, capBytes = 200)
        val lines = (1..20).map { "line-%02d-".format(it).padEnd(40, 'x') }
        lines.forEach(log::append)
        assertEquals(listOf("memory.log", "memory.log.1"), dir.list()!!.sorted())
        assertEquals(lines.takeLast(5), log.lastLines(5))
        assertEquals(lines.subList(13, 20), log.lastLines(7))
    }

    @Test
    fun theBugSettingsHoldNoCertificateOrSearch() {
        val settings = AppSettings(
            trustedCertificates = mapOf("music.example.com" to "ab"),
            recentSearches = listOf("me@example.com"),
        )
        val text = bugDetails("1.2.3", null, 0, unknown, bugSettings(settings, 5), emptyList())
        assertFalse(text.contains("example.com"))
        assertFalse(text.contains("@"))
        assertTrue(text.contains("side panel=none"))
    }

    @Test
    fun aReadingNeverThrowsAndKnowsTheCpuTime() {
        val r = readMemory { 1L }
        assertNotNull(r.cpuSeconds)
    }

    @Test
    fun theBugDetailsHoldTheReportAndNoAccountDetail() {
        val text = bugDetails(
            "1.2.3",
            "Direct3D | NVIDIA",
            3_900_000,
            unknown,
            listOf("ambience" to "Glow", "close to tray" to "false"),
            listOf("first log line", "second log line"),
        )
        assertTrue(text.contains("Renderer: Direct3D | NVIDIA"))
        assertTrue(text.contains("Up for 1 h 5 min"))
        assertTrue(text.contains("Memory now: private=?"))
        assertTrue(text.contains("ambience=Glow, close to tray=false"))
        assertTrue(text.indexOf("first log line") in 0 until text.indexOf("second log line"))
        assertFalse(text.contains("http"))
        assertFalse(text.contains("@"))
    }

    @Test
    fun aLiveReadingHoldsTheCounters() {
        Assume.assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        val r = readMemory { 3L }
        assertNotNull(r.privateMb)
        assertNotNull(r.workingSetMb)
        assertNotNull(r.handles)
        assertNotNull(r.threads)
        assertEquals(3L, r.deviceReopens)
        assertTrue(r.heapCommittedMb >= r.heapUsedMb)
    }
}
