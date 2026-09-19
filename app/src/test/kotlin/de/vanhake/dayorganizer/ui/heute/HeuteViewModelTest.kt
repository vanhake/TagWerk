package de.vanhake.dayorganizer.ui.heute

import app.cash.turbine.test
import de.vanhake.dayorganizer.FakeAktivitaetRepository
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
    ): HeuteViewModel {
        val aktivitaeten = FakeAktivitaetRepository(listOf(tabletten))
        return HeuteViewModel(
            aktivitaeten = aktivitaeten,
            faelligkeiten = faelligkeiten,
            buchungen = buchungen,
            erledigenUseCase = FaelligkeitErledigen(
                aktivitaeten, faelligkeiten, buchungen, PunkteRechner(), SerienRechner(),
                FakeTransaktionen(), { Einstellungen(tagesbonusPunkte = 0) }, uhr,
            ),
            auslassenUseCase = FaelligkeitAuslassen(faelligkeiten, uhr),
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
    fun `Erledigen aktualisiert Fortschritt und Kontostand`() = runTest(dispatcher) {
        val faelligkeiten = FakeFaelligkeitRepository(listOf(morgens))
        val buchungen = FakeBuchungRepository()
        val vm = viewModel(faelligkeiten, buchungen)

        vm.zustand.test {
            skipItems(2)
            vm.erledigen(10)
            val danach = awaitItem()
            assertEquals(1, danach.erledigtHeute)
            assertEquals(10, danach.kontostand)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
