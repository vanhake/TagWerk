package de.vanhake.dayorganizer.ui.belohnungen

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.onClick
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
import de.vanhake.dayorganizer.domain.model.Einloesung
import de.vanhake.dayorganizer.domain.rechner.Hindernis
import de.vanhake.dayorganizer.ui.gemeinsam.ArchivBereich
import de.vanhake.dayorganizer.ui.gemeinsam.ArchivierenDialog
import de.vanhake.dayorganizer.ui.gemeinsam.Hinweistoast
import de.vanhake.dayorganizer.ui.gemeinsam.HinweistoastLeiste
import de.vanhake.dayorganizer.ui.gemeinsam.SpeicherErgebnis
import de.vanhake.dayorganizer.ui.theme.DayOrganizerFesteFarben
import de.vanhake.dayorganizer.ui.theme.DayOrganizerFormen
import de.vanhake.dayorganizer.ui.theme.DayOrganizerTheme
import de.vanhake.dayorganizer.ui.theme.belohnungSymbol
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter

private fun EinloeseMeldung.textId(): Int = when (this) {
    EinloeseMeldung.ZU_WENIG_PUNKTE -> R.string.noch_punkte_noetig
    EinloeseMeldung.SERIE_ZU_KURZ -> R.string.serie_zu_kurz
    EinloeseMeldung.ZU_FRUEH -> R.string.noch_zu_frueh
    EinloeseMeldung.NICHT_MEHR_VORHANDEN -> R.string.einloese_nicht_mehr_vorhanden
    EinloeseMeldung.FEHLER -> R.string.einloese_fehlgeschlagen
    EinloeseMeldung.RUECKGAENGIG_FEHLGESCHLAGEN -> R.string.rueckgaengig_fehlgeschlagen
}

private val DATUM_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

/** Wie lange eine Fehlschlag-Meldung steht -- gleich lang wie die Toasts der ViewModels. */
private const val MELDUNG_DAUER_MS = 3_800L

@Composable
fun BelohnungenScreen(
    bearbeiten: (Long) -> Unit,
    gespeichert: SpeicherErgebnis?,
    gespeichertGezeigt: () -> Unit,
    viewModel: BelohnungenViewModel = hiltViewModel(),
) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val meldung by viewModel.meldung.collectAsStateWithLifecycle()
    val archiviert by viewModel.archiviert.collectAsStateWithLifecycle()
    var archivierenId by rememberSaveable { mutableStateOf<Long?>(null) }
    val kontext = LocalContext.current
    val farben = DayOrganizerTheme.farben

    // Ergebnis aus dem Editor einmalig melden und im savedStateHandle loeschen -- Muster
    // AktivitaetenScreen.
    LaunchedEffect(gespeichert) {
        gespeichert?.let {
            viewModel.gespeichertMelden(it)
            gespeichertGezeigt()
        }
    }

    // Fehlschlag-Meldungen stehen so lange wie ein Toast und werden dann zurueckgesetzt --
    // sonst erschiene dieselbe Meldung nach jeder Rotation erneut. Die Wartezeit beginnt erst,
    // wenn kein Toast mehr sichtbar ist: der Toast hat Vorrang, die Meldung ginge sonst hinter
    // ihm ab, ohne je gezeigt worden zu sein.
    LaunchedEffect(meldung, toast == null) {
        if (meldung == null || toast != null) return@LaunchedEffect
        delay(MELDUNG_DAUER_MS)
        viewModel.meldungGezeigt()
    }

    val meldungsToast = meldung?.let { aktuelle ->
        val text = if (aktuelle == EinloeseMeldung.ZU_WENIG_PUNKTE) {
            kontext.getString(aktuelle.textId(), viewModel.meldungFehlendePunkte.value)
        } else {
            kontext.getString(aktuelle.textId())
        }
        Hinweistoast(text, kannRueckgaengig = false)
    }
    // Der Toast hat Vorrang: er gehoert zur letzten erfolgreichen Aktion.
    val sichtbarerToast = toast ?: meldungsToast

    Box(Modifier.fillMaxSize().background(farben.bg)) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.tab_belohnungen_titel),
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.02).em,
                    color = farben.ink,
                )
            }

            item { PunktestandKarte(zustand.kontostand) }

            if (zustand.kacheln.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.belohnungen_leer),
                        modifier = Modifier.padding(horizontal = 4.dp),
                        fontSize = 15.sp,
                        color = farben.ink2,
                    )
                }
            }

            // "kachel-"/"einloesung-" statt der nackten id: beide ids sind eigene, je bei 1
            // beginnende Autoincrement-Folgen und teilen sich sonst den Key-Namensraum der
            // LazyColumn -- Compose stuerzt bei gleicher id ab.
            items(zustand.kacheln, key = { "kachel-${it.id}" }) { kachel ->
                BelohnungKarte(
                    kachel = kachel,
                    kontostand = zustand.kontostand,
                    bearbeiten = { bearbeiten(kachel.id) },
                    einloesen = { viewModel.einloesen(kachel.id) },
                    archivieren = { archivierenId = kachel.id },
                )
            }

            item(key = "archiv") {
                ArchivBereich(
                    eintraege = archiviert,
                    symbol = { belohnungSymbol(it) },
                    wiederherstellen = { viewModel.wiederherstellen(it) },
                )
            }

            if (zustand.einloesungen.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.einloese_historie).uppercase(),
                        modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 0.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.06.em,
                        color = farben.ink2,
                    )
                }
                items(zustand.einloesungen, key = { "einloesung-${it.id}" }) { einloesung ->
                    HistorienZeile(einloesung)
                }
            }
        }

        if (sichtbarerToast == null) {
            ExtendedFloatingActionButton(
                onClick = { bearbeiten(0L) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.neue_belohnung), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) },
                shape = DayOrganizerFormen.feld,
                containerColor = farben.accent,
                contentColor = farben.onAccent,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }

        zustand.kacheln.firstOrNull { it.id == archivierenId }?.let { kachel ->
            ArchivierenDialog(
                name = kachel.name,
                erklaerung = stringResource(R.string.archivieren_erklaerung_belohnung),
                bestaetigen = {
                    viewModel.archivieren(kachel.id)
                    archivierenId = null
                },
                abbrechen = { archivierenId = null },
            )
        }

        sichtbarerToast?.let { aktueller ->
            HinweistoastLeiste(
                toast = aktueller,
                rueckgaengig = { viewModel.rueckgaengig() },
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            )
        }
    }
}

