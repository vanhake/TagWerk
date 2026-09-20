package de.vanhake.dayorganizer.domainuse

import de.vanhake.dayorganizer.FakeAktivitaetRepository
import de.vanhake.dayorganizer.FakeBuchungRepository
import de.vanhake.dayorganizer.FakeFaelligkeitRepository
import de.vanhake.dayorganizer.FakeTransaktionen
import de.vanhake.dayorganizer.data.Einstellungen
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
import kotlin.test.assertEquals

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
        faelligkeiten: FakeFaelligkeitRepository,
        buchungen: FakeBuchungRepository,
        tagesbonus: Int = 0,
        transaktionen: FakeTransaktionen = FakeTransaktionen(),
    ) = FaelligkeitErledigen(
        aktivitaeten = aktivitaeten,
        faelligkeiten = faelligkeiten,
        buchungen = buchungen,
        punkteRechner = PunkteRechner(),
        serienRechner = SerienRechner(),
        transaktionen = transaktionen,
        einstellungenLesen = { Einstellungen(tagesbonusPunkte = tagesbonus) },
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
    fun `Auslassen bucht nichts und markiert die Faelligkeit`() = runTest {
        val f = FakeFaelligkeitRepository(listOf(morgens))
        val b = FakeBuchungRepository()
        val use = FaelligkeitAuslassen(f, uhrUm(9, 0))

        use(10)

        assertEquals(FaelligkeitStatus.AUSGELASSEN, f.stand.value.single().status)
        assertEquals(0, b.stand.value.size)
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
            uhrUm(8, 10), FakeAktivitaetRepository(listOf(tabletten)), f, b,
            transaktionen = transaktionen,
        )

        use(10)

        assertEquals(1, transaktionen.aufrufe)
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
