package de.vanhake.dayorganizer.ui.aktivitaeten

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.vanhake.dayorganizer.R
import de.vanhake.dayorganizer.ui.gemeinsam.Stepper
import de.vanhake.dayorganizer.ui.gemeinsam.StepperZeile
import de.vanhake.dayorganizer.ui.gemeinsam.StepperZeilenInhalt
import de.vanhake.dayorganizer.ui.gemeinsam.UhrzeitDialog
import de.vanhake.dayorganizer.ui.theme.AKTIVITAET_SYMBOLE
import de.vanhake.dayorganizer.ui.theme.DayOrganizerFormen
import de.vanhake.dayorganizer.ui.theme.DayOrganizerTheme
import de.vanhake.dayorganizer.ui.theme.aktivitaetSymbol
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime

/** Wofuer der offene Zeitwaehler gerade waehlt. */
private enum class UhrzeitZiel { NEU, VON, BIS }

@Composable
fun AktivitaetBearbeitenScreen(
    id: Long,
    fertig: (SpeicherErgebnis) -> Unit,
    zurueck: () -> Unit,
    viewModel: AktivitaetBearbeitenViewModel = hiltViewModel(),
) {
    val entwurf by viewModel.entwurf.collectAsStateWithLifecycle()
    val fehler by viewModel.fehler.collectAsStateWithLifecycle()
    val bereich = rememberCoroutineScope()
    val farben = DayOrganizerTheme.farben
    var uhrzeitZiel by remember { mutableStateOf<UhrzeitZiel?>(null) }

    LaunchedEffect(id) { viewModel.laden(id) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(farben.bg)
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Kopfzeile(
            titel = stringResource(if (id == 0L) R.string.neue_aktivitaet else R.string.aktivitaet_bearbeiten_titel),
            zurueck = zurueck,
        )

        Abschnitt(R.string.feld_symbol) {
            SymbolRaster(entwurf.symbol) { neu -> viewModel.aendern { it.copy(symbol = neu) } }
        }

        Abschnitt(R.string.feld_name) {
            NameFeld(
                name = entwurf.name,
                fehlerhaft = EntwurfFehler.NAME_LEER in fehler,
                aendern = { neu -> viewModel.aendern { it.copy(name = neu) } },
            )
            FehlerText(EntwurfFehler.NAME_LEER in fehler, R.string.fehler_name_leer)
        }

        StepperZeile(titel = stringResource(R.string.feld_punkte)) {
            Stepper(
                wert = entwurf.basisPunkte,
                verringern = { viewModel.aendern { it.punkteAendern(-1) } },
                erhoehen = { viewModel.aendern { it.punkteAendern(+1) } },
                verringernMoeglich = entwurf.basisPunkte > AktivitaetEntwurf.PUNKTE_MIN,
            )
        }

        Abschnitt(R.string.feld_zeitplan) {
            TypKarten(entwurf.typ) { typ -> viewModel.aendern { it.copy(typ = typ) } }
        }

        if (entwurf.typ != ZeitplanTyp.WOCHENZIEL) {
            Abschnitt(R.string.feld_wochentage) {
                WochentagKacheln(entwurf.wochentage) { tag -> viewModel.aendern { it.wochentagUmschalten(tag) } }
                FehlerText(EntwurfFehler.KEIN_WOCHENTAG in fehler, R.string.fehler_kein_wochentag)
            }
        }

        if (entwurf.typ == ZeitplanTyp.FESTE_ZEITEN || entwurf.typ == ZeitplanTyp.TAGESZIEL) {
            Abschnitt(R.string.feld_uhrzeiten) {
                UhrzeitChips(
                    zeiten = entwurf.zeiten,
                    entfernen = { zeit -> viewModel.aendern { it.zeitEntfernen(zeit) } },
                    hinzufuegen = { uhrzeitZiel = UhrzeitZiel.NEU },
                )
                FehlerText(EntwurfFehler.KEINE_ZEIT in fehler, R.string.fehler_keine_zeit)
            }
        }

        if (entwurf.typ == ZeitplanTyp.INTERVALL) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IntervallKarte(
                    entwurf = entwurf,
                    intervallAendern = { schritt -> viewModel.aendern { it.intervallAendern(schritt) } },
                    vonWaehlen = { uhrzeitZiel = UhrzeitZiel.VON },
                    bisWaehlen = { uhrzeitZiel = UhrzeitZiel.BIS },
                )
                FehlerText(EntwurfFehler.INTERVALL_UNGUELTIG in fehler, R.string.fehler_intervall)
            }
        }

        if (entwurf.typ == ZeitplanTyp.TAGESZIEL || entwurf.typ == ZeitplanTyp.WOCHENZIEL) {
            StepperZeile(
                titel = stringResource(R.string.feld_zielanzahl),
                untertitel = stringResource(
                    if (entwurf.typ == ZeitplanTyp.WOCHENZIEL) R.string.ziel_einheit_woche else R.string.ziel_einheit_tag,
                ),
            ) {
                Stepper(
                    wert = entwurf.zielAnzahl,
                    verringern = { viewModel.aendern { it.zielAendern(-1) } },
                    erhoehen = { viewModel.aendern { it.zielAendern(+1) } },
                    verringernMoeglich = entwurf.zielAnzahl > AktivitaetEntwurf.ZIEL_MIN,
                )
            }
        }

        PlanZusammenfassung(PlanText.aus(entwurf.zeitplan()))

        Button(
            onClick = { bereich.launch { viewModel.speichern()?.let(fertig) } },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = DayOrganizerFormen.feld,
            colors = ButtonDefaults.buttonColors(containerColor = farben.accent, contentColor = farben.onAccent),
        ) {
            Text(stringResource(R.string.speichern), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        }
    }

    uhrzeitZiel?.let { ziel ->
        UhrzeitDialog(
            start = when (ziel) {
                UhrzeitZiel.NEU -> entwurf.zeiten.lastOrNull() ?: LocalTime.of(8, 0)
                UhrzeitZiel.VON -> entwurf.fensterVon
                UhrzeitZiel.BIS -> entwurf.fensterBis
            },
            bestaetigen = { zeit ->
                viewModel.aendern {
                    when (ziel) {
                        UhrzeitZiel.NEU -> it.zeitHinzufuegen(zeit)
                        UhrzeitZiel.VON -> it.copy(fensterVon = zeit)
                        UhrzeitZiel.BIS -> it.copy(fensterBis = zeit)
                    }
                }
                uhrzeitZiel = null
            },
            abbrechen = { uhrzeitZiel = null },
        )
    }
}

