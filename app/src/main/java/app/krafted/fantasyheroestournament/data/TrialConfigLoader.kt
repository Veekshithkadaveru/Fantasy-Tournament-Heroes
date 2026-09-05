package app.krafted.fantasyheroestournament.data

import android.content.Context
import com.google.gson.Gson
import java.io.InputStreamReader
import java.io.Reader

object TrialConfigLoader {

    private val gson = Gson()

    val defaultConfig: TournamentConfig = TournamentConfig(
        rounds = listOf(
            RoundConfig(id = "QUALIFIER", threshold = 1500, trialDurationMs = 20000L),
            RoundConfig(id = "SEMIFINAL", threshold = 1900, trialDurationMs = 20000L),
            RoundConfig(id = "FINAL", threshold = 0, trialDurationMs = 25000L)
        ),
        zeus = ZeusConfig(
            throws = listOf(5, 5, 5),
            bandStart = 0.24f,
            bandEnd = 0.10f,
            driftSpeed = listOf(1.0f, 1.4f, 1.8f)
        ),
        pilot = PilotConfig(
            baseSpeed = listOf(5.0f, 6.5f, 8.0f),
            speedStepMs = 7000L,
            coinRate = 0.8f
        ),
        joker = JokerConfig(
            ruleSwitchMs = 7000L,
            gridSize = 4,
            penalty = 35
        )
    )

    fun loadConfig(context: Context): TournamentConfig {
        return runCatching {
            context.assets.open("trials.json").use { inputStream ->
                InputStreamReader(inputStream, Charsets.UTF_8).use { reader ->
                    parseConfig(reader)
                }
            }
        }.getOrDefault(defaultConfig)
    }

    internal fun parseConfig(reader: Reader): TournamentConfig {
        return runCatching {
            gson.fromJson(reader, TournamentConfig::class.java)
        }.getOrNull()
            ?.takeIf { it.isValid() }
            ?: defaultConfig
    }

    private fun TournamentConfig.isValid(): Boolean = runCatching {
        val roundCount = rounds.size
        roundCount == 3 &&
            rounds.map { it.id } == listOf("QUALIFIER", "SEMIFINAL", "FINAL") &&
            rounds.all { it.threshold >= 0 && it.trialDurationMs > 0L } &&
            zeus.throws.size == roundCount && zeus.throws.all { it > 0 } &&
            zeus.bandStart in 0f..1f &&
            zeus.bandEnd in 0f..zeus.bandStart &&
            zeus.driftSpeed.size == roundCount && zeus.driftSpeed.all { it > 0f } &&
            pilot.baseSpeed.size == roundCount && pilot.baseSpeed.all { it > 0f } &&
            pilot.speedStepMs > 0L && pilot.coinRate > 0f &&
            joker.ruleSwitchMs > 0L && joker.gridSize > 0 && joker.penalty >= 0
    }.getOrDefault(false)
}
