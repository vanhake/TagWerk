package de.vanhake.dayorganizer.ui.statistik

import de.vanhake.dayorganizer.domain.rechner.AktivitaetStatistik
import de.vanhake.dayorganizer.domain.rechner.TagesBilanz
import java.time.LocalDate

// Nicht AktivitaetZeile: so heisst schon die Zeile in ui.aktivitaeten.
data class StatistikZeile(
    val aktivitaetId: Long,
    val name: String,
    val symbol: String,
    val serieInWochen: Boolean,
    val statistik: AktivitaetStatistik,
)

data class StatistikZustand(
    val zeitraumTage: Int = 30,
    val bilanzen: List<TagesBilanz> = emptyList(),
    val zeilen: List<StatistikZeile> = emptyList(),
    val punkteVerlauf: List<Pair<LocalDate, Int>> = emptyList(),
    val gesamtQuote: Int? = null,
)
