package de.vanhake.dayorganizer.ui.belohnungen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.vanhake.dayorganizer.R
import de.vanhake.dayorganizer.domain.rechner.Hindernis

private fun EinloeseMeldung.textId(): Int = when (this) {
    EinloeseMeldung.ZU_WENIG_PUNKTE -> R.string.noch_punkte_noetig
    EinloeseMeldung.SERIE_ZU_KURZ -> R.string.serie_zu_kurz
    EinloeseMeldung.ZU_FRUEH -> R.string.noch_zu_frueh
    EinloeseMeldung.NICHT_MEHR_VORHANDEN -> R.string.einloese_nicht_mehr_vorhanden
    EinloeseMeldung.FEHLER -> R.string.einloese_fehlgeschlagen
}

@Composable
fun BelohnungenScreen(
    bearbeiten: (Long) -> Unit,
    viewModel: BelohnungenViewModel = hiltViewModel(),
) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()
    val meldung by viewModel.meldung.collectAsStateWithLifecycle()
    val kontext = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Einmalig zeigen, dann zuruecksetzen -- sonst erschiene dieselbe Meldung bei jeder
    // Rekomposition (z. B. Bildschirmdrehung) erneut. Gleiches Muster wie EinstellungenScreen.
    LaunchedEffect(meldung) {
        val aktuelleMeldung = meldung ?: return@LaunchedEffect
        val text = if (aktuelleMeldung == EinloeseMeldung.ZU_WENIG_PUNKTE) {
            kontext.getString(aktuelleMeldung.textId(), viewModel.meldungFehlendePunkte.value)
        } else {
            kontext.getString(aktuelleMeldung.textId())
        }
        snackbarHostState.showSnackbar(text)
        viewModel.meldungGezeigt()
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { bearbeiten(0L) }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.neue_belohnung))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    archivieren = { viewModel.archivieren(kachel.id) },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Kachel(
    kachel: BelohnungKachel,
    kontostand: Int,
    einloesen: () -> Unit,
    bearbeiten: () -> Unit,
    archivieren: () -> Unit,
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
            // FlowRow statt Row: drei Knoepfe passen bei 360 dp Breite und grosser
            // Schrift nicht in eine Zeile. In einer Row wurde "Archivieren" in den
            // Restplatz gequetscht, brach senkrecht um und blaehte die Karte auf die
            // halbe Bildschirmhoehe auf -- ohne selbst sichtbar zu sein. So auf dem
            // Emulator gesehen (wm density 480, font_scale 1.3).
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Button(
                    onClick = einloesen,
                    enabled = kachel.einloesbarkeit.moeglich && !kachel.wirdEingeloest,
                ) {
                    Text(stringResource(R.string.einloesen))
                }
                Button(onClick = bearbeiten) { Text(stringResource(R.string.bearbeiten)) }
                // Archivieren wirkt nur auf das archiviert-Flag und greift nicht in die
                // Punkte-Transaktion des Einloesens ein (siehe BelohnungEinloesen.loese):
                // anders als beim Einloesen-Knopf gibt es hier keinen Doppeltipp-Schaden,
                // der eine Sperre waehrend wirdEingeloest rechtfertigen wuerde.
                TextButton(onClick = archivieren) { Text(stringResource(R.string.archivieren)) }
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
