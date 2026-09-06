package app.krafted.fantasyheroestournament.domain

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class SoundManager(private val context: Context) {

    private var toneGenerator: ToneGenerator? = runCatching {
        ToneGenerator(AudioManager.STREAM_MUSIC, 70)
    }.getOrNull()

    private val vibrator: Vibrator? by lazy {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }.getOrNull()
    }

    fun playTone(toneType: Int, soundEnabled: Boolean, durationMs: Int = 150) {
        if (!soundEnabled) return
        runCatching {
            toneGenerator?.startTone(toneType, durationMs)
        }
    }

    fun playSuccessTone(soundEnabled: Boolean) {
        playTone(ToneGenerator.TONE_PROP_BEEP, soundEnabled, 200)
    }

    fun playFailureTone(soundEnabled: Boolean) {
        playTone(ToneGenerator.TONE_PROP_NACK, soundEnabled, 300)
    }

    fun vibrate(vibrateEnabled: Boolean, durationMs: Long = 100) {
        if (!vibrateEnabled) return
        runCatching {
            vibrator?.let { v ->
                if (v.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        v.vibrate(durationMs)
                    }
                }
            }
        }
    }

    fun release() {
        runCatching {
            toneGenerator?.release()
            toneGenerator = null
        }
    }
}
