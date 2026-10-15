package de.vanhake.dayorganizer.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Icecream
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.RamenDining
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Schluessel der Belohnungssymbole. Die ersten Eintraege nach "redeem" stammen aus dem
 * Redesign-Prototyp (Konstante rewards), der Rest ergaenzt auf zwei volle Reihen im Raster.
 * Der Standard muss Belohnung.symbol im Domaenenmodell entsprechen.
 */
val BELOHNUNG_SYMBOLE: List<String> = listOf(
    "redeem", "auto_stories", "movie", "ramen_dining", "spa", "local_cafe",
    "icecream", "sports_esports", "shopping_bag", "flight", "music_note", "celebration",
)

const val BELOHNUNG_SYMBOL_STANDARD = "redeem"

/** Unbekannte Schluessel (aelteres Backup, kuenftige Version) fallen auf das Standardsymbol
 * zurueck -- rein kosmetisch, wie bei aktivitaetSymbol. */
fun belohnungSymbol(schluessel: String): ImageVector = when (schluessel) {
    "auto_stories" -> Icons.Filled.AutoStories
    "movie" -> Icons.Filled.Movie
    "ramen_dining" -> Icons.Filled.RamenDining
    "spa" -> Icons.Filled.Spa
    "local_cafe" -> Icons.Filled.LocalCafe
    "icecream" -> Icons.Filled.Icecream
    "sports_esports" -> Icons.Filled.SportsEsports
    "shopping_bag" -> Icons.Filled.ShoppingBag
    "flight" -> Icons.Filled.Flight
    "music_note" -> Icons.Filled.MusicNote
    "celebration" -> Icons.Filled.Celebration
    else -> Icons.Filled.Redeem
}
