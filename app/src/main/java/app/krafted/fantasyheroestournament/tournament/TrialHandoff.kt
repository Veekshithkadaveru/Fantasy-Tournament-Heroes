package app.krafted.fantasyheroestournament.tournament

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.delay

/** One beat on the finished arena before the tournament takes the screen back. */
const val ResultHandoffMillis = 750L

/**
 * Bridges a finished trial to the tournament's own result screen. The trial
 * holds its last frame — a landed strike, a crash, a cleared board — then banks
 * the score exactly once, however many times this composable recomposes.
 */
@Composable
fun HandOffToTournament(onComplete: () -> Unit) {
    val bank by rememberUpdatedState(onComplete)
    LaunchedEffect(Unit) {
        delay(ResultHandoffMillis)
        bank()
    }
}
