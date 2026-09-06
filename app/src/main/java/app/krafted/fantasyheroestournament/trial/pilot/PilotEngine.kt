package app.krafted.fantasyheroestournament.trial.pilot

import app.krafted.fantasyheroestournament.data.TournamentConfig
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class PilotPhase { READY, FLYING, CRASH, COMPLETE }

enum class PilotImpact { NONE, CLOUD, WALL }

/**
 * The corridor is a world one unit wide and [PilotEngine.VIEW_UNITS] tall per arena screen.
 * Horizontal positions are fractions of the corridor width; vertical positions are world units
 * of travelled distance, so [PilotEngine.UNITS_PER_WIDTH] converts between the two.
 */
data class PilotCoin(val id: Int, val distance: Float, val x: Float)

data class PilotCloud(val id: Int, val distance: Float, val x: Float, val radius: Float, val sway: Float) {
    /** Clouds breathe sideways; collision and drawing must share this position. */
    fun centerX(elapsedSeconds: Float): Float =
        x + sin(elapsedSeconds * .9f + sway) * PilotEngine.CLOUD_SWAY
}

data class PilotPickup(val points: Int, val isStreakBonus: Boolean, val x: Float)

data class PilotState(
    val roundIndex: Int,
    val durationSeconds: Float,
    val baseSpeed: Float,
    val phase: PilotPhase = PilotPhase.READY,
    val elapsedSeconds: Float = 0f,
    val planeX: Float = PilotEngine.START_X,
    val targetX: Float = PilotEngine.START_X,
    val scrollDistance: Float = 0f,
    val currentSpeed: Float = baseSpeed,
    val speedStep: Int = 0,
    val coinPoints: Int = 0,
    val coinStreak: Int = 0,
    val coinsCollected: Int = 0,
    val survivalTimeSeconds: Float = 0f,
    val isDead: Boolean = false,
    val impact: PilotImpact = PilotImpact.NONE,
    val clouds: List<PilotCloud> = emptyList(),
    val coins: List<PilotCoin> = emptyList(),
    val lastPickup: PilotPickup? = null,
    val pickupSeconds: Float = 0f,
    val crashSeconds: Float = 0f,
    val paused: Boolean = false
) {
    val survivalPoints: Int get() = (survivalTimeSeconds * PilotEngine.SURVIVAL_POINTS_PER_SECOND).toInt()
    val totalScore: Int get() = (survivalPoints + coinPoints).coerceAtMost(1000)
    val remainingSeconds: Float get() = (durationSeconds - elapsedSeconds).coerceAtLeast(0f)
    val timedOut: Boolean get() = remainingSeconds <= 0f
    val canSteer: Boolean get() = !paused && phase == PilotPhase.FLYING
    /** Coins banked toward the next streak bonus, 0 until 7. */
    val streakProgress: Int get() = coinStreak % PilotEngine.STREAK_LENGTH
    val corridorLeft: Float get() = PilotEngine.corridorLeft(scrollDistance, roundIndex)
    val corridorRight: Float get() = PilotEngine.corridorRight(scrollDistance, roundIndex)
}

/**
 * Frame contract from the development plan: survival ticks every frame, a collected coin adds its
 * value plus the streak bonus, and an obstacle ends the run with the banked score intact.
 */
fun updatePilotFrame(
    state: PilotState,
    deltaTime: Float,
    isCollectingCoin: Boolean,
    hitObstacle: Boolean
): PilotState {
    if (state.isDead) return state
    if (hitObstacle) return state.crash(PilotImpact.CLOUD)
    val flown = state.copy(survivalTimeSeconds = state.survivalTimeSeconds + deltaTime)
    return if (isCollectingCoin) flown.collectCoin(flown.planeX) else flown
}

