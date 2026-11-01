package de.vanhake.dayorganizer.backup

import de.vanhake.dayorganizer.data.Einstellungen
import de.vanhake.dayorganizer.data.ThemeModus
import de.vanhake.dayorganizer.data.mapper.ZeitplanFelder
import de.vanhake.dayorganizer.data.mapper.ZeitplanKodierung
import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Belohnung
import de.vanhake.dayorganizer.domain.model.Belohnungsbedingung
import de.vanhake.dayorganizer.domain.model.Buchung
import de.vanhake.dayorganizer.domain.model.BuchungsArt
import de.vanhake.dayorganizer.domain.model.Einloesung
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import de.vanhake.dayorganizer.domain.model.VerspaetungsRegel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.time.LocalDate
import java.time.LocalDateTime

class UnbekanntesSchema(val gefunden: Int) :
    Exception("Backup-Schema $gefunden wird von dieser Version nicht gelesen")

data class BackupInhalt(
    val erstelltAm: LocalDateTime,
    val aktivitaeten: List<Aktivitaet>,
    val faelligkeiten: List<Faelligkeit>,
    val buchungen: List<Buchung>,
    val belohnungen: List<Belohnung>,
    val einloesungen: List<Einloesung>,
    val einstellungen: Einstellungen,
)

object BackupUmwandlung {

    const val SCHEMA_VERSION = 5