@Composable
private fun PunktestandKarte(kontostand: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = DayOrganizerFormen.heldenKarte,
        color = DayOrganizerTheme.farben.hero,
        contentColor = Color.White,
    ) {
        Row(
            modifier = Modifier.padding(22.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.dein_punktestand),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.85f),
                )
                Text(
                    kontostand.toString(),
                    fontSize = 48.sp,
                    lineHeight = 48.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.03).em,
                )
            }
            Icon(
                Icons.Filled.Star,
                contentDescription = null,
                tint = DayOrganizerFesteFarben.heroStern,
                modifier = Modifier.size(44.dp),
            )
        }
    }
}

@Composable
private fun BelohnungKarte(
    kachel: BelohnungKachel,
    kontostand: Int,
    bearbeiten: () -> Unit,
    einloesen: () -> Unit,
    archivieren: () -> Unit,
) {
    val farben = DayOrganizerTheme.farben
    val moeglich = kachel.einloesbarkeit.moeglich
    // Preis 0 kann nur aus einem von Hand geaenderten Backup kommen (Editor und Import-Pfad
    // erzeugen ihn nicht); ohne Schutz waere das 0/0 = NaN.
    val anteil = if (kachel.preisPunkte <= 0) 1f else (kontostand.toFloat() / kachel.preisPunkte).coerceIn(0f, 1f)
    val bearbeitenText = stringResource(R.string.belohnung_bearbeiten_aktion)

    Surface(
        onClick = bearbeiten,
        // Beschriftet die Klick-Aktion fuer TalkBack ("Bearbeiten" statt "Aktivieren").
        modifier = Modifier.semantics { onClick(label = bearbeitenText) { bearbeiten(); true } },
        shape = DayOrganizerFormen.karte,
        color = farben.surface,
        contentColor = farben.ink,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                Surface(
                    shape = DayOrganizerFormen.knopf,
                    color = farben.goldSoft,
                    contentColor = farben.goldInk,
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(belohnungSymbol(kachel.symbol), contentDescription = null, modifier = Modifier.size(26.dp))
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(kachel.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (kachel.beschreibung.isNotBlank()) {
                        Text(kachel.beschreibung, fontSize = 13.sp, lineHeight = 17.sp, color = farben.ink2)
                    }
                }
                Text(
                    stringResource(R.string.punkte_kurz, kachel.preisPunkte),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                )
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .semantics { progressBarRangeInfo = ProgressBarRangeInfo(anteil, 0f..1f) }
                    .clip(RoundedCornerShape(4.dp))
                    .background(farben.surface2),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(anteil)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (moeglich) farben.green else farben.accent),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val (symbol, text) = hinweis(kachel)
                Row(
                    Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val hinweisFarbe = if (moeglich) farben.green else farben.ink2
                    Icon(symbol, contentDescription = null, tint = hinweisFarbe, modifier = Modifier.size(18.dp))
                    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = hinweisFarbe)
                }
                IconButton(onClick = archivieren) {
                    Icon(Icons.Outlined.Archive, contentDescription = stringResource(R.string.archivieren), tint = farben.ink2)
                }
                Button(
                    onClick = einloesen,
                    enabled = moeglich && !kachel.wirdEingeloest,
                    shape = DayOrganizerFormen.kachel,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = farben.accent,
                        contentColor = farben.onAccent,
                        disabledContainerColor = farben.surface2,
                        disabledContentColor = farben.ink2,
                    ),
                ) {
                    Text(stringResource(R.string.einloesen), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun hinweis(kachel: BelohnungKachel): Pair<ImageVector, String> =
    when (kachel.einloesbarkeit.hindernis) {
        Hindernis.ZU_WENIG_PUNKTE -> Icons.Filled.HourglassTop to
            stringResource(R.string.noch_punkte_noetig, kachel.einloesbarkeit.fehlendePunkte)
        Hindernis.SERIE_ZU_KURZ -> Icons.Filled.LocalFireDepartment to stringResource(R.string.serie_zu_kurz)
        Hindernis.ZU_FRUEH -> Icons.Filled.LockClock to stringResource(R.string.noch_zu_frueh)
        null -> Icons.Filled.CheckCircle to stringResource(R.string.bereit_zum_einloesen)
    }

@Composable
private fun HistorienZeile(einloesung: Einloesung) {
    val farben = DayOrganizerTheme.farben
    Column(Modifier.padding(horizontal = 4.dp)) {
        HorizontalDivider(color = farben.line)
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(einloesung.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = farben.ink)
                Text(einloesung.zeitpunkt.format(DATUM_FORMAT), fontSize = 12.sp, color = farben.ink2)
            }
            Text(
                stringResource(R.string.einloesung_abzug, einloesung.preisPunkte),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = farben.ink2,
            )
        }
    }
}
