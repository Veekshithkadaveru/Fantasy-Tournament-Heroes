package app.krafted.fantasyheroestournament.domain

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.io.File
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Everything the game can say out loud. A caller names the event, never a tone
 * or a vibration pattern, so the two stay matched wherever the cue is fired.
 */
enum class Cue {
    STRIKE,     // Zeus lands inside the band
    PERFECT,    // Zeus finds the centre of it
    MISS,       // Zeus releases outside the band
    TAP,        // Joker obeys the rule
    PENALTY,    // Joker tapped what the rule forbade
    RULE_FLIP,  // the banner changes
    FREEZE,     // a clean freeze pays out
    COIN,       // Pilot collects one
    STREAK,     // Pilot completes the eight-coin run
    CRASH,      // Pilot's flight ends
    BANK,       // a trial's score is banked
    MEDAL       // the ceremony medal lands
}

private const val SampleRate = 22050

/**
 * A cue's voice: a short sequence of notes, a harmonic mix, and how fast each
 * note dies. Rendered once into the cache and played from a pool thereafter.
 */
private class Voice(
    val notes: FloatArray,
    val stepMillis: Int,
    val decay: Float,
    val partials: FloatArray = floatArrayOf(1f, .4f, .15f),
    val noise: Float = 0f,
    val bend: Float = 1f,
    val gain: Float = .85f
)

/**
 * The app's sound and haptics. There are no audio assets in the pack, so the
 * cues are synthesised on first run, written to the cache as small WAVs, and
 * played through a [SoundPool] — which buys polyphony and a playback rate, so
 * a rising coin streak can climb in pitch without a second sample.
 *
 * Every part of this is optional: a device that refuses audio or vibration
 * leaves the game silent rather than broken.
 */
class SoundManager(context: Context) {

    private val appContext = context.applicationContext
    private val samples = HashMap<Cue, Int>(Cue.entries.size)
    private val ready = HashSet<Int>()
    private var released = false

