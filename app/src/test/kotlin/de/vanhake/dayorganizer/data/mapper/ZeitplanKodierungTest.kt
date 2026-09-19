package de.vanhake.dayorganizer.data.mapper

import de.vanhake.dayorganizer.domain.model.Anstoss
import de.vanhake.dayorganizer.domain.model.SerienSchwelle
import de.vanhake.dayorganizer.domain.model.Zeitplan
import java.time.DayOfWeek
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ZeitplanKodierungTest {

    private fun rundlauf(plan: Zeitplan) {
        assertEquals(plan, ZeitplanKodierung.dekodiere(ZeitplanKodierung.kodiere(plan)))
    }

    @Test
    fun `feste Zeiten ueberstehen den Rundlauf`() {
        rundlauf(
            Zeitplan.FesteZeiten(
                zeiten = listOf(LocalTime.of(8, 0), LocalTime.of(20, 30)),
                wochentage = setOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY),
            ),
        )
    }

    @Test
    fun `Intervall uebersteht den Rundlauf`() {
        rundlauf(
            Zeitplan.Intervall(
                alleNStunden = 3,
                von = LocalTime.of(8, 0),
                bis = LocalTime.of(22, 0),
                wochentage = DayOfWeek.entries.toSet(),
            ),
        )
    }

    @Test
    fun `Tagesziel mit Anstoessen uebersteht den Rundlauf`() {
        rundlauf(
            Zeitplan.Tagesziel(
                anzahl = 3,
                wochentage = setOf(DayOfWeek.TUESDAY),
                anstossZeiten = listOf(LocalTime.of(12, 0), LocalTime.of(19, 0)),
            ),
        )
    }

    @Test
    fun `Wochenziel mit Anstoessen uebersteht den Rundlauf`() {
        rundlauf(
            Zeitplan.Wochenziel(
                anzahl = 3,
                anstoss = listOf(
                    Anstoss(DayOfWeek.TUESDAY, LocalTime.of(18, 0)),
                    Anstoss(DayOfWeek.THURSDAY, LocalTime.of(18, 0)),
                ),
            ),
        )
    }

    @Test
    fun `leere Anstossliste bleibt leer`() {
        rundlauf(Zeitplan.Wochenziel(anzahl = 2))
        rundlauf(Zeitplan.Tagesziel(anzahl = 2, wochentage = setOf(DayOfWeek.FRIDAY)))
    }

    @Test
    fun `Serienschwellen ueberstehen den Rundlauf`() {
        val schwellen = listOf(SerienSchwelle(7, 10), SerienSchwelle(30, 50))

        assertEquals(
            schwellen,
            ZeitplanKodierung.dekodiereSchwellen(ZeitplanKodierung.kodiereSchwellen(schwellen)),
        )
        assertEquals(emptyList(), ZeitplanKodierung.dekodiereSchwellen(""))
    }

    @Test
    fun `leere Wochentage bei festen Zeiten ueberstehen den Rundlauf`() {
        rundlauf(
            Zeitplan.FesteZeiten(
                zeiten = listOf(LocalTime.of(8, 0)),
                wochentage = emptySet(),
            ),
        )
    }

    @Test
    fun `Uhrzeit mit Sekunden uebersteht den Rundlauf`() {
        rundlauf(
            Zeitplan.FesteZeiten(
                zeiten = listOf(LocalTime.of(8, 0, 30)),
                wochentage = setOf(DayOfWeek.MONDAY),
            ),
        )
    }

    @Test
    fun `unbekannter Typ scheitert mit sprechender Meldung`() {
        val fehler = assertFailsWith<IllegalStateException> {
            ZeitplanKodierung.dekodiere(ZeitplanFelder(typ = "UNSINN"))
        }
        assertTrue(fehler.message.orEmpty().contains("UNSINN"))
    }

    @Test
    fun `Intervall ohne Stundenwert scheitert`() {
        assertFailsWith<IllegalArgumentException> {
            ZeitplanKodierung.dekodiere(
                ZeitplanFelder(
                    typ = "INTERVALL",
                    intervallStunden = null,
                    fensterVon = "08:00",
                    fensterBis = "20:00",
                ),
            )
        }
    }

    @Test
    fun `Tagesziel ohne Anzahl scheitert`() {
        assertFailsWith<IllegalArgumentException> {
            ZeitplanKodierung.dekodiere(
                ZeitplanFelder(typ = "TAGESZIEL", zielAnzahl = null),
            )
        }
    }

    @Test
    fun `Anstoss ohne Trenner scheitert mit sprechender Meldung`() {
        val fehler = assertFailsWith<IllegalArgumentException> {
            ZeitplanKodierung.dekodiere(
                ZeitplanFelder(typ = "WOCHENZIEL", zielAnzahl = 3, anstoss = "MONDAY18:00"),
            )
        }
        assertTrue(fehler.message.orEmpty().contains("MONDAY18:00"))
    }

    @Test
    fun `Serienschwelle ohne Trenner scheitert mit sprechender Meldung`() {
        val fehler = assertFailsWith<IllegalArgumentException> {
            ZeitplanKodierung.dekodiereSchwellen("7-10")
        }
        assertTrue(fehler.message.orEmpty().contains("7-10"))
    }

    @Test
    fun `Serienschwelle mit ungueltiger Zahl scheitert mit sprechender Meldung`() {
        val fehler = assertFailsWith<IllegalArgumentException> {
            ZeitplanKodierung.dekodiereSchwellen("sieben:10")
        }
        assertTrue(fehler.message.orEmpty().contains("sieben"))
    }

    @Test
    fun `ungueltiger Wochentag in wochentage scheitert mit sprechender Meldung`() {
        val fehler = assertFailsWith<IllegalArgumentException> {
            ZeitplanKodierung.dekodiere(
                ZeitplanFelder(
                    typ = "FESTE_ZEITEN",
                    zeiten = "08:00",
                    wochentage = "MONTAG",
                ),
            )
        }
        assertTrue(fehler.message.orEmpty().contains("MONTAG"))
        assertTrue(fehler.message.orEmpty().contains("in \"wochentage\""))
    }

    @Test
    fun `ungueltiger Wochentag in Wochenziel-Anstoss scheitert mit sprechender Meldung`() {
        val fehler = assertFailsWith<IllegalArgumentException> {
            ZeitplanKodierung.dekodiere(
                ZeitplanFelder(
                    typ = "WOCHENZIEL",
                    zielAnzahl = 3,
                    anstoss = "MONTAG@18:00",
                ),
            )
        }
        assertTrue(fehler.message.orEmpty().contains("MONTAG"))
        assertTrue(fehler.message.orEmpty().contains("in \"Anstoss\""))
    }

    @Test
    fun `ungueltige Uhrzeit in zeiten scheitert mit sprechender Meldung`() {
        val fehler = assertFailsWith<IllegalArgumentException> {
            ZeitplanKodierung.dekodiere(
                ZeitplanFelder(
                    typ = "FESTE_ZEITEN",
                    zeiten = "25:99",
                    wochentage = "MONDAY",
                ),
            )
        }
        assertTrue(fehler.message.orEmpty().contains("25:99"))
    }

    @Test
    fun `ungueltige Uhrzeit in Intervall-fensterVon scheitert mit sprechender Meldung`() {
        val fehler = assertFailsWith<IllegalArgumentException> {
            ZeitplanKodierung.dekodiere(
                ZeitplanFelder(
                    typ = "INTERVALL",
                    intervallStunden = 3,
                    fensterVon = "25:99",
                    fensterBis = "20:00",
                ),
            )
        }
        assertTrue(fehler.message.orEmpty().contains("25:99"))
    }
}
