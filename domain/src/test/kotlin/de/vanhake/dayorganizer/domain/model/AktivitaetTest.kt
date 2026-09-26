package de.vanhake.dayorganizer.domain.model

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class AktivitaetTest {
    @Test
    fun `eine neue Aktivitaet bekommt ein Standardsymbol`() {
        val aktivitaet = Aktivitaet(
            name = "Test", farbe = 0, basisPunkte = 1,
            zeitplan = Zeitplan.Wochenziel(1),
            erstelltAm = LocalDate.of(2026, 1, 1),
        )

        assertEquals("self_improvement", aktivitaet.icon)
    }
}
