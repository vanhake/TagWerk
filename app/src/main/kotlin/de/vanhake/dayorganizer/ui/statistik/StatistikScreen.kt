package de.vanhake.dayorganizer.ui.statistik

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.vanhake.dayorganizer.R
import de.vanhake.dayorganizer.ui.theme.DayOrganizerFormen
import de.vanhake.dayorganizer.ui.theme.DayOrganizerTheme
import de.vanhake.dayorganizer.ui.theme.aktivitaetSymbol
import kotlin.math.roundToInt

@Composable
fun StatistikScreen(viewModel: StatistikViewModel = hiltViewModel()) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()
    val farben = DayOrganizerTheme.farben

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(farben.bg),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                stringResource(R.string.tab_statistik_titel),
                modifier = Modifier.padding(horizontal = 4.dp),
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.02).em,
                color = farben.ink,
            )
        }

        item {
            Row(modifier = Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30, 90, 365).forEach { tage ->
                    ZeitraumPille(
                        tage = tage,
                        gewaehlt = zustand.zeitraumTage == tage,
                        waehlen = { viewModel.zeitraumWaehlen(tage) },
                    )
                }
            }
        }

        item {
            Surface(shape = DayOrganizerFormen.karte, color = farben.surface, contentColor = farben.ink) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.heatmap_titel), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        if (zustand.geladen) {
                            Text(
                                text = zustand.gesamtQuote?.let { stringResource(R.string.statistik_quote, it) }
                                    ?: stringResource(R.string.statistik_keine_faelligkeiten),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = farben.accent,
                            )
                        }
                    }
                    Heatmap(zustand.bilanzen, zustand.zeitraumTage)
                    HeatmapLegende()
                }
            }
        }

        item {
            Surface(shape = DayOrganizerFormen.karte, color = farben.surface, contentColor = farben.ink) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.punkteverlauf_titel), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        if (zustand.geladen) {
                            Text(
                                stringResource(R.string.punkteverlauf_max, zustand.punkteVerlauf.maxOfOrNull { it.second } ?: 0),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = farben.ink2,
                            )
                        }
                    }
                    Punkteverlaufsdiagramm(zustand.punkteVerlauf)
                    if (zustand.geladen) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                stringResource(R.string.punkteverlauf_von, zustand.zeitraumTage),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = farben.ink2,
                            )
                            Text(
                                stringResource(R.string.punkteverlauf_heute),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = farben.ink2,
                            )
                        }
                    }
                }
            }
        }

        items(zustand.zeilen, key = { it.aktivitaetId }) { zeile ->
            AktivitaetStatistikKarte(zeile)
        }

        if (zustand.geladen && zustand.zeilen.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.statistik_leer),
                    modifier = Modifier.padding(horizontal = 4.dp),
                    fontSize = 15.sp,
                    color = farben.ink2,
                )
            }
        }
    }
}

@Composable
private fun ZeitraumPille(tage: Int, gewaehlt: Boolean, waehlen: () -> Unit) {
    val farben = DayOrganizerTheme.farben
    Surface(
        // Auf die Pillenform beschneiden, sonst zeichnet der Ripple ein Rechteck.
        modifier = Modifier
            .height(40.dp)
            .clip(DayOrganizerFormen.pille)
            .selectable(selected = gewaehlt, role = Role.Tab, onClick = waehlen),
        shape = DayOrganizerFormen.pille,
        color = if (gewaehlt) farben.accentSoft else Color.Transparent,
        contentColor = if (gewaehlt) farben.onAccentSoft else farben.ink2,
        border = if (gewaehlt) null else BorderStroke(1.5.dp, farben.line),
    ) {
        Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.zeitraum_tage, tage), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HeatmapLegende() {
    val farben = DayOrganizerTheme.farben
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
    ) {
        Text(stringResource(R.string.heatmap_weniger), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = farben.ink2)
        repeat(HEAT_STUFEN) { stufe ->
            Box(
                Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(heatFarbe(stufe, farben)),
            )
        }
        Text(stringResource(R.string.heatmap_mehr), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = farben.ink2)
    }
}

@Composable
private fun AktivitaetStatistikKarte(zeile: StatistikZeile) {
    val farben = DayOrganizerTheme.farben
    val statistik = zeile.statistik
    // Eine neue Aktivitaet ohne erledigte/verpasste Faelligkeit hat keine Quote: "–" statt "0%".
    val ohneQuote = statistik.erledigt + statistik.verpasst == 0
    val anteil = if (ohneQuote) 0f else statistik.quote.coerceIn(0f, 1f)

    Surface(shape = DayOrganizerFormen.listenKarte, color = farben.surface, contentColor = farben.ink) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    shape = DayOrganizerFormen.chip,
                    color = farben.accentSoft,
                    // Archivierte Zeilen zeichnen das Symbol gedaempft in ink2.
                    contentColor = if (zeile.archiviert) farben.ink2 else farben.onAccentSoft,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(aktivitaetSymbol(zeile.symbol), contentDescription = null, modifier = Modifier.size(22.dp))
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(zeile.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val meta = metaText(statistik.erledigt, statistik.verpasst)
                    Text(
                        if (zeile.archiviert) stringResource(R.string.statistik_meta_archiviert, meta) else meta,
                        fontSize = 12.sp,
                        color = farben.ink2,
                    )
                }
                val leerVorlesen = stringResource(R.string.aktivitaet_quote_leer_vorlesen)
                Text(
                    if (ohneQuote) {
                        stringResource(R.string.aktivitaet_quote_leer)
                    } else {
                        stringResource(R.string.aktivitaet_quote, (statistik.quote * 100).roundToInt())
                    },
                    // TalkBack liest "–" sonst als "Strich" oder gar nicht.
                    modifier = if (ohneQuote) Modifier.semantics { contentDescription = leerVorlesen } else Modifier,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .semantics { progressBarRangeInfo = ProgressBarRangeInfo(anteil, 0f..1f) }
                    .clip(RoundedCornerShape(3.dp))
                    .background(farben.surface2),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(anteil)
                        .clip(RoundedCornerShape(3.dp))
                        .background(farben.accent),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    Icons.Filled.LocalFireDepartment,
                    contentDescription = null,
                    tint = farben.coral,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    serieText(statistik.aktuelleSerie, statistik.laengsteSerie, zeile.serieInWochen),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = farben.ink2,
                )
            }
        }
    }
}