@Composable
private fun Kopfzeile(titel: String, zurueck: () -> Unit) {
    Row(
        modifier = Modifier.offset(x = (-8).dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconButton(onClick = zurueck) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.zurueck),
                tint = DayOrganizerTheme.farben.ink,
            )
        }
        Text(titel, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = DayOrganizerTheme.farben.ink)
    }
}

@Composable
private fun Abschnitt(titelId: Int, inhalt: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(titelId),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = DayOrganizerTheme.farben.ink2,
        )
        inhalt()
    }
}

@Composable
private fun FehlerText(sichtbar: Boolean, textId: Int) {
    if (sichtbar) {
        Text(
            stringResource(textId),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = DayOrganizerTheme.farben.coral,
        )
    }
}

@Composable
private fun SymbolRaster(gewaehlt: String, waehlen: (String) -> Unit) {
    val farben = DayOrganizerTheme.farben
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AKTIVITAET_SYMBOLE.chunked(6).forEach { reihe ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                reihe.forEach { schluessel ->
                    val aktiv = schluessel == gewaehlt
                    Surface(
                        selected = aktiv,
                        onClick = { waehlen(schluessel) },
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                        shape = DayOrganizerFormen.knopf,
                        color = if (aktiv) farben.accent else farben.surface,
                        contentColor = if (aktiv) farben.onAccent else farben.ink2,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            // Der Schluessel ist zugleich die Bedienhilfe-Beschreibung: es gibt
                            // keine deutschen Namen fuer die Symbole, und ein leeres
                            // contentDescription machte das Raster fuer TalkBack stumm.
                            Icon(aktivitaetSymbol(schluessel), contentDescription = schluessel, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NameFeld(name: String, fehlerhaft: Boolean, aendern: (String) -> Unit) {
    val farben = DayOrganizerTheme.farben
    OutlinedTextField(
        value = name,
        onValueChange = aendern,
        placeholder = { Text(stringResource(R.string.name_platzhalter)) },
        singleLine = true,
        isError = fehlerhaft,
        shape = DayOrganizerFormen.knopf,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = farben.surface,
            unfocusedContainerColor = farben.surface,
            errorContainerColor = farben.surface,
            focusedBorderColor = farben.accent,
            unfocusedBorderColor = farben.line,
            errorBorderColor = farben.coral,
            focusedTextColor = farben.ink,
            unfocusedTextColor = farben.ink,
            errorTextColor = farben.ink,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

private data class TypKarte(val typ: ZeitplanTyp, val titelId: Int, val hinweisId: Int)

private val TYP_KARTEN = listOf(
    TypKarte(ZeitplanTyp.FESTE_ZEITEN, R.string.typ_feste_zeiten, R.string.typ_feste_zeiten_hinweis),
    TypKarte(ZeitplanTyp.INTERVALL, R.string.typ_intervall, R.string.typ_intervall_hinweis),
    TypKarte(ZeitplanTyp.TAGESZIEL, R.string.typ_tagesziel, R.string.typ_tagesziel_hinweis),
    TypKarte(ZeitplanTyp.WOCHENZIEL, R.string.typ_wochenziel, R.string.typ_wochenziel_hinweis),
)

@Composable
private fun TypKarten(gewaehlt: ZeitplanTyp, waehlen: (ZeitplanTyp) -> Unit) {
    val farben = DayOrganizerTheme.farben
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TYP_KARTEN.chunked(2).forEach { reihe ->
            // IntrinsicSize.Min + fillMaxHeight: beide Karten einer Reihe gleich hoch, auch
            // wenn ein Hinweistext bei grosser Schrift umbricht.
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                reihe.forEach { karte ->
                    val aktiv = karte.typ == gewaehlt
                    Surface(
                        selected = aktiv,
                        onClick = { waehlen(karte.typ) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        shape = DayOrganizerFormen.knopf,
                        color = if (aktiv) farben.accentSoft else farben.surface,
                        border = BorderStroke(2.dp, if (aktiv) farben.accent else Color.Transparent),
                    ) {
                        Column(
                            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                stringResource(karte.titelId),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (aktiv) farben.onAccentSoft else farben.ink,
                            )
                            Text(stringResource(karte.hinweisId), fontSize = 12.sp, color = farben.ink2)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WochentagKacheln(gewaehlt: Set<DayOfWeek>, umschalten: (DayOfWeek) -> Unit) {
    val farben = DayOrganizerTheme.farben
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DayOfWeek.entries.forEach { tag ->
            val aktiv = tag in gewaehlt
            Surface(
                selected = aktiv,
                onClick = { umschalten(tag) },
                modifier = Modifier.weight(1f).height(44.dp),
                shape = DayOrganizerFormen.kachel,
                color = if (aktiv) farben.accent else farben.surface,
                contentColor = if (aktiv) farben.onAccent else farben.ink2,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(PlanText.kuerzel(tag), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UhrzeitChips(zeiten: List<LocalTime>, entfernen: (LocalTime) -> Unit, hinzufuegen: () -> Unit) {
    val farben = DayOrganizerTheme.farben
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        zeiten.forEach { zeit ->
            val text = PlanText.uhrzeit(zeit)
            Surface(shape = DayOrganizerFormen.chip, color = farben.accentSoft, contentColor = farben.onAccentSoft) {
                Row(
                    modifier = Modifier.height(40.dp).padding(start = 14.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    IconButton(onClick = { entfernen(zeit) }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.uhrzeit_entfernen, text),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
        Surface(
            onClick = hinzufuegen,
            shape = DayOrganizerFormen.chip,
            color = farben.surface,
            contentColor = farben.ink,
            border = BorderStroke(1.5.dp, farben.line),
        ) {
            Row(
                modifier = Modifier.height(40.dp).padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.uhrzeit_hinzufuegen), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(farben.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = farben.onAccent, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun IntervallKarte(
    entwurf: AktivitaetEntwurf,
    intervallAendern: (Int) -> Unit,
    vonWaehlen: () -> Unit,
    bisWaehlen: () -> Unit,
) {
    val farben = DayOrganizerTheme.farben
    Surface(shape = DayOrganizerFormen.feld, color = farben.surface, contentColor = farben.ink) {
        Column {
            StepperZeilenInhalt(titel = stringResource(R.string.feld_intervall)) {
                Stepper(
                    wert = entwurf.intervallStunden,
                    verringern = { intervallAendern(-1) },
                    erhoehen = { intervallAendern(+1) },
                    verringernMoeglich = entwurf.intervallStunden > AktivitaetEntwurf.INTERVALL_MIN,
                    erhoehenMoeglich = entwurf.intervallStunden < AktivitaetEntwurf.INTERVALL_MAX,
                )
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = farben.line)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.feld_zeitfenster), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ZeitKnopf(entwurf.fensterVon, vonWaehlen)
                    Text(stringResource(R.string.zeitfenster_bis), fontSize = 14.sp, color = farben.ink2)
                    ZeitKnopf(entwurf.fensterBis, bisWaehlen)
                }
            }
        }
    }
}

@Composable
private fun ZeitKnopf(zeit: LocalTime, waehlen: () -> Unit) {
    Surface(
        onClick = waehlen,
        shape = DayOrganizerFormen.kachel,
        color = DayOrganizerTheme.farben.surface2,
        contentColor = DayOrganizerTheme.farben.ink,
    ) {
        Text(
            PlanText.uhrzeit(zeit),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun PlanZusammenfassung(text: String) {
    Surface(
        shape = DayOrganizerFormen.knopf,
        color = DayOrganizerTheme.farben.surface2,
        contentColor = DayOrganizerTheme.farben.ink2,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Filled.EventRepeat, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
