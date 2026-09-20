package de.vanhake.dayorganizer.ui.belohnungen

import de.vanhake.dayorganizer.domain.model.Belohnungsbedingung
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BelohnungEntwurfTest {

    private val gueltig = BelohnungEntwurf(name = "Kinoabend", preisText = "200")

    @Test
    fun `ein vollstaendiger Entwurf hat keine Fehler`() {
        assertEquals(emptyList(), gueltig.pruefen())
    }

    @Test
    fun `leerer Name und nicht positiver Preis werden gemeldet`() {
        val fehler = gueltig.copy(name = " ", preisText = "0").pruefen()

        assertTrue(BelohnungFehler.NAME_LEER in fehler)
        assertTrue(BelohnungFehler.PREIS_NICHT_POSITIV in fehler)
    }

    @Test
    fun `eine Serienbedingung ohne Aktivitaet wird gemeldet`() {
        val fehler = gueltig.copy(
            bedingungTyp = BedingungTyp.MINDESTSERIE,
            bedingungAktivitaetId = null,
            bedingungPeriodenText = "14",
        ).pruefen()

        assertTrue(BelohnungFehler.SERIE_OHNE_AKTIVITAET in fehler)
    }

    @Test
    fun `eine Serienbedingung mit Periode null wird gemeldet`() {
        val fehler = gueltig.copy(
            bedingungTyp = BedingungTyp.MINDESTSERIE,
            bedingungAktivitaetId = 7,
            bedingungPeriodenText = "0",
        ).pruefen()

        assertTrue(BelohnungFehler.SERIE_NICHT_POSITIV in fehler)
    }

    @Test
    fun `der Entwurf wird in die richtige Bedingung umgesetzt`() {
        val mitSerie = gueltig.copy(
            bedingungTyp = BedingungTyp.MINDESTSERIE,
            bedingungAktivitaetId = 7,
            bedingungPeriodenText = "14",
        )

        assertEquals(
            Belohnungsbedingung.MindestSerie(7, 14),
            mitSerie.zuBelohnung().bedingung,
        )

        val mitDatum = gueltig.copy(
            bedingungTyp = BedingungTyp.FRUEHESTENS_AB,
            bedingungDatum = LocalDate.of(2026, 4, 1),
        )

        assertEquals(
            Belohnungsbedingung.FruehestensAb(LocalDate.of(2026, 4, 1)),
            mitDatum.zuBelohnung().bedingung,
        )

        assertEquals(null, gueltig.zuBelohnung().bedingung)
    }
}
