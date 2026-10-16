package de.vanhake.dayorganizer.ui.gemeinsam

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.vanhake.dayorganizer.ui.theme.DayOrganizerTheme

data class NavigationsEintrag(
    val ziel: String,
    val titelId: Int,
    val symbolAktiv: ImageVector,
    val symbolInaktiv: ImageVector,
)

/** Untere Tab-Leiste im Stil des Prototyps (showNav): aktiver Tab als Pille mit gefuelltem Symbol. */
@Composable
fun Navigationsleiste(
    eintraege: List<NavigationsEintrag>,
    aktuell: String?,
    waehlen: (String) -> Unit,
) {
    val farben = DayOrganizerTheme.farben
    Column(modifier = Modifier.fillMaxWidth().background(farben.surface)) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(farben.line))
        Row(
            modifier = Modifier
                .selectableGroup()
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 4.dp, top = 10.dp, end = 4.dp, bottom = 6.dp),
        ) {
            eintraege.forEach { eintrag ->
                val aktiv = eintrag.ziel == aktuell
                val inhaltsfarbe = if (aktiv) farben.onAccentSoft else farben.ink2
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .selectable(selected = aktiv, role = Role.Tab, onClick = { waehlen(eintrag.ziel) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .width(60.dp)
                            .height(32.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (aktiv) farben.accentSoft else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (aktiv) eintrag.symbolAktiv else eintrag.symbolInaktiv,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = inhaltsfarbe,
                        )
                    }
                    // Einzeilig mit Auslassungszeichen: bei grosser Schrift passen fuenf
                    // Beschriftungen nicht nebeneinander, und ein Umbruch mitten im Wort
                    // ("Statisti/k") ist schlechter als ein gekuerztes "Statis...".
                    Text(
                        stringResource(eintrag.titelId),
                        fontSize = 12.sp,
                        fontWeight = if (aktiv) FontWeight.ExtraBold else FontWeight.SemiBold,
                        color = inhaltsfarbe,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
