package de.vanhake.dayorganizer.data.mapper

import de.vanhake.dayorganizer.data.db.AktivitaetEntity
import de.vanhake.dayorganizer.data.db.BelohnungEntity
import de.vanhake.dayorganizer.data.db.BuchungEntity
import de.vanhake.dayorganizer.data.db.EinloesungEntity
import de.vanhake.dayorganizer.data.db.FaelligkeitEntity
import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Belohnung
import de.vanhake.dayorganizer.domain.model.Belohnungsbedingung
import de.vanhake.dayorganizer.domain.model.Buchung
import de.vanhake.dayorganizer.domain.model.BuchungsArt
import de.vanhake.dayorganizer.domain.model.Einloesung
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus

fun AktivitaetEntity.zuDomaene(): Aktivitaet = Aktivitaet(
    id = id,
    name = name,
    notiz = notiz,
    farbe = farbe,
    symbol = symbol,
    basisPunkte = basisPunkte,
    zeitplan = ZeitplanKodierung.dekodiere(
        ZeitplanFelder(
            typ = zeitplanTyp,
            zeiten = zeitplanZeiten,
            wochentage = zeitplanWochentage,
            intervallStunden = zeitplanIntervallStunden,
            fensterVon = zeitplanFensterVon,
            fensterBis = zeitplanFensterBis,
            zielAnzahl = zeitplanZielAnzahl,
            anstoss = zeitplanAnstoss,
        ),
    ),
    kulanzMinuten = kulanzMinuten,
    puenktlichkeitsBonus = puenktlichkeitsBonus,
    serienSchwellen = ZeitplanKodierung.dekodiereSchwellen(serienSchwellen),
    nachfassAnzahl = nachfassAnzahl,
    nachfassAbstandMinuten = nachfassAbstandMinuten,
    aktiv = aktiv,
    erstelltAm = erstelltAm,
    archiviertAm = archiviertAm,
)

fun Aktivitaet.zuEntity(): AktivitaetEntity {
    val felder = ZeitplanKodierung.kodiere(zeitplan)
    return AktivitaetEntity(
        id = id,
        name = name,
        notiz = notiz,
        farbe = farbe,
        symbol = symbol,
        basisPunkte = basisPunkte,
        kulanzMinuten = kulanzMinuten,
        puenktlichkeitsBonus = puenktlichkeitsBonus,
        serienSchwellen = ZeitplanKodierung.kodiereSchwellen(serienSchwellen),
        nachfassAnzahl = nachfassAnzahl,
        nachfassAbstandMinuten = nachfassAbstandMinuten,
        aktiv = aktiv,
        erstelltAm = erstelltAm,
        archiviertAm = archiviertAm,
        zeitplanTyp = felder.typ,
        zeitplanZeiten = felder.zeiten,
        zeitplanWochentage = felder.wochentage,
        zeitplanIntervallStunden = felder.intervallStunden,
        zeitplanFensterVon = felder.fensterVon,
        zeitplanFensterBis = felder.fensterBis,
        zeitplanZielAnzahl = felder.zielAnzahl,
        zeitplanAnstoss = felder.anstoss,
    )
}

fun Faelligkeit.schluessel(): String =
    "$aktivitaetId|$periodeVon|${geplantFuer?.toString() ?: "ZIEL"}"

fun FaelligkeitEntity.zuDomaene(): Faelligkeit = Faelligkeit(
    id = id,
    aktivitaetId = aktivitaetId,
    periodeVon = periodeVon,
    periodeBis = periodeBis,
    geplantFuer = geplantFuer,
    sollAnzahl = sollAnzahl,
    istAnzahl = istAnzahl,
    status = FaelligkeitStatus.valueOf(status),
    erledigtAm = erledigtAm,
    warPuenktlich = warPuenktlich,
)

