package app.krafted.fantasyheroestournament.tournament

import app.krafted.fantasyheroestournament.data.IRecordsStore
import app.krafted.fantasyheroestournament.data.TrialConfigLoader
import app.krafted.fantasyheroestournament.data.UserRecords
import app.krafted.fantasyheroestournament.domain.Rank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeRecordsStore : IRecordsStore {
    val recordsStateFlow = MutableStateFlow(UserRecords())
    override val userRecordsFlow: StateFlow<UserRecords> = recordsStateFlow

    var lastRecordedRank: String? = null
    var lastRecordedGrandTotal: Int? = null
    var recordCompletedRunCallCount = 0

    override suspend fun recordCompletedRun(
        rank: String,
        grandTotal: Int,
        zeusScore: Int,
        pilotScore: Int,
        jokerScore: Int
    ) {
        recordCompletedRunCallCount++
        lastRecordedRank = rank
        lastRecordedGrandTotal = grandTotal
        recordsStateFlow.value = recordsStateFlow.value.copy(
            bestRank = rank,
            bestGrandTotal = maxOf(recordsStateFlow.value.bestGrandTotal, grandTotal),
            runsCompleted = recordsStateFlow.value.runsCompleted + 1
        )
    }

    override suspend fun setSoundOn(enabled: Boolean) {
        recordsStateFlow.value = recordsStateFlow.value.copy(soundOn = enabled)
    }

    override suspend fun setVibrateOn(enabled: Boolean) {
        recordsStateFlow.value = recordsStateFlow.value.copy(vibrateOn = enabled)
    }

    override suspend fun setTutorialSeen(seen: Boolean) {
        recordsStateFlow.value = recordsStateFlow.value.copy(tutorialSeen = seen)
    }

    override suspend fun resetRecords() {
        recordsStateFlow.value = UserRecords()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TournamentViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeRecordsStore: FakeRecordsStore
    private lateinit var viewModel: TournamentViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRecordsStore = FakeRecordsStore()
        viewModel = TournamentViewModel(
            recordsStore = fakeRecordsStore,
            tournamentConfig = TrialConfigLoader.defaultConfig,
            soundManager = null
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialState() {
        val state = viewModel.uiState.value
        assertEquals(TournamentScreen.MAIN_MENU, state.currentScreen)
        assertEquals(TournamentRound.QUALIFIER, state.currentRound)
        assertEquals(TrialType.ZEUS, state.activeTrial)
        assertEquals(1500, state.roundsData[TournamentRound.QUALIFIER]?.threshold)
        assertEquals(1900, state.roundsData[TournamentRound.SEMIFINAL]?.threshold)
        assertEquals(0, state.roundsData[TournamentRound.FINAL]?.threshold)
    }

    @Test
    fun testStartNewTournament() {
        viewModel.startNewTournament()

        val state = viewModel.uiState.value
        assertEquals(TournamentScreen.BRACKET, state.currentScreen)
        assertEquals(TournamentRound.QUALIFIER, state.currentRound)
        assertEquals(TrialType.ZEUS, state.activeTrial)
        assertTrue(state.isRunActive)
        assertEquals(0, state.grandTotalScore)
    }

    @Test
    fun testSelectTrialAndLaunchGameplay() {
        viewModel.startNewTournament()
        viewModel.selectTrial(TrialType.ZEUS)

        assertEquals(TournamentScreen.TRIAL_INTRO, viewModel.uiState.value.currentScreen)
        assertEquals(TrialType.ZEUS, viewModel.uiState.value.activeTrial)

        viewModel.launchTrialGameplay()

        assertEquals(TournamentScreen.TRIAL_GAMEPLAY, viewModel.uiState.value.currentScreen)
        assertEquals(RoundStatus.IN_PROGRESS, viewModel.uiState.value.roundsData[TournamentRound.QUALIFIER]?.status)
    }

    @Test
    fun testCompleteTrialScoringAndQualifierPass() {
        viewModel.startNewTournament()

        // Trial 1: Zeus (600)
        viewModel.selectTrial(TrialType.ZEUS)
        viewModel.launchTrialGameplay()
        viewModel.completeTrial(TrialType.ZEUS, 600, "3 Perfects")

        assertEquals(TournamentScreen.TRIAL_RESULT, viewModel.uiState.value.currentScreen)
        assertEquals(600, viewModel.uiState.value.roundsData[TournamentRound.QUALIFIER]?.zeusScore)

        viewModel.onTrialResultDismissed()

        // Auto advanced to PILOT, screen returned to BRACKET
        assertEquals(TrialType.PILOT, viewModel.uiState.value.activeTrial)
        assertEquals(TournamentScreen.BRACKET, viewModel.uiState.value.currentScreen)

        // Trial 2: Pilot (500)
        viewModel.selectTrial(TrialType.PILOT)
        viewModel.launchTrialGameplay()
        viewModel.completeTrial(TrialType.PILOT, 500)
        viewModel.onTrialResultDismissed()

        assertEquals(TrialType.JOKER, viewModel.uiState.value.activeTrial)

        // Trial 3: Joker (500) -> Total 1600 (>= 1500 threshold)
        viewModel.selectTrial(TrialType.JOKER)
        viewModel.launchTrialGameplay()
        viewModel.completeTrial(TrialType.JOKER, 500)

        val roundData = viewModel.uiState.value.roundsData[TournamentRound.QUALIFIER]
        assertNotNull(roundData)
        assertEquals(1600, roundData?.roundScore)
        assertTrue(roundData?.isPassed == true)
        assertEquals(RoundStatus.PASSED, roundData?.status)

        viewModel.onTrialResultDismissed()
        assertEquals(TournamentScreen.ROUND_RESULT, viewModel.uiState.value.currentScreen)
    }

    @Test
    fun testRoundFailureAndRetry() {
        viewModel.startNewTournament()

        // Qualifier total = 1000 (< 1500 threshold)
        viewModel.completeTrial(TrialType.ZEUS, 400)
        viewModel.completeTrial(TrialType.PILOT, 300)
        viewModel.completeTrial(TrialType.JOKER, 300)

        val roundData = viewModel.uiState.value.roundsData[TournamentRound.QUALIFIER]
        assertEquals(1000, roundData?.roundScore)
        assertFalse(roundData?.isPassed == true)
        assertEquals(RoundStatus.FAILED, roundData?.status)

        // Retry round
        viewModel.retryCurrentRound()

        val resetState = viewModel.uiState.value
        assertEquals(TournamentScreen.BRACKET, resetState.currentScreen)
        assertEquals(TrialType.ZEUS, resetState.activeTrial)
        assertNull(resetState.roundsData[TournamentRound.QUALIFIER]?.zeusScore)
        assertEquals(0, resetState.roundsData[TournamentRound.QUALIFIER]?.roundScore)
        assertEquals(RoundStatus.IN_PROGRESS, resetState.roundsData[TournamentRound.QUALIFIER]?.status)
    }

    @Test
    fun testAdvanceToNextRound() {
        viewModel.startNewTournament()
        viewModel.advanceToNextRound()

        val state = viewModel.uiState.value
        assertEquals(TournamentRound.SEMIFINAL, state.currentRound)
        assertEquals(TrialType.ZEUS, state.activeTrial)
        assertEquals(TournamentScreen.BRACKET, state.currentScreen)
    }

    @Test
    fun testTournamentCompletionAndRankCalculation() = runTest {
        viewModel.startNewTournament()

        // Round 1: Qualifier -> 3 x 950 = 2850
        viewModel.completeTrial(TrialType.ZEUS, 950)
        viewModel.onTrialResultDismissed()
        viewModel.completeTrial(TrialType.PILOT, 950)
        viewModel.onTrialResultDismissed()
        viewModel.completeTrial(TrialType.JOKER, 950)
        viewModel.onTrialResultDismissed()
        viewModel.advanceToNextRound()

        // Round 2: Semifinal -> 3 x 950 = 2850
        viewModel.completeTrial(TrialType.ZEUS, 950)
        viewModel.onTrialResultDismissed()
        viewModel.completeTrial(TrialType.PILOT, 950)
        viewModel.onTrialResultDismissed()
        viewModel.completeTrial(TrialType.JOKER, 950)
        viewModel.onTrialResultDismissed()
        viewModel.advanceToNextRound()

        // Round 3: Final -> 3 x 1000 = 3000 -> Grand Total = 8700 (>= 8550 -> CHAMPION)
        assertEquals(TournamentRound.FINAL, viewModel.uiState.value.currentRound)
        viewModel.completeTrial(TrialType.ZEUS, 1000)
        viewModel.onTrialResultDismissed()
        viewModel.completeTrial(TrialType.PILOT, 1000)
        viewModel.onTrialResultDismissed()
        viewModel.completeTrial(TrialType.JOKER, 1000)
        viewModel.onTrialResultDismissed()

        val finalState = viewModel.uiState.value
        assertEquals(8700, finalState.grandTotalScore)
        assertEquals(Rank.CHAMPION, finalState.finalRank)
        assertEquals(TournamentScreen.FINAL_CEREMONY, finalState.currentScreen)
        assertFalse(finalState.isRunActive)

        assertEquals(1, fakeRecordsStore.recordCompletedRunCallCount)
        assertEquals("CHAMPION", fakeRecordsStore.lastRecordedRank)
        assertEquals(8700, fakeRecordsStore.lastRecordedGrandTotal)
    }
}
