package de.vanhake.dayorganizer.domain.rechner

import de.vanhake.dayorganizer.domain.aktivitaet
import de.vanhake.dayorganizer.domain.model.Zeitplan
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class ZeitplanRechnerIntervallTest {

    private val rechner = ZeitplanRechner()

    @Test
    fun `alle drei Stunden zwischen acht und zweiundzwanzig Uhr ergibt fuenf Zeitpunkte`() {
        val a = aktivitaet(
            zeitplan = Zeitplan.Intervall(
                alleNStunden = 3,
                von = LocalTime.of(8, 0),
                bis = LocalTime.of(22, 0),
                wochentage = DayOfWeek.entries.toSet(),
            ),
        )

        val ergebnis = rechner.faelligkeitenFuer(
            aktivitaet = a,
            von = LocalDate.of(2026, 3, 2),
            bis = LocalDate.of(2026, 3, 2),
        )

        assertEquals(
            listOf(
                LocalTime.of(8, 0),
                LocalTime.of(11, 0),
                LocalTime.of(14, 0),
                LocalTime.of(17, 0),
                LocalTime.of(20, 0),
            ),
            ergebnis.map { it.geplantFuer!!.toLocalTime() },
        )
    }

    @Test
    fun `das Fensterende ist inklusive wenn es genau getroffen wird`() {
        val a = aktivitaet(
            zeitplan = Zeitplan.Intervall(
                alleNStunden = 4,
                von = LocalTime.of(8, 0),
                bis = LocalTime.of(20, 0),
                wochentage = DayOfWeek.entries.toSet(),
            ),
        )

        val ergebnis = rechner.faelligkeitenFuer(
            aktivitaet = a,
            von = LocalDate.of(2026, 3, 2),
            bis = LocalDate.of(2026, 3, 2),
        )

        assertEquals(4, ergebnis.size)
        assertEquals(LocalTime.of(20, 0), ergebnis.last().geplantFuer!!.toLocalTime())
    }

    @Test
    fun `Intervall respektiert die Wochentagsauswahl`() {
        val a = aktivitaet(
            zeitplan = Zeitplan.Intervall(
                alleNStunden = 6,
                von = LocalTime.of(9, 0),
                bis = LocalTime.of(21, 0),
                wochentage = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            ),
        )

        val ergebnis = rechner.faelligkeitenFuer(
            aktivitaet = a,
            von = LocalDate.of(2026, 3, 2),
            bis = LocalDate.of(2026, 3, 8),
        )

        // Samstag 2026-03-07 und Sonntag 2026-03-08, je drei Zeitpunkte (9, 15, 21 Uhr).
        assertEquals(6, ergebnis.size)
        assertEquals(
            setOf(LocalDate.of(2026, 3, 7), LocalDate.of(2026, 3, 8)),
            ergebnis.map { it.periodeVon }.toSet(),
        )
    }
}
