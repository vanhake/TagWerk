package de.vanhake.dayorganizer.data.mapper

import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Buchung
import de.vanhake.dayorganizer.domain.model.BuchungsArt
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import de.vanhake.dayorganizer.domain.model.SerienSchwelle
import de.vanhake.dayorganizer.domain.model.Zeitplan
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class EntityMapperTest {

    private val aktivitaet = Aktivitaet(
        id = 7,
        name = "Sport",
        notiz = "Laufen oder Rad",
        farbe = 123,
        basisPunkte = 20,
        zeitplan = Zeitplan.FesteZeiten(
            zeiten = listOf(LocalTime.of(18, 0)),
            wochentage = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
        ),
        kulanzMinuten = 45,
        puenktlichkeitsBonus = 5,
        serienSchwellen = listOf(SerienSchwelle(7, 10)),
        nachfassAnzahl = 1,
        nachfassAbstandMinuten = 20,
        aktiv = true,
        erstelltAm = LocalDate.of(2026, 2, 1),
        archiviertAm = null,
    )

    @Test
    fun `Aktivitaet uebersteht den Rundlauf`() {
        assertEquals(aktivitaet, aktivitaet.zuEntity().zuDomaene())
    }

    @Test
    fun `Faelligkeit uebersteht den Rundlauf`() {
        val f = Faelligkeit(
            id = 3,
            aktivitaetId = 7,
            periodeVon = LocalDate.of(2026, 3, 2),
            periodeBis = LocalDate.of(2026, 3, 2),
            geplantFuer = LocalDateTime.of(2026, 3, 2, 18, 0),
            sollAnzahl = 1,
            istAnzahl = 1,
            status = FaelligkeitStatus.ERLEDIGT,
            erledigtAm = LocalDateTime.of(2026, 3, 2, 18, 12),
            warPuenktlich = true,
        )

        assertEquals(f, f.zuEntity().zuDomaene())
    }

    @Test
    fun `der Schluessel unterscheidet Sollzeitpunkt und Zaehlziel`() {
        val termin = Faelligkeit(
            aktivitaetId = 7,
            periodeVon = LocalDate.of(2026, 3, 2),
            periodeBis = LocalDate.of(2026, 3, 2),
            geplantFuer = LocalDateTime.of(2026, 3, 2, 18, 0),
            sollAnzahl = 1,
        )
        val ziel = termin.copy(geplantFuer = null, sollAnzahl = 3)

        assertEquals("7|2026-03-02|2026-03-02T18:00", termin.schluessel())
        assertEquals("7|2026-03-02|ZIEL", ziel.schluessel())
    }

    @Test
    fun `Buchung uebersteht den Rundlauf`() {
        val b = Buchung(
            id = 9,
            art = BuchungsArt.SERIENBONUS,
            betrag = 10,
            zeitpunkt = LocalDateTime.of(2026, 3, 2, 18, 12),
            aktivitaetId = 7,
            faelligkeitId = 3,
            belohnungId = null,
            schwelle = 7,
            notiz = "",
        )

        assertEquals(b, b.zuEntity().zuDomaene())
    }
}
