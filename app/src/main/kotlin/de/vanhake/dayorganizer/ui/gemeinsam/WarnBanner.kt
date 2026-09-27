package de.vanhake.dayorganizer.ui.gemeinsam

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.vanhake.dayorganizer.R
import de.vanhake.dayorganizer.platform.permission.Hinweis
import de.vanhake.dayorganizer.ui.theme.DayOrganizerFormen
import de.vanhake.dayorganizer.ui.theme.DayOrganizerTheme

@Composable
fun WarnBanner(hinweis: Hinweis, beheben: () -> Unit, modifier: Modifier = Modifier) {
    val (titel, text) = when (hinweis) {
        Hinweis.BENACHRICHTIGUNGEN_FEHLEN ->
            R.string.warnung_benachrichtigungen_titel to R.string.warnung_benachrichtigungen_text
        Hinweis.EXAKTE_ALARME_FEHLEN ->
            R.string.warnung_exakte_alarme_titel to R.string.warnung_exakte_alarme_text
        Hinweis.AKKUOPTIMIERUNG_AKTIV ->
            R.string.warnung_akku_titel to R.string.warnung_akku_text
        Hinweis.WAECHTER_HAENGT ->
            R.string.warnung_waechter_titel to R.string.warnung_waechter_text
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = DayOrganizerFormen.zeile,
        colors = CardDefaults.cardColors(
            containerColor = DayOrganizerTheme.farben.goldSoft,
            contentColor = DayOrganizerTheme.farben.ink,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(titel), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
            Row {
                TextButton(onClick = beheben) { Text(stringResource(R.string.warnung_beheben)) }
            }
        }
    }
}
