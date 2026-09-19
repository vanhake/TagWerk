package de.vanhake.dayorganizer.domainuse

import de.vanhake.dayorganizer.FakeAktivitaetRepository
import de.vanhake.dayorganizer.FakeErinnerungRepository
import de.vanhake.dayorganizer.FakeFaelligkeitRepository
import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.Zeitplan
import de.vanhake.dayorganizer.domain.rechner.ZeitplanRechner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FaelligkeitenMaterialisierenTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val uhr = Clock.fixed(
        LocalDateTime.of(2026, 3, 2, 6, 0).atZone(zone).toInstant(),
        zone,
    )

    private val tabletten = Aktivitaet(
        id = 1,
        name = "Tabletten",
        farbe = 1,
        basisPunkte = 10,
        zeitplan = Zeitplan.FesteZeiten(
            zeiten = listOf(java.time.LocalTime.of(8, 0)),
            wochentage = DayOfWeek.entries.toSet(),
        ),
        erstelltAm = LocalDate.of(2026, 1, 1),
    )

    @Test
    fun `legt Faelligkeiten fuer den Vorausblick an`() = runTest {
        val faelligkeiten = FakeFaelligkeitRepository()
        val use = FaelligkeitenMaterialisieren(
            aktivitaeten = FakeAktivitaetRepository(listOf(tabletten)),
            faelligkeiten = faelligkeiten,
            erinnerungen = FakeErinnerungRepository(),
            zeitplanRechner = ZeitplanRechner(),
            uhr = uhr,
        )

        use(vorausTage = 6)

        assertEquals(7, faelligkeiten.stand.value.size)
        assertEquals(LocalDate.of(2026, 3, 2), faelligkeiten.stand.value.first().periodeVon)
        assertEquals(LocalDate.of(2026, 3, 8), faelligkeiten.stand.value.last().periodeVon)
    }

    @Test
    fun `ein zweiter Lauf legt nichts doppelt an`() = runTest {
        val faelligkeiten = FakeFaelligkeitRepository()
        val use = FaelligkeitenMaterialisieren(
            aktivitaeten = FakeAktivitaetRepository(listOf(tabletten)),
            faelligkeiten = faelligkeiten,
            erinnerungen = FakeErinnerungRepository(),
            zeitplanRechner = ZeitplanRechner(),
            uhr = uhr,
        )

        use(vorausTage = 6)
        use(vorausTage = 6)

        assertEquals(7, faelligkeiten.stand.value.size)
    }

    @Test
    fun `archivierte Aktivitaeten erzeugen keine Faelligkeiten`() = runTest {
        val faelligkeiten = FakeFaelligkeitRepository()
        val aktivitaeten = FakeAktivitaetRepository(
            listOf(tabletten.copy(aktiv = false, archiviertAm = LocalDate.of(2026, 2, 1))),
        )
        val use = FaelligkeitenMaterialisieren(
            aktivitaeten,
            faelligkeiten,
            FakeErinnerungRepository(),
            ZeitplanRechner(),
            uhr,
        )

        use(vorausTage = 6)

        assertEquals(0, faelligkeiten.stand.value.size)
    }

    @Test
    fun `ein Wochenziel wird bei wiederholtem Lauf nicht doppelt angelegt`() = runTest {
        val sport = tabletten.copy(
            id = 2,
            name = "Sport",
            zeitplan = Zeitplan.Wochenziel(anzahl = 3),
        )
        val faelligkeiten = FakeFaelligkeitRepository()
        val use = FaelligkeitenMaterialisieren(
            aktivitaeten = FakeAktivitaetRepository(listOf(sport)),
            faelligkeiten = faelligkeiten,
            erinnerungen = FakeErinnerungRepository(),
            zeitplanRechner = ZeitplanRechner(),
            uhr = uhr,
        )

        use(vorausTage = 6)
        use(vorausTage = 6)

        assertEquals(1, faelligkeiten.stand.value.size)
        assertNull(faelligkeiten.stand.value.single().geplantFuer)
    }

    @Test
    fun `fuerZeitraum liefert terminierte Faelligkeiten vor Zielen der gleichen Periode`() = runTest {
        val periodeVon = LocalDate.of(2026, 3, 2)
        val periodeBis = LocalDate.of(2026, 3, 8)
        val ziel = Faelligkeit(
            id = 1,
            aktivitaetId = 1,
            periodeVon = periodeVon,
            periodeBis = periodeBis,
            geplantFuer = null,
            sollAnzahl = 3,
        )
        val termin = Faelligkeit(
            id = 2,
            aktivitaetId = 1,
            periodeVon = periodeVon,
            periodeBis = periodeVon,
            geplantFuer = periodeVon.atTime(8, 0),
            sollAnzahl = 1,
        )
        // Absichtlich in "falscher" Reihenfolge geseedet, damit der Test die Sortierung
        // der Fake-Implementierung prueft und nicht nur die Einfuegereihenfolge.
        val faelligkeiten = FakeFaelligkeitRepository(listOf(ziel, termin))

        val ergebnis = faelligkeiten.fuerZeitraum(periodeVon, periodeBis)

        assertEquals(listOf(termin.id, ziel.id), ergebnis.map { it.id })
    }

    @Test
    fun `beobachteAktive liefert Aktivitaeten alphabetisch sortiert nach Namen`() = runTest {
        val zeta = tabletten.copy(id = 1, name = "Zeta")
        val alpha = tabletten.copy(id = 2, name = "Alpha")
        // Absichtlich in "falscher" Reihenfolge geseedet.
        val aktivitaeten = FakeAktivitaetRepository(listOf(zeta, alpha))

        val ergebnis = aktivitaeten.beobachteAktive().first()

        assertEquals(listOf("Alpha", "Zeta"), ergebnis.map { it.name })
    }
}
