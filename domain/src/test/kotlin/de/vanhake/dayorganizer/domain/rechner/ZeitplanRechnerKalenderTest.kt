package de.vanhake.dayorganizer.domain.rechner

import de.vanhake.dayorganizer.domain.aktivitaet
import de.vanhake.dayorganizer.domain.model.Zeitplan
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests fuer die Berechnung von Faelligkeiten aus Zeitplaenen.
 *
 * Tests an den Umstellungstagen (29.03. und 25.10.) verwenden diese Daten,
 * um sicherzustellen, dass [faelligkeitenFuer] rein mit LocalDate und LocalTime arbeitet
 * und keine Zeitzone in die Berechnung einfuehrt. Die Tests schlagen fehl, sobald eine
 * Zeitzone in die Berechnung von festeZeiten eindringt.
 */
class ZeitplanRechnerKalenderTest {

    private val rechner = ZeitplanRechner()

    private fun taeglichUm(vararg stunden: Int) = aktivitaet(
        zeitplan = Zeitplan.FesteZeiten(
            zeiten = stunden.map { LocalTime.of(it, 0) },
            wochentage = DayOfWeek.entries.toSet(),
        ),
    )

    @Test
    fun `die Berechnung bleibt am Sommerzeitwechsel zeitzonenfrei`() {
        // In Europa wird am letzten Sonntag im Maerz 2026 (29.03.) umgestellt;
        // 02:30 gibt es an diesem Tag nicht.
        val ergebnis = rechner.faelligkeitenFuer(
            aktivitaet = taeglichUm(2, 8),
            von = LocalDate.of(2026, 3, 29),
            bis = LocalDate.of(2026, 3, 29),
        )

        assertEquals(2, ergebnis.size)
        assertEquals(
            listOf(LocalDateTime.of(2026, 3, 29, 2, 0), LocalDateTime.of(2026, 3, 29, 8, 0)),
            ergebnis.map { it.geplantFuer },
        )
    }

    @Test
    fun `die Berechnung bleibt am Winterzeitwechsel zeitzonenfrei`() {
        // Am 25.10.2026 gibt es 02:30 zweimal.
        val ergebnis = rechner.faelligkeitenFuer(
            aktivitaet = taeglichUm(2),
            von = LocalDate.of(2026, 10, 25),
            bis = LocalDate.of(2026, 10, 25),
        )

        assertEquals(1, ergebnis.size)
    }

    @Test
    fun `der 29 Februar eines Schaltjahres erzeugt eine Faelligkeit`() {
        val ergebnis = rechner.faelligkeitenFuer(
            aktivitaet = taeglichUm(8),
            von = LocalDate.of(2028, 2, 28),
            bis = LocalDate.of(2028, 3, 1),
        )

        assertEquals(3, ergebnis.size)
        assertEquals(LocalDate.of(2028, 2, 29), ergebnis[1].periodeVon)
    }

    @Test
    fun `ein Wochenziel ueber den Jahreswechsel bleibt eine Periode je Woche`() {
        val ergebnis = rechner.faelligkeitenFuer(
            aktivitaet = aktivitaet(zeitplan = Zeitplan.Wochenziel(anzahl = 3)),
            von = LocalDate.of(2026, 12, 28),
            bis = LocalDate.of(2027, 1, 10),
        )

        assertEquals(2, ergebnis.size)
        assertEquals(LocalDate.of(2026, 12, 28), ergebnis[0].periodeVon)
        assertEquals(LocalDate.of(2027, 1, 4), ergebnis[1].periodeVon)
    }
}
