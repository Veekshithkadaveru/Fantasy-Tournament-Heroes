package app.krafted.fantasyheroestournament.tournament

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.krafted.fantasyheroestournament.data.RecordsStore
import app.krafted.fantasyheroestournament.data.TournamentConfig
import app.krafted.fantasyheroestournament.data.TrialConfigLoader
import app.krafted.fantasyheroestournament.domain.RankCalculator
import app.krafted.fantasyheroestournament.domain.SoundManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import app.krafted.fantasyheroestournament.data.IRecordsStore

class TournamentViewModel(
    private val recordsStore: IRecordsStore,
    val tournamentConfig: TournamentConfig = TrialConfigLoader.defaultConfig,
    private val soundManager: SoundManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(createInitialState(tournamentConfig))
    val uiState: StateFlow<TournamentUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            recordsStore.userRecordsFlow.collectLatest { records ->
                _uiState.update { currentState ->
                    currentState.copy(userRecords = records)
                }
            }
        }
    }

    fun navigateTo(screen: TournamentScreen) {
        _uiState.update { it.copy(currentScreen = screen) }
    }

    fun startNewTournament() {
        val freshRounds = createInitialRoundsData(tournamentConfig)
        _uiState.update { currentState ->
            currentState.copy(
                currentScreen = TournamentScreen.BRACKET,
                currentRound = TournamentRound.QUALIFIER,
                activeTrial = TrialType.ZEUS,
                roundsData = freshRounds,
                lastCompletedTrialResult = null,
                finalRank = null,
                isRunActive = true
            )
        }
    }

    fun selectTrial(trialType: TrialType) {
        _uiState.update { currentState ->
            currentState.copy(
                activeTrial = trialType,
                currentScreen = TournamentScreen.TRIAL_INTRO
            )
        }
    }

    fun launchTrialGameplay() {
        _uiState.update { currentState ->
            val roundData = currentState.roundsData[currentState.currentRound]
            val updatedRounds = if (roundData?.status == RoundStatus.NOT_STARTED) {
                currentState.roundsData + (currentState.currentRound to roundData.copy(status = RoundStatus.IN_PROGRESS))
            } else {
                currentState.roundsData
            }
            currentState.copy(
                roundsData = updatedRounds,
                currentScreen = TournamentScreen.TRIAL_GAMEPLAY
            )
        }
    }

    fun completeTrial(trialType: TrialType, rawScore: Int, details: String = "") {
        val score = rawScore.coerceIn(0, 1000)
        _uiState.update { currentState ->
            val currentRoundData = currentState.roundsData[currentState.currentRound]
                ?: RoundResultData(currentState.currentRound, getThresholdForRound(currentState.currentRound))

            val updatedRoundData = when (trialType) {
                TrialType.ZEUS -> currentRoundData.copy(zeusScore = score)
                TrialType.PILOT -> currentRoundData.copy(pilotScore = score)
                TrialType.JOKER -> currentRoundData.copy(jokerScore = score)
            }

            val finalRoundData = if (updatedRoundData.isCompleted) {
                val passed = updatedRoundData.isPassed || currentState.currentRound == TournamentRound.FINAL
                updatedRoundData.copy(status = if (passed) RoundStatus.PASSED else RoundStatus.FAILED)
            } else {
                updatedRoundData.copy(status = RoundStatus.IN_PROGRESS)
            }

            val updatedMap = currentState.roundsData + (currentState.currentRound to finalRoundData)
            val trialResult = TrialResultData(trialType = trialType, score = score, details = details)

            currentState.copy(
                roundsData = updatedMap,
                lastCompletedTrialResult = trialResult,
                currentScreen = TournamentScreen.TRIAL_RESULT
            )
        }

        val soundEnabled = uiState.value.userRecords.soundOn
        val vibrateEnabled = uiState.value.userRecords.vibrateOn
        if (score >= 500) {
            soundManager?.playSuccessTone(soundEnabled)
        } else {
            soundManager?.playFailureTone(soundEnabled)
        }
        soundManager?.vibrate(vibrateEnabled, 100)
    }

    fun onTrialResultDismissed() {
        val currentState = _uiState.value
        val roundData = currentState.roundsData[currentState.currentRound]

        if (roundData != null && roundData.isCompleted) {
            if (currentState.currentRound == TournamentRound.FINAL) {
                val grandTotal = currentState.grandTotalScore
                val finalRank = RankCalculator.calculateRank(grandTotal)

                val bestZeusInRun = currentState.roundsData.values.mapNotNull { it.zeusScore }.maxOrNull() ?: 0
                val bestPilotInRun = currentState.roundsData.values.mapNotNull { it.pilotScore }.maxOrNull() ?: 0
                val bestJokerInRun = currentState.roundsData.values.mapNotNull { it.jokerScore }.maxOrNull() ?: 0

                viewModelScope.launch {
                    recordsStore.recordCompletedRun(
                        rank = finalRank.name,
                        grandTotal = grandTotal,
                        zeusScore = bestZeusInRun,
                        pilotScore = bestPilotInRun,
                        jokerScore = bestJokerInRun
                    )
                }

                _uiState.update {
                    it.copy(
                        finalRank = finalRank,
                        isRunActive = false,
                        currentScreen = TournamentScreen.FINAL_CEREMONY
                    )
                }
            } else {
                _uiState.update { it.copy(currentScreen = TournamentScreen.ROUND_RESULT) }
            }
        } else {
            val nextTrial = getNextTrial(currentState.activeTrial)
            _uiState.update {
                it.copy(
                    activeTrial = nextTrial,
                    currentScreen = TournamentScreen.BRACKET
                )
            }
        }
    }

    fun advanceToNextRound() {
        val currentState = _uiState.value
        val nextRound = getNextRound(currentState.currentRound) ?: return

        _uiState.update { state ->
            val updatedRounds = state.roundsData.toMutableMap()
            val existingNext = updatedRounds[nextRound]
                ?: RoundResultData(nextRound, getThresholdForRound(nextRound))
            updatedRounds[nextRound] = existingNext.copy(status = RoundStatus.IN_PROGRESS)

            state.copy(
                currentRound = nextRound,
                activeTrial = TrialType.ZEUS,
                roundsData = updatedRounds,
                currentScreen = TournamentScreen.BRACKET
            )
        }
    }

    fun retryCurrentRound() {
        val currentState = _uiState.value
        val round = currentState.currentRound

        _uiState.update { state ->
            val updatedRounds = state.roundsData.toMutableMap()
            updatedRounds[round] = RoundResultData(
                round = round,
                threshold = getThresholdForRound(round),
                status = RoundStatus.IN_PROGRESS,
                zeusScore = null,
                pilotScore = null,
                jokerScore = null
            )

            state.copy(
                activeTrial = TrialType.ZEUS,
                roundsData = updatedRounds,
                currentScreen = TournamentScreen.BRACKET
            )
        }
    }

    fun setSoundOn(enabled: Boolean) {
        viewModelScope.launch { recordsStore.setSoundOn(enabled) }
    }

    fun setVibrateOn(enabled: Boolean) {
        viewModelScope.launch { recordsStore.setVibrateOn(enabled) }
    }

    fun setTutorialSeen(seen: Boolean) {
        viewModelScope.launch { recordsStore.setTutorialSeen(seen) }
    }

    fun resetRecords() {
        viewModelScope.launch { recordsStore.resetRecords() }
    }

    private fun getThresholdForRound(round: TournamentRound): Int {
        val roundConfig = tournamentConfig.rounds.find { it.id.equals(round.name, ignoreCase = true) }
        return roundConfig?.threshold ?: round.defaultThreshold
    }

    private fun getNextTrial(current: TrialType): TrialType = when (current) {
        TrialType.ZEUS -> TrialType.PILOT
        TrialType.PILOT -> TrialType.JOKER
        TrialType.JOKER -> TrialType.ZEUS
    }

    private fun getNextRound(current: TournamentRound): TournamentRound? = when (current) {
        TournamentRound.QUALIFIER -> TournamentRound.SEMIFINAL
        TournamentRound.SEMIFINAL -> TournamentRound.FINAL
        TournamentRound.FINAL -> null
    }

    companion object {
        private fun createInitialRoundsData(config: TournamentConfig): Map<TournamentRound, RoundResultData> {
            val qualifierThresh = config.rounds.find { it.id.equals("QUALIFIER", true) }?.threshold ?: TournamentRound.QUALIFIER.defaultThreshold
            val semifinalThresh = config.rounds.find { it.id.equals("SEMIFINAL", true) }?.threshold ?: TournamentRound.SEMIFINAL.defaultThreshold
            val finalThresh = config.rounds.find { it.id.equals("FINAL", true) }?.threshold ?: TournamentRound.FINAL.defaultThreshold

            return mapOf(
                TournamentRound.QUALIFIER to RoundResultData(TournamentRound.QUALIFIER, qualifierThresh),
                TournamentRound.SEMIFINAL to RoundResultData(TournamentRound.SEMIFINAL, semifinalThresh),
                TournamentRound.FINAL to RoundResultData(TournamentRound.FINAL, finalThresh)
            )
        }

        private fun createInitialState(config: TournamentConfig) = TournamentUiState(
            currentScreen = TournamentScreen.MAIN_MENU,
            currentRound = TournamentRound.QUALIFIER,
            activeTrial = TrialType.ZEUS,
            roundsData = createInitialRoundsData(config)
        )
    }
}
