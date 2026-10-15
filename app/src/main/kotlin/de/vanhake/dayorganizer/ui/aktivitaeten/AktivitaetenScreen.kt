package de.vanhake.dayorganizer.ui.aktivitaeten

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.vanhake.dayorganizer.R

@Composable
fun AktivitaetenScreen(
    bearbeiten: (Long) -> Unit,
    viewModel: AktivitaetenViewModel = hiltViewModel(),
) {
    val liste by viewModel.zeilen.collectAsStateWithLifecycle()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { bearbeiten(0L) }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.neue_aktivitaet))
            }
        },
    ) { innen ->
        LazyColumn(
            modifier = Modifier.padding(innen).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(liste, key = { it.id }) { aktivitaet ->
                Card(Modifier.fillMaxWidth().clickable { bearbeiten(aktivitaet.id) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(aktivitaet.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.punkte_je_erledigung, aktivitaet.basisPunkte),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        TextButton(onClick = { viewModel.archivieren(aktivitaet.id) }) {
                            Text(stringResource(R.string.archivieren))
                        }
                    }
                }
            }
        }
    }
}
