package de.vanhake.dayorganizer.domain.rechner

import de.vanhake.dayorganizer.domain.aktivitaet
import de.vanhake.dayorganizer.domain.model.Zeitplan
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class ZeitplanRechnerKalenderTest {

    private val rechner = ZeitplanRechner()
    private val berlin = ZoneId.of("Europe/Berlin")

    private fun taeglichUm(vararg stunden: Int) = aktivitaet(
        zeitplan = Zeitplan.FesteZeiten(
            zeiten = stunden.map { LocalTime.of(it, 0) },
            wochentage = DayOfWeek.entries.toSet(),
        ),
    )

    @Test
    fun `an der Sommerzeitumstellung entsteht genau eine Faelligkeit je Uhrzeit`() {
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
    fun `an der Winterzeitumstellung entsteht keine doppelte Faelligkeit`() {
        // Am 25.10.2026 gibt es 02:30 zweimal.
        val ergebnis = rechner.faelligkeitenFuer(
            aktivitaet = taeglichUm(2),
            von = LocalDate.of(2026, 10, 25),
            bis = LocalDate.of(2026, 10, 25),
        )

        assertEquals(1, ergebnis.size)
    }

    @Test
    fun `eine ausgefallene Uhrzeit wird beim Aufloesen auf einen gueltigen Zeitpunkt gelegt`() {
        // So loest der AlarmPlaner spaeter auf. ZoneId.getRules verschiebt eine
        // nicht existierende Ortszeit nach vorn statt zu scheitern.
        val ausgefallen = LocalDateTime.of(2026, 3, 29, 2, 30)

        val instant = ausgefallen.atZone(berlin).toInstant()

        assertEquals(LocalDateTime.of(2026, 3, 29, 3, 30), LocalDateTime.ofInstant(instant, berlin))
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
