package app.krafted.fantasyheroestournament.data

import com.google.gson.annotations.SerializedName

data class RoundConfig(
    @SerializedName("id") val id: String,
    @SerializedName("threshold") val threshold: Int,
    @SerializedName("trialDurationMs") val trialDurationMs: Long
)

data class ZeusConfig(
    @SerializedName("throws") val throws: List<Int>,
    @SerializedName("bandStart") val bandStart: Float,
    @SerializedName("bandEnd") val bandEnd: Float,
    @SerializedName("driftSpeed") val driftSpeed: List<Float>
)

data class PilotConfig(
    @SerializedName("baseSpeed") val baseSpeed: List<Float>,
    @SerializedName("speedStepMs") val speedStepMs: Long,
    @SerializedName("coinRate") val coinRate: Float
)

data class JokerConfig(
    @SerializedName("ruleSwitchMs") val ruleSwitchMs: Long,
    @SerializedName("gridSize") val gridSize: Int,
    @SerializedName("penalty") val penalty: Int
)

data class TournamentConfig(
    @SerializedName("rounds") val rounds: List<RoundConfig>,
    @SerializedName("zeus") val zeus: ZeusConfig,
    @SerializedName("pilot") val pilot: PilotConfig,
    @SerializedName("joker") val joker: JokerConfig
)
