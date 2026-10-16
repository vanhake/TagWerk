package de.vanhake.dayorganizer.ui.aktivitaeten

import de.vanhake.dayorganizer.FakeAktivitaetRepository
import de.vanhake.dayorganizer.FakeErinnerungRepository
import de.vanhake.dayorganizer.FakeFaelligkeitRepository
import de.vanhake.dayorganizer.data.repository.AktivitaetRepository
import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Zeitplan
import de.vanhake.dayorganizer.domain.rechner.ZeitplanRechner
import de.vanhake.dayorganizer.domainuse.AktivitaetNeuPlanen
import de.vanhake.dayorganizer.domainuse.FaelligkeitenMaterialisieren
import de.vanhake.dayorganizer.ui.gemeinsam.SpeicherErgebnis
import de.vanhake.dayorganizer.ui.gemeinsam.SpeicherFehler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import java.io.IOException
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        aktivitaeten: AktivitaetRepository = FakeAktivitaetRepository(),
        standardKulanz: Int = 60,
    ): AktivitaetBearbeitenViewModel {
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
        return AktivitaetBearbeitenViewModel(
            aktivitaeten = aktivitaeten,
            neuPlanen = neuPlanen,
            standardKulanzLesen = { standardKulanz },
            uhr = uhr,
        )
    }

    @Test
    fun `fehlt der Datensatz beim Speichern bleibt der Editor mit Entwurf offen`() = runTest(dispatcher) {
        val repo = FakeAktivitaetRepository(listOf(bestehendeAktivitaet))
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
        val echt = FakeAktivitaetRepository()
        val vm = viewModel(werfendesRepository(echt, werfenBis = Int.MAX_VALUE))
        vm.laden(0L).join()
        vm.aendern { it.copy(name = "Lesen") }

        assertNull(vm.speichern())

        assertEquals(SpeicherFehler.FEHLGESCHLAGEN, vm.speicherFehler.value)
    }

    @Test
    fun `nach einem Fehlschlag laesst sich erneut speichern`() = runTest(dispatcher) {
        val echt = FakeAktivitaetRepository(listOf(bestehendeAktivitaet))
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
    private fun werfendesRepository(echt: FakeAktivitaetRepository, werfenBis: Int): AktivitaetRepository {
        var aufrufe = 0
        return object : AktivitaetRepository by echt {
            override suspend fun speichern(aktivitaet: Aktivitaet): Long {
                if (++aufrufe <= werfenBis) throw IOException("Datenbank nicht erreichbar")
                return echt.speichern(aktivitaet)
            }
        }
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

    private val bestehendeAktivitaet = Aktivitaet(
        id = 1, name = "Tabletten", farbe = 1, basisPunkte = 10,
        zeitplan = Zeitplan.FesteZeiten(listOf(LocalTime.of(8, 0)), DayOfWeek.entries.toSet()),
        erstelltAm = LocalDate.of(2026, 1, 1),
    )

    @Test
    fun `ein neuer gueltiger Entwurf wird angelegt`() = runTest(dispatcher) {
        val repo = FakeAktivitaetRepository()
        val vm = viewModel(repo)
        vm.laden(0L).join()
        vm.aendern { it.copy(name = "Lesen") }

        assertEquals(SpeicherErgebnis.ANGELEGT, vm.speichern())
        assertEquals("Lesen", repo.stand.value.single().name)
    }

    @Test
    fun `ein bestehender Entwurf meldet geaendert`() = runTest(dispatcher) {
        val repo = FakeAktivitaetRepository(listOf(bestehendeAktivitaet))
        val vm = viewModel(repo)
        vm.laden(1L).join()
        vm.aendern { it.punkteAendern(+1) }

        assertEquals(SpeicherErgebnis.GEAENDERT, vm.speichern())
        assertEquals(11, repo.stand.value.single().basisPunkte)
    }

    @Test
    fun `ein fehlerhafter Entwurf wird nicht gespeichert`() = runTest(dispatcher) {
        val repo = FakeAktivitaetRepository()
        val vm = viewModel(repo)
        vm.laden(0L).join()

        assertNull(vm.speichern())
        assertEquals(listOf(EntwurfFehler.NAME_LEER), vm.fehler.value)
        assertTrue(repo.stand.value.isEmpty())
    }

    @Test
    fun `vor dem ersten Speicherversuch bleiben Fehler stumm`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.laden(0L).join()

        vm.aendern { it.copy(wochentage = emptySet()) }

        assertEquals(emptyList(), vm.fehler.value)
    }

    @Test
    fun `nach einem gescheiterten Speichern verschwindet ein behobener Fehler sofort`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.laden(0L).join()
        vm.speichern()

        vm.aendern { it.copy(name = "Lesen") }

        assertEquals(emptyList(), vm.fehler.value)
    }

    @Test
    fun `zwei gleichzeitige Speichern legen genau eine Aktivitaet an`() = runTest(dispatcher) {
        val echt = FakeAktivitaetRepository()
        // Das Fake suspendiert nie; erst eine echte Suspension macht aus dem zweiten Aufruf
        // einen gleichzeitigen, wie bei Room.
        val langsam = object : AktivitaetRepository by echt {
            override suspend fun speichern(aktivitaet: Aktivitaet): Long {
                delay(10)
                return echt.speichern(aktivitaet)
            }
        }
        val vm = viewModel(langsam)
        vm.laden(0L).join()
        vm.aendern { it.copy(name = "Lesen") }

        val ergebnisse = listOf(async { vm.speichern() }, async { vm.speichern() }).awaitAll()

        assertEquals(1, echt.stand.value.size)
        assertEquals(listOf(SpeicherErgebnis.ANGELEGT, null), ergebnisse.sortedBy { it == null })
    }

    @Test
    fun `ein zweites Laden mit derselben id verwirft keine Aenderungen`() = runTest(dispatcher) {
        val vm = viewModel(FakeAktivitaetRepository(listOf(bestehendeAktivitaet)))
        vm.laden(1L).join()
        vm.aendern { it.copy(name = "Neu") }

        vm.laden(1L).join()

        assertEquals("Neu", vm.entwurf.value.name)
    }

    @Test
    fun `ein zweites Laden einer neuen Aktivitaet verwirft keine Aenderungen`() = runTest(dispatcher) {
        val vm = viewModel(standardKulanz = 90)
        vm.laden(0L).join()
        vm.aendern { it.copy(kulanzMinutenText = "15") }

        vm.laden(0L).join()

        assertEquals("15", vm.entwurf.value.kulanzMinutenText)
    }

    @Test
    fun `nach einem Speicherversuch wird jede Aenderung neu geprueft`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.laden(0L).join()
        vm.speichern()
        vm.aendern { it.copy(name = "Lesen") }
        assertEquals(emptyList(), vm.fehler.value)

        vm.aendern { it.copy(wochentage = emptySet()) }

        assertEquals(listOf(EntwurfFehler.KEIN_WOCHENTAG), vm.fehler.value)
    }

    @Test
    fun `ein spaeter zweiter Tipp nach dem Speichern legt kein Duplikat an`() = runTest(dispatcher) {
        val repo = FakeAktivitaetRepository()
        val vm = viewModel(repo)
        vm.laden(0L).join()
        vm.aendern { it.copy(name = "Lesen") }

        val erstes = vm.speichern()
        val zweites = vm.speichern()

        assertEquals(1, repo.stand.value.size)
        assertEquals(SpeicherErgebnis.ANGELEGT, erstes)
        assertEquals(SpeicherErgebnis.GEAENDERT, zweites)
    }

    @Test
    fun `nach einem abgebrochenen Speichern ist die Sperre wieder frei`() = runTest(dispatcher) {
        val echt = FakeAktivitaetRepository()
        val langsam = object : AktivitaetRepository by echt {
            override suspend fun speichern(aktivitaet: Aktivitaet): Long {
                delay(10)
                return echt.speichern(aktivitaet)
            }
        }
        val vm = viewModel(langsam)
        vm.laden(0L).join()
        vm.aendern { it.copy(name = "Lesen") }

        val abgebrochen = async { vm.speichern() }
        runCurrent()
        abgebrochen.cancel()
        runCurrent()
        assertTrue(abgebrochen.isCancelled)
        assertTrue(echt.stand.value.isEmpty())

        assertEquals(SpeicherErgebnis.ANGELEGT, vm.speichern())
        assertEquals(1, echt.stand.value.size)
    }
}
