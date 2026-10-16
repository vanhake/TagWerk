package de.vanhake.dayorganizer.domainuse

import de.vanhake.dayorganizer.FakeAktivitaetRepository
import de.vanhake.dayorganizer.FakeBuchungRepository
import de.vanhake.dayorganizer.FakeFaelligkeitRepository
import de.vanhake.dayorganizer.FakeTransaktionen
import de.vanhake.dayorganizer.data.Einstellungen
import de.vanhake.dayorganizer.data.repository.FaelligkeitRepository
import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.BuchungsArt
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import de.vanhake.dayorganizer.domain.model.Zeitplan
import de.vanhake.dayorganizer.domain.rechner.PunkteRechner
import de.vanhake.dayorganizer.domain.rechner.SerienRechner
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Spion um den entscheidenden Lesezugriff: schlaegt sofort fehl, wenn der Status ausserhalb
 * der Transaktion gelesen wird. Ohne diese Absicherung lesen zwei gleichzeitige Aufrufer
 * denselben Status, bestehen beide die Pruefung und buchen doppelt. */
private class StatusSpion(
    private val delegate: FaelligkeitRepository,
    private val transaktionen: FakeTransaktionen,
) : FaelligkeitRepository by delegate {
    override suspend fun nachId(id: Long): Faelligkeit? {
        assertTrue(transaktionen.aktiv, "nachId() wurde ausserhalb der Transaktion gelesen")
        return delegate.nachId(id)
    }
}

class FaelligkeitErledigenTest {

    private val zone = ZoneId.of("Europe/Berlin")

    private fun uhrUm(stunde: Int, minute: Int) = Clock.fixed(
        LocalDateTime.of(2026, 3, 2, stunde, minute).atZone(zone).toInstant(),
        zone,
    )

    private val tabletten = Aktivitaet(
        id = 1,
        name = "Tabletten",
        farbe = 1,
        basisPunkte = 10,
        kulanzMinuten = 60,
        puenktlichkeitsBonus = 5,
        zeitplan = Zeitplan.FesteZeiten(listOf(LocalTime.of(8, 0)), DayOfWeek.entries.toSet()),
        erstelltAm = LocalDate.of(2026, 1, 1),
    )

    private val morgens = Faelligkeit(
        id = 10,
        aktivitaetId = 1,
        periodeVon = LocalDate.of(2026, 3, 2),
        periodeBis = LocalDate.of(2026, 3, 2),
        geplantFuer = LocalDateTime.of(2026, 3, 2, 8, 0),
        sollAnzahl = 1,
    )

    private fun useCase(
        uhr: Clock,
        aktivitaeten: FakeAktivitaetRepository,
        faelligkeiten: FaelligkeitRepository,
        buchungen: FakeBuchungRepository,
        tagesbonus: Int = 0,
        transaktionen: FakeTransaktionen = FakeTransaktionen(),
        einstellungen: suspend () -> Einstellungen = { Einstellungen(tagesbonusPunkte = tagesbonus) },
    ) = FaelligkeitErledigen(
        aktivitaeten = aktivitaeten,
        faelligkeiten = faelligkeiten,
        buchungen = buchungen,
        punkteRechner = PunkteRechner(),
        serienRechner = SerienRechner(),
        transaktionen = transaktionen,
        einstellungenLesen = einstellungen,
        uhr = uhr,
    )

    @Test
    fun `puenktliches Erledigen bucht Basis und Bonus und schliesst die Faelligkeit`() = runTest {
        val f = FakeFaelligkeitRepository(listOf(morgens))
        val b = FakeBuchungRepository()
        val use = useCase(uhrUm(8, 10), FakeAktivitaetRepository(listOf(tabletten)), f, b)

        assertEquals(ErledigungsErgebnis.GEBUCHT, use(10))

        assertEquals(15, b.stand.value.sumOf { it.betrag })
        assertEquals(FaelligkeitStatus.ERLEDIGT, f.stand.value.single().status)
        assertEquals(true, f.stand.value.single().warPuenktlich)
    }

