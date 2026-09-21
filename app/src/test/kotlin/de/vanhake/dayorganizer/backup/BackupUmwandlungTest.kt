package de.vanhake.dayorganizer.backup

import de.vanhake.dayorganizer.data.Einstellungen
import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Belohnung
import de.vanhake.dayorganizer.domain.model.Belohnungsbedingung
import de.vanhake.dayorganizer.domain.model.Buchung
import de.vanhake.dayorganizer.domain.model.BuchungsArt
import de.vanhake.dayorganizer.domain.model.Einloesung
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import de.vanhake.dayorganizer.domain.model.SerienSchwelle
import de.vanhake.dayorganizer.domain.model.Zeitplan
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BackupUmwandlungTest {

    private val aktivitaet = Aktivitaet(
        id = 1, name = "Tabletten", notiz = "morgens und abends", farbe = 42, basisPunkte = 10,
        zeitplan = Zeitplan.FesteZeiten(
            listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
            setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY),
        ),
        kulanzMinuten = 45, puenktlichkeitsBonus = 5,
        serienSchwellen = listOf(SerienSchwelle(7, 10)),
        nachfassAnzahl = 2, nachfassAbstandMinuten = 15,
        erstelltAm = LocalDate.of(2026, 1, 1),
    )

    private val faelligkeit = Faelligkeit(
        id = 10, aktivitaetId = 1,
        periodeVon = LocalDate.of(2026, 3, 2), periodeBis = LocalDate.of(2026, 3, 2),
        geplantFuer = LocalDateTime.of(2026, 3, 2, 8, 0), sollAnzahl = 1, istAnzahl = 1,
        status = FaelligkeitStatus.ERLEDIGT,
        erledigtAm = LocalDateTime.of(2026, 3, 2, 8, 12), warPuenktlich = true,
    )

    private val buchung = Buchung(
        id = 5, art = BuchungsArt.SERIENBONUS, betrag = 10,
        zeitpunkt = LocalDateTime.of(2026, 3, 2, 8, 12),
        aktivitaetId = 1, faelligkeitId = 10, schwelle = 7,
    )

    private val belohnung = Belohnung(
        id = 3, name = "Kinoabend", beschreibung = "mit Popcorn", preisPunkte = 200,
        bedingung = Belohnungsbedingung.MindestSerie(1, 14),
    )

    private val einloesung = Einloesung(
        id = 2, belohnungId = 3, name = "Kinoabend", preisPunkte = 200,
        zeitpunkt = LocalDateTime.of(2026, 3, 2, 19, 0),
    )

    private fun datei() = BackupUmwandlung.zuDatei(
        erstelltAm = LocalDateTime.of(2026, 3, 2, 21, 0),
        aktivitaeten = listOf(aktivitaet),
        faelligkeiten = listOf(faelligkeit),
        buchungen = listOf(buchung),
        belohnungen = listOf(belohnung),
        einloesungen = listOf(einloesung),
        einstellungen = Einstellungen(tagesbonusPunkte = 25, standardKulanzMinuten = 45),
    )

    @Test
    fun `alle Daten ueberstehen den Rundlauf durch JSON`() {
        val json = Json.encodeToString(BackupDatei.serializer(), datei())
        val zurueck = BackupUmwandlung.ausDatei(Json.decodeFromString(BackupDatei.serializer(), json))

        assertEquals(listOf(aktivitaet), zurueck.aktivitaeten)
        assertEquals(listOf(faelligkeit), zurueck.faelligkeiten)
        assertEquals(listOf(buchung), zurueck.buchungen)
        assertEquals(listOf(belohnung), zurueck.belohnungen)
        assertEquals(listOf(einloesung), zurueck.einloesungen)
        assertEquals(25, zurueck.einstellungen.tagesbonusPunkte)
    }

    @Test
    fun `die Schemaversion wird mitgeschrieben`() {
        assertEquals(BackupUmwandlung.SCHEMA_VERSION, datei().schemaVersion)
    }

    @Test
    fun `eine unbekannte Schemaversion wird abgelehnt`() {
        val fremd = datei().copy(schemaVersion = 99)

        val fehler = assertFailsWith<UnbekanntesSchema> { BackupUmwandlung.ausDatei(fremd) }

        assertEquals(99, fehler.gefunden)
    }

    @Test
    fun `alle vier Zeitplantypen ueberstehen den Rundlauf`() {
        val plaene = listOf(
            Zeitplan.FesteZeiten(listOf(LocalTime.of(7, 30)), setOf(DayOfWeek.FRIDAY)),
            Zeitplan.Intervall(3, LocalTime.of(8, 0), LocalTime.of(22, 0), setOf(DayOfWeek.MONDAY)),
            Zeitplan.Tagesziel(3, setOf(DayOfWeek.SUNDAY), listOf(LocalTime.of(12, 0))),
            Zeitplan.Wochenziel(
                3,
                listOf(de.vanhake.dayorganizer.domain.model.Anstoss(DayOfWeek.TUESDAY, LocalTime.of(18, 0))),
            ),
        )

        plaene.forEach { plan ->
            val eine = aktivitaet.copy(zeitplan = plan)
            val datei = BackupUmwandlung.zuDatei(
                erstelltAm = LocalDateTime.of(2026, 3, 2, 21, 0),
                aktivitaeten = listOf(eine),
                faelligkeiten = emptyList(), buchungen = emptyList(),
                belohnungen = emptyList(), einloesungen = emptyList(),
                einstellungen = Einstellungen(),
            )
            val json = Json.encodeToString(BackupDatei.serializer(), datei)
            val zurueck = BackupUmwandlung.ausDatei(Json.decodeFromString(BackupDatei.serializer(), json))

            assertEquals(plan, zurueck.aktivitaeten.single().zeitplan)
        }
    }
}
