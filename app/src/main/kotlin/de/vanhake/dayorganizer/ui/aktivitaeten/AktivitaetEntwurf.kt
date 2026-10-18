package de.vanhake.dayorganizer.ui.aktivitaeten

import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Anstoss
import de.vanhake.dayorganizer.domain.model.SerienSchwelle
import de.vanhake.dayorganizer.domain.model.Zeitplan
import de.vanhake.dayorganizer.ui.theme.AKTIVITAET_SYMBOL_STANDARD
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

enum class ZeitplanTyp { FESTE_ZEITEN, INTERVALL, TAGESZIEL, WOCHENZIEL }

enum class EntwurfFehler {
    NAME_LEER,
    PUNKTE_NICHT_POSITIV,
    KEINE_ZEIT,
    KEIN_WOCHENTAG,
    ZIEL_NICHT_POSITIV,
    INTERVALL_UNGUELTIG,
}

/**
 * Bearbeitbarer Zwischenstand.
 *
 * Seit dem Redesign (Plan 5) kommen Punkte, Intervall und Zielanzahl aus Steppern und
 * Uhrzeiten aus einem Zeitwaehler -- halb getippte Zahlen oder unlesbare Uhrzeiten gibt
 * es nicht mehr, darum sind diese Felder typisiert. Die Felder, die der Bildschirm nicht
 * anbietet (Kulanz, Nachfassen, ...), bleiben Text und werden unveraendert durchgereicht.
 *
 * Die Bearbeitungsoperationen (punkteAendern, zeitHinzufuegen, ...) tragen die Regeln der
 * Bedienelemente, damit sie ohne Compose testbar sind.
 */
