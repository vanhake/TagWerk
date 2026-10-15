package de.vanhake.dayorganizer.ui.aktivitaeten

import de.vanhake.dayorganizer.domain.model.Zeitplan
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class PlanTextTest {

    private val alle = DayOfWeek.entries.toSet()
    private val werktage = setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY)

    @Test
    fun `feste Zeiten an allen Tagen`() {
        val plan = Zeitplan.FesteZeiten(listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)), alle)

        assertEquals("Täglich · 08:00, 20:00", PlanText.aus(plan))
    }

    @Test
    fun `feste Zeiten an Werktagen`() {
        val plan = Zeitplan.FesteZeiten(listOf(LocalTime.of(12, 30)), werktage)

        assertEquals("Mo–Fr · 12:30", PlanText.aus(plan))
    }

    @Test
    fun `einzelne Tage erscheinen in Wochenreihenfolge, egal wie das Set sortiert ist`() {
        val plan = Zeitplan.FesteZeiten(emptyList(), linkedSetOf(SATURDAY, MONDAY, WEDNESDAY))

        assertEquals("Mo, Mi, Sa · keine Uhrzeit", PlanText.aus(plan))
    }

    @Test
    fun `ein Intervall nennt Abstand und Zeitfenster`() {
        val plan = Zeitplan.Intervall(3, LocalTime.of(8, 0), LocalTime.of(22, 0), alle)

        assertEquals("Täglich · alle 3 Std. von 08:00 bis 22:00", PlanText.aus(plan))
    }

    @Test
    fun `ein Intervall ohne Tage sagt das`() {
        val plan = Zeitplan.Intervall(2, LocalTime.of(9, 0), LocalTime.of(17, 0), emptySet())

        assertEquals("Keine Tage · alle 2 Std. von 09:00 bis 17:00", PlanText.aus(plan))
    }

    @Test
    fun `ein taegliches Tagesziel ohne Erinnerung ist nur die Anzahl`() {
        assertEquals("2× täglich", PlanText.aus(Zeitplan.Tagesziel(2, alle)))
    }

    @Test
    fun `ein Tagesziel an einzelnen Tagen mit Erinnerung nennt beides`() {
        val plan = Zeitplan.Tagesziel(1, setOf(SATURDAY, SUNDAY), listOf(LocalTime.of(19, 0)))

        assertEquals("1× täglich · Sa, So · Erinnerung 19:00", PlanText.aus(plan))
    }

    @Test
    fun `ein Wochenziel ist nur die Anzahl pro Woche`() {
        assertEquals("3× pro Woche", PlanText.aus(Zeitplan.Wochenziel(3)))
    }

    @Test
    fun `Sekunden werden in der Uhrzeit nicht angezeigt`() {
        assertEquals("08:00", PlanText.uhrzeit(LocalTime.of(8, 0, 30)))
    }

    @Test
    fun `die Kuerzel folgen der Wochenreihenfolge ab Montag`() {
        assertEquals(listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"), DayOfWeek.entries.map(PlanText::kuerzel))
    }
}
