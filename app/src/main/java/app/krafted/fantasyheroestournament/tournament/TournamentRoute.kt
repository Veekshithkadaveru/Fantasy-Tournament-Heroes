package app.krafted.fantasyheroestournament.tournament

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import app.krafted.fantasyheroestournament.data.RecordsStore
import app.krafted.fantasyheroestournament.data.TournamentConfig
import app.krafted.fantasyheroestournament.data.TrialConfigLoader
import app.krafted.fantasyheroestournament.domain.LocalGameFeedback
import app.krafted.fantasyheroestournament.domain.Rank
import app.krafted.fantasyheroestournament.domain.SoundManager
import app.krafted.fantasyheroestournament.domain.rememberGameFeedback
import app.krafted.fantasyheroestournament.trial.joker.JokerTrialRoute
import app.krafted.fantasyheroestournament.trial.pilot.PilotTrialRoute
import app.krafted.fantasyheroestournament.trial.zeus.ZeusTrialRoute
import app.krafted.fantasyheroestournament.ui.bracket.BracketScreen
import app.krafted.fantasyheroestournament.ui.menu.MainMenuScreen
import app.krafted.fantasyheroestournament.ui.menu.RecordsScreen
import app.krafted.fantasyheroestournament.ui.menu.SettingsScreen
import app.krafted.fantasyheroestournament.ui.menu.SplashScreen
import app.krafted.fantasyheroestournament.ui.results.FinalCeremonyScreen
import app.krafted.fantasyheroestournament.ui.results.RoundResultScreen
import app.krafted.fantasyheroestournament.ui.results.TrialIntroScreen
import app.krafted.fantasyheroestournament.ui.results.TrialResultScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** How long a returning player's trial intro waits before entering on its own. */
private const val IntroAutoEnterSeconds = 6

/**
 * What is on screen, stripped of the numbers inside it. Scores change every
 * frame; this does not, so it can drive [AnimatedContent] without restarting a
 * transition on every point scored.
 */
private data class Scene(val screen: TournamentScreen, val session: TrialSession? = null)

