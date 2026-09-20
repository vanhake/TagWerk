package de.vanhake.dayorganizer.ui.heute

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.vanhake.dayorganizer.R
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import de.vanhake.dayorganizer.platform.permission.einstellungsIntent
import de.vanhake.dayorganizer.ui.gemeinsam.WarnBanner

@Composable
fun HeuteScreen(viewModel: HeuteViewModel = hiltViewModel()) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()
    val kontext = LocalContext.current

    // Bei jedem Sichtbarwerden neu pruefen: der Nutzer kommt aus den
    // Systemeinstellungen zurueck, und dann muss der Banner verschwinden.
    LifecycleResumeEffect(Unit) {
        viewModel.beimSichtbarwerden()
        onPauseOrDispose { }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(zustand.hinweise, key = { it.name }) { hinweis ->
            WarnBanner(
                hinweis = hinweis,
                beheben = {
                    ContextCompat.startActivity(kontext, hinweis.einstellungsIntent(kontext), null)
                },
            )
        }

        item { Kopf(zustand) }

        abschnitt(R.string.abschnitt_ueberfaellig, zustand.ueberfaellig, viewModel)
        abschnitt(R.string.abschnitt_anstehend, zustand.anstehend, viewModel)
        abschnitt(R.string.abschnitt_spaeter, zustand.spaeter, viewModel)
        abschnitt(R.string.abschnitt_abgeschlossen, zustand.abgeschlossen, viewModel)
    }
}

private fun LazyListScope.abschnitt(
    titelId: Int,
    eintraege: List<HeuteEintrag>,
    viewModel: HeuteViewModel,
) {
    if (eintraege.isEmpty()) return
    item {
        Text(stringResource(titelId), style = MaterialTheme.typography.titleSmall)
    }
    items(eintraege, key = { it.faelligkeitId }) { eintrag ->
        EintragZeile(
            eintrag = eintrag,
            erledigen = { viewModel.erledigen(eintrag.faelligkeitId) },
            auslassen = { viewModel.auslassen(eintrag.faelligkeitId) },
        )
    }
    item { HorizontalDivider() }
}

@Composable
private fun Kopf(zustand: HeuteZustand) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.punktestand, zustand.kontostand),
                style = MaterialTheme.typography.headlineSmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(
                    progress = {
                        val anteil =
                            if (zustand.gesamtHeute == 0) 0f
                            else zustand.erledigtHeute.toFloat() / zustand.gesamtHeute
                        anteil.coerceIn(0f, 1f)
                    },
                )
                Text(
                    stringResource(R.string.tagesfortschritt, zustand.erledigtHeute, zustand.gesamtHeute),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            zustand.naechsteBelohnung?.let { ziel ->
                Text(
                    if (ziel.fehlendePunkte > 0) {
                        stringResource(R.string.noch_bis_belohnung, ziel.fehlendePunkte, ziel.name)
                    } else {
                        stringResource(R.string.belohnung_bereit, ziel.name)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun EintragZeile(eintrag: HeuteEintrag, erledigen: () -> Unit, auslassen: () -> Unit) {
    Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(eintrag.aktivitaetName, style = MaterialTheme.typography.titleMedium)
                val untertitel = eintrag.zeitText
                    ?: stringResource(R.string.ziel_fortschritt, eintrag.istAnzahl, eintrag.sollAnzahl)
                Text(untertitel, style = MaterialTheme.typography.bodySmall)
            }
            if (eintrag.status == FaelligkeitStatus.OFFEN) {
                TextButton(onClick = auslassen) { Text(stringResource(R.string.aktion_ausgelassen)) }
                TextButton(onClick = erledigen) { Text(stringResource(R.string.aktion_erledigt)) }
            }
        }
    }
}
