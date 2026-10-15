package de.vanhake.dayorganizer.ui.gemeinsam

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.vanhake.dayorganizer.R
import de.vanhake.dayorganizer.ui.theme.DayOrganizerFormen
import de.vanhake.dayorganizer.ui.theme.DayOrganizerTheme

/** Plus/Minus-Pille aus dem Prototyp (Punkte, Intervall, Ziel; in Plan 6 Bonus/Kulanz).
 * Die Grenzen kennt der Aufrufer -- hier wird nur der jeweilige Knopf deaktiviert. */
@Composable
fun Stepper(
    wert: Int,
    verringern: () -> Unit,
    erhoehen: () -> Unit,
    modifier: Modifier = Modifier,
    verringernMoeglich: Boolean = true,
    erhoehenMoeglich: Boolean = true,
) {
    Row(
        modifier = modifier
            .clip(DayOrganizerFormen.pille)
            .background(DayOrganizerTheme.farben.surface2)
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        StepperKnopf(Icons.Filled.Remove, stringResource(R.string.stepper_weniger), verringernMoeglich, verringern)
        Text(
            wert.toString(),
            modifier = Modifier.widthIn(min = 36.dp),
            textAlign = TextAlign.Center,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = DayOrganizerTheme.farben.ink,
        )
        StepperKnopf(Icons.Filled.Add, stringResource(R.string.stepper_mehr), erhoehenMoeglich, erhoehen)
    }
}

@Composable
private fun StepperKnopf(symbol: ImageVector, beschreibung: String, aktiv: Boolean, aktion: () -> Unit) {
    val farben = DayOrganizerTheme.farben
    FilledIconButton(
        onClick = aktion,
        enabled = aktiv,
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = farben.surface,
            contentColor = farben.ink,
            disabledContainerColor = farben.surface,
            disabledContentColor = farben.ink2.copy(alpha = 0.4f),
        ),
    ) {
        Icon(symbol, contentDescription = beschreibung, modifier = Modifier.size(20.dp))
    }
}

/** Titel (+ optionaler Untertitel) links, Stepper rechts, ohne eigene Flaeche -- fuer
 * Karten, die mehrere Zeilen buendeln (Intervall-Karte). */
@Composable
fun StepperZeilenInhalt(
    titel: String,
    modifier: Modifier = Modifier,
    untertitel: String? = null,
    stepper: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(titel, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            untertitel?.let { Text(it, fontSize = 12.sp, color = DayOrganizerTheme.farben.ink2) }
        }
        stepper()
    }
}

/** [StepperZeilenInhalt] auf eigener Flaeche (Prototyp: border-radius 18px, var(--surface)). */
@Composable
fun StepperZeile(
    titel: String,
    modifier: Modifier = Modifier,
    untertitel: String? = null,
    stepper: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = DayOrganizerFormen.feld,
        color = DayOrganizerTheme.farben.surface,
        contentColor = DayOrganizerTheme.farben.ink,
    ) {
        StepperZeilenInhalt(titel = titel, untertitel = untertitel, stepper = stepper)
    }
}
