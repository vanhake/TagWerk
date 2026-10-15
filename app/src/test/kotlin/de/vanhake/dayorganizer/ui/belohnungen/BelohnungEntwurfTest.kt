package de.vanhake.dayorganizer.ui.belohnungen

import de.vanhake.dayorganizer.domain.model.Belohnung
import de.vanhake.dayorganizer.domain.model.Belohnungsbedingung
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BelohnungEntwurfTest {

    private val gueltig = BelohnungEntwurf(name = "Kinoabend", preisPunkte = 200)

    @Test
    fun `ein vollstaendiger Entwurf hat keine Fehler`() {
        assertEquals(emptyList(), gueltig.pruefen())
    }

    @Test
    fun `ein neuer Entwurf ist bis auf den Namen gueltig und hat das Standardsymbol`() {
        val neu = BelohnungEntwurf()

        assertEquals(listOf(BelohnungFehler.NAME_LEER), neu.pruefen())
        assertEquals("redeem", neu.symbol)
        assertEquals(100, neu.preisPunkte)
    }

    @Test
    fun `leerer Name und nicht positiver Preis werden gemeldet`() {
        val fehler = gueltig.copy(name = " ", preisPunkte = 0).pruefen()

        assertTrue(BelohnungFehler.NAME_LEER in fehler)
        assertTrue(BelohnungFehler.PREIS_NICHT_POSITIV in fehler)
    }

    @Test
    fun `eine Serienbedingung ohne Aktivitaet wird gemeldet`() {
        val fehler = gueltig.copy(bedingungTyp = BedingungTyp.MINDESTSERIE, bedingungAktivitaetId = null).pruefen()

        assertTrue(BelohnungFehler.SERIE_OHNE_AKTIVITAET in fehler)
    }

    @Test
    fun `eine Serienbedingung mit Periode null wird gemeldet`() {
        val fehler = gueltig.copy(
            bedingungTyp = BedingungTyp.MINDESTSERIE,
            bedingungAktivitaetId = 7,
            bedingungPerioden = 0,
        ).pruefen()

        assertTrue(BelohnungFehler.SERIE_NICHT_POSITIV in fehler)
    }

    @Test
    fun `eine Datumsbedingung ohne Datum wird gemeldet, mit Datum nicht`() {
        val ohne = gueltig.copy(bedingungTyp = BedingungTyp.FRUEHESTENS_AB, bedingungDatum = null)
        assertEquals(listOf(BelohnungFehler.DATUM_FEHLT), ohne.pruefen())

        val mit = ohne.copy(bedingungDatum = LocalDate.of(2026, 4, 1))
        assertEquals(emptyList(), mit.pruefen())
    }

    @Test
    fun `der Preis aendert sich in Zehnerschritten und bleibt mindestens 10`() {
        assertEquals(210, gueltig.preisAendern(+1).preisPunkte)
        assertEquals(190, gueltig.preisAendern(-1).preisPunkte)
        assertEquals(10, gueltig.copy(preisPunkte = 10).preisAendern(-1).preisPunkte)
    }

    @Test
    fun `ein geladener Altpreis unter 10 faellt beim Verringern auf 10 und waechst beim Erhoehen um 10`() {
        assertEquals(10, gueltig.copy(preisPunkte = 5).preisAendern(-1).preisPunkte)
        assertEquals(15, gueltig.copy(preisPunkte = 5).preisAendern(+1).preisPunkte)
    }

    @Test
    fun `die Perioden bleiben mindestens 1`() {
        assertEquals(1, gueltig.copy(bedingungPerioden = 1).periodenAendern(-1).bedingungPerioden)
        assertEquals(8, gueltig.copy(bedingungPerioden = 7).periodenAendern(+1).bedingungPerioden)
    }

    @Test
    fun `nur die gewaehlte Bedingung wird gespeichert, die anderen Felder bleiben im Entwurf`() {
        val entwurf = gueltig.copy(
            bedingungTyp = BedingungTyp.KEINE,
            bedingungAktivitaetId = 7,
            bedingungPerioden = 14,
            bedingungDatum = LocalDate.of(2026, 4, 1),
        )

        assertEquals(null, entwurf.zuBelohnung().bedingung)
        assertEquals(
            Belohnungsbedingung.MindestSerie(7, 14),
            entwurf.copy(bedingungTyp = BedingungTyp.MINDESTSERIE).zuBelohnung().bedingung,
        )
        assertEquals(
            Belohnungsbedingung.FruehestensAb(LocalDate.of(2026, 4, 1)),
            entwurf.copy(bedingungTyp = BedingungTyp.FRUEHESTENS_AB).zuBelohnung().bedingung,
        )
    }

    @Test
    fun `der Rundlauf ueber aus und zuBelohnung liefert die urspruengliche Belohnung`() {
        val ohneBedingung = Belohnung(
            id = 5, name = "Kinoabend", beschreibung = "Popcorn", preisPunkte = 200, symbol = "movie",
        )
        assertEquals(ohneBedingung, BelohnungEntwurf.aus(ohneBedingung).zuBelohnung())

        val mitSerie = ohneBedingung.copy(bedingung = Belohnungsbedingung.MindestSerie(aktivitaetId = 7, perioden = 14))
        assertEquals(mitSerie, BelohnungEntwurf.aus(mitSerie).zuBelohnung())

        val mitDatum = ohneBedingung.copy(bedingung = Belohnungsbedingung.FruehestensAb(LocalDate.of(2026, 4, 1)))
        assertEquals(mitDatum, BelohnungEntwurf.aus(mitDatum).zuBelohnung())
    }

    @Test
    fun `eine archivierte Belohnung bleibt nach dem Rundlauf archiviert`() {
        val archiviert = Belohnung(id = 5, name = "Kinoabend", beschreibung = "Popcorn", preisPunkte = 200, archiviert = true)

        val ergebnis = BelohnungEntwurf.aus(archiviert).zuBelohnung()

        assertTrue(ergebnis.archiviert)
        assertEquals(archiviert, ergebnis)
    }
}
