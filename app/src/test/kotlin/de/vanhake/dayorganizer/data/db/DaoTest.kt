package de.vanhake.dayorganizer.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
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

    private fun belohnung(
        name: String = "Kinoabend",
        preisPunkte: Int = 100,
        bedingungTyp: String? = null,
        bedingungAktivitaetId: Long? = null,
        bedingungPerioden: Int? = null,
        bedingungDatum: LocalDate? = null,
        archiviert: Boolean = false,
    ) = BelohnungEntity(
        name = name,
        beschreibung = "",
        preisPunkte = preisPunkte,
        bedingungTyp = bedingungTyp,
        bedingungAktivitaetId = bedingungAktivitaetId,
        bedingungPerioden = bedingungPerioden,
        bedingungDatum = bedingungDatum,
        archiviert = archiviert,
    )

    private fun einloesung(
        belohnungId: Long,
        name: String = "Kinoabend",
        preisPunkte: Int = 100,
        zeitpunkt: LocalDateTime,
    ) = EinloesungEntity(
        belohnungId = belohnungId,
        name = name,
        preisPunkte = preisPunkte,
        zeitpunkt = zeitpunkt,
    )

    @Test
    fun beobachteAktive_sortiert_archivierte_Belohnungen_aus_und_sortiert_nach_preisPunkte() = runTest {
        db.belohnungDao().einfuegen(belohnung(name = "Teuer", preisPunkte = 500))
        db.belohnungDao().einfuegen(belohnung(name = "Guenstig", preisPunkte = 50))
        db.belohnungDao().einfuegen(belohnung(name = "Archiviert", preisPunkte = 10, archiviert = true))

        val aktive = db.belohnungDao().beobachteAktive().first()

        assertEquals(listOf("Guenstig", "Teuer"), aktive.map { it.name })
    }

    @Test
    fun alle_liefert_aktive_vor_archivierten_und_je_nach_preisPunkte() = runTest {
        db.belohnungDao().einfuegen(belohnung(name = "TeuerArchiviert", preisPunkte = 900, archiviert = true))
        db.belohnungDao().einfuegen(belohnung(name = "GuenstigAktiv", preisPunkte = 20))
        db.belohnungDao().einfuegen(belohnung(name = "TeuerAktiv", preisPunkte = 400))
        db.belohnungDao().einfuegen(belohnung(name = "GuenstigArchiviert", preisPunkte = 5, archiviert = true))

        val alle = db.belohnungDao().alle()

        assertEquals(
            listOf("GuenstigAktiv", "TeuerAktiv", "GuenstigArchiviert", "TeuerArchiviert"),
            alle.map { it.name },
        )
    }

    @Test
    fun archivieren_trifft_nur_die_angegebene_Belohnung() = runTest {
        val idA = db.belohnungDao().einfuegen(belohnung(name = "A"))
        val idB = db.belohnungDao().einfuegen(belohnung(name = "B"))

        db.belohnungDao().archivieren(idA)

        assertEquals(true, db.belohnungDao().nachId(idA)?.archiviert)
        assertEquals(false, db.belohnungDao().nachId(idB)?.archiviert)
    }

    @Test
    fun Belohnung_ohne_Bedingung_uebersteht_den_Rundlauf() = runTest {
        val id = db.belohnungDao().einfuegen(belohnung(name = "Ohne Bedingung"))

        val gelesen = db.belohnungDao().nachId(id)

        assertEquals(null, gelesen?.bedingungTyp)
        assertEquals(null, gelesen?.bedingungAktivitaetId)
        assertEquals(null, gelesen?.bedingungPerioden)
        assertEquals(null, gelesen?.bedingungDatum)
    }

    @Test
    fun Belohnung_mit_MindestSerie_uebersteht_den_Rundlauf() = runTest {
        val id = db.belohnungDao().einfuegen(
            belohnung(
                name = "Serienbedingung",
                bedingungTyp = "MINDESTSERIE",
                bedingungAktivitaetId = 7,
                bedingungPerioden = 14,
            ),
        )

        val gelesen = db.belohnungDao().nachId(id)

        assertEquals("MINDESTSERIE", gelesen?.bedingungTyp)
        assertEquals(7L, gelesen?.bedingungAktivitaetId)
        assertEquals(14, gelesen?.bedingungPerioden)
        assertEquals(null, gelesen?.bedingungDatum)
    }

    @Test
    fun Belohnung_mit_FruehestensAb_uebersteht_den_Rundlauf() = runTest {
        val id = db.belohnungDao().einfuegen(
            belohnung(
                name = "Datumsbedingung",
                bedingungTyp = "FRUEHESTENS_AB",
                bedingungDatum = LocalDate.of(2026, 4, 1),
            ),
        )

        val gelesen = db.belohnungDao().nachId(id)

        assertEquals("FRUEHESTENS_AB", gelesen?.bedingungTyp)
        assertEquals(LocalDate.of(2026, 4, 1), gelesen?.bedingungDatum)
        assertEquals(null, gelesen?.bedingungAktivitaetId)
        assertEquals(null, gelesen?.bedingungPerioden)
    }

    @Test
    fun EinloesungEntity_uebersteht_den_Rundlauf() = runTest {
        val belohnungId = db.belohnungDao().einfuegen(belohnung(name = "Kinoabend", preisPunkte = 200))
        val zeitpunkt = LocalDateTime.of(2026, 3, 1, 8, 0)

        db.belohnungDao().einloesungEinfuegen(
            einloesung(belohnungId = belohnungId, name = "Kinoabend", preisPunkte = 200, zeitpunkt = zeitpunkt),
        )

        val gelesen = db.belohnungDao().alleEinloesungen().single()

        assertEquals(belohnungId, gelesen.belohnungId)
        assertEquals("Kinoabend", gelesen.name)
        assertEquals(200, gelesen.preisPunkte)
        assertEquals(zeitpunkt, gelesen.zeitpunkt)
    }

    @Test
    fun beobachteEinloesungen_liefert_absteigend_nach_zeitpunkt() = runTest {
        val belohnungId = db.belohnungDao().einfuegen(belohnung(name = "Kinoabend", preisPunkte = 200))
        db.belohnungDao().einloesungEinfuegen(
            einloesung(belohnungId = belohnungId, name = "Fruehe", zeitpunkt = LocalDateTime.of(2026, 3, 1, 8, 0)),
        )
        db.belohnungDao().einloesungEinfuegen(
            einloesung(belohnungId = belohnungId, name = "Spaete", zeitpunkt = LocalDateTime.of(2026, 3, 2, 8, 0)),
        )

        val einloesungen = db.belohnungDao().beobachteEinloesungen().first()

        assertEquals(listOf("Spaete", "Fruehe"), einloesungen.map { it.name })
    }

    // Zwei Einloesungen im selben Moment sind keine Theorie: wer zweimal kurz
    // hintereinander einloest, trifft dieselbe Minute, und LocalDateTime.toString
    // kuerzt Sekunden weg, wenn sie null sind.
    //
    // Ehrlichkeitshalber: dieser Test kann den id-Tiebreaker NICHT beweisen. Ohne
    // ihn besteht er ebenfalls -- per Mutationstest geprueft -- weil SQLite bei
    // diesem Tabellenscan zufaellig schon in id-absteigender Reihenfolge liefert.
    // Genau das ist der Grund fuer den Tiebreaker: "zufaellig richtig" ist keine
    // Zusage, und sobald ein Index oder eine andere Engine-Version den Plan
    // aendert, kippt die Reihenfolge stillschweigend. Der Test haelt die heute
    // beobachtete Ordnung fest, damit ein solcher Wechsel auffaellt; die Zusage
    // selbst steht in der @Query.
    @Test
    fun bei_gleichem_zeitpunkt_steht_die_neuere_Einloesung_oben() = runTest {
        val belohnungId = db.belohnungDao().einfuegen(belohnung(name = "Kinoabend", preisPunkte = 200))
        val gleich = LocalDateTime.of(2026, 3, 1, 8, 0)
        db.belohnungDao().einloesungEinfuegen(
            einloesung(belohnungId = belohnungId, name = "Zuerst", zeitpunkt = gleich),
        )
        db.belohnungDao().einloesungEinfuegen(
            einloesung(belohnungId = belohnungId, name = "Danach", zeitpunkt = gleich),
        )

        assertEquals(
            listOf("Danach", "Zuerst"),
            db.belohnungDao().beobachteEinloesungen().first().map { it.name },
        )
        assertEquals(
            listOf("Danach", "Zuerst"),
            db.belohnungDao().alleEinloesungen().map { it.name },
        )
    }
}
