package de.vanhake.dayorganizer.ui.statistik

import de.vanhake.dayorganizer.domain.rechner.TagesBilanz
import kotlin.math.roundToInt

/** Anzahl der Heatmap-Stufen von 0 (keine Faelligkeit) bis 4 (alles erledigt). */
const val HEAT_STUFEN = 5

/**
 * Stufe einer Heatmap-Kachel von 0 bis 4.
 *
 * Stufe 1 gilt auch bei Anteil 0: "belegt und nichts geschafft" muss sichtbar anders
 * aussehen als "nichts geplant" (Stufe 0). Auf einem Bildschirm, dessen Zweck
 * Rechenschaft ist, waeren beide sonst nicht zu unterscheiden. Die Grenzen werden mit
 * Ganzzahlen verglichen, damit 1/3 und 2/3 ohne Gleitkomma-Rundung exakt treffen.
 */
fun heatStufe(bilanz: TagesBilanz): Int = when {
    bilanz.gesamt == 0 -> 0
    bilanz.erledigt * 3 <= bilanz.gesamt -> 1
    bilanz.erledigt * 3 <= bilanz.gesamt * 2 -> 2
    bilanz.erledigt < bilanz.gesamt -> 3
    else -> 4
}

/** Gesamtquote in Prozent ueber alle Tage; null, wenn nichts faellig war. */
fun gesamtQuoteProzent(bilanzen: List<TagesBilanz>): Int? {
    val gesamt = bilanzen.sumOf { it.gesamt }
    if (gesamt == 0) return null
    val erledigt = bilanzen.sumOf { it.erledigt }
    return (erledigt.toDouble() / gesamt * 100).roundToInt()
}

// Kotlin-Literale statt strings.xml, damit die Texte ohne Context testbar sind
// (wie PlanText).
fun serieText(aktuell: Int, laengste: Int, inWochen: Boolean): String =
    "Serie ${einheit(aktuell, inWochen)} · längste ${einheit(laengste, inWochen)}"

fun metaText(erledigt: Int, verpasst: Int): String = "$erledigt geschafft · $verpasst verpasst"

private fun einheit(anzahl: Int, inWochen: Boolean): String = when {
    inWochen -> if (anzahl == 1) "1 Woche" else "$anzahl Wochen"
    else -> if (anzahl == 1) "1 Tag" else "$anzahl Tage"
}
