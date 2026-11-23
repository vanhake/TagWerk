package de.vanhake.dayorganizer.neuigkeiten

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.vanhake.dayorganizer.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Zeigt nach einem Update einmal die Infokarte mit den Neuigkeiten. */
@Composable
fun NeuigkeitenNachUpdate() {
    val kontext = LocalContext.current.applicationContext
    var offen by remember { mutableStateOf<NeuigkeitenSpeicher.Offen?>(null) }
    LaunchedEffect(Unit) { offen = withContext(Dispatchers.IO) { NeuigkeitenSpeicher.offen(kontext) } }
    offen?.let { o ->
        NeuigkeitenDialog(o.versionName, o.eintraege) {
            NeuigkeitenSpeicher.gesehen(kontext, o.neuesteId)
            offen = null
        }
    }
}

@Composable
fun NeuigkeitenDialog(versionName: String, eintraege: List<NeuigkeitenEintrag>, schliessen: () -> Unit) {
    AlertDialog(
        onDismissRequest = schliessen,
        icon = { Icon(Icons.Outlined.NewReleases, contentDescription = null) },
        title = {
            Text(
                if (versionName.isBlank()) stringResource(R.string.neuigkeiten_titel_ohne_version)
                else stringResource(R.string.neuigkeiten_titel, versionName),
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                eintraege.forEach { eintrag ->
                    if (eintraege.size > 1) {
                        Text(
                            Neuigkeiten.datumFormatieren(eintrag.datum),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    eintrag.aenderungen.forEach { aenderung ->
                        Row {
                            Text("•  ", style = MaterialTheme.typography.bodyMedium)
                            Text(aenderung, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = schliessen) { Text(stringResource(R.string.neuigkeiten_verstanden)) }
        },
    )
}
