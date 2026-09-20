package de.vanhake.dayorganizer.ui.statistik

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/**
 * Schlichtes Liniendiagramm des kumulierten Punktestands ueber den gewaehlten
 * Zeitraum. Aufbau und Groesse orientieren sich an Heatmap.kt, das direkt
 * darueber in derselben Art von Karte liegt.
 */
@Composable
fun Punkteverlaufsdiagramm(verlauf: List<Pair<LocalDate, Int>>, modifier: Modifier = Modifier) {
    if (verlauf.isEmpty()) return
    val linie = MaterialTheme.colorScheme.primary

    val werte = verlauf.map { it.second }
    val minimum = werte.min()
    val maximum = werte.max()
    val spanne = maximum - minimum

    Canvas(modifier.fillMaxWidth().height((7 * 14).dp)) {
        val strichbreite = 3.dp.toPx()

        if (werte.size == 1) {
            // Ein einzelner Punkt hat keine Strecke -- als Punkt in der Mitte
            // zeichnen, statt durch (Anzahl - 1) Stuetzstellen zu teilen.
            drawCircle(color = linie, radius = 4.dp.toPx(), center = Offset(size.width / 2f, size.height / 2f))
            return@Canvas
        }

        // Ohne Innenabstand liegt der Hoechstwert exakt auf y = 0 und die halbe
        // Strichbreite wird vom oberen Rand abgeschnitten -- auf dem Emulator war die
        // Spitze der Kurve sichtbar gekappt. Der Rand haelt Hoechst- und Tiefstwert
        // um die halbe Strichbreite plus etwas Luft vom Kartenrand fern.
        val rand = strichbreite
        val nutzbar = size.height - 2 * rand

        fun hoehe(wert: Int): Float = if (spanne == 0) {
            // Durchgehend derselbe Wert: eine waagrechte Linie in der Mitte statt
            // Division durch die Spanne null.
            size.height / 2f
        } else {
            rand + nutzbar - (wert - minimum).toFloat() / spanne * nutzbar
        }

        val breite = size.width / (werte.size - 1)
        val punkte = werte.mapIndexed { index, wert -> Offset(index * breite, hoehe(wert)) }

        for (i in 0 until punkte.size - 1) {
            drawLine(
                color = linie,
                start = punkte[i],
                end = punkte[i + 1],
                strokeWidth = strichbreite,
            )
        }
    }
}
