package app.krafted.fantasyheroestournament.trial.zeus

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.krafted.fantasyheroestournament.data.TournamentConfig
import app.krafted.fantasyheroestournament.tournament.TrialSession
import app.krafted.fantasyheroestournament.data.RecordsStore
import app.krafted.fantasyheroestournament.data.TrialConfigLoader
import app.krafted.fantasyheroestournament.data.UserRecords
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ZeusViewModel(application: Application) : AndroidViewModel(application) {
    private var engine: ZeusEngine? = null
    private var tournamentSessionId: Long? = null
    private val mutableState = MutableStateFlow<ZeusState?>(null)
    val state = mutableState.asStateFlow()
    val preferences = RecordsStore(application).userRecordsFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), UserRecords())

    init {
        viewModelScope.launch {
            val config = withContext(Dispatchers.IO) { TrialConfigLoader.loadConfig(application) }
            if (tournamentSessionId == null) {
                engine = ZeusEngine(config)
                mutableState.value = engine?.state
            }
        }
    }

    /** Retained across rotation, reset only for a genuinely new tournament attempt. */
    fun prepareTournament(session: TrialSession, config: TournamentConfig) {
        if (tournamentSessionId == session.id) return
        tournamentSessionId = session.id
        engine = ZeusEngine(config, roundIndex = session.round.roundIndex)
        mutableState.value = engine?.state
    }

    private fun change(action: ZeusEngine.() -> Unit) {
        engine?.let { it.action(); mutableState.value = it.state }
    }

    fun frame(seconds: Float) = change { advance(seconds) }
    fun start() = change { start() }
    fun hold() = change { hold() }
    fun release() = change { release() }
    fun cancelCharge() = change { cancelCharge() }
    fun pause() = change { pause() }
    fun resume() = change { resume() }
    fun restart() = change { restart() }
    fun selectRound(index: Int) = change { selectRound(index) }
}
