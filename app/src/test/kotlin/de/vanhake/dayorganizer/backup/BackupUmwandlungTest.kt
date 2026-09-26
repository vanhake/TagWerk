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
import kotlinx.serialization.SerializationException
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

    // Review-Fund W2: die Importvorschau zeigt, von wann das Backup ist -- dafuer muss
    // ausDatei() den Zeitpunkt aus der Datei zurueckgeben, nicht nur die Listen.
    @Test
    fun `erstelltAm wird beim Einlesen mitgeliefert`() {
        val erstelltAm = LocalDateTime.of(2026, 3, 2, 21, 0)
        val inhalt = BackupUmwandlung.ausDatei(
            BackupUmwandlung.zuDatei(
                erstelltAm = erstelltAm,
                aktivitaeten = listOf(aktivitaet), faelligkeiten = listOf(faelligkeit),
                buchungen = listOf(buchung), belohnungen = listOf(belohnung),
                einloesungen = listOf(einloesung),
                einstellungen = Einstellungen(tagesbonusPunkte = 25, standardKulanzMinuten = 45),
            ),
        )

        assertEquals(erstelltAm, inhalt.erstelltAm)
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

    // --- Fund 1: Rundlauf mit jedem Feld abweichend vom Standardwert -----------------------
    //
    // Der obige Rundlauftest nutzt fuer viele Felder genau den Kotlin-Standardwert
    // (aktiv = true, archiviertAm = null, archiviert = false, belohnungId = null,
    // notiz = ""). Liesse `zuDomaene()` eines dieser Felder weg, wuerde Kotlin den
    // Standardwert einsetzen und der Test bliebe trotzdem gruen. Hier weicht daher
    // jedes einzelne Feld jedes Modells vom Standardwert ab.

    private val archivierteAktivitaet = Aktivitaet(
        id = 21, name = "Sport", notiz = "dreimal woechentlich", farbe = 7, basisPunkte = 20,
        zeitplan = Zeitplan.FesteZeiten(listOf(LocalTime.of(18, 0)), setOf(DayOfWeek.WEDNESDAY)),
        kulanzMinuten = 90, puenktlichkeitsBonus = 15,
        serienSchwellen = listOf(SerienSchwelle(7, 10), SerienSchwelle(30, 50)),
        nachfassAnzahl = 4, nachfassAbstandMinuten = 30,
        aktiv = false,
        erstelltAm = LocalDate.of(2025, 5, 1),
        archiviertAm = LocalDate.of(2026, 2, 1),
    )

    private val zielfaelligkeitOhneGeplantFuer = Faelligkeit(
        id = 22, aktivitaetId = 21,
        periodeVon = LocalDate.of(2026, 4, 1), periodeBis = LocalDate.of(2026, 4, 7),
        geplantFuer = null, sollAnzahl = 3, istAnzahl = 2,
        status = FaelligkeitStatus.AUSGELASSEN,
        erledigtAm = LocalDateTime.of(2026, 4, 7, 21, 0), warPuenktlich = true,
    )

    private val buchungMitBelohnungsbezug = Buchung(
        id = 23, art = BuchungsArt.EINLOESUNG, betrag = -200,
        zeitpunkt = LocalDateTime.of(2026, 4, 7, 19, 30),
        belohnungId = 3, schwelle = 7, notiz = "Kinokarte eingeloest",
    )

    private val archivierteBelohnungMitFruehestensAb = Belohnung(
        id = 24, name = "Wochenendtrip", beschreibung = "zwei Naechte", preisPunkte = 500,
        bedingung = Belohnungsbedingung.FruehestensAb(LocalDate.of(2026, 6, 1)),
        archiviert = true,
    )

    private val archivierteBelohnungOhneBedingung = Belohnung(
        id = 25, name = "Fruehstueck im Bett", beschreibung = "einmalig", preisPunkte = 50,
        bedingung = null,
        archiviert = true,
    )

    private val zweiteEinloesung = Einloesung(
        id = 26, belohnungId = 24, name = "Wochenendtrip", preisPunkte = 500,
        zeitpunkt = LocalDateTime.of(2026, 6, 5, 10, 0),
    )

    private val abweichendeEinstellungen = Einstellungen(tagesbonusPunkte = 99, standardKulanzMinuten = 120)

    @Test
    fun `jedes Feld weicht vom Standardwert ab und uebersteht den Rundlauf`() {
        val datei = BackupUmwandlung.zuDatei(
            erstelltAm = LocalDateTime.of(2026, 4, 7, 21, 0),
            aktivitaeten = listOf(archivierteAktivitaet),
            faelligkeiten = listOf(zielfaelligkeitOhneGeplantFuer),
            buchungen = listOf(buchungMitBelohnungsbezug),
            belohnungen = listOf(archivierteBelohnungMitFruehestensAb, archivierteBelohnungOhneBedingung),
            einloesungen = listOf(zweiteEinloesung),
            einstellungen = abweichendeEinstellungen,
        )
        val json = Json.encodeToString(BackupDatei.serializer(), datei)
        val zurueck = BackupUmwandlung.ausDatei(Json.decodeFromString(BackupDatei.serializer(), json))

        assertEquals(listOf(archivierteAktivitaet), zurueck.aktivitaeten)
        assertEquals(listOf(zielfaelligkeitOhneGeplantFuer), zurueck.faelligkeiten)
        assertEquals(listOf(buchungMitBelohnungsbezug), zurueck.buchungen)
        assertEquals(
            listOf(archivierteBelohnungMitFruehestensAb, archivierteBelohnungOhneBedingung),
            zurueck.belohnungen,
        )
        assertEquals(listOf(zweiteEinloesung), zurueck.einloesungen)
        assertEquals(abweichendeEinstellungen, zurueck.einstellungen)
    }

    // --- Fund 2: Die Versionspruefung muss vor dem vollstaendigen Einlesen stattfinden -----

    /**
     * Baut einen minimalen, aber vollstaendigen Backup-Text mit der gegebenen [schemaVersion].
     * Mit [mitUnbekanntemZusatzfeld] traegt der Text ein Feld, das keine bekannte DTO-Eigenschaft
     * ist -- genau der Fall, an dem `ignoreUnknownKeys = false` beim vollstaendigen Einlesen
     * scheitern wuerde, wenn die Versionspruefung nicht zuerst kaeme.
     */
    private fun backupText(schemaVersion: String, mitUnbekanntemZusatzfeld: Boolean = false): String {
        val zusatzfeld = if (mitUnbekanntemZusatzfeld) {
            ",\n  \"neuesFeldAusEinerKuenftigenVersion\": \"wert\""
        } else {
            ""
        }
        return """
        {
          "schemaVersion": $schemaVersion,
          "erstelltAm": "2026-03-02T21:00",
          "aktivitaeten": [],
          "faelligkeiten": [],
          "buchungen": [],
          "belohnungen": [],
          "einloesungen": [],
          "einstellungen": { "tagesbonusPunkte": 15, "standardKulanzMinuten": 60 }$zusatzfeld
        }
        """.trimIndent()
    }

    @Test
    fun `eine Version-2-Datei mit unbekanntem Zusatzfeld gilt als neuere Version, nicht als beschaedigt`() {
        val text = backupText(schemaVersion = "2", mitUnbekanntemZusatzfeld = true)

        val fehler = assertFailsWith<UnbekanntesSchema> { BackupUmwandlung.ausText(text) }

        assertEquals(2, fehler.gefunden)
    }

    @Test
    fun `die Schemaversion 0 wird als unbekannt erkannt`() {
        val fehler = assertFailsWith<UnbekanntesSchema> {
            BackupUmwandlung.ausText(backupText(schemaVersion = "0"))
        }

        assertEquals(0, fehler.gefunden)
    }

    @Test
    fun `eine Schemaversion kleiner als SCHEMA_VERSION wird abgelehnt und nicht nur eine groessere`() {
        val kleinereVersion = BackupUmwandlung.SCHEMA_VERSION - 1

        val fehler = assertFailsWith<UnbekanntesSchema> {
            BackupUmwandlung.ausText(backupText(schemaVersion = kleinereVersion.toString()))
        }

        assertEquals(kleinereVersion, fehler.gefunden)
    }

    @Test
    fun `eine Datei ganz ohne Schemaversion wird nicht als UnbekanntesSchema gemeldet`() {
        val textOhneSchemaVersion = """
            {
              "erstelltAm": "2026-03-02T21:00",
              "aktivitaeten": [],
              "faelligkeiten": [],
              "buchungen": [],
              "belohnungen": [],
              "einloesungen": [],
              "einstellungen": { "tagesbonusPunkte": 15, "standardKulanzMinuten": 60 }
            }
        """.trimIndent()

        // Wichtig: hier wird SerializationException erwartet, nicht UnbekanntesSchema --
        // ohne erkennbare Version ist der Text schlicht kein DayOrganizer-Backup.
        assertFailsWith<SerializationException> { BackupUmwandlung.ausText(textOhneSchemaVersion) }
    }

    // --- Fund 3: feste Beispieldatei als Vertrag fuer Schema 1 -----------------------------
    //
    // Aendert sich diese Datei (umbenanntes DTO-Feld, andere Enum-Konstante, andere
    // ZeitplanKodierung-Texte, ...), werden bestehende Backups von Anwendern unlesbar.
    // Das ist dann keine Testanpassung, sondern eine neue Schemaversion.

    private val beispielDateiSchema1 = javaClass.classLoader
        ?.getResourceAsStream("backup/beispiel-schema-1.json")!!
        .bufferedReader()
        .readText()

    private fun beispielInhaltSchema1() = BackupUmwandlung.zuDatei(
        erstelltAm = LocalDateTime.of(2026, 3, 2, 21, 0),
        aktivitaeten = listOf(aktivitaet),
        faelligkeiten = listOf(faelligkeit),
        buchungen = listOf(buchung),
        belohnungen = listOf(belohnung),
        einloesungen = listOf(einloesung),
        einstellungen = Einstellungen(tagesbonusPunkte = 25, standardKulanzMinuten = 45),
    )

    @Test
    fun `die eingefrorene Beispieldatei fuer Schema 1 wird zu den erwarteten Domaenenobjekten gelesen`() {
        val inhalt = BackupUmwandlung.ausText(beispielDateiSchema1)

        assertEquals(listOf(aktivitaet), inhalt.aktivitaeten)
        assertEquals(listOf(faelligkeit), inhalt.faelligkeiten)
        assertEquals(listOf(buchung), inhalt.buchungen)
        assertEquals(listOf(belohnung), inhalt.belohnungen)
        assertEquals(listOf(einloesung), inhalt.einloesungen)
        assertEquals(Einstellungen(tagesbonusPunkte = 25, standardKulanzMinuten = 45), inhalt.einstellungen)
    }

    @Test
    fun `dieselben Domaenenobjekte erzeugen wieder genau die eingefrorene Beispieldatei`() {
        val text = BackupUmwandlung.zuText(beispielInhaltSchema1())

        assertEquals(beispielDateiSchema1.trim(), text.trim())
    }
}
