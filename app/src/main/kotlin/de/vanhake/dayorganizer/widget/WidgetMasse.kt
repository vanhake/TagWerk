package de.vanhake.dayorganizer.widget

import kotlin.math.max

/** Reine Rechnung, wie viele Zeilen das Widget bei gegebener Hoehe zeigen kann. */
object WidgetMasse {

    private const val MAX_ZEILEN = 4
    private const val KARTE_INNENABSTAND_DP = 16f
    private const val ABSTAND_DP = 10f
    private const val KOPF_MIN_DP = 28f
    // 21 statt 20: bei 200 % Schrift wuchsen Pille und Kopfzeile auf dem Emulator ca. 2 dp
    // ueber die Annahme hinaus.
    private const val KOPF_SCHRIFT_DP = 21f
    private const val ZEILE_INNENABSTAND_DP = 16f
    private const val ZEILE_TEXT_MIN_DP = 30f

    // Namenszeile 14 sp (ca. 20 dp) plus Untertitelzeile 12 sp (ca. 16 dp).
    private const val ZEILE_TEXT_DP = 36f
    private const val FUSS_SCHRIFT_DP = 16f

    // Leerhinweis (14 sp, ca. 20 dp) mit je 8 dp Innenabstand oben und unten.
    private const val LEERHINWEIS_SCHRIFT_DP = 20f
    private const val LEERHINWEIS_INNENABSTAND_DP = 8f

    /**
     * Groesstes k aus 0..min(4, offen), bei dem Kopf, k Zeilen und (falls noch Eintraege
     * uebrig bleiben) die Fusszeile "+ n weitere" in die Hoehe passen. Grosse Systemschrift
     * ([schriftFaktor]) macht Kopf, Zeilen und Fusszeile hoeher, es passen dann weniger --
     * so wird keine Zeile mittendrin abgeschnitten. Passt nicht einmal k = 0 (sehr
     * kleine Hoehe, grosse Schrift), ist das Ergebnis 0 statt einer Ausnahme.
     */
    fun passendeZeilen(hoeheDp: Float, offen: Int, schriftFaktor: Float): Int {
        val kopf = max(KOPF_MIN_DP, KOPF_SCHRIFT_DP * schriftFaktor)
        val zeile = ZEILE_INNENABSTAND_DP + max(ZEILE_TEXT_MIN_DP, ZEILE_TEXT_DP * schriftFaktor)
        return (0..minOf(MAX_ZEILEN, offen)).lastOrNull { k ->
            val fuss = if (offen > k) ABSTAND_DP + FUSS_SCHRIFT_DP * schriftFaktor else 0f
            2 * KARTE_INNENABSTAND_DP + kopf + k * (ABSTAND_DP + zeile) + fuss <= hoeheDp
        } ?: 0
    }

    /**
     * Ob unter dem Kopf der Leerhinweis ("Alles erledigt fuer heute" / "Heute steht nichts
     * an") samt Abstand in die Hoehe passt. Bei grosser Schrift waere er sonst am unteren
     * Rand abgeschnitten; dann entfaellt er ganz.
     */
    fun leerhinweisPasst(hoeheDp: Float, schriftFaktor: Float): Boolean {
        val kopf = max(KOPF_MIN_DP, KOPF_SCHRIFT_DP * schriftFaktor)
        val hinweis = 2 * LEERHINWEIS_INNENABSTAND_DP + LEERHINWEIS_SCHRIFT_DP * schriftFaktor
        return 2 * KARTE_INNENABSTAND_DP + kopf + ABSTAND_DP + hinweis <= hoeheDp
    }
}
