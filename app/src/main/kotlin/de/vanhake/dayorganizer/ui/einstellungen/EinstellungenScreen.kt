package de.vanhake.dayorganizer.ui.einstellungen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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

private fun Meldung.textId(): Int = when (this) {
    Meldung.EXPORT_FERTIG -> R.string.backup_export_fertig
    Meldung.EXPORT_FEHLGESCHLAGEN -> R.string.backup_export_fehlgeschlagen
    Meldung.IMPORT_FERTIG -> R.string.backup_import_fertig
    Meldung.IMPORT_FALSCHES_SCHEMA -> R.string.backup_falsches_schema
    Meldung.IMPORT_UNLESBAR -> R.string.backup_unlesbar
}

@Composable
fun EinstellungenScreen(viewModel: EinstellungenViewModel = hiltViewModel()) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()
    val meldung by viewModel.meldung.collectAsStateWithLifecycle()
    val kontext = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Bei jedem Sichtbarwerden neu pruefen: der Nutzer kommt aus den
    // Systemeinstellungen zurueck, und dann muss der Banner verschwinden.
    LifecycleResumeEffect(Unit) {
        viewModel.beimSichtbarwerden()
        onPauseOrDispose { }
    }

    // Einmalig zeigen, dann zuruecksetzen -- sonst erschiene dieselbe Meldung bei
    // jeder Rekomposition (z. B. Bildschirmdrehung) erneut.
    LaunchedEffect(meldung) {
        val aktuelleMeldung = meldung ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(kontext.getString(aktuelleMeldung.textId()))
        viewModel.meldungGezeigt()
    }

    val exportZiel = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let {
            kontext.contentResolver.openOutputStream(it)?.use { strom ->
                viewModel.exportieren(strom)
            }
        }
    }

    val importQuelle = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let {
            kontext.contentResolver.openInputStream(it)?.use { strom ->
                viewModel.importieren(strom)
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innen ->
        Column(
            modifier = Modifier.fillMaxWidth().padding(innen).padding(16.dp),
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

            Button(
                onClick = { exportZiel.launch("dayorganizer-backup.json") },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.backup_exportieren))
            }

            Button(
                onClick = { importQuelle.launch(arrayOf("application/json")) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.backup_importieren))
            }
        }
    }
}
