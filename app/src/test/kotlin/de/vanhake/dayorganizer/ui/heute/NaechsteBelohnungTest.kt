package de.vanhake.dayorganizer.ui.heute

import de.vanhake.dayorganizer.domain.model.Belohnung
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NaechsteBelohnungTest {

    private val kino = Belohnung(1, "Kinoabend", preisPunkte = 200)
    private val buch = Belohnung(2, "Neues Buch", preisPunkte = 300)
    private val ausflug = Belohnung(3, "Ausflug", preisPunkte = 800)

    @Test
    fun `die guenstigste noch nicht bezahlbare Belohnung wird gezeigt`() {
        val ergebnis = NaechsteBelohnungWahl.aus(listOf(ausflug, buch, kino), kontostand = 250)

        assertEquals(NaechsteBelohnung("Neues Buch", 300, 50), ergebnis)
    }

    @Test
    fun `sind alle bezahlbar wird die teuerste gezeigt`() {
        val ergebnis = NaechsteBelohnungWahl.aus(listOf(kino, buch), kontostand = 500)

        assertEquals(NaechsteBelohnung("Neues Buch", 300, 0), ergebnis)
    }

    @Test
    fun `ohne Belohnungen gibt es nichts zu zeigen`() {
        assertNull(NaechsteBelohnungWahl.aus(emptyList(), kontostand = 500))
    }

    @Test
    fun `archivierte Belohnungen zaehlen nicht`() {
        val ergebnis = NaechsteBelohnungWahl.aus(
            listOf(kino.copy(archiviert = true), buch),
            kontostand = 0,
        )

        assertEquals("Neues Buch", ergebnis?.name)
    }

    // Die Grenze selbst: genau bezahlbar zaehlt als bezahlbar und ist damit kein
    // Ziel mehr, auf das hingespart wird -- sonst stuende dauerhaft "noch 0 Punkte
    // bis". Der Test braucht die teurere zweite Belohnung, um ueberhaupt
    // unterscheiden zu koennen: mit nur einer liefern > und >= dasselbe Ergebnis.
    @Test
    fun `eine genau bezahlbare Belohnung ist kein Sparziel mehr`() {
        val ziel = NaechsteBelohnungWahl.aus(
            listOf(
                Belohnung(id = 1, name = "Kinoabend", preisPunkte = 200),
                Belohnung(id = 2, name = "Wochenendausflug", preisPunkte = 800),
            ),
            kontostand = 200,
        )

        assertEquals("Wochenendausflug", ziel?.name)
        assertEquals(600, ziel?.fehlendePunkte)
    }
}
