package de.vanhake.dayorganizer.platform.permission

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class WaechterFrischeTest {

    private val installiert = LocalDateTime.of(2026, 3, 2, 12, 0)

    @Test
    fun `frisch installiert und noch kein Lauf gilt nicht als haengend`() {
        assertEquals(
            false,
            waechterHaengt(
                letzterErfolg = null,
                installiertAm = installiert,
                jetzt = installiert.plusMinutes(1),
            ),
        )
    }

    @Test
    fun `ohne Lauf wird ab der Installation gemessen`() {
        assertEquals(
            true,
            waechterHaengt(
                letzterErfolg = null,
                installiertAm = installiert,
                jetzt = installiert.plusHours(37),
            ),
        )
    }

    @Test
    fun `ein frischer Lauf haelt den Hinweis fern`() {
        assertEquals(
            false,
            waechterHaengt(
                letzterErfolg = installiert.plusHours(30),
                installiertAm = installiert,
                jetzt = installiert.plusHours(40),
            ),
        )
    }

    @Test
    fun `ein zu lange zurueckliegender Lauf gilt als haengend`() {
        assertEquals(
            true,
            waechterHaengt(
                letzterErfolg = installiert.plusHours(1),
                installiertAm = installiert,
                jetzt = installiert.plusHours(38),
            ),
        )
    }

    @Test
    fun `genau an der Grenze gilt noch nicht als haengend`() {
        assertEquals(
            false,
            waechterHaengt(
                letzterErfolg = null,
                installiertAm = installiert,
                jetzt = installiert.plus(WAECHTER_FRISCHE_GRENZE),
            ),
        )
    }
}
