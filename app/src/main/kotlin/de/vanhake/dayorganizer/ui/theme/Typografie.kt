package de.vanhake.dayorganizer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import de.vanhake.dayorganizer.R

/**
 * Eine einzelne Variable-Font-Datei (Achse "wght"), fuenfmal mit unterschiedlichen
 * FontVariation.Settings eingebunden -- Compose waehlt darueber das jeweilige Gewicht,
 * ganz ohne fuenf separate Dateien.
 *
 * FontVariation.Settings ist in dieser Compose-Version als @ExperimentalTextApi markiert,
 * deshalb der Opt-in unten -- an der Architektur (eine Datei, fuenf Settings) aendert das nichts.
 */
@OptIn(ExperimentalTextApi::class)
val Manrope = FontFamily(
    Font(R.font.manrope, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.manrope, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.manrope, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.manrope, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.manrope, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
)

/**
 * Das Redesign setzt durchgehend deutlich schwerere Schnitte ein als die
 * Material-3-Standardtypografie (z. B. Abschnittstitel mit font-weight:800
 * statt der M3-Vorgabe 500) -- deshalb eine eigene Gewichtszuordnung je Rolle,
 * nicht nur ein Fontfamilientausch.
 */
fun manropeTypografie(): Typography {
    val basis = Typography()
    fun androidx.compose.ui.text.TextStyle.mitManrope(gewicht: FontWeight) =
        copy(fontFamily = Manrope, fontWeight = gewicht)

    return basis.copy(
        displayLarge = basis.displayLarge.mitManrope(FontWeight.ExtraBold),
        displayMedium = basis.displayMedium.mitManrope(FontWeight.ExtraBold),
        displaySmall = basis.displaySmall.mitManrope(FontWeight.ExtraBold),
        headlineLarge = basis.headlineLarge.mitManrope(FontWeight.ExtraBold),
        headlineMedium = basis.headlineMedium.mitManrope(FontWeight.Bold),
        headlineSmall = basis.headlineSmall.mitManrope(FontWeight.Bold),
        titleLarge = basis.titleLarge.mitManrope(FontWeight.Bold),
        titleMedium = basis.titleMedium.mitManrope(FontWeight.Bold),
        titleSmall = basis.titleSmall.mitManrope(FontWeight.ExtraBold),
        bodyLarge = basis.bodyLarge.mitManrope(FontWeight.SemiBold),
        bodyMedium = basis.bodyMedium.mitManrope(FontWeight.SemiBold),
        bodySmall = basis.bodySmall.mitManrope(FontWeight.Medium),
        labelLarge = basis.labelLarge.mitManrope(FontWeight.Bold),
        labelMedium = basis.labelMedium.mitManrope(FontWeight.Bold),
        labelSmall = basis.labelSmall.mitManrope(FontWeight.Bold),
    )
}
