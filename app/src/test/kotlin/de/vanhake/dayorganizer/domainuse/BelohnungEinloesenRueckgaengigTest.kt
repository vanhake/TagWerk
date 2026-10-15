package de.vanhake.dayorganizer.domainuse

import de.vanhake.dayorganizer.FakeBelohnungRepository
import de.vanhake.dayorganizer.FakeBuchungRepository
import de.vanhake.dayorganizer.FakeFaelligkeitRepository
import de.vanhake.dayorganizer.FakeTransaktionen
import de.vanhake.dayorganizer.domain.model.Belohnung
import de.vanhake.dayorganizer.domain.model.Buchung
import de.vanhake.dayorganizer.domain.model.BuchungsArt
import de.vanhake.dayorganizer.domain.rechner.BelohnungsPruefer
import de.vanhake.dayorganizer.domain.rechner.SerienRechner
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class BelohnungEinloesenRueckgaengigTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val jetzt = LocalDateTime.of(2026, 3, 2, 19, 0)
    private val uhr = Clock.fixed(jetzt.atZone(zone).toInstant(), zone)

    private val belohnungen = FakeBelohnungRepository(listOf(Belohnung(1, "Kinoabend", preisPunkte = 200)))
    private val buchungen = FakeBuchungRepository(
        listOf(Buchung(art = BuchungsArt.ERLEDIGUNG, betrag = 250, zeitpunkt = jetzt.minusDays(1))),
    )
    private val transaktionen = FakeTransaktionen()
    private val einloesen = BelohnungEinloesen(
        belohnungen, buchungen, FakeFaelligkeitRepository(),
        BelohnungsPruefer(), SerienRechner(), transaktionen, uhr,
    )
    private val rueckgaengig = BelohnungEinloesenRueckgaengig(belohnungen, buchungen, transaktionen, uhr)

    private suspend fun eingeloest() = (einloesen(1) as EinloeseErgebnis.Erfolg).einloesung

    @Test
    fun `Rueckgaengig stellt den Kontostand per Gegenbuchung wieder her`() = runTest {
        val einloesung = eingeloest()

        assertEquals(true, rueckgaengig(einloesung))

        assertEquals(250, buchungen.kontostand())
        val korrektur = buchungen.stand.value.last()
        assertEquals(BuchungsArt.KORREKTUR, korrektur.art)
        assertEquals(200, korrektur.betrag)
        assertEquals(1L, korrektur.belohnungId)
        assertEquals(jetzt, korrektur.zeitpunkt)
        // die Einloesungs-Id macht zwei Einloesungen derselben Belohnung im Journal unterscheidbar
        assertEquals("Rueckgaengig: EINLOESUNG #${einloesung.id}", korrektur.notiz)
    }

    @Test
    fun `die Einloesung bleibt stehen und wird nur storniert`() = runTest {
        val einloesung = eingeloest()

        rueckgaengig(einloesung)

        assertEquals(jetzt, belohnungen.einloesungen.value.single().storniertAm)
    }

    @Test
    fun `ein zweites Rueckgaengig bucht nichts mehr`() = runTest {
        val einloesung = eingeloest()
        rueckgaengig(einloesung)
        val buchungenNachErstem = buchungen.stand.value.size

        assertEquals(false, rueckgaengig(einloesung))

        assertEquals(buchungenNachErstem, buchungen.stand.value.size)
        assertEquals(250, buchungen.kontostand())
    }

    @Test
    fun `Rueckgaengig laeuft in einer Transaktion`() = runTest {
        val einloesung = eingeloest()
        val vorher = transaktionen.aufrufe

        rueckgaengig(einloesung)

        assertEquals(vorher + 1, transaktionen.aufrufe)
    }
}
