package de.vanhake.dayorganizer.ui.heute

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.vanhake.dayorganizer.R
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import de.vanhake.dayorganizer.platform.permission.einstellungOeffnen
import de.vanhake.dayorganizer.ui.gemeinsam.HinweistoastLeiste
import de.vanhake.dayorganizer.ui.gemeinsam.WarnBanner
import de.vanhake.dayorganizer.ui.theme.DayOrganizerFesteFarben
import de.vanhake.dayorganizer.ui.theme.DayOrganizerFormen
import de.vanhake.dayorganizer.ui.theme.DayOrganizerTheme
import de.vanhake.dayorganizer.ui.theme.aktivitaetSymbol

@Composable
fun HeuteScreen(viewModel: HeuteViewModel = hiltViewModel()) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val kontext = LocalContext.current

    LifecycleResumeEffect(Unit) {
        viewModel.beimSichtbarwerden()
        onPauseOrDispose { }
    }

    val heldenEintrag = zustand.ueberfaellig.firstOrNull()
        ?: zustand.anstehend.firstOrNull()
        ?: zustand.spaeter.firstOrNull()

    Box(Modifier.fillMaxSize().background(DayOrganizerTheme.farben.bg)) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(zustand.hinweise, key = { it.name }) { hinweis ->
                Box(Modifier.padding(horizontal = 16.dp)) {
                    WarnBanner(
                        hinweis = hinweis,
                        beheben = {
                            hinweis.einstellungOeffnen(kontext)
                        },
                    )
                }
            }

            item { Kopf(zustand) }

            if (heldenEintrag != null) {
                item {
                    HeldenKarte(
                        eintrag = heldenEintrag,
                        erledigen = { viewModel.erledigen(heldenEintrag.faelligkeitId) },
                        auslassen = { viewModel.auslassen(heldenEintrag.faelligkeitId) },
                    )
                }
            } else if (zustand.erledigtHeute == zustand.gesamtHeute && zustand.gesamtHeute > 0) {
                item { AllesErledigtKarte() }
            }

            item { FortschrittsKarte(zustand) }

            abschnitt(
                R.string.abschnitt_ueberfaellig,
                zustand.ueberfaellig.filter { it.faelligkeitId != heldenEintrag?.faelligkeitId },
                viewModel,
            )
            abschnitt(
                R.string.abschnitt_anstehend,
                zustand.anstehend.filter { it.faelligkeitId != heldenEintrag?.faelligkeitId },
                viewModel,
            )
            abschnitt(
                R.string.abschnitt_spaeter,
                zustand.spaeter.filter { it.faelligkeitId != heldenEintrag?.faelligkeitId },
                viewModel,
            )
            abschnitt(R.string.abschnitt_aufgaben, zustand.aufgaben, viewModel)
            abschnitt(R.string.abschnitt_abgeschlossen, zustand.abgeschlossen, viewModel)
        }

        toast?.let { aktuellerToast ->
            HinweistoastLeiste(
                toast = aktuellerToast,
                rueckgaengig = viewModel::rueckgaengig,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            )
        }
    }
}

private fun LazyListScope.abschnitt(
    titelId: Int,
    eintraege: List<HeuteEintrag>,
    viewModel: HeuteViewModel,
) {
    if (eintraege.isEmpty()) return
    item {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(titelId),
                style = MaterialTheme.typography.titleSmall,
                color = DayOrganizerTheme.farben.ink2,
            )
            Text(
                eintraege.size.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = DayOrganizerTheme.farben.ink2,
            )
        }
    }
    items(eintraege, key = { it.faelligkeitId }) { eintrag ->
        Box(Modifier.padding(horizontal = 16.dp)) {
            EintragZeile(
                eintrag = eintrag,
                erledigen = { viewModel.erledigen(eintrag.faelligkeitId) },
                auslassen = { viewModel.auslassen(eintrag.faelligkeitId) },
            )
        }
    }
}

@Composable
private fun Kopf(zustand: HeuteZustand) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Bottom,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = DayOrganizerTheme.farben.goldInk)
            Text(
                stringResource(R.string.punktestand, zustand.kontostand),
                color = DayOrganizerTheme.farben.goldInk,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

@Composable
private fun HeldenKarte(eintrag: HeuteEintrag, erledigen: () -> Unit, auslassen: () -> Unit) {
    val heldenFarbe = if (eintrag.ueberfaellig) {
        DayOrganizerFesteFarben.heroUeberfaelligSolid
    } else {
        DayOrganizerTheme.farben.hero
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = DayOrganizerFormen.heldenKarte,
        color = heldenFarbe,
        contentColor = Color.White,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = DayOrganizerFormen.kachel,
                    color = DayOrganizerFesteFarben.heroChipflaeche,
                ) {
                    Icon(
                        aktivitaetSymbol(eintrag.symbol),
                        contentDescription = null,
                        modifier = Modifier.padding(18.dp).size(24.dp),
                        tint = Color.White,
                    )
                }
                Column {
                    Text(eintrag.aktivitaetName, fontWeight = FontWeight.ExtraBold)
                    Text(eintrag.zeitText ?: stringResource(R.string.ziel_fortschritt, eintrag.istAnzahl, eintrag.sollAnzahl))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = erledigen,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = heldenFarbe),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                        Text(stringResource(R.string.aktion_erledigt))
                    }
                }
                OutlinedIconButton(
                    onClick = auslassen,
                    border = BorderStroke(1.5.dp, DayOrganizerFesteFarben.heroGeisterlinie),
                    colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = Color.White),
                ) {
                    Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.aktion_ausgelassen))
                }
            }
        }
    }
}

