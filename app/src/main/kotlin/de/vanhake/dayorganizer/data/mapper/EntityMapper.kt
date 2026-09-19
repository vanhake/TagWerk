package de.vanhake.dayorganizer.data.mapper

import de.vanhake.dayorganizer.data.db.AktivitaetEntity
import de.vanhake.dayorganizer.data.db.BuchungEntity
import de.vanhake.dayorganizer.data.db.FaelligkeitEntity
import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Buchung
import de.vanhake.dayorganizer.domain.model.BuchungsArt
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus

fun AktivitaetEntity.zuDomaene(): Aktivitaet = Aktivitaet(
    id = id,
    name = name,
    notiz = notiz,
    farbe = farbe,
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
