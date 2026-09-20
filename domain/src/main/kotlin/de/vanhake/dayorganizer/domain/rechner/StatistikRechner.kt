package de.vanhake.dayorganizer.domain.rechner

import de.vanhake.dayorganizer.domain.model.Buchung
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import java.time.LocalDate

data class AktivitaetStatistik(
    val aktivitaetId: Long,
    val erledigt: Int,
    val ausgelassen: Int,
    val verpasst: Int,
    /** erledigt / (erledigt + verpasst); bewusst Ausgelassenes zaehlt nicht dagegen. */
    val quote: Float,
    val aktuelleSerie: Int,
    val laengsteSerie: Int,
)

data class TagesBilanz(val tag: LocalDate, val erledigt: Int, val gesamt: Int)

class StatistikRechner(private val serienRechner: SerienRechner = SerienRechner()) {

    fun fuerAktivitaet(
        aktivitaetId: Long,
        faelligkeiten: List<Faelligkeit>,
        heute: LocalDate,
    ): AktivitaetStatistik {
        val eigene = faelligkeiten.filter { it.aktivitaetId == aktivitaetId }
        val erledigt = eigene.count { it.status == FaelligkeitStatus.ERLEDIGT }
        val verpasst = eigene.count { it.status == FaelligkeitStatus.VERPASST }
        val nenner = erledigt + verpasst

        return AktivitaetStatistik(
            aktivitaetId = aktivitaetId,
            erledigt = erledigt,
            ausgelassen = eigene.count { it.status == FaelligkeitStatus.AUSGELASSEN },
            verpasst = verpasst,
            quote = if (nenner == 0) 0f else erledigt.toFloat() / nenner,
            aktuelleSerie = serienRechner.aktuelleSerie(eigene, heute),
            laengsteSerie = serienRechner.laengsteSerie(eigene),
        )
    }

    fun tagesBilanzen(
        faelligkeiten: List<Faelligkeit>,
        von: LocalDate,
        bis: LocalDate,
    ): List<TagesBilanz> = generateSequence(von) { it.plusDays(1) }
        .takeWhile { !it.isAfter(bis) }
        .map { tag ->
            val desTages = faelligkeiten.filter {
                !it.periodeVon.isAfter(tag) && !it.periodeBis.isBefore(tag)
            }
            TagesBilanz(
                tag = tag,
                erledigt = desTages.count { it.status == FaelligkeitStatus.ERLEDIGT },
                gesamt = desTages.size,
            )
        }
        .toList()

    fun punkteVerlauf(
        buchungen: List<Buchung>,
        von: LocalDate,
        bis: LocalDate,
    ): List<Pair<LocalDate, Int>> {
        val jeTag = buchungen.groupBy { it.zeitpunkt.toLocalDate() }
            .mapValues { (_, liste) -> liste.sumOf { it.betrag } }

        var stand = buchungen
            .filter { it.zeitpunkt.toLocalDate().isBefore(von) }
            .sumOf { it.betrag }

        return generateSequence(von) { it.plusDays(1) }
            .takeWhile { !it.isAfter(bis) }
            .map { tag ->
                stand += jeTag[tag] ?: 0
                tag to stand
            }
            .toList()
    }
}
