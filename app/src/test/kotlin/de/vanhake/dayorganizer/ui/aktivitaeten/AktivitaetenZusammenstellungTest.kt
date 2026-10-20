package de.vanhake.dayorganizer.ui.aktivitaeten

import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import de.vanhake.dayorganizer.domain.model.Zeitplan
import de.vanhake.dayorganizer.domain.rechner.SerienRechner
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class AktivitaetenZusammenstellungTest {

    // Montag
    private val heute = LocalDate.of(2026, 3, 2)

    private fun aktivitaet(id: Long, name: String, zeitplan: Zeitplan, symbol: String = "pill") = Aktivitaet(
        id = id, name = name, farbe = 1, symbol = symbol, basisPunkte = 5,
        zeitplan = zeitplan, erstelltAm = LocalDate.of(2026, 1, 1),
    )

    private fun tag(aktivitaetId: Long, tag: LocalDate, status: FaelligkeitStatus) = Faelligkeit(
        aktivitaetId = aktivitaetId, periodeVon = tag, periodeBis = tag,
        geplantFuer = tag.atTime(8, 0), sollAnzahl = 1, status = status,
    )

    private fun woche(aktivitaetId: Long, montag: LocalDate, status: FaelligkeitStatus) = Faelligkeit(
        aktivitaetId = aktivitaetId, periodeVon = montag, periodeBis = montag.plusDays(6),
        geplantFuer = null, sollAnzahl = 3, status = status,
    )

    private val tabletten = aktivitaet(
        1, "Tabletten", Zeitplan.FesteZeiten(listOf(LocalTime.of(8, 0)), DayOfWeek.entries.toSet()),
    )
    private val sport = aktivitaet(2, "Sport", Zeitplan.Wochenziel(3), symbol = "fitness_center")

    @Test
    fun `eine Zeile traegt Name, Symbol, Plantext und Punkte`() {
        val zeile = AktivitaetenZusammenstellung.zeilen(listOf(tabletten), emptyMap(), heute, SerienRechner()).single()

        assertEquals(
            AktivitaetZeile(
                id = 1, name = "Tabletten", symbol = "pill", planText = "Täglich · 08:00",
                basisPunkte = 5, serie = 0, serieInWochen = false,
            ),
            zeile,
        )
    }

    @Test
    fun `die Serie zaehlt abgeschlossene Tage bis zum letzten Verpassen`() {
        val historie = listOf(
            tag(1, heute.minusDays(3), FaelligkeitStatus.VERPASST),
            tag(1, heute.minusDays(2), FaelligkeitStatus.ERLEDIGT),
            tag(1, heute.minusDays(1), FaelligkeitStatus.ERLEDIGT),
            tag(1, heute, FaelligkeitStatus.OFFEN),
        )

        val zeile = AktivitaetenZusammenstellung.zeilen(
            listOf(tabletten), mapOf(1L to historie), heute, SerienRechner(),
        ).single()

        assertEquals(2, zeile.serie)
    }

    @Test
    fun `ein Wochenziel zaehlt seine Serie in Wochen`() {
        val historie = listOf(
            woche(2, heute.minusWeeks(2), FaelligkeitStatus.ERLEDIGT),
            woche(2, heute.minusWeeks(1), FaelligkeitStatus.ERLEDIGT),
            woche(2, heute, FaelligkeitStatus.OFFEN),
        )

        val zeile = AktivitaetenZusammenstellung.zeilen(
            listOf(sport), mapOf(2L to historie), heute, SerienRechner(),
        ).single()

        assertEquals(2, zeile.serie)
        assertEquals(true, zeile.serieInWochen)
        assertEquals("3× pro Woche", zeile.planText)
    }

    @Test
    fun `die Reihenfolge der Eingabe bleibt erhalten`() {
        val zeilen = AktivitaetenZusammenstellung.zeilen(listOf(sport, tabletten), emptyMap(), heute, SerienRechner())

        assertEquals(listOf("Sport", "Tabletten"), zeilen.map { it.name })
    }

    @Test
    fun `eine Einmalaufgabe wird als solche markiert`() {
        val steuer = Aktivitaet(
            id = 9, name = "Steuererklaerung", farbe = 1, basisPunkte = 20,
            zeitplan = Zeitplan.Einmalig(LocalDate.of(2026, 5, 31)),
            erstelltAm = LocalDate.of(2026, 3, 1),
        )

        val zeile = AktivitaetenZusammenstellung.zeilen(
            listOf(steuer), emptyMap(), LocalDate.of(2026, 3, 2), SerienRechner(),
        ).single()

        assertEquals(true, zeile.einmalig)
        assertEquals("Einmalig · bis 31.05.2026", zeile.planText)
    }
}
