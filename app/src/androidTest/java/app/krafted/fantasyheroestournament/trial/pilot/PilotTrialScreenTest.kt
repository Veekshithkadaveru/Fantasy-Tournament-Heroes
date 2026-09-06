package app.krafted.fantasyheroestournament.trial.pilot

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.krafted.fantasyheroestournament.data.TrialConfigLoader
import app.krafted.fantasyheroestournament.ui.theme.FantasyHeroesTournamentTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.ceil

@RunWith(AndroidJUnit4::class)
class PilotTrialScreenTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var engine: PilotEngine
    private lateinit var publish: () -> Unit

    private fun launch() {
        engine = PilotEngine(TrialConfigLoader.defaultConfig, seed = 7L)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            var state by remember { mutableStateOf(engine.state) }
            publish = { state = engine.state }
            fun change(action: PilotEngine.() -> Unit) { engine.action(); publish() }
            FantasyHeroesTournamentTheme(darkTheme = true, dynamicColor = false) {
                PilotTrialScreen(state,
                    onStart = { change { start() } }, onSteer = { delta -> change { steer(delta) } },
                    onSteerTo = { x -> change { steerTo(x) } },
                    onPause = { change { pause() } }, onResume = { change { resume() } },
                    onRestart = { change { restart() } }, onSelectRound = { index -> change { selectRound(index) } })
            }
        }
        compose.mainClock.advanceTimeByFrame()
    }

    /** Widest lane clear of the storms in the row the plane is flying through right now. */
    private fun safeLane(state: PilotState): Float {
        val window = state.scrollDistance - PilotEngine.TAIL_UNITS..
            state.scrollDistance + PilotEngine.ROW_SPACING * .6f
        val ahead = state.clouds.filter { it.distance in window }
        val low = state.corridorLeft + PilotEngine.PLANE_RADIUS * 1.6f
        val high = state.corridorRight - PilotEngine.PLANE_RADIUS * 1.6f
        var best = (low + high) / 2f
        var bestRoom = -Float.MAX_VALUE
        var lane = low
        while (lane <= high) {
            val room = ahead.minOfOrNull { cloud ->
                abs(lane - cloud.centerX(state.elapsedSeconds)) -
                    cloud.radius * PilotEngine.CLOUD_HITBOX / PilotEngine.UNITS_PER_WIDTH
            } ?: 1f
            if (room > bestRoom) { bestRoom = room; best = lane }
            lane += .004f
        }
        return best
    }

    /** Advances the simulation on a flyable line, so a UI assertion is never a crash in disguise. */
    private fun step(seconds: Float) {
        compose.runOnIdle {
            val target = engine.state.elapsedSeconds + seconds
            while (engine.state.phase == PilotPhase.FLYING && engine.state.elapsedSeconds < target) {
                engine.steerTo(safeLane(engine.state))
                engine.advance((target - engine.state.elapsedSeconds).coerceAtMost(1f / 60f))
            }
            if (engine.state.phase != PilotPhase.FLYING) engine.advance(seconds)
            publish()
        }
        compose.mainClock.advanceTimeBy(200)
    }

    private fun begin() {
        compose.onNodeWithTag("launch").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(200)
    }

    private fun assertTimerMatchesEngine(expectedSeconds: Int) {
        compose.runOnIdle {
            assertEquals(expectedSeconds.toFloat(), ceil(engine.state.remainingSeconds), .5f)
        }
        compose.onNodeWithTag("timer")
            .assertTextEquals("00:%02d".format(ceil(engine.state.remainingSeconds).toInt()))
    }

    @Test fun draggingTheCorridorSteersThePlaneAndSurvivesRecomposition() {
        launch(); begin()
        compose.runOnIdle { assertEquals(PilotPhase.FLYING, engine.state.phase) }
        compose.onNodeWithTag("arena").performTouchInput { down(center); moveBy(Offset(width * .2f, 0f)) }
        compose.mainClock.advanceTimeBy(200)
        compose.runOnIdle { assertTrue("Drag should push the target right", engine.state.targetX > .5f) }
        compose.onNodeWithTag("arena").performTouchInput { up() }
        compose.runOnIdle { engine.advance(.4f); publish() }
        compose.mainClock.advanceTimeBy(200)
        compose.runOnIdle { assertTrue(engine.state.planeX > .5f) }
    }

    @Test fun theFlightStickMovesThePlaneToAnAbsolutePosition() {
        launch(); begin()
        compose.onNodeWithTag("stick").performScrollTo().performTouchInput { click(centerLeft) }
        compose.mainClock.advanceTimeBy(200)
        compose.runOnIdle { assertTrue("Tapping the left of the stick aims left", engine.state.targetX < .2f) }
    }

    @Test fun coinsAndSurvivalRaiseTheScoreWhileFlying() {
        launch(); begin(); step(6f)
        compose.runOnIdle {
            assertEquals(PilotPhase.FLYING, engine.state.phase)
            assertTrue("Six seconds airborne is worth at least the survival tick",
                engine.state.totalScore >= 90)
        }
        assertTimerMatchesEngine(14)
        compose.onNodeWithTag("score").assertTextEquals(engine.state.totalScore.toString())
    }

    @Test fun pauseFreezesTimerAndResumeKeepsRun() {
        launch(); begin(); step(2f)
        compose.onNodeWithTag("pause").performScrollTo().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("ENGINE IDLING").assertIsDisplayed()
        step(30f)
        assertTimerMatchesEngine(18)
        compose.onNodeWithText("RESUME TRIAL").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertFalse(engine.state.paused) }
        step(1f)
        assertTimerMatchesEngine(17)
    }

    @Test fun crashingIntoTheWallEndsTheRunAndKeepsTheBankedScore() {
        launch(); begin()
        compose.onNodeWithTag("stick").performScrollTo().performTouchInput { click(centerLeft) }
        compose.runOnIdle { repeat(180) { engine.advance(1f / 60f) }; publish() }
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithText("LOST TO THE WALL").assertIsDisplayed()
        compose.runOnIdle { assertTrue(engine.state.isDead) }
        compose.onNodeWithText("FLY AGAIN").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle {
            assertEquals(PilotPhase.READY, engine.state.phase)
            assertEquals(20f, engine.state.remainingSeconds, 0f)
            assertEquals(0, engine.state.totalScore)
        }
    }

    @Test fun survivingToTheWhistleCompletesTheTrial() {
        launch(); begin(); step(21f)
        compose.onNodeWithText("TRIAL COMPLETE").assertIsDisplayed()
        compose.runOnIdle {
            assertFalse(engine.state.isDead)
            assertEquals(300, engine.state.survivalPoints)
        }
        compose.onNodeWithTag("final_score").assertTextEquals(engine.state.totalScore.toString())
    }

    @Test fun finalDifficultyUses25SecondTimerAndTheFastestCorridor() {
        launch()
        compose.onNodeWithText("Final").performScrollTo().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("timer").assertTextEquals("00:25")
        compose.runOnIdle {
            assertEquals(2, engine.state.roundIndex)
            assertEquals(8f, engine.state.baseSpeed, 0f)
        }
    }
}
