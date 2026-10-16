package de.vanhake.dayorganizer.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.vanhake.dayorganizer.FakeBuchungRepository
import de.vanhake.dayorganizer.data.db.DayOrganizerDatenbank
import de.vanhake.dayorganizer.domain.model.Buchung
import de.vanhake.dayorganizer.domain.model.BuchungsArt
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals

/**
 * Dieselben Szenarien gegen die SQL-Implementierung (RaumBuchungRepository) und gegen
 * FakeBuchungRepository. Die Fakes tragen die Logik der Bonus-Dedupe-Abfragen nach; ohne
 * diesen Vertrag koennten beide auseinanderlaufen, und die Use-Case-Tests (die nur das Fake
 * sehen) blieben trotzdem gruen. Folgearbeit aus dem Abschlussreview von Plan 4.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BuchungRepositoryVertragTest {

    private val tag = LocalDate.of(2026, 3, 2)
    private fun um(stunde: Int) = tag.atTime(stunde, 0)

    private suspend fun fuerBeide(block: suspend (name: String, repo: BuchungRepository) -> Unit) {
        block("Fake", FakeBuchungRepository())
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            DayOrganizerDatenbank::class.java,
        ).allowMainThreadQueries().build()
        try {
            block("Raum", RaumBuchungRepository(db.buchungDao()))
        } finally {
            db.close()
        }
    }

    @Test
    fun `ein gebuchter Tagesbonus zaehlt`() = runTest {
        fuerBeide { name, repo ->
            repo.buchen(listOf(Buchung(art = BuchungsArt.TAGESBONUS, betrag = 15, zeitpunkt = um(20))))
            assertEquals(true, repo.tagesbonusGebuchtAm(tag), name)
        }
    }

    @Test
    fun `ein zurueckgenommener Tagesbonus zaehlt nicht mehr`() = runTest {
        fuerBeide { name, repo ->
            repo.buchen(
                listOf(
                    Buchung(art = BuchungsArt.TAGESBONUS, betrag = 15, zeitpunkt = um(20)),
                    Buchung(art = BuchungsArt.KORREKTUR, betrag = -15, zeitpunkt = um(21)),
                ),
            )
            assertEquals(false, repo.tagesbonusGebuchtAm(tag), name)
        }
    }

    @Test
    fun `eine zurueckgenommene Einloesung gilt nicht als Tagesbonus`() = runTest {
        fuerBeide { name, repo ->
            repo.buchen(
                listOf(
                    Buchung(art = BuchungsArt.EINLOESUNG, betrag = -200, zeitpunkt = um(19), belohnungId = 1),
                    Buchung(art = BuchungsArt.KORREKTUR, betrag = 200, zeitpunkt = um(19), belohnungId = 1),
                ),
            )
            assertEquals(false, repo.tagesbonusGebuchtAm(tag), name)
        }
    }

    @Test
    fun `ein Serienbonus zaehlt bis er zurueckgenommen wird`() = runTest {
        fuerBeide { name, repo ->
            val seit = um(0)
            repo.buchen(
                listOf(Buchung(art = BuchungsArt.SERIENBONUS, betrag = 10, zeitpunkt = um(8), aktivitaetId = 1, schwelle = 7)),
            )
            assertEquals(setOf(7), repo.serienbonusSchwellenSeit(1, seit), name)

            repo.buchen(
                listOf(Buchung(art = BuchungsArt.KORREKTUR, betrag = -10, zeitpunkt = um(9), aktivitaetId = 1, schwelle = 7)),
            )
            assertEquals(emptySet(), repo.serienbonusSchwellenSeit(1, seit), name)
        }
    }

    @Test
    fun `ein Serienbonus einer anderen Aktivitaet oder vor dem Stichtag zaehlt nicht`() = runTest {
        fuerBeide { name, repo ->
            repo.buchen(
                listOf(
                    Buchung(art = BuchungsArt.SERIENBONUS, betrag = 10, zeitpunkt = um(8), aktivitaetId = 2, schwelle = 7),
                    Buchung(art = BuchungsArt.SERIENBONUS, betrag = 30, zeitpunkt = um(8).minusDays(5), aktivitaetId = 1, schwelle = 30),
                ),
            )
            assertEquals(emptySet(), repo.serienbonusSchwellenSeit(1, um(0)), name)
        }
    }
}
