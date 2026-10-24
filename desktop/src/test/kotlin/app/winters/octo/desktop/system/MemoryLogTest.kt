package app.winters.octo.desktop.system

import org.junit.Assert.assertEquals
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
        val line = memoryLine("2026-01-01T10:00:00", 11, unknown, "tray", false)
        assertEquals(
            "2026-01-01T10:00:00 min=11 private=? ws=? handles=? threads=? gpu=?/? cpu=? io=? heap=120/256 reopens=? window=tray playing=no",
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
