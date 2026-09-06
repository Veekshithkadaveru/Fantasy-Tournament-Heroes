package app.krafted.fantasyheroestournament.tournament

import app.krafted.fantasyheroestournament.data.UserRecords
import app.krafted.fantasyheroestournament.domain.Rank

enum class TournamentRound(val displayName: String, val roundIndex: Int, val defaultThreshold: Int) {
    QUALIFIER("Qualifier", 0, 1500),
    SEMIFINAL("Semifinal", 1, 1900),
    FINAL("Final", 2, 0)
}

enum class TrialType(val displayName: String, val subtitle: String, val trialIndex: Int) {
    ZEUS("Zeus", "Trial of Strength", 0),
    PILOT("Pilot", "Trial of Flight", 1),
    JOKER("Joker", "Trial of Chaos", 2)
}

enum class RoundStatus {
    NOT_STARTED,
    IN_PROGRESS,
    PASSED,
    FAILED
}

enum class TournamentScreen {
    SPLASH,
    MAIN_MENU,
    BRACKET,
    TRIAL_INTRO,
    TRIAL_GAMEPLAY,
    TRIAL_RESULT,
    ROUND_RESULT,
    FINAL_CEREMONY,
    RECORDS,
    SETTINGS
}

data class TrialResultData(
    val trialType: TrialType,
    val score: Int,
    val details: String = ""
)

data class RoundResultData(
    val round: TournamentRound,
    val threshold: Int,
    val status: RoundStatus = RoundStatus.NOT_STARTED,
    val zeusScore: Int? = null,
    val pilotScore: Int? = null,
    val jokerScore: Int? = null
) {
    val roundScore: Int get() = (zeusScore ?: 0) + (pilotScore ?: 0) + (jokerScore ?: 0)
    val isPassed: Boolean get() = roundScore >= threshold
    val isCompleted: Boolean get() = zeusScore != null && pilotScore != null && jokerScore != null
}

data class TournamentUiState(
    val currentScreen: TournamentScreen = TournamentScreen.MAIN_MENU,
    val currentRound: TournamentRound = TournamentRound.QUALIFIER,
    val activeTrial: TrialType = TrialType.ZEUS,
    val roundsData: Map<TournamentRound, RoundResultData> = mapOf(
        TournamentRound.QUALIFIER to RoundResultData(TournamentRound.QUALIFIER, TournamentRound.QUALIFIER.defaultThreshold),
        TournamentRound.SEMIFINAL to RoundResultData(TournamentRound.SEMIFINAL, TournamentRound.SEMIFINAL.defaultThreshold),
        TournamentRound.FINAL to RoundResultData(TournamentRound.FINAL, TournamentRound.FINAL.defaultThreshold)
    ),
    val lastCompletedTrialResult: TrialResultData? = null,
    val finalRank: Rank? = null,
    val isRunActive: Boolean = false,
    val userRecords: UserRecords = UserRecords()
) {
    val grandTotalScore: Int
        get() = roundsData.values.sumOf { it.roundScore }
}
