package de.vanhake.dayorganizer.ui.aktivitaeten

import de.vanhake.dayorganizer.FakeAktivitaetRepository
import de.vanhake.dayorganizer.FakeErinnerungRepository
import de.vanhake.dayorganizer.FakeFaelligkeitRepository
import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Zeitplan
import de.vanhake.dayorganizer.domain.rechner.ZeitplanRechner
import de.vanhake.dayorganizer.domainuse.AktivitaetNeuPlanen
import de.vanhake.dayorganizer.domainuse.FaelligkeitenMaterialisieren
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

/** Deckt Finding 3 der Review ab: der Vorgabewert fuer das Kulanzfenster einer neuen
 * Aktivitaet muss aus den Einstellungen kommen, eine bestehende Aktivitaet behaelt
 * dagegen ihren eigenen gespeicherten Wert unangetastet. */
class AktivitaetBearbeitenViewModelTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val uhr = Clock.fixed(
        LocalDateTime.of(2026, 3, 2, 12, 0).atZone(zone).toInstant(),
        zone,
    )
    private val dispatcher = StandardTestDispatcher()

    @Before fun vorher() = Dispatchers.setMain(dispatcher)
    @After fun nachher() = Dispatchers.resetMain()

    private fun viewModel(
        aktivitaeten: FakeAktivitaetRepository = FakeAktivitaetRepository(),
        standardKulanz: Int = 60,
    ): AktivitaetBearbeitenViewModel {
        val faelligkeiten = FakeFaelligkeitRepository()
        val erinnerungen = FakeErinnerungRepository()
        val materialisieren = FaelligkeitenMaterialisieren(
            aktivitaeten, faelligkeiten, erinnerungen, ZeitplanRechner(), uhr,
        )
        val neuPlanen = AktivitaetNeuPlanen(faelligkeiten, erinnerungen, materialisieren, uhr) {}
        return AktivitaetBearbeitenViewModel(
            aktivitaeten = aktivitaeten,
            neuPlanen = neuPlanen,
            standardKulanzLesen = { standardKulanz },
            uhr = uhr,
        )
    }

    @Test
    fun `ein neuer Entwurf uebernimmt die eingestellte Standard-Kulanz`() = runTest(dispatcher) {
        val vm = viewModel(standardKulanz = 90)

        vm.laden(0L).join()

        assertEquals("90", vm.entwurf.value.kulanzMinutenText)
    }

    @Test
    fun `ein geladener Entwurf behaelt seine eigene Kulanz`() = runTest(dispatcher) {
        val bestehend = Aktivitaet(
            id = 1, name = "Tabletten", farbe = 1, basisPunkte = 10, kulanzMinuten = 30,
            zeitplan = Zeitplan.FesteZeiten(listOf(LocalTime.of(8, 0)), DayOfWeek.entries.toSet()),
            erstelltAm = LocalDate.of(2026, 1, 1),
        )
        val vm = viewModel(FakeAktivitaetRepository(listOf(bestehend)), standardKulanz = 90)

        vm.laden(1L).join()

        assertEquals("30", vm.entwurf.value.kulanzMinutenText)
    }
}
