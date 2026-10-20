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

    private val acht = LocalTime.of(8, 0)
    private val zwoelf = LocalTime.of(12, 0)

    private val gueltig = AktivitaetEntwurf(
        name = "Tabletten",
        basisPunkte = 10,
        typ = ZeitplanTyp.FESTE_ZEITEN,
        zeiten = listOf(acht),
        wochentage = DayOfWeek.entries.toSet(),
    )

    @Test
    fun `ein vollstaendiger Entwurf hat keine Fehler`() {
        assertEquals(emptyList(), gueltig.pruefen())
    }

    @Test
    fun `ein neuer Entwurf ist bis auf den Namen sofort gueltig`() {
        assertEquals(listOf(EntwurfFehler.NAME_LEER), AktivitaetEntwurf().pruefen())
    }

    @Test
    fun `leerer Name und nicht positive Punkte werden gemeldet`() {
        val fehler = gueltig.copy(name = "  ", basisPunkte = 0).pruefen()

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
            zielAnzahl = 0,
        )

        assertEquals(listOf(EntwurfFehler.ZIEL_NICHT_POSITIV), entwurf.pruefen())
    }

    @Test
    fun `ein Intervall braucht ein positives Fenster`() {
        val entwurf = gueltig.copy(
            typ = ZeitplanTyp.INTERVALL,
            zeiten = emptyList(),
            intervallStunden = 0,
            fensterVon = LocalTime.of(20, 0),
            fensterBis = LocalTime.of(8, 0),
        )

        assertTrue(EntwurfFehler.INTERVALL_UNGUELTIG in entwurf.pruefen())
    }

    @Test
    fun `ein Tagesziel braucht eine positive Anzahl und einen Wochentag, aber keine Uhrzeit`() {
        val entwurf = gueltig.copy(
            typ = ZeitplanTyp.TAGESZIEL,
            zeiten = emptyList(),
            wochentage = emptySet(),
            zielAnzahl = 0,
        )

        assertEquals(
            listOf(EntwurfFehler.ZIEL_NICHT_POSITIV, EntwurfFehler.KEIN_WOCHENTAG),
            entwurf.pruefen(),
        )
    }

    @Test
    fun `Punkte aendern sich in Fuenferschritten und bleiben mindestens 5`() {
        val entwurf = gueltig.copy(basisPunkte = 10)

        assertEquals(15, entwurf.punkteAendern(+1).basisPunkte)
        assertEquals(5, entwurf.punkteAendern(-1).basisPunkte)
        assertEquals(5, entwurf.copy(basisPunkte = 5).punkteAendern(-1).basisPunkte)
    }

    @Test
    fun `krumme Altwerte springen aufs Fuenferraster`() {
        val krumm = gueltig.copy(basisPunkte = 7)

        assertEquals(10, krumm.punkteAendern(+1).basisPunkte)
        assertEquals(5, krumm.punkteAendern(-1).basisPunkte)
        assertEquals(5, gueltig.copy(basisPunkte = 3).punkteAendern(+1).basisPunkte)
        assertEquals(5, gueltig.copy(basisPunkte = 3).punkteAendern(-1).basisPunkte)
    }

    @Test
    fun `das Intervall bleibt zwischen 1 und 12 Stunden`() {
        assertEquals(1, gueltig.copy(intervallStunden = 1).intervallAendern(-1).intervallStunden)
        assertEquals(12, gueltig.copy(intervallStunden = 12).intervallAendern(+1).intervallStunden)
        assertEquals(4, gueltig.copy(intervallStunden = 3).intervallAendern(+1).intervallStunden)
    }

    @Test
    fun `ein geladener Altwert ueber 12 Stunden faellt beim ersten Schritt auf 12`() {
        assertEquals(12, gueltig.copy(intervallStunden = 16).intervallAendern(-1).intervallStunden)
    }

    @Test
    fun `das Ziel bleibt beim Verringern mindestens 1`() {
        assertEquals(1, gueltig.copy(zielAnzahl = 1).zielAendern(-1).zielAnzahl)
        assertEquals(4, gueltig.copy(zielAnzahl = 3).zielAendern(+1).zielAnzahl)
    }

    @Test
    fun `ein Wochentag wird umgeschaltet`() {
        val ohneMontag = gueltig.wochentagUmschalten(DayOfWeek.MONDAY)

        assertTrue(DayOfWeek.MONDAY !in ohneMontag.wochentage)
        assertTrue(DayOfWeek.MONDAY in ohneMontag.wochentagUmschalten(DayOfWeek.MONDAY).wochentage)
    }

    @Test
    fun `eine hinzugefuegte Uhrzeit wird einsortiert und nicht doppelt aufgenommen`() {
        val entwurf = gueltig.copy(zeiten = listOf(zwoelf))
            .zeitHinzufuegen(acht)
            .zeitHinzufuegen(zwoelf)

        assertEquals(listOf(acht, zwoelf), entwurf.zeiten)
    }

    @Test
    fun `eine Uhrzeit wird entfernt`() {
        val entwurf = gueltig.copy(zeiten = listOf(acht, zwoelf)).zeitEntfernen(acht)

        assertEquals(listOf(zwoelf), entwurf.zeiten)
    }

    @Test
    fun `der Zeitplan nimmt die Uhrzeiten sortiert auf`() {
        val entwurf = gueltig.copy(zeiten = listOf(zwoelf, acht))

        assertEquals(
            Zeitplan.FesteZeiten(listOf(acht, zwoelf), DayOfWeek.entries.toSet()),
            entwurf.zeitplan(),
        )
    }

    @Test
    fun `beim Tagesziel werden die Uhrzeiten zu Erinnerungszeiten`() {
        val entwurf = gueltig.copy(typ = ZeitplanTyp.TAGESZIEL, zielAnzahl = 2, zeiten = listOf(acht))

        assertEquals(
            Zeitplan.Tagesziel(2, DayOfWeek.entries.toSet(), listOf(acht)),
            entwurf.zeitplan(),
        )
    }

    @Test
    fun `das Intervall uebernimmt das Zeitfenster`() {
        val entwurf = gueltig.copy(
            typ = ZeitplanTyp.INTERVALL,
            intervallStunden = 2,
            fensterVon = LocalTime.of(9, 0),
            fensterBis = LocalTime.of(17, 0),
        )

        assertEquals(
            Zeitplan.Intervall(2, LocalTime.of(9, 0), LocalTime.of(17, 0), DayOfWeek.entries.toSet()),
            entwurf.zeitplan(),
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
        assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(20, 30)), entwurf.zeiten)

        val rundlauf = entwurf.zuAktivitaet(LocalDate.of(2026, 3, 2))
        assertEquals(aktivitaet.zeitplan, rundlauf.zeitplan)
    }

    @Test
    fun `ein Tagesziel ohne Erinnerung laedt ohne Uhrzeiten`() {
        val aktivitaet = Aktivitaet(
            id = 7,
            name = "Lesen",
            farbe = 0xFF4CAF50.toInt(),
            basisPunkte = 10,
            zeitplan = Zeitplan.Tagesziel(1, DayOfWeek.entries.toSet()),
            erstelltAm = LocalDate.of(2025, 12, 1),
        )

        assertEquals(emptyList(), AktivitaetEntwurf.aus(aktivitaet).zeiten)
    }

    @Test
    fun `der Entwurf wird in den richtigen Zeitplan umgesetzt`() {
        val aktivitaet = gueltig.zuAktivitaet(LocalDate.of(2026, 3, 2))

        assertEquals("Tabletten", aktivitaet.name)
        assertEquals(10, aktivitaet.basisPunkte)
        assertEquals(
            Zeitplan.FesteZeiten(listOf(acht), DayOfWeek.entries.toSet()),
            aktivitaet.zeitplan,
        )
    }

    @Test
    fun `ein Wochenziel wird in den Wochenziel-Zeitplan umgesetzt`() {
        val entwurf = gueltig.copy(typ = ZeitplanTyp.WOCHENZIEL, zielAnzahl = 3)

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
            symbol = "pill",
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

    @Test
    fun `eine Einmalaufgabe ohne Deadline laesst sich nicht speichern`() {
        val entwurf = gueltig.copy(typ = ZeitplanTyp.EINMALIG)

        assertEquals(listOf(EntwurfFehler.DEADLINE_FEHLT), entwurf.pruefen())
    }

    @Test
    fun `eine Einmalaufgabe uebersteht das Laden und Speichern`() {
        val aktivitaet = Aktivitaet(
            id = 3,
            name = "Steuererklaerung",
            farbe = 1,
            basisPunkte = 20,
            zeitplan = Zeitplan.Einmalig(LocalDate.of(2026, 5, 31), LocalTime.of(18, 0)),
            erstelltAm = LocalDate.of(2026, 3, 1),
        )

        val entwurf = AktivitaetEntwurf.aus(aktivitaet)

        assertEquals(ZeitplanTyp.EINMALIG, entwurf.typ)
        assertEquals(emptyList(), entwurf.pruefen())
        assertEquals(aktivitaet.zeitplan, entwurf.zuAktivitaet(LocalDate.of(2026, 4, 1)).zeitplan)
    }

    @Test
    fun `eine Deadline vor dem Beginn wird mit Datum gemeldet`() {
        val heute = LocalDate.of(2026, 3, 2)
        val entwurf = gueltig.copy(typ = ZeitplanTyp.EINMALIG, deadline = heute.minusDays(1))

        assertEquals(listOf(EntwurfFehler.DEADLINE_ZU_FRUEH), entwurf.pruefen(heute))
        assertEquals(emptyList(), entwurf.copy(deadline = heute).pruefen(heute))
        // Ohne Datum entfaellt nur diese Pruefung.
        assertEquals(emptyList(), entwurf.pruefen())
    }

    @Test
    fun `eine bestehende Einmalaufgabe misst die Deadline am Anlegetag`() {
        val entwurf = gueltig.copy(
            typ = ZeitplanTyp.EINMALIG,
            erstelltAm = LocalDate.of(2026, 2, 1),
            deadline = LocalDate.of(2026, 2, 20),
        )

        assertEquals(emptyList(), entwurf.pruefen(LocalDate.of(2026, 3, 2)))
    }

    @Test
    fun `der Wechsel zur Einmalaufgabe hebt unberuehrte Punkte auf deren Standard`() {
        assertEquals(20, AktivitaetEntwurf().typWaehlen(ZeitplanTyp.EINMALIG).basisPunkte)
        assertEquals(15, AktivitaetEntwurf(basisPunkte = 15).typWaehlen(ZeitplanTyp.EINMALIG).basisPunkte)
        assertEquals(10, AktivitaetEntwurf().typWaehlen(ZeitplanTyp.TAGESZIEL).basisPunkte)
    }

    @Test
    fun `Erinnern laesst sich aus- und mit der Standardzeit wieder einschalten`() {
        val aus = AktivitaetEntwurf(erinnerungsZeit = LocalTime.of(18, 0)).erinnernUmschalten(false)

        assertEquals(null, aus.erinnerungsZeit)
        assertEquals(LocalTime.of(9, 0), aus.erinnernUmschalten(true).erinnerungsZeit)
        assertEquals(
            LocalTime.of(18, 0),
            AktivitaetEntwurf(erinnerungsZeit = LocalTime.of(18, 0)).erinnernUmschalten(true).erinnerungsZeit,
        )
    }

    @Test
    fun `Einmalaufgabe und wiederkehrender Zeitplan lassen sich nicht ineinander umstellen`() {
        assertEquals(ZeitplanTyp.entries, AktivitaetEntwurf.waehlbareTypen(null))
        assertEquals(listOf(ZeitplanTyp.EINMALIG), AktivitaetEntwurf.waehlbareTypen(ZeitplanTyp.EINMALIG))
        assertEquals(
            listOf(ZeitplanTyp.FESTE_ZEITEN, ZeitplanTyp.INTERVALL, ZeitplanTyp.TAGESZIEL, ZeitplanTyp.WOCHENZIEL),
            AktivitaetEntwurf.waehlbareTypen(ZeitplanTyp.TAGESZIEL),
        )
    }
}
