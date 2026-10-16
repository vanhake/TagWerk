package de.vanhake.dayorganizer.ui.einstellungen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.vanhake.dayorganizer.R
import de.vanhake.dayorganizer.data.ThemeModus
import de.vanhake.dayorganizer.platform.permission.einstellungsIntent
import de.vanhake.dayorganizer.ui.gemeinsam.Hinweistoast
import de.vanhake.dayorganizer.ui.gemeinsam.HinweistoastLeiste
import de.vanhake.dayorganizer.ui.gemeinsam.Segment
import de.vanhake.dayorganizer.ui.gemeinsam.Segmentschalter
import de.vanhake.dayorganizer.ui.gemeinsam.Stepper
import de.vanhake.dayorganizer.ui.gemeinsam.StepperZeilenInhalt
import de.vanhake.dayorganizer.ui.gemeinsam.WarnBanner
import de.vanhake.dayorganizer.ui.theme.DayOrganizerFormen
import de.vanhake.dayorganizer.ui.theme.DayOrganizerTheme
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

/** Wie lange eine Export-/Import-Meldung steht -- gleich lang wie die Toasts der ViewModels. */
private const val MELDUNG_DAUER_MS = 3_800L

private fun Meldung.textId(): Int = when (this) {
    Meldung.EXPORT_FERTIG -> R.string.backup_export_fertig
    Meldung.EXPORT_FEHLGESCHLAGEN -> R.string.backup_export_fehlgeschlagen
    Meldung.IMPORT_FERTIG -> R.string.backup_import_fertig
    Meldung.IMPORT_EINSTELLUNGEN_FEHLGESCHLAGEN -> R.string.backup_import_einstellungen_fehlgeschlagen
    Meldung.IMPORT_FALSCHES_SCHEMA -> R.string.backup_falsches_schema
    Meldung.IMPORT_UNLESBAR -> R.string.backup_unlesbar
}

