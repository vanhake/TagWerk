package de.vanhake.dayorganizer.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.vanhake.dayorganizer.data.db.AktivitaetEntity
import de.vanhake.dayorganizer.data.db.DayOrganizerDatenbank
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BackupDienstTest {

    private lateinit var db: DayOrganizerDatenbank
    private lateinit var dienst: BackupDienst

    @Before
    fun aufbauen() {
        val kontext = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(kontext, DayOrganizerDatenbank::class.java)
            .allowMainThreadQueries()
            .build()
        dienst = BackupDienst.fuerTest(db)
    }

    @After
    fun abbauen() = db.close()

    private fun aktivitaet(name: String) = AktivitaetEntity(
        name = name, notiz = "", farbe = 1, basisPunkte = 10, kulanzMinuten = 60,
        puenktlichkeitsBonus = 5, serienSchwellen = "7:10", nachfassAnzahl = 2,
        nachfassAbstandMinuten = 15, aktiv = true, erstelltAm = LocalDate.of(2026, 1, 1),
        archiviertAm = null, zeitplanTyp = "FESTE_ZEITEN", zeitplanZeiten = "08:00",
        zeitplanWochentage = "MONDAY", zeitplanIntervallStunden = null,
        zeitplanFensterVon = null, zeitplanFensterBis = null, zeitplanZielAnzahl = null,
        zeitplanAnstoss = "",
    )

    @Test
    fun export_und_import_stellen_den_Bestand_wieder_her() = runTest {
        db.aktivitaetDao().einfuegen(aktivitaet("Tabletten"))
        db.aktivitaetDao().einfuegen(aktivitaet("Sport"))

        val puffer = ByteArrayOutputStream()
        dienst.exportieren(puffer)

        db.aktivitaetDao().alleLoeschen()
        assertEquals(0, db.aktivitaetDao().alle().size)

        val ergebnis = dienst.importieren(ByteArrayInputStream(puffer.toByteArray()))

        assertTrue(ergebnis is ImportErgebnis.Erfolg)
        assertEquals(
            listOf("Sport", "Tabletten"),
            db.aktivitaetDao().alle().map { it.name }.sorted(),
        )
    }

    @Test
    fun ein_Import_ersetzt_statt_zu_mischen() = runTest {
        db.aktivitaetDao().einfuegen(aktivitaet("Tabletten"))
        val puffer = ByteArrayOutputStream()
        dienst.exportieren(puffer)

        db.aktivitaetDao().einfuegen(aktivitaet("Spaeter dazugekommen"))
        dienst.importieren(ByteArrayInputStream(puffer.toByteArray()))

        assertEquals(listOf("Tabletten"), db.aktivitaetDao().alle().map { it.name })
    }

    @Test
    fun eine_fremde_Schemaversion_wird_abgelehnt() = runTest {
        val fremd = """{"schemaVersion":99,"erstelltAm":"2026-03-02T21:00",""" +
            """"aktivitaeten":[],"faelligkeiten":[],"buchungen":[],"belohnungen":[],""" +
            """"einloesungen":[],"einstellungen":{"tagesbonusPunkte":15,"standardKulanzMinuten":60}}"""

        val ergebnis = dienst.importieren(ByteArrayInputStream(fremd.toByteArray()))

        assertEquals(ImportErgebnis.FalschesSchema(99), ergebnis)
    }

    @Test
    fun beschaedigtes_JSON_laesst_den_Bestand_unberuehrt() = runTest {
        db.aktivitaetDao().einfuegen(aktivitaet("Tabletten"))

        val ergebnis = dienst.importieren(ByteArrayInputStream("{kein json".toByteArray()))

        assertTrue(ergebnis is ImportErgebnis.Unlesbar)
        assertEquals(1, db.aktivitaetDao().alle().size)
    }

    // Die Datei ist gueltiges JSON mit bekannter Version, passt aber nicht in die
    // Datenbank: die Faelligkeit verweist auf eine Aktivitaet, die es nicht gibt. Das
    // faellt erst beim Einfuegen auf, NACHDEM der alte Bestand in derselben
    // Transaktion schon geloescht wurde. Dass "Tabletten" danach noch da ist, ist der
    // erste Nachweis im Projekt, dass der Rollback wirklich greift -- bisher ruhte
    // die ganze Zusage "keine halb importierte Datenbank" allein auf Rooms Wort.
    @Test
    fun ein_Verweis_ins_Leere_wird_abgelehnt_und_der_Bestand_bleibt() = runTest {
        db.aktivitaetDao().einfuegen(aktivitaet("Tabletten"))
        val verwaist = """{"schemaVersion":1,"erstelltAm":"2026-03-02T21:00",""" +
            """"aktivitaeten":[],"faelligkeiten":[{"id":1,"aktivitaetId":999,""" +
            """"periodeVon":"2026-03-02","periodeBis":"2026-03-02",""" +
            """"geplantFuer":"2026-03-02T08:00","sollAnzahl":1,"istAnzahl":0,""" +
            """"status":"OFFEN","erledigtAm":null,"warPuenktlich":false}],""" +
            """"buchungen":[],"belohnungen":[],"einloesungen":[],""" +
            """"einstellungen":{"tagesbonusPunkte":15,"standardKulanzMinuten":60}}"""

        val ergebnis = dienst.importieren(ByteArrayInputStream(verwaist.toByteArray()))

        assertTrue(ergebnis is ImportErgebnis.Unlesbar)
        assertEquals(listOf("Tabletten"), db.aktivitaetDao().alle().map { it.name })
    }
}
