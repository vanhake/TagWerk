package de.vanhake.dayorganizer.ui.belohnungen

import de.vanhake.dayorganizer.domain.model.Belohnung
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
    fun `eine Datumsbedingung ohne lesbares Datum wird gemeldet`() {
        val leer = gueltig.copy(
            bedingungTyp = BedingungTyp.FRUEHESTENS_AB,
            bedingungDatumText = "",
        ).pruefen()
        assertTrue(BelohnungFehler.DATUM_FEHLT in leer)

        val halbGetippt = gueltig.copy(
            bedingungTyp = BedingungTyp.FRUEHESTENS_AB,
            bedingungDatumText = "2026-0",
        ).pruefen()
        assertTrue(BelohnungFehler.DATUM_FEHLT in halbGetippt)
    }

    @Test
    fun `eine Datumsbedingung mit lesbarem Datum wird nicht gemeldet`() {
        val fehler = gueltig.copy(
            bedingungTyp = BedingungTyp.FRUEHESTENS_AB,
            bedingungDatumText = "2026-04-01",
        ).pruefen()

        assertEquals(emptyList(), fehler)
    }

    @Test
    fun `der Entwurf wird in die richtige Bedingung umgesetzt`() {
        val basis = gueltig.copy(id = 3, beschreibung = "Ein Abend nur fuer mich")

        val mitSerie = basis.copy(
            bedingungTyp = BedingungTyp.MINDESTSERIE,
            bedingungAktivitaetId = 7,
            bedingungPeriodenText = "14",
        )
        val belohnungMitSerie = mitSerie.zuBelohnung()
        assertEquals(3, belohnungMitSerie.id)
        assertEquals("Kinoabend", belohnungMitSerie.name)
        assertEquals("Ein Abend nur fuer mich", belohnungMitSerie.beschreibung)
        assertEquals(200, belohnungMitSerie.preisPunkte)
        assertEquals(Belohnungsbedingung.MindestSerie(7, 14), belohnungMitSerie.bedingung)

        val mitDatum = basis.copy(
            bedingungTyp = BedingungTyp.FRUEHESTENS_AB,
            bedingungDatumText = "2026-04-01",
        )
        val belohnungMitDatum = mitDatum.zuBelohnung()
        assertEquals(3, belohnungMitDatum.id)
        assertEquals("Kinoabend", belohnungMitDatum.name)
        assertEquals("Ein Abend nur fuer mich", belohnungMitDatum.beschreibung)
        assertEquals(200, belohnungMitDatum.preisPunkte)
        assertEquals(
            Belohnungsbedingung.FruehestensAb(LocalDate.of(2026, 4, 1)),
            belohnungMitDatum.bedingung,
        )

        val ohneBedingung = basis.zuBelohnung()
        assertEquals(3, ohneBedingung.id)
        assertEquals("Kinoabend", ohneBedingung.name)
        assertEquals("Ein Abend nur fuer mich", ohneBedingung.beschreibung)
        assertEquals(200, ohneBedingung.preisPunkte)
        assertEquals(null, ohneBedingung.bedingung)
    }

    @Test
    fun `der Rundlauf ueber aus und zuBelohnung liefert die urspruengliche Belohnung`() {
        val ohneBedingung = Belohnung(id = 5, name = "Kinoabend", beschreibung = "Popcorn", preisPunkte = 200)
        assertEquals(ohneBedingung, BelohnungEntwurf.aus(ohneBedingung).zuBelohnung())

        val mitSerie = ohneBedingung.copy(
            bedingung = Belohnungsbedingung.MindestSerie(aktivitaetId = 7, perioden = 14),
        )
        assertEquals(mitSerie, BelohnungEntwurf.aus(mitSerie).zuBelohnung())

        val mitDatum = ohneBedingung.copy(
            bedingung = Belohnungsbedingung.FruehestensAb(LocalDate.of(2026, 4, 1)),
        )
        assertEquals(mitDatum, BelohnungEntwurf.aus(mitDatum).zuBelohnung())
    }

    @Test
    fun `eine archivierte Belohnung bleibt nach dem Rundlauf archiviert`() {
        val archiviert = Belohnung(
            id = 5,
            name = "Kinoabend",
            beschreibung = "Popcorn",
            preisPunkte = 200,
            archiviert = true,
        )

        val ergebnis = BelohnungEntwurf.aus(archiviert).zuBelohnung()

        assertTrue(ergebnis.archiviert)
        assertEquals(archiviert, ergebnis)
    }
}
