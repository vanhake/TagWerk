package de.vanhake.dayorganizer.ui.gemeinsam

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.vanhake.dayorganizer.ui.theme.DayOrganizerFormen
import de.vanhake.dayorganizer.ui.theme.DayOrganizerTheme

data class Segment<T>(val wert: T, val titelId: Int, val symbol: ImageVector)

/** Zwei oder mehr gleich breite Segmente in einer Mulde (Prototyp sOpt, Hell/Dunkel).
 * [aktiv] sperrt die Auswahl, solange die Werte noch nicht geladen sind. */
@Composable
fun <T> Segmentschalter(
    segmente: List<Segment<T>>,
    gewaehlt: T,
    waehlen: (T) -> Unit,
    modifier: Modifier = Modifier,
    aktiv: Boolean = true,
) {
    val farben = DayOrganizerTheme.farben
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(DayOrganizerFormen.feld)
            .background(farben.surface2)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        segmente.forEach { segment ->
            val gewaehlteAnzeige = segment.wert == gewaehlt
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(DayOrganizerFormen.kachel)
                    .background(if (gewaehlteAnzeige) farben.surface else Color.Transparent)
                    .selectable(
                        selected = gewaehlteAnzeige,
                        enabled = aktiv,
                        role = Role.Tab,
                        onClick = { waehlen(segment.wert) },
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                val inhaltsfarbe = if (gewaehlteAnzeige) farben.ink else farben.ink2
                Icon(segment.symbol, contentDescription = null, modifier = Modifier.size(18.dp), tint = inhaltsfarbe)
                Text(
                    stringResource(segment.titelId),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = inhaltsfarbe,
                )
            }
        }
    }
}
