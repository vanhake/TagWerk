package de.vanhake.dayorganizer.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DaoTest {

    private lateinit var db: DayOrganizerDatenbank

    @Before
    fun aufbauen() {
        val kontext = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(kontext, DayOrganizerDatenbank::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun abbauen() = db.close()

    private fun aktivitaet() = AktivitaetEntity(
        name = "Tabletten",
        notiz = "",
        farbe = 1,
        basisPunkte = 10,
        kulanzMinuten = 60,
        puenktlichkeitsBonus = 5,
        serienSchwellen = "7:10",
        nachfassAnzahl = 2,
        nachfassAbstandMinuten = 15,
        aktiv = true,
        erstelltAm = LocalDate.of(2026, 1, 1),
        archiviertAm = null,
        zeitplanTyp = "FESTE_ZEITEN",
        zeitplanZeiten = "08:00",
        zeitplanWochentage = "MONDAY",
        zeitplanIntervallStunden = null,
        zeitplanFensterVon = null,
        zeitplanFensterBis = null,
        zeitplanZielAnzahl = null,
        zeitplanAnstoss = "",
    )

    @Test
    fun aktivitaet_ueberlebt_den_Rundlauf() = runTest {
        val id = db.aktivitaetDao().einfuegen(aktivitaet())

        val gelesen = db.aktivitaetDao().nachId(id)

        assertEquals("Tabletten", gelesen?.name)
        assertEquals(LocalDate.of(2026, 1, 1), gelesen?.erstelltAm)
    }

    @Test
    fun doppeltes_Materialisieren_legt_nichts_doppelt_an() = runTest {
        val id = db.aktivitaetDao().einfuegen(aktivitaet())
        val f = FaelligkeitEntity(
            aktivitaetId = id,
            schluessel = "$id|2026-03-02|2026-03-02T08:00",
            periodeVon = LocalDate.of(2026, 3, 2),
            periodeBis = LocalDate.of(2026, 3, 2),
            geplantFuer = LocalDateTime.of(2026, 3, 2, 8, 0),
            sollAnzahl = 1,
            istAnzahl = 0,
            status = "OFFEN",
            erledigtAm = null,
            warPuenktlich = false,
        )

        db.faelligkeitDao().anlegenFallsNeu(listOf(f))
        db.faelligkeitDao().anlegenFallsNeu(listOf(f))

        assertEquals(
            1,
            db.faelligkeitDao().fuerZeitraum(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 3)).size,
        )
    }

    @Test
    fun kontostand_ist_die_Summe_der_Buchungen() = runTest {
        val id = db.aktivitaetDao().einfuegen(aktivitaet())
        db.buchungDao().einfuegen(
            listOf(
                BuchungEntity(
                    art = "ERLEDIGUNG", betrag = 10,
                    zeitpunkt = LocalDateTime.of(2026, 3, 2, 8, 5),
                    aktivitaetId = id, faelligkeitId = null, belohnungId = null,
                    schwelle = null, notiz = "",
                ),
                BuchungEntity(
                    art = "EINLOESUNG", betrag = -4,
                    zeitpunkt = LocalDateTime.of(2026, 3, 2, 9, 0),
                    aktivitaetId = null, faelligkeitId = null, belohnungId = null,
                    schwelle = null, notiz = "",
                ),
            ),
        )

        assertEquals(6, db.buchungDao().kontostandJetzt())
    }

    @Test
    fun offene_Faelligkeiten_bis_Zeitpunkt_werden_gefunden() = runTest {
        val id = db.aktivitaetDao().einfuegen(aktivitaet())
        fun eintrag(stunde: Int, status: String) = FaelligkeitEntity(
            aktivitaetId = id,
            schluessel = "$id|2026-03-02|2026-03-02T$stunde:00",
            periodeVon = LocalDate.of(2026, 3, 2),
            periodeBis = LocalDate.of(2026, 3, 2),
            geplantFuer = LocalDateTime.of(2026, 3, 2, stunde, 0),
            sollAnzahl = 1, istAnzahl = 0, status = status,
            erledigtAm = null, warPuenktlich = false,
        )
        db.faelligkeitDao().anlegenFallsNeu(
            listOf(eintrag(8, "OFFEN"), eintrag(12, "ERLEDIGT"), eintrag(20, "OFFEN")),
        )

        val offen = db.faelligkeitDao().offeneBis(LocalDateTime.of(2026, 3, 2, 13, 0))

        assertEquals(1, offen.size)
        assertEquals(LocalDateTime.of(2026, 3, 2, 8, 0), offen.single().geplantFuer)
    }

    @Test
    fun Zaehlziele_stehen_hinter_den_terminierten_Eintraegen_des_Tages() = runTest {
        val id = db.aktivitaetDao().einfuegen(aktivitaet())
        val tag = LocalDate.of(2026, 3, 2)
        // Inserted in order: 20:00, null, 08:00
        db.faelligkeitDao().anlegenFallsNeu(
            listOf(
                FaelligkeitEntity(
                    aktivitaetId = id,
                    schluessel = "$id|$tag|${tag}T20:00",
                    periodeVon = tag,
                    periodeBis = tag,
                    geplantFuer = LocalDateTime.of(2026, 3, 2, 20, 0),
                    sollAnzahl = 1, istAnzahl = 0, status = "OFFEN",
                    erledigtAm = null, warPuenktlich = false,
                ),
                FaelligkeitEntity(
                    aktivitaetId = id,
                    schluessel = "$id|$tag|count-goal",
                    periodeVon = tag,
                    periodeBis = tag,
                    geplantFuer = null,
                    sollAnzahl = 3, istAnzahl = 0, status = "OFFEN",
                    erledigtAm = null, warPuenktlich = false,
                ),
                FaelligkeitEntity(
                    aktivitaetId = id,
                    schluessel = "$id|$tag|${tag}T08:00",
                    periodeVon = tag,
                    periodeBis = tag,
                    geplantFuer = LocalDateTime.of(2026, 3, 2, 8, 0),
                    sollAnzahl = 1, istAnzahl = 0, status = "OFFEN",
                    erledigtAm = null, warPuenktlich = false,
                ),
            ),
        )

        val eintraege = db.faelligkeitDao().fuerZeitraum(tag, tag)

        assertEquals(3, eintraege.size)
        assertEquals(LocalDateTime.of(2026, 3, 2, 8, 0), eintraege[0].geplantFuer)
        assertEquals(LocalDateTime.of(2026, 3, 2, 20, 0), eintraege[1].geplantFuer)
        assertEquals(null, eintraege[2].geplantFuer)
    }

    @Test
    fun leeres_Journal_hat_Kontostand_null() = runTest {
        assertEquals(0, db.buchungDao().kontostandJetzt())
    }
}
