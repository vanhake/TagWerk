package de.vanhake.dayorganizer.ui.belohnungen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.vanhake.dayorganizer.R
import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.ui.gemeinsam.Abschnitt
import de.vanhake.dayorganizer.ui.gemeinsam.AuswahlKarte
import de.vanhake.dayorganizer.ui.gemeinsam.AuswahlKarten
import de.vanhake.dayorganizer.ui.gemeinsam.DatumDialog
import de.vanhake.dayorganizer.ui.gemeinsam.FehlerText
import de.vanhake.dayorganizer.ui.gemeinsam.Kopfzeile
import de.vanhake.dayorganizer.ui.gemeinsam.NamensFeld
import de.vanhake.dayorganizer.ui.gemeinsam.Hinweistoast
import de.vanhake.dayorganizer.ui.gemeinsam.HinweistoastLeiste
import de.vanhake.dayorganizer.ui.gemeinsam.SpeicherErgebnis
import de.vanhake.dayorganizer.ui.gemeinsam.SpeicherFehler
import de.vanhake.dayorganizer.ui.gemeinsam.Stepper
import de.vanhake.dayorganizer.ui.gemeinsam.StepperZeile
import de.vanhake.dayorganizer.ui.gemeinsam.SymbolRaster
import de.vanhake.dayorganizer.ui.gemeinsam.ToastStapel
import de.vanhake.dayorganizer.ui.theme.BELOHNUNG_SYMBOLE
import de.vanhake.dayorganizer.ui.theme.DayOrganizerFormen
import de.vanhake.dayorganizer.ui.theme.DayOrganizerTheme
import de.vanhake.dayorganizer.ui.theme.aktivitaetSymbol
import de.vanhake.dayorganizer.ui.theme.belohnungSymbol
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DATUM_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

private val BEDINGUNG_KARTEN = listOf(
    AuswahlKarte(BedingungTyp.KEINE, R.string.bedingung_keine, R.string.bedingung_keine_hinweis),
    AuswahlKarte(BedingungTyp.MINDESTSERIE, R.string.bedingung_serie, R.string.bedingung_serie_hinweis),
    AuswahlKarte(BedingungTyp.FRUEHESTENS_AB, R.string.bedingung_datum, R.string.bedingung_datum_hinweis),
)

