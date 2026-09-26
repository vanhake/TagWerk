package de.vanhake.dayorganizer.backup

import de.vanhake.dayorganizer.data.Einstellungen
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

    const val SCHEMA_VERSION = 1

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = false
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
     */
    fun ausText(text: String): BackupInhalt {
        val gefundeneVersion = schemaVersionVorabLesen(text)
        if (gefundeneVersion != null && gefundeneVersion != SCHEMA_VERSION) {
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
        ),
    )

    fun ausDatei(datei: BackupDatei): BackupInhalt {
        if (datei.schemaVersion != SCHEMA_VERSION) throw UnbekanntesSchema(datei.schemaVersion)

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
            ),
        )
    }

    private fun Aktivitaet.zuDto(): AktivitaetDto {
        val felder = ZeitplanKodierung.kodiere(zeitplan)
        return AktivitaetDto(
            id = id, name = name, notiz = notiz, farbe = farbe, basisPunkte = basisPunkte,
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
        )
    }

    private fun AktivitaetDto.zuDomaene() = Aktivitaet(
        id = id, name = name, notiz = notiz, farbe = farbe, basisPunkte = basisPunkte,
        zeitplan = ZeitplanKodierung.dekodiere(
            ZeitplanFelder(
                typ = zeitplanTyp, zeiten = zeitplanZeiten, wochentage = zeitplanWochentage,
                intervallStunden = zeitplanIntervallStunden,
                fensterVon = zeitplanFensterVon, fensterBis = zeitplanFensterBis,
                zielAnzahl = zeitplanZielAnzahl, anstoss = zeitplanAnstoss,
            ),
        ),
        kulanzMinuten = kulanzMinuten, puenktlichkeitsBonus = puenktlichkeitsBonus,
        serienSchwellen = ZeitplanKodierung.dekodiereSchwellen(serienSchwellen),
        nachfassAnzahl = nachfassAnzahl, nachfassAbstandMinuten = nachfassAbstandMinuten,
        aktiv = aktiv, erstelltAm = LocalDate.parse(erstelltAm),
        archiviertAm = archiviertAm?.let(LocalDate::parse),
    )

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
    )

    private fun BelohnungDto.zuDomaene() = Belohnung(
        id = id, name = name, beschreibung = beschreibung, preisPunkte = preisPunkte,
        bedingung = when (bedingungTyp) {
            "MINDESTSERIE" -> Belohnungsbedingung.MindestSerie(
                requireNotNull(bedingungAktivitaetId) {
                    "Belohnung $id: bedingungAktivitaetId fehlt fuer MINDESTSERIE"
                },
                requireNotNull(bedingungPerioden) {
                    "Belohnung $id: bedingungPerioden fehlt fuer MINDESTSERIE"
                },
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
    )

    private fun Einloesung.zuDto() = EinloesungDto(
        id = id, belohnungId = belohnungId, name = name,
        preisPunkte = preisPunkte, zeitpunkt = zeitpunkt.toString(),
    )

    private fun EinloesungDto.zuDomaene() = Einloesung(
        id = id, belohnungId = belohnungId, name = name,
        preisPunkte = preisPunkte, zeitpunkt = LocalDateTime.parse(zeitpunkt),
    )
}