    private val pool: SoundPool? = runCatching {
        SoundPool.Builder().setMaxStreams(6).setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        ).build()
    }.getOrNull()

    private val vibrator: Vibrator? by lazy {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }.getOrNull()
    }

    init {
        pool?.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) synchronized(samples) { ready += sampleId }
        }
        // Synthesis and disk work never touch the frame the caller is on.
        pool?.let { Thread({ prime(it) }, "cue-synth").apply { isDaemon = true }.start() }
    }

    private fun prime(pool: SoundPool) {
        runCatching {
            val dir = File(appContext.cacheDir, "cues").apply { mkdirs() }
            Cue.entries.forEach { cue ->
                val file = File(dir, "${cue.name.lowercase()}.wav")
                if (!file.isFile || file.length() < 64L) file.writeBytes(render(voiceFor(cue)))
                val id = pool.load(file.absolutePath, 1)
                synchronized(samples) { if (released) return@runCatching else samples[cue] = id }
            }
        }
    }

    /**
     * Fires [cue] under the player's own settings. [rate] shifts the pitch —
     * used to climb a streak — and is clamped to what a pool will accept.
     */
    fun play(cue: Cue, soundOn: Boolean, vibrateOn: Boolean, rate: Float = 1f) {
        if (soundOn) {
            // A cue that has not finished loading is skipped rather than queued:
            // a stale sound arriving late is worse than a missing one.
            val id = synchronized(samples) { samples[cue]?.takeIf { it in ready } }
            if (id != null) {
                val level = levelFor(cue)
                runCatching { pool?.play(id, level, level, 1, 0, rate.coerceIn(.5f, 2f)) }
            }
        }
        if (vibrateOn) buzz(cue)
    }

    private fun buzz(cue: Cue) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val timings = hapticFor(cue)
        runCatching {
            val effect = if (v.hasAmplitudeControl()) {
                VibrationEffect.createWaveform(timings.first, timings.second, -1)
            } else {
                VibrationEffect.createWaveform(timings.first, -1)
            }
            v.vibrate(effect)
        }
    }

    fun release() {
        synchronized(samples) {
            released = true
            samples.clear()
            ready.clear()
        }
        runCatching { pool?.release() }
    }

    // -----------------------------------------------------------------------
    // The cue table: one voice, one level and one haptic per event.
    // -----------------------------------------------------------------------

    private fun voiceFor(cue: Cue): Voice = when (cue) {
        // A short, low crack with a downward bend — thunder, not a beep.
        Cue.STRIKE -> Voice(floatArrayOf(196f), 260, decay = 7f,
            partials = floatArrayOf(1f, .7f, .45f, .2f), noise = .18f, bend = .74f)
        // The perfect zone answers with a rising major triad.
        Cue.PERFECT -> Voice(floatArrayOf(523.25f, 659.25f, 783.99f, 1046.5f), 95, decay = 4.5f)
        Cue.MISS -> Voice(floatArrayOf(110f), 220, decay = 9f,
            partials = floatArrayOf(1f, .3f), noise = .1f, bend = .8f, gain = .6f)
        Cue.TAP -> Voice(floatArrayOf(880f), 90, decay = 11f,
            partials = floatArrayOf(1f, .25f), gain = .7f)
        Cue.PENALTY -> Voice(floatArrayOf(220f, 155f), 110, decay = 6f,
            partials = floatArrayOf(1f, .8f, .6f), noise = .12f, gain = .7f)
        Cue.RULE_FLIP -> Voice(floatArrayOf(1174.66f), 220, decay = 8f,
            partials = floatArrayOf(1f, .18f, .08f), gain = .45f)
        Cue.FREEZE -> Voice(floatArrayOf(1318.51f, 1975.53f), 190, decay = 5f,
            partials = floatArrayOf(1f, .3f, .12f), gain = .65f)
        Cue.COIN -> Voice(floatArrayOf(1046.5f, 1567.98f), 62, decay = 8f,
            partials = floatArrayOf(1f, .3f), gain = .6f)
        Cue.STREAK -> Voice(floatArrayOf(783.99f, 987.77f, 1174.66f, 1567.98f), 80, decay = 5f)
        // Noise falling away under a collapsing tone.
        Cue.CRASH -> Voice(floatArrayOf(330f), 520, decay = 5.5f,
            partials = floatArrayOf(1f, .6f, .35f), noise = .6f, bend = .35f)
        Cue.BANK -> Voice(floatArrayOf(659.25f, 830.61f, 987.77f), 130, decay = 3.6f)
        // The ceremony fanfare: a fourth, a fifth, and the octave held.
        Cue.MEDAL -> Voice(floatArrayOf(523.25f, 698.46f, 783.99f, 1046.5f, 1046.5f), 210,
            decay = 2.6f, partials = floatArrayOf(1f, .5f, .28f, .12f))
    }

    private fun levelFor(cue: Cue): Float = when (cue) {
        Cue.RULE_FLIP -> .35f
        Cue.COIN, Cue.TAP -> .55f
        Cue.MISS, Cue.PENALTY -> .6f
        Cue.MEDAL, Cue.STREAK, Cue.PERFECT -> .9f
        else -> .75f
    }

    /** Timings and amplitudes, in the shape [VibrationEffect.createWaveform] wants. */
    private fun hapticFor(cue: Cue): Pair<LongArray, IntArray> = when (cue) {
        Cue.STRIKE -> longArrayOf(0, 26) to intArrayOf(0, 210)
        Cue.PERFECT -> longArrayOf(0, 14, 34, 26) to intArrayOf(0, 150, 0, 255)
        Cue.MISS -> longArrayOf(0, 34) to intArrayOf(0, 90)
        Cue.TAP -> longArrayOf(0, 10) to intArrayOf(0, 110)
        Cue.PENALTY -> longArrayOf(0, 18, 30, 18) to intArrayOf(0, 180, 0, 180)
        Cue.RULE_FLIP -> longArrayOf(0, 8) to intArrayOf(0, 70)
        Cue.FREEZE -> longArrayOf(0, 12, 40, 12) to intArrayOf(0, 120, 0, 160)
        Cue.COIN -> longArrayOf(0, 8) to intArrayOf(0, 90)
        Cue.STREAK -> longArrayOf(0, 12, 26, 12, 26, 22) to intArrayOf(0, 130, 0, 170, 0, 230)
        Cue.CRASH -> longArrayOf(0, 70) to intArrayOf(0, 255)
        Cue.BANK -> longArrayOf(0, 18, 40, 30) to intArrayOf(0, 140, 0, 200)
        Cue.MEDAL -> longArrayOf(0, 30, 60, 30, 60, 90) to intArrayOf(0, 160, 0, 200, 0, 255)
    }

    // -----------------------------------------------------------------------
    // Synthesis
    // -----------------------------------------------------------------------

    private fun render(voice: Voice): ByteArray {
        val perNote = voice.stepMillis * SampleRate / 1000
        val total = perNote * voice.notes.size
        val out = ByteArray(44 + total * 2)
        writeWavHeader(out, total)
        // Partials and noise are summed, so normalise by what they can reach.
        val ceiling = voice.partials.sum() + voice.noise
        val attackSamples = SampleRate * .004f
        val rng = Random(cueSeed)
        var phase = 0.0
        var at = 44
        voice.notes.forEach { base ->
            for (s in 0 until perNote) {
                val t = s.toFloat() / perNote
                phase += 2.0 * PI * (base * (1f + (voice.bend - 1f) * t)) / SampleRate
                var value = 0f
                voice.partials.forEachIndexed { k, amp ->
                    value += amp * sin(phase * (k + 1)).toFloat()
                }
                if (voice.noise > 0f) value += voice.noise * (rng.nextFloat() * 2f - 1f)
                val envelope = (s / attackSamples).coerceAtMost(1f) * exp(-voice.decay * t)
                val pcm = ((value / ceiling) * envelope * voice.gain).coerceIn(-1f, 1f) * 32767f
                val word = pcm.toInt()
                out[at++] = (word and 0xFF).toByte()
                out[at++] = ((word shr 8) and 0xFF).toByte()
            }
        }
        return out
    }

    private fun writeWavHeader(out: ByteArray, samples: Int) {
        val dataBytes = samples * 2
        fun ascii(at: Int, text: String) = text.forEachIndexed { i, c -> out[at + i] = c.code.toByte() }
        fun le(at: Int, value: Int, bytes: Int) {
            for (i in 0 until bytes) out[at + i] = ((value shr (8 * i)) and 0xFF).toByte()
        }
        ascii(0, "RIFF"); le(4, 36 + dataBytes, 4); ascii(8, "WAVE")
        ascii(12, "fmt "); le(16, 16, 4); le(20, 1, 2); le(22, 1, 2)
        le(24, SampleRate, 4); le(28, SampleRate * 2, 4); le(32, 2, 2); le(34, 16, 2)
        ascii(36, "data"); le(40, dataBytes, 4)
    }

    private companion object {
        /** Fixed, so a cue's noise is identical every time it is rendered. */
        const val cueSeed = 0x5EED
    }
}
