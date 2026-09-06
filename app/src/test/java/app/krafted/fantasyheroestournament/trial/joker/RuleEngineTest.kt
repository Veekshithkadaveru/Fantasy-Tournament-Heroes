package app.krafted.fantasyheroestournament.trial.joker

import app.krafted.fantasyheroestournament.data.TrialConfigLoader
import org.junit.Assert.*
import org.junit.Test

class RuleEngineTest {
    private val config = TrialConfigLoader.defaultConfig

    private fun engine(round: Int = 0, seed: Long = 7L) = RuleEngine(config, round, seed)

    /** Runs the trial until [rule] is the one on the banner, so a rule's own behaviour can be tested. */
    private fun engineShowing(rule: JokerRule, round: Int = 0): RuleEngine {
        for (seed in 1L..80L) {
            val engine = RuleEngine(config, round, seed)
            engine.start()
            var guard = 0
            while (engine.state.phase == JokerPhase.PLAYING && guard++ < 10) {
                if (engine.state.rule == rule) return engine
                engine.advance(engine.state.ruleRemaining)
            }
        }
        fail("No seed reached $rule")
        error("unreachable")
    }

    private fun indexOf(engine: RuleEngine, itemType: Int): Int =
        engine.state.cells.indexOfFirst { it.itemType == itemType && !it.locked }
            .also { assertTrue("Grid should hold a $itemType", it >= 0) }

    private fun indexOfOther(engine: RuleEngine, itemType: Int): Int =
        engine.state.cells.indexOfFirst { it.itemType != itemType && !it.locked }
            .also { assertTrue("Grid should hold something other than $itemType", it >= 0) }

    @Test fun everyRuleScoresTheWayThePlanDescribes() {
        assertEquals(50, evaluateJokerTap(JokerRule.TAP_MATCH, RuleEngine.LOLLIPOP))
        assertEquals(-35, evaluateJokerTap(JokerRule.TAP_MATCH, RuleEngine.PINK_GEM))
        assertEquals(50, evaluateJokerTap(JokerRule.TAP_INVERSE, RuleEngine.WRAPPED_CANDY))
        assertEquals(-35, evaluateJokerTap(JokerRule.TAP_INVERSE, RuleEngine.LOLLIPOP))
        assertEquals(50, evaluateJokerTap(JokerRule.TAP_COLOR, RuleEngine.PINK_GEM))
        assertEquals(-35, evaluateJokerTap(JokerRule.TAP_COLOR, RuleEngine.PURPLE_ORB))
        assertEquals(50, evaluateJokerTap(JokerRule.TAP_PURPLE, RuleEngine.PURPLE_ORB))
        assertEquals(-35, evaluateJokerTap(JokerRule.TAP_PURPLE, RuleEngine.LOLLIPOP))
        // A freeze has no right answer: every sweet on the board is the wrong one.
        RuleEngine.ITEM_TYPES.forEach { assertEquals(-35, evaluateJokerTap(JokerRule.TAP_NONE, it)) }
    }

    @Test fun theGridIsFourByFourAndFullyDealtBeforeTheStart() {
        val engine = engine()
        assertEquals(16, engine.state.cells.size)
        assertEquals(4, engine.state.gridSize)
        assertTrue(engine.state.cells.all { it.itemType in RuleEngine.ITEM_TYPES })
        assertEquals(JokerPhase.READY, engine.state.phase)
        assertNotEquals(JokerRule.TAP_NONE, engine.state.rule)
    }

    @Test fun trialWaitsForExplicitStart() {
        val engine = engine()
        val initial = engine.state
        engine.advance(30f)
        assertNull(engine.tap(0))
        assertEquals(initial, engine.state)
    }

