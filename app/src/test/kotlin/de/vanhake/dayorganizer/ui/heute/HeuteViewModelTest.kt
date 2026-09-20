package de.vanhake.dayorganizer.ui.heute

import app.cash.turbine.test
import de.vanhake.dayorganizer.FakeAktivitaetRepository
import de.vanhake.dayorganizer.FakeBelohnungRepository
import de.vanhake.dayorganizer.FakeBuchungRepository
import de.vanhake.dayorganizer.FakeFaelligkeitRepository
import de.vanhake.dayorganizer.FakeTransaktionen
import de.vanhake.dayorganizer.data.Einstellungen
import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.Zeitplan
import de.vanhake.dayorganizer.domain.rechner.PunkteRechner
import de.vanhake.dayorganizer.domain.rechner.SerienRechner
import de.vanhake.dayorganizer.domainuse.FaelligkeitAuslassen
import de.vanhake.dayorganizer.domainuse.FaelligkeitErledigen
import de.vanhake.dayorganizer.platform.notification.Erinnerungsanzeige
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

/** Ein Clock, dessen Instant sich waehrend des Tests verschieben laesst. */
private class VerstellbareUhr(var instant: java.time.Instant, private val zone: ZoneId) : Clock() {
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = VerstellbareUhr(instant, zone)
    override fun instant(): java.time.Instant = instant
}

private class FakeAnzeige : Erinnerungsanzeige {
    val versteckt = mutableListOf<Long>()
    override fun zeige(faelligkeit: Faelligkeit, aktivitaet: Aktivitaet) {}
    override fun verstecke(faelligkeitId: Long) {
        versteckt += faelligkeitId
    }
}

class HeuteViewModelTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val uhr = Clock.fixed(
        LocalDateTime.of(2026, 3, 2, 12, 0).atZone(zone).toInstant(),
        zone,
    )
    private val dispatcher = StandardTestDispatcher()

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before fun vorher() = Dispatchers.setMain(dispatcher)

    @OptIn(ExperimentalCoroutinesApi::class)
    @After fun nachher() = Dispatchers.resetMain()

    private val tabletten = Aktivitaet(
        id = 1, name = "Tabletten", farbe = 1, basisPunkte = 10, puenktlichkeitsBonus = 0,
        zeitplan = Zeitplan.FesteZeiten(listOf(LocalTime.of(8, 0)), DayOfWeek.entries.toSet()),
        erstelltAm = LocalDate.of(2026, 1, 1),
    )
    private val morgens = Faelligkeit(
        id = 10, aktivitaetId = 1,
        periodeVon = LocalDate.of(2026, 3, 2), periodeBis = LocalDate.of(2026, 3, 2),
        geplantFuer = LocalDateTime.of(2026, 3, 2, 8, 0), sollAnzahl = 1,
    )

    private fun viewModel(
        faelligkeiten: FakeFaelligkeitRepository,
        buchungen: FakeBuchungRepository,
        uhr: Clock = this.uhr,
        anzeige: Erinnerungsanzeige = FakeAnzeige(),
        belohnungen: FakeBelohnungRepository = FakeBelohnungRepository(),
    ): HeuteViewModel {
        val aktivitaeten = FakeAktivitaetRepository(listOf(tabletten))
        return HeuteViewModel(
            aktivitaeten = aktivitaeten,
            faelligkeiten = faelligkeiten,
            buchungen = buchungen,
            belohnungen = belohnungen,
            erledigenUseCase = FaelligkeitErledigen(
                aktivitaeten, faelligkeiten, buchungen, PunkteRechner(), SerienRechner(),
                FakeTransaktionen(), { Einstellungen(tagesbonusPunkte = 0) }, uhr,
            ),
            auslassenUseCase = FaelligkeitAuslassen(faelligkeiten, uhr),
            anzeige = anzeige,
            alarmNeuPlanen = {},
            hinweiseLesen = { emptyList() },
            uhr = uhr,
        )
    }

    @Test
    fun `der Zustand zeigt die heutigen Faelligkeiten`() = runTest(dispatcher) {
        val vm = viewModel(FakeFaelligkeitRepository(listOf(morgens)), FakeBuchungRepository())

        vm.zustand.test {
            assertEquals(HeuteZustand(), awaitItem())
            val gefuellt = awaitItem()
            assertEquals(1, gefuellt.gesamtHeute)
            assertEquals(listOf(10L), gefuellt.ueberfaellig.map { it.faelligkeitId })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `nach Mitternacht zeigt der Screen den neuen Tag`() = runTest(dispatcher) {
        val gestern = Faelligkeit(
            id = 20, aktivitaetId = 1,
            periodeVon = LocalDate.of(2026, 3, 2), periodeBis = LocalDate.of(2026, 3, 2),
            geplantFuer = LocalDateTime.of(2026, 3, 2, 8, 0), sollAnzahl = 1,
        )
        val heute = Faelligkeit(
            id = 21, aktivitaetId = 1,
            periodeVon = LocalDate.of(2026, 3, 3), periodeBis = LocalDate.of(2026, 3, 3),
            geplantFuer = LocalDateTime.of(2026, 3, 3, 8, 0), sollAnzahl = 1,
        )
        val verstellbareUhr = VerstellbareUhr(
            LocalDateTime.of(2026, 3, 2, 23, 59).atZone(zone).toInstant(),
            zone,
        )
        val vm = viewModel(
            FakeFaelligkeitRepository(listOf(gestern, heute)),
            FakeBuchungRepository(),
            uhr = verstellbareUhr,
        )

        vm.zustand.test {
            assertEquals(HeuteZustand(), awaitItem())
            val vorMitternacht = awaitItem()
            assertEquals(listOf(20L), vorMitternacht.ueberfaellig.map { it.faelligkeitId })

            verstellbareUhr.instant = LocalDateTime.of(2026, 3, 3, 0, 1)
                .atZone(zone).toInstant()
            vm.beimSichtbarwerden()

            val nachMitternacht = awaitItem()
            assertEquals(1, nachMitternacht.gesamtHeute)
            assertEquals(listOf(21L), nachMitternacht.spaeter.map { it.faelligkeitId })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Erledigen aktualisiert Fortschritt und Kontostand`() = runTest(dispatcher) {
        val faelligkeiten = FakeFaelligkeitRepository(listOf(morgens))
        val buchungen = FakeBuchungRepository()
        val anzeige = FakeAnzeige()
        val vm = viewModel(faelligkeiten, buchungen, anzeige = anzeige)

        vm.zustand.test {
            skipItems(2)
            vm.erledigen(10)
            val danach = awaitItem()
            assertEquals(1, danach.erledigtHeute)
            assertEquals(10, danach.kontostand)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf(10L), anzeige.versteckt)
    }

    @Test
    fun `Auslassen versteckt die Benachrichtigung, damit sie nicht stehen bleibt`() = runTest(dispatcher) {
        val faelligkeiten = FakeFaelligkeitRepository(listOf(morgens))
        val anzeige = FakeAnzeige()
        val vm = viewModel(faelligkeiten, FakeBuchungRepository(), anzeige = anzeige)

        vm.zustand.test {
            skipItems(2)
            vm.auslassen(10)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(listOf(10L), anzeige.versteckt)
    }
}
