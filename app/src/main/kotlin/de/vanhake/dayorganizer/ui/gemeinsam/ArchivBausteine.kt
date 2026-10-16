package de.vanhake.dayorganizer.ui.gemeinsam

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.vanhake.dayorganizer.R
import de.vanhake.dayorganizer.ui.theme.DayOrganizerTheme

/** Eine archivierte Aktivitaet oder Belohnung in der Liste unter dem Archiv-Kopf. */
data class ArchivEintrag(val id: Long, val name: String, val symbol: String)

@Composable
fun ArchivierenDialog(name: String, erklaerung: String, bestaetigen: () -> Unit, abbrechen: () -> Unit) {
    AlertDialog(
        onDismissRequest = abbrechen,
        title = { Text(stringResource(R.string.archivieren_frage, name)) },
        text = { Text(erklaerung) },
        confirmButton = { TextButton(onClick = bestaetigen) { Text(stringResource(R.string.archivieren)) } },
        dismissButton = { TextButton(onClick = abbrechen) { Text(stringResource(R.string.dialog_abbrechen)) } },
    )
}

/** Einklappbarer Bereich "Archiviert (n)"; ohne Eintraege gar nicht da. Der Aufklappzustand
 * ueberlebt Rotation und Prozesstod (rememberSaveable). */
@Composable
fun ArchivBereich(
    eintraege: List<ArchivEintrag>,
    symbol: @Composable (String) -> ImageVector,
    wiederherstellen: (Long) -> Unit,
) {
    if (eintraege.isEmpty()) return
    val farben = DayOrganizerTheme.farben
    var offen by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { offen = !offen }
                .padding(horizontal = 4.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(R.string.archiv_titel, eintraege.size),
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = farben.ink2,
            )
            Icon(
                if (offen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = farben.ink2,
            )
        }
        if (offen) {
            eintraege.forEach { eintrag ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(symbol(eintrag.symbol), contentDescription = null, tint = farben.ink2, modifier = Modifier.size(20.dp))
                    Text(
                        eintrag.name,
                        modifier = Modifier.weight(1f),
                        fontSize = 14.sp,
                        color = farben.ink2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(onClick = { wiederherstellen(eintrag.id) }) {
                        Text(stringResource(R.string.wiederherstellen))
                    }
                }
            }
        }
    }
}
