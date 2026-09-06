package app.krafted.fantasyheroestournament.trial.zeus

import app.krafted.fantasyheroestournament.data.TournamentConfig
import kotlin.math.abs
import kotlin.math.sin

enum class ZeusPhase { READY, AIMING, CHARGING, STRIKE, COMPLETE }

data class ZeusStrikeResult(
    val power: Float,
    val bandStart: Float,
    val bandEnd: Float,
    val isPerfect: Boolean,
    val isHit: Boolean,
    val points: Int
)

/** The perfect zone occupies the central 20% of the whole target band. */
fun calculateZeusPoints(strikeRatio: Float, bandStart: Float, bandEnd: Float): ZeusStrikeResult {
    require(bandStart.isFinite() && bandEnd.isFinite() && bandStart in 0f..bandEnd && bandEnd <= 1f)
    val hit = strikeRatio.isFinite() && strikeRatio in bandStart..bandEnd
    val perfect = hit && abs(strikeRatio - (bandStart + bandEnd) / 2f) <=
        (bandEnd - bandStart) * 0.1f + 0.000001f
    return ZeusStrikeResult(strikeRatio, bandStart, bandEnd, perfect, hit,
        when { perfect -> 200; hit -> 130; else -> 0 })
}

data class ZeusState(
    val roundIndex: Int,
    val durationSeconds: Float,
    val totalThrows: Int,
    val phase: ZeusPhase = ZeusPhase.READY,
    val elapsedSeconds: Float = 0f,
    val power: Float = 0f,
    val bandStart: Float = 0f,
    val bandEnd: Float = 0f,
    val strikes: List<ZeusStrikeResult> = emptyList(),
    val feedbackSeconds: Float = 0f,
    val paused: Boolean = false
) {
    val score: Int get() = strikes.sumOf { it.points }.coerceAtMost(1000)
    val remainingSeconds: Float get() = (durationSeconds - elapsedSeconds).coerceAtLeast(0f)
    val lastStrike: ZeusStrikeResult? get() = strikes.lastOrNull()
    val perfectCount: Int get() = strikes.count { it.isPerfect }
    val canCharge: Boolean get() = !paused && phase == ZeusPhase.AIMING
    val timedOut: Boolean get() = remainingSeconds <= 0f
}

/** Pure Kotlin simulation. Time comes from the UI frame clock, never a background timer. */
class ZeusEngine(private val config: TournamentConfig, roundIndex: Int = 0) {
    var state: ZeusState = initialState(roundIndex)
        private set

    private fun initialState(roundIndex: Int): ZeusState {
        require(roundIndex in config.rounds.indices)
        return updateBand(ZeusState(roundIndex, config.rounds[roundIndex].trialDurationMs / 1000f,
            config.zeus.throws[roundIndex]))
    }

    fun selectRound(roundIndex: Int) {
        if (state.phase == ZeusPhase.READY || state.phase == ZeusPhase.COMPLETE) {
            state = initialState(roundIndex)
        }
    }

    fun start() {
        if (state.phase == ZeusPhase.READY) state = state.copy(phase = ZeusPhase.AIMING)
    }

    fun restart() { state = initialState(state.roundIndex) }

    fun hold() {
        if (state.canCharge) state = state.copy(phase = ZeusPhase.CHARGING, power = 0f)
    }

    fun cancelCharge() {
        if (state.phase == ZeusPhase.CHARGING) state = state.copy(phase = ZeusPhase.AIMING, power = 0f)
    }

    fun release(): ZeusStrikeResult? {
        if (state.paused || state.phase != ZeusPhase.CHARGING) return null
        val result = calculateZeusPoints(state.power, state.bandStart, state.bandEnd)
        state = state.copy(phase = ZeusPhase.STRIKE, strikes = state.strikes + result, feedbackSeconds = 0f)
        return result
    }

    fun pause() {
        if (state.phase == ZeusPhase.READY || state.phase == ZeusPhase.COMPLETE) return
        cancelCharge()
        state = state.copy(paused = true)
    }

    fun resume() { state = state.copy(paused = false) }

    fun advance(deltaSeconds: Float) {
        if (!deltaSeconds.isFinite() || deltaSeconds <= 0f || state.paused ||
            state.phase == ZeusPhase.READY || state.phase == ZeusPhase.COMPLETE) return

        val dt = deltaSeconds.coerceAtMost(state.remainingSeconds)
        var next = state.copy(elapsedSeconds = (state.elapsedSeconds + dt).coerceAtMost(state.durationSeconds))
        next = when (state.phase) {
            ZeusPhase.CHARGING -> updateBand(next.copy(power = (state.power + dt / CHARGE_SECONDS).coerceAtMost(1f)))
            ZeusPhase.STRIKE -> {
                val feedback = state.feedbackSeconds + dt
                if (feedback >= STRIKE_SECONDS) {
                    if (state.strikes.size >= state.totalThrows) next.copy(phase = ZeusPhase.COMPLETE)
                    else updateBand(next.copy(phase = ZeusPhase.AIMING, power = 0f, feedbackSeconds = 0f))
                } else next.copy(feedbackSeconds = feedback)
            }
            else -> updateBand(next)
        }
        if (next.remainingSeconds <= 0f) next = next.copy(phase = ZeusPhase.COMPLETE, power = 0f)
        state = next
    }

    private fun updateBand(value: ZeusState): ZeusState {
        val throwProgress = value.strikes.size.coerceAtMost(value.totalThrows - 1).toFloat() /
            (value.totalThrows - 1).coerceAtLeast(1)
        // Config bandStart/bandEnd describe widths on throw 1/5, not absolute positions.
        val width = (config.zeus.bandStart + (config.zeus.bandEnd - config.zeus.bandStart) * throwProgress) *
            (1f - value.roundIndex * 0.12f)
        val center = 0.52f + 0.22f * sin(value.elapsedSeconds * config.zeus.driftSpeed[value.roundIndex] * 1.25f)
        val boundedCenter = center.coerceIn(width / 2f, 1f - width / 2f)
        return value.copy(bandStart = boundedCenter - width / 2f, bandEnd = boundedCenter + width / 2f)
    }

    companion object {
        const val CHARGE_SECONDS = 1.45f
        const val STRIKE_SECONDS = 0.8f
    }
}
