package de.vanhake.dayorganizer.ui.statistik

import app.cash.turbine.test
import de.vanhake.dayorganizer.FakeAktivitaetRepository
import de.vanhake.dayorganizer.FakeBuchungRepository
import de.vanhake.dayorganizer.FakeFaelligkeitRepository
import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Buchung
import de.vanhake.dayorganizer.domain.model.BuchungsArt
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import de.vanhake.dayorganizer.domain.model.Zeitplan
import de.vanhake.dayorganizer.domain.rechner.StatistikRechner
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

class StatistikViewModelTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val heute = LocalDate.of(2026, 3, 10)
    private val uhr = Clock.fixed(heute.atTime(12, 0).atZone(zone).toInstant(), zone)
    private val dispatcher = StandardTestDispatcher()

    @Before fun vorher() = Dispatchers.setMain(dispatcher)
    @After fun nachher() = Dispatchers.resetMain()

    private val tabletten = Aktivitaet(
        id = 1, name = "Tabletten", farbe = 1, basisPunkte = 10,
        zeitplan = Zeitplan.FesteZeiten(listOf(LocalTime.of(8, 0)), DayOfWeek.entries.toSet()),
        erstelltAm = LocalDate.of(2026, 1, 1),
    )

    private fun tag(versatz: Long, status: FaelligkeitStatus) = Faelligkeit(
        id = versatz + 1, aktivitaetId = 1,
        periodeVon = heute.minusDays(versatz), periodeBis = heute.minusDays(versatz),
        geplantFuer = heute.minusDays(versatz).atTime(8, 0), sollAnzahl = 1, status = status,
    )

    @Test
    fun `der Zustand fasst Quote und Serie je Aktivitaet zusammen`() = runTest(dispatcher) {
        val vm = StatistikViewModel(
            aktivitaeten = FakeAktivitaetRepository(listOf(tabletten)),
            faelligkeiten = FakeFaelligkeitRepository(
                listOf(
                    tag(0, FaelligkeitStatus.ERLEDIGT),
                    tag(1, FaelligkeitStatus.ERLEDIGT),
                    tag(2, FaelligkeitStatus.VERPASST),
                ),
            ),
            buchungen = FakeBuchungRepository(
                listOf(
                    Buchung(
                        art = BuchungsArt.ERLEDIGUNG, betrag = 10,
                        zeitpunkt = LocalDateTime.of(2026, 3, 9, 8, 5),
                    ),
                ),
            ),
            rechner = StatistikRechner(),
            uhr = uhr,
        )

        vm.zustand.test {
            skipItems(1)
            val zustand = awaitItem()
            val zeile = zustand.zeilen.single()
            assertEquals("Tabletten", zeile.name)
            assertEquals(2, zeile.statistik.erledigt)
            assertEquals(1, zeile.statistik.verpasst)
            assertEquals(30, zustand.bilanzen.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ein anderer Zeitraum aendert die Anzahl der Tagesbilanzen`() = runTest(dispatcher) {
        val vm = StatistikViewModel(
            aktivitaeten = FakeAktivitaetRepository(listOf(tabletten)),
            faelligkeiten = FakeFaelligkeitRepository(),
            buchungen = FakeBuchungRepository(),
            rechner = StatistikRechner(),
            uhr = uhr,
        )

        vm.zustand.test {
            skipItems(2)
            vm.zeitraumWaehlen(90)
            assertEquals(90, awaitItem().bilanzen.size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
