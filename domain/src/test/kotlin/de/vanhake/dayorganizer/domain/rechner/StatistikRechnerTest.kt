package de.vanhake.dayorganizer.domain.rechner

import de.vanhake.dayorganizer.domain.model.Buchung
import de.vanhake.dayorganizer.domain.model.BuchungsArt
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class StatistikRechnerTest {

    private val rechner = StatistikRechner()
    private val start = LocalDate.of(2026, 3, 2)

    private fun tag(versatz: Long, status: FaelligkeitStatus, id: Long = versatz + 1) = Faelligkeit(
        id = id,
        aktivitaetId = 1,
        periodeVon = start.plusDays(versatz),
        periodeBis = start.plusDays(versatz),
        geplantFuer = start.plusDays(versatz).atTime(8, 0),
        sollAnzahl = 1,
        status = status,
    )

    @Test
    fun `die Quote laesst ausgelassene ausser Betracht`() {
        val historie = listOf(
            tag(0, FaelligkeitStatus.ERLEDIGT),
            tag(1, FaelligkeitStatus.ERLEDIGT),
            tag(2, FaelligkeitStatus.ERLEDIGT),
            tag(3, FaelligkeitStatus.VERPASST),
            tag(4, FaelligkeitStatus.AUSGELASSEN),
        )

        val statistik = rechner.fuerAktivitaet(1, historie, start.plusDays(4))

        assertEquals(3, statistik.erledigt)
        assertEquals(1, statistik.verpasst)
        assertEquals(1, statistik.ausgelassen)
        assertEquals(0.75f, statistik.quote)
    }

    @Test
    fun `ohne erledigte und verpasste ist die Quote null`() {
        val statistik = rechner.fuerAktivitaet(1, listOf(tag(0, FaelligkeitStatus.OFFEN)), start)

        assertEquals(0f, statistik.quote)
    }

    @Test
    fun `Serien werden mitgeliefert`() {
        val historie = listOf(
            tag(0, FaelligkeitStatus.ERLEDIGT),
            tag(1, FaelligkeitStatus.ERLEDIGT),
            tag(2, FaelligkeitStatus.VERPASST),
            tag(3, FaelligkeitStatus.ERLEDIGT),
        )

        val statistik = rechner.fuerAktivitaet(1, historie, start.plusDays(3))

        assertEquals(1, statistik.aktuelleSerie)
        assertEquals(2, statistik.laengsteSerie)
    }

    @Test
    fun `Tagesbilanzen sind lueckenlos`() {
        val historie = listOf(
            tag(0, FaelligkeitStatus.ERLEDIGT),
            tag(0, FaelligkeitStatus.VERPASST, id = 100),
            tag(2, FaelligkeitStatus.ERLEDIGT),
        )

        val bilanzen = rechner.tagesBilanzen(historie, start, start.plusDays(2))

        assertEquals(3, bilanzen.size)
        assertEquals(TagesBilanz(start, erledigt = 1, gesamt = 2), bilanzen[0])
        assertEquals(TagesBilanz(start.plusDays(1), erledigt = 0, gesamt = 0), bilanzen[1])
        assertEquals(TagesBilanz(start.plusDays(2), erledigt = 1, gesamt = 1), bilanzen[2])
    }

    @Test
    fun `der Punkteverlauf ist kumuliert und haelt sich an Tagen ohne Buchung`() {
        val buchungen = listOf(
            Buchung(art = BuchungsArt.ERLEDIGUNG, betrag = 10, zeitpunkt = start.atTime(8, 0)),
            Buchung(art = BuchungsArt.ERLEDIGUNG, betrag = 5, zeitpunkt = start.atTime(20, 0)),
            Buchung(
                art = BuchungsArt.EINLOESUNG, betrag = -12,
                zeitpunkt = start.plusDays(2).atTime(19, 0),
            ),
        )

        val verlauf = rechner.punkteVerlauf(buchungen, start, start.plusDays(2))

        assertEquals(
            listOf(start to 15, start.plusDays(1) to 15, start.plusDays(2) to 3),
            verlauf,
        )
    }
}
