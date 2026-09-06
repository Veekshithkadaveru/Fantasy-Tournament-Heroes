package app.krafted.fantasyheroestournament.domain

enum class Rank(val displayName: String, val threshold: Int) {
    NONE("None", 0),
    BRONZE("Bronze", 0),
    SILVER("Silver", 5400),
    GOLD("Gold", 7200),
    CHAMPION("Champion", 8550)
}

object RankCalculator {
    fun calculateRank(grandTotalScore: Int): Rank = when {
        grandTotalScore >= Rank.CHAMPION.threshold -> Rank.CHAMPION
        grandTotalScore >= Rank.GOLD.threshold -> Rank.GOLD
        grandTotalScore >= Rank.SILVER.threshold -> Rank.SILVER
        else -> Rank.BRONZE
    }
}
