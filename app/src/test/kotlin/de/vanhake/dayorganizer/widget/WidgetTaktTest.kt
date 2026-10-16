package de.vanhake.dayorganizer.widget

import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WidgetTaktTest {

    private val jetzt = LocalDateTime.of(2026, 10, 16, 7, 30)
    private val heute = LocalDate.of(2026, 10, 16)

    private fun eintrag(
        uhrzeit: LocalDateTime?,
        status: FaelligkeitStatus = FaelligkeitStatus.OFFEN,
        von: LocalDate = heute,
        bis: LocalDate = von,
    ) = Faelligkeit(
        aktivitaetId = 1, periodeVon = von, periodeBis = bis,
        geplantFuer = uhrzeit, sollAnzahl = 1, status = status,
    )

    private fun um(stunde: Int, minute: Int = 0) = LocalDateTime.of(2026, 10, 16, stunde, minute)

    @Test
    fun `der naechste Umschaltpunkt ist die frueheste kuenftige Uhrzeit eines offenen Eintrags`() {
        val liste = listOf(eintrag(um(9)), eintrag(um(8)))
        assertEquals(um(8), WidgetTakt.naechsterUmschaltpunkt(liste, jetzt))
    }

    @Test
    fun `erledigte und vergangene Eintraege zaehlen nicht`() {
        val liste = listOf(
            eintrag(um(7, 45), FaelligkeitStatus.ERLEDIGT),
            eintrag(um(7)),
            eintrag(um(10)),
        )
        assertEquals(um(10), WidgetTakt.naechsterUmschaltpunkt(liste, jetzt))
    }

    @Test
    fun `ein Eintrag genau jetzt zaehlt noch`() {
        assertEquals(jetzt, WidgetTakt.naechsterUmschaltpunkt(listOf(eintrag(um(7, 30))), jetzt))
    }

    @Test
    fun `ohne kuenftige Uhrzeit ist Mitternacht der Umschaltpunkt`() {
        val ziel = eintrag(null, von = LocalDate.of(2026, 10, 12), bis = LocalDate.of(2026, 10, 18))
        assertEquals(LocalDateTime.of(2026, 10, 17, 0, 0), WidgetTakt.naechsterUmschaltpunkt(listOf(ziel), jetzt))
    }

    @Test
    fun `Mitternacht gewinnt gegen einen Termin von morgen`() {
        val morgen = LocalDate.of(2026, 10, 17)
        val liste = listOf(eintrag(LocalDateTime.of(2026, 10, 17, 6, 0), von = morgen))
        assertEquals(LocalDateTime.of(2026, 10, 17, 0, 0), WidgetTakt.naechsterUmschaltpunkt(liste, jetzt))
    }

    @Test
    fun `ein Wochenziel gehoert zu jedem Tag seiner Woche`() {
        val ziel = eintrag(null, von = LocalDate.of(2026, 10, 12), bis = LocalDate.of(2026, 10, 18))
        assertTrue(WidgetTakt.gehoertZuTag(ziel, LocalDate.of(2026, 10, 16)))
        assertFalse(WidgetTakt.gehoertZuTag(ziel, LocalDate.of(2026, 10, 19)))
    }
}
