package de.vanhake.dayorganizer.ui.statistik

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.vanhake.dayorganizer.R
import de.vanhake.dayorganizer.domain.rechner.TagesBilanz
import de.vanhake.dayorganizer.ui.theme.DayOrganizerFarben
import de.vanhake.dayorganizer.ui.theme.DayOrganizerTheme
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Obergrenze fuer die Kantenlaenge einer Tageskachel. Ein Kalendergitter mit wenigen
 * Wochen soll nicht bildschirmfuellend werden, nur weil Platz da waere. Nur die
 * 30-Tage-Ansicht (sechs Spalten) darf groesser werden; bei 90 und 365 Tagen
 * begrenzt ohnehin die Breite.
 */
private val HOECHSTE_KANTE = 18.dp
private val HOECHSTE_KANTE_30_TAGE = 34.dp

/** Eckradius und Abstand der Kacheln je Zeitraum: kleine Gitter bekommen weichere Kacheln. */
private fun radiusDp(zeitraumTage: Int): Float = when (zeitraumTage) {
    30 -> 7f
    90 -> 4f
    else -> 2f
}

private fun abstandDp(zeitraumTage: Int): Float = when (zeitraumTage) {
    30 -> 6f
    90 -> 3f
    else -> 2f
}

/**
 * Farbe einer Heatmap-Stufe 0..4: Stufe 0 ist die leere Flaeche, Stufe 4 die volle
 * Akzentfarbe, dazwischen Mischungen zu 28, 52 und 76 Prozent (linear in sRGB, eine
 * bewusste Vereinfachung gegenueber der oklch-Mischung des Prototyps).
 */
fun heatFarbe(stufe: Int, farben: DayOrganizerFarben): Color = when (stufe) {
    0 -> farben.surface2
    1 -> mische(farben.surface2, farben.accent, 0.28f)
    2 -> mische(farben.surface2, farben.accent, 0.52f)
    3 -> mische(farben.surface2, farben.accent, 0.76f)
    else -> farben.accent
}

/**
 * Kalender-Heatmap im Wochenraster: eine Spalte je Woche, sieben Zeilen von
 * Montag bis Sonntag. Die Faerbung ist die Stufe aus [heatStufe].
 */
@Composable
fun Heatmap(bilanzen: List<TagesBilanz>, zeitraumTage: Int, modifier: Modifier = Modifier) {
    if (bilanzen.isEmpty()) return
    val farben = DayOrganizerTheme.farben

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
        val kante = minOf(maxWidth / wochen, if (zeitraumTage == 30) HOECHSTE_KANTE_30_TAGE else HOECHSTE_KANTE)
        val breite = kante * wochen

        // TalkBack liest das Gitter nicht aus dem Canvas: der Container traegt die
        // Zusammenfassung, darueber liegt je Woche eine unsichtbare Box in Spaltenbreite
        // mit dem Wochentext. Breite und Mittigkeit wie beim Canvas, sonst laegen die
        // Fokusrahmen neben den Spalten.
        val zusammenfassung = heatmapZusammenfassung(bilanzen).let {
            stringResource(R.string.heatmap_vorlesen, it.tage, it.vollstaendig, it.teilweise, it.nichtsErledigt, it.frei)
        }
        val wochenTexte = heatmapWochen(bilanzen).map { wochenText(it) }

        Box(
            Modifier
                .width(breite)
                .height(kante * 7)
                .align(Alignment.Center),
        ) {
            // Die Zusammenfassung sitzt am Canvas-Blatt: ein Container mit fokussierbaren
            // Kindern ohne mergeDescendants wird von TalkBack nie selbst fokussiert. Das
            // Canvas steht vor dem Wochen-Overlay und kommt damit zuerst.
            Canvas(Modifier.fillMaxSize().clearAndSetSemantics { contentDescription = zusammenfassung }) {
                zeichne(bilanzen, versatz, size.width / wochen, zeitraumTage, farben)
            }
            Row(Modifier.fillMaxSize()) {
                wochenTexte.forEach { text ->
                    Box(Modifier.width(kante).fillMaxHeight().semantics { contentDescription = text })
                }
            }
        }
    }
}

private val WOCHENTAG: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE", Locale.GERMAN)
private val DATUM: DateTimeFormatter = DateTimeFormatter.ofPattern("d. MMMM", Locale.GERMAN)

@Composable
private fun wochenText(woche: List<TagesBilanz>): String {
    val tage = woche.map { bilanz ->
        val name = WOCHENTAG.format(bilanz.tag)
        when {
            bilanz.gesamt == 0 -> stringResource(R.string.heatmap_tag_frei, name)
            bilanz.erledigt == bilanz.gesamt -> stringResource(R.string.heatmap_tag_vollstaendig, name)
            else -> stringResource(R.string.heatmap_tag_teilweise, name, bilanz.erledigt, bilanz.gesamt)
        }
    }
    return stringResource(R.string.heatmap_woche_vorlesen, DATUM.format(woche.first().tag), tage.joinToString(", "))
}

private fun DrawScope.zeichne(
    bilanzen: List<TagesBilanz>,
    versatz: Int,
    kante: Float,
    zeitraumTage: Int,
    farben: DayOrganizerFarben,
) {
    val abstand = abstandDp(zeitraumTage).dp.toPx()
    val radius = radiusDp(zeitraumTage).dp.toPx()

    bilanzen.forEachIndexed { index, bilanz ->
        val position = index + versatz
        val spalte = position / 7
        val zeile = position % 7

        drawRoundRect(
            color = heatFarbe(heatStufe(bilanz), farben),
            topLeft = Offset(spalte * kante, zeile * kante),
            size = Size(kante - abstand, kante - abstand),
            cornerRadius = CornerRadius(radius),
        )
    }
}

private fun mische(von: Color, nach: Color, anteil: Float): Color = Color(
    red = von.red + (nach.red - von.red) * anteil,
    green = von.green + (nach.green - von.green) * anteil,
    blue = von.blue + (nach.blue - von.blue) * anteil,
    alpha = 1f,
)
