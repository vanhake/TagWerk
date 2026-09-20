package de.vanhake.dayorganizer.ui.statistik

import de.vanhake.dayorganizer.domain.rechner.AktivitaetStatistik
import de.vanhake.dayorganizer.domain.rechner.TagesBilanz
import java.time.LocalDate

data class AktivitaetZeile(
    val aktivitaetId: Long,
    val name: String,
    val statistik: AktivitaetStatistik,
)

data class StatistikZustand(
    val zeitraumTage: Int = 30,
    val bilanzen: List<TagesBilanz> = emptyList(),
    val zeilen: List<AktivitaetZeile> = emptyList(),
    val punkteVerlauf: List<Pair<LocalDate, Int>> = emptyList(),
)