data class AktivitaetEntwurf(
    val id: Long = 0,
    val name: String = "",
    val notiz: String = "",
    val farbe: Int = 0xFF4CAF50.toInt(),
    val symbol: String = AKTIVITAET_SYMBOL_STANDARD,
    val basisPunkte: Int = 10,
    val typ: ZeitplanTyp = ZeitplanTyp.FESTE_ZEITEN,
    // Prototyp openEdit(0): eine neue Aktivitaet startet mit 08:00, damit "Feste Zeiten"
    // ohne weiteren Schritt speicherbar ist.
    val zeiten: List<LocalTime> = listOf(LocalTime.of(8, 0)),
    val wochentage: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val intervallStunden: Int = 3,
    val fensterVon: LocalTime = LocalTime.of(8, 0),
    val fensterBis: LocalTime = LocalTime.of(22, 0),
    val zielAnzahl: Int = 3,
    val anstoss: List<Anstoss> = emptyList(),
    val kulanzMinutenText: String = "60",
    val puenktlichkeitsBonusText: String = "5",
    val serienSchwellen: List<SerienSchwelle> = listOf(SerienSchwelle(7, 10)),
    val nachfassAnzahlText: String = "2",
    val nachfassAbstandText: String = "15",
    val aktiv: Boolean = true,
    val archiviertAm: LocalDate? = null,
    val erstelltAm: LocalDate? = null,
) {

    /** Fuenferschritte, nie unter [PUNKTE_MIN]. Ein geladener Altwert ausserhalb des Rasters
     * (etwa 7) springt beim ersten Schritt auf den naechsten Fuenfer in Schrittrichtung (10
     * bzw. 5), statt das krumme Raster mitzuschleppen. */
    fun punkteAendern(schritt: Int): AktivitaetEntwurf {
        val raster = if (schritt > 0) {
            Math.floorDiv(basisPunkte, PUNKTE_SCHRITT) * PUNKTE_SCHRITT
        } else {
            -Math.floorDiv(-basisPunkte, PUNKTE_SCHRITT) * PUNKTE_SCHRITT
        }
        return copy(basisPunkte = (raster + schritt * PUNKTE_SCHRITT).coerceAtLeast(PUNKTE_MIN))
    }

    /** coerceIn statt nur einer Untergrenze: der Prototyp begrenzt auf 12 Stunden. Ein
     * geladener Altwert darueber bleibt stehen, bis der Stepper bedient wird. */
    fun intervallAendern(schritt: Int): AktivitaetEntwurf =
        copy(intervallStunden = (intervallStunden + schritt).coerceIn(INTERVALL_MIN, INTERVALL_MAX))

    fun zielAendern(schritt: Int): AktivitaetEntwurf =
        copy(zielAnzahl = (zielAnzahl + schritt).coerceAtLeast(ZIEL_MIN))

    fun wochentagUmschalten(tag: DayOfWeek): AktivitaetEntwurf =
        copy(wochentage = if (tag in wochentage) wochentage - tag else wochentage + tag)

    /** Sortiert und ohne Doppelte -- dieselbe Uhrzeit zweimal ergaebe zwei identische
     * Faelligkeiten. */
    fun zeitHinzufuegen(zeit: LocalTime): AktivitaetEntwurf =
        copy(zeiten = (zeiten + zeit).distinct().sorted())

    fun zeitEntfernen(zeit: LocalTime): AktivitaetEntwurf = copy(zeiten = zeiten - zeit)

    fun pruefen(): List<EntwurfFehler> = buildList {
        if (name.isBlank()) add(EntwurfFehler.NAME_LEER)
        if (basisPunkte <= 0) add(EntwurfFehler.PUNKTE_NICHT_POSITIV)

        when (typ) {
            ZeitplanTyp.FESTE_ZEITEN -> {
                if (zeiten.isEmpty()) add(EntwurfFehler.KEINE_ZEIT)
                if (wochentage.isEmpty()) add(EntwurfFehler.KEIN_WOCHENTAG)
            }

            ZeitplanTyp.INTERVALL -> {
                if (intervallStunden <= 0 || !fensterVon.isBefore(fensterBis)) {
                    add(EntwurfFehler.INTERVALL_UNGUELTIG)
                }
                if (wochentage.isEmpty()) add(EntwurfFehler.KEIN_WOCHENTAG)
            }

            ZeitplanTyp.TAGESZIEL -> {
                if (zielAnzahl <= 0) add(EntwurfFehler.ZIEL_NICHT_POSITIV)
                if (wochentage.isEmpty()) add(EntwurfFehler.KEIN_WOCHENTAG)
            }

            ZeitplanTyp.WOCHENZIEL -> {
                if (zielAnzahl <= 0) add(EntwurfFehler.ZIEL_NICHT_POSITIV)
            }
        }
    }

    /** Auch fuer einen noch fehlerhaften Entwurf aufrufbar -- die Plan-Zusammenfassung im
     * Bildschirm zeigt den Zwischenstand live an. */
    fun zeitplan(): Zeitplan {
        val sortiert = zeiten.sorted()
        return when (typ) {
            ZeitplanTyp.FESTE_ZEITEN -> Zeitplan.FesteZeiten(sortiert, wochentage)
            ZeitplanTyp.INTERVALL -> Zeitplan.Intervall(intervallStunden, fensterVon, fensterBis, wochentage)
            ZeitplanTyp.TAGESZIEL -> Zeitplan.Tagesziel(zielAnzahl, wochentage, sortiert)
            ZeitplanTyp.WOCHENZIEL -> Zeitplan.Wochenziel(zielAnzahl, anstoss)
        }
    }

    /** Nur aufrufen, wenn [pruefen] leer ist. [heute] gilt nur, wenn der Entwurf noch kein
     * eigenes [erstelltAm] traegt -- ein neu angelegter Entwurf bekommt so das heutige Datum,
     * eine bestehende Aktivitaet behaelt ihr echtes Anlegedatum ueber jedes Speichern hinweg. */
    fun zuAktivitaet(heute: LocalDate): Aktivitaet = Aktivitaet(
        id = id,
        name = name.trim(),
        notiz = notiz.trim(),
        farbe = farbe,
        symbol = symbol,
        basisPunkte = basisPunkte,
        zeitplan = zeitplan(),
        kulanzMinuten = kulanzMinutenText.toIntOrNull() ?: 60,
        puenktlichkeitsBonus = puenktlichkeitsBonusText.toIntOrNull() ?: 0,
        serienSchwellen = serienSchwellen,
        nachfassAnzahl = nachfassAnzahlText.toIntOrNull() ?: 2,
        nachfassAbstandMinuten = nachfassAbstandText.toIntOrNull() ?: 15,
        erstelltAm = erstelltAm ?: heute,
        aktiv = aktiv,
        archiviertAm = archiviertAm,
    )

    companion object {
        const val PUNKTE_SCHRITT = 5
        const val PUNKTE_MIN = 5
        const val INTERVALL_MIN = 1
        const val INTERVALL_MAX = 12
        const val ZIEL_MIN = 1

        fun aus(aktivitaet: Aktivitaet): AktivitaetEntwurf {
            val basis = AktivitaetEntwurf(
                id = aktivitaet.id,
                name = aktivitaet.name,
                notiz = aktivitaet.notiz,
                farbe = aktivitaet.farbe,
                symbol = aktivitaet.symbol,
                basisPunkte = aktivitaet.basisPunkte,
                kulanzMinutenText = aktivitaet.kulanzMinuten.toString(),
                puenktlichkeitsBonusText = aktivitaet.puenktlichkeitsBonus.toString(),
                serienSchwellen = aktivitaet.serienSchwellen,
                nachfassAnzahlText = aktivitaet.nachfassAnzahl.toString(),
                nachfassAbstandText = aktivitaet.nachfassAbstandMinuten.toString(),
                aktiv = aktivitaet.aktiv,
                archiviertAm = aktivitaet.archiviertAm,
                erstelltAm = aktivitaet.erstelltAm,
            )
            return when (val plan = aktivitaet.zeitplan) {
                is Zeitplan.FesteZeiten -> basis.copy(
                    typ = ZeitplanTyp.FESTE_ZEITEN,
                    zeiten = plan.zeiten,
                    wochentage = plan.wochentage,
                )
                is Zeitplan.Intervall -> basis.copy(
                    typ = ZeitplanTyp.INTERVALL,
                    intervallStunden = plan.alleNStunden,
                    fensterVon = plan.von,
                    fensterBis = plan.bis,
                    wochentage = plan.wochentage,
                )
                is Zeitplan.Tagesziel -> basis.copy(
                    typ = ZeitplanTyp.TAGESZIEL,
                    zielAnzahl = plan.anzahl,
                    wochentage = plan.wochentage,
                    zeiten = plan.anstossZeiten,
                )
                is Zeitplan.Wochenziel -> basis.copy(
                    typ = ZeitplanTyp.WOCHENZIEL,
                    zielAnzahl = plan.anzahl,
                    anstoss = plan.anstoss,
                )
            }
        }
    }
}
