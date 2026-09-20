package de.vanhake.dayorganizer.ui.belohnungen

import de.vanhake.dayorganizer.domain.model.Einloesung
import de.vanhake.dayorganizer.domain.rechner.Einloesbarkeit

data class BelohnungKachel(
    val id: Long,
    val name: String,
    val beschreibung: String,
    val preisPunkte: Int,
    val einloesbarkeit: Einloesbarkeit,
    // Waehrend eine Einloesung dieser Kachel laeuft true, siehe BelohnungenViewModel.einloesen.
    // Blendet den Knopf aus, bis die Transaktion durch ist -- sonst passt ein Doppeltipp
    // zweimal hinein, obwohl einmal getippt werden sollte.
    val wirdEingeloest: Boolean = false,
)

data class BelohnungenZustand(
    val kontostand: Int = 0,
    val kacheln: List<BelohnungKachel> = emptyList(),
    val einloesungen: List<Einloesung> = emptyList(),
)
