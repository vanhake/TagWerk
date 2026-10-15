@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package de.vanhake.dayorganizer.ui.aktivitaeten

import de.vanhake.dayorganizer.FakeAktivitaetRepository
import de.vanhake.dayorganizer.FakeErinnerungRepository
import de.vanhake.dayorganizer.FakeFaelligkeitRepository
import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Zeitplan
import de.vanhake.dayorganizer.domain.rechner.SerienRechner
import de.vanhake.dayorganizer.domain.rechner.ZeitplanRechner
import de.vanhake.dayorganizer.domainuse.AktivitaetNeuPlanen
import de.vanhake.dayorganizer.domainuse.FaelligkeitenMaterialisieren
import de.vanhake.dayorganizer.ui.gemeinsam.Hinweistoast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AktivitaetenViewModelTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val heute = LocalDate.of(2026, 3, 2)
    private val uhr = Clock.fixed(LocalDateTime.of(heute, LocalTime.NOON).atZone(zone).toInstant(), zone)
    private val dispatcher = StandardTestDispatcher()

    @Before fun vorher() = Dispatchers.setMain(dispatcher)
    @After fun nachher() = Dispatchers.resetMain()

    private val tabletten = Aktivitaet(
        id = 1, name = "Tabletten", farbe = 1, basisPunkte = 10,
        zeitplan = Zeitplan.FesteZeiten(listOf(LocalTime.of(8, 0)), DayOfWeek.entries.toSet()),
        erstelltAm = LocalDate.of(2026, 1, 1),
    )

    private fun viewModel(aktivitaeten: FakeAktivitaetRepository = FakeAktivitaetRepository()): AktivitaetenViewModel {
        val faelligkeiten = FakeFaelligkeitRepository()
        val erinnerungen = FakeErinnerungRepository()
        val materialisieren = FaelligkeitenMaterialisieren(
            aktivitaeten, faelligkeiten, erinnerungen, ZeitplanRechner(), uhr,
        )
        val neuPlanen = AktivitaetNeuPlanen(
            faelligkeiten, erinnerungen, materialisieren, uhr,
            alarmNeuPlanen = {},
            widgetAnstossen = {},
        )
        return AktivitaetenViewModel(aktivitaeten, faelligkeiten, neuPlanen, SerienRechner(), uhr)
    }

    @Test
    fun `nach dem Anlegen erscheint ein Toast ohne Rueckgaengig`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.gespeichertMelden(SpeicherErgebnis.ANGELEGT)
        testScheduler.runCurrent()

        assertEquals(Hinweistoast("Aktivität angelegt", kannRueckgaengig = false), vm.toast.value)
    }

    @Test
    fun `nach dem Aendern erscheint der Geaendert-Toast`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.gespeichertMelden(SpeicherErgebnis.GEAENDERT)
        testScheduler.runCurrent()

        assertEquals("Änderungen gespeichert", vm.toast.value?.text)
    }

    @Test
    fun `der Toast verschwindet nach der Anzeigedauer`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.gespeichertMelden(SpeicherErgebnis.ANGELEGT)
        testScheduler.runCurrent()

        testScheduler.advanceTimeBy(3_801)
        testScheduler.runCurrent()

        assertNull(vm.toast.value)
    }

    @Test
    fun `ein neuer Toast ersetzt den alten samt dessen Ablauf`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.gespeichertMelden(SpeicherErgebnis.ANGELEGT)
        testScheduler.runCurrent()
        testScheduler.advanceTimeBy(3_000)

        vm.gespeichertMelden(SpeicherErgebnis.GEAENDERT)
        testScheduler.runCurrent()
        // Der erste Toast waere jetzt abgelaufen -- sein Ablauf darf den zweiten nicht loeschen.
        testScheduler.advanceTimeBy(1_000)
        testScheduler.runCurrent()

        assertEquals("Änderungen gespeichert", vm.toast.value?.text)
    }

    @Test
    fun `Archivieren archiviert und meldet den Namen`() = runTest(dispatcher) {
        val repo = FakeAktivitaetRepository(listOf(tabletten))
        val vm = viewModel(repo)

        vm.archivieren(1L).join()

        assertEquals(heute, repo.stand.value.single().archiviertAm)
        assertEquals(Hinweistoast("Tabletten archiviert", kannRueckgaengig = false), vm.toast.value)
    }
}
