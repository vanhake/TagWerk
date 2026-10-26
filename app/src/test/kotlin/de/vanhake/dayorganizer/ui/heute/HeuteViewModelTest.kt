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
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import de.vanhake.dayorganizer.domain.model.Zeitplan
import de.vanhake.dayorganizer.domain.rechner.PunkteRechner
import de.vanhake.dayorganizer.domain.rechner.SerienRechner
import de.vanhake.dayorganizer.domainuse.FaelligkeitAuslassen
import de.vanhake.dayorganizer.domainuse.FaelligkeitAuslassenRueckgaengig
import de.vanhake.dayorganizer.domainuse.FaelligkeitErledigen
import de.vanhake.dayorganizer.domainuse.FaelligkeitErledigenRueckgaengig
import de.vanhake.dayorganizer.domainuse.FaelligkeitWiederOeffnen
import de.vanhake.dayorganizer.platform.notification.Erinnerungsanzeige
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    override fun alleVerstecken() {}
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
    private val sport = Aktivitaet(
        id = 2, name = "Sport", farbe = 2, basisPunkte = 20, puenktlichkeitsBonus = 0,
        zeitplan = Zeitplan.FesteZeiten(listOf(LocalTime.of(18, 0)), DayOfWeek.entries.toSet()),
        erstelltAm = LocalDate.of(2026, 1, 1),
    )
    private val abends = Faelligkeit(
        id = 11, aktivitaetId = 2,
        periodeVon = LocalDate.of(2026, 3, 2), periodeBis = LocalDate.of(2026, 3, 2),
        geplantFuer = LocalDateTime.of(2026, 3, 2, 18, 0), sollAnzahl = 1,
    )

    private fun viewModel(
        faelligkeiten: FakeFaelligkeitRepository,
        buchungen: FakeBuchungRepository,
        uhr: Clock = this.uhr,
        anzeige: Erinnerungsanzeige = FakeAnzeige(),
        belohnungen: FakeBelohnungRepository = FakeBelohnungRepository(),
        widgetAnstossen: suspend () -> Unit = {},
        alarmNeuPlanen: suspend () -> Unit = {},
        einstellungenLesen: suspend () -> Einstellungen = { Einstellungen(tagesbonusPunkte = 0) },
        aktivitaetenListe: List<Aktivitaet> = listOf(tabletten, sport),
    ): HeuteViewModel {
        val aktivitaeten = FakeAktivitaetRepository(aktivitaetenListe)
        val transaktionen = FakeTransaktionen()
        val erledigenUseCase = FaelligkeitErledigen(
            aktivitaeten, faelligkeiten, buchungen, PunkteRechner(), SerienRechner(),
            transaktionen, einstellungenLesen, uhr,
        )
        return HeuteViewModel(
            aktivitaeten = aktivitaeten,
            faelligkeiten = faelligkeiten,
            buchungen = buchungen,
            belohnungen = belohnungen,
            erledigenUseCase = erledigenUseCase,
            auslassenUseCase = FaelligkeitAuslassen(faelligkeiten, uhr),
            erledigenRueckgaengig = FaelligkeitErledigenRueckgaengig(faelligkeiten, buchungen, transaktionen, uhr),
            auslassenRueckgaengig = FaelligkeitAuslassenRueckgaengig(faelligkeiten),
            wiederOeffnenUseCase = FaelligkeitWiederOeffnen(faelligkeiten, buchungen, transaktionen, uhr),
            anzeige = anzeige,
            alarmNeuPlanen = alarmNeuPlanen,
            hinweiseLesen = { emptyList() },
            uhr = uhr,
            widgetAnstossen = widgetAnstossen,
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

    @Test
    fun `Erledigen stoesst das Widget an, damit der Startbildschirm nicht veraltet`() = runTest(dispatcher) {
        val faelligkeiten = FakeFaelligkeitRepository(listOf(morgens))
        var widgetAngestossen = 0
        val vm = viewModel(faelligkeiten, FakeBuchungRepository(), widgetAnstossen = { widgetAngestossen++ })

        vm.zustand.test {
            skipItems(2)
            vm.erledigen(10)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(1, widgetAngestossen)
    }

    @Test
    fun `Auslassen stoesst das Widget an, damit der Startbildschirm nicht veraltet`() = runTest(dispatcher) {
        val faelligkeiten = FakeFaelligkeitRepository(listOf(morgens))
        var widgetAngestossen = 0
        val vm = viewModel(faelligkeiten, FakeBuchungRepository(), widgetAnstossen = { widgetAngestossen++ })

        vm.zustand.test {
            skipItems(2)
            vm.auslassen(10)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(1, widgetAngestossen)
    }

    @Test
    fun `Erledigen zeigt einen Toast mit Rueckgaengig-Option`() = runTest(dispatcher) {
        val vm = viewModel(FakeFaelligkeitRepository(listOf(morgens)), FakeBuchungRepository())

        vm.erledigen(10)
        // Bewusst runCurrent() statt advanceUntilIdle(): Letzteres wuerde auch den erst in
        // 3.8s faelligen Auto-Dismiss-Job der Toast-Anzeige mitausfuehren (virtuelle Zeit
        // kennt keine Wartezeit) und den Toast damit vor der Zusicherung schon wieder loeschen.
        dispatcher.scheduler.runCurrent()

        assertEquals(true, vm.toast.value?.kannRueckgaengig)
    }

    @Test
    fun `ein Fehler beim Abhaken meldet sich als Toast und laesst die Benachrichtigung stehen`() = runTest(dispatcher) {
        val anzeige = FakeAnzeige()
        var alarme = 0
        var widgets = 0
        val vm = viewModel(
            FakeFaelligkeitRepository(listOf(morgens)), FakeBuchungRepository(),
            anzeige = anzeige,
            widgetAnstossen = { widgets++ },
            alarmNeuPlanen = { alarme++ },
            einstellungenLesen = { throw IOException("Einstellungen unlesbar") },
        )

        vm.erledigen(10)
        dispatcher.scheduler.runCurrent()

        assertEquals("Abhaken fehlgeschlagen", vm.toast.value?.text)
        assertEquals(false, vm.toast.value?.kannRueckgaengig)
        assertEquals(emptyList(), anzeige.versteckt)
        assertEquals(0, alarme)
        assertEquals(0, widgets)
    }

    @Test
    fun `scheitert das Neuplanen nach dem Abhaken bleibt die Buchung und der Toast erscheint`() = runTest(dispatcher) {
        val faelligkeiten = FakeFaelligkeitRepository(listOf(morgens))
        val vm = viewModel(
            faelligkeiten, FakeBuchungRepository(),
            alarmNeuPlanen = { throw IOException("Datenbank nicht erreichbar") },
        )

        vm.erledigen(10)
        dispatcher.scheduler.runCurrent()

        assertEquals(FaelligkeitStatus.ERLEDIGT, faelligkeiten.stand.value.single().status)
        assertEquals(true, vm.toast.value?.kannRueckgaengig)
    }

    @Test
    fun `Rueckgaengig macht die Erledigung und ihre Buchungen wieder rueckgaengig`() = runTest(dispatcher) {
        val faelligkeiten = FakeFaelligkeitRepository(listOf(morgens))
        val buchungen = FakeBuchungRepository()
        val vm = viewModel(faelligkeiten, buchungen)

        vm.erledigen(10)
        dispatcher.scheduler.runCurrent()
        vm.rueckgaengig()
        dispatcher.scheduler.runCurrent()

        assertEquals(null, vm.toast.value)
        assertEquals(0, buchungen.stand.value.sumOf { it.betrag })
        assertEquals(FaelligkeitStatus.OFFEN, faelligkeiten.stand.value.single().status)
    }

    @Test
    fun `der Toast verschwindet von selbst, wenn niemand Rueckgaengig antippt`() = runTest(dispatcher) {
        val vm = viewModel(FakeFaelligkeitRepository(listOf(morgens)), FakeBuchungRepository())

        vm.erledigen(10)
        dispatcher.scheduler.runCurrent()
        assertEquals(true, vm.toast.value != null)

        dispatcher.scheduler.advanceTimeBy(3_900)
        dispatcher.scheduler.runCurrent()

        assertEquals(null, vm.toast.value)
    }

    @Test
    fun `eine neue Aktion zeigt ihren Toast oben und jeder Toast laeuft ab seinem eigenen Start ab`() = runTest(dispatcher) {
        val faelligkeiten = FakeFaelligkeitRepository(listOf(morgens, abends))
        val vm = viewModel(faelligkeiten, FakeBuchungRepository())

        vm.erledigen(10)
        dispatcher.scheduler.runCurrent()
        assertEquals("+10 Punkte · Tabletten", vm.toast.value?.text)

        // Zweite Aktion, deutlich vor Ablauf des ersten 3.8s-Fensters.
        dispatcher.scheduler.advanceTimeBy(2_000)
        vm.erledigen(11)
        dispatcher.scheduler.runCurrent()

        // Der Toast zeigt jetzt die ZWEITE Aktion (die erste liegt gestapelt darunter).
        assertEquals("+20 Punkte · Sport", vm.toast.value?.text)

        // Ab Start der ersten Aktion sind jetzt 3_900ms vergangen -- ihr eigenes 3.8s-Fenster
        // ist damit ueberschritten. Ihr Ablauf darf den oberen Toast der zweiten Aktion nicht
        // mitnehmen.
        dispatcher.scheduler.advanceTimeBy(1_900)
        dispatcher.scheduler.runCurrent()
        assertEquals("+20 Punkte · Sport", vm.toast.value?.text)

        // Jetzt ist auch das eigene 3.8s-Fenster der zweiten Aktion ueberschritten
        // (2_000ms seit ihrem Start) -- erst jetzt darf ihr Auto-Dismiss greifen.
        dispatcher.scheduler.advanceTimeBy(2_000)
        dispatcher.scheduler.runCurrent()
        assertEquals(null, vm.toast.value)
    }

    @Test
    fun `zwei schnell abgehakte Eintraege lassen sich beide rueckgaengig machen`() = runTest(dispatcher) {
        val faelligkeiten = FakeFaelligkeitRepository(listOf(morgens, abends))
        val buchungen = FakeBuchungRepository()
        val vm = viewModel(faelligkeiten, buchungen)

        vm.erledigen(10)
        dispatcher.scheduler.runCurrent()
        vm.erledigen(11)
        dispatcher.scheduler.runCurrent()
        vm.rueckgaengig()
        dispatcher.scheduler.runCurrent()
        // Zweiter Tipp nach der Doppeltipp-Sperre (ToastStapel.DOPPELTIPP_SPERRE_MS), aber
        // weit innerhalb der 3.8 s beider Toasts.
        dispatcher.scheduler.advanceTimeBy(400)
        dispatcher.scheduler.runCurrent()
        vm.rueckgaengig()
        dispatcher.scheduler.runCurrent()

        assertEquals(listOf(FaelligkeitStatus.OFFEN, FaelligkeitStatus.OFFEN), faelligkeiten.stand.value.map { it.status })
        assertEquals(0, buchungen.stand.value.sumOf { it.betrag })
    }

    @Test
    fun `Auslassen und Rueckgaengig setzt die Faelligkeit zurueck auf OFFEN`() = runTest(dispatcher) {
        val faelligkeiten = FakeFaelligkeitRepository(listOf(morgens))
        val vm = viewModel(faelligkeiten, FakeBuchungRepository())

        vm.auslassen(10)
        dispatcher.scheduler.runCurrent()
        assertEquals(FaelligkeitStatus.AUSGELASSEN, faelligkeiten.stand.value.single().status)

        vm.rueckgaengig()
        dispatcher.scheduler.runCurrent()

        assertEquals(null, vm.toast.value)
        assertEquals(FaelligkeitStatus.OFFEN, faelligkeiten.stand.value.single().status)
    }

    @Test
    fun `Rueckgaengig ohne ausstehende Aktion ist ein sicheres No-op`() = runTest(dispatcher) {
        val faelligkeiten = FakeFaelligkeitRepository(listOf(morgens))
        val buchungen = FakeBuchungRepository()
        val vm = viewModel(faelligkeiten, buchungen)

        vm.rueckgaengig()
        dispatcher.scheduler.runCurrent()

        assertEquals(null, vm.toast.value)
        assertEquals(0, buchungen.stand.value.sumOf { it.betrag })
        assertEquals(FaelligkeitStatus.OFFEN, faelligkeiten.stand.value.single().status)
    }

    @Test
    fun `verstecke laeuft auch, wenn nichts mehr gebucht wird, damit die Benachrichtigung nicht stehen bleibt`() =
        runTest(dispatcher) {
            val faelligkeiten = FakeFaelligkeitRepository(listOf(morgens))
            val anzeige = FakeAnzeige()
            val vm = viewModel(faelligkeiten, FakeBuchungRepository(), anzeige = anzeige)

            vm.erledigen(10)
            dispatcher.scheduler.runCurrent()
            assertEquals(listOf(10L), anzeige.versteckt)

            // Die Faelligkeit ist jetzt ERLEDIGT (NichtOffen) -- ein zweiter Aufruf bucht
            // nichts mehr, muss aber trotzdem verstecke() aufrufen (stale Liste, Doppel-Tap
            // oder der Watchdog war schneller -- Finding 3 der Branch-Review).
            vm.erledigen(10)
            dispatcher.scheduler.runCurrent()

            assertEquals(listOf(10L, 10L), anzeige.versteckt)
        }

    @Test
    fun `eine verpasste Einmalaufgabe steht unter Aufgaben und verschwindet nach dem Abhaken ohne Punkte`() =
        runTest(dispatcher) {
            val steuer = Aktivitaet(
                id = 3, name = "Steuererklaerung", farbe = 3, basisPunkte = 20,
                zeitplan = Zeitplan.Einmalig(LocalDate.of(2026, 2, 27)),
                erstelltAm = LocalDate.of(2026, 2, 1),
            )
            val verpasst = Faelligkeit(
                id = 30, aktivitaetId = 3,
                periodeVon = LocalDate.of(2026, 2, 1), periodeBis = LocalDate.of(2026, 2, 27),
                geplantFuer = null, sollAnzahl = 1, status = FaelligkeitStatus.VERPASST,
            )
            val buchungen = FakeBuchungRepository()
            val vm = viewModel(
                FakeFaelligkeitRepository(listOf(morgens, verpasst)),
                buchungen,
                aktivitaetenListe = listOf(tabletten, steuer),
            )

            vm.zustand.test {
                skipItems(1)
                val vorher = awaitItem()
                assertEquals(listOf(30L), vorher.aufgaben.map { it.faelligkeitId })
                assertEquals(1, vorher.gesamtHeute)

                vm.erledigen(30)
                assertEquals(emptyList(), awaitItem().aufgaben)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(0, buchungen.stand.value.sumOf { it.betrag })
        }

    @Test
    fun `Wieder oeffnen nimmt eine Erledigung auch nach Ablauf des Toasts zurueck`() = runTest(dispatcher) {
        val faelligkeiten = FakeFaelligkeitRepository(listOf(morgens))
        val buchungen = FakeBuchungRepository()
        var widgetAnstoesse = 0
        var neuGeplant = 0
        val vm = viewModel(
            faelligkeiten, buchungen,
            widgetAnstossen = { widgetAnstoesse++ },
            alarmNeuPlanen = { neuGeplant++ },
        )

        vm.erledigen(10)
        dispatcher.scheduler.advanceTimeBy(3_900)
        dispatcher.scheduler.runCurrent()
        assertEquals(null, vm.toast.value)

        vm.wiederOeffnen(10)
        dispatcher.scheduler.runCurrent()

        assertEquals(FaelligkeitStatus.OFFEN, faelligkeiten.stand.value.single().status)
        assertEquals(0, buchungen.stand.value.sumOf { it.betrag })
        assertEquals("Tabletten wieder offen", vm.toast.value?.text)
        assertEquals(2, widgetAnstoesse)
        assertEquals(2, neuGeplant)
    }
}
