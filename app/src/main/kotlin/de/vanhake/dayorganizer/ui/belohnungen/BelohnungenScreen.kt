package de.vanhake.dayorganizer.ui.belohnungen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.vanhake.dayorganizer.R
import de.vanhake.dayorganizer.domain.rechner.Hindernis

@Composable
fun BelohnungenScreen(
    bearbeiten: (Long) -> Unit,
    viewModel: BelohnungenViewModel = hiltViewModel(),
) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { bearbeiten(0L) }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.neue_belohnung))
            }
        },
    ) { innen ->
        LazyColumn(
            modifier = Modifier.padding(innen).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.punktestand, zustand.kontostand),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }

            // "kachel-"/"einloesung-" statt der nackten id: beide ids sind eigene,
            // je bei 1 beginnende Autoincrement-Folgen. Ohne Praefix teilen sich
            // die zwei items(...)-Aufrufe denselben Key-Namensraum der LazyColumn
            // und kollidieren sobald beide ids gleich sind -- Compose stuerzt dann ab.
            items(zustand.kacheln, key = { "kachel-${it.id}" }) { kachel ->
                Kachel(
                    kachel = kachel,
                    kontostand = zustand.kontostand,
                    einloesen = { viewModel.einloesen(kachel.id) },
                    bearbeiten = { bearbeiten(kachel.id) },
                )
            }

            if (zustand.einloesungen.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.einloese_historie),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                items(zustand.einloesungen, key = { "einloesung-${it.id}" }) { einloesung ->
                    Text(
                        stringResource(
                            R.string.einloesung_zeile,
                            einloesung.name,
                            einloesung.preisPunkte,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun Kachel(
    kachel: BelohnungKachel,
    kontostand: Int,
    einloesen: () -> Unit,
    bearbeiten: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(kachel.name, style = MaterialTheme.typography.titleMedium)
            if (kachel.beschreibung.isNotBlank()) {
                Text(kachel.beschreibung, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                stringResource(R.string.punktestand, kachel.preisPunkte),
                style = MaterialTheme.typography.bodyMedium,
            )
            LinearProgressIndicator(
                progress = {
                    (kontostand.toFloat() / kachel.preisPunkte).coerceIn(0f, 1f)
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = einloesen,
                    enabled = kachel.einloesbarkeit.moeglich && !kachel.wirdEingeloest,
                ) {
                    Text(stringResource(R.string.einloesen))
                }
                Button(onClick = bearbeiten) { Text(stringResource(R.string.bearbeiten)) }
            }
            val hinweis = when (kachel.einloesbarkeit.hindernis) {
                Hindernis.ZU_WENIG_PUNKTE -> stringResource(
                    R.string.noch_punkte_noetig,
                    kachel.einloesbarkeit.fehlendePunkte,
                )
                Hindernis.SERIE_ZU_KURZ -> stringResource(R.string.serie_zu_kurz)
                Hindernis.ZU_FRUEH -> stringResource(R.string.noch_zu_frueh)
                null -> null
            }
            if (hinweis != null) {
                Text(hinweis, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
