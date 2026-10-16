package de.vanhake.dayorganizer.ui.statistik

import de.vanhake.dayorganizer.domain.rechner.TagesBilanz
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatistikDarstellungTest {

    private val t = LocalDate.of(2026, 3, 10)

    @Test
    fun `heatStufe unterscheidet nichts geplant von nichts geschafft`() {
        assertEquals(0, heatStufe(TagesBilanz(t, 0, 0)))
        assertEquals(1, heatStufe(TagesBilanz(t, 0, 3)))
        assertEquals(1, heatStufe(TagesBilanz(t, 1, 3)))
        assertEquals(2, heatStufe(TagesBilanz(t, 2, 3)))
        assertEquals(3, heatStufe(TagesBilanz(t, 3, 4)))
        assertEquals(4, heatStufe(TagesBilanz(t, 3, 3)))
    }

    @Test
    fun `heatStufe bleibt im gueltigen Bereich`() {
        for (gesamt in 0..6) {
            for (erledigt in 0..gesamt) {
                assertTrue(heatStufe(TagesBilanz(t, erledigt, gesamt)) in 0 until HEAT_STUFEN)
            }
        }
    }

    @Test
    fun `gesamtQuoteProzent rundet ueber alle Tage`() {
        assertEquals(75, gesamtQuoteProzent(listOf(TagesBilanz(t, 1, 2), TagesBilanz(t, 2, 2))))
        assertEquals(33, gesamtQuoteProzent(listOf(TagesBilanz(t, 1, 3))))
        assertNull(gesamtQuoteProzent(emptyList()))
        assertNull(gesamtQuoteProzent(listOf(TagesBilanz(t, 0, 0))))
    }

    @Test
    fun `serieText nennt die Einheit und beachtet den Singular`() {
        assertEquals("Serie 9 Tage · längste 21 Tage", serieText(9, 21, false))
        assertEquals("Serie 1 Tag · längste 1 Tag", serieText(1, 1, false))
        assertEquals("Serie 3 Wochen · längste 6 Wochen", serieText(3, 6, true))
    }

    @Test
    fun `metaText nennt geschafft und verpasst`() {
        assertEquals("56 geschafft · 2 verpasst", metaText(56, 2))
    }
}