internal fun PilotState.collectCoin(x: Float): PilotState {
    val streak = coinStreak + 1
    val bonus = if (streak % PilotEngine.STREAK_LENGTH == 0) PilotEngine.STREAK_BONUS else 0
    return copy(
        coinPoints = coinPoints + PilotEngine.COIN_POINTS + bonus,
        coinStreak = streak,
        coinsCollected = coinsCollected + 1,
        lastPickup = PilotPickup(PilotEngine.COIN_POINTS + bonus, bonus > 0, x),
        pickupSeconds = 0f
    )
}

internal fun PilotState.crash(cause: PilotImpact): PilotState =
    copy(phase = PilotPhase.CRASH, isDead = true, impact = cause, crashSeconds = 0f)

/** Pure Kotlin simulation. Time comes from the UI frame clock, never a background timer. */
class PilotEngine(
    private val config: TournamentConfig,
    roundIndex: Int = 0,
    private val seed: Long = DEFAULT_SEED
) {
    private var runCount = 0
    private var random = Random(seed)
    private var nextRow = 0
    private var nextId = 0

    var state: PilotState = initialState(roundIndex)
        private set

    private fun initialState(roundIndex: Int): PilotState {
        require(roundIndex in config.rounds.indices)
        random = Random(seed + runCount)
        nextRow = 0
        nextId = 0
        val speed = config.pilot.baseSpeed[roundIndex]
        return spawnAhead(
            PilotState(
                roundIndex = roundIndex,
                durationSeconds = config.rounds[roundIndex].trialDurationMs / 1000f,
                baseSpeed = speed,
                currentSpeed = speed
            )
        )
    }

    fun selectRound(roundIndex: Int) {
        if (state.phase == PilotPhase.READY || state.phase == PilotPhase.COMPLETE) {
            state = initialState(roundIndex)
        }
    }

    fun start() {
        if (state.phase == PilotPhase.READY) state = state.copy(phase = PilotPhase.FLYING)
    }

    fun restart() {
        runCount++
        state = initialState(state.roundIndex)
    }

    /** Relative steering: [delta] is a fraction of the corridor width. */
    fun steer(delta: Float) {
        if (!delta.isFinite()) return
        steerTo(state.targetX + delta)
    }

    fun steerTo(x: Float) {
        if (!state.canSteer || !x.isFinite()) return
        state = state.copy(targetX = x.coerceIn(0f, 1f))
    }

    fun pause() {
        if (state.phase == PilotPhase.READY || state.phase == PilotPhase.COMPLETE) return
        state = state.copy(paused = true, targetX = state.planeX)
    }

    fun resume() {
        state = state.copy(paused = false)
    }

    fun advance(deltaSeconds: Float) {
        if (!deltaSeconds.isFinite() || deltaSeconds <= 0f || state.paused ||
            state.phase == PilotPhase.READY || state.phase == PilotPhase.COMPLETE
        ) return

        if (state.phase == PilotPhase.CRASH) {
            val crash = state.crashSeconds + deltaSeconds
            state = if (crash >= CRASH_SECONDS) state.copy(phase = PilotPhase.COMPLETE, crashSeconds = CRASH_SECONDS)
            else state.copy(crashSeconds = crash)
            return
        }

        val dt = deltaSeconds.coerceAtMost(state.remainingSeconds)
        var next = state.copy(
            elapsedSeconds = (state.elapsedSeconds + dt).coerceAtMost(state.durationSeconds),
            pickupSeconds = state.pickupSeconds + dt
        )
        // Capped sub-steps keep steering and collision stable at any frame rate, and stop a fast
        // corridor from tunnelling a coin or a cloud between two frames.
        var remaining = dt
        while (remaining > 0f && next.phase == PilotPhase.FLYING) {
            val step = remaining.coerceAtMost(FIXED_STEP)
            remaining -= step
            next = tick(next, step)
        }
        if (next.phase == PilotPhase.FLYING && next.remainingSeconds <= 0f) {
            next = next.copy(phase = PilotPhase.COMPLETE)
        }
        state = next
    }

    private fun tick(value: PilotState, step: Float): PilotState {
        val speedStep = (value.elapsedSeconds * 1000f / config.pilot.speedStepMs).toInt()
        val speed = value.baseSpeed * (1f + SPEED_STEP_GAIN).pow(speedStep)
        val glide = 1f - exp(-STEER_RESPONSE * step)
        var next = updatePilotFrame(value, step, isCollectingCoin = false, hitObstacle = false).copy(
            speedStep = speedStep,
            currentSpeed = speed,
            scrollDistance = value.scrollDistance + speed * step,
            planeX = value.planeX + (value.targetX - value.planeX) * glide
        )
        next = spawnAhead(next)

        if (next.coins.any { it.isTouching(next) }) {
            val collected = next.coins.filter { it.isTouching(next) }
            next = next.copy(coins = next.coins - collected.toSet())
            repeat(collected.size) { next = next.collectCoin(next.planeX) }
        }

        val wallHit = next.planeX - PLANE_RADIUS < next.corridorLeft ||
            next.planeX + PLANE_RADIUS > next.corridorRight
        val cloudHit = next.clouds.any { it.isTouching(next) }
        return when {
            wallHit -> next.crash(PilotImpact.WALL)
            cloudHit -> next.crash(PilotImpact.CLOUD)
            else -> next
        }
    }

    private fun PilotCoin.isTouching(value: PilotState): Boolean =
        hypotUnits((value.planeX - x) * UNITS_PER_WIDTH, distance - value.scrollDistance) <
            COIN_RADIUS_UNITS + PLANE_RADIUS_UNITS

    private fun PilotCloud.isTouching(value: PilotState): Boolean =
        hypotUnits(
            (value.planeX - centerX(value.elapsedSeconds)) * UNITS_PER_WIDTH,
            distance - value.scrollDistance
        ) < radius * CLOUD_HITBOX + PLANE_RADIUS_UNITS

    private fun hypotUnits(dx: Float, dy: Float): Float = sqrt(dx * dx + dy * dy)

    /** Fills the corridor up to one screen beyond the plane and drops whatever fell behind it. */
    private fun spawnAhead(value: PilotState): PilotState {
        var clouds = value.clouds
        var coins = value.coins
        val horizon = value.scrollDistance + VIEW_UNITS + ROW_SPACING
        while (FIRST_ROW + nextRow * ROW_SPACING <= horizon) {
            val distance = FIRST_ROW + nextRow * ROW_SPACING
            val row = nextRow++
            val left = corridorLeft(distance, value.roundIndex)
            val right = corridorRight(distance, value.roundIndex)
            val slack = (right - left - SAFE_GAP).coerceAtLeast(0f)
            val lane = left + SAFE_GAP / 2f
            // The opening storm lines up with where the plane starts, so the first row teaches the
            // rule instead of ending the run on it.
            val gate = if (row == 0) START_X.coerceIn(lane, lane + slack)
            else lane + random.nextFloat() * slack

            clouds = clouds + cloud(distance, gate - SAFE_GAP / 2f, toLeft = true)
            if (value.roundIndex > 0 || random.nextFloat() < .6f) {
                clouds = clouds + cloud(distance, gate + SAFE_GAP / 2f, toLeft = false)
            }
            if (random.nextFloat() < config.pilot.coinRate) {
                // Kept sparse on purpose: with the streak bonus, a coin is worth about 45 points,
                // so a full clean run has to bank most of them to reach the 1,000 cap.
                val length = if (random.nextFloat() < .45f) 2 else 1
                // A trail curves, but never outside the passage the row guarantees: chasing every
                // coin has to stay survivable.
                val reach = SAFE_GAP / 2f - PLANE_RADIUS - CLOUD_SWAY
                val drift = (random.nextFloat() - .5f) * reach
                coins = coins + List(length) { index ->
                    val offset = (index - (length - 1) / 2f) * COIN_TRAIL_SPACING
                    PilotCoin(
                        id = nextId++,
                        distance = distance + offset,
                        x = (gate + (drift * index).coerceIn(-reach, reach))
                            .coerceIn(left + PLANE_RADIUS, right - PLANE_RADIUS)
                    )
                }
            }
        }
        // The loop runs on every sub-step, so it must not allocate when nothing entered or left.
        val tail = value.scrollDistance - TAIL_UNITS
        if (clouds.any { it.distance <= tail }) clouds = clouds.filter { it.distance > tail }
        if (coins.any { it.distance <= tail }) coins = coins.filter { it.distance > tail }
        return if (clouds === value.clouds && coins === value.coins) value
        else value.copy(clouds = clouds, coins = coins)
    }

    private fun cloud(distance: Float, edge: Float, toLeft: Boolean): PilotCloud {
        val radius = CLOUD_MIN_RADIUS + random.nextFloat() * (CLOUD_MAX_RADIUS - CLOUD_MIN_RADIUS)
        val half = radius * CLOUD_HITBOX / UNITS_PER_WIDTH
        return PilotCloud(
            id = nextId++,
            distance = distance + (random.nextFloat() - .5f) * ROW_SPACING * .3f,
            x = if (toLeft) edge - half else edge + half,
            radius = radius,
            sway = random.nextFloat() * 6.283f
        )
    }

    companion object {
        const val VIEW_UNITS = 10f
        const val UNITS_PER_WIDTH = 7f
        /** Corridor width divided by corridor height, so a world unit is square on screen. */
        const val ARENA_ASPECT = UNITS_PER_WIDTH / VIEW_UNITS
        const val PLANE_Y = .76f
        const val START_X = .5f
        const val PLANE_RADIUS_UNITS = .46f
        const val PLANE_RADIUS = PLANE_RADIUS_UNITS / UNITS_PER_WIDTH
        const val COIN_RADIUS_UNITS = .52f
        const val CLOUD_MIN_RADIUS = 1.05f
        const val CLOUD_MAX_RADIUS = 1.7f
        /** Clouds are fluffy, so the hitbox sits just inside the drawn sprite. */
        const val CLOUD_HITBOX = .85f
        const val CLOUD_SWAY = .014f
        const val ROW_SPACING = 6f
        /** Better than a screen of clear sky first, so take-off is not a reflex test. */
        const val FIRST_ROW = 13.5f
        const val COIN_TRAIL_SPACING = 1.15f
        const val TAIL_UNITS = 2f
        const val SPEED_STEP_GAIN = .18f
        const val STEER_RESPONSE = 11f
        const val FIXED_STEP = 1f / 90f
        const val CRASH_SECONDS = 1.25f
        const val PICKUP_SECONDS = .8f
        const val COIN_POINTS = 30
        const val STREAK_BONUS = 120
        const val STREAK_LENGTH = 8
        const val SURVIVAL_POINTS_PER_SECOND = 15f
        const val DEFAULT_SEED = 20260906L
        /**
         * Passage always left open between the clouds of a row: the plane, room for the sway, and
         * enough clearance that a steady hand still fits while the corridor itself drifts.
         */
        val SAFE_GAP = PLANE_RADIUS * 2f + .075f + CLOUD_SWAY * 2f

        private fun center(distance: Float): Float =
            .5f + .125f * sin(distance * .105f) + .045f * sin(distance * .268f + 1.7f)

        fun halfWidth(distance: Float, roundIndex: Int): Float =
            (.335f - roundIndex * .036f) - .045f * abs(sin(distance * .07f))

        fun corridorLeft(distance: Float, roundIndex: Int): Float {
            val half = halfWidth(distance, roundIndex)
            return (center(distance).coerceIn(half + .02f, 1f - half - .02f) - half)
        }

        fun corridorRight(distance: Float, roundIndex: Int): Float {
            val half = halfWidth(distance, roundIndex)
            return (center(distance).coerceIn(half + .02f, 1f - half - .02f) + half)
        }
    }
}
