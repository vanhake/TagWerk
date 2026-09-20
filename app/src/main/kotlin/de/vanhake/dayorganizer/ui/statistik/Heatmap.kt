package de.vanhake.dayorganizer.ui.statistik

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.vanhake.dayorganizer.domain.rechner.TagesBilanz
import java.time.DayOfWeek

/**
 * Mindestanteil, mit dem ein belegter Tag eingefaerbt wird, selbst wenn die Quote
 * genau null ist. Ohne diese Untergrenze ergibt mische(leer, basis, 0f) exakt [leer]
 * -- ein Tag mit drei verpassten Faelligkeiten saehe dann genauso aus wie ein Tag
 * ganz ohne Faelligkeit. Auf einem Bildschirm, dessen Zweck Rechenschaft ist, muss
 * "belegt und nichts geschafft" sichtbar anders aussehen als "nichts geplant".
 */
private const val MINDESTANTEIL_BELEGTER_TAG = 0.18f

/**
 * Kalender-Heatmap im Wochenraster: eine Spalte je Woche, sieben Zeilen von
 * Montag bis Sonntag. Die Faerbung ist der Anteil erledigter Faelligkeiten.
 */
@Composable
fun Heatmap(bilanzen: List<TagesBilanz>, modifier: Modifier = Modifier) {
    if (bilanzen.isEmpty()) return
    val basis = MaterialTheme.colorScheme.primary
    val leer = MaterialTheme.colorScheme.surfaceVariant

    val ersterTag = bilanzen.first().tag
    val versatz = ersterTag.dayOfWeek.value - DayOfWeek.MONDAY.value
    val wochen = (bilanzen.size + versatz + 6) / 7

    Canvas(modifier.fillMaxWidth().height((7 * 14).dp)) {
        val kante = minOf(size.width / wochen, size.height / 7)
        val abstand = kante * 0.15f

        bilanzen.forEachIndexed { index, bilanz ->
            val position = index + versatz
            val spalte = position / 7
            val zeile = position % 7
            val anteil = if (bilanz.gesamt == 0) {
                0f
            } else {
                (bilanz.erledigt.toFloat() / bilanz.gesamt).coerceAtLeast(MINDESTANTEIL_BELEGTER_TAG)
            }

            drawRect(
                color = if (bilanz.gesamt == 0) leer else mische(leer, basis, anteil),
                topLeft = Offset(spalte * kante, zeile * kante),
                size = Size(kante - abstand, kante - abstand),
            )
        }
    }
}

private fun mische(von: Color, nach: Color, anteil: Float): Color = Color(
    red = von.red + (nach.red - von.red) * anteil,
    green = von.green + (nach.green - von.green) * anteil,
    blue = von.blue + (nach.blue - von.blue) * anteil,
    alpha = 1f,
)
