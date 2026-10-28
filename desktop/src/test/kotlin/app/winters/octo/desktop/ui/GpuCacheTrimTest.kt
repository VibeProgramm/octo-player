package app.winters.octo.desktop.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

// When the GPU cache trimmer runs: every so often, and soon after a window
// hides, never twice within the debounce, never dropped.
@OptIn(ExperimentalCoroutinesApi::class)
class GpuCacheTrimTest {
    private class Rig(scope: TestScope) {
        val calls = AtomicInteger()
        val trimmer = GpuCacheTrimmer(
            trim = {
                calls.incrementAndGet()
                1
            },
            readGpuMb = { null },
            dispatcher = StandardTestDispatcher(scope.testScheduler),
            clock = { scope.testScheduler.currentTime },
        )

        init {
            trimmer.start(scope.backgroundScope)
        }
    }

    @Test
    fun twoAsksWithinTheDebounceTrimOnce() = runTest {
        val rig = Rig(this)
        runCurrent()
        advanceTimeBy(1_000)
        rig.trimmer.soon()
        rig.trimmer.soon()
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(1, rig.calls.get())
        assertEquals(1L, rig.trimmer.trims.get())
    }

    @Test
    fun anAskSoonAfterATrimWaitsForTheDebounceAndIsNotDropped() = runTest {
        val rig = Rig(this)
        advanceTimeBy(300_001)
        runCurrent()
        assertEquals(1, rig.calls.get())
        advanceTimeBy(2_999)
        rig.trimmer.soon()
        advanceTimeBy(6_999)
        runCurrent()
        assertEquals(1, rig.calls.get())
        advanceTimeBy(2)
        runCurrent()
        assertEquals(2, rig.calls.get())
    }

    @Test
    fun theIntervalPassingTrimsWithoutAnAsk() = runTest {
        val rig = Rig(this)
        advanceTimeBy(299_000)
        runCurrent()
        assertEquals(0, rig.calls.get())
        advanceTimeBy(1_500)
        runCurrent()
        assertEquals(1, rig.calls.get())
    }

    @Test
    fun switchedOffNothingRuns() = runTest {
        val before = System.getProperty("octo.gpuTrim")
        val trimsBefore = GpuCacheTrim.trims.get()
        try {
            System.setProperty("octo.gpuTrim", "off")
            GpuCacheTrim.start(backgroundScope)
            GpuCacheTrim.soon()
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(trimsBefore, GpuCacheTrim.trims.get())
        } finally {
            if (before == null) System.clearProperty("octo.gpuTrim") else System.setProperty("octo.gpuTrim", before)
        }
    }

    @Test
    fun trimmingWithNoWindowsDoesNotThrow() = runTest {
        val emptied = trimAllWindows(holdMs = 0)
        assertTrue(emptied >= 0)
    }
}
