package de.vanhake.dayorganizer.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Schluessel exakt wie im Redesign-Prototyp (DayOrganizer App.dc.html, Konstante ICONS).
 * "pill" und "medication" haben im Material-Icons-Extended-Katalog kein eigenes
 * Tabletten-Symbol -- Medication/LocalPharmacy sind die naechstliegenden, optisch
 * unterscheidbaren Ersatzsymbole.
 */
val AKTIVITAET_SYMBOLE: List<String> = listOf(
    "pill", "medication", "water_drop", "fitness_center", "menu_book", "directions_walk",
    "self_improvement", "bedtime", "restaurant", "spa", "music_note", "cleaning_services",
)

const val AKTIVITAET_SYMBOL_STANDARD = "self_improvement"

/**
 * Ein unbekannter Schluessel (aelteres Backup, kuenftige Version) faellt auf das
 * Standardsymbol zurueck statt abzustuerzen -- anders als bei den Belohnungsbedingungen
 * ist das Symbol rein kosmetisch, kein Vertragsbruch.
 */
fun aktivitaetSymbol(schluessel: String): ImageVector = when (schluessel) {
    "pill" -> Icons.Filled.Medication
    "medication" -> Icons.Filled.LocalPharmacy
    "water_drop" -> Icons.Filled.WaterDrop
    "fitness_center" -> Icons.Filled.FitnessCenter
    "menu_book" -> Icons.Filled.MenuBook
    "directions_walk" -> Icons.Filled.DirectionsWalk
    "bedtime" -> Icons.Filled.Bedtime
    "restaurant" -> Icons.Filled.Restaurant
    "spa" -> Icons.Filled.Spa
    "music_note" -> Icons.Filled.MusicNote
    "cleaning_services" -> Icons.Filled.CleaningServices
    else -> Icons.Filled.SelfImprovement
}
