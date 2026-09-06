package app.krafted.fantasyheroestournament.trial.joker

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
import kotlin.math.ceil

@RunWith(AndroidJUnit4::class)
class JokerTrialScreenTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var engine: RuleEngine
    private lateinit var publish: () -> Unit

    private fun launch() {
        engine = RuleEngine(TrialConfigLoader.defaultConfig, seed = 7L)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            var state by remember { mutableStateOf(engine.state) }
            publish = { state = engine.state }
            fun change(action: RuleEngine.() -> Unit) { engine.action(); publish() }
            FantasyHeroesTournamentTheme(darkTheme = true, dynamicColor = false) {
                JokerTrialScreen(state,
                    onStart = { change { start() } }, onTap = { index -> change { tap(index) } },
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
        compose.onNodeWithTag("launch").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(200)
    }

    /** Taps the first cell the active rule rewards, or the first it punishes. */
    private fun tapCell(correct: Boolean) {
        val index = engine.state.cells.indices.first {
            val cell = engine.state.cells[it]
            !cell.locked && (evaluateJokerTap(engine.state.rule, cell.itemType) > 0) == correct
        }
        compose.onNodeWithTag("cell_$index").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(200)
    }

    private fun assertTimerMatchesEngine() {
        compose.onNodeWithTag("timer")
            .assertTextEquals("00:%02d".format(ceil(engine.state.remainingSeconds).toInt()))
    }

    @Test fun theBoardIsAFourByFourGridOfSweets() {
        launch()
        compose.onAllNodesWithTag("cell_0", useUnmergedTree = true).assertCountEquals(1)
        repeat(16) { compose.onNodeWithTag("cell_$it").assertExists() }
        compose.runOnIdle { assertEquals(16, engine.state.cells.size) }
    }

    @Test fun obeyingTheBannerScoresFiftyAndBreakingItCostsThirtyFive() {
        launch(); begin()
        compose.runOnIdle { assertEquals(JokerPhase.PLAYING, engine.state.phase) }
        tapCell(correct = true)
        compose.runOnIdle {
            assertEquals(50, engine.state.score)
            assertEquals(1, engine.state.correctTaps)
        }
        compose.onNodeWithTag("score").assertTextEquals("50")
        step(RuleEngine.FLASH_SECONDS)
        tapCell(correct = false)
        compose.runOnIdle {
            assertEquals(15, engine.state.score)
            assertEquals(1, engine.state.wrongTaps)
        }
        compose.onNodeWithTag("score").assertTextEquals("15")
    }

    @Test fun theGridIgnoresTapsUntilTheTrialStarts() {
        launch()
        compose.onNodeWithTag("cell_0").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(200)
        compose.runOnIdle {
            assertEquals(JokerPhase.READY, engine.state.phase)
            assertEquals(0, engine.state.correctTaps + engine.state.wrongTaps)
        }
    }

    @Test fun theBannerFlipsToANewRuleAfterSevenSeconds() {
        launch(); begin()
        val opening = engine.state.rule
        compose.onNodeWithText(activityText(opening)).assertIsDisplayed()
        step(7.1f)
        compose.runOnIdle { assertNotEquals(opening, engine.state.rule) }
        compose.onNodeWithText(activityText(engine.state.rule)).assertIsDisplayed()
    }

    @Test fun pauseFreezesTimerAndResumeKeepsRun() {
        launch(); begin(); step(2f)
        compose.onNodeWithTag("pause").performScrollTo().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("THE COURT WAITS").assertIsDisplayed()
        step(30f)
        compose.runOnIdle { assertEquals(18f, engine.state.remainingSeconds, 0f) }
        compose.onNodeWithText("RESUME TRIAL").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertFalse(engine.state.paused) }
        step(1f)
        assertTimerMatchesEngine()
    }

    @Test fun timeoutShowsBankedScoreAndReplayStartsFresh() {
        launch(); begin()
        tapCell(correct = true)
        step(21f)
        compose.onNodeWithText("TIME’S UP").assertIsDisplayed()
        compose.onNodeWithTag("final_score").assertTextEquals(engine.state.score.toString())
        compose.onNodeWithText("PLAY AGAIN").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle {
            assertEquals(JokerPhase.READY, engine.state.phase)
            assertEquals(20f, engine.state.remainingSeconds, 0f)
            assertEquals(0, engine.state.score)
        }
    }

    @Test fun finalDifficultyUses25SecondTimerAndTheFastestRuleFlips() {
        launch()
        compose.onNodeWithText("Final").performScrollTo().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("timer").assertTextEquals("00:25")
        compose.runOnIdle {
            assertEquals(2, engine.state.roundIndex)
            assertTrue(engine.state.ruleSeconds < 7f)
        }
    }

    private fun activityText(rule: JokerRule): String = when (rule) {
        JokerRule.TAP_MATCH -> "TAP ONLY LOLLIPOPS!"
        JokerRule.TAP_INVERSE -> "AVOID LOLLIPOPS!"
        JokerRule.TAP_COLOR -> "TAP PINK ITEMS ONLY!"
        JokerRule.TAP_PURPLE -> "TAP PURPLE ORBS ONLY!"
        JokerRule.TAP_NONE -> "FREEZE! TAP NOTHING!"
    }
}
