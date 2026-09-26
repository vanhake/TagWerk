package de.vanhake.dayorganizer.widget

import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import de.vanhake.dayorganizer.domain.model.Zeitplan
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class WidgetZustandTest {

    private val tag = LocalDate.of(2026, 3, 2)
    private val jetzt = LocalDateTime.of(2026, 3, 2, 12, 0)

    private val tabletten = Aktivitaet(
        id = 1, name = "Tabletten", farbe = 1, basisPunkte = 10,
        zeitplan = Zeitplan.FesteZeiten(listOf(LocalTime.of(8, 0)), DayOfWeek.entries.toSet()),
        erstelltAm = LocalDate.of(2026, 1, 1),
    )

    private fun termin(id: Long, stunde: Int, status: FaelligkeitStatus = FaelligkeitStatus.OFFEN) =
        Faelligkeit(
            id = id, aktivitaetId = 1, periodeVon = tag, periodeBis = tag,
            geplantFuer = tag.atTime(stunde, 0), sollAnzahl = 1, status = status,
        )

    @Test
    fun `ueberfaellige stehen oben`() {
        val zustand = WidgetZusammenstellung.aus(
            faelligkeiten = listOf(termin(11, 18), termin(10, 8)),
            aktivitaeten = listOf(tabletten),
            kontostand = 120,
            jetzt = jetzt,
            hoechstens = 5,
        )

        assertEquals(listOf(10L, 11L), zustand.eintraege.map { it.faelligkeitId })
        assertEquals(true, zustand.eintraege.first().ueberfaellig)
        assertEquals(120, zustand.kontostand)
    }

    @Test
    fun `erledigte erscheinen nicht`() {
        val zustand = WidgetZusammenstellung.aus(
            faelligkeiten = listOf(
                termin(10, 8, FaelligkeitStatus.ERLEDIGT),
                termin(11, 18),
            ),
            aktivitaeten = listOf(tabletten),
            kontostand = 0,
            jetzt = jetzt,
            hoechstens = 5,
        )

        assertEquals(listOf(11L), zustand.eintraege.map { it.faelligkeitId })
    }

    @Test
    fun `was nicht passt wird gezaehlt statt verschwiegen`() {
        val zustand = WidgetZusammenstellung.aus(
            faelligkeiten = (1L..6L).map { termin(it, 8 + it.toInt()) },
            aktivitaeten = listOf(tabletten),
            kontostand = 0,
            jetzt = jetzt,
            hoechstens = 4,
        )

        assertEquals(4, zustand.eintraege.size)
        assertEquals(2, zustand.weitere)
        // Die Anzahl allein beweist nichts: takeLast statt take oder eine verkehrte
        // Sortierung ergaeben ebenfalls vier Eintraege und zwei weitere -- nur die
        // falschen. Es muessen die dringendsten bleiben. Um 12:00 sind 09:00 bis
        // 11:00 ueberfaellig, danach kommt 12:00; 13:00 und 14:00 fallen weg.
        assertEquals(listOf(1L, 2L, 3L, 4L), zustand.eintraege.map { it.faelligkeitId })
    }

    // Zaehlziele haben keinen Sollzeitpunkt. Sie gehoeren hinter alles mit Uhrzeit,
    // auch hinter spaete Termine, und fallen bei Platzmangel als Erste weg -- sie
    // lassen sich ueber den ganzen Tag erledigen, eine Uhrzeit nicht.
    @Test
    fun `Zaehlziele stehen hinter allem mit Uhrzeit und fallen zuerst weg`() {
        val ziel = Faelligkeit(
            id = 50, aktivitaetId = 1, periodeVon = tag, periodeBis = tag.plusDays(6),
            geplantFuer = null, sollAnzahl = 3,
        )
        val zustand = WidgetZusammenstellung.aus(
            faelligkeiten = listOf(ziel, termin(10, 20), termin(11, 9)),
            aktivitaeten = listOf(tabletten),
            kontostand = 0,
            jetzt = jetzt,
            hoechstens = 2,
        )

        assertEquals(listOf(11L, 10L), zustand.eintraege.map { it.faelligkeitId })
        assertEquals(1, zustand.weitere)
    }

    @Test
    fun `Zaehlziele erscheinen ohne Uhrzeit`() {
        val ziel = Faelligkeit(
            id = 30, aktivitaetId = 1, periodeVon = tag, periodeBis = tag.plusDays(6),
            geplantFuer = null, sollAnzahl = 3, istAnzahl = 1,
        )

        val zustand = WidgetZusammenstellung.aus(listOf(ziel), listOf(tabletten), 0, jetzt, 5)

        assertEquals(null, zustand.eintraege.single().zeitText)
    }

    // Review-Fund G2: ein Tipp auf ein Zaehlziel aendert am Eintrag heute nichts
    // Sichtbares, ein zweiter Tipp aus Unsicherheit zaehlt dann doppelt. Das Widget muss
    // denselben Stand tragen wie der Heute-Bildschirm (HeuteZusammenstellung/HeuteScreen).
    @Test
    fun `ein Zaehlziel traegt seinen Stand`() {
        val ziel = Faelligkeit(
            id = 31, aktivitaetId = 1, periodeVon = tag, periodeBis = tag.plusDays(6),
            geplantFuer = null, sollAnzahl = 3, istAnzahl = 1,
        )

        val zustand = WidgetZusammenstellung.aus(listOf(ziel), listOf(tabletten), 0, jetzt, 5)

        val eintrag = zustand.eintraege.single()
        assertEquals(1, eintrag.istAnzahl)
        assertEquals(3, eintrag.sollAnzahl)
    }
}