    @Test fun aCorrectTapPaysFiftyAndTakesTheSweet() {
        val engine = engineShowing(JokerRule.TAP_MATCH)
        val index = indexOf(engine, RuleEngine.LOLLIPOP)
        val id = engine.state.cells[index].id
        assertEquals(50, engine.tap(index))
        assertEquals(50, engine.state.score)
        assertEquals(1, engine.state.correctTaps)
        assertEquals(0, engine.state.wrongTaps)
        assertEquals(JokerMark.CORRECT, engine.state.cells[index].mark)
        // The cell is locked while it bursts, then a fresh sweet is dealt into it.
        assertNull(engine.tap(index))
        engine.advance(RuleEngine.FLASH_SECONDS + .01f)
        assertEquals(JokerMark.NONE, engine.state.cells[index].mark)
        assertNotEquals(id, engine.state.cells[index].id)
    }

    @Test fun aWrongTapCostsThirtyFiveAndLeavesTheSweetInPlace() {
        val engine = engineShowing(JokerRule.TAP_MATCH)
        val index = indexOfOther(engine, RuleEngine.LOLLIPOP)
        val cell = engine.state.cells[index]
        assertEquals(-35, engine.tap(index))
        assertEquals(1, engine.state.wrongTaps)
        assertEquals(JokerMark.WRONG, engine.state.cells[index].mark)
        engine.advance(RuleEngine.FLASH_SECONDS + .01f)
        // The wrong sweet stays exactly where it was: the mistake is the player's to see.
        assertEquals(cell.id, engine.state.cells[index].id)
        assertEquals(cell.itemType, engine.state.cells[index].itemType)
    }

    @Test fun inverseRewardsEverythingButTheLollipop() {
        val engine = engineShowing(JokerRule.TAP_INVERSE)
        assertEquals(50, engine.tap(indexOfOther(engine, RuleEngine.LOLLIPOP)))
        val lollipop = engine.state.cells.indexOfFirst { it.itemType == RuleEngine.LOLLIPOP && !it.locked }
        if (lollipop >= 0) assertEquals(-35, engine.tap(lollipop))
    }

    @Test fun colourAndPurpleRulesRewardOnlyTheirOwnItem() {
        val pink = engineShowing(JokerRule.TAP_COLOR)
        assertEquals(50, pink.tap(indexOf(pink, RuleEngine.PINK_GEM)))
        assertEquals(-35, pink.tap(indexOfOther(pink, RuleEngine.PINK_GEM)))
        val purple = engineShowing(JokerRule.TAP_PURPLE)
        assertEquals(50, purple.tap(indexOf(purple, RuleEngine.PURPLE_ORB)))
        assertEquals(-35, purple.tap(indexOfOther(purple, RuleEngine.PURPLE_ORB)))
    }

    @Test fun theBannerChangesEverySevenSecondsAndDealsANewBoard() {
        val engine = engine()
        engine.start()
        val opening = engine.state.rule
        val board = engine.state.cells.map { it.id }
        assertEquals(7f, engine.state.ruleSeconds, .001f)
        engine.advance(6.9f)
        assertEquals(opening, engine.state.rule)
        assertEquals(1, engine.state.rules.size)
        engine.advance(.2f)
        assertNotEquals(opening, engine.state.rule)
        assertEquals(2, engine.state.rules.size)
        assertNotEquals(board, engine.state.cells.map { it.id })
        assertEquals(0f, engine.state.ruleElapsed, .11f)
        // 20 seconds hold three rules; the phase track has to be able to show all of them.
        assertEquals(3, engine.state.totalPhases)
    }

    @Test fun everyRuleAlwaysHasSomethingOnTheBoardToObey() {
        repeat(3) { round ->
            val engine = RuleEngine(config, round, seed = 13L * round + 3)
            engine.start()
            while (engine.state.phase == JokerPhase.PLAYING) {
                if (!engine.state.isFreeze) {
                    assertTrue("Round $round left ${engine.state.rule} with no target",
                        engine.state.targets > 0)
                    // Sweeping every target off the board must not strand the rule either.
                    engine.state.cells.indices
                        .filter { evaluateJokerTap(engine.state.rule, engine.state.cells[it].itemType) > 0 }
                        .forEach { engine.tap(it) }
                }
                // Long enough for the bursts to settle, so each check lands on a board at rest.
                engine.advance(RuleEngine.FLASH_SECONDS)
            }
        }
    }

