package de.vanhake.dayorganizer.domain.rechner

import de.vanhake.dayorganizer.domain.aktivitaet
import de.vanhake.dayorganizer.domain.model.Anstoss
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.Zeitplan
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class ZeitplanRechnerAnstossTest {

    private val rechner = ZeitplanRechner()

    @Test
    fun `feste Zeiten stossen genau zum Sollzeitpunkt an`() {
        val a = aktivitaet(
            zeitplan = Zeitplan.FesteZeiten(
                zeiten = listOf(LocalTime.of(8, 0)),
                wochentage = DayOfWeek.entries.toSet(),
            ),
        )
        val f = Faelligkeit(
            aktivitaetId = a.id,
            periodeVon = LocalDate.of(2026, 3, 2),
            periodeBis = LocalDate.of(2026, 3, 2),
            geplantFuer = LocalDateTime.of(2026, 3, 2, 8, 0),
            sollAnzahl = 1,
        )

        assertEquals(
            listOf(LocalDateTime.of(2026, 3, 2, 8, 0)),
            rechner.anstossZeitpunkteFuer(a, f),
        )
    }

    @Test
    fun `Tagesziel stoesst zu den konfigurierten Uhrzeiten des Tages an`() {
        val a = aktivitaet(
            zeitplan = Zeitplan.Tagesziel(
                anzahl = 3,
                wochentage = DayOfWeek.entries.toSet(),
                anstossZeiten = listOf(LocalTime.of(19, 0), LocalTime.of(12, 0)),
            ),
        )
        val f = Faelligkeit(
            aktivitaetId = a.id,
            periodeVon = LocalDate.of(2026, 3, 2),
            periodeBis = LocalDate.of(2026, 3, 2),
            geplantFuer = null,
            sollAnzahl = 3,
        )

        assertEquals(
            listOf(
                LocalDateTime.of(2026, 3, 2, 12, 0),
                LocalDateTime.of(2026, 3, 2, 19, 0),
            ),
            rechner.anstossZeitpunkteFuer(a, f),
        )
    }

    @Test
    fun `Wochenziel stoesst am konfigurierten Wochentag innerhalb der Periode an`() {
        val a = aktivitaet(
            zeitplan = Zeitplan.Wochenziel(
                anzahl = 3,
                anstoss = listOf(
                    Anstoss(DayOfWeek.THURSDAY, LocalTime.of(18, 0)),
                    Anstoss(DayOfWeek.TUESDAY, LocalTime.of(18, 0)),
                ),
            ),
        )
        val f = Faelligkeit(
            aktivitaetId = a.id,
            periodeVon = LocalDate.of(2026, 3, 2),
            periodeBis = LocalDate.of(2026, 3, 8),
            geplantFuer = null,
            sollAnzahl = 3,
        )

        assertEquals(
            listOf(
                LocalDateTime.of(2026, 3, 3, 18, 0),
                LocalDateTime.of(2026, 3, 5, 18, 0),
            ),
            rechner.anstossZeitpunkteFuer(a, f),
        )
    }

    @Test
    fun `ohne konfigurierte Anstoesse gibt es keine Erinnerung`() {
        val a = aktivitaet(
            zeitplan = Zeitplan.Tagesziel(anzahl = 2, wochentage = DayOfWeek.entries.toSet()),
        )
        val f = Faelligkeit(
            aktivitaetId = a.id,
            periodeVon = LocalDate.of(2026, 3, 2),
            periodeBis = LocalDate.of(2026, 3, 2),
            geplantFuer = null,
            sollAnzahl = 2,
        )

        assertEquals(emptyList(), rechner.anstossZeitpunkteFuer(a, f))
    }
}