@Composable
fun EinstellungenScreen(viewModel: EinstellungenViewModel = hiltViewModel()) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()
    val meldung by viewModel.meldung.collectAsStateWithLifecycle()
    val importVorschau by viewModel.importVorschau.collectAsStateWithLifecycle()
    val kontext = LocalContext.current
    val farben = DayOrganizerTheme.farben

    // Bei jedem Sichtbarwerden neu pruefen: der Nutzer kommt aus den
    // Systemeinstellungen zurueck, und dann muss der Banner verschwinden.
    LifecycleResumeEffect(Unit) {
        viewModel.beimSichtbarwerden()
        onPauseOrDispose { }
    }

    // Einmalig zeigen, dann zuruecksetzen -- sonst erschiene dieselbe Meldung bei
    // jeder Rekomposition (z. B. Bildschirmdrehung) erneut.
    LaunchedEffect(meldung) {
        if (meldung == null) return@LaunchedEffect
        delay(MELDUNG_DAUER_MS)
        viewModel.meldungGezeigt()
    }

    // Oeffnen, Schreiben/Lesen und Schliessen des Stroms muessen in derselben Koroutine
    // passieren (Review-Fund K1): der Screen reicht deshalb nur noch eine Funktion
    // herein, die den Strom oeffnet, statt ihn hier schon zu oeffnen und per `use`
    // wieder zu schliessen, bevor die Koroutine im ViewModel ueberhaupt geschrieben hat.
    val exportZiel = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let { ziel ->
            viewModel.exportieren { kontext.contentResolver.openOutputStream(ziel) }
        }
    }

    // Review-Fund G5: manche Anbieter melden .json-Dateien nicht als application/json,
    // sondern als application/octet-stream oder text/plain -- OpenDocument blendete sie
    // dann aus. Eine falsche Datei faengt ohnehin die Schemapruefung ab.
    val importQuelle = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { quelle ->
            viewModel.importVorschauAnfordern { kontext.contentResolver.openInputStream(quelle) }
        }
    }

    // Kein now(): exportDatum kommt aus dem im ViewModel injizierten Clock (Review-Fund W2).
    val backupDateiname = stringResource(R.string.backup_dateiname, viewModel.exportDatum)

    Box(Modifier.fillMaxSize().background(farben.bg)) {
        Column(
            modifier = Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 110.dp)),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.tab_einstellungen),
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.02).em,
                color = farben.ink,
            )

            zustand.hinweise.forEach { hinweis ->
                WarnBanner(
                    hinweis = hinweis,
                    beheben = {
                        ContextCompat.startActivity(kontext, hinweis.einstellungsIntent(kontext), null)
                    },
                )
            }

            AbschnittsTitel(stringResource(R.string.optionen_abschnitt_punkte))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = DayOrganizerFormen.listenKarte,
                color = farben.surface,
                contentColor = farben.ink,
            ) {
                Column {
                    StepperZeilenInhalt(
                        titel = stringResource(R.string.tagesbonus_titel),
                        untertitel = stringResource(R.string.tagesbonus_hinweis),
                    ) {
                        Stepper(
                            wert = zustand.tagesbonusPunkte,
                            verringern = { viewModel.tagesbonusAendern(-1) },
                            erhoehen = { viewModel.tagesbonusAendern(+1) },
                            // Vor dem Erstladen sind es nur Defaults -- ein Tipp wuerde sie
                            // als Basis nehmen und gespeicherte Werte ueberschreiben.
                            verringernMoeglich = zustand.geladen &&
                                zustand.tagesbonusPunkte > EinstellungenViewModel.STEPPER_MIN,
                            erhoehenMoeglich = zustand.geladen,
                        )
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = farben.line)
                    StepperZeilenInhalt(
                        titel = stringResource(R.string.kulanz_titel),
                        untertitel = stringResource(R.string.kulanz_hinweis),
                    ) {
                        Stepper(
                            wert = zustand.standardKulanzMinuten,
                            verringern = { viewModel.kulanzAendern(-1) },
                            erhoehen = { viewModel.kulanzAendern(+1) },
                            verringernMoeglich = zustand.geladen &&
                                zustand.standardKulanzMinuten > EinstellungenViewModel.STEPPER_MIN,
                            erhoehenMoeglich = zustand.geladen,
                        )
                    }
                }
            }

            AbschnittsTitel(stringResource(R.string.optionen_abschnitt_darstellung))
            Segmentschalter(
                segmente = listOf(
                    Segment(ThemeModus.HELL, R.string.theme_hell, Icons.Filled.LightMode),
                    Segment(ThemeModus.DUNKEL, R.string.theme_dunkel, Icons.Filled.DarkMode),
                ),
                gewaehlt = zustand.themeModus,
                waehlen = { viewModel.themeModusAendern(it) },
                aktiv = zustand.geladen,
            )

            AbschnittsTitel(stringResource(R.string.optionen_abschnitt_backup))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = DayOrganizerFormen.listenKarte,
                color = farben.surface,
                contentColor = farben.ink,
            ) {
                Column {
                    BackupZeile(
                        symbol = Icons.Filled.Download,
                        titel = stringResource(R.string.backup_exportieren),
                        untertitel = backupDateiname,
                        klick = { exportZiel.launch(backupDateiname) },
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = farben.line)
                    BackupZeile(
                        symbol = Icons.Filled.Upload,
                        titel = stringResource(R.string.backup_importieren),
                        untertitel = stringResource(R.string.backup_einlesen_hinweis),
                        klick = {
                            importQuelle.launch(
                                arrayOf("application/json", "application/octet-stream", "text/plain"),
                            )
                        },
                    )
                }
            }

            Text(
                stringResource(R.string.optionen_fusszeile),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
                color = farben.ink2,
            )
        }

        meldung?.let { aktuelle ->
            HinweistoastLeiste(
                toast = Hinweistoast(kontext.getString(aktuelle.textId()), kannRueckgaengig = false),
                rueckgaengig = {},
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            )
        }
    }

    // Review-Fund W2: erst nach dieser Rueckfrage wird tatsaechlich importiert --
    // "Abbrechen" laesst den Bestand unveraendert.
    importVorschau?.let { vorschau ->
        val muster = stringResource(R.string.backup_import_vorschau_datum_muster)
        val formatierer = remember(muster) { DateTimeFormatter.ofPattern(muster, Locale.getDefault()) }
        Dialog(onDismissRequest = viewModel::importVorschauAbbrechen) {
            Surface(
                shape = DayOrganizerFormen.heldenKarte,
                color = farben.surface,
                contentColor = farben.ink,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier.size(48.dp)
                            .background(farben.coralSoft, DayOrganizerFormen.kachel),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.UploadFile, contentDescription = null, tint = farben.coral)
                    }
                    Text(
                        stringResource(R.string.backup_import_vorschau_titel),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        stringResource(
                            R.string.backup_import_vorschau_text,
                            vorschau.erstelltAm.format(formatierer),
                            vorschau.anzahlAktivitaeten,
                        ),
                        fontSize = 14.sp,
                        color = farben.ink2,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = viewModel::importVorschauAbbrechen) {
                            Text(
                                stringResource(R.string.backup_import_vorschau_abbrechen),
                                color = farben.ink,
                            )
                        }
                        Button(
                            onClick = viewModel::importBestaetigen,
                            shape = DayOrganizerFormen.kachel,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = farben.coral,
                                contentColor = Color.White,
                            ),
                        ) {
                            Text(stringResource(R.string.backup_import_vorschau_bestaetigen))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AbschnittsTitel(text: String) {
    Text(
        text.uppercase(),
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.06.em,
        color = DayOrganizerTheme.farben.ink2,
    )
}

@Composable
private fun BackupZeile(symbol: ImageVector, titel: String, untertitel: String, klick: () -> Unit) {
    val farben = DayOrganizerTheme.farben
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = klick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(farben.accentSoft, DayOrganizerFormen.kachel),
            contentAlignment = Alignment.Center,
        ) {
            Icon(symbol, contentDescription = null, tint = farben.onAccentSoft)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(titel, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(untertitel, fontSize = 12.sp, color = farben.ink2)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = farben.ink2)
    }
}
