package de.vanhake.dayorganizer.ui.belohnungen

import app.cash.turbine.test
import de.vanhake.dayorganizer.FakeBelohnungRepository
import de.vanhake.dayorganizer.FakeBuchungRepository
import de.vanhake.dayorganizer.FakeFaelligkeitRepository
import de.vanhake.dayorganizer.FakeTransaktionen
import de.vanhake.dayorganizer.data.repository.BuchungRepository
import de.vanhake.dayorganizer.domain.model.Belohnung
import de.vanhake.dayorganizer.domain.model.Belohnungsbedingung
import de.vanhake.dayorganizer.domain.model.Buchung
import de.vanhake.dayorganizer.domain.model.BuchungsArt
import de.vanhake.dayorganizer.domain.rechner.BelohnungsPruefer
import de.vanhake.dayorganizer.domain.rechner.Hindernis
import de.vanhake.dayorganizer.domain.rechner.SerienRechner
import de.vanhake.dayorganizer.domainuse.BelohnungEinloesen
import kotlinx.coroutines.Dispatchers
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
        belohnungen: FakeBelohnungRepository,
        buchungen: BuchungRepository,
        widgetAnstossen: suspend () -> Unit = {},
    ): BelohnungenViewModel {
        val faelligkeiten = FakeFaelligkeitRepository()
        return BelohnungenViewModel(
            belohnungen = belohnungen,
            buchungen = buchungen,
            einloesenUseCase = BelohnungEinloesen(
                belohnungen, buchungen, faelligkeiten,
                BelohnungsPruefer(), SerienRechner(), FakeTransaktionen(), uhr,
            ),
            widgetAnstossen = widgetAnstossen,
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
}
