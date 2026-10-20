package de.vanhake.dayorganizer.ui.aktivitaeten

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.vanhake.dayorganizer.R
import de.vanhake.dayorganizer.ui.gemeinsam.ArchivBereich
import de.vanhake.dayorganizer.ui.gemeinsam.ArchivierenDialog
import de.vanhake.dayorganizer.ui.gemeinsam.HinweistoastLeiste
import de.vanhake.dayorganizer.ui.gemeinsam.SpeicherErgebnis
import de.vanhake.dayorganizer.ui.theme.DayOrganizerFormen
import de.vanhake.dayorganizer.ui.theme.DayOrganizerTheme
import de.vanhake.dayorganizer.ui.theme.aktivitaetSymbol

@Composable
fun AktivitaetenScreen(
    bearbeiten: (Long) -> Unit,
    gespeichert: SpeicherErgebnis?,
    gespeichertGezeigt: () -> Unit,
    viewModel: AktivitaetenViewModel = hiltViewModel(),
) {
    val zeilen by viewModel.zeilen.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val archiviert by viewModel.archiviert.collectAsStateWithLifecycle()
    // Dialogzustand ueber Rotation hinweg: nur die id, den Namen sucht der Screen neu aus der Liste.
    var archivierenId by rememberSaveable { mutableStateOf<Long?>(null) }
    val farben = DayOrganizerTheme.farben

    // Das Ergebnis kommt einmalig aus dem savedStateHandle des Back-Stack-Eintrags;
    // gespeichertGezeigt() loescht es dort, sonst kaeme der Toast nach jeder Rotation wieder.
    LaunchedEffect(gespeichert) {
        gespeichert?.let {
            viewModel.gespeichertMelden(it)
            gespeichertGezeigt()
        }
    }

    Box(Modifier.fillMaxSize().background(farben.bg)) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        stringResource(R.string.tab_aktivitaeten),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.02).em,
                        color = farben.ink,
                    )
                    Text(
                        stringResource(R.string.anzahl_aktiv, zeilen.size),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = farben.ink2,
                    )
                }
            }

            if (zeilen.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.aktivitaeten_leer),
                        modifier = Modifier.padding(horizontal = 4.dp),
                        fontSize = 15.sp,
                        color = farben.ink2,
                    )
                }
            }

            val (einmalig, wiederkehrend) = zeilen.partition { it.einmalig }
            items(wiederkehrend, key = { it.id }) { zeile ->
                AktivitaetZeileKarte(
                    zeile = zeile,
                    bearbeiten = { bearbeiten(zeile.id) },
                    archivieren = { archivierenId = zeile.id },
                )
            }

            if (einmalig.isNotEmpty()) {
                item(key = "einmalaufgaben") {
                    Text(
                        stringResource(R.string.abschnitt_einmalaufgaben),
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = farben.ink2,
                    )
                }
                items(einmalig, key = { it.id }) { zeile ->
                    AktivitaetZeileKarte(
                        zeile = zeile,
                        bearbeiten = { bearbeiten(zeile.id) },
                        archivieren = { archivierenId = zeile.id },
                    )
                }
            }

            item(key = "archiv") {
                ArchivBereich(
                    eintraege = archiviert,
                    symbol = { aktivitaetSymbol(it) },
                    wiederherstellen = { viewModel.wiederherstellen(it) },
                )
            }
        }

        if (toast == null) {
            ExtendedFloatingActionButton(
                onClick = { bearbeiten(0L) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.neue_aktivitaet), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) },
                shape = DayOrganizerFormen.feld,
                containerColor = farben.accent,
                contentColor = farben.onAccent,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }

        zeilen.firstOrNull { it.id == archivierenId }?.let { zeile ->
            ArchivierenDialog(
                name = zeile.name,
                erklaerung = stringResource(R.string.archivieren_erklaerung_aktivitaet),
                bestaetigen = {
                    viewModel.archivieren(zeile.id)
                    archivierenId = null
                },
                abbrechen = { archivierenId = null },
            )
        }

        toast?.let { aktuellerToast ->
            HinweistoastLeiste(
                toast = aktuellerToast,
                rueckgaengig = { viewModel.rueckgaengig() },
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            )
        }
    }
}

@Composable
private fun AktivitaetZeileKarte(zeile: AktivitaetZeile, bearbeiten: () -> Unit, archivieren: () -> Unit) {
    val farben = DayOrganizerTheme.farben
    Surface(
        onClick = bearbeiten,
        shape = DayOrganizerFormen.listenKarte,
        color = farben.surface,
        contentColor = farben.ink,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = DayOrganizerFormen.knopf,
                color = farben.accentSoft,
                contentColor = farben.onAccentSoft,
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(aktivitaetSymbol(zeile.symbol), contentDescription = null, modifier = Modifier.size(26.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    zeile.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(zeile.planText, fontSize = 13.sp, lineHeight = 17.sp, color = farben.ink2)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Plakette(
                        symbol = Icons.Filled.Star,
                        text = stringResource(R.string.punkte_kurz, zeile.basisPunkte),
                        flaeche = farben.goldSoft,
                        farbe = farben.goldInk,
                    )
                    if (!zeile.einmalig) {
                        Plakette(
                            symbol = Icons.Filled.LocalFireDepartment,
                            text = if (zeile.serieInWochen) {
                                pluralStringResource(R.plurals.serie_wochen, zeile.serie, zeile.serie)
                            } else {
                                pluralStringResource(R.plurals.serie_tage, zeile.serie, zeile.serie)
                            },
                            flaeche = farben.coralSoft,
                            farbe = farben.coral,
                        )
                    }
                }
            }
            IconButton(onClick = archivieren) {
                Icon(Icons.Outlined.Archive, contentDescription = stringResource(R.string.archivieren), tint = farben.ink2)
            }
        }
    }
}

@Composable
private fun Plakette(symbol: ImageVector, text: String, flaeche: Color, farbe: Color) {
    Row(
        modifier = Modifier
            .clip(DayOrganizerFormen.pille)
            .background(flaeche)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(symbol, contentDescription = null, tint = farbe, modifier = Modifier.size(14.dp))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = farbe)
    }
}
