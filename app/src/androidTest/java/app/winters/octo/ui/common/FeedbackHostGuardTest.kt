package app.winters.octo.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// A tap on the place a message has just left is taken and does nothing; once the guard has
// passed, a tap there reaches what is under it.
@RunWith(AndroidJUnit4::class)
class FeedbackHostGuardTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun aLateTapIsSwallowedUntilTheGuardPasses() {
        val feedback = Feedback()
        var taps by mutableIntStateOf(0)
        var density = 1f
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val haze = rememberHazeState()
            density = LocalDensity.current.density
            CompositionLocalProvider(LocalHaze provides haze) {
                Box(Modifier.fillMaxSize().hazeSource(haze).clickable { taps++ })
                FeedbackHost(feedback)
            }
        }
        feedback.show("Saved")
        rule.mainClock.advanceTimeBy(500)
        val bounds = rule.onNodeWithText("Saved").getBoundsInRoot()
        val center = Offset(
            (bounds.left + bounds.right).value / 2f * density,
            (bounds.top + bounds.bottom).value / 2f * density,
        )
        // Past the time it stays up and the time it takes to leave.
        rule.mainClock.advanceTimeBy(3_700)
        rule.mainClock.advanceTimeBy(200)
        rule.onRoot().performTouchInput { click(center) }
        assertEquals(0, taps)
        rule.mainClock.advanceTimeBy(LATE_TAP_GUARD_MS + 100)
        rule.onRoot().performTouchInput { click(center) }
        assertEquals(1, taps)
    }
}
