package app.krafted.fantasyheroestournament.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RankCalculatorTest {

    @Test
    fun testChampionThreshold() {
        assertEquals(Rank.CHAMPION, RankCalculator.calculateRank(8550))
        assertEquals(Rank.CHAMPION, RankCalculator.calculateRank(9000))
    }

    @Test
    fun testGoldThreshold() {
        assertEquals(Rank.GOLD, RankCalculator.calculateRank(7200))
        assertEquals(Rank.GOLD, RankCalculator.calculateRank(8549))
    }

    @Test
    fun testSilverThreshold() {
        assertEquals(Rank.SILVER, RankCalculator.calculateRank(5400))
        assertEquals(Rank.SILVER, RankCalculator.calculateRank(7199))
    }

    @Test
    fun testBronzeThreshold() {
        assertEquals(Rank.BRONZE, RankCalculator.calculateRank(0))
        assertEquals(Rank.BRONZE, RankCalculator.calculateRank(5399))
    }
}
