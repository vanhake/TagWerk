package de.vanhake.dayorganizer.domainuse

import de.vanhake.dayorganizer.FakeAktivitaetRepository
import de.vanhake.dayorganizer.FakeFaelligkeitRepository
import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Zeitplan
import de.vanhake.dayorganizer.domain.rechner.ZeitplanRechner
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

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
        val use = FaelligkeitenMaterialisieren(aktivitaeten, faelligkeiten, ZeitplanRechner(), uhr)

        use(vorausTage = 6)

        assertEquals(0, faelligkeiten.stand.value.size)
    }
}
