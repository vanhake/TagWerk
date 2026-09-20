package de.vanhake.dayorganizer.ui.aktivitaeten

import de.vanhake.dayorganizer.domain.model.Aktivitaet
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
        zeitenText = "08:00",
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
        val fehler = gueltig.copy(zeitenText = "", wochentage = emptySet()).pruefen()

        assertTrue(EntwurfFehler.KEINE_ZEIT in fehler)
        assertTrue(EntwurfFehler.KEIN_WOCHENTAG in fehler)
    }

    @Test
    fun `ein Wochenziel braucht keine Wochentage aber eine positive Anzahl`() {
        val entwurf = gueltig.copy(
            typ = ZeitplanTyp.WOCHENZIEL,
            zeitenText = "",
            wochentage = emptySet(),
            zielAnzahlText = "0",
        )

        assertEquals(listOf(EntwurfFehler.ZIEL_NICHT_POSITIV), entwurf.pruefen())
    }

    @Test
    fun `ein Intervall braucht ein positives Fenster`() {
        val entwurf = gueltig.copy(
            typ = ZeitplanTyp.INTERVALL,
            zeitenText = "",
            intervallStundenText = "0",
            fensterVon = LocalTime.of(20, 0),
            fensterBis = LocalTime.of(8, 0),
        )

        assertTrue(EntwurfFehler.INTERVALL_UNGUELTIG in entwurf.pruefen())
    }

    @Test
    fun `ein Tagesziel braucht eine positive Anzahl und einen Wochentag`() {
        val entwurf = gueltig.copy(
            typ = ZeitplanTyp.TAGESZIEL,
            zeitenText = "",
            wochentage = emptySet(),
            zielAnzahlText = "0",
        )

        val fehler = entwurf.pruefen()

        assertTrue(EntwurfFehler.ZIEL_NICHT_POSITIV in fehler)
        assertTrue(EntwurfFehler.KEIN_WOCHENTAG in fehler)
    }

    @Test
    fun `ein unlesbarer Zeiteintrag wird bei festen Zeiten gemeldet`() {
        val entwurf = gueltig.copy(zeitenText = "08:00, nicht-lesbar")

        assertEquals(listOf(EntwurfFehler.ZEIT_UNLESBAR), entwurf.pruefen())
    }

    @Test
    fun `ein unlesbarer Zeiteintrag wird auch beim Tagesziel gemeldet`() {
        val entwurf = gueltig.copy(typ = ZeitplanTyp.TAGESZIEL, zeitenText = "nicht-lesbar")

        assertTrue(EntwurfFehler.ZEIT_UNLESBAR in entwurf.pruefen())
    }

    @Test
    fun `eine gueltige kommagetrennte Liste wird sortiert geparst`() {
        val entwurf = gueltig.copy(zeitenText = "12:00, 08:00")

        assertEquals(
            Zeitplan.FesteZeiten(
                listOf(LocalTime.of(8, 0), LocalTime.of(12, 0)),
                DayOfWeek.entries.toSet(),
            ),
            entwurf.zuAktivitaet(LocalDate.of(2026, 3, 2)).zeitplan,
        )
    }

    @Test
    fun `der Rundlauf durch aus() erhaelt die Uhrzeiten`() {
        val aktivitaet = Aktivitaet(
            id = 7,
            name = "Tabletten",
            farbe = 0xFF4CAF50.toInt(),
            basisPunkte = 10,
            zeitplan = Zeitplan.FesteZeiten(
                listOf(LocalTime.of(8, 0), LocalTime.of(20, 30)),
                DayOfWeek.entries.toSet(),
            ),
            erstelltAm = LocalDate.of(2025, 12, 1),
        )

        val entwurf = AktivitaetEntwurf.aus(aktivitaet)
        assertEquals("08:00, 20:30", entwurf.zeitenText)

        val rundlauf = entwurf.zuAktivitaet(LocalDate.of(2026, 3, 2))
        assertEquals(aktivitaet.zeitplan, rundlauf.zeitplan)
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

    @Test
    fun `ein archivierter Entwurf bleibt beim Rundlauf archiviert`() {
        val archiviertAm = LocalDate.of(2026, 1, 15)
        val aktivitaet = Aktivitaet(
            id = 7,
            name = "Tabletten",
            farbe = 0xFF4CAF50.toInt(),
            basisPunkte = 10,
            zeitplan = Zeitplan.FesteZeiten(listOf(LocalTime.of(8, 0)), DayOfWeek.entries.toSet()),
            aktiv = false,
            erstelltAm = LocalDate.of(2025, 12, 1),
            archiviertAm = archiviertAm,
        )

        val rundlauf = AktivitaetEntwurf.aus(aktivitaet).zuAktivitaet(LocalDate.of(2026, 3, 2))

        assertEquals(false, rundlauf.aktiv)
        assertEquals(archiviertAm, rundlauf.archiviertAm)
    }

    @Test
    fun `ein Entwurf behaelt das echte Erstellungsdatum beim erneuten Speichern`() {
        val erstelltAm = LocalDate.of(2025, 12, 1)
        val aktivitaet = Aktivitaet(
            id = 7,
            name = "Tabletten",
            farbe = 0xFF4CAF50.toInt(),
            basisPunkte = 10,
            zeitplan = Zeitplan.FesteZeiten(listOf(LocalTime.of(8, 0)), DayOfWeek.entries.toSet()),
            erstelltAm = erstelltAm,
        )

        // Umbenennen simuliert das erneute Speichern eines bestehenden Entwurfs an einem
        // spaeteren Tag -- das Anlegedatum darf dabei nicht auf "heute" zurueckspringen.
        val rundlauf = AktivitaetEntwurf.aus(aktivitaet).copy(name = "Tabletten neu")
            .zuAktivitaet(LocalDate.of(2026, 3, 2))

        assertEquals(erstelltAm, rundlauf.erstelltAm)
    }

    @Test
    fun `ein brandneuer Entwurf ohne eigenes Erstellungsdatum bekommt heute`() {
        val heute = LocalDate.of(2026, 3, 2)

        val aktivitaet = gueltig.zuAktivitaet(heute)

        assertEquals(heute, aktivitaet.erstelltAm)
    }

    @Test
    fun `eine aktive Aktivitaet bleibt beim Rundlauf gleich`() {
        val aktivitaet = Aktivitaet(
            id = 7,
            name = "Tabletten",
            notiz = "vor dem Essen",
            farbe = 0xFF4CAF50.toInt(),
            basisPunkte = 10,
            zeitplan = Zeitplan.FesteZeiten(listOf(LocalTime.of(8, 0)), DayOfWeek.entries.toSet()),
            kulanzMinuten = 45,
            puenktlichkeitsBonus = 5,
            nachfassAnzahl = 1,
            nachfassAbstandMinuten = 20,
            aktiv = true,
            erstelltAm = LocalDate.of(2025, 12, 1),
            archiviertAm = null,
        )

        val rundlauf = AktivitaetEntwurf.aus(aktivitaet).zuAktivitaet(aktivitaet.erstelltAm)

        assertEquals(aktivitaet, rundlauf)
    }

    @Test
    fun `ein geleertes Nachfass-Feld faellt auf den Modell-Standard zurueck statt Nachfassen abzuschalten`() {
        val entwurf = gueltig.copy(nachfassAnzahlText = "")

        val aktivitaet = entwurf.zuAktivitaet(LocalDate.of(2026, 3, 2))

        // 2 ist Aktivitaet.nachfassAnzahl's eigener Standard -- ein geleertes Feld darf
        // Nachfassen nicht implizit auf 0 (aus) setzen.
        assertEquals(2, aktivitaet.nachfassAnzahl)
    }
}
