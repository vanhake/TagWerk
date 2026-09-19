package de.vanhake.dayorganizer.ui.aktivitaeten

import de.vanhake.dayorganizer.domain.model.Zeitplan
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AktivitaetEntwurfTest {

    private val gueltig = AktivitaetEntwurf(
        name = "Tabletten",
        basisPunkteText = "10",
        typ = ZeitplanTyp.FESTE_ZEITEN,
        zeiten = listOf(LocalTime.of(8, 0)),
        wochentage = DayOfWeek.entries.toSet(),
    )

    @Test
    fun `ein vollstaendiger Entwurf hat keine Fehler`() {
        assertEquals(emptyList(), gueltig.pruefen())
    }

    @Test
    fun `leerer Name und nicht positive Punkte werden gemeldet`() {
        val fehler = gueltig.copy(name = "  ", basisPunkteText = "0").pruefen()

        assertTrue(EntwurfFehler.NAME_LEER in fehler)
        assertTrue(EntwurfFehler.PUNKTE_NICHT_POSITIV in fehler)
    }

    @Test
    fun `feste Zeiten brauchen mindestens eine Uhrzeit und einen Wochentag`() {
        val fehler = gueltig.copy(zeiten = emptyList(), wochentage = emptySet()).pruefen()

        assertTrue(EntwurfFehler.KEINE_ZEIT in fehler)
        assertTrue(EntwurfFehler.KEIN_WOCHENTAG in fehler)
    }

    @Test
    fun `ein Wochenziel braucht keine Wochentage aber eine positive Anzahl`() {
        val entwurf = gueltig.copy(
            typ = ZeitplanTyp.WOCHENZIEL,
            zeiten = emptyList(),
            wochentage = emptySet(),
            zielAnzahlText = "0",
        )

        assertEquals(listOf(EntwurfFehler.ZIEL_NICHT_POSITIV), entwurf.pruefen())
    }

    @Test
    fun `ein Intervall braucht ein positives Fenster`() {
        val entwurf = gueltig.copy(
            typ = ZeitplanTyp.INTERVALL,
            zeiten = emptyList(),
            intervallStundenText = "0",
            fensterVon = LocalTime.of(20, 0),
            fensterBis = LocalTime.of(8, 0),
        )

        assertTrue(EntwurfFehler.INTERVALL_UNGUELTIG in entwurf.pruefen())
    }

    @Test
    fun `der Entwurf wird in den richtigen Zeitplan umgesetzt`() {
        val aktivitaet = gueltig.zuAktivitaet(LocalDate.of(2026, 3, 2))

        assertEquals("Tabletten", aktivitaet.name)
        assertEquals(10, aktivitaet.basisPunkte)
        assertEquals(
            Zeitplan.FesteZeiten(listOf(LocalTime.of(8, 0)), DayOfWeek.entries.toSet()),
            aktivitaet.zeitplan,
        )
    }

    @Test
    fun `ein Wochenziel wird in den Wochenziel-Zeitplan umgesetzt`() {
        val entwurf = gueltig.copy(typ = ZeitplanTyp.WOCHENZIEL, zielAnzahlText = "3")

        assertEquals(Zeitplan.Wochenziel(3), entwurf.zuAktivitaet(LocalDate.of(2026, 3, 2)).zeitplan)
    }
}