    @Test
    fun `scheitert das Lesen der Einstellungen wird weder gebucht noch abgehakt`() = runTest {
        val f = FakeFaelligkeitRepository(listOf(morgens))
        val b = FakeBuchungRepository()
        val use = useCase(
            uhrUm(8, 10), FakeAktivitaetRepository(listOf(tabletten)), f, b,
            einstellungen = { throw IOException("Einstellungen unlesbar") },
        )

        assertFailsWith<IOException> { use(10) }

        assertEquals(FaelligkeitStatus.OFFEN, f.stand.value.single().status)
        assertEquals(0, f.stand.value.single().istAnzahl)
        assertEquals(emptyList(), b.stand.value)
    }

    @Test
    fun `ein zweiter Aufruf bucht nicht erneut`() = runTest {
        val f = FakeFaelligkeitRepository(listOf(morgens))
        val b = FakeBuchungRepository()
        val use = useCase(uhrUm(8, 10), FakeAktivitaetRepository(listOf(tabletten)), f, b)

        use(10)
        assertEquals(ErledigungsErgebnis.NICHT_OFFEN, use(10))

        assertEquals(15, b.stand.value.sumOf { it.betrag })
    }

    @Test
    fun `ein Zaehlziel bleibt offen bis die Sollanzahl erreicht ist`() = runTest {
        val sport = tabletten.copy(
            id = 2,
            name = "Sport",
            basisPunkte = 20,
            puenktlichkeitsBonus = 0,
            zeitplan = Zeitplan.Wochenziel(anzahl = 3),
        )
        val ziel = Faelligkeit(
            id = 20,
            aktivitaetId = 2,
            periodeVon = LocalDate.of(2026, 3, 2),
            periodeBis = LocalDate.of(2026, 3, 8),
            geplantFuer = null,
            sollAnzahl = 3,
        )
        val f = FakeFaelligkeitRepository(listOf(ziel))
        val b = FakeBuchungRepository()
        val use = useCase(uhrUm(18, 0), FakeAktivitaetRepository(listOf(sport)), f, b)

        use(20)
        use(20)

        assertEquals(2, f.stand.value.single().istAnzahl)
        assertEquals(FaelligkeitStatus.OFFEN, f.stand.value.single().status)
        assertEquals(40, b.stand.value.sumOf { it.betrag })

        use(20)

        assertEquals(FaelligkeitStatus.ERLEDIGT, f.stand.value.single().status)
        assertEquals(60, b.stand.value.sumOf { it.betrag })
    }

    @Test
    fun `der Serienbonus wird bei zwei Erledigungen am selben Tag nur einmal gebucht`() = runTest {
        val zweimal = tabletten.copy(
            puenktlichkeitsBonus = 0,
            serienSchwellen = listOf(de.vanhake.dayorganizer.domain.model.SerienSchwelle(1, 100)),
            zeitplan = Zeitplan.FesteZeiten(
                listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
                DayOfWeek.entries.toSet(),
            ),
        )
        val abends = morgens.copy(id = 11, geplantFuer = LocalDateTime.of(2026, 3, 2, 20, 0))
        val f = FakeFaelligkeitRepository(listOf(morgens, abends))
        val b = FakeBuchungRepository()
        val use = useCase(uhrUm(20, 30), FakeAktivitaetRepository(listOf(zweimal)), f, b)

        use(10)
        // Der Tag ist erst nach der Abenddosis abgeschlossen -- vorher darf der
        // Serienbonus noch nicht gebucht sein, sonst waere er mit der ersten statt
        // der letzten Faelligkeit des Tages gekommen.
        assertEquals(0, b.stand.value.count { it.art == BuchungsArt.SERIENBONUS })

        use(11)

        assertEquals(1, b.stand.value.count { it.art == BuchungsArt.SERIENBONUS })
        assertEquals(120, b.stand.value.sumOf { it.betrag })
    }

    @Test
    fun `der Tagesbonus kommt erst mit der letzten Faelligkeit des Tages`() = runTest {
        val abends = morgens.copy(id = 11, geplantFuer = LocalDateTime.of(2026, 3, 2, 20, 0))
        val f = FakeFaelligkeitRepository(listOf(morgens, abends))
        val b = FakeBuchungRepository()
        val use = useCase(
            uhrUm(20, 30), FakeAktivitaetRepository(listOf(tabletten)), f, b, tagesbonus = 25,
        )

        use(10)
        assertEquals(0, b.stand.value.count { it.art == BuchungsArt.TAGESBONUS })

        use(11)
        assertEquals(1, b.stand.value.count { it.art == BuchungsArt.TAGESBONUS })
    }

