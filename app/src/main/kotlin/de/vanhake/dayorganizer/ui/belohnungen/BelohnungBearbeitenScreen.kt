package de.vanhake.dayorganizer.ui.belohnungen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.vanhake.dayorganizer.R
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BelohnungBearbeitenScreen(
    id: Long,
    fertig: () -> Unit,
    viewModel: BelohnungBearbeitenViewModel = hiltViewModel(),
) {
    val entwurf by viewModel.entwurf.collectAsStateWithLifecycle()
    val fehler by viewModel.fehler.collectAsStateWithLifecycle()
    val auswahl by viewModel.auswahl.collectAsStateWithLifecycle()
    val bereich = rememberCoroutineScope()

    LaunchedEffect(id) { viewModel.laden(id) }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = entwurf.name,
            onValueChange = { neu -> viewModel.aendern { it.copy(name = neu) } },
            label = { Text(stringResource(R.string.feld_name)) },
            isError = BelohnungFehler.NAME_LEER in fehler,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = entwurf.beschreibung,
            onValueChange = { neu -> viewModel.aendern { it.copy(beschreibung = neu) } },
            label = { Text(stringResource(R.string.feld_beschreibung)) },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = entwurf.preisText,
            onValueChange = { neu -> viewModel.aendern { it.copy(preisText = neu) } },
            label = { Text(stringResource(R.string.feld_preis)) },
            isError = BelohnungFehler.PREIS_NICHT_POSITIV in fehler,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(stringResource(R.string.feld_bedingung), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BedingungTyp.entries.forEach { typ ->
                FilterChip(
                    selected = entwurf.bedingungTyp == typ,
                    onClick = { viewModel.aendern { it.copy(bedingungTyp = typ) } },
                    label = {
                        Text(
                            stringResource(
                                when (typ) {
                                    BedingungTyp.KEINE -> R.string.bedingung_keine
                                    BedingungTyp.MINDESTSERIE -> R.string.bedingung_serie
                                    BedingungTyp.FRUEHESTENS_AB -> R.string.bedingung_datum
                                },
                            ),
                        )
                    },
                )
            }
        }

        when (entwurf.bedingungTyp) {
            BedingungTyp.KEINE -> Unit

            BedingungTyp.MINDESTSERIE -> {
                Text(
                    stringResource(R.string.feld_bedingung_aktivitaet),
                    style = MaterialTheme.typography.titleSmall,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    auswahl.forEach { aktivitaet ->
                        FilterChip(
                            selected = entwurf.bedingungAktivitaetId == aktivitaet.id,
                            onClick = {
                                viewModel.aendern { it.copy(bedingungAktivitaetId = aktivitaet.id) }
                            },
                            label = { Text(aktivitaet.name) },
                        )
                    }
                }
                OutlinedTextField(
                    value = entwurf.bedingungPeriodenText,
                    onValueChange = { neu ->
                        viewModel.aendern { it.copy(bedingungPeriodenText = neu) }
                    },
                    label = { Text(stringResource(R.string.feld_bedingung_perioden)) },
                    isError = BelohnungFehler.SERIE_NICHT_POSITIV in fehler,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            BedingungTyp.FRUEHESTENS_AB -> OutlinedTextField(
                value = entwurf.bedingungDatum?.toString().orEmpty(),
                onValueChange = { neu ->
                    val datum = runCatching { LocalDate.parse(neu.trim()) }.getOrNull()
                    viewModel.aendern { it.copy(bedingungDatum = datum) }
                },
                label = { Text(stringResource(R.string.feld_bedingung_datum)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Button(
            onClick = { bereich.launch { if (viewModel.speichern()) fertig() } },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.speichern))
        }
    }
}
