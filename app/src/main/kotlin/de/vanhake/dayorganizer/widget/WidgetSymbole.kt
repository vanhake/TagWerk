package de.vanhake.dayorganizer.widget

import androidx.annotation.DrawableRes
import de.vanhake.dayorganizer.R

/**
 * Glance kennt keine Compose-ImageVectors, daher gibt es fuer jeden Symbolschluessel ein
 * Vector-Drawable (dieselben Material-Glyphen wie in aktivitaetSymbol()). Ein unbekannter
 * Schluessel faellt wie in der App auf das Standardsymbol zurueck statt abzustuerzen.
 */
@DrawableRes
fun widgetSymbol(schluessel: String): Int = when (schluessel) {
    "pill" -> R.drawable.widget_symbol_pill
    "medication" -> R.drawable.widget_symbol_medication
    "water_drop" -> R.drawable.widget_symbol_water_drop
    "fitness_center" -> R.drawable.widget_symbol_fitness_center
    "menu_book" -> R.drawable.widget_symbol_menu_book
    "directions_walk" -> R.drawable.widget_symbol_directions_walk
    "bedtime" -> R.drawable.widget_symbol_bedtime
    "restaurant" -> R.drawable.widget_symbol_restaurant
    "spa" -> R.drawable.widget_symbol_spa
    "music_note" -> R.drawable.widget_symbol_music_note
    "cleaning_services" -> R.drawable.widget_symbol_cleaning_services
    else -> R.drawable.widget_symbol_self_improvement
}
