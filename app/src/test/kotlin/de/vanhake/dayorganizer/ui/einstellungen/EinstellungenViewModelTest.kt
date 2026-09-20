package de.vanhake.dayorganizer.ui.einstellungen

import app.cash.turbine.test
import de.vanhake.dayorganizer.data.Einstellungen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals

class EinstellungenViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun vorher() = Dispatchers.setMain(dispatcher)
    @After fun nachher() = Dispatchers.resetMain()

    private class FakeSpeicher(start: Einstellungen = Einstellungen()) {
        val stand = MutableStateFlow(start)
    }

    private fun viewModel(speicher: FakeSpeicher) = EinstellungenViewModel(
        lesen = { speicher.stand.value },
        schreiben = { speicher.stand.value = it },
        hinweiseLesen = { emptyList() },
    )

    @Test
    fun `die gespeicherten Werte erscheinen im Zustand`() = runTest(dispatcher) {
        val speicher = FakeSpeicher(Einstellungen(tagesbonusPunkte = 25, standardKulanzMinuten = 45))
        val vm = viewModel(speicher)

        vm.zustand.test {
            skipItems(1)
            val zustand = awaitItem()
            assertEquals("25", zustand.tagesbonusText)
            assertEquals("45", zustand.standardKulanzText)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Speichern uebernimmt gueltige Werte`() = runTest(dispatcher) {
        val speicher = FakeSpeicher()
        val vm = viewModel(speicher)

        vm.zustand.test { skipItems(2); cancelAndIgnoreRemainingEvents() }
        vm.tagesbonusAendern("30")
        vm.kulanzAendern("90")
        vm.speichern().join()

        assertEquals(Einstellungen(tagesbonusPunkte = 30, standardKulanzMinuten = 90), speicher.stand.value)
    }

    @Test
    fun `unsinnige Eingaben zerstoeren die Einstellung nicht`() = runTest(dispatcher) {
        val speicher = FakeSpeicher(Einstellungen(tagesbonusPunkte = 25, standardKulanzMinuten = 45))
        val vm = viewModel(speicher)

        vm.zustand.test { skipItems(2); cancelAndIgnoreRemainingEvents() }
        vm.tagesbonusAendern("viel")
        vm.kulanzAendern("-5")
        vm.speichern().join()

        assertEquals(Einstellungen(tagesbonusPunkte = 25, standardKulanzMinuten = 45), speicher.stand.value)
    }
}
