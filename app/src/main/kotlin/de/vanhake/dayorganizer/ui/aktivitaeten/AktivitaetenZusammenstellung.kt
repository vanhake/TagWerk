package de.vanhake.dayorganizer.ui.aktivitaeten

import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.Zeitplan
import de.vanhake.dayorganizer.domain.rechner.SerienRechner
import java.time.LocalDate

/** Eine Zeile der Aktivitaeten-Liste (Prototyp actRows). */
data class AktivitaetZeile(
    val id: Long,
    val name: String,
    val symbol: String,
    val planText: String,
    val basisPunkte: Int,
    val serie: Int,
    /** Wochenziele zaehlen ihre Serie in Wochen, alle anderen Zeitplaene in Tagen. */
    val serieInWochen: Boolean,
)

/** Rein und zustandslos, damit die Zeilenbildung ohne ViewModel/Dispatcher testbar ist --
 * gleiches Muster wie HeuteZusammenstellung. */
object AktivitaetenZusammenstellung {

    fun zeilen(
        aktivitaeten: List<Aktivitaet>,
        historien: Map<Long, List<Faelligkeit>>,
        heute: LocalDate,
        serienRechner: SerienRechner,
    ): List<AktivitaetZeile> = aktivitaeten.map { aktivitaet ->
        AktivitaetZeile(
            id = aktivitaet.id,
            name = aktivitaet.name,
            symbol = aktivitaet.symbol,
            planText = PlanText.aus(aktivitaet.zeitplan),
            basisPunkte = aktivitaet.basisPunkte,
            // Gleiche Berechnung wie StatistikRechner.fuerAktivitaet: heute als obere
            // Grenze; die laufende Periode zaehlt erst, wenn nichts mehr offen ist.
            serie = serienRechner.aktuelleSerie(historien[aktivitaet.id].orEmpty(), heute),
            serieInWochen = aktivitaet.zeitplan is Zeitplan.Wochenziel,
        )
    }
}
