package app.krafted.fantasyheroestournament.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.StringReader

class TrialConfigTest {

    @Test
    fun testDefaultConfigValues() {
        val config = TrialConfigLoader.defaultConfig

        assertEquals(3, config.rounds.size)

        assertEquals("QUALIFIER", config.rounds[0].id)
        assertEquals(1500, config.rounds[0].threshold)

        assertEquals("SEMIFINAL", config.rounds[1].id)
        assertEquals(1900, config.rounds[1].threshold)

        assertEquals("FINAL", config.rounds[2].id)
        assertEquals(0, config.rounds[2].threshold)

        assertEquals(listOf(5, 5, 5), config.zeus.throws)
        assertEquals(0.24f, config.zeus.bandStart)
        assertEquals(0.10f, config.zeus.bandEnd)
        assertEquals(listOf(5.0f, 6.5f, 8.0f), config.pilot.baseSpeed)
        assertEquals(7000L, config.joker.ruleSwitchMs)
    }

    @Test
    fun testValidJsonIsParsed() {
        val json = """
            {
              "rounds": [
                {"id":"QUALIFIER","threshold":1500,"trialDurationMs":20000},
                {"id":"SEMIFINAL","threshold":1900,"trialDurationMs":20000},
                {"id":"FINAL","threshold":0,"trialDurationMs":25000}
              ],
              "zeus": {
                "throws":[5,5,5],
                "bandStart":0.24,
                "bandEnd":0.10,
                "driftSpeed":[1.0,1.4,1.8]
              },
              "pilot": {"baseSpeed":[5.0,6.5,8.0],"speedStepMs":7000,"coinRate":0.8},
              "joker": {"ruleSwitchMs":7000,"gridSize":4,"penalty":35}
            }
        """.trimIndent()

        val config = TrialConfigLoader.parseConfig(StringReader(json))

        assertEquals(1900, config.rounds[1].threshold)
        assertEquals(0.10f, config.zeus.bandEnd)
        assertEquals(8.0f, config.pilot.baseSpeed[2])
        assertEquals(4, config.joker.gridSize)
    }

    @Test
    fun testMalformedJsonUsesDefaultConfig() {
        val config = TrialConfigLoader.parseConfig(StringReader("{not-json}"))

        assertSame(TrialConfigLoader.defaultConfig, config)
    }

    @Test
    fun testIncompleteJsonUsesDefaultConfig() {
        val config = TrialConfigLoader.parseConfig(StringReader("{\"rounds\": []}"))

        assertSame(TrialConfigLoader.defaultConfig, config)
    }
}