@Composable
fun TournamentRoute(lifecycle: Lifecycle) {
    val context = LocalContext.current.applicationContext
    var config by remember { mutableStateOf<TournamentConfig?>(null) }
    LaunchedEffect(context) {
        config = withContext(Dispatchers.IO) { TrialConfigLoader.loadConfig(context) }
    }
    // The title card holds until the config is in hand, and survives rotation once shown.
    var titleShown by rememberSaveable { mutableStateOf(false) }
    val loadedConfig = config
    if (!titleShown || loadedConfig == null) {
        SplashScreen(ready = loadedConfig != null) { titleShown = true }
        return
    }

    val factory = remember(context, loadedConfig) {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(TournamentViewModel::class.java))
                @Suppress("UNCHECKED_CAST")
                return TournamentViewModel(RecordsStore(context), loadedConfig, SoundManager(context)) as T
            }
        }
    }
    val tournament: TournamentViewModel = viewModel(factory = factory)
    val state by tournament.uiState.collectAsState()
    val scene = Scene(state.currentScreen, state.activeSession.takeIf {
        state.currentScreen == TournamentScreen.TRIAL_GAMEPLAY
    })
    val feedback = rememberGameFeedback(tournament.soundManager,
        state.userRecords.soundOn, state.userRecords.vibrateOn)

    CompositionLocalProvider(LocalGameFeedback provides feedback) {
        AnimatedContent(targetState = scene, modifier = Modifier.fillMaxSize(),
            transitionSpec = { sceneTransition(initialState, targetState) },
            label = "tournament scene") { current ->
            when (current.screen) {
                // Splash is handled above; a state that somehow names it lands on the menu.
                TournamentScreen.SPLASH, TournamentScreen.MAIN_MENU -> MainMenuScreen(
                    records = state.userRecords,
                    runActive = state.isRunActive,
                    onStart = tournament::startNewTournament,
                    onResume = { tournament.navigateTo(TournamentScreen.BRACKET) },
                    onRecords = { tournament.navigateTo(TournamentScreen.RECORDS) },
                    onSettings = { tournament.navigateTo(TournamentScreen.SETTINGS) }
                )

                TournamentScreen.RECORDS -> RecordsScreen(
                    records = state.userRecords,
                    onBack = { tournament.navigateTo(TournamentScreen.MAIN_MENU) }
                )

                TournamentScreen.SETTINGS -> SettingsScreen(
                    records = state.userRecords,
                    onSoundChange = tournament::setSoundOn,
                    onVibrateChange = tournament::setVibrateOn,
                    onReset = tournament::resetRecords,
                    onBack = { tournament.navigateTo(TournamentScreen.MAIN_MENU) }
                )

                TournamentScreen.BRACKET -> BracketScreen(
                    state = state, config = tournament.tournamentConfig,
                    onStart = tournament::startNewTournament,
                    onPlay = tournament::selectTrial,
                    onAdvance = tournament::advanceToNextRound,
                    onRetry = tournament::retryCurrentRound,
                    onLeave = tournament::abandonTournament,
                    onMenu = { tournament.navigateTo(TournamentScreen.MAIN_MENU) }
                )

                // Starting another run resets the scorecard while the ceremony is
                // still fading out, so it keeps the run it was opened to celebrate.
                // The save status is deliberately live: it is the one thing here
                // that is still in flight when the screen arrives.
                TournamentScreen.FINAL_CEREMONY -> {
                    val awarded = remember { state.finalRank ?: Rank.BRONZE }
                    val ledger = remember { TournamentRound.entries.map { state.roundsData.getValue(it) } }
                    val total = remember { state.grandTotalScore }
                    val previousBest = remember { state.previousBestTotal }
                    FinalCeremonyScreen(
                        rank = awarded,
                        rounds = ledger,
                        grandTotal = total,
                        previousBest = previousBest,
                        saveStatus = state.recordSaveStatus,
                        onPlayAgain = tournament::startNewTournament,
                        onMenu = { tournament.navigateTo(TournamentScreen.MAIN_MENU) },
                        onRetrySaving = tournament::retrySavingRecords
                    )
                }

                TournamentScreen.TRIAL_INTRO -> TrialIntroScreen(
                    trial = state.activeTrial,
                    round = state.currentRound,
                    roundData = state.currentRoundData,
                    durationSeconds = ((tournament.tournamentConfig.rounds
                        .getOrNull(state.currentRound.roundIndex)?.trialDurationMs ?: 20_000L) / 1000).toInt(),
                    // A first-time player reads at their own pace; after that the run keeps moving.
                    autoEnterSeconds = if (state.userRecords.tutorialSeen) IntroAutoEnterSeconds else 0,
                    onBegin = {
                        tournament.setTutorialSeen(true)
                        tournament.launchTrialGameplay()
                    },
                    onBack = { tournament.navigateTo(TournamentScreen.BRACKET) }
                )

                TournamentScreen.TRIAL_GAMEPLAY -> current.session?.let { session ->
                    val finish: (TrialOutcome) -> Unit = { outcome ->
                        tournament.completeTrial(session, outcome.score, outcome.headline, outcome.stats)
                    }
                    key(session.id) {
                        when (session.trial) {
                            TrialType.ZEUS -> ZeusTrialRoute(lifecycle, session = session,
                                config = tournament.tournamentConfig, onTournamentComplete = finish)
                            TrialType.PILOT -> PilotTrialRoute(lifecycle, session = session,
                                config = tournament.tournamentConfig, onTournamentComplete = finish)
                            TrialType.JOKER -> JokerTrialRoute(lifecycle, session = session,
                                config = tournament.tournamentConfig, onTournamentComplete = finish)
                        }
                    }
                }

                TournamentScreen.TRIAL_RESULT -> state.lastCompletedTrialResult?.let { result ->
                    TrialResultScreen(
                        result = result,
                        round = state.currentRound,
                        roundData = state.currentRoundData,
                        grandTotal = state.grandTotalScore,
                        // Only a genuine improvement counts; on a first run every
                        // trial would otherwise beat a stored zero.
                        personalBest = bestFor(result.trialType, state).let { it > 0 && result.score > it },
                        onContinue = tournament::onTrialResultDismissed
                    )
                }

                // Advancing moves the current round and retrying clears its scores,
                // both while this screen is still fading out. It keeps the verdict it
                // was opened with rather than repainting as the next empty scorecard.
                TournamentScreen.ROUND_RESULT -> {
                    val decided = remember { state.currentRound }
                    val scorecard = remember { state.currentRoundData }
                    val bankedTotal = remember { state.grandTotalScore }
                    RoundResultScreen(
                        round = decided,
                        data = scorecard,
                        grandTotal = bankedTotal,
                        nextRound = TournamentRound.entries.getOrNull(decided.roundIndex + 1),
                        onAdvance = tournament::advanceToNextRound,
                        onRetry = tournament::retryCurrentRound,
                        onViewBracket = { tournament.navigateTo(TournamentScreen.BRACKET) }
                    )
                }
            }
        }
    }
}

