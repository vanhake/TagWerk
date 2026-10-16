package de.vanhake.dayorganizer.ui.belohnungen

import de.vanhake.dayorganizer.domain.model.Einloesung
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class BelohnungenZustandTest {

    private fun einloesung(id: Long, storniertAm: LocalDateTime? = null) = Einloesung(
        id = id, belohnungId = 1, name = "Kino $id", preisPunkte = 100,
        zeitpunkt = LocalDateTime.of(2026, 3, id.toInt(), 19, 0), storniertAm = storniertAm,
    )

    @Test
    fun `stornierte Einloesungen erscheinen nicht in der sichtbaren Historie`() {
        val liste = listOf(einloesung(3), einloesung(2, storniertAm = LocalDateTime.of(2026, 3, 2, 19, 1)), einloesung(1))

        assertEquals(listOf(3L, 1L), liste.sichtbare().map { it.id })
    }
}
