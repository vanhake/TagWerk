package de.vanhake.dayorganizer.domain.rechner

import de.vanhake.dayorganizer.domain.aktivitaet
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.Zeitplan
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ZeitplanRechnerEinmaligTest {

    private val rechner = ZeitplanRechner()

    // aktivitaet() legt die Aktivitaet am 2026-01-01 an.
    private val angelegt = LocalDate.of(2026, 1, 1)

    // Ein Freitag, gut zehn Wochen nach dem Anlegen.
    private val deadline = LocalDate.of(2026, 3, 13)

    private fun steuer(erinnerung: LocalTime? = LocalTime.of(9, 0)) =
        aktivitaet(zeitplan = Zeitplan.Einmalig(deadline, erinnerung))

    @Test
    fun `eine einzige Faelligkeit vom Anlegetag bis zur Deadline`() {
        val faelligkeiten = rechner.faelligkeitenFuer(steuer(), LocalDate.of(2026, 2, 2), LocalDate.of(2026, 2, 9))

        assertEquals(
            listOf(
                Faelligkeit(
                    aktivitaetId = 1,
                    periodeVon = angelegt,
                    periodeBis = deadline,
                    geplantFuer = null,
                    sollAnzahl = 1,
                ),
            ),
            faelligkeiten,
        )
    }

    @Test
    fun `jeder Lauf liefert dieselbe Faelligkeit`() {
        val a = steuer()

        assertEquals(
            rechner.faelligkeitenFuer(a, LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 12)),
            rechner.faelligkeitenFuer(a, LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 16)),
        )
    }

    @Test
    fun `nach der Deadline entsteht keine Faelligkeit mehr`() {
        assertEquals(
            emptyList(),
            rechner.faelligkeitenFuer(steuer(), deadline.plusDays(1), deadline.plusDays(8)),
        )
    }

    @Test
    fun `eine Deadline vor dem Anlegetag ergibt keine Faelligkeit`() {
        val a = aktivitaet(zeitplan = Zeitplan.Einmalig(angelegt.minusDays(1)))

        assertEquals(emptyList(), rechner.faelligkeitenFuer(a, angelegt, angelegt.plusDays(7)))
    }

    @Test
    fun `erinnert woechentlich und in den letzten vier Wochen zweimal`() {
        val tage = rechner.einmaligErinnerungstage(angelegt, deadline)

        val freitage = generateSequence(deadline) { it.minusWeeks(1) }
            .takeWhile { !it.isBefore(angelegt) }
            .toList()
        val montage = listOf(
            LocalDate.of(2026, 3, 9),
            LocalDate.of(2026, 3, 2),
            LocalDate.of(2026, 2, 23),
            LocalDate.of(2026, 2, 16),
        )
        assertEquals((freitage + montage).sorted(), tage)
        assertTrue(tage.all { it.dayOfWeek == DayOfWeek.FRIDAY || it.dayOfWeek == DayOfWeek.MONDAY })
        assertEquals(deadline, tage.last())
    }

    @Test
    fun `liegt die Deadline nah gilt sofort der Zweimal-Rhythmus`() {
        val start = LocalDate.of(2026, 3, 1)

        assertEquals(
            listOf(
                LocalDate.of(2026, 3, 2),
                LocalDate.of(2026, 3, 6),
                LocalDate.of(2026, 3, 9),
                LocalDate.of(2026, 3, 13),
            ),
            rechner.einmaligErinnerungstage(start, deadline),
        )
    }

    @Test
    fun `Deadline am Anlegetag erinnert genau an diesem Tag`() {
        assertEquals(listOf(deadline), rechner.einmaligErinnerungstage(deadline, deadline))
    }

    @Test
    fun `Anstoesse liegen zur gewaehlten Uhrzeit an den Erinnerungstagen`() {
        val a = steuer(LocalTime.of(18, 30))
        val f = rechner.faelligkeitenFuer(a, angelegt, angelegt).single()

        val anstoesse = rechner.anstossZeitpunkteFuer(a, f)

        assertEquals(
            rechner.einmaligErinnerungstage(angelegt, deadline).map { it.atTime(18, 30) },
            anstoesse,
        )
        assertEquals(LocalDateTime.of(2026, 3, 13, 18, 30), anstoesse.last())
    }

    @Test
    fun `ohne Erinnerungszeit gibt es keine Anstoesse`() {
        val a = steuer(erinnerung = null)
        val f = rechner.faelligkeitenFuer(a, angelegt, angelegt).single()

        assertEquals(emptyList(), rechner.anstossZeitpunkteFuer(a, f))
    }
}
