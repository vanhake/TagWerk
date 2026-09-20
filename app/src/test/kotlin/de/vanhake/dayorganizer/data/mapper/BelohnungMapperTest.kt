package de.vanhake.dayorganizer.data.mapper

import de.vanhake.dayorganizer.domain.model.Belohnung
import de.vanhake.dayorganizer.domain.model.Belohnungsbedingung
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class BelohnungMapperTest {

    private fun rundlauf(belohnung: Belohnung) {
        assertEquals(belohnung, belohnung.zuEntity().zuDomaene())
    }

    @Test
    fun `Belohnung ohne Bedingung uebersteht den Rundlauf`() {
        rundlauf(Belohnung(id = 1, name = "Kinoabend", beschreibung = "mit Popcorn", preisPunkte = 200))
    }

    @Test
    fun `Serienbedingung uebersteht den Rundlauf`() {
        rundlauf(
            Belohnung(
                id = 2, name = "Neues Buch", preisPunkte = 300,
                bedingung = Belohnungsbedingung.MindestSerie(aktivitaetId = 7, perioden = 14),
            ),
        )
    }

    @Test
    fun `Datumsbedingung uebersteht den Rundlauf`() {
        rundlauf(
            Belohnung(
                id = 3, name = "Wochenendausflug", preisPunkte = 800,
                bedingung = Belohnungsbedingung.FruehestensAb(LocalDate.of(2026, 4, 1)),
            ),
        )
    }

    @Test
    fun `archivierte Belohnung uebersteht den Rundlauf`() {
        rundlauf(Belohnung(id = 4, name = "Alt", preisPunkte = 50, archiviert = true))
    }
}
