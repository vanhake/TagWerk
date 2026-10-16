package de.vanhake.dayorganizer

import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Zeitplan
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FakeAktivitaetRepositoryTest {

    private val tabletten = Aktivitaet(
        id = 7,
        name = "Tabletten",
        farbe = 1,
        basisPunkte = 10,
        zeitplan = Zeitplan.FesteZeiten(listOf(LocalTime.of(8, 0)), DayOfWeek.entries.toSet()),
        erstelltAm = LocalDate.of(2026, 1, 1),
    )

    /** Spiegelt Room's @Update-Verhalten (Finding 3 der Branch-Review): ein Update auf eine
     * nicht existierende id speichert nicht heimlich als neue Zeile, sondern scheitert
     * sichtbar. RaumAktivitaetRepository.speichern wertet dafuer den Rueckgabewert von
     * AktivitaetDao.aktualisieren aus; die Fake muss dasselbe Verhalten zeigen, sonst
     * verdeckt sie genau das Loch, das Room hier haette. */
    @Test
    fun `speichern mit unbekannter id schlaegt fehl statt heimlich einzufuegen`() = runTest {
        val repository = FakeAktivitaetRepository()

        assertFailsWith<IllegalStateException> {
            repository.speichern(tabletten)
        }
    }

    @Test
    fun `speichern mit id 0 legt eine neue Aktivitaet an`() = runTest {
        val repository = FakeAktivitaetRepository()

        val id = repository.speichern(tabletten.copy(id = 0))

        assertFailsWith<IllegalStateException> {
            // Kontrast: eine tatsaechlich vorhandene id darf aktualisiert werden.
            repository.speichern(tabletten.copy(id = id + 1))
        }
    }

    /** Spiegelt AktivitaetDao.beobachteArchivierte ("archiviertAm IS NOT NULL ORDER BY name") und
     * wiederherstellen ("... AND archiviertAm IS NOT NULL"): beim zweiten Aufruf false. */
    @Test
    fun `Archivierte sortieren nach Name und Wiederherstellen wirkt genau einmal`() = runTest {
        val am = LocalDate.of(2026, 3, 1)
        val repository = FakeAktivitaetRepository(
            listOf(
                tabletten.copy(id = 1, name = "B", aktiv = false, archiviertAm = am),
                tabletten.copy(id = 2, name = "A", aktiv = false, archiviertAm = am),
                tabletten.copy(id = 3, name = "Aktiv"),
            ),
        )

        assertEquals(listOf("A", "B"), repository.beobachteArchivierte().first().map { it.name })
        assertEquals(true, repository.wiederherstellen(2))
        assertEquals(false, repository.wiederherstellen(2))
        assertEquals(false, repository.wiederherstellen(3))
        assertEquals(true, repository.nachId(2)?.aktiv)
        assertEquals(null, repository.nachId(2)?.archiviertAm)
        assertEquals(listOf("B"), repository.beobachteArchivierte().first().map { it.name })
    }
}
