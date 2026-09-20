package de.vanhake.dayorganizer.ui.heute

import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Belohnung
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import de.vanhake.dayorganizer.domain.model.Zeitplan
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class HeuteZusammenstellungTest {

    private val tag = LocalDate.of(2026, 3, 2)
    private val jetzt = LocalDateTime.of(2026, 3, 2, 12, 0)

    private val tabletten = Aktivitaet(
        id = 1, name = "Tabletten", farbe = 11, basisPunkte = 10,
        zeitplan = Zeitplan.FesteZeiten(listOf(LocalTime.of(8, 0)), DayOfWeek.entries.toSet()),
        erstelltAm = LocalDate.of(2026, 1, 1),
    )
    private val sport = Aktivitaet(
        id = 2, name = "Sport", farbe = 22, basisPunkte = 20,
        zeitplan = Zeitplan.Wochenziel(3),
        erstelltAm = LocalDate.of(2026, 1, 1),
    )

    private fun termin(id: Long, stunde: Int, status: FaelligkeitStatus) = Faelligkeit(
        id = id, aktivitaetId = 1, periodeVon = tag, periodeBis = tag,
        geplantFuer = tag.atTime(stunde, 0), sollAnzahl = 1, status = status,
    )

    @Test
    fun `Eintraege werden nach Dringlichkeit einsortiert`() {
        val ziel = Faelligkeit(
            id = 30, aktivitaetId = 2, periodeVon = tag, periodeBis = tag.plusDays(6),
            geplantFuer = null, sollAnzahl = 3, istAnzahl = 1,
        )

        val zustand = HeuteZusammenstellung.aus(
            faelligkeiten = listOf(
                termin(10, 8, FaelligkeitStatus.OFFEN),
                termin(11, 13, FaelligkeitStatus.OFFEN),
                termin(12, 20, FaelligkeitStatus.OFFEN),
                termin(13, 7, FaelligkeitStatus.ERLEDIGT),
                ziel,
            ),
            aktivitaeten = listOf(tabletten, sport),
            belohnungen = emptyList(),
            kontostand = 120,
            jetzt = jetzt,
        )

        assertEquals(listOf(10L), zustand.ueberfaellig.map { it.faelligkeitId })
        assertEquals(listOf(11L, 30L), zustand.anstehend.map { it.faelligkeitId })
        assertEquals(listOf(12L), zustand.spaeter.map { it.faelligkeitId })
        assertEquals(listOf(13L), zustand.abgeschlossen.map { it.faelligkeitId })
        assertEquals(120, zustand.kontostand)
    }

    @Test
    fun `der Tagesfortschritt zaehlt nur erledigte`() {
        val zustand = HeuteZusammenstellung.aus(
            faelligkeiten = listOf(
                termin(10, 8, FaelligkeitStatus.ERLEDIGT),
                termin(11, 13, FaelligkeitStatus.AUSGELASSEN),
                termin(12, 20, FaelligkeitStatus.OFFEN),
            ),
            aktivitaeten = listOf(tabletten),
            belohnungen = emptyList(),
            kontostand = 0,
            jetzt = jetzt,
        )

        assertEquals(1, zustand.erledigtHeute)
        assertEquals(3, zustand.gesamtHeute)
    }

    @Test
    fun `Zaehlziele zeigen Uhrzeit nicht sondern ihren Fortschritt`() {
        val ziel = Faelligkeit(
            id = 30, aktivitaetId = 2, periodeVon = tag, periodeBis = tag.plusDays(6),
            geplantFuer = null, sollAnzahl = 3, istAnzahl = 2,
        )

        val zustand = HeuteZusammenstellung.aus(listOf(ziel), listOf(sport), emptyList(), 0, jetzt)

        val eintrag = zustand.anstehend.single()
        assertEquals(null, eintrag.zeitText)
        assertEquals(2, eintrag.istAnzahl)
        assertEquals(3, eintrag.sollAnzahl)
        assertEquals("Sport", eintrag.aktivitaetName)
    }

    // Ohne diesen Test bliebe alles gruen, wenn aus() den Parameter belohnungen
    // stillschweigend ignorierte: jeder andere Test uebergibt hier emptyList().
    @Test
    fun `die naechste Belohnung wird in den Zustand uebernommen`() {
        val zustand = HeuteZusammenstellung.aus(
            faelligkeiten = emptyList(),
            aktivitaeten = emptyList(),
            belohnungen = listOf(
                Belohnung(id = 1, name = "Kinoabend", preisPunkte = 200),
                Belohnung(id = 2, name = "Wochenendausflug", preisPunkte = 800),
            ),
            kontostand = 160,
            jetzt = jetzt,
        )

        val ziel = assertNotNull(zustand.naechsteBelohnung)
        assertEquals("Kinoabend", ziel.name)
        assertEquals(200, ziel.preisPunkte)
        assertEquals(40, ziel.fehlendePunkte)
    }

    @Test
    fun `Faelligkeiten ohne passende Aktivitaet werden uebersprungen`() {
        val zustand = HeuteZusammenstellung.aus(
            faelligkeiten = listOf(termin(10, 8, FaelligkeitStatus.OFFEN)),
            aktivitaeten = emptyList(),
            belohnungen = emptyList(),
            kontostand = 0,
            jetzt = jetzt,
        )

        assertEquals(0, zustand.gesamtHeute)
    }
}