    @Test
    fun `ein offenes Wochenziel blockiert den Tagesbonus nicht`() = runTest {
        val sport = tabletten.copy(
            id = 2,
            name = "Sport",
            zeitplan = Zeitplan.Wochenziel(anzahl = 3),
        )
        val wochenziel = Faelligkeit(
            id = 20,
            aktivitaetId = 2,
            periodeVon = LocalDate.of(2026, 3, 2),
            periodeBis = LocalDate.of(2026, 3, 8),
            geplantFuer = null,
            sollAnzahl = 3,
        )
        val f = FakeFaelligkeitRepository(listOf(morgens, wochenziel))
        val b = FakeBuchungRepository()
        val use = useCase(
            uhrUm(8, 10), FakeAktivitaetRepository(listOf(tabletten, sport)), f, b, tagesbonus = 25,
        )

        use(10)

        assertEquals(1, b.stand.value.count { it.art == BuchungsArt.TAGESBONUS })
    }

    @Test
    fun `eine unbekannte Faelligkeit wird gemeldet statt zu buchen`() = runTest {
        val b = FakeBuchungRepository()
        val use = useCase(uhrUm(8, 10), FakeAktivitaetRepository(listOf(tabletten)), FakeFaelligkeitRepository(), b)

        assertEquals(ErledigungsErgebnis.UNBEKANNT, use(999))
        assertEquals(0, b.stand.value.size)
    }

    @Test
    fun `mitProtokoll liefert die vorherige Faelligkeit und alle gebuchten Zeilen`() = runTest {
        val f = FakeFaelligkeitRepository(listOf(morgens))
        val b = FakeBuchungRepository()
        val use = useCase(uhrUm(8, 10), FakeAktivitaetRepository(listOf(tabletten)), f, b)

        val protokoll = use.mitProtokoll(10) as ErledigungsProtokoll.Gebucht

        assertEquals(morgens, protokoll.faelligkeitVorher)
        assertEquals(15, protokoll.buchungen.sumOf { it.betrag })
        assertEquals(setOf(BuchungsArt.ERLEDIGUNG, BuchungsArt.PUENKTLICHKEITSBONUS), protokoll.buchungen.map { it.art }.toSet())
    }

    @Test
    fun `mitProtokoll meldet NichtOffen ohne zu buchen`() = runTest {
        val f = FakeFaelligkeitRepository(listOf(morgens))
        val b = FakeBuchungRepository()
        val use = useCase(uhrUm(8, 10), FakeAktivitaetRepository(listOf(tabletten)), f, b)
        use(10)

        val protokoll = use.mitProtokoll(10)

        assertEquals(ErledigungsProtokoll.NichtOffen, protokoll)
    }

    @Test
    fun `invoke verhaelt sich nach der Protokoll-Erweiterung unveraendert`() = runTest {
        val f = FakeFaelligkeitRepository(listOf(morgens))
        val b = FakeBuchungRepository()
        val use = useCase(uhrUm(8, 10), FakeAktivitaetRepository(listOf(tabletten)), f, b)

        assertEquals(ErledigungsErgebnis.GEBUCHT, use(10))
        assertEquals(ErledigungsErgebnis.NICHT_OFFEN, use(10))
    }

    @Test
    fun `Auslassen bucht nichts und markiert die Faelligkeit`() = runTest {
        val f = FakeFaelligkeitRepository(listOf(morgens))
        val b = FakeBuchungRepository()
        val use = FaelligkeitAuslassen(f, uhrUm(9, 0))

        use(10)

        assertEquals(FaelligkeitStatus.AUSGELASSEN, f.stand.value.single().status)
        assertEquals(0, b.stand.value.size)
    }

    @Test
    fun `Auslassen mitProtokoll liefert die vorherige Faelligkeit`() = runTest {
        val f = FakeFaelligkeitRepository(listOf(morgens))
        val use = FaelligkeitAuslassen(f, uhrUm(9, 0))

        val vorher = use.mitProtokoll(10)

        assertEquals(morgens, vorher)
        assertEquals(FaelligkeitStatus.AUSGELASSEN, f.stand.value.single().status)
    }

