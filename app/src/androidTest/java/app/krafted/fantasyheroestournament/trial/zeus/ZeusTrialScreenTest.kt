package app.krafted.fantasyheroestournament.trial.zeus

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.krafted.fantasyheroestournament.data.TrialConfigLoader
import app.krafted.fantasyheroestournament.ui.theme.FantasyHeroesTournamentTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ZeusTrialScreenTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var engine: ZeusEngine
    private lateinit var publish: () -> Unit

    private fun launch() {
        engine = ZeusEngine(TrialConfigLoader.defaultConfig)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            var state by remember { mutableStateOf(engine.state) }
            publish = { state = engine.state }
            fun change(action: ZeusEngine.() -> Unit) { engine.action(); publish() }
            FantasyHeroesTournamentTheme(darkTheme = true, dynamicColor = false) {
                ZeusTrialScreen(state,
                    onStart = { change { start() } }, onHold = { change { hold() } },
                    onRelease = { change { release() } }, onCancelCharge = { change { cancelCharge() } },
                    onPause = { change { pause() } }, onResume = { change { resume() } },
                    onRestart = { change { restart() } }, onSelectRound = { index -> change { selectRound(index) } })
            }
        }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun step(seconds: Float) {
        compose.runOnIdle { engine.advance(seconds); publish() }
        compose.mainClock.advanceTimeBy(200)
    }

    private fun begin() {
        compose.onNodeWithTag("charge").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(200)
    }

    @Test fun realPressSurvivesRecompositionAndScoresOneStrike() {
        launch(); begin()
        compose.onNodeWithTag("charge").performTouchInput { down(center) }
        step(.5f)
        compose.runOnIdle { assertEquals(ZeusPhase.CHARGING, engine.state.phase); assertTrue(engine.state.power > 0f) }
        step(.5f)
        compose.onNodeWithTag("charge").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(200)
        compose.runOnIdle {
            assertEquals(1, engine.state.strikes.size)
            assertEquals(ZeusPhase.STRIKE, engine.state.phase)
            assertTrue(engine.state.score > 0)
        }
        compose.onNodeWithContentDescription("Strike 1. Points: 130.").assertExists()
    }

    @Test fun cancellingTouchDoesNotSpendStrike() {
        launch(); begin()
        compose.onNodeWithTag("charge").performTouchInput { down(center) }
        step(.4f)
        compose.onNodeWithTag("charge").performTouchInput { cancel() }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertEquals(0, engine.state.strikes.size); assertTrue(engine.state.canCharge) }
    }

    @Test fun pauseFreezesTimerAndResumeKeepsRun() {
        launch(); begin(); step(2f)
        compose.onNodeWithTag("pause").performScrollTo().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("STORM ON HOLD").assertIsDisplayed()
        step(30f)
        compose.runOnIdle { assertEquals(18f, engine.state.remainingSeconds, 0f) }
        compose.onNodeWithText("RESUME TRIAL").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertFalse(engine.state.paused) }
        step(1f)
        compose.onNodeWithTag("timer").assertTextEquals("00:17")
    }

    @Test fun timeoutShowsBankedScoreAndReplayStartsFresh() {
        launch(); begin(); step(21f)
        compose.onNodeWithText("TIME’S UP").assertIsDisplayed()
        compose.onNodeWithTag("final_score").assertTextEquals("0")
        compose.onNodeWithText("5 unused strikes").assertExists()
        compose.onNodeWithText("PLAY AGAIN").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertEquals(ZeusPhase.READY, engine.state.phase); assertEquals(20f, engine.state.remainingSeconds, 0f) }
    }

    @Test fun finalDifficultyUses25SecondTimer() {
        launch()
        compose.onNodeWithText("Final").performScrollTo().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("timer").assertTextEquals("00:25")
        compose.runOnIdle { assertEquals(2, engine.state.roundIndex) }
    }
}
