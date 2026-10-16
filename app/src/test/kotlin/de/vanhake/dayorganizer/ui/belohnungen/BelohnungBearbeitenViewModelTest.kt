package de.vanhake.dayorganizer.ui.belohnungen

import de.vanhake.dayorganizer.FakeAktivitaetRepository
import de.vanhake.dayorganizer.FakeBelohnungRepository
import de.vanhake.dayorganizer.domain.model.Belohnung
import de.vanhake.dayorganizer.data.repository.BelohnungRepository
import de.vanhake.dayorganizer.ui.gemeinsam.SpeicherErgebnis
import de.vanhake.dayorganizer.ui.gemeinsam.SpeicherFehler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BelohnungBearbeitenViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun vorher() = Dispatchers.setMain(dispatcher)
    @After fun nachher() = Dispatchers.resetMain()

    private val kino = Belohnung(id = 1, name = "Kinoabend", preisPunkte = 200, symbol = "movie")

    /** Verzoegert speichern(), damit sich zwei Aufrufe ueberlappen koennen -- ohne Suspension
     * liefe der erste komplett durch, bevor der zweite startet. */
    private class LangsamesRepository(private val delegate: FakeBelohnungRepository) :
        de.vanhake.dayorganizer.data.repository.BelohnungRepository by delegate {
        override suspend fun speichern(belohnung: Belohnung): Long {
            delay(100)
            return delegate.speichern(belohnung)
        }
    }

    private fun viewModel(belohnungen: de.vanhake.dayorganizer.data.repository.BelohnungRepository = FakeBelohnungRepository()) =
        BelohnungBearbeitenViewModel(belohnungen, FakeAktivitaetRepository())

    @Test
    fun `fehlt der Datensatz beim Speichern bleibt der Editor mit Entwurf offen`() = runTest(dispatcher) {
        val repo = FakeBelohnungRepository(listOf(kino))
        val vm = viewModel(repo)
        vm.laden(1L).join()
        vm.aendern { it.copy(name = "Neu") }
        val entwurf = vm.entwurf.value
        repo.stand.value = emptyList()

        assertNull(vm.speichern())

        assertEquals(SpeicherFehler.NICHT_MEHR_VORHANDEN, vm.speicherFehler.value)
        assertEquals(entwurf, vm.entwurf.value)
    }

    @Test
    fun `eine Datenbankausnahme beim Speichern wird als Fehlschlag gemeldet`() = runTest(dispatcher) {
        val vm = viewModel(werfendesRepository(FakeBelohnungRepository(), werfenBis = Int.MAX_VALUE))
        vm.laden(0L).join()
        vm.aendern { it.copy(name = "Eis", symbol = "icecream") }

        assertNull(vm.speichern())

        assertEquals(SpeicherFehler.FEHLGESCHLAGEN, vm.speicherFehler.value)
    }

    @Test
    fun `nach einem Fehlschlag laesst sich erneut speichern`() = runTest(dispatcher) {
        val echt = FakeBelohnungRepository(listOf(kino))
        val vm = viewModel(werfendesRepository(echt, werfenBis = 1))
        vm.laden(1L).join()
        vm.aendern { it.copy(name = "Neu") }

        assertNull(vm.speichern())
        vm.speicherFehlerGezeigt()
        assertNull(vm.speicherFehler.value)

        assertEquals(SpeicherErgebnis.GEAENDERT, vm.speichern())
        assertEquals("Neu", echt.stand.value.single().name)
    }

    /** Wirft bei den ersten [werfenBis] Aufrufen von speichern() eine IOException. */
    private fun werfendesRepository(echt: FakeBelohnungRepository, werfenBis: Int): BelohnungRepository {
        var aufrufe = 0
        return object : BelohnungRepository by echt {
            override suspend fun speichern(belohnung: Belohnung): Long {
                if (++aufrufe <= werfenBis) throw IOException("Datenbank nicht erreichbar")
                return echt.speichern(belohnung)
            }
        }
    }

    @Test
    fun `eine neue gueltige Belohnung wird angelegt`() = runTest(dispatcher) {
        val repo = FakeBelohnungRepository()
        val vm = viewModel(repo)
        vm.laden(0L).join()
        vm.aendern { it.copy(name = "Eis", symbol = "icecream") }

        assertEquals(SpeicherErgebnis.ANGELEGT, vm.speichern())
        assertEquals(listOf("Eis" to "icecream"), repo.stand.value.map { it.name to it.symbol })
    }

    @Test
    fun `eine bestehende Belohnung meldet geaendert und behaelt ihr Symbol`() = runTest(dispatcher) {
        val repo = FakeBelohnungRepository(listOf(kino))
        val vm = viewModel(repo)
        vm.laden(1L).join()
        vm.aendern { it.preisAendern(+1) }

        assertEquals(SpeicherErgebnis.GEAENDERT, vm.speichern())
        assertEquals(kino.copy(preisPunkte = 210), repo.stand.value.single())
    }

    @Test
    fun `ein fehlerhafter Entwurf wird nicht gespeichert`() = runTest(dispatcher) {
        val repo = FakeBelohnungRepository()
        val vm = viewModel(repo)
        vm.laden(0L).join()

        assertNull(vm.speichern())
        assertEquals(listOf(BelohnungFehler.NAME_LEER), vm.fehler.value)
        assertEquals(emptyList(), repo.stand.value)
    }

    @Test
    fun `Fehler bleiben bis zum ersten Speicherversuch stumm und werden danach live geprueft`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.laden(0L).join()
        vm.aendern { it.copy(preisPunkte = 0) }
        assertEquals(emptyList(), vm.fehler.value)

        vm.speichern()
        vm.aendern { it.copy(name = "Eis", preisPunkte = 50) }
        assertEquals(emptyList(), vm.fehler.value)

        vm.aendern { it.copy(name = "") }
        assertEquals(listOf(BelohnungFehler.NAME_LEER), vm.fehler.value)
    }

    @Test
    fun `ein zweites laden ueberschreibt ungespeicherte Aenderungen nicht`() = runTest(dispatcher) {
        val vm = viewModel(FakeBelohnungRepository(listOf(kino)))
        vm.laden(1L).join()
        vm.aendern { it.copy(name = "Neu") }

        vm.laden(1L).join()

        assertEquals("Neu", vm.entwurf.value.name)
    }

    @Test
    fun `zwei ueberlappende Speichervorgaenge legen nur eine Belohnung an`() = runTest(dispatcher) {
        val repo = FakeBelohnungRepository()
        val vm = viewModel(LangsamesRepository(repo))
        vm.laden(0L).join()
        vm.aendern { it.copy(name = "Eis") }

        val ergebnisse = listOf(async { vm.speichern() }, async { vm.speichern() }).awaitAll()

        assertEquals(1, repo.stand.value.size)
        assertEquals(setOf(SpeicherErgebnis.ANGELEGT, null), ergebnisse.toSet())
    }

    @Test
    fun `ein spaeter zweiter Speichern-Tipp aktualisiert statt neu anzulegen`() = runTest(dispatcher) {
        val repo = FakeBelohnungRepository()
        val vm = viewModel(repo)
        vm.laden(0L).join()
        vm.aendern { it.copy(name = "Eis") }

        assertEquals(SpeicherErgebnis.ANGELEGT, vm.speichern())
        assertEquals(SpeicherErgebnis.GEAENDERT, vm.speichern())
        assertEquals(1, repo.stand.value.size)
    }
}