    @Test
    fun `Auslassen mitProtokoll liefert null wenn nicht offen`() = runTest {
        val f = FakeFaelligkeitRepository(listOf(morgens))
        val use = FaelligkeitAuslassen(f, uhrUm(9, 0))
        use(10)

        assertEquals(null, use.mitProtokoll(10))
    }

    @Test
    fun `eine ausgelassene Faelligkeit wird nicht nachtraeglich bezahlt`() = runTest {
        val f = FakeFaelligkeitRepository(listOf(morgens))
        val b = FakeBuchungRepository()
        val auslassen = FaelligkeitAuslassen(f, uhrUm(9, 0))
        auslassen(10)

        val use = useCase(uhrUm(9, 30), FakeAktivitaetRepository(listOf(tabletten)), f, b)

        assertEquals(ErledigungsErgebnis.NICHT_OFFEN, use(10))
        assertEquals(FaelligkeitStatus.AUSGELASSEN, f.stand.value.single().status)
        assertEquals(0, b.stand.value.size)
    }

    @Test
    fun `eine verpasste Faelligkeit wird nicht nachtraeglich bezahlt`() = runTest {
        val verpasst = morgens.copy(status = FaelligkeitStatus.VERPASST)
        val f = FakeFaelligkeitRepository(listOf(verpasst))
        val b = FakeBuchungRepository()
        val use = useCase(uhrUm(9, 30), FakeAktivitaetRepository(listOf(tabletten)), f, b)

        assertEquals(ErledigungsErgebnis.NICHT_OFFEN, use(10))
        assertEquals(FaelligkeitStatus.VERPASST, f.stand.value.single().status)
        assertEquals(0, b.stand.value.size)
    }

    @Test
    fun `alles laeuft in einer Transaktion`() = runTest {
        val f = FakeFaelligkeitRepository(listOf(morgens))
        val b = FakeBuchungRepository()
        val transaktionen = FakeTransaktionen()
        val use = useCase(
            uhrUm(8, 10), FakeAktivitaetRepository(listOf(tabletten)),
            StatusSpion(f, transaktionen), b,
            transaktionen = transaktionen,
        )

        use(10)

        // Der Spion wirft bereits waehrend des Aufrufs, falls nachId() ausserhalb der
        // Transaktion gelesen wurde -- diese Zusicherung bestaetigt nur den Rahmen.
        assertEquals(1, transaktionen.aufrufe)
    }

    @Test
    fun `nach Rueckgaengig wird derselbe Tagesbonus bei erneutem Erledigen wieder gebucht`() = runTest {
        val f = FakeFaelligkeitRepository(listOf(morgens))
        val b = FakeBuchungRepository()
        val transaktionen = FakeTransaktionen()
        val use = useCase(
            uhrUm(20, 0), FakeAktivitaetRepository(listOf(tabletten)), f, b,
            tagesbonus = 25, transaktionen = transaktionen,
        )
        val rueckgaengig = FaelligkeitErledigenRueckgaengig(f, b, transaktionen, uhrUm(20, 5))

        val erstesProtokoll = use.mitProtokoll(10) as ErledigungsProtokoll.Gebucht
        assertEquals(1, b.stand.value.count { it.art == BuchungsArt.TAGESBONUS })

        rueckgaengig(erstesProtokoll)
        assertEquals(FaelligkeitStatus.OFFEN, f.stand.value.single().status)

        val zweitesProtokoll = use.mitProtokoll(10)
        assertEquals(true, zweitesProtokoll is ErledigungsProtokoll.Gebucht)
        assertEquals(2, b.stand.value.count { it.art == BuchungsArt.TAGESBONUS })
    }

    @Test
    fun `VerpassteAbschliessen markiert nur abgelaufene offene Faelligkeiten`() = runTest {
        val gestern = morgens.copy(
            id = 9,
            periodeVon = LocalDate.of(2026, 3, 1),
            periodeBis = LocalDate.of(2026, 3, 1),
            geplantFuer = LocalDateTime.of(2026, 3, 1, 8, 0),
        )
        val f = FakeFaelligkeitRepository(listOf(gestern, morgens))
        val use = VerpassteAbschliessen(f, uhrUm(3, 0))

        use()

        assertEquals(FaelligkeitStatus.VERPASST, f.stand.value.first { it.id == 9L }.status)
        assertEquals(FaelligkeitStatus.OFFEN, f.stand.value.first { it.id == 10L }.status)
    }
}
