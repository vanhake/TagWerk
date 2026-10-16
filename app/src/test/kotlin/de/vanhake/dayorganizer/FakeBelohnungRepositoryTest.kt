package de.vanhake.dayorganizer

import de.vanhake.dayorganizer.data.repository.DatensatzFehlt
import de.vanhake.dayorganizer.domain.model.Belohnung
import de.vanhake.dayorganizer.domain.model.Einloesung
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Pinnt die fuenf Zusicherungen, die beim Bau von FakeBelohnungRepository gegenueber der
 * urspruenglichen Vorlage korrigiert wurden, gegen die tatsaechliche SQL in BelohnungDao.kt
 * (nicht gegen den Fake selbst -- sonst waere das Pinnen der Luege statt der Wahrheit).
 */
class FakeBelohnungRepositoryTest {

    private val guenstig = Belohnung(id = 1, name = "Kaffee", preisPunkte = 50)
    private val teuer = Belohnung(id = 2, name = "Kinoabend", preisPunkte = 200)
    private val archiviertGuenstig = Belohnung(id = 3, name = "Alt", preisPunkte = 10, archiviert = true)

    /** Spiegelt "SELECT * FROM belohnung WHERE archiviert = 0 ORDER BY preisPunkte"
     * (BelohnungDao.beobachteAktive): Archivierte fehlen, der Rest sortiert nach Preis. */
    @Test
    fun `beobachteAktive filtert Archivierte und sortiert nach Preis`() = runTest {
        val repository = FakeBelohnungRepository(listOf(teuer, archiviertGuenstig, guenstig))

        val aktive = repository.beobachteAktive().first()

        assertEquals(listOf(guenstig, teuer), aktive)
    }

    /** Spiegelt "SELECT * FROM belohnung ORDER BY archiviert, preisPunkte" (BelohnungDao.alle):
     * aktive vor archivierten, jeweils nach Preis sortiert. */
    @Test
    fun `alle sortiert aktive vor archivierten, jeweils nach Preis`() = runTest {
        val archiviertTeuer = teuer.copy(id = 4, archiviert = true)
        val repository = FakeBelohnungRepository(listOf(archiviertTeuer, teuer, guenstig))

        val alle = repository.alle()

        assertEquals(listOf(guenstig, teuer, archiviertTeuer), alle)
    }

    /** Spiegelt "SELECT * FROM einloesung ORDER BY zeitpunkt DESC, id DESC" (BelohnungDao.
     * beobachteEinloesungen / alleEinloesungen): neuester Zeitpunkt zuerst, bei Gleichstand
     * die groessere id zuerst. */
    @Test
    fun `Einloesungen sortieren nach Zeitpunkt absteigend, bei Gleichstand nach id absteigend`() = runTest {
        val repository = FakeBelohnungRepository()
        val frueh = Einloesung(
            belohnungId = 1, name = "A", preisPunkte = 10,
            zeitpunkt = LocalDateTime.of(2026, 3, 1, 8, 0),
        )
        val spaetZuerst = Einloesung(
            belohnungId = 1, name = "B", preisPunkte = 10,
            zeitpunkt = LocalDateTime.of(2026, 3, 2, 8, 0),
        )
        val spaetZuletzt = Einloesung(
            belohnungId = 1, name = "C", preisPunkte = 10,
            zeitpunkt = LocalDateTime.of(2026, 3, 2, 8, 0),
        )

        repository.einloesungAnlegen(frueh)
        repository.einloesungAnlegen(spaetZuerst)
        repository.einloesungAnlegen(spaetZuletzt)

        val erwartet = listOf("C", "B", "A")
        assertEquals(erwartet, repository.alleEinloesungen().map { it.name })
        assertEquals(erwartet, repository.beobachteEinloesungen().first().map { it.name })
    }

    /** Spiegelt Room's @Update-Verhalten: RaumBelohnungRepository.speichern wirft, wenn kein
     * passender Datensatz existiert, statt heimlich einzufuegen -- der Fake muss dasselbe tun. */
    @Test
    fun `speichern einer unbekannten id wirft DatensatzFehlt`() = runTest {
        val fehler = assertFailsWith<DatensatzFehlt> { FakeBelohnungRepository().speichern(guenstig.copy(id = 5)) }

        assertEquals(5L, fehler.id)
    }

    @Test
    fun `speichern mit unbekannter id schlaegt fehl statt heimlich einzufuegen`() = runTest {
        val repository = FakeBelohnungRepository()

        assertFailsWith<IllegalStateException> {
            repository.speichern(guenstig.copy(id = 5))
        }
    }

    @Test
    fun `Stornieren wirkt genau einmal und liefert die neue id beim Anlegen`() = runTest {
        val repository = FakeBelohnungRepository()
        val id = repository.einloesungAnlegen(
            Einloesung(belohnungId = 1, name = "Kino", preisPunkte = 300, zeitpunkt = LocalDateTime.of(2026, 3, 2, 19, 0)),
        )
        val am = LocalDateTime.of(2026, 3, 2, 19, 1)

        assertEquals(true, repository.einloesungStornieren(id, am))
        assertEquals(false, repository.einloesungStornieren(id, am.plusMinutes(1)))
        assertEquals(false, repository.einloesungStornieren(id + 99, am))
        assertEquals(am, repository.alleEinloesungen().single().storniertAm)
    }

    /** Spiegelt BelohnungDao.beobachteArchivierte ("archiviert = 1 ORDER BY name") und
     * wiederherstellen ("... AND archiviert = 1"): beim zweiten Aufruf false. */
    @Test
    fun `Archivierte sortieren nach Name und Wiederherstellen wirkt genau einmal`() = runTest {
        val repository = FakeBelohnungRepository(
            listOf(
                guenstig.copy(id = 1, name = "B", archiviert = true),
                guenstig.copy(id = 2, name = "A", archiviert = true),
                teuer,
            ),
        )

        assertEquals(listOf("A", "B"), repository.beobachteArchivierte().first().map { it.name })
        assertEquals(true, repository.wiederherstellen(2))
        assertEquals(false, repository.wiederherstellen(2))
        assertEquals(false, repository.wiederherstellen(teuer.id))
        assertEquals(false, repository.nachId(2)?.archiviert)
    }
}
