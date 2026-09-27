package de.vanhake.dayorganizer.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantische Farbrollen aus dem Redesign-Prototyp (DayOrganizer App.dc.html).
 * Die oklch(...)-Werte des Prototyps sind vorab in sRGB umgerechnet (Standard-
 * CSS-Color-4-Formel OKLCH -> OKLab -> linear sRGB -> sRGB), damit hier keine
 * Farbraum-Mathematik zur Laufzeit noetig ist.
 */
data class DayOrganizerFarben(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val ink: Color,
    val ink2: Color,
    val line: Color,
    val accent: Color,
    val onAccent: Color,
    val hero: Color,
    val accentSoft: Color,
    val onAccentSoft: Color,
    val coral: Color,
    val coralSoft: Color,
    val green: Color,
    val greenSoft: Color,
    val goldSoft: Color,
    val goldInk: Color,
    val inverse: Color,
    val onInverse: Color,
)

/** Feste Literale des Prototyps, die nicht vom Farbschema abhaengen (kein var() im CSS). */
object DayOrganizerFesteFarben {
    val heroUeberfaelligSolid = Color(0xFFD33C33)
    val toastRueckgaengigInk = Color(0xFFAFABFF)
    val heroChipflaeche = Color(0x2EFFFFFF)
    val heroGeisterlinie = Color(0x73FFFFFF)
}

val HellFarben = DayOrganizerFarben(
    bg = Color(0xFFF6F5F9), surface = Color(0xFFFFFFFF), surface2 = Color(0xFFEFEDF4),
    ink = Color(0xFF17151F), ink2 = Color(0xFF5F5B6E), line = Color(0x1417151F),
    accent = Color(0xFF6445DF), onAccent = Color(0xFFFFFFFF), hero = Color(0xFF5F3ED8),
    accentSoft = Color(0xFFE8E8FF), onAccentSoft = Color(0xFF452AA2),
    coral = Color(0xFFD33C33), coralSoft = Color(0xFFFFE7E3),
    green = Color(0xFF0E9254), greenSoft = Color(0xFFDAF7E3),
    goldSoft = Color(0xFFFFF0D0), goldInk = Color(0xFF8A5600),
    inverse = Color(0xFF1D1B25), onInverse = Color(0xFFFFFFFF),
)

val DunkelFarben = DayOrganizerFarben(
    bg = Color(0xFF100F15), surface = Color(0xFF1B1923), surface2 = Color(0xFF26232F),
    ink = Color(0xFFF3F1F8), ink2 = Color(0xFFA6A2B5), line = Color(0x17FFFFFF),
    accent = Color(0xFF968BFF), onAccent = Color(0xFF190F41), hero = Color(0xFF5437C0),
    accentSoft = Color(0xFF2D2957), onAccentSoft = Color(0xFFD2D2FF),
    coral = Color(0xFFF9786A), coralSoft = Color(0xFF4E201C),
    green = Color(0xFF55C483), greenSoft = Color(0xFF133621),
    goldSoft = Color(0xFF3E2D10), goldInk = Color(0xFFF5CB70),
    inverse = Color(0xFFF3F1F8), onInverse = Color(0xFF17151F),
)

val LocalDayOrganizerFarben = staticCompositionLocalOf { HellFarben }
