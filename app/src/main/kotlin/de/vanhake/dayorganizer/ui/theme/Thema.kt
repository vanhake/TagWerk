package de.vanhake.dayorganizer.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun DayOrganizerThema(dunkel: Boolean = isSystemInDarkTheme(), inhalt: @Composable () -> Unit) {
    val kontext = LocalContext.current
    val farben = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dunkel -> dynamicDarkColorScheme(kontext)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(kontext)
        dunkel -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = farben, content = inhalt)
}
