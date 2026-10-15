package de.vanhake.dayorganizer.ui.gemeinsam

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.vanhake.dayorganizer.R
import de.vanhake.dayorganizer.ui.theme.DayOrganizerFormen
import de.vanhake.dayorganizer.ui.theme.DayOrganizerTheme

/** Zurueck-Pfeil und Titel der Bearbeiten-Bildschirme. */
@Composable
fun Kopfzeile(titel: String, zurueck: () -> Unit) {
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

/** Kleiner Abschnittstitel (Prototyp: 13px, 700, ink2) ueber seinem Inhalt. */
@Composable
fun Abschnitt(titelId: Int, inhalt: @Composable ColumnScope.() -> Unit) {
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
fun FehlerText(sichtbar: Boolean, textId: Int) {
    if (sichtbar) {
        Text(
            stringResource(textId),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = DayOrganizerTheme.farben.coral,
        )
    }
}

/** Texteingabe im Prototyp-Stil. Mit [einzeilig] = false fuer laengere Texte (Beschreibung). */
@Composable
fun NamensFeld(
    wert: String,
    platzhalterId: Int,
    fehlerhaft: Boolean,
    aendern: (String) -> Unit,
    einzeilig: Boolean = true,
) {
    val farben = DayOrganizerTheme.farben
    OutlinedTextField(
        value = wert,
        onValueChange = aendern,
        placeholder = { Text(stringResource(platzhalterId)) },
        singleLine = einzeilig,
        minLines = if (einzeilig) 1 else 2,
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

/** Raster mit sechs Symbolen je Reihe; [symbolFuer] bildet einen Katalogschluessel ab. */
@Composable
fun SymbolRaster(
    schluessel: List<String>,
    gewaehlt: String,
    symbolFuer: (String) -> ImageVector,
    waehlen: (String) -> Unit,
) {
    val farben = DayOrganizerTheme.farben
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        schluessel.chunked(6).forEach { reihe ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                reihe.forEach { eintrag ->
                    val aktiv = eintrag == gewaehlt
                    Surface(
                        selected = aktiv,
                        onClick = { waehlen(eintrag) },
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                        shape = DayOrganizerFormen.knopf,
                        color = if (aktiv) farben.accent else farben.surface,
                        contentColor = if (aktiv) farben.onAccent else farben.ink2,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            // Der Schluessel ist zugleich die Bedienhilfe-Beschreibung: es gibt
                            // keine deutschen Namen fuer die Symbole, und ein leeres
                            // contentDescription machte das Raster fuer TalkBack stumm.
                            Icon(symbolFuer(eintrag), contentDescription = eintrag, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

data class AuswahlKarte<T>(val wert: T, val titelId: Int, val hinweisId: Int)

/** Zwei Karten je Reihe (Prototyp: Zeitplan-Typkarten). */
@Composable
fun <T> AuswahlKarten(karten: List<AuswahlKarte<T>>, gewaehlt: T, waehlen: (T) -> Unit) {
    val farben = DayOrganizerTheme.farben
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        karten.chunked(2).forEach { reihe ->
            // IntrinsicSize.Min + fillMaxHeight: beide Karten einer Reihe gleich hoch, auch
            // wenn ein Hinweistext bei grosser Schrift umbricht.
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                reihe.forEach { karte ->
                    val aktiv = karte.wert == gewaehlt
                    Surface(
                        selected = aktiv,
                        onClick = { waehlen(karte.wert) },
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
                // Eine allein stehende letzte Karte behaelt halbe Breite statt die Reihe zu fuellen.
                if (reihe.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
