package app.krafted.fantasyheroestournament.trial.zeus

import app.krafted.fantasyheroestournament.data.TrialConfigLoader
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class ZeusEngineTest {
    private val config = TrialConfigLoader.defaultConfig

    @Test fun centerAndPerfectBoundariesAward200() {
        for (power in listOf(.48f, .5f, .52f)) {
            val result = calculateZeusPoints(power, .4f, .6f)
            assertTrue(result.isPerfect)
            assertTrue(result.isHit)
            assertEquals(200, result.points)
        }
    }

    @Test fun bandEdgesAndJustOutsidePerfectAward130() {
        for (power in listOf(.4f, .479f, .521f, .6f)) {
            val result = calculateZeusPoints(power, .4f, .6f)
            assertFalse(result.isPerfect)
            assertTrue(result.isHit)
            assertEquals(130, result.points)
        }
    }

    @Test fun missesAndInvalidPowerNeverScore() {
        for (power in listOf(.399f, .601f, -1f, 2f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertEquals(0, calculateZeusPoints(power, .4f, .6f).points)
        }
    }

    @Test fun trialWaitsForExplicitStart() {
        val engine = ZeusEngine(config)
        val initial = engine.state
        engine.advance(30f)
        engine.hold()
        assertNull(engine.release())
        assertEquals(initial, engine.state)
    }

    @Test fun holdChargesAndReleaseUsesCurrentMovingBand() {
        val engine = ZeusEngine(config)
        engine.start()
        engine.hold()
        engine.advance(.7f)
        val charged = engine.state
        assertTrue(charged.power > 0f)
        val result = engine.release()!!
        assertEquals(charged.power, result.power, .0001f)
        assertEquals(charged.bandStart, result.bandStart, .0001f)
        assertEquals(charged.bandEnd, result.bandEnd, .0001f)
        assertEquals(ZeusPhase.STRIKE, engine.state.phase)
    }

    @Test fun releaseAndHoldDuringRecoveryCannotSpendExtraThrows() {
        val engine = ZeusEngine(config)
        engine.start()
        engine.hold()
        engine.advance(.5f)
        engine.release()
        repeat(10) { engine.hold(); assertNull(engine.release()) }
        assertEquals(1, engine.state.strikes.size)
        engine.advance(ZeusEngine.STRIKE_SECONDS)
        assertEquals(ZeusPhase.AIMING, engine.state.phase)
        assertEquals(0f, engine.state.power, 0f)
    }

    @Test fun fivePerfectStrikesCompleteWith1000InEveryRound() {
        repeat(3) { round ->
            val engine = ZeusEngine(config, round)
            engine.start()
            repeat(5) {
                engine.hold()
                var reached = false
                repeat(1600) {
                    if (!reached) {
                        engine.advance(.001f)
                        val s = engine.state
                        reached = abs(s.power - (s.bandStart + s.bandEnd) / 2) < (s.bandEnd - s.bandStart) * .08f
                    }
                }
                assertTrue("A perfect strike must be reachable in round $round", reached)
                assertEquals(200, engine.release()!!.points)
                engine.advance(ZeusEngine.STRIKE_SECONDS)
            }
            assertEquals(ZeusPhase.COMPLETE, engine.state.phase)
            assertEquals(1000, engine.state.score)
            assertEquals(5, engine.state.perfectCount)
            val finished = engine.state
            engine.hold(); engine.release(); engine.advance(100f)
            assertEquals(finished, engine.state)
        }
    }

    @Test fun targetNarrowsEachThrowAndAcrossRounds() {
        val firstWidths = mutableListOf<Float>()
        repeat(3) { round ->
            val engine = ZeusEngine(config, round)
            firstWidths += engine.state.bandEnd - engine.state.bandStart
            engine.start()
            var previousWidth = firstWidths.last()
            repeat(4) {
                engine.hold(); engine.release(); engine.advance(ZeusEngine.STRIKE_SECONDS)
                val width = engine.state.bandEnd - engine.state.bandStart
                assertTrue(width < previousWidth)
                previousWidth = width
            }
        }
        assertTrue(firstWidths[0] > firstWidths[1] && firstWidths[1] > firstWidths[2])
    }

    @Test fun bandDriftsContinuouslyAndStaysWithinMeter() {
        repeat(3) { round ->
            val engine = ZeusEngine(config, round)
            val initialBand = engine.state.bandStart
            engine.start()
            repeat(1900) {
                engine.advance(.01f)
                assertTrue(engine.state.bandStart >= 0f)
                assertTrue(engine.state.bandEnd <= 1f)
                assertTrue(engine.state.bandEnd > engine.state.bandStart)
            }
            assertNotEquals(initialBand, engine.state.bandStart)
        }
    }

    @Test fun pauseCancelsChargeAndFreezesTimeWithoutSpendingThrow() {
        val engine = ZeusEngine(config)
        engine.start(); engine.hold(); engine.advance(.6f); engine.pause()
        val paused = engine.state
        engine.advance(200f); engine.hold()
        assertNull(engine.release())
        assertEquals(paused, engine.state)
        assertEquals(0, engine.state.strikes.size)
        engine.resume()
        assertTrue(engine.state.canCharge)
        assertEquals(0f, engine.state.power, 0f)
        engine.advance(.5f)
        assertEquals(paused.elapsedSeconds + .5f, engine.state.elapsedSeconds, .001f)
    }

    @Test fun cancelledTouchDoesNotScore() {
        val engine = ZeusEngine(config)
        engine.start(); engine.hold(); engine.advance(.3f); engine.cancelCharge()
        assertNull(engine.release())
        assertTrue(engine.state.canCharge)
        assertEquals(0, engine.state.strikes.size)
    }

    @Test fun timeoutEndsTrialAndKeepsBankedPoints() {
        val engine = ZeusEngine(config)
        engine.start(); engine.hold(); engine.advance(1f); engine.release()
        val score = engine.state.score
        assertTrue(score > 0)
        engine.advance(30f)
        assertEquals(ZeusPhase.COMPLETE, engine.state.phase)
        assertEquals(0f, engine.state.remainingSeconds, 0f)
        assertEquals(score, engine.state.score)
        assertNull(engine.release())
    }

    @Test fun timeoutWhileHoldingCannotScoreAfterDeadline() {
        val engine = ZeusEngine(config, 2)
        assertEquals(25f, engine.state.remainingSeconds, 0f)
        engine.start(); engine.hold(); engine.advance(26f)
        assertTrue(engine.state.timedOut)
        assertNull(engine.release())
        assertEquals(0, engine.state.score)
    }

    @Test fun powerCapsAtFullAndInvalidFrameDeltasAreIgnored() {
        val engine = ZeusEngine(config)
        engine.start(); engine.hold(); engine.advance(3f)
        assertEquals(1f, engine.state.power, 0f)
        val before = engine.state
        for (delta in listOf(-1f, 0f, Float.NaN, Float.POSITIVE_INFINITY)) engine.advance(delta)
        assertEquals(before, engine.state)
    }

    @Test fun restartAndRoundSelectionResetRunWithoutChangingAnActiveRound() {
        val engine = ZeusEngine(config)
        engine.selectRound(2)
        assertEquals(25f, engine.state.remainingSeconds, 0f)
        engine.start(); engine.selectRound(0)
        assertEquals(2, engine.state.roundIndex)
        engine.hold(); engine.advance(.5f); engine.release(); engine.pause(); engine.restart()
        assertEquals(ZeusPhase.READY, engine.state.phase)
        assertEquals(2, engine.state.roundIndex)
        assertEquals(0, engine.state.score)
        assertFalse(engine.state.paused)
        engine.selectRound(1)
        assertEquals(20f, engine.state.remainingSeconds, 0f)
    }

    @Test fun chargeAndDriftAreIndependentOfFrameRate() {
        val oneFrame = ZeusEngine(config)
        val manyFrames = ZeusEngine(config)
        oneFrame.start(); oneFrame.hold(); oneFrame.advance(.75f)
        manyFrames.start(); manyFrames.hold(); repeat(75) { manyFrames.advance(.01f) }
        assertEquals(oneFrame.state.power, manyFrames.state.power, .00001f)
        assertEquals(oneFrame.state.bandStart, manyFrames.state.bandStart, .00001f)
        assertEquals(oneFrame.release()!!.points, manyFrames.release()!!.points)
    }
}
