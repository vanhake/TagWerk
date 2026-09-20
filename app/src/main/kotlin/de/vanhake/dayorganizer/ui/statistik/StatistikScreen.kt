package de.vanhake.dayorganizer.ui.statistik

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.vanhake.dayorganizer.R
import kotlin.math.roundToInt

@Composable
fun StatistikScreen(viewModel: StatistikViewModel = hiltViewModel()) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30, 90, 365).forEach { tage ->
                    FilterChip(
                        selected = zustand.zeitraumTage == tage,
                        onClick = { viewModel.zeitraumWaehlen(tage) },
                        label = { Text(stringResource(R.string.zeitraum_tage, tage)) },
                    )
                }
            }
        }

        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.heatmap_titel),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Heatmap(zustand.bilanzen, Modifier.padding(top = 8.dp))
                }
            }
        }

        items(zustand.zeilen, key = { it.aktivitaetId }) { zeile ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(zeile.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(
                            R.string.quote_zeile,
                            (zeile.statistik.quote * 100).roundToInt(),
                            zeile.statistik.erledigt,
                            zeile.statistik.verpasst,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        stringResource(
                            R.string.serie_zeile,
                            zeile.statistik.aktuelleSerie,
                            zeile.statistik.laengsteSerie,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
