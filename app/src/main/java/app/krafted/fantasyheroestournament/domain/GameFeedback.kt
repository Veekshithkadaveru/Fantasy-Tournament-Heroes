package app.krafted.fantasyheroestournament.domain

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The handle a screen uses to make something felt. It carries the player's own
 * sound and vibration settings, so a caller only has to name the moment — no
 * screen has to reach for the records to find out whether it is allowed to.
 *
 * A feedback with no [SoundManager] behind it is silent, which is what previews
 * and tests get for free.
 */
@Stable
class GameFeedback internal constructor(private val sounds: SoundManager?) {
    internal var soundOn: Boolean = true
    internal var vibrateOn: Boolean = true

    fun play(cue: Cue, rate: Float = 1f) {
        sounds?.play(cue, soundOn, vibrateOn, rate)
    }
}

/** Silent by default: a screen can fire cues wherever it is hosted. */
val LocalGameFeedback = staticCompositionLocalOf { GameFeedback(null) }

/**
 * Binds the app's one [SoundManager] to the player's current settings. The
 * handle itself is stable, so changing a setting never restarts a trial.
 */
@Composable
fun rememberGameFeedback(sounds: SoundManager?, soundOn: Boolean, vibrateOn: Boolean): GameFeedback {
    val feedback = remember(sounds) { GameFeedback(sounds) }
    SideEffect {
        feedback.soundOn = soundOn
        feedback.vibrateOn = vibrateOn
    }
    return feedback
}