/** Records and settings are stepped aside into, not descended through. */
private val SidePages = setOf(TournamentScreen.RECORDS, TournamentScreen.SETTINGS)

/**
 * How one scene gives way to the next. The flow has cuts of genuinely different
 * weight — stepping into an arena, coming back out holding a score, and the one
 * long fade into the ceremony — and they should not all feel like a crossfade.
 */
private fun AnimatedContentTransitionScope<Scene>.sceneTransition(from: Scene, to: Scene): ContentTransform {
    val leaving = from.screen
    val entering = to.screen
    return when {
        // Into the arena: the trial comes forward as the page it replaces falls back.
        entering == TournamentScreen.TRIAL_GAMEPLAY ->
            (fadeIn(tween(280, delayMillis = 140)) +
                scaleIn(tween(560, easing = FastOutSlowInEasing), .88f)) togetherWith
                (fadeOut(tween(220)) + scaleOut(tween(380), targetScale = 1.08f))

        // Out of it: the score settles over the trial's last held frame.
        leaving == TournamentScreen.TRIAL_GAMEPLAY ->
            (fadeIn(tween(420, delayMillis = 180)) +
                slideInVertically(tween(560, 180, FastOutSlowInEasing)) { it / 6 }) togetherWith
                fadeOut(tween(420))

        // The ceremony is the one cut in the app allowed to take its time. Its
        // own curtain waits out this fade before the medal is struck.
        entering == TournamentScreen.FINAL_CEREMONY ->
            fadeIn(tween(600, delayMillis = 260)) togetherWith fadeOut(tween(320))

        entering in SidePages ->
            (fadeIn(tween(300)) +
                slideInHorizontally(tween(400, easing = FastOutSlowInEasing)) { it / 5 }) togetherWith
                (fadeOut(tween(220)) + slideOutHorizontally(tween(340)) { -it / 8 })

        leaving in SidePages ->
            (fadeIn(tween(300)) +
                slideInHorizontally(tween(400, easing = FastOutSlowInEasing)) { -it / 8 }) togetherWith
                (fadeOut(tween(220)) + slideOutHorizontally(tween(340)) { it / 5 })

        else -> (fadeIn(tween(360, delayMillis = 90)) + scaleIn(tween(440), initialScale = .975f)) togetherWith
            (fadeOut(tween(240)) + scaleOut(tween(300), targetScale = 1.015f))
    } using SizeTransform(clip = false)
}

private fun bestFor(trial: TrialType, state: TournamentUiState): Int = when (trial) {
    TrialType.ZEUS -> state.userRecords.bestZeus
    TrialType.PILOT -> state.userRecords.bestPilot
    TrialType.JOKER -> state.userRecords.bestJoker
}
