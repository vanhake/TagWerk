package de.vanhake.dayorganizer.ui.gemeinsam

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.vanhake.dayorganizer.R
import de.vanhake.dayorganizer.ui.theme.DayOrganizerFesteFarben
import de.vanhake.dayorganizer.ui.theme.DayOrganizerTheme

data class Hinweistoast(val text: String, val kannRueckgaengig: Boolean)

@Composable
fun HinweistoastLeiste(toast: Hinweistoast, rueckgaengig: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        // Live-Region wie bei der frueheren Snackbar: ohne sie sagt TalkBack den Toast
        // ("Backup gespeichert") nicht an.
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(16.dp),
        color = DayOrganizerTheme.farben.inverse,
        contentColor = DayOrganizerTheme.farben.onInverse,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(toast.text, modifier = Modifier.weight(1f))
            if (toast.kannRueckgaengig) {
                TextButton(onClick = rueckgaengig) {
                    Text(
                        stringResource(R.string.toast_rueckgaengig),
                        color = DayOrganizerFesteFarben.toastRueckgaengigInk,
                    )
                }
            }
        }
    }
}
