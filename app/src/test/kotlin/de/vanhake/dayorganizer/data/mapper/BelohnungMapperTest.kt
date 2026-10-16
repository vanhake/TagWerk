package de.vanhake.dayorganizer.data.mapper

import de.vanhake.dayorganizer.domain.model.Belohnung
import de.vanhake.dayorganizer.domain.model.Belohnungsbedingung
import de.vanhake.dayorganizer.data.db.BelohnungEntity
import de.vanhake.dayorganizer.domain.model.Einloesung
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BelohnungMapperTest {

    private fun rundlauf(belohnung: Belohnung) {
        assertEquals(belohnung, belohnung.zuEntity().zuDomaene())
    }

    @Test
    fun `Belohnung ohne Bedingung uebersteht den Rundlauf`() {
        rundlauf(Belohnung(id = 1, name = "Kinoabend", beschreibung = "mit Popcorn", preisPunkte = 200))
    }

    @Test
    fun `Belohnung ohne Bedingung speichert bedingungTyp als null`() {
        val entity = Belohnung(id = 1, name = "Kinoabend", beschreibung = "mit Popcorn", preisPunkte = 200)
            .zuEntity()

        assertNull(entity.bedingungTyp)
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
    fun `Serienbedingung speichert bedingungTyp als MINDESTSERIE`() {
        val entity = Belohnung(
            id = 2, name = "Neues Buch", preisPunkte = 300,
            bedingung = Belohnungsbedingung.MindestSerie(aktivitaetId = 7, perioden = 14),
        ).zuEntity()

        assertEquals("MINDESTSERIE", entity.bedingungTyp)
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
    fun `Datumsbedingung speichert bedingungTyp als FRUEHESTENS_AB`() {
        val entity = Belohnung(
            id = 3, name = "Wochenendausflug", preisPunkte = 800,
            bedingung = Belohnungsbedingung.FruehestensAb(LocalDate.of(2026, 4, 1)),
        ).zuEntity()

        assertEquals("FRUEHESTENS_AB", entity.bedingungTyp)
    }

    @Test
    fun `archivierte Belohnung uebersteht den Rundlauf`() {
        rundlauf(Belohnung(id = 4, name = "Alt", preisPunkte = 50, archiviert = true))
    }

    @Test
    fun `Symbol und Stornodatum ueberstehen den Rundlauf durch die Entity`() {
        val belohnung = Belohnung(id = 4, name = "Kino", preisPunkte = 300, symbol = "movie")
        val einloesung = Einloesung(
            id = 2, belohnungId = 4, name = "Kino", preisPunkte = 300,
            zeitpunkt = LocalDateTime.of(2026, 3, 2, 19, 0),
            storniertAm = LocalDateTime.of(2026, 3, 2, 19, 1),
        )

        assertEquals(belohnung, belohnung.zuEntity().zuDomaene())
        assertEquals(einloesung, einloesung.zuEntity().zuDomaene())
    }

    private fun entity(typ: String?, aktivitaetId: Long? = null) = BelohnungEntity(
        id = 1, name = "Kaputt", beschreibung = "", preisPunkte = 10,
        bedingungTyp = typ, bedingungAktivitaetId = aktivitaetId,
        bedingungPerioden = null, bedingungDatum = null, archiviert = false,
    )

    @Test
    fun `eine unbekannte Bedingung ergibt null statt einer Ausnahme`() {
        assertNull(entity("ZUKUNFT").zuDomaeneOderNull())
    }

    @Test
    fun `eine Mindestserie ohne Aktivitaet ergibt null`() {
        assertNull(entity("MINDESTSERIE", aktivitaetId = null).zuDomaeneOderNull())
    }
}
