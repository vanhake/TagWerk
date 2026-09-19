package de.vanhake.dayorganizer.data.mapper

import de.vanhake.dayorganizer.domain.model.Anstoss
import de.vanhake.dayorganizer.domain.model.SerienSchwelle
import de.vanhake.dayorganizer.domain.model.Zeitplan
import java.time.DayOfWeek
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