@Composable
fun BelohnungBearbeitenScreen(
    id: Long,
    fertig: (SpeicherErgebnis) -> Unit,
    zurueck: () -> Unit,
    viewModel: BelohnungBearbeitenViewModel = hiltViewModel(),
) {
    val entwurf by viewModel.entwurf.collectAsStateWithLifecycle()
    val fehler by viewModel.fehler.collectAsStateWithLifecycle()
    val auswahl by viewModel.auswahl.collectAsStateWithLifecycle()
    val speicherFehler by viewModel.speicherFehler.collectAsStateWithLifecycle()
    val bereich = rememberCoroutineScope()
    val farben = DayOrganizerTheme.farben
    var datumOffen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(id) { viewModel.laden(id) }

    // Einmalig zeigen, dann zuruecksetzen -- Muster der meldung im EinstellungenScreen.
    LaunchedEffect(speicherFehler) {
        if (speicherFehler == null) return@LaunchedEffect
        delay(ToastStapel.TOAST_DAUER_MS)
        viewModel.speicherFehlerGezeigt()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(farben.bg)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Kopfzeile(
                titel = stringResource(if (id == 0L) R.string.neue_belohnung else R.string.belohnung_bearbeiten_titel),
                zurueck = zurueck,
            )

            Abschnitt(R.string.feld_symbol) {
                SymbolRaster(BELOHNUNG_SYMBOLE, entwurf.symbol, ::belohnungSymbol) { neu ->
                    viewModel.aendern { it.copy(symbol = neu) }
                }
            }

            Abschnitt(R.string.feld_name) {
                NamensFeld(
                    wert = entwurf.name,
                    platzhalterId = R.string.belohnung_name_platzhalter,
                    fehlerhaft = BelohnungFehler.NAME_LEER in fehler,
                    aendern = { neu -> viewModel.aendern { it.copy(name = neu) } },
                )
                FehlerText(BelohnungFehler.NAME_LEER in fehler, R.string.fehler_name_leer)
            }

            Abschnitt(R.string.feld_beschreibung) {
                NamensFeld(
                    wert = entwurf.beschreibung,
                    platzhalterId = R.string.beschreibung_platzhalter,
                    fehlerhaft = false,
                    aendern = { neu -> viewModel.aendern { it.copy(beschreibung = neu) } },
                    einzeilig = false,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StepperZeile(titel = stringResource(R.string.feld_preis)) {
                    Stepper(
                        wert = entwurf.preisPunkte,
                        verringern = { viewModel.aendern { it.preisAendern(-1) } },
                        erhoehen = { viewModel.aendern { it.preisAendern(+1) } },
                        verringernMoeglich = entwurf.preisPunkte > BelohnungEntwurf.PREIS_MIN,
                    )
                }
                FehlerText(BelohnungFehler.PREIS_NICHT_POSITIV in fehler, R.string.fehler_preis)
            }

            Abschnitt(R.string.feld_bedingung) {
                AuswahlKarten(BEDINGUNG_KARTEN, entwurf.bedingungTyp) { typ ->
                    viewModel.aendern { it.copy(bedingungTyp = typ) }
                }
            }

            when (entwurf.bedingungTyp) {
                BedingungTyp.KEINE -> Unit

                BedingungTyp.MINDESTSERIE -> {
                    Abschnitt(R.string.feld_bedingung_aktivitaet) {
                        if (auswahl.isEmpty()) {
                            Text(stringResource(R.string.keine_aktivitaeten_fuer_serie), fontSize = 14.sp, color = farben.ink2)
                        } else {
                            AktivitaetsChips(auswahl, entwurf.bedingungAktivitaetId) { gewaehlt ->
                                viewModel.aendern { it.copy(bedingungAktivitaetId = gewaehlt) }
                            }
                        }
                        FehlerText(BelohnungFehler.SERIE_OHNE_AKTIVITAET in fehler, R.string.fehler_serie_aktivitaet)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StepperZeile(titel = stringResource(R.string.feld_bedingung_perioden)) {
                            Stepper(
                                wert = entwurf.bedingungPerioden,
                                verringern = { viewModel.aendern { it.periodenAendern(-1) } },
                                erhoehen = { viewModel.aendern { it.periodenAendern(+1) } },
                                verringernMoeglich = entwurf.bedingungPerioden > BelohnungEntwurf.PERIODEN_MIN,
                            )
                        }
                        FehlerText(BelohnungFehler.SERIE_NICHT_POSITIV in fehler, R.string.fehler_serie_perioden)
                    }
                }

                BedingungTyp.FRUEHESTENS_AB -> Abschnitt(R.string.feld_datum) {
                    Surface(
                        onClick = { datumOffen = true },
                        shape = DayOrganizerFormen.feld,
                        color = farben.surface,
                        contentColor = farben.ink,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Filled.Event, contentDescription = null, tint = farben.ink2)
                            Text(
                                entwurf.bedingungDatum?.format(DATUM_FORMAT) ?: stringResource(R.string.datum_waehlen),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    FehlerText(BelohnungFehler.DATUM_FEHLT in fehler, R.string.bedingung_datum_fehlt)
                }
            }

            Button(
                onClick = { bereich.launch { viewModel.speichern()?.let(fertig) } },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = DayOrganizerFormen.feld,
                colors = ButtonDefaults.buttonColors(containerColor = farben.accent, contentColor = farben.onAccent),
            ) {
                Text(stringResource(R.string.speichern), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            }
        }

        speicherFehler?.let { grund ->
            HinweistoastLeiste(
                toast = Hinweistoast(
                    stringResource(
                        if (grund == SpeicherFehler.NICHT_MEHR_VORHANDEN) {
                            R.string.speichern_nicht_mehr_vorhanden
                        } else {
                            R.string.speichern_fehlgeschlagen
                        },
                    ),
                    kannRueckgaengig = false,
                ),
                rueckgaengig = {},
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            )
        }
    }

    if (datumOffen) {
        DatumDialog(
            start = entwurf.bedingungDatum ?: LocalDate.now(),
            bestaetigen = { datum ->
                viewModel.aendern { it.copy(bedingungDatum = datum) }
                datumOffen = false
            },
            abbrechen = { datumOffen = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AktivitaetsChips(aktivitaeten: List<Aktivitaet>, gewaehlt: Long?, waehlen: (Long) -> Unit) {
    val farben = DayOrganizerTheme.farben
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        aktivitaeten.forEach { aktivitaet ->
            val aktiv = aktivitaet.id == gewaehlt
            Surface(
                selected = aktiv,
                onClick = { waehlen(aktivitaet.id) },
                shape = DayOrganizerFormen.chip,
                color = if (aktiv) farben.accentSoft else farben.surface,
                contentColor = if (aktiv) farben.onAccentSoft else farben.ink,
                border = BorderStroke(2.dp, if (aktiv) farben.accent else Color.Transparent),
            ) {
                Row(
                    modifier = Modifier.height(40.dp).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(aktivitaetSymbol(aktivitaet.symbol), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(aktivitaet.name, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
