package de.vanhake.dayorganizer.domainuse

import de.vanhake.dayorganizer.FakeBuchungRepository
import de.vanhake.dayorganizer.FakeFaelligkeitRepository
import de.vanhake.dayorganizer.FakeTransaktionen
import de.vanhake.dayorganizer.domain.model.Buchung
import de.vanhake.dayorganizer.domain.model.BuchungsArt
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class FaelligkeitRueckgaengigTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private fun uhrUm(stunde: Int, minute: Int) = Clock.fixed(
        LocalDateTime.of(2026, 3, 2, stunde, minute).atZone(zone).toInstant(), zone,
    )

    private val vorher = Faelligkeit(
        id = 10, aktivitaetId = 1,
        periodeVon = LocalDate.of(2026, 3, 2), periodeBis = LocalDate.of(2026, 3, 2),
        geplantFuer = LocalDateTime.of(2026, 3, 2, 8, 0), sollAnzahl = 1,
    )
    private val nachher = vorher.copy(
        istAnzahl = 1, status = FaelligkeitStatus.ERLEDIGT,
        erledigtAm = LocalDateTime.of(2026, 3, 2, 8, 10), warPuenktlich = true,
    )
    private val gebuchteZeilen = listOf(
        Buchung(
            id = 100, art = BuchungsArt.ERLEDIGUNG, betrag = 10,
            zeitpunkt = LocalDateTime.of(2026, 3, 2, 8, 10), aktivitaetId = 1, faelligkeitId = 10,
        ),
        Buchung(
            id = 101, art = BuchungsArt.PUENKTLICHKEITSBONUS, betrag = 5,
            zeitpunkt = LocalDateTime.of(2026, 3, 2, 8, 10), aktivitaetId = 1, faelligkeitId = 10,
        ),
    )

    @Test
    fun `Erledigen-Rueckgaengig stellt die Faelligkeit wieder her und bucht Gegenbuchungen`() = runTest {
        val f = FakeFaelligkeitRepository(listOf(nachher))
        val b = FakeBuchungRepository()
        val rueckgaengig = FaelligkeitErledigenRueckgaengig(f, b, FakeTransaktionen(), uhrUm(8, 12))

        rueckgaengig(ErledigungsProtokoll.Gebucht(faelligkeitVorher = vorher, buchungen = gebuchteZeilen))

        assertEquals(vorher, f.stand.value.single())
        // FakeBuchungRepository startet leer -- hier stehen ausschliesslich die zwei neuen
        // Korrekturbuchungen, die Original-Buchungen aus gebuchteZeilen waren nie in b enthalten
        // (sie kamen im echten Ablauf aus einem frueheren FaelligkeitErledigen-Aufruf).
        assertEquals(2, b.stand.value.size)
        // Nur die zwei Korrekturzeilen liegen hier in b (siehe Kommentar oben) -- ihre
        // Summe ist daher -15, nicht 0. Die Null-Summen-Eigenschaft der echten Umkehrung
        // bezieht sich auf original (+10, +5) und Korrektur (-10, -5) zusammen; das wird
        // unten ueber die exakten negierten Betraege (setOf(-10, -5)) verifiziert.
        assertEquals(-15, b.stand.value.sumOf { it.betrag })
        assertEquals(2, b.stand.value.count { it.art == BuchungsArt.KORREKTUR })
        assertEquals(setOf(-10, -5), b.stand.value.map { it.betrag }.toSet())
    }

    @Test
    fun `Auslassen-Rueckgaengig stellt die Faelligkeit wieder her`() = runTest {
        val f = FakeFaelligkeitRepository(listOf(vorher.copy(status = FaelligkeitStatus.AUSGELASSEN)))
        val rueckgaengig = FaelligkeitAuslassenRueckgaengig(f)

        rueckgaengig(vorher)

        assertEquals(vorher, f.stand.value.single())
    }
}