    /**
     * The trial tests reading, not tapping speed. Every deal has to hold enough wrong sweets that
     * hitting cells without checking the banner loses points — TAP_INVERSE most of all, since it
     * rewards three of the four item types.
     */
    @Test fun tappingWithoutReadingTheBannerNeverPays() {
        JokerRule.entries.filter { it != JokerRule.TAP_NONE }.forEach { rule ->
            var banked = 0
            var boards = 0
            for (seed in 1L..40L) {
                val engine = RuleEngine(config, 0, seed)
                engine.start()
                var guard = 0
                while (engine.state.rule != rule && engine.state.phase == JokerPhase.PLAYING && guard++ < 10) {
                    engine.advance(engine.state.ruleRemaining)
                }
                if (engine.state.rule != rule) continue
                boards++
                banked += engine.state.cells.sumOf { evaluateJokerTap(rule, it.itemType) }
            }
            assertTrue("No board dealt $rule", boards > 0)
            assertTrue("Blind tapping under $rule paid ${banked / boards} a board", banked <= 0)
        }
    }

    @Test fun survivingAFreezePaysOneHundredAndFifty() {
        val engine = engineShowing(JokerRule.TAP_NONE)
        val before = engine.state.rawScore
        engine.advance(engine.state.ruleRemaining)
        assertEquals(before + RuleEngine.FREEZE_BONUS, engine.state.rawScore)
        assertEquals(1, engine.state.freezeBonuses)
        assertNotEquals(JokerRule.TAP_NONE, engine.state.rule)
    }

    @Test fun touchingTheBoardDuringAFreezeCostsThePenaltyAndTheBonus() {
        val engine = engineShowing(JokerRule.TAP_NONE)
        val before = engine.state.rawScore
        assertEquals(-35, engine.tap(0))
        assertFalse(engine.state.freezeClean)
        engine.advance(engine.state.ruleRemaining)
        assertEquals(before - 35, engine.state.rawScore)
        assertEquals(0, engine.state.freezeBonuses)
    }

    @Test fun aFreezeStillStandingAtTheWhistleIsPaidOut() {
        val engine = engineShowing(JokerRule.TAP_NONE)
        val before = engine.state.rawScore
        engine.advance(engine.state.remainingSeconds)
        assertEquals(JokerPhase.COMPLETE, engine.state.phase)
        assertTrue(engine.state.timedOut)
        assertEquals(before + RuleEngine.FREEZE_BONUS, engine.state.rawScore)
    }

    @Test fun theScoreIsClampedBetweenZeroAndOneThousand() {
        val engine = engineShowing(JokerRule.TAP_MATCH)
        // Penalties can outrun points, but a trial never hands a round a negative number.
        repeat(20) {
            val index = engine.state.cells.indexOfFirst { it.itemType != RuleEngine.LOLLIPOP && !it.locked }
            if (index >= 0) engine.tap(index)
            engine.advance(RuleEngine.FLASH_SECONDS)
        }
        assertTrue(engine.state.rawScore < 0)
        assertEquals(0, engine.state.score)

        val capped = engine()
        capped.start()
        while (capped.state.phase == JokerPhase.PLAYING) {
            capped.state.cells.indices
                .filter { evaluateJokerTap(capped.state.rule, capped.state.cells[it].itemType) > 0 }
                .forEach { capped.tap(it) }
            capped.advance(RuleEngine.FLASH_SECONDS)
        }
        assertTrue("A flawless run should reach the cap, got ${capped.state.rawScore}",
            capped.state.rawScore > 1000)
        assertEquals(1000, capped.state.score)
    }

