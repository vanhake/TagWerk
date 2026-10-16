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
        )

        assertEquals(listOf(10L, 11L), zustand.offen.map { it.faelligkeitId })
        assertEquals(true, zustand.offen.first().ueberfaellig)
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
        )

        assertEquals(listOf(11L), zustand.offen.map { it.faelligkeitId })
    }

    // Zaehlziele haben keinen Sollzeitpunkt. Sie gehoeren hinter alles mit Uhrzeit,
    // auch hinter spaete Termine -- sie lassen sich ueber den ganzen Tag erledigen,
    // eine Uhrzeit nicht.
    @Test
    fun `Zaehlziele stehen hinter allem mit Uhrzeit`() {
        val ziel = Faelligkeit(
            id = 50, aktivitaetId = 1, periodeVon = tag, periodeBis = tag.plusDays(6),
            geplantFuer = null, sollAnzahl = 3,
        )
        val zustand = WidgetZusammenstellung.aus(
            faelligkeiten = listOf(ziel, termin(10, 20), termin(11, 9)),
            aktivitaeten = listOf(tabletten),
            kontostand = 0,
            jetzt = jetzt,
        )

        assertEquals(listOf(11L, 10L, 50L), zustand.offen.map { it.faelligkeitId })
    }

    @Test
    fun `Zaehlziele erscheinen ohne Uhrzeit`() {
        val ziel = Faelligkeit(
            id = 30, aktivitaetId = 1, periodeVon = tag, periodeBis = tag.plusDays(6),
            geplantFuer = null, sollAnzahl = 3, istAnzahl = 1,
        )

        val zustand = WidgetZusammenstellung.aus(listOf(ziel), listOf(tabletten), 0, jetzt)

        assertEquals(null, zustand.offen.single().zeitText)
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

        val zustand = WidgetZusammenstellung.aus(listOf(ziel), listOf(tabletten), 0, jetzt)

        val eintrag = zustand.offen.single()
        assertEquals(1, eintrag.istAnzahl)
        assertEquals(3, eintrag.sollAnzahl)
    }

    @Test
    fun `zaehlt erledigt und gesamt wie der Heute-Tab`() {
        val zustand = WidgetZusammenstellung.aus(
            faelligkeiten = listOf(
                termin(1, 8, FaelligkeitStatus.ERLEDIGT),
                termin(2, 9, FaelligkeitStatus.AUSGELASSEN),
                termin(3, 10),
                termin(4, 18),
            ),
            aktivitaeten = listOf(tabletten),
            kontostand = 0,
            jetzt = jetzt,
        )

        assertEquals(1, zustand.erledigt)
        assertEquals(4, zustand.gesamt)
        assertEquals(2, zustand.offen.size)
        assertEquals(null, zustand.leer)
    }

    @Test
    fun `ohne Faelligkeiten heisst es nichts geplant`() {
        val zustand = WidgetZusammenstellung.aus(emptyList(), listOf(tabletten), 0, jetzt)

        assertEquals(WidgetLeer.NICHTS_GEPLANT, zustand.leer)
        assertEquals(0, zustand.gesamt)
    }

    @Test
    fun `alles abgehakt heisst alles erledigt`() {
        val zustand = WidgetZusammenstellung.aus(
            faelligkeiten = listOf(
                termin(1, 8, FaelligkeitStatus.ERLEDIGT),
                termin(2, 9, FaelligkeitStatus.ERLEDIGT),
            ),
            aktivitaeten = listOf(tabletten),
            kontostand = 0,
            jetzt = jetzt,
        )

        assertEquals(WidgetLeer.ALLES_ERLEDIGT, zustand.leer)
    }

    @Test
    fun `Faelligkeiten archivierter Aktivitaeten zaehlen nicht`() {
        val fremd = termin(1, 8).copy(aktivitaetId = 99)

        val zustand = WidgetZusammenstellung.aus(listOf(fremd), listOf(tabletten), 0, jetzt)

        assertEquals(0, zustand.gesamt)
        assertEquals(0, zustand.erledigt)
        assertEquals(WidgetLeer.NICHTS_GEPLANT, zustand.leer)
    }

    @Test
    fun `ein Eintrag traegt Symbol und Wochenziel-Kennung`() {
        val wasser = tabletten.copy(symbol = "water_drop")
        val woche = Faelligkeit(
            id = 60, aktivitaetId = 1, periodeVon = tag, periodeBis = tag.plusDays(6),
            geplantFuer = null, sollAnzahl = 3,
        )

        val zustand = WidgetZusammenstellung.aus(
            listOf(woche, termin(61, 18)), listOf(wasser), 0, jetzt,
        )

        val wochenEintrag = zustand.offen.single { it.faelligkeitId == 60L }
        assertEquals("water_drop", wochenEintrag.symbol)
        assertEquals(true, wochenEintrag.wochenziel)
        assertEquals(false, zustand.offen.single { it.faelligkeitId == 61L }.wochenziel)
    }
}
