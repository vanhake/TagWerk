package de.vanhake.dayorganizer.domain.rechner

import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Buchung
import de.vanhake.dayorganizer.domain.model.BuchungsArt
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.abs

/**
 * @param faelligkeitVorher Zustand der Faelligkeit vor dieser Erledigung.
 * @param serieNachher Serienlaenge, nachdem diese Erledigung verbucht ist.
 * @param gebuchteSchwellen Schwellenwerte, die in der laufenden Serie schon
 *   Bonus erzeugt haben. Bricht die Serie, leert der Aufrufer diese Menge.
 * @param tagesFaelligkeitenNachher alle Faelligkeiten des Tages im Zustand
 *   nach dieser Erledigung.
 */
data class ErledigungsKontext(
    val aktivitaet: Aktivitaet,
    val faelligkeitVorher: Faelligkeit,
    val zeitpunkt: LocalDateTime,
    val serieNachher: Int,
    val gebuchteSchwellen: Set<Int>,
    val tagesFaelligkeitenNachher: List<Faelligkeit>,
    val tagesbonusPunkte: Int,
    val tagesbonusBereitsGebucht: Boolean,
)

/** Einziger Ort im Projekt, an dem Punkte entstehen. */
class PunkteRechner {

    fun buchungen(kontext: ErledigungsKontext): List<Buchung> = buildList {
        val a = kontext.aktivitaet

        add(buchung(kontext, BuchungsArt.ERLEDIGUNG, a.basisPunkte))

        if (a.puenktlichkeitsBonus > 0 && istPuenktlich(kontext)) {
            add(buchung(kontext, BuchungsArt.PUENKTLICHKEITSBONUS, a.puenktlichkeitsBonus))
        }

        a.serienSchwellen
            .filter { it.perioden <= kontext.serieNachher && it.perioden !in kontext.gebuchteSchwellen }
            .sortedBy { it.perioden }
            .forEach { add(buchung(kontext, BuchungsArt.SERIENBONUS, it.bonus, schwelle = it.perioden)) }

        if (verdientTagesbonus(kontext)) {
            add(
                Buchung(
                    art = BuchungsArt.TAGESBONUS,
                    betrag = kontext.tagesbonusPunkte,
                    zeitpunkt = kontext.zeitpunkt,
                ),
            )
        }
    }

    fun istPuenktlich(kontext: ErledigungsKontext): Boolean {
        val soll = kontext.faelligkeitVorher.geplantFuer ?: return false
        val abweichung = abs(Duration.between(soll, kontext.zeitpunkt).toMinutes())
        return abweichung <= kontext.aktivitaet.kulanzMinuten
    }

    private fun verdientTagesbonus(kontext: ErledigungsKontext): Boolean =
        kontext.tagesbonusPunkte > 0 &&
            !kontext.tagesbonusBereitsGebucht &&
            kontext.tagesFaelligkeitenNachher.isNotEmpty() &&
            kontext.tagesFaelligkeitenNachher.all { it.status == FaelligkeitStatus.ERLEDIGT }

    private fun buchung(
        kontext: ErledigungsKontext,
        art: BuchungsArt,
        betrag: Int,
        schwelle: Int? = null,
    ) = Buchung(
        art = art,
        betrag = betrag,
        zeitpunkt = kontext.zeitpunkt,
        aktivitaetId = kontext.aktivitaet.id,
        faelligkeitId = kontext.faelligkeitVorher.id,
        schwelle = schwelle,
    )
}
