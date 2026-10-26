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
import kotlin.test.assertNull

class FaelligkeitWiederOeffnenTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val tag = LocalDate.of(2026, 3, 2)
    private val erledigtAm = LocalDateTime.of(2026, 3, 2, 8, 10)

    private fun uhrUm(zeitpunkt: LocalDateTime) = Clock.fixed(zeitpunkt.atZone(zone).toInstant(), zone)

    private val erledigt = Faelligkeit(
        id = 10, aktivitaetId = 1, periodeVon = tag, periodeBis = tag,
        geplantFuer = tag.atTime(8, 0), sollAnzahl = 1,
        istAnzahl = 1, status = FaelligkeitStatus.ERLEDIGT, erledigtAm = erledigtAm, warPuenktlich = true,
    )

    private fun buchung(id: Long, art: BuchungsArt, betrag: Int, zeitpunkt: LocalDateTime = erledigtAm, faelligkeitId: Long? = 10) =
        Buchung(
            id = id, art = art, betrag = betrag, zeitpunkt = zeitpunkt,
            aktivitaetId = faelligkeitId?.let { 1L }, faelligkeitId = faelligkeitId,
        )

    private fun useCase(f: FakeFaelligkeitRepository, b: FakeBuchungRepository, jetzt: LocalDateTime) =
        FaelligkeitWiederOeffnen(f, b, FakeTransaktionen(), uhrUm(jetzt))

    @Test
    fun `eine erledigte Faelligkeit wird wieder offen und ihre Punkte gegengebucht`() = runTest {
        val f = FakeFaelligkeitRepository(listOf(erledigt))
        val b = FakeBuchungRepository(
            listOf(
                buchung(100, BuchungsArt.ERLEDIGUNG, 10),
                buchung(101, BuchungsArt.PUENKTLICHKEITSBONUS, 5),
                buchung(102, BuchungsArt.TAGESBONUS, 15, faelligkeitId = null),
                // Fremde Buchungen: eine andere Faelligkeit und ein Tagesbonus zu anderer Zeit.
                buchung(103, BuchungsArt.ERLEDIGUNG, 10, faelligkeitId = 11),
                buchung(104, BuchungsArt.TAGESBONUS, 15, zeitpunkt = erledigtAm.minusDays(1), faelligkeitId = null),
            ),
        )

        val vorher = useCase(f, b, erledigtAm.plusHours(3))(10)

        assertEquals(erledigt, vorher)
        val nachher = f.stand.value.single()
        assertEquals(FaelligkeitStatus.OFFEN, nachher.status)
        assertEquals(0, nachher.istAnzahl)
        assertNull(nachher.erledigtAm)
        assertEquals(false, nachher.warPuenktlich)
        val korrekturen = b.stand.value.filter { it.art == BuchungsArt.KORREKTUR }
        assertEquals(setOf(-10, -5, -15), korrekturen.map { it.betrag }.toSet())
        assertEquals(10 + 15, b.kontostand())
        // Die Tagesbonus-Korrektur behaelt die Form des Tagesbonus und gibt ihn frei.
        assertEquals(false, b.tagesbonusGebuchtAm(tag))
    }

    @Test
    fun `bei einem Zaehlziel wird nur der letzte Schritt zurueckgenommen`() = runTest {
        val ziel = erledigt.copy(geplantFuer = null, sollAnzahl = 3, istAnzahl = 3, warPuenktlich = false)
        val f = FakeFaelligkeitRepository(listOf(ziel))
        val b = FakeBuchungRepository(
            listOf(
                buchung(100, BuchungsArt.ERLEDIGUNG, 10, zeitpunkt = erledigtAm.minusHours(2)),
                buchung(101, BuchungsArt.ERLEDIGUNG, 10, zeitpunkt = erledigtAm.minusHours(1)),
                buchung(102, BuchungsArt.ERLEDIGUNG, 10),
            ),
        )

        useCase(f, b, erledigtAm.plusMinutes(5))(10)

        val nachher = f.stand.value.single()
        assertEquals(FaelligkeitStatus.OFFEN, nachher.status)
        assertEquals(2, nachher.istAnzahl)
        assertEquals(20, b.kontostand())
    }

    @Test
    fun `eine Tagesbonus-Korrektur an einem spaeteren Tag bleibt am Tag des Bonus`() = runTest {
        val f = FakeFaelligkeitRepository(listOf(erledigt))
        val b = FakeBuchungRepository(listOf(buchung(102, BuchungsArt.TAGESBONUS, 15, faelligkeitId = null)))

        useCase(f, b, erledigtAm.plusDays(1))(10)

        assertEquals(false, b.tagesbonusGebuchtAm(tag))
        // Ohne diese Regel stuende am Folgetag ein negativer Tagesbonus-Saldo.
        assertEquals(false, b.tagesbonusGebuchtAm(tag.plusDays(1)))
        assertEquals(0, b.stand.value.filter { it.zeitpunkt.toLocalDate() == tag.plusDays(1) }.sumOf { it.betrag })
    }

    @Test
    fun `eine ausgelassene Faelligkeit wird ohne Buchung wieder offen`() = runTest {
        val ausgelassen = erledigt.copy(istAnzahl = 0, status = FaelligkeitStatus.AUSGELASSEN, warPuenktlich = false)
        val f = FakeFaelligkeitRepository(listOf(ausgelassen))
        val b = FakeBuchungRepository()

        useCase(f, b, erledigtAm.plusHours(1))(10)

        assertEquals(ausgelassen.copy(status = FaelligkeitStatus.OFFEN, erledigtAm = null), f.stand.value.single())
        assertEquals(emptyList(), b.stand.value)
    }

    @Test
    fun `eine nachtraeglich abgehakte Einmalaufgabe steht wieder als verpasst offen`() = runTest {
        val nachgeholt = erledigt.copy(status = FaelligkeitStatus.VERPASST, warPuenktlich = false)
        val f = FakeFaelligkeitRepository(listOf(nachgeholt))
        val b = FakeBuchungRepository()

        useCase(f, b, erledigtAm.plusHours(1))(10)

        assertEquals(nachgeholt.copy(istAnzahl = 0, erledigtAm = null), f.stand.value.single())
        assertEquals(emptyList(), b.stand.value)
    }

    @Test
    fun `offene und vom Waechter verpasste Faelligkeiten bleiben unveraendert`() = runTest {
        val offen = erledigt.copy(id = 20, istAnzahl = 0, status = FaelligkeitStatus.OFFEN, erledigtAm = null)
        val verpasst = offen.copy(id = 21, status = FaelligkeitStatus.VERPASST)
        val f = FakeFaelligkeitRepository(listOf(offen, verpasst))
        val b = FakeBuchungRepository()
        val wiederOeffnen = useCase(f, b, erledigtAm)

        assertNull(wiederOeffnen(20))
        assertNull(wiederOeffnen(21))
        assertNull(wiederOeffnen(99))
        assertEquals(listOf(offen, verpasst), f.stand.value)
    }
}
