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
import de.vanhake.dayorganizer.domain.rechner.TagesBilanz
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

    private val sport = Aktivitaet(
        id = 2, name = "Sport", farbe = 2, basisPunkte = 20,
        zeitplan = Zeitplan.Wochenziel(3),
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
            rechenDispatcher = dispatcher,
        )

        vm.zustand.test {
            skipItems(1)
            val zustand = awaitItem()
            val zeile = zustand.zeilen.single()
            assertEquals("Tabletten", zeile.name)
            assertEquals(2, zeile.statistik.erledigt)
            assertEquals(1, zeile.statistik.verpasst)
            // Quote und Serien stehen im Namen dieses Tests und wurden vorher nicht
            // geprueft -- ein fuerAktivitaet, das hier konstant 0 liefert, waere
            // unbemerkt geblieben.
            assertEquals(2f / 3f, zeile.statistik.quote)
            assertEquals(2, zeile.statistik.aktuelleSerie)
            assertEquals(2, zeile.statistik.laengsteSerie)
            assertEquals(30, zustand.zeitraumTage)
            assertEquals(30, zustand.bilanzen.size)
            // Die blosse Anzahl ist eine reine Funktion des Zeitraums und sagt ueber
            // den Inhalt nichts: ohne diese Zusicherungen bliebe der Test gruen, wenn
            // das ViewModel eine leere Faelligkeitsliste an den Rechner reichte.
            assertEquals(heute.minusDays(29), zustand.bilanzen.first().tag)
            assertEquals(heute, zustand.bilanzen.last().tag)
            assertEquals(TagesBilanz(heute, erledigt = 1, gesamt = 1), zustand.bilanzen.last())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `der Zustand traegt Symbol, Wochen-Einheit und Gesamtquote`() = runTest(dispatcher) {
        val vm = StatistikViewModel(
            aktivitaeten = FakeAktivitaetRepository(
                listOf(tabletten, sport.copy(symbol = "fitness_center")),
            ),
            faelligkeiten = FakeFaelligkeitRepository(
                listOf(
                    tag(0, FaelligkeitStatus.ERLEDIGT),
                    tag(1, FaelligkeitStatus.ERLEDIGT),
                    tag(2, FaelligkeitStatus.VERPASST),
                ),
            ),
            buchungen = FakeBuchungRepository(),
            rechner = StatistikRechner(),
            uhr = uhr,
            rechenDispatcher = dispatcher,
        )

        vm.zustand.test {
            skipItems(1)
            val zustand = awaitItem()
            val zeilen = zustand.zeilen.associateBy { it.name }
            assertEquals(true, zeilen.getValue("Sport").serieInWochen)
            assertEquals("fitness_center", zeilen.getValue("Sport").symbol)
            assertEquals(false, zeilen.getValue("Tabletten").serieInWochen)
            assertEquals(67, zustand.gesamtQuote)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // Vor der ersten Berechnung darf der Screen keine Leertexte zeigen ("Noch keine
    // Aktivitaeten", "0 max."): der Anfangswert ist deshalb als ungeladen markiert.
    @Test
    fun `der Anfangszustand ist ungeladen, der berechnete geladen`() = runTest(dispatcher) {
        val vm = StatistikViewModel(
            aktivitaeten = FakeAktivitaetRepository(listOf(tabletten)),
            faelligkeiten = FakeFaelligkeitRepository(),
            buchungen = FakeBuchungRepository(),
            rechner = StatistikRechner(),
            uhr = uhr,
            rechenDispatcher = dispatcher,
        )

        vm.zustand.test {
            assertEquals(false, awaitItem().geladen)
            assertEquals(true, awaitItem().geladen)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ohne Faelligkeiten gibt es keine Gesamtquote`() = runTest(dispatcher) {
        val vm = StatistikViewModel(
            aktivitaeten = FakeAktivitaetRepository(listOf(tabletten)),
            faelligkeiten = FakeFaelligkeitRepository(),
            buchungen = FakeBuchungRepository(),
            rechner = StatistikRechner(),
            uhr = uhr,
            rechenDispatcher = dispatcher,
        )

        vm.zustand.test {
            skipItems(1)
            assertEquals(null, awaitItem().gesamtQuote)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // Die Quote je Aktivitaet liest bewusst die VOLLE Historie, nicht nur den
    // gewaehlten Zeitraum -- so steht es im Design. Ohne einen Eintrag ausserhalb
    // des Fensters liefern historie() und fuerZeitraum() dieselbe Liste, und die
    // Entscheidung waere durch nichts festgenagelt.
    @Test
    fun `die Quote je Aktivitaet zaehlt auch Faelligkeiten vor dem Zeitraum`() = runTest(dispatcher) {
        val altErledigt = Faelligkeit(
            id = 900, aktivitaetId = 1,
            periodeVon = heute.minusDays(200), periodeBis = heute.minusDays(200),
            geplantFuer = null, sollAnzahl = 1, status = FaelligkeitStatus.ERLEDIGT,
        )
        val fremd = Faelligkeit(
            id = 901, aktivitaetId = 2,
            periodeVon = heute, periodeBis = heute,
            geplantFuer = null, sollAnzahl = 1, status = FaelligkeitStatus.VERPASST,
        )
        val vm = StatistikViewModel(
            aktivitaeten = FakeAktivitaetRepository(listOf(tabletten, sport)),
            faelligkeiten = FakeFaelligkeitRepository(
                listOf(tag(0, FaelligkeitStatus.ERLEDIGT), altErledigt, fremd),
            ),
            buchungen = FakeBuchungRepository(),
            rechner = StatistikRechner(),
            uhr = uhr,
            rechenDispatcher = dispatcher,
        )

        vm.zustand.test {
            skipItems(1)
            val zeilen = awaitItem().zeilen.associateBy { it.name }
            // 200 Tage alt und damit weit ausserhalb des 30-Tage-Fensters.
            assertEquals(2, zeilen.getValue("Tabletten").statistik.erledigt)
            assertEquals(1, zeilen.getValue("Sport").statistik.verpasst)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // Der Punkteverlauf soll den Kontostand zeigen, nicht die Netto-Aenderung seit
    // Zeitraumbeginn. Dafuer braucht der Rechner alle Buchungen, auch die aelteren:
    // aus ihnen bildet er den Anfangsbestand.
    @Test
    fun `der Punkteverlauf beginnt beim Stand vor dem Zeitraum`() = runTest(dispatcher) {
        val vm = StatistikViewModel(
            aktivitaeten = FakeAktivitaetRepository(),
            faelligkeiten = FakeFaelligkeitRepository(),
            buchungen = FakeBuchungRepository(
                listOf(
                    Buchung(
                        art = BuchungsArt.ERLEDIGUNG, betrag = 100,
                        zeitpunkt = heute.minusDays(60).atTime(8, 0),
                    ),
                    Buchung(
                        art = BuchungsArt.ERLEDIGUNG, betrag = 10,
                        zeitpunkt = heute.minusDays(1).atTime(8, 0),
                    ),
                ),
            ),
            rechner = StatistikRechner(),
            uhr = uhr,
            rechenDispatcher = dispatcher,
        )

        vm.zustand.test {
            skipItems(1)
            val verlauf = awaitItem().punkteVerlauf
            assertEquals(30, verlauf.size)
            // Die 100 Punkte von vor 60 Tagen liegen ausserhalb des Fensters und
            // muessen trotzdem im Stand stecken. Mit fuerZeitraum statt alle waere
            // hier ueberall 10 statt 110 herausgekommen.
            assertEquals(heute.minusDays(29) to 100, verlauf.first())
            assertEquals(heute to 110, verlauf.last())
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
            rechenDispatcher = dispatcher,
        )

        vm.zustand.test {
            skipItems(2)
            vm.zeitraumWaehlen(90)
            val zustand = awaitItem()
            assertEquals(90, zustand.bilanzen.size)
            // zeitraumTage steuert, welcher Knopf markiert ist -- ohne diese Zeile
            // bliebe der Test gruen, wenn der Wert nie im Zustand ankaeme.
            assertEquals(90, zustand.zeitraumTage)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
