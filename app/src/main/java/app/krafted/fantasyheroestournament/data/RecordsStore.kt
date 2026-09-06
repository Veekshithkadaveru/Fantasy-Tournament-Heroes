package app.krafted.fantasyheroestournament.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.recordsDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_records")

data class UserRecords(
    val bestRank: String = "NONE",
    val bestGrandTotal: Int = 0,
    val bestZeus: Int = 0,
    val bestPilot: Int = 0,
    val bestJoker: Int = 0,
    val runsCompleted: Int = 0,
    val soundOn: Boolean = true,
    val vibrateOn: Boolean = true,
    val tutorialSeen: Boolean = false
)

interface IRecordsStore {
    val userRecordsFlow: Flow<UserRecords>
    suspend fun recordCompletedRun(
        rank: String,
        grandTotal: Int,
        zeusScore: Int,
        pilotScore: Int,
        jokerScore: Int
    )
    suspend fun setSoundOn(enabled: Boolean)
    suspend fun setVibrateOn(enabled: Boolean)
    suspend fun setTutorialSeen(seen: Boolean)
    suspend fun resetRecords()
}

class RecordsStore(private val context: Context) : IRecordsStore {

    object PreferencesKeys {
        val BEST_RANK = stringPreferencesKey("best_rank")
        val BEST_GRAND_TOTAL = intPreferencesKey("best_grand_total")
        val BEST_ZEUS = intPreferencesKey("best_zeus")
        val BEST_PILOT = intPreferencesKey("best_pilot")
        val BEST_JOKER = intPreferencesKey("best_joker")
        val RUNS_COMPLETED = intPreferencesKey("runs_completed")
        val SOUND_ON = booleanPreferencesKey("sound_on")
        val VIBRATE_ON = booleanPreferencesKey("vibrate_on")
        val TUTORIAL_SEEN = booleanPreferencesKey("tutorial_seen")
    }

    override val userRecordsFlow: Flow<UserRecords> = context.recordsDataStore.data.map { prefs ->
        UserRecords(
            bestRank = prefs[PreferencesKeys.BEST_RANK] ?: "NONE",
            bestGrandTotal = prefs[PreferencesKeys.BEST_GRAND_TOTAL] ?: 0,
            bestZeus = prefs[PreferencesKeys.BEST_ZEUS] ?: 0,
            bestPilot = prefs[PreferencesKeys.BEST_PILOT] ?: 0,
            bestJoker = prefs[PreferencesKeys.BEST_JOKER] ?: 0,
            runsCompleted = prefs[PreferencesKeys.RUNS_COMPLETED] ?: 0,
            soundOn = prefs[PreferencesKeys.SOUND_ON] ?: true,
            vibrateOn = prefs[PreferencesKeys.VIBRATE_ON] ?: true,
            tutorialSeen = prefs[PreferencesKeys.TUTORIAL_SEEN] ?: false
        )
    }

    override suspend fun recordCompletedRun(
        rank: String,
        grandTotal: Int,
        zeusScore: Int,
        pilotScore: Int,
        jokerScore: Int
    ) {
        context.recordsDataStore.edit { prefs ->
            val currentBestTotal = prefs[PreferencesKeys.BEST_GRAND_TOTAL] ?: 0
            if (grandTotal > currentBestTotal) {
                prefs[PreferencesKeys.BEST_GRAND_TOTAL] = grandTotal
                prefs[PreferencesKeys.BEST_RANK] = rank
            }

            val currentZeus = prefs[PreferencesKeys.BEST_ZEUS] ?: 0
            if (zeusScore > currentZeus) {
                prefs[PreferencesKeys.BEST_ZEUS] = zeusScore
            }

            val currentPilot = prefs[PreferencesKeys.BEST_PILOT] ?: 0
            if (pilotScore > currentPilot) {
                prefs[PreferencesKeys.BEST_PILOT] = pilotScore
            }

            val currentJoker = prefs[PreferencesKeys.BEST_JOKER] ?: 0
            if (jokerScore > currentJoker) {
                prefs[PreferencesKeys.BEST_JOKER] = jokerScore
            }

            val currentRuns = prefs[PreferencesKeys.RUNS_COMPLETED] ?: 0
            prefs[PreferencesKeys.RUNS_COMPLETED] = currentRuns + 1
        }
    }

    override suspend fun setSoundOn(enabled: Boolean) {
        context.recordsDataStore.edit { prefs ->
            prefs[PreferencesKeys.SOUND_ON] = enabled
        }
    }

    override suspend fun setVibrateOn(enabled: Boolean) {
        context.recordsDataStore.edit { prefs ->
            prefs[PreferencesKeys.VIBRATE_ON] = enabled
        }
    }

    override suspend fun setTutorialSeen(seen: Boolean) {
        context.recordsDataStore.edit { prefs ->
            prefs[PreferencesKeys.TUTORIAL_SEEN] = seen
        }
    }

    override suspend fun resetRecords() {
        context.recordsDataStore.edit { prefs ->
            prefs[PreferencesKeys.BEST_RANK] = "NONE"
            prefs[PreferencesKeys.BEST_GRAND_TOTAL] = 0
            prefs[PreferencesKeys.BEST_ZEUS] = 0
            prefs[PreferencesKeys.BEST_PILOT] = 0
            prefs[PreferencesKeys.BEST_JOKER] = 0
            prefs[PreferencesKeys.RUNS_COMPLETED] = 0
        }
    }
}
