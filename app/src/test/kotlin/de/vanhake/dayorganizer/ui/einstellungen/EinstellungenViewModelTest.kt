package de.vanhake.dayorganizer.ui.einstellungen

import app.cash.turbine.test
import de.vanhake.dayorganizer.data.Einstellungen
import de.vanhake.dayorganizer.platform.permission.Hinweis
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

    private fun viewModel(
        speicher: FakeSpeicher,
        hinweiseLesen: suspend () -> List<Hinweis> = { emptyList() },
    ) = EinstellungenViewModel(
        lesen = { speicher.stand.value },
        schreiben = { speicher.stand.value = it },
        hinweiseLesen = hinweiseLesen,
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
    fun `die Hinweise aus hinweiseLesen landen im Zustand`() = runTest(dispatcher) {
        val speicher = FakeSpeicher()
        val vm = viewModel(speicher, hinweiseLesen = { listOf(Hinweis.WAECHTER_HAENGT) })

        vm.zustand.test {
            skipItems(1)
            val zustand = awaitItem()
            assertEquals(listOf(Hinweis.WAECHTER_HAENGT), zustand.hinweise)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Speichern uebernimmt gueltige Werte bei beiden Feldern`() = runTest(dispatcher) {
        val speicher = FakeSpeicher()
        val vm = viewModel(speicher)

        vm.zustand.test { skipItems(2); cancelAndIgnoreRemainingEvents() }
        vm.tagesbonusAendern("30")
        vm.kulanzAendern("90")
        vm.speichern().join()

        assertEquals(Einstellungen(tagesbonusPunkte = 30, standardKulanzMinuten = 90), speicher.stand.value)
    }

    @Test
    fun `Null ist ein gueltiger Wert bei beiden Feldern`() = runTest(dispatcher) {
        val speicher = FakeSpeicher(Einstellungen(tagesbonusPunkte = 25, standardKulanzMinuten = 45))
        val vm = viewModel(speicher)

        vm.zustand.test { skipItems(2); cancelAndIgnoreRemainingEvents() }
        vm.tagesbonusAendern("0")
        vm.kulanzAendern("0")
        vm.speichern().join()

        assertEquals(Einstellungen(tagesbonusPunkte = 0, standardKulanzMinuten = 0), speicher.stand.value)
    }

    @Test
    fun `ein negativer Tagesbonus wird verworfen, die gueltige Kulanz aber uebernommen`() = runTest(dispatcher) {
        val speicher = FakeSpeicher(Einstellungen(tagesbonusPunkte = 25, standardKulanzMinuten = 45))
        val vm = viewModel(speicher)

        vm.zustand.test { skipItems(2); cancelAndIgnoreRemainingEvents() }
        vm.tagesbonusAendern("-5")
        vm.kulanzAendern("90")
        vm.speichern().join()

        assertEquals(Einstellungen(tagesbonusPunkte = 25, standardKulanzMinuten = 90), speicher.stand.value)
    }

    @Test
    fun `eine negative Kulanz wird verworfen, der gueltige Tagesbonus aber uebernommen`() = runTest(dispatcher) {
        val speicher = FakeSpeicher(Einstellungen(tagesbonusPunkte = 25, standardKulanzMinuten = 45))
        val vm = viewModel(speicher)

        vm.zustand.test { skipItems(2); cancelAndIgnoreRemainingEvents() }
        vm.tagesbonusAendern("30")
        vm.kulanzAendern("-5")
        vm.speichern().join()

        assertEquals(Einstellungen(tagesbonusPunkte = 30, standardKulanzMinuten = 45), speicher.stand.value)
    }

    @Test
    fun `ein unlesbarer Tagesbonus wird verworfen, die gueltige Kulanz aber uebernommen`() = runTest(dispatcher) {
        val speicher = FakeSpeicher(Einstellungen(tagesbonusPunkte = 25, standardKulanzMinuten = 45))
        val vm = viewModel(speicher)

        vm.zustand.test { skipItems(2); cancelAndIgnoreRemainingEvents() }
        vm.tagesbonusAendern("viel")
        vm.kulanzAendern("90")
        vm.speichern().join()

        assertEquals(Einstellungen(tagesbonusPunkte = 25, standardKulanzMinuten = 90), speicher.stand.value)
    }

    @Test
    fun `eine unlesbare Kulanz wird verworfen, der gueltige Tagesbonus aber uebernommen`() = runTest(dispatcher) {
        val speicher = FakeSpeicher(Einstellungen(tagesbonusPunkte = 25, standardKulanzMinuten = 45))
        val vm = viewModel(speicher)

        vm.zustand.test { skipItems(2); cancelAndIgnoreRemainingEvents() }
        vm.tagesbonusAendern("30")
        vm.kulanzAendern("viel")
        vm.speichern().join()

        assertEquals(Einstellungen(tagesbonusPunkte = 30, standardKulanzMinuten = 45), speicher.stand.value)
    }

    @Test
    fun `ein leeres Feld laesst den bisherigen Wert bei beiden Feldern unveraendert`() = runTest(dispatcher) {
        val speicher = FakeSpeicher(Einstellungen(tagesbonusPunkte = 25, standardKulanzMinuten = 45))
        val vm = viewModel(speicher)

        vm.zustand.test { skipItems(2); cancelAndIgnoreRemainingEvents() }
        vm.tagesbonusAendern("")
        vm.kulanzAendern("")
        vm.speichern().join()

        assertEquals(Einstellungen(tagesbonusPunkte = 25, standardKulanzMinuten = 45), speicher.stand.value)
    }
}
