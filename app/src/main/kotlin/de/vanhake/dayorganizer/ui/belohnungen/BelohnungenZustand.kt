package de.vanhake.dayorganizer.ui.belohnungen

import de.vanhake.dayorganizer.domain.model.Einloesung
import de.vanhake.dayorganizer.domain.rechner.Einloesbarkeit

data class BelohnungKachel(
    val id: Long,
    val name: String,
    val beschreibung: String,
    val preisPunkte: Int,
    val einloesbarkeit: Einloesbarkeit,
)

data class BelohnungenZustand(
    val kontostand: Int = 0,
    val kacheln: List<BelohnungKachel> = emptyList(),
    val einloesungen: List<Einloesung> = emptyList(),
)
