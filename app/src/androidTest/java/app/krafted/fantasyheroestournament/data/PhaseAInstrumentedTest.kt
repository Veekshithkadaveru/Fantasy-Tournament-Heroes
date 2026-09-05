package app.krafted.fantasyheroestournament.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhaseAInstrumentedTest {

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun packagedTrialsConfigMatchesPlan() {
        val config = TrialConfigLoader.loadConfig(context)

        assertEquals(listOf("QUALIFIER", "SEMIFINAL", "FINAL"), config.rounds.map { it.id })
        assertEquals(listOf(1500, 1900, 0), config.rounds.map { it.threshold })
        assertEquals(listOf(20000L, 20000L, 25000L), config.rounds.map { it.trialDurationMs })
        assertEquals(0.24f, config.zeus.bandStart)
        assertEquals(0.10f, config.zeus.bandEnd)
        assertEquals(7000L, config.pilot.speedStepMs)
        assertEquals(7000L, config.joker.ruleSwitchMs)
    }

    @Test
    fun recordsPersistAcrossStoreInstances() = runBlocking {
        val firstStore = RecordsStore(context)
        firstStore.resetRecords()
        firstStore.setSoundOn(false)
        firstStore.setVibrateOn(false)
        firstStore.setTutorialSeen(true)

        try {
            firstStore.recordCompletedRun(
                rank = "GOLD",
                grandTotal = 7_500,
                zeusScore = 900,
                pilotScore = 850,
                jokerScore = 800
            )

            val restored = RecordsStore(context).userRecordsFlow.first()

            assertEquals("GOLD", restored.bestRank)
            assertEquals(7_500, restored.bestGrandTotal)
            assertEquals(900, restored.bestZeus)
            assertEquals(850, restored.bestPilot)
            assertEquals(800, restored.bestJoker)
            assertEquals(1, restored.runsCompleted)
            assertFalse(restored.soundOn)
            assertFalse(restored.vibrateOn)
            assertEquals(true, restored.tutorialSeen)
        } finally {
            firstStore.resetRecords()
            firstStore.setSoundOn(true)
            firstStore.setVibrateOn(true)
            firstStore.setTutorialSeen(false)
        }
    }
}