    @Test fun theTrialEndsOnTheClockAndIgnoresLaterTaps() {
        val engine = engine()
        engine.start()
        engine.advance(19.5f)
        assertEquals(JokerPhase.PLAYING, engine.state.phase)
        engine.advance(1f)
        assertEquals(JokerPhase.COMPLETE, engine.state.phase)
        assertEquals(0f, engine.state.remainingSeconds, 0f)
        val finished = engine.state
        assertNull(engine.tap(0))
        engine.advance(5f)
        assertEquals(finished, engine.state)
    }

    @Test fun pauseFreezesTheClockAndTheBoardWithoutEndingTheRun() {
        val engine = engine()
        engine.start()
        engine.advance(2f)
        engine.pause()
        val paused = engine.state
        engine.advance(200f)
        assertNull(engine.tap(0))
        assertEquals(paused, engine.state)
        engine.resume()
        assertTrue(engine.state.canTap)
        engine.advance(.5f)
        assertEquals(paused.elapsedSeconds + .5f, engine.state.elapsedSeconds, .001f)
    }

    @Test fun roundsFlipFasterAndDealFewerTargets() {
        val seconds = (0..2).map { RuleEngine(config, it, seed = 5L).state.ruleSeconds }
        assertEquals(7f, seconds[0], .001f)
        assertTrue(seconds[0] > seconds[1] && seconds[1] > seconds[2])
        // The Final gets 25 seconds of a faster shuffle, so it holds the most rule phases.
        assertTrue(RuleEngine(config, 2, seed = 5L).state.totalPhases >
            RuleEngine(config, 0, seed = 5L).state.totalPhases)
        val targets = (0..2).map { round ->
            val engine = RuleEngine(config, round, seed = 5L)
            engine.state.cells.count { evaluateJokerTap(engine.state.rule, it.itemType) > 0 }
        }
        assertTrue("Every round must still be playable", targets.all { it > 0 })
    }

    @Test fun invalidFrameDeltasAndTapsAreIgnored() {
        val engine = engine()
        engine.start()
        engine.advance(.5f)
        val before = engine.state
        for (delta in listOf(-1f, 0f, Float.NaN, Float.POSITIVE_INFINITY)) engine.advance(delta)
        assertNull(engine.tap(-1))
        assertNull(engine.tap(16))
        assertEquals(before, engine.state)
    }

    @Test fun restartAndRoundSelectionResetTheRunWithoutChangingAnActiveRound() {
        val engine = engine()
        engine.selectRound(2)
        assertEquals(25f, engine.state.remainingSeconds, 0f)
        engine.start()
        engine.selectRound(0)
        assertEquals(2, engine.state.roundIndex)
        engine.advance(2f)
        engine.tap(0)
        engine.pause()
        engine.restart()
        assertEquals(JokerPhase.READY, engine.state.phase)
        assertEquals(2, engine.state.roundIndex)
        assertEquals(0, engine.state.score)
        assertEquals(0, engine.state.correctTaps + engine.state.wrongTaps)
        assertEquals(1, engine.state.rules.size)
        assertFalse(engine.state.paused)
        engine.selectRound(1)
        assertEquals(20f, engine.state.remainingSeconds, 0f)
    }

    @Test fun replayReshufflesTheBoard() {
        val engine = engine()
        val first = engine.state.cells.map { it.itemType }
        engine.restart()
        assertNotEquals(first, engine.state.cells.map { it.itemType })
    }

    @Test fun ruleTimingIsIndependentOfFrameRate() {
        val slow = engine()
        val fast = engine()
        slow.start(); fast.start()
        slow.advance(9f)
        repeat(900) { fast.advance(.01f) }
        assertEquals(slow.state.elapsedSeconds, fast.state.elapsedSeconds, .001f)
        assertEquals(slow.state.ruleElapsed, fast.state.ruleElapsed, .001f)
        assertEquals(slow.state.rules, fast.state.rules)
    }
}
