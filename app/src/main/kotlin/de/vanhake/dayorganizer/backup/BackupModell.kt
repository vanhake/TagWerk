package de.vanhake.dayorganizer.backup

import kotlinx.serialization.Serializable

/**
 * Alle Felder sind Text oder Zahl. Datums- und Zeitwerte werden als
 * ISO-8601-Text gefuehrt, damit die Datei ohne die App lesbar bleibt.
 */
@Serializable
data class BackupDatei(
    val schemaVersion: Int,
    val erstelltAm: String,
    val aktivitaeten: List<AktivitaetDto>,
    val faelligkeiten: List<FaelligkeitDto>,
    val buchungen: List<BuchungDto>,
    val belohnungen: List<BelohnungDto>,
    val einloesungen: List<EinloesungDto>,
    val einstellungen: EinstellungenDto,
)

@Serializable
data class AktivitaetDto(
    val id: Long,
    val name: String,
    val notiz: String,
    val farbe: Int,
    val symbol: String,
    val basisPunkte: Int,
    val kulanzMinuten: Int,
    val puenktlichkeitsBonus: Int,
    /** "7:10,30:50" */
    val serienSchwellen: String,
    val nachfassAnzahl: Int,
    val nachfassAbstandMinuten: Int,
    val aktiv: Boolean,
    val erstelltAm: String,
    val archiviertAm: String?,
    val zeitplanTyp: String,
    val zeitplanZeiten: String,
    val zeitplanWochentage: String,
    val zeitplanIntervallStunden: Int?,
    val zeitplanFensterVon: String?,
    val zeitplanFensterBis: String?,
    val zeitplanZielAnzahl: Int?,
    val zeitplanAnstoss: String,
)

@Serializable
data class FaelligkeitDto(
    val id: Long,
    val aktivitaetId: Long,
    val periodeVon: String,
    val periodeBis: String,
    val geplantFuer: String?,
    val sollAnzahl: Int,
    val istAnzahl: Int,
    val status: String,
    val erledigtAm: String?,
    val warPuenktlich: Boolean,
)

@Serializable
data class BuchungDto(
    val id: Long,
    val art: String,
    val betrag: Int,
    val zeitpunkt: String,
    val aktivitaetId: Long?,
    val faelligkeitId: Long?,
    val belohnungId: Long?,
    val schwelle: Int?,
    val notiz: String,
)

@Serializable
data class BelohnungDto(
    val id: Long,
    val name: String,
    val beschreibung: String,
    val preisPunkte: Int,
    val bedingungTyp: String?,
    val bedingungAktivitaetId: Long?,
    val bedingungPerioden: Int?,
    val bedingungDatum: String?,
    val archiviert: Boolean,
    /** Seit Schema 3. Der Standardwert macht Schema-2-Dateien lesbar. */
    val symbol: String = "redeem",
)

@Serializable
data class EinloesungDto(
    val id: Long,
    val belohnungId: Long,
    val name: String,
    val preisPunkte: Int,
    val zeitpunkt: String,
    /** Seit Schema 3. Der Standardwert macht Schema-2-Dateien lesbar. */
    val storniertAm: String? = null,
)

@Serializable
data class EinstellungenDto(
    val tagesbonusPunkte: Int,
    val standardKulanzMinuten: Int,
    val themeModus: String,
)
