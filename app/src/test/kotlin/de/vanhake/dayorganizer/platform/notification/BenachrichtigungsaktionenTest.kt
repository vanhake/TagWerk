package de.vanhake.dayorganizer.platform.notification

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BenachrichtigungsaktionenTest {

    @Test
    fun `bekannte Aktionen werden zugeordnet`() {
        assertEquals(
            Benachrichtigungsaktion.Erledigt(7),
            Benachrichtigungsaktionen.aus(Benachrichtigungsaktionen.ACTION_ERLEDIGT, 7),
        )
        assertEquals(
            Benachrichtigungsaktion.Spaeter(7),
            Benachrichtigungsaktionen.aus(Benachrichtigungsaktionen.ACTION_SPAETER, 7),
        )
        assertEquals(
            Benachrichtigungsaktion.Ausgelassen(7),
            Benachrichtigungsaktionen.aus(Benachrichtigungsaktionen.ACTION_AUSGELASSEN, 7),
        )
    }

    @Test
    fun `unbekannte Aktion ergibt nichts`() {
        assertNull(Benachrichtigungsaktionen.aus("de.example.UNSINN", 7))
        assertNull(Benachrichtigungsaktionen.aus(null, 7))
    }

    @Test
    fun `fehlende Faelligkeit ergibt nichts`() {
        assertNull(Benachrichtigungsaktionen.aus(Benachrichtigungsaktionen.ACTION_ERLEDIGT, 0))
        assertNull(Benachrichtigungsaktionen.aus(Benachrichtigungsaktionen.ACTION_ERLEDIGT, -1))
    }
}
