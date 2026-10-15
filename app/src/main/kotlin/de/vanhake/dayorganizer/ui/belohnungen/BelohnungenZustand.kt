package de.vanhake.dayorganizer.ui.belohnungen

import de.vanhake.dayorganizer.domain.model.Einloesung
import de.vanhake.dayorganizer.domain.rechner.Einloesbarkeit

data class BelohnungKachel(
    val id: Long,
    val name: String,
    val beschreibung: String,
    val symbol: String,
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

/**
 * Ergebnis eines gescheiterten Einloesens (oder Rueckgaengigs), vom Screen einmalig als
 * kurzer Hinweistoast gezeigt; ein Timer setzt sie danach auf null zurueck. Gleiches Muster wie Meldung in EinstellungenViewModel.
 * Ein Erfolg braucht keine eigene Meldung -- er erscheint als Toast mit Rückgängig
 * (BelohnungenViewModel.toast).
 */
enum class EinloeseMeldung {
    ZU_WENIG_PUNKTE,
    SERIE_ZU_KURZ,
    ZU_FRUEH,
    NICHT_MEHR_VORHANDEN,
    FEHLER,
    RUECKGAENGIG_FEHLGESCHLAGEN,
}

/** Stornierte Einloesungen bleiben fuer Backup und Nachvollziehbarkeit in der Datenbank,
 * gehoeren aber nicht in die sichtbare Historie. */
fun List<Einloesung>.sichtbare(): List<Einloesung> = filter { it.storniertAm == null }
