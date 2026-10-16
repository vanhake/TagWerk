package de.vanhake.dayorganizer.widget

import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domainuse.ErledigungsProtokoll
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals

class WidgetAbhakenTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val uhr = Clock.fixed(LocalDateTime.of(2026, 10, 17, 0, 10).atZone(zone).toInstant(), zone)

    private val gestern = LocalDate.of(2026, 10, 16)
    private val heute = LocalDate.of(2026, 10, 17)

    private fun faelligkeit(von: LocalDate, bis: LocalDate) = Faelligkeit(
        id = 7, aktivitaetId = 1, periodeVon = von, periodeBis = bis,
        geplantFuer = null, sollAnzahl = 1,
    )

    private val gebucht = ErledigungsProtokoll.Gebucht(
        faelligkeitVorher = faelligkeit(heute, heute),
        buchungen = emptyList(),
    )

    private class Aufrufe {
        var erledigt = 0
        val nachbereitet = mutableListOf<Long>()
        var angestossen = 0
    }

    private fun abhaken(
        f: Faelligkeit?,
        aufrufe: Aufrufe,
        erledigen: suspend (Long) -> ErledigungsProtokoll = { gebucht },
    ) = WidgetAbhaken(
        faelligkeitLesen = { f },
        erledigen = { aufrufe.erledigt++; erledigen(it) },
        nachbereiten = { aufrufe.nachbereitet += it },
        widgetAnstossen = { aufrufe.angestossen++ },
        uhr = uhr,
    )

    @Test
    fun `ein Eintrag von gestern wird nicht gebucht, das Widget zeichnet trotzdem neu`() = runTest {
        val aufrufe = Aufrufe()

        val ergebnis = abhaken(faelligkeit(gestern, gestern), aufrufe)(7)

        assertEquals(WidgetAbhakErgebnis.ANDERER_TAG, ergebnis)
        assertEquals(0, aufrufe.erledigt)
        assertEquals(1, aufrufe.angestossen)
    }

    @Test
    fun `ein heutiger Eintrag wird gebucht und nachbereitet`() = runTest {
        val aufrufe = Aufrufe()

        val ergebnis = abhaken(faelligkeit(heute, heute), aufrufe)(7)

        assertEquals(WidgetAbhakErgebnis.GEBUCHT, ergebnis)
        assertEquals(listOf(7L), aufrufe.nachbereitet)
        assertEquals(1, aufrufe.angestossen)
    }

    @Test
    fun `ein Fehler beim Buchen reisst die Aktion nicht mit`() = runTest {
        val aufrufe = Aufrufe()

        val ergebnis = abhaken(faelligkeit(heute, heute), aufrufe, erledigen = { throw IOException("kaputt") })(7)

        assertEquals(WidgetAbhakErgebnis.FEHLER, ergebnis)
        assertEquals(1, aufrufe.angestossen)
    }

    @Test
    fun `ein Wochenziel dieser Woche wird nach Mitternacht weiter gebucht`() = runTest {
        val aufrufe = Aufrufe()

        val ergebnis = abhaken(faelligkeit(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18)), aufrufe)(7)

        assertEquals(WidgetAbhakErgebnis.GEBUCHT, ergebnis)
    }
}
