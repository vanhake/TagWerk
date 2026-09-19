package de.vanhake.dayorganizer.domain.rechner

import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class SerienRechnerTest {

    private val rechner = SerienRechner()
    private val start = LocalDate.of(2026, 3, 2)

    private fun tag(versatz: Long, status: FaelligkeitStatus) = Faelligkeit(
        id = versatz + 1,
        aktivitaetId = 1,
        periodeVon = start.plusDays(versatz),
        periodeBis = start.plusDays(versatz),
        geplantFuer = start.plusDays(versatz).atTime(8, 0),
        sollAnzahl = 1,
        status = status,
    )

    @Test
    fun `vier erledigte Tage in Folge ergeben Serie vier`() {
        val f = (0L..3L).map { tag(it, FaelligkeitStatus.ERLEDIGT) }

        assertEquals(4, rechner.aktuelleSerie(f, start.plusDays(3)))
    }

    @Test
    fun `eine verpasste Periode bricht die Serie`() {
        val f = listOf(
            tag(0, FaelligkeitStatus.ERLEDIGT),
            tag(1, FaelligkeitStatus.VERPASST),
            tag(2, FaelligkeitStatus.ERLEDIGT),
            tag(3, FaelligkeitStatus.ERLEDIGT),
        )

        assertEquals(2, rechner.aktuelleSerie(f, start.plusDays(3)))
    }

    @Test
    fun `eine ausgelassene Periode bricht die Serie nicht zaehlt aber auch nicht`() {
        val f = listOf(
            tag(0, FaelligkeitStatus.ERLEDIGT),
            tag(1, FaelligkeitStatus.AUSGELASSEN),
            tag(2, FaelligkeitStatus.ERLEDIGT),
        )

        assertEquals(2, rechner.aktuelleSerie(f, start.plusDays(2)))
    }

    @Test
    fun `eine noch offene heutige Periode senkt die Serie nicht`() {
        val f = listOf(
            tag(0, FaelligkeitStatus.ERLEDIGT),
            tag(1, FaelligkeitStatus.ERLEDIGT),
            tag(2, FaelligkeitStatus.OFFEN),
        )

        assertEquals(2, rechner.aktuelleSerie(f, start.plusDays(2)))
    }

    @Test
    fun `eine Periode mit mehreren Faelligkeiten bricht sobald eine verpasst ist`() {
        val morgens = tag(1, FaelligkeitStatus.ERLEDIGT).copy(id = 100)
        val abends = tag(1, FaelligkeitStatus.VERPASST).copy(id = 101)
        val f = listOf(tag(0, FaelligkeitStatus.ERLEDIGT), morgens, abends, tag(2, FaelligkeitStatus.ERLEDIGT))

        assertEquals(1, rechner.aktuelleSerie(f, start.plusDays(2)))
    }

    @Test
    fun `laengste Serie findet den laengsten Block der Historie`() {
        val f = listOf(
            tag(0, FaelligkeitStatus.ERLEDIGT),
            tag(1, FaelligkeitStatus.ERLEDIGT),
            tag(2, FaelligkeitStatus.ERLEDIGT),
            tag(3, FaelligkeitStatus.VERPASST),
            tag(4, FaelligkeitStatus.ERLEDIGT),
        )

        assertEquals(3, rechner.laengsteSerie(f))
    }

    @Test
    fun `ohne Historie ist die Serie null`() {
        assertEquals(0, rechner.aktuelleSerie(emptyList(), start))
        assertEquals(0, rechner.laengsteSerie(emptyList()))
    }

    @Test
    fun `der Serienstand nennt die erste Periode der laufenden Serie`() {
        val f = listOf(
            tag(0, FaelligkeitStatus.ERLEDIGT),
            tag(1, FaelligkeitStatus.VERPASST),
            tag(2, FaelligkeitStatus.ERLEDIGT),
            tag(3, FaelligkeitStatus.AUSGELASSEN),
            tag(4, FaelligkeitStatus.ERLEDIGT),
        )

        val stand = rechner.aktuelleSerieMitStart(f, start.plusDays(4))

        assertEquals(2, stand.laenge)
        assertEquals(start.plusDays(2), stand.startPeriode)
    }

    @Test
    fun `ohne laufende Serie gibt es keine Startperiode`() {
        val f = listOf(tag(0, FaelligkeitStatus.VERPASST))

        assertEquals(SerienStand(0, null), rechner.aktuelleSerieMitStart(f, start))
    }

    @Test
    fun `Perioden nach dem Stichtag zaehlen nicht mit`() {
        val f = listOf(
            tag(0, FaelligkeitStatus.ERLEDIGT),
            tag(1, FaelligkeitStatus.ERLEDIGT),
            tag(2, FaelligkeitStatus.ERLEDIGT),
        )

        assertEquals(2, rechner.aktuelleSerie(f, start.plusDays(1)))
    }

    @Test
    fun `eine verpasste Periode nach dem Stichtag bricht die Serie nicht`() {
        val f = listOf(
            tag(0, FaelligkeitStatus.ERLEDIGT),
            tag(1, FaelligkeitStatus.ERLEDIGT),
            tag(2, FaelligkeitStatus.VERPASST),
        )

        val stand = rechner.aktuelleSerieMitStart(f, start.plusDays(1))

        assertEquals(2, stand.laenge)
        assertEquals(start.plusDays(0), stand.startPeriode)
    }

    @Test
    fun `eine ausgelassene Periode in der Mitte teilt die laengste Serie nicht`() {
        val f = listOf(
            tag(0, FaelligkeitStatus.ERLEDIGT),
            tag(1, FaelligkeitStatus.ERLEDIGT),
            tag(2, FaelligkeitStatus.AUSGELASSEN),
            tag(3, FaelligkeitStatus.ERLEDIGT),
            tag(4, FaelligkeitStatus.ERLEDIGT),
        )

        assertEquals(4, rechner.laengsteSerie(f))
    }
}