    /** Schema 2 bis 4 bleiben lesbar: die mit Schema 3 und 4 neuen Felder haben
     * DTO-Standardwerte, die genau dem Zustand davor entsprechen (Schema 4 brachte nur die
     * Deadline der Einmalaufgaben). Schema 5 brachte die Verspaetungsregel; aeltere Dateien
     * bekommen dafuer die Vorgabe der App (keine Punkte nach dem Kulanzfenster), nicht das
     * fruehere Verhalten. Schema 1 kannte weder Aktivitaetensymbol noch Farbmodus ohne
     * Standardwert und bleibt abgelehnt. */
    val LESBARE_VERSIONEN: Set<Int> = setOf(2, 3, 4, SCHEMA_VERSION)

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = false
        // Sonst fehlten Felder mit DTO-Standardwert in der Datei, sobald sie ihm entsprechen --
        // die Datei waere dann je nach Inhalt mal vollstaendig, mal nicht.
        encodeDefaults = true
    }

    /** Wandelt eine [BackupDatei] in formatiertes JSON um -- fuer Export und Ablage. */
    fun zuText(datei: BackupDatei): String = json.encodeToString(BackupDatei.serializer(), datei)

    /**
     * Liest [text] als Backup ein. Prueft zuerst -- vor dem vollstaendigen Einlesen mit
     * `ignoreUnknownKeys = false` -- nur das Feld `schemaVersion`: Ein spaeteres Schema mit
     * neuen oder umbenannten Feldern soll als [UnbekanntesSchema] erkannt werden, statt am
     * strengen Parser zu scheitern und faelschlich als beschaedigt zu gelten. Fehlt das Feld
     * oder ist es keine Zahl, ist der Text kein DayOrganizer-Backup; das vollstaendige
     * Einlesen wirft dann eine gewoehnliche Ausnahme, die der Import als unlesbar meldet.
     * Aeltere, lesbare Schemata werden ueber DTO-Standardwerte aufgefuellt.
     */
    fun ausText(text: String): BackupInhalt {
        val gefundeneVersion = schemaVersionVorabLesen(text)
        if (gefundeneVersion != null && gefundeneVersion !in LESBARE_VERSIONEN) {
            throw UnbekanntesSchema(gefundeneVersion)
        }
        return ausDatei(json.decodeFromString(BackupDatei.serializer(), text))
    }

    private fun schemaVersionVorabLesen(text: String): Int? {
        val wurzel = Json.parseToJsonElement(text) as? JsonObject ?: return null
        return (wurzel["schemaVersion"] as? JsonPrimitive)?.intOrNull
    }

    fun zuDatei(
        erstelltAm: LocalDateTime,
        aktivitaeten: List<Aktivitaet>,
        faelligkeiten: List<Faelligkeit>,
        buchungen: List<Buchung>,
        belohnungen: List<Belohnung>,
        einloesungen: List<Einloesung>,
        einstellungen: Einstellungen,
    ) = BackupDatei(
        schemaVersion = SCHEMA_VERSION,
        erstelltAm = erstelltAm.toString(),
        aktivitaeten = aktivitaeten.map { it.zuDto() },
        faelligkeiten = faelligkeiten.map { it.zuDto() },
        buchungen = buchungen.map { it.zuDto() },
        belohnungen = belohnungen.map { it.zuDto() },
        einloesungen = einloesungen.map { it.zuDto() },
        einstellungen = EinstellungenDto(
            tagesbonusPunkte = einstellungen.tagesbonusPunkte,
            standardKulanzMinuten = einstellungen.standardKulanzMinuten,
            themeModus = einstellungen.themeModus.name,
            verspaetung = einstellungen.verspaetung.name,
        ),
    )

    /**
     * Wandelt [datei] in Domaenenobjekte um und wirft [IllegalArgumentException], wenn ein
     * Wert fachlich unmoeglich ist. Geprueft werden genau drei: Belohnungspreis, Perioden
     * einer Serienbedingung und Basispunkte einer Aktivitaet muessen positiv sein. Die
     * Editoren erzeugen nie 0 oder weniger, und jeder dieser Werte haette sichtbare Folgen
     * (Belohnung immer einloesbar, Serienbedingung immer erfuellt, Erledigung ohne Punkte).
     * Alle uebrigen Felder haben Standardwert-Fallbacks und brauchen keine Pruefung.
     */
    fun ausDatei(datei: BackupDatei): BackupInhalt {
        if (datei.schemaVersion !in LESBARE_VERSIONEN) throw UnbekanntesSchema(datei.schemaVersion)

        return BackupInhalt(
            // Fuer die Importvorschau (Review-Fund W2): der Nutzer soll sehen, von wann
            // das Backup ist, bevor er den aktuellen Bestand unwiderruflich ersetzt.
            erstelltAm = LocalDateTime.parse(datei.erstelltAm),
            aktivitaeten = datei.aktivitaeten.map { it.zuDomaene() },
            faelligkeiten = datei.faelligkeiten.map { it.zuDomaene() },
            buchungen = datei.buchungen.map { it.zuDomaene() },
            belohnungen = datei.belohnungen.map { it.zuDomaene() },
            einloesungen = datei.einloesungen.map { it.zuDomaene() },
            einstellungen = Einstellungen(
                tagesbonusPunkte = datei.einstellungen.tagesbonusPunkte,
                standardKulanzMinuten = datei.einstellungen.standardKulanzMinuten,
                // Ein unbekannter Modus-Text (kuenftige Version, beschaedigte Datei) faellt auf
                // HELL zurueck, statt den ganzen Import an einem kosmetischen Feld scheitern zu
                // lassen -- anders als bei Belohnungsbedingungen ist das hier kein Vertragsbruch.
                themeModus = runCatching { ThemeModus.valueOf(datei.einstellungen.themeModus) }
                    .getOrDefault(ThemeModus.HELL),
                // Wie beim Farbmodus: ein unbekannter Wert faellt auf die Vorgabe zurueck.
                verspaetung = runCatching { VerspaetungsRegel.valueOf(datei.einstellungen.verspaetung) }
                    .getOrDefault(VerspaetungsRegel.KEINE_PUNKTE),
            ),
        )
    }

    private fun Aktivitaet.zuDto(): AktivitaetDto {
        val felder = ZeitplanKodierung.kodiere(zeitplan)
        return AktivitaetDto(
            id = id, name = name, notiz = notiz, farbe = farbe, symbol = symbol,
            basisPunkte = basisPunkte,
            kulanzMinuten = kulanzMinuten, puenktlichkeitsBonus = puenktlichkeitsBonus,
            serienSchwellen = ZeitplanKodierung.kodiereSchwellen(serienSchwellen),
            nachfassAnzahl = nachfassAnzahl, nachfassAbstandMinuten = nachfassAbstandMinuten,
            aktiv = aktiv, erstelltAm = erstelltAm.toString(),
            archiviertAm = archiviertAm?.toString(),
            zeitplanTyp = felder.typ, zeitplanZeiten = felder.zeiten,
            zeitplanWochentage = felder.wochentage,
            zeitplanIntervallStunden = felder.intervallStunden,
            zeitplanFensterVon = felder.fensterVon, zeitplanFensterBis = felder.fensterBis,
            zeitplanZielAnzahl = felder.zielAnzahl, zeitplanAnstoss = felder.anstoss,
            zeitplanDeadline = felder.deadline,
        )
    }

    private fun AktivitaetDto.zuDomaene(): Aktivitaet {
        require(basisPunkte > 0) { "Aktivitaet $id: basisPunkte muss positiv sein" }
        return Aktivitaet(
            id = id, name = name, notiz = notiz, farbe = farbe, symbol = symbol,
            basisPunkte = basisPunkte,
            zeitplan = ZeitplanKodierung.dekodiere(
                ZeitplanFelder(
                    typ = zeitplanTyp, zeiten = zeitplanZeiten, wochentage = zeitplanWochentage,
                    intervallStunden = zeitplanIntervallStunden,
                    fensterVon = zeitplanFensterVon, fensterBis = zeitplanFensterBis,
                    zielAnzahl = zeitplanZielAnzahl, anstoss = zeitplanAnstoss,
                    deadline = zeitplanDeadline,
                ),
            ),
            kulanzMinuten = kulanzMinuten, puenktlichkeitsBonus = puenktlichkeitsBonus,
            serienSchwellen = ZeitplanKodierung.dekodiereSchwellen(serienSchwellen),
            nachfassAnzahl = nachfassAnzahl, nachfassAbstandMinuten = nachfassAbstandMinuten,
            aktiv = aktiv, erstelltAm = LocalDate.parse(erstelltAm),
            archiviertAm = archiviertAm?.let(LocalDate::parse),
        )
    }

    private fun Faelligkeit.zuDto() = FaelligkeitDto(
        id = id, aktivitaetId = aktivitaetId,
        periodeVon = periodeVon.toString(), periodeBis = periodeBis.toString(),
        geplantFuer = geplantFuer?.toString(), sollAnzahl = sollAnzahl, istAnzahl = istAnzahl,
        status = status.name, erledigtAm = erledigtAm?.toString(), warPuenktlich = warPuenktlich,
    )

    private fun FaelligkeitDto.zuDomaene() = Faelligkeit(
        id = id, aktivitaetId = aktivitaetId,
        periodeVon = LocalDate.parse(periodeVon), periodeBis = LocalDate.parse(periodeBis),
        geplantFuer = geplantFuer?.let(LocalDateTime::parse),
        sollAnzahl = sollAnzahl, istAnzahl = istAnzahl,
        status = FaelligkeitStatus.valueOf(status),
        erledigtAm = erledigtAm?.let(LocalDateTime::parse), warPuenktlich = warPuenktlich,
    )

    private fun Buchung.zuDto() = BuchungDto(
        id = id, art = art.name, betrag = betrag, zeitpunkt = zeitpunkt.toString(),
        aktivitaetId = aktivitaetId, faelligkeitId = faelligkeitId, belohnungId = belohnungId,
        schwelle = schwelle, notiz = notiz,
    )

    private fun BuchungDto.zuDomaene() = Buchung(
        id = id, art = BuchungsArt.valueOf(art), betrag = betrag,
        zeitpunkt = LocalDateTime.parse(zeitpunkt),
        aktivitaetId = aktivitaetId, faelligkeitId = faelligkeitId, belohnungId = belohnungId,
        schwelle = schwelle, notiz = notiz,
    )

    private fun Belohnung.zuDto() = BelohnungDto(
        id = id, name = name, beschreibung = beschreibung, preisPunkte = preisPunkte,
        bedingungTyp = when (bedingung) {
            null -> null
            is Belohnungsbedingung.MindestSerie -> "MINDESTSERIE"
            is Belohnungsbedingung.FruehestensAb -> "FRUEHESTENS_AB"
        },
        bedingungAktivitaetId = (bedingung as? Belohnungsbedingung.MindestSerie)?.aktivitaetId,
        bedingungPerioden = (bedingung as? Belohnungsbedingung.MindestSerie)?.perioden,
        bedingungDatum = (bedingung as? Belohnungsbedingung.FruehestensAb)?.datum?.toString(),
        archiviert = archiviert,
        symbol = symbol,
    )

    private fun BelohnungDto.zuDomaene(): Belohnung {
        require(preisPunkte > 0) { "Belohnung $id: preisPunkte muss positiv sein" }
        return Belohnung(
            id = id, name = name, beschreibung = beschreibung, preisPunkte = preisPunkte,
            bedingung = when (bedingungTyp) {
                "MINDESTSERIE" -> Belohnungsbedingung.MindestSerie(
                    requireNotNull(bedingungAktivitaetId) {
                        "Belohnung $id: bedingungAktivitaetId fehlt fuer MINDESTSERIE"
                    },
                    requireNotNull(bedingungPerioden) {
                        "Belohnung $id: bedingungPerioden fehlt fuer MINDESTSERIE"
                    }.also { require(it > 0) { "Belohnung $id: bedingungPerioden muss positiv sein" } },
                )
                "FRUEHESTENS_AB" -> Belohnungsbedingung.FruehestensAb(
                    LocalDate.parse(
                        requireNotNull(bedingungDatum) {
                            "Belohnung $id: bedingungDatum fehlt fuer FRUEHESTENS_AB"
                        },
                    ),
                )
                null -> null
                else -> error("Unbekannte Belohnungsbedingung: $bedingungTyp")
            },
            archiviert = archiviert,
            symbol = symbol,
        )
    }

    private fun Einloesung.zuDto() = EinloesungDto(
        id = id, belohnungId = belohnungId, name = name,
        preisPunkte = preisPunkte, zeitpunkt = zeitpunkt.toString(),
        storniertAm = storniertAm?.toString(),
    )

    private fun EinloesungDto.zuDomaene() = Einloesung(
        id = id, belohnungId = belohnungId, name = name,
        preisPunkte = preisPunkte, zeitpunkt = LocalDateTime.parse(zeitpunkt),
        storniertAm = storniertAm?.let(LocalDateTime::parse),
    )
}
