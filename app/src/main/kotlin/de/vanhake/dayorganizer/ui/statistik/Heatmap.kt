package de.vanhake.dayorganizer.ui.statistik

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
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
 * Obergrenze fuer die Kantenlaenge einer Tageskachel. Ein Kalendergitter mit wenigen
 * Wochen soll nicht bildschirmfuellend werden, nur weil Platz da waere.
 */
private val HOECHSTE_KANTE = 18.dp

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

    // Die Kantenlaenge folgt der Breite, aber gedeckelt, und die Hoehe ergibt sich
    // daraus. Vorher war die Hoehe fest und die Kante das Minimum aus beidem -- damit
    // deckelte bei 30 Tagen die Hoehe (ein kleiner Block links oben, der Rest der
    // Karte leer) und bei 365 Tagen die Breite (ein duennes Band mit leerem Raum
    // darunter). Ohne den Deckel schluege es ins Gegenteil um: sechs Spalten ueber die
    // volle Breite ergeben bei 30 Tagen Kacheln von der Groesse einer Kachel-Karte.
    // Passt das Gitter nicht in die Breite (ein Jahr sind 53 Spalten), gewinnt die
    // Breite; sonst steht es in seiner natuerlichen Groesse mittig.
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val kante = minOf(maxWidth / wochen, HOECHSTE_KANTE)
        val breite = kante * wochen

        Canvas(Modifier.width(breite).height(kante * 7).align(Alignment.Center)) {
            zeichne(bilanzen, versatz, size.width / wochen, basis, leer)
        }
    }
}

private fun DrawScope.zeichne(
    bilanzen: List<TagesBilanz>,
    versatz: Int,
    kante: Float,
    basis: Color,
    leer: Color,
) {
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

private fun mische(von: Color, nach: Color, anteil: Float): Color = Color(
    red = von.red + (nach.red - von.red) * anteil,
    green = von.green + (nach.green - von.green) * anteil,
    blue = von.blue + (nach.blue - von.blue) * anteil,
    alpha = 1f,
)
