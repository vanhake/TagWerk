package de.vanhake.dayorganizer.ui.aktivitaeten

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.vanhake.dayorganizer.R
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AktivitaetBearbeitenScreen(
    id: Long,
    fertig: () -> Unit,
    viewModel: AktivitaetBearbeitenViewModel = hiltViewModel(),
) {
    val entwurf by viewModel.entwurf.collectAsStateWithLifecycle()
    val fehler by viewModel.fehler.collectAsStateWithLifecycle()
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
            isError = EntwurfFehler.NAME_LEER in fehler,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = entwurf.basisPunkteText,
            onValueChange = { neu -> viewModel.aendern { it.copy(basisPunkteText = neu) } },
            label = { Text(stringResource(R.string.feld_punkte)) },
            isError = EntwurfFehler.PUNKTE_NICHT_POSITIV in fehler,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(stringResource(R.string.feld_zeitplan), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ZeitplanTyp.entries.forEach { typ ->
                FilterChip(
                    selected = entwurf.typ == typ,
                    onClick = { viewModel.aendern { it.copy(typ = typ) } },
                    label = { Text(beschriftung(typ)) },
                )
            }
        }

        if (entwurf.typ != ZeitplanTyp.WOCHENZIEL) {
            Text(stringResource(R.string.feld_wochentage), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DayOfWeek.entries.forEach { tag ->
                    FilterChip(
                        selected = tag in entwurf.wochentage,
                        onClick = {
                            viewModel.aendern {
                                it.copy(
                                    wochentage = if (tag in it.wochentage) {
                                        it.wochentage - tag
                                    } else {
                                        it.wochentage + tag
                                    },
                                )
                            }
                        },
                        label = { Text(tag.getDisplayName(TextStyle.SHORT, Locale.GERMAN)) },
                    )
                }
            }
        }

        when (entwurf.typ) {
            ZeitplanTyp.FESTE_ZEITEN, ZeitplanTyp.TAGESZIEL -> UhrzeitenFeld(entwurf, viewModel)
            ZeitplanTyp.INTERVALL -> OutlinedTextField(
                value = entwurf.intervallStundenText,
                onValueChange = { neu -> viewModel.aendern { it.copy(intervallStundenText = neu) } },
                label = { Text(stringResource(R.string.feld_intervall)) },
                isError = EntwurfFehler.INTERVALL_UNGUELTIG in fehler,
                modifier = Modifier.fillMaxWidth(),
            )
            ZeitplanTyp.WOCHENZIEL -> Unit
        }

        if (entwurf.typ == ZeitplanTyp.TAGESZIEL || entwurf.typ == ZeitplanTyp.WOCHENZIEL) {
            OutlinedTextField(
                value = entwurf.zielAnzahlText,
                onValueChange = { neu -> viewModel.aendern { it.copy(zielAnzahlText = neu) } },
                label = { Text(stringResource(R.string.feld_zielanzahl)) },
                isError = EntwurfFehler.ZIEL_NICHT_POSITIV in fehler,
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

@Composable
private fun UhrzeitenFeld(entwurf: AktivitaetEntwurf, viewModel: AktivitaetBearbeitenViewModel) {
    // Eingabe als kommagetrennte Liste im Format HH:mm. Ein Zeitwaehler-Dialog
    // waere schoener, kostet aber Aufwand, den Version 1 nicht braucht.
    OutlinedTextField(
        value = entwurf.zeiten.joinToString(", ") { it.toString() },
        onValueChange = { neu ->
            val zeiten = neu.split(",").mapNotNull { runCatching { LocalTime.parse(it.trim()) }.getOrNull() }
            viewModel.aendern { it.copy(zeiten = zeiten) }
        },
        label = { Text(stringResource(R.string.feld_uhrzeiten)) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun beschriftung(typ: ZeitplanTyp): String = stringResource(
    when (typ) {
        ZeitplanTyp.FESTE_ZEITEN -> R.string.typ_feste_zeiten
        ZeitplanTyp.INTERVALL -> R.string.typ_intervall
        ZeitplanTyp.TAGESZIEL -> R.string.typ_tagesziel
        ZeitplanTyp.WOCHENZIEL -> R.string.typ_wochenziel
    },
)
