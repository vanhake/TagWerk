package de.vanhake.dayorganizer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import de.vanhake.dayorganizer.data.ThemeModus

@Composable
fun DayOrganizerThema(themeModus: ThemeModus, inhalt: @Composable () -> Unit) {
    val farben = if (themeModus == ThemeModus.DUNKEL) DunkelFarben else HellFarben
    val materialFarben = if (themeModus == ThemeModus.DUNKEL) {
        darkColorScheme(
            primary = farben.accent, onPrimary = farben.onAccent,
            background = farben.bg, onBackground = farben.ink,
            surface = farben.surface, onSurface = farben.ink,
            surfaceVariant = farben.surface2, error = farben.coral,
        )
    } else {
        lightColorScheme(
            primary = farben.accent, onPrimary = farben.onAccent,
            background = farben.bg, onBackground = farben.ink,
            surface = farben.surface, onSurface = farben.ink,
            surfaceVariant = farben.surface2, error = farben.coral,
        )
    }

    CompositionLocalProvider(LocalDayOrganizerFarben provides farben) {
        MaterialTheme(colorScheme = materialFarben, typography = manropeTypografie(), content = inhalt)
    }
}

/** Zugriff auf die Redesign-Farbrollen, analog zu MaterialTheme.colorScheme. */
object DayOrganizerTheme {
    val farben: DayOrganizerFarben
        @Composable get() = LocalDayOrganizerFarben.current
}
