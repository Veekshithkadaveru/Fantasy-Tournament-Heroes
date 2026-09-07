package app.krafted.fantasyheroestournament.tournament

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import app.krafted.fantasyheroestournament.R
import app.krafted.fantasyheroestournament.data.RecordsStore
import app.krafted.fantasyheroestournament.data.TournamentConfig
import app.krafted.fantasyheroestournament.data.TrialConfigLoader
import app.krafted.fantasyheroestournament.domain.SoundManager
import app.krafted.fantasyheroestournament.trial.joker.JokerTrialRoute
import app.krafted.fantasyheroestournament.trial.pilot.PilotTrialRoute
import app.krafted.fantasyheroestournament.trial.zeus.ZeusTrialRoute
import app.krafted.fantasyheroestournament.ui.bracket.BracketScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun TournamentRoute(lifecycle: Lifecycle) {
    val context = LocalContext.current.applicationContext
    var config by remember { mutableStateOf<TournamentConfig?>(null) }
    LaunchedEffect(context) {
        config = withContext(Dispatchers.IO) { TrialConfigLoader.loadConfig(context) }
    }
    val loadedConfig = config
    if (loadedConfig == null) {
        Box(Modifier.fillMaxSize().background(Color(0xFF070D1B)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                CircularProgressIndicator(color = Color(0xFFEBC781))
                Text(stringResource(R.string.tournament_loading), color = Color(0xFFEBC781))
            }
        }
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

    // C2 uses the trials' existing ready/result presentations. Dedicated C3 screens can take
    // over these transitions later without changing the tournament's scoring contract.
    AnimatedContent(targetState = state.activeSession, modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            (fadeIn(tween(360, delayMillis = 80)) + scaleIn(tween(440), initialScale = .975f)) togetherWith
                fadeOut(tween(220))
        }, label = "tournament scene") { session ->
        if (session == null) {
            BracketScreen(
                state = state, config = tournament.tournamentConfig,
                onStart = tournament::startNewTournament,
                onPlay = { trial ->
                    tournament.selectTrial(trial)
                    tournament.launchTrialGameplay()
                },
                onAdvance = tournament::advanceToNextRound,
                onRetry = tournament::retryCurrentRound,
                onLeave = tournament::abandonTournament,
                onRetrySaving = tournament::retrySavingRecords
            )
        } else {
            val finish: (Int) -> Unit = { score ->
                if (tournament.completeTrial(session, score)) tournament.onTrialResultDismissed()
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
    }
}
