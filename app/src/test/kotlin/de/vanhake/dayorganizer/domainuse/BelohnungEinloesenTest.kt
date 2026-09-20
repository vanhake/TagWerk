package de.vanhake.dayorganizer.domainuse

import de.vanhake.dayorganizer.FakeBelohnungRepository
import de.vanhake.dayorganizer.FakeBuchungRepository
import de.vanhake.dayorganizer.FakeFaelligkeitRepository
import de.vanhake.dayorganizer.FakeTransaktionen
import de.vanhake.dayorganizer.data.repository.BuchungRepository
import de.vanhake.dayorganizer.domain.model.Belohnung
import de.vanhake.dayorganizer.domain.model.Belohnungsbedingung
import de.vanhake.dayorganizer.domain.model.Buchung
import de.vanhake.dayorganizer.domain.model.BuchungsArt
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import de.vanhake.dayorganizer.domain.rechner.BelohnungsPruefer
import de.vanhake.dayorganizer.domain.rechner.Hindernis
import de.vanhake.dayorganizer.domain.rechner.SerienRechner
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Spion um den entscheidenden Lesezugriff: schlaegt sofort fehl, wenn der Kontostand
 * ausserhalb der Transaktion gelesen wird. Ohne diese Absicherung lesen zwei gleichzeitige
 * Aufrufer denselben Kontostand, bestehen beide die Pruefung und ueberziehen das Konto. */
private class KontostandSpion(
    private val delegate: BuchungRepository,
    private val transaktionen: FakeTransaktionen,
) : BuchungRepository by delegate {
    override suspend fun kontostand(): Int {
        assertTrue(transaktionen.aktiv, "kontostand() wurde ausserhalb der Transaktion gelesen")
        return delegate.kontostand()
    }
}

class BelohnungEinloesenTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val uhr = Clock.fixed(
        LocalDateTime.of(2026, 3, 2, 19, 0).atZone(zone).toInstant(),
        zone,
    )
    private val erwarteterZeitpunkt = LocalDateTime.of(2026, 3, 2, 19, 0)
    private val kinoabend = Belohnung(id = 1, name = "Kinoabend", preisPunkte = 200)

    private fun guthaben(punkte: Int) = FakeBuchungRepository(
        listOf(
            Buchung(
                art = BuchungsArt.ERLEDIGUNG, betrag = punkte,
                zeitpunkt = LocalDateTime.of(2026, 3, 1, 8, 0),
            ),
        ),
    )

    private fun useCase(
        belohnungen: FakeBelohnungRepository,
        buchungen: BuchungRepository,
        faelligkeiten: FakeFaelligkeitRepository = FakeFaelligkeitRepository(),
        transaktionen: FakeTransaktionen = FakeTransaktionen(),
    ) = BelohnungEinloesen(
        belohnungen = belohnungen,
        buchungen = buchungen,
        faelligkeiten = faelligkeiten,
        pruefer = BelohnungsPruefer(),
        serienRechner = SerienRechner(),
        transaktionen = transaktionen,
        uhr = uhr,
    )

    @Test
    fun `mit genug Punkten wird abgebucht und die Einloesung angelegt`() = runTest {
        val belohnungen = FakeBelohnungRepository(listOf(kinoabend))
        val buchungen = guthaben(250)

        val ergebnis = useCase(belohnungen, buchungen)(1)

        assertTrue(ergebnis is EinloeseErgebnis.Erfolg)
        assertEquals(50, buchungen.kontostand())
        assertEquals(1, belohnungen.einloesungen.value.size)
        assertEquals("Kinoabend", belohnungen.einloesungen.value.single().name)
        val buchung = buchungen.stand.value.single { it.art == BuchungsArt.EINLOESUNG }
        assertEquals(-200, buchung.betrag)
        assertEquals(erwarteterZeitpunkt, buchung.zeitpunkt)
        assertEquals(erwarteterZeitpunkt, belohnungen.einloesungen.value.single().zeitpunkt)
    }

    @Test
    fun `ohne genug Punkte passiert nichts`() = runTest {
        val belohnungen = FakeBelohnungRepository(listOf(kinoabend))
        val buchungen = guthaben(100)

        val ergebnis = useCase(belohnungen, buchungen)(1)

        assertTrue(ergebnis is EinloeseErgebnis.Abgelehnt)
        assertEquals(Hindernis.ZU_WENIG_PUNKTE, ergebnis.einloesbarkeit.hindernis)
        assertEquals(100, buchungen.kontostand())
        assertEquals(0, belohnungen.einloesungen.value.size)
    }

    @Test
    fun `eine unerfuellte Serienbedingung blockiert trotz Guthaben`() = runTest {
        val mitSerie = kinoabend.copy(
            bedingung = Belohnungsbedingung.MindestSerie(aktivitaetId = 7, perioden = 14),
        )
        val belohnungen = FakeBelohnungRepository(listOf(mitSerie))
        val buchungen = guthaben(500)

        val ergebnis = useCase(belohnungen, buchungen)(1)

        assertTrue(ergebnis is EinloeseErgebnis.Abgelehnt)
        assertEquals(Hindernis.SERIE_ZU_KURZ, ergebnis.einloesbarkeit.hindernis)
        assertEquals(500, buchungen.kontostand())
    }

    @Test
    fun `eine erfuellte Serienbedingung laesst einloesen`() = runTest {
        val mitSerie = kinoabend.copy(
            bedingung = Belohnungsbedingung.MindestSerie(aktivitaetId = 7, perioden = 2),
        )
        val historie = (0L..2L).map { versatz ->
            Faelligkeit(
                id = versatz + 1, aktivitaetId = 7,
                periodeVon = LocalDate.of(2026, 2, 28).plusDays(versatz),
                periodeBis = LocalDate.of(2026, 2, 28).plusDays(versatz),
                geplantFuer = null, sollAnzahl = 1, istAnzahl = 1,
                status = FaelligkeitStatus.ERLEDIGT,
            )
        }
        val belohnungen = FakeBelohnungRepository(listOf(mitSerie))
        val buchungen = guthaben(500)

        val ergebnis = useCase(belohnungen, buchungen, FakeFaelligkeitRepository(historie))(1)

        assertTrue(ergebnis is EinloeseErgebnis.Erfolg)
        assertEquals(300, buchungen.kontostand())
    }

    @Test
    fun `eine unbekannte Belohnung wird gemeldet`() = runTest {
        val ergebnis = useCase(FakeBelohnungRepository(), guthaben(500))(999)

        assertEquals(EinloeseErgebnis.Unbekannt, ergebnis)
    }

    @Test
    fun `Pruefung, Buchung und Einloesung liegen in einer Transaktion`() = runTest {
        val transaktionen = FakeTransaktionen()
        val belohnungen = FakeBelohnungRepository(listOf(kinoabend))
        val buchungen = KontostandSpion(guthaben(250), transaktionen)

        val ergebnis = useCase(belohnungen, buchungen, transaktionen = transaktionen)(1)

        // Der Spion wirft bereits waehrend des Aufrufs, falls kontostand() ausserhalb der
        // Transaktion gelesen wurde -- diese Zusicherungen bestaetigen nur den Rahmen.
        assertTrue(ergebnis is EinloeseErgebnis.Erfolg)
        assertEquals(1, transaktionen.aufrufe)
    }
}
