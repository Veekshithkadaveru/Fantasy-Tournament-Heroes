package app.krafted.fantasyheroestournament.trial.joker

import app.krafted.fantasyheroestournament.data.TournamentConfig
import kotlin.math.ceil
import kotlin.random.Random

enum class JokerPhase { READY, PLAYING, COMPLETE }

/**
 * The rule pool from the development plan. Banner copy lives in `joker_strings.xml`, so the engine
 * carries the rules and not the words for them.
 */
enum class JokerRule { TAP_MATCH, TAP_INVERSE, TAP_COLOR, TAP_PURPLE, TAP_NONE }

/** How a cell is currently reacting to a tap. */
enum class JokerMark { NONE, CORRECT, WRONG }

/**
 * Scoring contract from the development plan: obeying the active rule pays
 * [RuleEngine.CORRECT_POINTS], everything else costs [penalty], and during a freeze every tap is
 * wrong. Item types are the Mix_1 element numbers, so the grid and the rules speak one language.
 */
fun evaluateJokerTap(rule: JokerRule, itemType: Int, penalty: Int = RuleEngine.PENALTY): Int = when (rule) {
    JokerRule.TAP_MATCH -> if (itemType == RuleEngine.LOLLIPOP) RuleEngine.CORRECT_POINTS else -penalty
    JokerRule.TAP_INVERSE -> if (itemType != RuleEngine.LOLLIPOP) RuleEngine.CORRECT_POINTS else -penalty
    JokerRule.TAP_COLOR -> if (itemType == RuleEngine.PINK_GEM) RuleEngine.CORRECT_POINTS else -penalty
    JokerRule.TAP_PURPLE -> if (itemType == RuleEngine.PURPLE_ORB) RuleEngine.CORRECT_POINTS else -penalty
    JokerRule.TAP_NONE -> -penalty
}

data class JokerCell(
    /** Bumped whenever a fresh item is dealt into the cell, so the UI can animate the swap. */
    val id: Int,
    val itemType: Int,
    val mark: JokerMark = JokerMark.NONE,
    val markSeconds: Float = 0f
) {
    /** A marked cell is playing out its burst or its cross and cannot be tapped again yet. */
    val locked: Boolean get() = mark != JokerMark.NONE
    /** 0 to 1 across the flash, shared by the ring, the cross and the refill. */
    val flash: Float get() = (markSeconds / RuleEngine.FLASH_SECONDS).coerceIn(0f, 1f)
}

data class JokerTap(val index: Int, val points: Int, val itemType: Int) {
    val correct: Boolean get() = points > 0
}

data class JokerState(
    val roundIndex: Int,
    val durationSeconds: Float,
    /** How long one rule holds this round. The Final flips faster than the Qualifier. */
    val ruleSeconds: Float,
    val gridSize: Int = 4,
    /** What a wrong tap costs, kept on the state so the result screen can total it honestly. */
    val penalty: Int = RuleEngine.PENALTY,
    val phase: JokerPhase = JokerPhase.READY,
    val elapsedSeconds: Float = 0f,
    /** Every rule the run has been dealt, the active one last. */
    val rules: List<JokerRule> = listOf(JokerRule.TAP_MATCH),
    val ruleElapsed: Float = 0f,
    val cells: List<JokerCell> = emptyList(),
    val rawScore: Int = 0,
    val correctTaps: Int = 0,
    val wrongTaps: Int = 0,
    val freezeBonuses: Int = 0,
    /** True while the current phase has gone untouched, which is what a freeze pays for. */
    val freezeClean: Boolean = true,
    val lastTap: JokerTap? = null,
    val tapSeconds: Float = 0f,
    val paused: Boolean = false
) {
    val rule: JokerRule get() = rules.last()
    /** Penalties can outrun points; a trial still never contributes less than nothing to a round. */
    val score: Int get() = rawScore.coerceIn(0, 1000)
    val remainingSeconds: Float get() = (durationSeconds - elapsedSeconds).coerceAtLeast(0f)
    val ruleRemaining: Float get() = (ruleSeconds - ruleElapsed).coerceAtLeast(0f)
    val ruleProgress: Float get() = (ruleElapsed / ruleSeconds).coerceIn(0f, 1f)
    val timedOut: Boolean get() = remainingSeconds <= 0f
    val canTap: Boolean get() = !paused && phase == JokerPhase.PLAYING
    val isFreeze: Boolean get() = rule == JokerRule.TAP_NONE
    /** Cells the active rule rewards right now. */
    val targets: Int get() =
        if (isFreeze) 0 else cells.count { !it.locked && evaluateJokerTap(rule, it.itemType) > 0 }
    /** Rule phases the round has room for, so the phase track can be drawn before they arrive. */
    val totalPhases: Int get() = ceil(durationSeconds / ruleSeconds).toInt()
}

