package de.vanhake.dayorganizer.ui.einstellungen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.vanhake.dayorganizer.R
import de.vanhake.dayorganizer.platform.permission.einstellungsIntent
import de.vanhake.dayorganizer.ui.gemeinsam.WarnBanner

@Composable
fun EinstellungenScreen(viewModel: EinstellungenViewModel = hiltViewModel()) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()
    val kontext = LocalContext.current

    // Bei jedem Sichtbarwerden neu pruefen: der Nutzer kommt aus den
    // Systemeinstellungen zurueck, und dann muss der Banner verschwinden.
    LifecycleResumeEffect(Unit) {
        viewModel.beimSichtbarwerden()
        onPauseOrDispose { }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        zustand.hinweise.forEach { hinweis ->
            WarnBanner(
                hinweis = hinweis,
                beheben = {
                    ContextCompat.startActivity(kontext, hinweis.einstellungsIntent(kontext), null)
                },
            )
        }

        OutlinedTextField(
            value = zustand.tagesbonusText,
            onValueChange = viewModel::tagesbonusAendern,
            label = { Text(stringResource(R.string.feld_tagesbonus)) },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = zustand.standardKulanzText,
            onValueChange = viewModel::kulanzAendern,
            label = { Text(stringResource(R.string.feld_standard_kulanz)) },
            modifier = Modifier.fillMaxWidth(),
        )

        Button(onClick = { viewModel.speichern() }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.speichern))
        }
    }
}
