package de.vanhake.dayorganizer.domain.rechner

import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import java.time.LocalDate

/**
 * Serien werden in Perioden gezaehlt: Tage bei taeglichen Zeitplaenen,
 * Wochen bei Wochenzielen. Die Gruppierung ueber periodeVon macht beides gleich.
 */
data class SerienStand(val laenge: Int, val startPeriode: LocalDate?)

/**
 * Eine Periode zaehlt erst, wenn sie abgeschlossen ist -- also keine ihrer
 * Faelligkeiten mehr OFFEN ist. Wer morgens die Tablette nimmt und abends nicht,
 * hat den Tag noch nicht geschafft und darf morgens auch noch keinen Serienbonus
 * dafuer bekommen. Deshalb bricht ein VERPASST sofort, ein verbleibendes OFFEN
 * haelt die Periode neutral, und erst wenn nichts mehr offen ist zaehlt ein
 * ERLEDIGT die Periode.
 */
class SerienRechner {

    private enum class PeriodenErgebnis { ZAEHLT, NEUTRAL, BRICHT }

    fun aktuelleSerie(faelligkeiten: List<Faelligkeit>, bisPeriodeVon: LocalDate): Int =
        aktuelleSerieMitStart(faelligkeiten, bisPeriodeVon).laenge

    fun aktuelleSerieMitStart(
        faelligkeiten: List<Faelligkeit>,
        bisPeriodeVon: LocalDate,
    ): SerienStand {
        var laenge = 0
        var start: LocalDate? = null
        perioden(faelligkeiten)
            .filterKeys { !it.isAfter(bisPeriodeVon) }
            .toSortedMap(reverseOrder())
            .forEach { (periode, gruppe) ->
                when (bewerte(gruppe)) {
                    PeriodenErgebnis.BRICHT -> return SerienStand(laenge, start)
                    PeriodenErgebnis.ZAEHLT -> {
                        laenge++
                        start = periode
                    }
                    PeriodenErgebnis.NEUTRAL -> Unit
                }
            }
        return SerienStand(laenge, start)
    }

    fun laengsteSerie(faelligkeiten: List<Faelligkeit>): Int {
        var laufend = 0
        var beste = 0
        perioden(faelligkeiten).toSortedMap().forEach { (_, gruppe) ->
            when (bewerte(gruppe)) {
                PeriodenErgebnis.BRICHT -> laufend = 0
                PeriodenErgebnis.ZAEHLT -> {
                    laufend++
                    if (laufend > beste) beste = laufend
                }
                PeriodenErgebnis.NEUTRAL -> Unit
            }
        }
        return beste
    }

    private fun perioden(faelligkeiten: List<Faelligkeit>): Map<LocalDate, List<Faelligkeit>> =
        faelligkeiten.groupBy { it.periodeVon }

    private fun bewerte(gruppe: List<Faelligkeit>): PeriodenErgebnis = when {
        gruppe.any { it.status == FaelligkeitStatus.VERPASST } -> PeriodenErgebnis.BRICHT
        gruppe.any { it.status == FaelligkeitStatus.OFFEN } -> PeriodenErgebnis.NEUTRAL
        gruppe.any { it.status == FaelligkeitStatus.ERLEDIGT } -> PeriodenErgebnis.ZAEHLT
        else -> PeriodenErgebnis.NEUTRAL
    }
}
