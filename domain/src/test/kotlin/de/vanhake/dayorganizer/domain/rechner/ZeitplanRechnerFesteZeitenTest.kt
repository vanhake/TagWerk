package de.vanhake.dayorganizer.domain.rechner

import de.vanhake.dayorganizer.domain.aktivitaet
import de.vanhake.dayorganizer.domain.model.Zeitplan
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class ZeitplanRechnerFesteZeitenTest {

    private val rechner = ZeitplanRechner()

    @Test
    fun `zwei Uhrzeiten an drei Tagen ergeben sechs Faelligkeiten`() {
        val a = aktivitaet(
            zeitplan = Zeitplan.FesteZeiten(
                zeiten = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
                wochentage = DayOfWeek.entries.toSet(),
            ),
        )

        val ergebnis = rechner.faelligkeitenFuer(
            aktivitaet = a,
            von = LocalDate.of(2026, 3, 2),
            bis = LocalDate.of(2026, 3, 4),
        )

        assertEquals(6, ergebnis.size)
        assertEquals(LocalDateTime.of(2026, 3, 2, 8, 0), ergebnis.first().geplantFuer)
        assertEquals(LocalDateTime.of(2026, 3, 4, 20, 0), ergebnis.last().geplantFuer)
        assertEquals(1, ergebnis.first().sollAnzahl)
        assertEquals(LocalDate.of(2026, 3, 2), ergebnis.first().periodeVon)
        assertEquals(LocalDate.of(2026, 3, 2), ergebnis.first().periodeBis)
    }

    @Test
    fun `nicht ausgewaehlte Wochentage erzeugen keine Faelligkeit`() {
        val a = aktivitaet(
            zeitplan = Zeitplan.FesteZeiten(
                zeiten = listOf(LocalTime.of(18, 0)),
                wochentage = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            ),
        )

        // 2026-03-02 ist ein Montag, der Zeitraum deckt eine volle Woche ab.
        val ergebnis = rechner.faelligkeitenFuer(
            aktivitaet = a,
            von = LocalDate.of(2026, 3, 2),
            bis = LocalDate.of(2026, 3, 8),
        )

        assertEquals(3, ergebnis.size)
        assertEquals(
            listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            ergebnis.map { it.periodeVon.dayOfWeek },
        )
    }

    @Test
    fun `Zeitraumgrenzen sind inklusive`() {
        val a = aktivitaet(
            zeitplan = Zeitplan.FesteZeiten(
                zeiten = listOf(LocalTime.of(9, 0)),
                wochentage = DayOfWeek.entries.toSet(),
            ),
        )

        val ergebnis = rechner.faelligkeitenFuer(
            aktivitaet = a,
            von = LocalDate.of(2026, 3, 2),
            bis = LocalDate.of(2026, 3, 2),
        )

        assertEquals(1, ergebnis.size)
    }
}
