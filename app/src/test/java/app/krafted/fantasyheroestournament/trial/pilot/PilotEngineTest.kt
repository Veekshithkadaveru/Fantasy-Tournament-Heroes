package app.krafted.fantasyheroestournament.trial.pilot

import app.krafted.fantasyheroestournament.data.TrialConfigLoader
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class PilotEngineTest {
    private val config = TrialConfigLoader.defaultConfig

    private fun engine(round: Int = 0, seed: Long = 7L) = PilotEngine(config, round, seed)

    /**
     * Picks the lane with the most room from the storms just ahead, leaning toward the next coin
     * when several lanes are equally safe. A run that survives this is one a player can fly.
     */
    private fun autopilot(state: PilotState): Float {
        // Under one row of lookahead, so two rows never average into an unflyable compromise lane.
        val window = state.scrollDistance - PilotEngine.TAIL_UNITS..
            state.scrollDistance + PilotEngine.ROW_SPACING * .6f
        val ahead = state.clouds.filter { it.distance in window }
        val coin = state.coins.filter { it.distance > state.scrollDistance && it.distance in window }
            .minByOrNull { it.distance }
        // Keep clear of the wall by more than the plane's own width: the corridor drifts sideways
        // while the plane is easing toward the lane it picked.
        val low = state.corridorLeft + PilotEngine.PLANE_RADIUS * 1.6f
        val high = state.corridorRight - PilotEngine.PLANE_RADIUS * 1.6f
        var best = (low + high) / 2f
        var bestScore = -Float.MAX_VALUE
        var lane = low
        while (lane <= high) {
            val clearance = ahead.minOfOrNull { cloud ->
                abs(lane - cloud.centerX(state.elapsedSeconds)) -
                    cloud.radius * PilotEngine.CLOUD_HITBOX / PilotEngine.UNITS_PER_WIDTH
            } ?: 1f
            val room = clearance - PilotEngine.PLANE_RADIUS
            val lure = if (coin == null) 0f else -abs(lane - coin.x) * .1f
            // Never trade safety for a coin: an unsafe lane is scored far below any safe one.
            val score = if (room <= 0f) room * 100f else room.coerceAtMost(.03f) + lure
            if (score > bestScore) { bestScore = score; best = lane }
            lane += .004f
        }
        return best
    }

    private fun report(state: PilotState): String {
        val cloud = state.clouds.minByOrNull { abs(it.distance - state.scrollDistance) }
        return "impact=${state.impact} at ${"%.2f".format(state.scrollDistance)}u " +
            "planeX=${"%.3f".format(state.planeX)} corridor=${"%.3f".format(state.corridorLeft)}" +
            "..${"%.3f".format(state.corridorRight)} nearestCloud=" +
            (cloud?.let {
                "x=${"%.3f".format(it.centerX(state.elapsedSeconds))} r=${"%.2f".format(it.radius)} " +
                    "dd=${"%.2f".format(it.distance - state.scrollDistance)}"
            } ?: "none")
    }

    /** Flies the guaranteed passage of every row, so the run only ends on the clock. */
    private fun flyTheGap(engine: PilotEngine, seconds: Float, step: Float = 1f / 60f) {
        var flown = 0f
        while (flown < seconds && engine.state.phase == PilotPhase.FLYING) {
            engine.steerTo(autopilot(engine.state))
            engine.advance(step)
            flown += step
        }
    }

    @Test fun coinValueAndEveryEighthCoinBonusFollowThePlan() {
        var state = PilotState(0, 20f, 5f, phase = PilotPhase.FLYING)
        repeat(8) { state = updatePilotFrame(state, 0f, isCollectingCoin = true, hitObstacle = false) }
        assertEquals(8, state.coinsCollected)
        assertEquals(7 * 30 + 30 + 120, state.coinPoints)
        assertTrue(state.lastPickup!!.isStreakBonus)
        state = updatePilotFrame(state, 0f, isCollectingCoin = true, hitObstacle = false)
        assertEquals(7 * 30 + 150 + 30, state.coinPoints)
        assertFalse(state.lastPickup!!.isStreakBonus)
    }

    @Test fun survivalPaysFifteenPointsPerSecondAndScoreCapsAtOneThousand() {
        var state = PilotState(0, 20f, 5f, phase = PilotPhase.FLYING)
        repeat(20) { state = updatePilotFrame(state, 1f, isCollectingCoin = false, hitObstacle = false) }
        assertEquals(300, state.survivalPoints)
        assertEquals(300, state.totalScore)
        repeat(40) { state = updatePilotFrame(state, 0f, isCollectingCoin = true, hitObstacle = false) }
        assertTrue(state.survivalPoints + state.coinPoints > 1000)
        assertEquals(1000, state.totalScore)
    }

    @Test fun obstacleEndsTheRunAndFreezesTheBankedScore() {
        var state = PilotState(0, 20f, 5f, phase = PilotPhase.FLYING)
        repeat(6) { state = updatePilotFrame(state, 1f, isCollectingCoin = true, hitObstacle = false) }
        val banked = state.totalScore
        assertTrue(banked > 0)
        state = updatePilotFrame(state, .5f, isCollectingCoin = false, hitObstacle = true)
        assertTrue(state.isDead)
        assertEquals(PilotPhase.CRASH, state.phase)
        state = updatePilotFrame(state, 5f, isCollectingCoin = true, hitObstacle = false)
        assertEquals(banked, state.totalScore)
    }

    @Test fun trialWaitsForExplicitStart() {
        val engine = engine()
        val initial = engine.state
        engine.advance(30f)
        engine.steerTo(.1f)
        assertEquals(initial, engine.state)
    }

    @Test fun theCorridorAlwaysLeavesAFlyablePassage() {
        repeat(3) { round ->
            repeat(4) { run ->
                val engine = PilotEngine(config, round, seed = 31L * run + round)
                engine.start()
                flyTheGap(engine, engine.state.durationSeconds + 1f)
                assertEquals("Round $round run $run should survive its own gap: ${report(engine.state)}",
                    PilotPhase.COMPLETE, engine.state.phase)
                assertFalse(engine.state.isDead)
                assertEquals(0f, engine.state.remainingSeconds, 0f)
            }
        }
    }

    @Test fun flyingTheGapCollectsCoinsAndClearsHalfTheTarget() {
        val engine = engine()
        engine.start()
        flyTheGap(engine, 20f)
        assertTrue("Coins should be reachable on the safe line", engine.state.coinsCollected >= 8)
        assertTrue("A clean flight should be worth real points", engine.state.totalScore >= 500)
        assertTrue(engine.state.totalScore <= 1000)
    }

    /**
     * The cap has to be a full run's reward. If half a flight already maxes the trial, the second
     * half and the whole speed escalation stop mattering.
     */
    @Test fun theThousandCapTakesMostOfTheTrialToReach() {
        repeat(4) { run ->
            val engine = PilotEngine(config, 0, seed = 91L * run + 5)
            engine.start()
            flyTheGap(engine, 10f)
            assertEquals(PilotPhase.FLYING, engine.state.phase)
            assertTrue("Run $run capped at half time with ${engine.state.totalScore}",
                engine.state.totalScore < 1000)
            flyTheGap(engine, 11f)
            assertEquals(PilotPhase.COMPLETE, engine.state.phase)
            assertTrue("A flawless run should still approach the cap, got ${engine.state.totalScore}",
                engine.state.totalScore >= 700)
        }
    }

    @Test fun holdingTheWallCrashesAndBanksWhatWasEarned() {
        val engine = engine()
        engine.start()
        // The first storm row sits a full screen ahead, so this can only be the wall.
        engine.steerTo(0f)
        repeat(120) { engine.advance(1f / 60f) }
        assertTrue(engine.state.isDead)
        assertEquals(PilotImpact.WALL, engine.state.impact)
        val crashed = engine.state
        assertTrue(crashed.survivalTimeSeconds > 0f)
        engine.advance(PilotEngine.CRASH_SECONDS + .1f)
        assertEquals(PilotPhase.COMPLETE, engine.state.phase)
        assertEquals(crashed.totalScore, engine.state.totalScore)
        // The clock and the corridor stop the moment the plane goes down.
        assertEquals(crashed.elapsedSeconds, engine.state.elapsedSeconds, 0f)
        assertEquals(crashed.scrollDistance, engine.state.scrollDistance, 0f)
    }

    @Test fun stormCloudsAreLethal() {
        val engine = engine()
        engine.start()
        while (engine.state.phase == PilotPhase.FLYING) {
            val cloud = engine.state.clouds
                .filter { it.distance > engine.state.scrollDistance }
                .minByOrNull { it.distance }
            if (cloud != null) {
                val state = engine.state
                engine.steerTo(cloud.centerX(state.elapsedSeconds).coerceIn(
                    state.corridorLeft + PilotEngine.PLANE_RADIUS + .01f,
                    state.corridorRight - PilotEngine.PLANE_RADIUS - .01f))
            }
            engine.advance(1f / 60f)
        }
        assertTrue("Steering into a cloud must end the run", engine.state.isDead)
        assertEquals(PilotImpact.CLOUD, engine.state.impact)
    }

    @Test fun speedEscalatesEverySevenSecondsAndNarrowsWithRound() {
        val engine = engine()
        engine.start()
        assertEquals(0, engine.state.speedStep)
        assertEquals(5f, engine.state.currentSpeed, .001f)
        flyTheGap(engine, 7.5f)
        assertEquals(1, engine.state.speedStep)
        assertTrue(engine.state.currentSpeed > 5f)
        flyTheGap(engine, 7f)
        assertEquals(2, engine.state.speedStep)
        val widths = (0..2).map { PilotEngine.halfWidth(0f, it) }
        assertTrue(widths[0] > widths[1] && widths[1] > widths[2])
    }

    @Test fun steeringEasesTowardTheTargetAndStaysInsideTheCorridor() {
        val engine = engine()
        engine.start()
        engine.steerTo(2f)
        assertEquals(1f, engine.state.targetX, 0f)
        engine.steerTo(-2f)
        assertEquals(0f, engine.state.targetX, 0f)
        engine.steerTo(.5f)
        engine.steer(.2f)
        assertEquals(.7f, engine.state.targetX, .0001f)
        val before = engine.state.planeX
        engine.advance(1f / 60f)
        assertTrue(engine.state.planeX > before)
        assertTrue(engine.state.planeX < engine.state.targetX)
    }

    @Test fun pauseFreezesTimeAndTheCorridorWithoutEndingTheRun() {
        val engine = engine()
        engine.start()
        flyTheGap(engine, 2f)
        engine.pause()
        val paused = engine.state
        engine.advance(200f)
        engine.steerTo(0f)
        assertEquals(paused, engine.state)
        engine.resume()
        assertTrue(engine.state.canSteer)
        engine.advance(.5f)
        assertEquals(paused.elapsedSeconds + .5f, engine.state.elapsedSeconds, .001f)
    }

    @Test fun timeoutCompletesTheTrialAliveWithTheFullSurvivalBonus() {
        val engine = engine()
        engine.start()
        flyTheGap(engine, 25f)
        assertEquals(PilotPhase.COMPLETE, engine.state.phase)
        assertFalse(engine.state.isDead)
        assertEquals(PilotImpact.NONE, engine.state.impact)
        assertEquals(20f, engine.state.survivalTimeSeconds, .05f)
        assertEquals(300, engine.state.survivalPoints)
    }

    @Test fun invalidFrameDeltasAndSteerValuesAreIgnored() {
        val engine = engine()
        engine.start()
        engine.advance(.5f)
        val before = engine.state
        for (delta in listOf(-1f, 0f, Float.NaN, Float.POSITIVE_INFINITY)) engine.advance(delta)
        engine.steer(Float.NaN)
        engine.steerTo(Float.NaN)
        assertEquals(before, engine.state)
    }

    @Test fun restartAndRoundSelectionResetTheRunWithoutChangingAnActiveRound() {
        val engine = engine()
        engine.selectRound(2)
        assertEquals(25f, engine.state.remainingSeconds, 0f)
        assertEquals(8f, engine.state.baseSpeed, 0f)
        engine.start()
        engine.selectRound(0)
        assertEquals(2, engine.state.roundIndex)
        flyTheGap(engine, 2f)
        engine.pause()
        engine.restart()
        assertEquals(PilotPhase.READY, engine.state.phase)
        assertEquals(2, engine.state.roundIndex)
        assertEquals(0, engine.state.totalScore)
        assertEquals(.5f, engine.state.planeX, 0f)
        assertFalse(engine.state.paused)
        engine.selectRound(1)
        assertEquals(20f, engine.state.remainingSeconds, 0f)
        assertEquals(6.5f, engine.state.baseSpeed, 0f)
    }

    @Test fun replayReshufflesTheCorridor() {
        val engine = engine()
        val first = engine.state.clouds.map { it.x }
        engine.restart()
        assertNotEquals(first, engine.state.clouds.map { it.x })
    }

    @Test fun scrollAndSteeringAreIndependentOfFrameRate() {
        val slow = engine()
        val fast = engine()
        slow.start(); fast.start()
        slow.steerTo(.8f); fast.steerTo(.8f)
        slow.advance(.75f)
        repeat(75) { fast.advance(.01f) }
        assertEquals(slow.state.scrollDistance, fast.state.scrollDistance, .001f)
        assertEquals(slow.state.planeX, fast.state.planeX, .002f)
        assertEquals(slow.state.elapsedSeconds, fast.state.elapsedSeconds, .001f)
    }

    @Test fun theCorridorIsRecycledInsteadOfGrowingForever() {
        val engine = engine()
        engine.start()
        flyTheGap(engine, 20f)
        assertTrue("Objects behind the plane must be culled",
            engine.state.clouds.size + engine.state.coins.size < 40)
        assertTrue(engine.state.clouds.all { it.distance > engine.state.scrollDistance - PilotEngine.TAIL_UNITS - 1f })
    }
}
