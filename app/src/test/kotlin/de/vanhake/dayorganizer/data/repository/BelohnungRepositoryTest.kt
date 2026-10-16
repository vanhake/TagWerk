package de.vanhake.dayorganizer.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.vanhake.dayorganizer.data.db.BelohnungEntity
import de.vanhake.dayorganizer.data.db.DayOrganizerDatenbank
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BelohnungRepositoryTest {

    private fun entity(name: String, preis: Int, typ: String? = null, archiviert: Boolean = false) =
        BelohnungEntity(
            name = name, beschreibung = "", preisPunkte = preis, bedingungTyp = typ,
            bedingungAktivitaetId = null, bedingungPerioden = null, bedingungDatum = null,
            archiviert = archiviert,
        )

    private suspend fun mitRepo(block: suspend (RaumBelohnungRepository) -> Unit) {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            DayOrganizerDatenbank::class.java,
        ).allowMainThreadQueries().build()
        try {
            val dao = db.belohnungDao()
            dao.einfuegen(entity("Teuer", 300))
            dao.einfuegen(entity("Kaputt", 100, typ = "ZUKUNFT"))
            dao.einfuegen(entity("Billig", 50))
            dao.einfuegen(entity("KaputtArchiviert", 10, typ = "ZUKUNFT", archiviert = true))
            dao.einfuegen(entity("Archiviert", 20, archiviert = true))
            block(RaumBelohnungRepository(dao))
        } finally {
            db.close()
        }
    }

    @Test
    fun `ein kaputter Datensatz fehlt im Shop, die anderen bleiben`() = runTest {
        mitRepo { repo ->
            assertEquals(listOf("Billig", "Teuer"), repo.beobachteAktive().first().map { it.name })
        }
    }

    @Test
    fun `ein kaputter Datensatz fehlt auch im Archiv, in alle und nachId`() = runTest {
        mitRepo { repo ->
            assertEquals(listOf("Archiviert"), repo.beobachteArchivierte().first().map { it.name })
            assertEquals(listOf("Billig", "Teuer", "Archiviert"), repo.alle().map { it.name })
            assertEquals(null, repo.nachId(2))
        }
    }
}