fun Faelligkeit.zuEntity(): FaelligkeitEntity = FaelligkeitEntity(
    id = id,
    aktivitaetId = aktivitaetId,
    schluessel = schluessel(),
    periodeVon = periodeVon,
    periodeBis = periodeBis,
    geplantFuer = geplantFuer,
    sollAnzahl = sollAnzahl,
    istAnzahl = istAnzahl,
    status = status.name,
    erledigtAm = erledigtAm,
    warPuenktlich = warPuenktlich,
)

fun BuchungEntity.zuDomaene(): Buchung = Buchung(
    id = id,
    art = BuchungsArt.valueOf(art),
    betrag = betrag,
    zeitpunkt = zeitpunkt,
    aktivitaetId = aktivitaetId,
    faelligkeitId = faelligkeitId,
    belohnungId = belohnungId,
    schwelle = schwelle,
    notiz = notiz,
)

fun Buchung.zuEntity(): BuchungEntity = BuchungEntity(
    id = id,
    art = art.name,
    betrag = betrag,
    zeitpunkt = zeitpunkt,
    aktivitaetId = aktivitaetId,
    faelligkeitId = faelligkeitId,
    belohnungId = belohnungId,
    schwelle = schwelle,
    notiz = notiz,
)

fun Belohnung.zuEntity(): BelohnungEntity = BelohnungEntity(
    id = id,
    name = name,
    beschreibung = beschreibung,
    preisPunkte = preisPunkte,
    bedingungTyp = when (bedingung) {
        null -> null
        is Belohnungsbedingung.MindestSerie -> "MINDESTSERIE"
        is Belohnungsbedingung.FruehestensAb -> "FRUEHESTENS_AB"
    },
    bedingungAktivitaetId = (bedingung as? Belohnungsbedingung.MindestSerie)?.aktivitaetId,
    bedingungPerioden = (bedingung as? Belohnungsbedingung.MindestSerie)?.perioden,
    bedingungDatum = (bedingung as? Belohnungsbedingung.FruehestensAb)?.datum,
    archiviert = archiviert,
    symbol = symbol,
)

/**
 * Strenge Variante fuer das Backup: dort soll ein kaputter Datensatz die Sicherung
 * scheitern lassen, statt still zu fehlen.
 */
fun BelohnungEntity.zuDomaene(): Belohnung =
    zuDomaeneOderNull() ?: error("Unlesbare Belohnung $id: Bedingung $bedingungTyp")

/**
 * null bei unbekanntem bedingungTyp oder fehlendem Pflichtfeld der Bedingung. Die
 * Lesepfade der App ueberspringen so einen Datensatz, statt dass ein einziger kaputter
 * Eintrag (etwa aus einer neueren App-Version) den ganzen Shop abstuerzen laesst.
 */
fun BelohnungEntity.zuDomaeneOderNull(): Belohnung? {
    val bedingung: Belohnungsbedingung? = when (bedingungTyp) {
        "MINDESTSERIE" -> Belohnungsbedingung.MindestSerie(
            aktivitaetId = bedingungAktivitaetId ?: return null,
            perioden = bedingungPerioden ?: return null,
        )
        "FRUEHESTENS_AB" -> Belohnungsbedingung.FruehestensAb(bedingungDatum ?: return null)
        null -> null
        else -> return null
    }
    return Belohnung(
        id = id,
        name = name,
        beschreibung = beschreibung,
        preisPunkte = preisPunkte,
        bedingung = bedingung,
        archiviert = archiviert,
        symbol = symbol,
    )
}

fun Einloesung.zuEntity(): EinloesungEntity = EinloesungEntity(
    id = id, belohnungId = belohnungId, name = name,
    preisPunkte = preisPunkte, zeitpunkt = zeitpunkt, storniertAm = storniertAm,
)

fun EinloesungEntity.zuDomaene(): Einloesung = Einloesung(
    id = id, belohnungId = belohnungId, name = name,
    preisPunkte = preisPunkte, zeitpunkt = zeitpunkt, storniertAm = storniertAm,
)
