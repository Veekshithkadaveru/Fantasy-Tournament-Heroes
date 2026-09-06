package app.krafted.fantasyheroestournament.trial.joker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

class JokerViewModel(application: Application) : AndroidViewModel(application) {
    private var engine: RuleEngine? = null
    private val mutableState = MutableStateFlow<JokerState?>(null)
    val state = mutableState.asStateFlow()
    val preferences = RecordsStore(application).userRecordsFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), UserRecords())

    init {
        viewModelScope.launch {
            val config = withContext(Dispatchers.IO) { TrialConfigLoader.loadConfig(application) }
            engine = RuleEngine(config, seed = System.currentTimeMillis())
            mutableState.value = engine?.state
        }
    }

    private fun change(action: RuleEngine.() -> Unit) {
        engine?.let { it.action(); mutableState.value = it.state }
    }

    fun frame(seconds: Float) = change { advance(seconds) }
    fun start() = change { start() }
    fun tap(index: Int) = change { tap(index) }
    fun pause() = change { pause() }
    fun resume() = change { resume() }
    fun restart() = change { restart() }
    fun selectRound(index: Int) = change { selectRound(index) }
}