@Composable
private fun AllesErledigtKarte() {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = DayOrganizerFormen.karte,
        color = DayOrganizerTheme.farben.greenSoft,
        contentColor = DayOrganizerTheme.farben.ink,
    ) {
        Row(Modifier.padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.heute_alles_erledigt), fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun FortschrittsKarte(zustand: HeuteZustand) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = DayOrganizerFormen.karte,
        color = DayOrganizerTheme.farben.surface,
        contentColor = DayOrganizerTheme.farben.ink,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = {
                        if (zustand.gesamtHeute == 0) 0f
                        else (zustand.erledigtHeute.toFloat() / zustand.gesamtHeute).coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxSize(),
                    color = DayOrganizerTheme.farben.accent,
                    trackColor = DayOrganizerTheme.farben.surface2,
                    strokeWidth = 7.dp,
                )
                Text(
                    "${zustand.erledigtHeute}/${zustand.gesamtHeute}",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Column(Modifier.padding(start = 16.dp)) {
                Text(
                    stringResource(R.string.tagesfortschritt, zustand.erledigtHeute, zustand.gesamtHeute),
                    fontWeight = FontWeight.Bold,
                )
                zustand.naechsteBelohnung?.let { ziel ->
                    Text(
                        if (ziel.fehlendePunkte > 0) {
                            stringResource(R.string.noch_bis_belohnung, ziel.fehlendePunkte, ziel.name)
                        } else {
                            stringResource(R.string.belohnung_bereit, ziel.name)
                        },
                        color = DayOrganizerTheme.farben.ink2,
                    )
                }
            }
        }
    }
}

@Composable
private fun EintragZeile(eintrag: HeuteEintrag, erledigen: () -> Unit, auslassen: () -> Unit) {
    val farben = DayOrganizerTheme.farben
    val erledigt = eintrag.status == FaelligkeitStatus.ERLEDIGT
    val ausgelassen = eintrag.status == FaelligkeitStatus.AUSGELASSEN
    val kachelFarbe = when {
        eintrag.ueberfaellig -> farben.coralSoft
        erledigt -> farben.greenSoft
        ausgelassen -> farben.surface2
        else -> farben.accentSoft
    }
    val kachelInk = when {
        eintrag.ueberfaellig -> farben.coral
        erledigt -> farben.green
        ausgelassen -> farben.ink2
        else -> farben.onAccentSoft
    }

    Surface(shape = DayOrganizerFormen.zeile, color = farben.surface, contentColor = farben.ink) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = DayOrganizerFormen.kachel, color = kachelFarbe, contentColor = kachelInk) {
                Icon(
                    aktivitaetSymbol(eintrag.symbol),
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp).size(24.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    eintrag.aktivitaetName,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val tage = eintrag.deadlineInTagen
                val untertitel = if (tage != null) {
                    deadlineText(tage)
                } else {
                    eintrag.zeitText
                        ?: stringResource(R.string.ziel_fortschritt, eintrag.istAnzahl, eintrag.sollAnzahl)
                }
                val untertitelFarbe = when {
                    eintrag.ueberfaellig -> farben.coral
                    tage != null && tage <= DEADLINE_BALD_TAGE -> farben.goldInk
                    else -> farben.ink2
                }
                Text(untertitel, color = untertitelFarbe)
            }
            // Eine Einmalaufgabe laesst sich nicht auslassen, nach der Deadline (VERPASST)
            // aber noch ohne Punkte abhaken.
            val aufgabe = eintrag.deadlineInTagen != null
            if (eintrag.status == FaelligkeitStatus.OFFEN && !aufgabe) {
                IconButton(onClick = auslassen) {
                    Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.aktion_ausgelassen), tint = farben.ink2)
                }
            }
            if (eintrag.status == FaelligkeitStatus.OFFEN || aufgabe) {
                OutlinedIconButton(onClick = erledigen) {
                    Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.aktion_erledigt), tint = farben.accent)
                }
            }
        }
    }
}

/** Ab so vielen Tagen vor der Deadline wird der Hinweis hervorgehoben. */
private const val DEADLINE_BALD_TAGE = 3L

@Composable
private fun deadlineText(tage: Long): String = when {
    tage < 0 -> pluralStringResource(R.plurals.deadline_ueberfaellig, (-tage).toInt(), (-tage).toInt())
    tage == 0L -> stringResource(R.string.deadline_heute)
    tage == 1L -> stringResource(R.string.deadline_morgen)
    else -> pluralStringResource(R.plurals.deadline_noch, tage.toInt(), tage.toInt())
}
