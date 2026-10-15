package de.vanhake.dayorganizer.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Eckenradien aus dem Redesign-Prototyp, px 1:1 als dp uebernommen. */
object DayOrganizerFormen {
    val heldenKarte = RoundedCornerShape(28.dp)
    val karte = RoundedCornerShape(24.dp)
    val zeile = RoundedCornerShape(20.dp)
    val kachel = RoundedCornerShape(14.dp)
    val knopf = RoundedCornerShape(16.dp)
    val pille = RoundedCornerShape(50)
    /** Stepper-Zeilen, Intervall-Karte, Speichern-Knopf (Prototyp border-radius:18px). */
    val feld = RoundedCornerShape(18.dp)
    /** Zeilen der Aktivitaeten-Liste (Prototyp border-radius:22px). */
    val listenKarte = RoundedCornerShape(22.dp)
    /** Uhrzeit-Chips (Prototyp border-radius:12px). */
    val chip = RoundedCornerShape(12.dp)
}
