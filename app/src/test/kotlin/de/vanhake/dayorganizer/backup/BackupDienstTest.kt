package de.vanhake.dayorganizer.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.vanhake.dayorganizer.data.Einstellungen
import de.vanhake.dayorganizer.data.db.AktivitaetEntity
import de.vanhake.dayorganizer.data.db.BelohnungEntity
import de.vanhake.dayorganizer.data.db.BuchungEntity
import de.vanhake.dayorganizer.data.db.DayOrganizerDatenbank
import de.vanhake.dayorganizer.data.db.EinloesungEntity
import de.vanhake.dayorganizer.data.db.FaelligkeitEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BackupDienstTest {

    private lateinit var db: DayOrganizerDatenbank
    private lateinit var dienst: BackupDienst

    // Direkt (nicht ueber BackupDienst.fuerTest) konstruiert, damit die Tests den
    // gehaltenen Stand lesen und veraendern koennen -- fuerTest haelt ihn privat in
    // einer Closure (Review-Fund W7: die Einstellungen muessen mitgeprueft werden).
    private var einstellungenGehalten = Einstellungen()

    @Before
    fun aufbauen() {
        val kontext = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(kontext, DayOrganizerDatenbank::class.java)
            .allowMainThreadQueries()
            .build()
        einstellungenGehalten = Einstellungen()
        dienst = BackupDienst(
            db = db,
            einstellungenLesen = { einstellungenGehalten },
            einstellungenSchreiben = { einstellungenGehalten = it },
            uhr = Clock.systemDefaultZone(),
        )
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

    private fun faelligkeit(aktivitaetId: Long, periodeVon: LocalDate) = FaelligkeitEntity(
        aktivitaetId = aktivitaetId,
        schluessel = "$aktivitaetId|$periodeVon|ZIEL",
        periodeVon = periodeVon, periodeBis = periodeVon, geplantFuer = null,
        sollAnzahl = 1, istAnzahl = 0, status = "OFFEN", erledigtAm = null, warPuenktlich = false,
    )

    private fun buchung(betrag: Int, zeitpunkt: LocalDateTime) = BuchungEntity(
        art = "TAGESBONUS", betrag = betrag, zeitpunkt = zeitpunkt,
        aktivitaetId = null, faelligkeitId = null, belohnungId = null, schwelle = null, notiz = "",
    )

    private fun belohnung(name: String) = BelohnungEntity(
        name = name, beschreibung = "", preisPunkte = 100,
        bedingungTyp = null, bedingungAktivitaetId = null, bedingungPerioden = null,
        bedingungDatum = null, archiviert = false,
    )

    private fun einloesung(belohnungId: Long, name: String) = EinloesungEntity(
        belohnungId = belohnungId, name = name, preisPunkte = 100,
        zeitpunkt = LocalDateTime.of(2026, 3, 2, 19, 0),
    )

    /** Fuellt jede der fuenf Tabellen und die Einstellungen mit genau einem Eintrag. */
    private suspend fun bestandAnlegen() {
        val aktivitaetId = db.aktivitaetDao().einfuegen(aktivitaet("Tabletten"))
        db.faelligkeitDao().alleEinfuegen(listOf(faelligkeit(aktivitaetId, LocalDate.of(2026, 1, 2))))
        db.buchungDao().einfuegen(listOf(buchung(10, LocalDateTime.of(2026, 1, 2, 8, 0))))
        val belohnungId = db.belohnungDao().einfuegen(belohnung("Kinoabend"))
        db.belohnungDao().einloesungEinfuegen(einloesung(belohnungId, "Kinoabend"))
        einstellungenGehalten = Einstellungen(tagesbonusPunkte = 15, standardKulanzMinuten = 20)
    }

    @Test
    fun export_und_import_stellen_den_Bestand_wieder_her() = runTest {
        db.aktivitaetDao().einfuegen(aktivitaet("Sport"))
        bestandAnlegen()

        val puffer = ByteArrayOutputStream()
        dienst.exportieren(puffer)

        db.faelligkeitDao().alleLoeschen()
        db.aktivitaetDao().alleLoeschen()
        db.buchungDao().alleLoeschen()
        db.belohnungDao().alleEinloesungenLoeschen()
        db.belohnungDao().alleLoeschen()
        einstellungenGehalten = Einstellungen()
        assertEquals(0, db.aktivitaetDao().alle().size)

        val ergebnis = dienst.importieren(ByteArrayInputStream(puffer.toByteArray()))

        assertTrue(ergebnis is ImportErgebnis.Erfolg)
        assertEquals(
            listOf("Sport", "Tabletten"),
            db.aktivitaetDao().alle().map { it.name }.sorted(),
        )
        assertEquals(1, db.faelligkeitDao().alle().size)
        assertEquals(1, db.buchungDao().alle().size)
        assertEquals(1, db.belohnungDao().alle().size)
        assertEquals(1, db.belohnungDao().alleEinloesungen().size)
        assertEquals(Einstellungen(tagesbonusPunkte = 15, standardKulanzMinuten = 20), einstellungenGehalten)
    }

    @Test
    fun ein_Import_ersetzt_statt_zu_mischen() = runTest {
        bestandAnlegen()
        val puffer = ByteArrayOutputStream()
        dienst.exportieren(puffer)

        // Nach dem Export kommt in jeder Tabelle und bei den Einstellungen noch etwas
        // dazu -- genau das muss der Import restlos wegwerfen, nicht dazumischen.
        val spaeterAktivitaetId = db.aktivitaetDao().einfuegen(aktivitaet("Spaeter dazugekommen"))
        db.faelligkeitDao().alleEinfuegen(listOf(faelligkeit(spaeterAktivitaetId, LocalDate.of(2026, 2, 2))))
        db.buchungDao().einfuegen(listOf(buchung(99, LocalDateTime.of(2026, 2, 2, 8, 0))))
        val spaeterBelohnungId = db.belohnungDao().einfuegen(belohnung("Spaeter dazugekommen"))
        db.belohnungDao().einloesungEinfuegen(einloesung(spaeterBelohnungId, "Spaeter dazugekommen"))
        einstellungenGehalten = Einstellungen(tagesbonusPunkte = 99, standardKulanzMinuten = 99)

        dienst.importieren(ByteArrayInputStream(puffer.toByteArray()))

        assertEquals(listOf("Tabletten"), db.aktivitaetDao().alle().map { it.name })
        assertEquals(1, db.faelligkeitDao().alle().size)
        assertEquals(1, db.buchungDao().alle().size)
        assertEquals(listOf(10), db.buchungDao().alle().map { it.betrag })
        assertEquals(listOf("Kinoabend"), db.belohnungDao().alle().map { it.name })
        assertEquals(listOf("Kinoabend"), db.belohnungDao().alleEinloesungen().map { it.name })
        assertEquals(Einstellungen(tagesbonusPunkte = 15, standardKulanzMinuten = 20), einstellungenGehalten)
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

    // Review-Fund W2: OnConflictStrategy.REPLACE auf den Sammelinserts liess zwei
    // Buchungen mit gleicher id in der Datei still eine davon verschlucken, statt den
    // Import abzulehnen. Mit ABORT (dem Standard) verletzt die zweite Buchung die
    // Primaerschluessel-Eindeutigkeit, die Transaktion rollt zurueck.
    @Test
    fun zwei_Buchungen_mit_gleicher_id_werden_abgelehnt_und_der_Bestand_bleibt() = runTest {
        db.aktivitaetDao().einfuegen(aktivitaet("Tabletten"))
        val doppelt = """{"schemaVersion":1,"erstelltAm":"2026-03-02T21:00",""" +
            """"aktivitaeten":[],"faelligkeiten":[],""" +
            """"buchungen":[{"id":1,"art":"TAGESBONUS","betrag":5,"zeitpunkt":"2026-03-02T08:00",""" +
            """"aktivitaetId":null,"faelligkeitId":null,"belohnungId":null,"schwelle":null,"notiz":""},""" +
            """{"id":1,"art":"TAGESBONUS","betrag":7,"zeitpunkt":"2026-03-02T09:00",""" +
            """"aktivitaetId":null,"faelligkeitId":null,"belohnungId":null,"schwelle":null,"notiz":""}],""" +
            """"belohnungen":[],"einloesungen":[],""" +
            """"einstellungen":{"tagesbonusPunkte":15,"standardKulanzMinuten":60}}"""

        val ergebnis = dienst.importieren(ByteArrayInputStream(doppelt.toByteArray()))

        assertTrue(ergebnis is ImportErgebnis.Unlesbar)
        assertEquals(listOf("Tabletten"), db.aktivitaetDao().alle().map { it.name })
        assertEquals(0, db.buchungDao().alle().size)
    }

    // Review-Fund W1: Scheitert nur noch das Schreiben der Einstellungen -- der
    // Datenbestand ist zu diesem Zeitpunkt laengst ersetzt und committet --, darf der
    // Dienst das weder ungefangen werfen noch als vollen Erfolg ausgeben.
    @Test
    fun scheiterndes_Schreiben_der_Einstellungen_meldet_das_ehrlich() = runTest {
        db.aktivitaetDao().einfuegen(aktivitaet("Tabletten"))
        val puffer = ByteArrayOutputStream()
        dienst.exportieren(puffer)

        val dienstMitFehlerhaftenEinstellungen = BackupDienst(
            db = db,
            einstellungenLesen = { einstellungenGehalten },
            einstellungenSchreiben = { throw IOException("Platte voll") },
            uhr = Clock.systemDefaultZone(),
        )

        val ergebnis = dienstMitFehlerhaftenEinstellungen.importieren(ByteArrayInputStream(puffer.toByteArray()))

        assertTrue(ergebnis is ImportErgebnis.Erfolg)
        assertEquals(false, (ergebnis as ImportErgebnis.Erfolg).einstellungenUebernommen)
        assertEquals(listOf("Tabletten"), db.aktivitaetDao().alle().map { it.name })
    }
}
