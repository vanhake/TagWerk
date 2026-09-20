package de.vanhake.dayorganizer

import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Zeitplan
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
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
}
