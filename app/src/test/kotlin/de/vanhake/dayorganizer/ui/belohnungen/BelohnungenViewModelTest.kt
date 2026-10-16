package de.vanhake.dayorganizer.ui.belohnungen

import app.cash.turbine.test
import de.vanhake.dayorganizer.FakeBelohnungRepository
import de.vanhake.dayorganizer.FakeBuchungRepository
import de.vanhake.dayorganizer.FakeFaelligkeitRepository
import de.vanhake.dayorganizer.FakeTransaktionen
import de.vanhake.dayorganizer.data.repository.BelohnungRepository
import de.vanhake.dayorganizer.data.repository.BuchungRepository
import de.vanhake.dayorganizer.domain.model.Belohnung
import de.vanhake.dayorganizer.domain.model.Belohnungsbedingung
import de.vanhake.dayorganizer.domain.model.Buchung
import de.vanhake.dayorganizer.domain.model.BuchungsArt
import de.vanhake.dayorganizer.domain.rechner.BelohnungsPruefer
import de.vanhake.dayorganizer.domain.rechner.Hindernis
import de.vanhake.dayorganizer.domain.rechner.SerienRechner
import de.vanhake.dayorganizer.domainuse.BelohnungEinloesen
import de.vanhake.dayorganizer.domainuse.BelohnungEinloesenRueckgaengig
import de.vanhake.dayorganizer.ui.gemeinsam.Hinweistoast
import de.vanhake.dayorganizer.ui.gemeinsam.SpeicherErgebnis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BelohnungenViewModelTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val uhr = Clock.fixed(
        LocalDateTime.of(2026, 3, 2, 19, 0).atZone(zone).toInstant(),
        zone,
    )
    private val dispatcher = StandardTestDispatcher()

    @Before fun vorher() = Dispatchers.setMain(dispatcher)
    @After fun nachher() = Dispatchers.resetMain()

    private fun viewModel(
        belohnungen: BelohnungRepository,
        buchungen: BuchungRepository,
        widgetAnstossen: suspend () -> Unit = {},
    ): BelohnungenViewModel {
        val faelligkeiten = FakeFaelligkeitRepository()
        val transaktionen = FakeTransaktionen()
        return BelohnungenViewModel(
            belohnungen = belohnungen,
            buchungen = buchungen,
            einloesenUseCase = BelohnungEinloesen(
                belohnungen, buchungen, faelligkeiten,
                BelohnungsPruefer(), SerienRechner(), transaktionen, uhr,
            ),
            einloesenRueckgaengig = BelohnungEinloesenRueckgaengig(belohnungen, buchungen, transaktionen, uhr),
            widgetAnstossen = widgetAnstossen,
            rechenDispatcher = dispatcher,
        )
    }

    private fun guthaben(punkte: Int) = FakeBuchungRepository(
        listOf(
            Buchung(
                art = BuchungsArt.ERLEDIGUNG, betrag = punkte,
                zeitpunkt = LocalDateTime.of(2026, 3, 1, 8, 0),
            ),
        ),
    )

    /** Wirft beim Lesen des Kontostands, dem ersten Zugriff innerhalb von
     * BelohnungEinloesen.loese() -- simuliert eine Datenbankausnahme waehrend der Einloesung. */
    private class WerfendeBuchungRepository(delegate: BuchungRepository) : BuchungRepository by delegate {
        override suspend fun kontostand(): Int = error("Datenbank nicht erreichbar")
    }

    /** Das Stornieren, der erste Schritt des Rueckgaengig, scheitert -- das Einloesen davor
     * laeuft normal durch. */
    private class WerfendeStornoRepository(delegate: BelohnungRepository) : BelohnungRepository by delegate {
        override suspend fun einloesungStornieren(id: Long, am: LocalDateTime): Boolean =
            error("Datenbank nicht erreichbar")
    }

    /** Bricht beim Lesen des Kontostands ab -- so, als verliesse der Nutzer den Tab,
     * waehrend die Einloesung laeuft, und der viewModelScope wuerde abgebrochen. */
    private class AbbrechendeBuchungRepository(delegate: BuchungRepository) : BuchungRepository by delegate {
        override suspend fun kontostand(): Int = throw CancellationException("Tab verlassen")
    }

    @Test
    fun `Kacheln zeigen fehlende Punkte`() = runTest(dispatcher) {
        val vm = viewModel(
            FakeBelohnungRepository(listOf(Belohnung(1, "Kinoabend", preisPunkte = 200))),
            guthaben(160),
        )

        vm.zustand.test {
            skipItems(1)
            val zustand = awaitItem()
            assertEquals(160, zustand.kontostand)
            assertEquals(40, zustand.kacheln.single().einloesbarkeit.fehlendePunkte)
            assertEquals(
                Hindernis.ZU_WENIG_PUNKTE,
                zustand.kacheln.single().einloesbarkeit.hindernis,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Einloesen senkt den Kontostand und erscheint in der Historie`() = runTest(dispatcher) {
        val belohnungen = FakeBelohnungRepository(listOf(Belohnung(1, "Kinoabend", preisPunkte = 200)))
        val buchungen = guthaben(250)
        val vm = viewModel(belohnungen, buchungen)

        vm.einloesen(1)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(50, buchungen.kontostand())
        assertEquals(listOf("Kinoabend"), belohnungen.einloesungen.value.map { it.name })
    }

    // Guthaben reicht fuer zwei Einloesungen (450 bei Preis 200) -- ein Test mit knappem
    // Guthaben bliebe gruen, auch wenn einloesen() den Use Case zweimal aufriefe, weil der
    // zweite Aufruf dann ohnehin am Kontostand scheitern wuerde. Erst mit ausreichend
    // Guthaben wird dieser Test zum Waechter fuer die Doppeltipp-Sperre.
    //
    // Geprueft wird das Ergebnis am Repository, nicht an einer Ausgabe des Zustandsflusses:
    // einloesen() laeuft im viewModelScope auf [dispatcher] und steht nach
    // advanceUntilIdle() fest, waehrend der Zustandsfluss seit flowOn(Dispatchers.Default)
    // von einem echten Thread kommt. Eine Zusicherung auf die Ausgaben bestuende dort
    // zeitabhaengig -- per Mutationstest belegt: sie fing die entfernte Sperre nicht mehr.
    @Test
    fun `ein Doppeltipp loest nur einmal ein`() = runTest(dispatcher) {
        val belohnungen = FakeBelohnungRepository(listOf(Belohnung(1, "Kinoabend", preisPunkte = 200)))
        val buchungen = guthaben(450)
        val vm = viewModel(belohnungen, buchungen)

        vm.einloesen(1)
        vm.einloesen(1)
        dispatcher.scheduler.advanceUntilIdle()

        // 450 minus einmal 200. Ohne die Sperre waeren es zwei Abbuchungen und 50.
        assertEquals(250, buchungen.kontostand())
        assertEquals(listOf("Kinoabend"), belohnungen.einloesungen.value.map { it.name })
    }

    // Geprueft wird gegen das Repository, nicht gegen eine Ausgabe von vm.zustand: der
    // Zustandsfluss laeuft seit flowOn(Dispatchers.Default) auf einem echten Thread und
    // stuende hier zeitabhaengig -- siehe "ein Doppeltipp loest nur einmal ein" fuer
    // dasselbe Muster.
    @Test
    fun `Archivieren entfernt die Kachel und behaelt die Einloese-Historie`() = runTest(dispatcher) {
        val belohnungen = FakeBelohnungRepository(listOf(Belohnung(1, "Kinoabend", preisPunkte = 200)))
        val buchungen = guthaben(250)
        val vm = viewModel(belohnungen, buchungen)

        // Vor dem Archivieren einmal einloesen, damit die Historie etwas enthaelt,
        // das das Archivieren stehen lassen muss.
        vm.einloesen(1)
        dispatcher.scheduler.advanceUntilIdle()

        vm.archivieren(1)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(true, belohnungen.stand.value.single { it.id == 1L }.archiviert)
        assertEquals(listOf("Kinoabend"), belohnungen.einloesungen.value.map { it.name })
    }

    @Test
    fun `Einloesen stoesst das Widget an, damit der Punktestand nicht veraltet`() = runTest(dispatcher) {
        val belohnungen = FakeBelohnungRepository(listOf(Belohnung(1, "Kinoabend", preisPunkte = 200)))
        val buchungen = guthaben(250)
        var widgetAngestossen = 0
        val vm = viewModel(belohnungen, buchungen, widgetAnstossen = { widgetAngestossen++ })

        vm.einloesen(1)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, widgetAngestossen)
    }

    // Geprueft nach advanceUntilIdle() direkt am StateFlow -- meldung wird synchron im
    // viewModelScope gesetzt, anders als zustand (siehe Kommentar an einloesen()) nicht
    // ueber flowOn(Dispatchers.Default), also ohne Zeitabhaengigkeit pruefbar.
    @Test
    fun `Einloesen ohne ausreichende Punkte zeigt die Meldung fuer zu wenig Punkte`() = runTest(dispatcher) {
        val belohnungen = FakeBelohnungRepository(listOf(Belohnung(1, "Kinoabend", preisPunkte = 200)))
        val buchungen = guthaben(160)
        val vm = viewModel(belohnungen, buchungen)

        vm.einloesen(1)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(EinloeseMeldung.ZU_WENIG_PUNKTE, vm.meldung.value)
        assertEquals(40, vm.meldungFehlendePunkte.value)
    }

    @Test
    fun `Einloesen vor Erreichen der Serie zeigt die Meldung fuer zu kurze Serie`() = runTest(dispatcher) {
        val belohnungen = FakeBelohnungRepository(
            listOf(
                Belohnung(
                    1, "Kinoabend", preisPunkte = 100,
                    bedingung = Belohnungsbedingung.MindestSerie(aktivitaetId = 1, perioden = 3),
                ),
            ),
        )
        val buchungen = guthaben(150)
        val vm = viewModel(belohnungen, buchungen)

        vm.einloesen(1)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(EinloeseMeldung.SERIE_ZU_KURZ, vm.meldung.value)
    }

    @Test
    fun `Einloesen vor dem Freischaltdatum zeigt die Meldung fuer zu frueh`() = runTest(dispatcher) {
        val belohnungen = FakeBelohnungRepository(
            listOf(
                Belohnung(
                    1, "Kinoabend", preisPunkte = 100,
                    bedingung = Belohnungsbedingung.FruehestensAb(LocalDate.of(2026, 3, 10)),
                ),
            ),
        )
        val buchungen = guthaben(150)
        val vm = viewModel(belohnungen, buchungen)

        vm.einloesen(1)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(EinloeseMeldung.ZU_FRUEH, vm.meldung.value)
    }

    @Test
    fun `Einloesen einer verschwundenen Belohnung zeigt die Meldung fuer nicht mehr vorhanden`() =
        runTest(dispatcher) {
            val belohnungen = FakeBelohnungRepository()
            val buchungen = guthaben(150)
            val vm = viewModel(belohnungen, buchungen)

            // Es gibt keine Belohnung mit id 1 -- BelohnungEinloesen.loese() liefert Unbekannt.
            vm.einloesen(1)
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(EinloeseMeldung.NICHT_MEHR_VORHANDEN, vm.meldung.value)
        }

    @Test
    fun `Eine Ausnahme beim Einloesen zeigt eine allgemeine Fehlermeldung`() = runTest(dispatcher) {
        val belohnungen = FakeBelohnungRepository(listOf(Belohnung(1, "Kinoabend", preisPunkte = 200)))
        val buchungen = WerfendeBuchungRepository(guthaben(250))
        val vm = viewModel(belohnungen, buchungen)

        vm.einloesen(1)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(EinloeseMeldung.FEHLER, vm.meldung.value)
    }

    @Test
    fun `Ein erfolgreiches Einloesen zeigt keine Meldung`() = runTest(dispatcher) {
        val belohnungen = FakeBelohnungRepository(listOf(Belohnung(1, "Kinoabend", preisPunkte = 200)))
        val buchungen = guthaben(250)
        val vm = viewModel(belohnungen, buchungen)

        vm.einloesen(1)
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(vm.meldung.value)
    }

    // Ein Abbruch ist kein Fehler. runCatching deutete ihn in einen um und zeigte
    // "Einloesen fehlgeschlagen", obwohl nur der Bildschirm verlassen wurde.
    @Test
    fun `ein Abbruch waehrend des Einloesens zeigt keine Fehlermeldung`() = runTest(dispatcher) {
        val belohnungen = FakeBelohnungRepository(listOf(Belohnung(1, "Kinoabend", preisPunkte = 200)))
        val buchungen = AbbrechendeBuchungRepository(guthaben(250))
        val vm = viewModel(belohnungen, buchungen)

        vm.einloesen(1)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(null, vm.meldung.value)
    }

    // Eine Belohnung, die zwischen Anzeige und Tipp archiviert wurde, liefert
    // Abgelehnt ohne Hindernis. Das ist ein eigener Zweig in der Zuordnung, und
    // der when ist ein Statement -- Kotlin erzwingt hier keine Vollstaendigkeit.
    @Test
    fun `eine inzwischen archivierte Belohnung meldet, dass es sie nicht mehr gibt`() = runTest(dispatcher) {
        val belohnungen = FakeBelohnungRepository(
            listOf(Belohnung(1, "Kinoabend", preisPunkte = 200, archiviert = true)),
        )
        val vm = viewModel(belohnungen, guthaben(250))

        vm.einloesen(1)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(EinloeseMeldung.NICHT_MEHR_VORHANDEN, vm.meldung.value)
    }

    @Test
    fun `ein erfolgreiches Einloesen zeigt einen Toast mit Rueckgaengig`() = runTest(dispatcher) {
        val belohnungen = FakeBelohnungRepository(listOf(Belohnung(1, "Kinoabend", preisPunkte = 200)))
        val vm = viewModel(belohnungen, guthaben(250))

        vm.einloesen(1)
        dispatcher.scheduler.runCurrent()

        assertEquals(Hinweistoast("Kinoabend eingelöst · −200 Punkte", kannRueckgaengig = true), vm.toast.value)
    }

    @Test
    fun `Rueckgaengig nach dem Einloesen stellt Kontostand her und storniert`() = runTest(dispatcher) {
        val belohnungen = FakeBelohnungRepository(listOf(Belohnung(1, "Kinoabend", preisPunkte = 200)))
        val buchungen = guthaben(250)
        var widgetAngestossen = 0
        val vm = viewModel(belohnungen, buchungen, widgetAnstossen = { widgetAngestossen++ })
        vm.einloesen(1)
        dispatcher.scheduler.runCurrent()

        vm.rueckgaengig()
        dispatcher.scheduler.runCurrent()

        assertEquals(250, buchungen.kontostand())
        assertEquals(true, belohnungen.einloesungen.value.single().storniertAm != null)
        assertNull(vm.toast.value)
        // einmal nach dem Einloesen, einmal nach dem Rueckgaengig
        assertEquals(2, widgetAngestossen)
    }

    @Test
    fun `ein gescheitertes Rueckgaengig meldet das und laesst den Kontostand beim eingeloesten Stand`() =
        runTest(dispatcher) {
            val belohnungen = WerfendeStornoRepository(
                FakeBelohnungRepository(listOf(Belohnung(1, "Kinoabend", preisPunkte = 200))),
            )
            val buchungen = guthaben(250)
            var widgetAngestossen = 0
            val vm = viewModel(belohnungen, buchungen, widgetAnstossen = { widgetAngestossen++ })
            vm.einloesen(1)
            dispatcher.scheduler.runCurrent()

            vm.rueckgaengig()
            dispatcher.scheduler.runCurrent()

            assertEquals(EinloeseMeldung.RUECKGAENGIG_FEHLGESCHLAGEN, vm.meldung.value)
            // nur das Einloesen hat das Widget angestossen
            assertEquals(1, widgetAngestossen)
            assertEquals(50, buchungen.kontostand())
        }

    @Test
    fun `nach Ablauf des Toasts gibt es kein Rueckgaengig mehr`() = runTest(dispatcher) {
        val belohnungen = FakeBelohnungRepository(listOf(Belohnung(1, "Kinoabend", preisPunkte = 200)))
        val buchungen = guthaben(250)
        val vm = viewModel(belohnungen, buchungen)
        vm.einloesen(1)
        dispatcher.scheduler.runCurrent()
        dispatcher.scheduler.advanceTimeBy(3_801)
        dispatcher.scheduler.runCurrent()

        vm.rueckgaengig()
        dispatcher.scheduler.runCurrent()

        assertNull(vm.toast.value)
        assertEquals(50, buchungen.kontostand())
    }

    @Test
    fun `ein Speichern-Toast verdraengt das Rueckgaengig einer Einloesung nicht`() = runTest(dispatcher) {
        val belohnungen = FakeBelohnungRepository(listOf(Belohnung(1, "Kinoabend", preisPunkte = 200)))
        val buchungen = guthaben(250)
        val vm = viewModel(belohnungen, buchungen)
        vm.einloesen(1)
        dispatcher.scheduler.runCurrent()

        vm.gespeichertMelden(SpeicherErgebnis.GEAENDERT)
        dispatcher.scheduler.runCurrent()
        assertEquals(true, vm.toast.value!!.kannRueckgaengig)

        vm.rueckgaengig()
        dispatcher.scheduler.runCurrent()
        assertEquals(250, buchungen.kontostand())
        assertEquals(true, belohnungen.einloesungen.value.single().storniertAm != null)
    }

    @Test
    fun `Archivieren zeigt einen Toast ohne Rueckgaengig`() = runTest(dispatcher) {
        val belohnungen = FakeBelohnungRepository(listOf(Belohnung(1, "Kinoabend", preisPunkte = 200)))
        val vm = viewModel(belohnungen, guthaben(0))

        vm.archivieren(1)
        dispatcher.scheduler.runCurrent()

        assertEquals(Hinweistoast("Kinoabend archiviert", kannRueckgaengig = true), vm.toast.value)
    }

    @Test
    fun `Archivieren einer Belohnung laesst sich rueckgaengig machen`() = runTest(dispatcher) {
        val belohnungen = FakeBelohnungRepository(listOf(Belohnung(1, "Kinoabend", preisPunkte = 200)))
        val vm = viewModel(belohnungen, guthaben(0))

        vm.archivieren(1).join()
        assertEquals(Hinweistoast("Kinoabend archiviert", kannRueckgaengig = true), vm.toast.value)
        assertEquals(true, belohnungen.stand.value.single().archiviert)

        vm.rueckgaengig().join()

        assertEquals(false, belohnungen.stand.value.single().archiviert)
    }

    @Test
    fun `Wiederherstellen einer Belohnung zeigt sie wieder im Shop`() = runTest(dispatcher) {
        val belohnungen = FakeBelohnungRepository(
            listOf(Belohnung(1, "Kinoabend", preisPunkte = 200, archiviert = true)),
        )
        val vm = viewModel(belohnungen, guthaben(0))

        backgroundScope.launch { vm.zustand.collect {} }
        backgroundScope.launch { vm.archiviert.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(emptyList(), vm.zustand.value.kacheln)
        assertEquals(listOf("Kinoabend"), vm.archiviert.value.map { it.name })

        vm.wiederherstellen(1).join()
        dispatcher.scheduler.runCurrent()

        assertEquals(listOf("Kinoabend"), vm.zustand.value.kacheln.map { it.name })
        assertEquals(emptyList(), vm.archiviert.value)
        assertEquals("Kinoabend wiederhergestellt", vm.toast.value?.text)
        assertEquals(false, vm.toast.value?.kannRueckgaengig)
    }

    @Test
    fun `gespeichert melden zeigt den passenden Toast`() = runTest(dispatcher) {
        val vm = viewModel(FakeBelohnungRepository(), guthaben(0))

        vm.gespeichertMelden(SpeicherErgebnis.ANGELEGT)
        dispatcher.scheduler.runCurrent()
        assertEquals("Belohnung angelegt", vm.toast.value?.text)

        vm.gespeichertMelden(SpeicherErgebnis.GEAENDERT)
        dispatcher.scheduler.runCurrent()
        assertEquals("Änderungen gespeichert", vm.toast.value?.text)
    }
}