/** Pure Kotlin simulation. Time comes from the UI frame clock, never a background timer. */
class RuleEngine(
    private val config: TournamentConfig,
    roundIndex: Int = 0,
    private val seed: Long = DEFAULT_SEED
) {
    private var runCount = 0
    private var random = Random(seed)
    private var nextId = 0

    var state: JokerState = initialState(roundIndex)
        private set

    private fun initialState(roundIndex: Int): JokerState {
        require(roundIndex in config.rounds.indices)
        random = Random(seed + runCount)
        nextId = 0
        // Never opens on a freeze: a trial that starts by asking for nothing teaches nothing.
        val opening = OPENING_RULES.random(random)
        val value = JokerState(
            roundIndex = roundIndex,
            durationSeconds = config.rounds[roundIndex].trialDurationMs / 1000f,
            ruleSeconds = config.joker.ruleSwitchMs / 1000f * (1f - roundIndex * RULE_SPEED_UP),
            gridSize = config.joker.gridSize,
            penalty = config.joker.penalty,
            rules = listOf(opening)
        )
        return value.copy(cells = deal(opening, value))
    }

    fun selectRound(roundIndex: Int) {
        if (state.phase == JokerPhase.READY || state.phase == JokerPhase.COMPLETE) {
            state = initialState(roundIndex)
        }
    }

    fun start() {
        if (state.phase == JokerPhase.READY) state = state.copy(phase = JokerPhase.PLAYING)
    }

    fun restart() {
        runCount++
        state = initialState(state.roundIndex)
    }

    /** Returns the points the tap scored, or null when the grid ignored it. */
    fun tap(index: Int): Int? {
        if (!state.canTap || index !in state.cells.indices) return null
        val cell = state.cells[index]
        if (cell.locked) return null
        val points = evaluateJokerTap(state.rule, cell.itemType, config.joker.penalty)
        val correct = points > 0
        state = state.copy(
            cells = state.cells.replace(index, cell.copy(
                mark = if (correct) JokerMark.CORRECT else JokerMark.WRONG, markSeconds = 0f)),
            rawScore = state.rawScore + points,
            correctTaps = state.correctTaps + if (correct) 1 else 0,
            wrongTaps = state.wrongTaps + if (correct) 0 else 1,
            // Any touch at all forfeits the freeze bonus, which is the whole point of the rule.
            freezeClean = state.freezeClean && !state.isFreeze,
            lastTap = JokerTap(index, points, cell.itemType),
            tapSeconds = 0f
        )
        return points
    }

    fun pause() {
        if (state.phase == JokerPhase.READY || state.phase == JokerPhase.COMPLETE) return
        state = state.copy(paused = true)
    }

    fun resume() {
        state = state.copy(paused = false)
    }

    fun advance(deltaSeconds: Float) {
        if (!deltaSeconds.isFinite() || deltaSeconds <= 0f || state.paused ||
            state.phase == JokerPhase.READY || state.phase == JokerPhase.COMPLETE
        ) return

        var next = state
        var remaining = deltaSeconds.coerceAtMost(state.remainingSeconds)
        // Steps are cut at the next rule flip, so even a long frame switches the banner on time.
        while (remaining > 0f) {
            val step = minOf(remaining, next.ruleRemaining, next.remainingSeconds)
            if (step <= 0f) break
            remaining -= step
            next = next.copy(
                elapsedSeconds = (next.elapsedSeconds + step).coerceAtMost(next.durationSeconds),
                ruleElapsed = next.ruleElapsed + step,
                tapSeconds = next.tapSeconds + step,
                cells = age(next.cells, step)
            )
            next = restock(next)
            if (next.remainingSeconds <= 0f) break
            if (next.ruleRemaining <= 0f) next = switchRule(next)
        }
        state = if (next.remainingSeconds <= 0f) finish(next) else next
    }

    /** Runs every marked cell through its flash, then refills the ones that were taken. */
    private fun age(cells: List<JokerCell>, step: Float): List<JokerCell> {
        // The loop runs on every step, so it must not allocate while the grid sits still.
        if (cells.none { it.locked }) return cells
        return cells.map { cell ->
            when {
                !cell.locked -> cell
                cell.markSeconds + step < FLASH_SECONDS -> cell.copy(markSeconds = cell.markSeconds + step)
                // A taken item is replaced; a wrongly tapped one stays put as its own reminder.
                cell.mark == JokerMark.CORRECT -> JokerCell(nextId++, ITEM_TYPES.random(random))
                else -> cell.copy(mark = JokerMark.NONE, markSeconds = 0f)
            }
        }
    }

    /** The active rule must always have somewhere to be obeyed, even after a run of refills. */
    private fun restock(value: JokerState): JokerState {
        val wanted = winners(value.rule) ?: return value
        if (value.targets > 0) return value
        val slot = value.cells.indices.filter { !value.cells[it].locked }.randomOrNull(random) ?: return value
        return value.copy(cells = value.cells.replace(slot, JokerCell(nextId++, wanted.random(random))))
    }

    private fun switchRule(value: JokerState): JokerState {
        val next = JokerRule.entries.filter { it != value.rule }.random(random)
        val earned = freezeBonus(value)
        return value.copy(
            rules = value.rules + next,
            ruleElapsed = 0f,
            freezeClean = true,
            freezeBonuses = value.freezeBonuses + if (earned > 0) 1 else 0,
            rawScore = value.rawScore + earned,
            // Chaos reshuffles the board on every flip, so the new rule always starts playable.
            cells = deal(next, value)
        )
    }

    private fun finish(value: JokerState): JokerState {
        // A freeze cut short by the whistle still pays: the player did exactly what was asked.
        val earned = freezeBonus(value)
        return value.copy(
            phase = JokerPhase.COMPLETE,
            rawScore = value.rawScore + earned,
            freezeBonuses = value.freezeBonuses + if (earned > 0) 1 else 0
        )
    }

    private fun freezeBonus(value: JokerState): Int =
        if (value.isFreeze && value.freezeClean) FREEZE_BONUS else 0

    /**
     * A fresh board for [rule]: enough targets that the rule is always playable, and enough traps
     * that tapping without reading the banner never pays. Left to chance, a rule as broad as
     * TAP_INVERSE would fill the grid with free points and stop asking the player to read anything.
     */
    private fun deal(rule: JokerRule, value: JokerState): List<JokerCell> {
        val size = value.gridSize * value.gridSize
        val wanted = winners(rule)
            ?: return List(size) { JokerCell(nextId++, ITEM_TYPES.random(random)) }
        val traps = ITEM_TYPES - wanted.toSet()
        val targets = (MIN_TARGETS - value.roundIndex).coerceIn(1, size - MIN_TRAPS)
        // Deal both halves, fill the rest at random, then shuffle so no position gives the rule away.
        return List(size) { index ->
            when {
                index < targets -> wanted.random(random)
                index < targets + MIN_TRAPS -> traps.random(random)
                else -> ITEM_TYPES.random(random)
            }
        }.shuffled(random).map { JokerCell(nextId++, it) }
    }

    /** Item types the rule rewards, or null when the rule wants nothing tapped at all. */
    private fun winners(rule: JokerRule): List<Int>? = when (rule) {
        JokerRule.TAP_MATCH -> listOf(LOLLIPOP)
        JokerRule.TAP_INVERSE -> ITEM_TYPES - LOLLIPOP
        JokerRule.TAP_COLOR -> listOf(PINK_GEM)
        JokerRule.TAP_PURPLE -> listOf(PURPLE_ORB)
        JokerRule.TAP_NONE -> null
    }

    private fun List<JokerCell>.replace(index: Int, cell: JokerCell): List<JokerCell> =
        toMutableList().also { it[index] = cell }

    companion object {
        /** Grid item types, numbered as their Mix_1 elements. */
        const val LOLLIPOP = 1
        const val PINK_GEM = 3
        const val PURPLE_ORB = 4
        const val WRAPPED_CANDY = 6
        val ITEM_TYPES = listOf(LOLLIPOP, PINK_GEM, PURPLE_ORB, WRAPPED_CANDY)
        val OPENING_RULES = JokerRule.entries.filter { it != JokerRule.TAP_NONE }
        const val CORRECT_POINTS = 50
        const val PENALTY = 35
        const val FREEZE_BONUS = 150
        /** How long a tapped cell holds its burst or its cross before it settles. */
        const val FLASH_SECONDS = .3f
        /** How long the score that a tap earned floats above the grid. */
        const val TAP_SECONDS = .6f
        /** The banner flare after a flip, long enough to catch the eye without hiding the rule. */
        const val SWITCH_SECONDS = .5f
        /** Each round flips the rules 15% faster: the Joker's answer to a narrowing target band. */
        const val RULE_SPEED_UP = .15f
        /** Targets a fresh deal guarantees, one fewer each round. */
        const val MIN_TARGETS = 5
        /** Wrong sweets a fresh deal guarantees, enough that a blind tap is worth less than nothing. */
        const val MIN_TRAPS = 9
        const val DEFAULT_SEED = 20260906L
    }
}
