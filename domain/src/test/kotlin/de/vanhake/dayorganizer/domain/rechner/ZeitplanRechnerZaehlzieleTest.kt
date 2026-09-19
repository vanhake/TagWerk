package de.vanhake.dayorganizer.domain.rechner

import de.vanhake.dayorganizer.domain.aktivitaet
import de.vanhake.dayorganizer.domain.model.Zeitplan
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ZeitplanRechnerZaehlzieleTest {

    private val rechner = ZeitplanRechner()

    @Test
    fun `Tagesziel erzeugt eine Faelligkeit je Tag mit der Sollanzahl`() {
        val a = aktivitaet(
            zeitplan = Zeitplan.Tagesziel(anzahl = 3, wochentage = DayOfWeek.entries.toSet()),
        )

        val ergebnis = rechner.faelligkeitenFuer(
            aktivitaet = a,
            von = LocalDate.of(2026, 3, 2),
            bis = LocalDate.of(2026, 3, 4),
        )

        assertEquals(3, ergebnis.size)
        ergebnis.forEach {
            assertEquals(3, it.sollAnzahl)
            assertEquals(0, it.istAnzahl)
            assertNull(it.geplantFuer)
            assertEquals(it.periodeVon, it.periodeBis)
        }
    }

    @Test
    fun `Wochenziel erzeugt eine Faelligkeit je ISO-Woche von Montag bis Sonntag`() {
        val a = aktivitaet(zeitplan = Zeitplan.Wochenziel(anzahl = 3))

        // Mittwoch 2026-03-04 bis Dienstag 2026-03-10 beruehrt zwei Wochen.
        val ergebnis = rechner.faelligkeitenFuer(
            aktivitaet = a,
            von = LocalDate.of(2026, 3, 4),
            bis = LocalDate.of(2026, 3, 10),
        )

        assertEquals(2, ergebnis.size)
        assertEquals(LocalDate.of(2026, 3, 2), ergebnis[0].periodeVon)
        assertEquals(LocalDate.of(2026, 3, 8), ergebnis[0].periodeBis)
        assertEquals(LocalDate.of(2026, 3, 9), ergebnis[1].periodeVon)
        assertEquals(LocalDate.of(2026, 3, 15), ergebnis[1].periodeBis)
        assertEquals(3, ergebnis[0].sollAnzahl)
    }

    @Test
    fun `Tagesziel respektiert die Wochentagsauswahl`() {
        val a = aktivitaet(
            zeitplan = Zeitplan.Tagesziel(
                anzahl = 2,
                wochentage = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY),
            ),
        )

        val ergebnis = rechner.faelligkeitenFuer(
            aktivitaet = a,
            von = LocalDate.of(2026, 3, 2),
            bis = LocalDate.of(2026, 3, 8),
        )

        assertEquals(2, ergebnis.size)
    }
}
