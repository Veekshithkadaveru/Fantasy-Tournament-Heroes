package app.krafted.fantasyheroestournament.tournament

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.krafted.fantasyheroestournament.data.IRecordsStore
import app.krafted.fantasyheroestournament.data.TournamentConfig
import app.krafted.fantasyheroestournament.data.TrialConfigLoader
import app.krafted.fantasyheroestournament.domain.RankCalculator
import app.krafted.fantasyheroestournament.domain.SoundManager
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TournamentViewModel(
    private val recordsStore: IRecordsStore,
    val tournamentConfig: TournamentConfig = TrialConfigLoader.defaultConfig,
    private val soundManager: SoundManager? = null
) : ViewModel() {
    private val sequence = AtomicLong()
    private val _uiState = MutableStateFlow(TournamentUiState(roundsData = createInitialRoundsData()))
    val uiState: StateFlow<TournamentUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            recordsStore.userRecordsFlow.collectLatest { records ->
                _uiState.update { it.copy(userRecords = records) }
            }
        }
    }

    /** Navigation cannot manufacture gameplay, a result, or a completed tournament. */
    fun navigateTo(screen: TournamentScreen) {
        transition { state ->
            val allowed = when (screen) {
                TournamentScreen.MAIN_MENU, TournamentScreen.RECORDS, TournamentScreen.SETTINGS ->
                    state.currentScreen in setOf(TournamentScreen.MAIN_MENU, TournamentScreen.RECORDS,
                        TournamentScreen.SETTINGS, TournamentScreen.BRACKET, TournamentScreen.FINAL_CEREMONY)
                TournamentScreen.BRACKET -> state.isRunActive && state.currentScreen in setOf(
                    TournamentScreen.MAIN_MENU, TournamentScreen.TRIAL_INTRO, TournamentScreen.ROUND_RESULT)
                else -> false
            }
            if (allowed) state.copy(currentScreen = screen) else state
        }
    }

    fun startNewTournament() {
        val runId = sequence.incrementAndGet()
        transition { state ->
            // A run must be explicitly abandoned before a new one can replace it.
            if (state.isRunActive || state.recordSaveStatus == RecordSaveStatus.SAVING) state
            else TournamentUiState(
                currentScreen = TournamentScreen.BRACKET,
                roundsData = createInitialRoundsData(),
                runId = runId,
                isRunActive = true,
                userRecords = state.userRecords
            )
        }
    }

    fun abandonTournament() {
        transition { state ->
            if (!state.isRunActive) state else TournamentUiState(
                roundsData = createInitialRoundsData(), runId = state.runId,
                userRecords = state.userRecords
            )
        }
    }

    fun selectTrial(trialType: TrialType) {
        transition { state ->
            if (!state.isRunActive || state.currentScreen != TournamentScreen.BRACKET ||
                state.currentRoundData.nextTrial != trialType || state.currentRoundData.isCompleted) state
            else state.copy(activeTrial = trialType, currentScreen = TournamentScreen.TRIAL_INTRO)
        }
    }

    fun launchTrialGameplay() {
        val sessionId = sequence.incrementAndGet()
        transition { state ->
            val round = state.currentRoundData
            if (!state.isRunActive || state.currentScreen != TournamentScreen.TRIAL_INTRO ||
                round.nextTrial != state.activeTrial || round.isCompleted) state
            else state.copy(
                roundsData = state.roundsData + (state.currentRound to round.copy(status = RoundStatus.IN_PROGRESS)),
                activeSession = TrialSession(sessionId, state.currentRound, state.activeTrial),
                lastCompletedTrialResult = null,
                currentScreen = TournamentScreen.TRIAL_GAMEPLAY
            )
        }
    }

    /** The UI must return the session it launched, never infer it from a newer state. */
    fun completeTrial(session: TrialSession, rawScore: Int, details: String = ""): Boolean {
        val score = rawScore.coerceIn(0, 1000)
        val accepted = transition { state ->
            val round = state.currentRoundData
            if (!state.isRunActive || state.currentScreen != TournamentScreen.TRIAL_GAMEPLAY ||
                state.activeSession != session || state.currentRound != session.round ||
                state.activeTrial != session.trial || round.nextTrial != session.trial ||
                round.status != RoundStatus.IN_PROGRESS) return@transition state

            val scored = when (session.trial) {
                TrialType.ZEUS -> round.copy(zeusScore = score)
                TrialType.PILOT -> round.copy(pilotScore = score)
                TrialType.JOKER -> round.copy(jokerScore = score)
            }
            val status = when {
                !scored.isCompleted -> RoundStatus.IN_PROGRESS
                scored.isPassed -> RoundStatus.PASSED
                else -> RoundStatus.FAILED
            }
            state.copy(
                roundsData = state.roundsData + (state.currentRound to scored.copy(status = status)),
                activeSession = null,
                lastCompletedTrialResult = TrialResultData(session.trial, score, details),
                currentScreen = TournamentScreen.TRIAL_RESULT
            )
        } ?: return false
        if (score >= 500) soundManager?.playSuccessTone(accepted.userRecords.soundOn)
        else soundManager?.playFailureTone(accepted.userRecords.soundOn)
        soundManager?.vibrate(accepted.userRecords.vibrateOn)
        return true
    }

    // Synchronous callers still receive the same state validation as the session-based UI.
    fun completeTrial(trialType: TrialType, rawScore: Int, details: String = "") {
        val session = _uiState.value.activeSession?.takeIf { it.trial == trialType } ?: return
        completeTrial(session, rawScore, details)
    }

    fun onTrialResultDismissed() {
        val accepted = transition { state ->
            if (!state.isRunActive || state.currentScreen != TournamentScreen.TRIAL_RESULT ||
                state.lastCompletedTrialResult == null) return@transition state
            val round = state.currentRoundData
            when {
                !round.isCompleted -> state.copy(
                    activeTrial = round.nextTrial ?: return@transition state,
                    currentScreen = TournamentScreen.BRACKET
                )
                state.currentRound != TournamentRound.FINAL -> state.copy(currentScreen = TournamentScreen.ROUND_RESULT)
                state.roundsData.values.all { it.isCompleted && it.isPassed } -> state.copy(
                    finalRank = RankCalculator.calculateRank(state.grandTotalScore),
                    isRunActive = false,
                    recordSaveStatus = RecordSaveStatus.SAVING,
                    currentScreen = TournamentScreen.FINAL_CEREMONY
                )
                else -> state
            }
        } ?: return
        // Claim completion atomically before starting an effect. Repeated taps cannot save twice.
        if (accepted.recordSaveStatus == RecordSaveStatus.SAVING) saveCompletedRun(accepted)
    }

    fun advanceToNextRound() {
        transition { state ->
            val round = state.currentRoundData
            if (!state.isRunActive || state.currentScreen !in setOf(TournamentScreen.ROUND_RESULT, TournamentScreen.BRACKET) ||
                !round.isCompleted || !round.isPassed || round.status != RoundStatus.PASSED) return@transition state
            val next = TournamentRound.entries.getOrNull(state.currentRound.roundIndex + 1) ?: return@transition state
            state.copy(
                currentRound = next, activeTrial = TrialType.ZEUS,
                lastCompletedTrialResult = null, activeSession = null,
                currentScreen = TournamentScreen.BRACKET
            )
        }
    }

    fun retryCurrentRound() {
        transition { state ->
            if (!state.isRunActive || state.currentScreen !in setOf(TournamentScreen.ROUND_RESULT, TournamentScreen.BRACKET) ||
                !state.currentRoundData.isCompleted || state.currentRoundData.status != RoundStatus.FAILED) state
            else state.copy(
                activeTrial = TrialType.ZEUS,
                roundsData = state.roundsData + (state.currentRound to RoundResultData(
                    state.currentRound, getThresholdForRound(state.currentRound))),
                lastCompletedTrialResult = null, activeSession = null, finalRank = null,
                currentScreen = TournamentScreen.BRACKET
            )
        }
    }

    fun retrySavingRecords() {
        val accepted = transition { state ->
            if (state.isRunActive || state.finalRank == null || state.recordSaveStatus != RecordSaveStatus.FAILED) state
            else state.copy(recordSaveStatus = RecordSaveStatus.SAVING)
        } ?: return
        saveCompletedRun(accepted)
    }

    private fun saveCompletedRun(completed: TournamentUiState) {
        viewModelScope.launch {
            val status = try {
                recordsStore.recordCompletedRun(
                    rank = requireNotNull(completed.finalRank).name,
                    grandTotal = completed.grandTotalScore,
                    zeusScore = completed.roundsData.values.mapNotNull { it.zeusScore }.maxOrNull() ?: 0,
                    pilotScore = completed.roundsData.values.mapNotNull { it.pilotScore }.maxOrNull() ?: 0,
                    jokerScore = completed.roundsData.values.mapNotNull { it.jokerScore }.maxOrNull() ?: 0
                )
                RecordSaveStatus.SAVED
            } catch (_: IOException) {
                RecordSaveStatus.FAILED
            }
            _uiState.update { if (it.runId == completed.runId) it.copy(recordSaveStatus = status) else it }
        }
    }

    fun setSoundOn(enabled: Boolean) { viewModelScope.launch { recordsStore.setSoundOn(enabled) } }
    fun setVibrateOn(enabled: Boolean) { viewModelScope.launch { recordsStore.setVibrateOn(enabled) } }
    fun setTutorialSeen(seen: Boolean) { viewModelScope.launch { recordsStore.setTutorialSeen(seen) } }
    fun resetRecords() { viewModelScope.launch { recordsStore.resetRecords() } }

    /** Pure transitions may be retried by CAS; effects run only after a successful change. */
    private fun transition(reduce: (TournamentUiState) -> TournamentUiState): TournamentUiState? {
        while (true) {
            val before = _uiState.value
            val after = reduce(before)
            if (after == before) return null
            if (_uiState.compareAndSet(before, after)) return after
        }
    }

    private fun getThresholdForRound(round: TournamentRound): Int =
        if (round == TournamentRound.FINAL) 0
        else tournamentConfig.rounds.find { it.id.equals(round.name, ignoreCase = true) }?.threshold ?: round.defaultThreshold

    private fun createInitialRoundsData(): Map<TournamentRound, RoundResultData> =
        TournamentRound.entries.associateWith { RoundResultData(it, getThresholdForRound(it)) }

    override fun onCleared() {
        soundManager?.release()
        super.onCleared()
    }
}
