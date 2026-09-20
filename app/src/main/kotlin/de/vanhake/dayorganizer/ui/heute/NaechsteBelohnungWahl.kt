package de.vanhake.dayorganizer.ui.heute

import de.vanhake.dayorganizer.domain.model.Belohnung

/**
 * Waehlt das Ziel, das den Punkten Sinn gibt: die naechste Belohnung, auf die
 * man noch hinsparen muss. Ist alles bezahlbar, die teuerste als Ausblick.
 */
object NaechsteBelohnungWahl {

    fun aus(belohnungen: List<Belohnung>, kontostand: Int): NaechsteBelohnung? {
        val offen = belohnungen.filterNot { it.archiviert }
        if (offen.isEmpty()) return null

        val ziel = offen.filter { it.preisPunkte > kontostand }.minByOrNull { it.preisPunkte }
            ?: offen.maxByOrNull { it.preisPunkte }
            ?: return null

        return NaechsteBelohnung(
            name = ziel.name,
            preisPunkte = ziel.preisPunkte,
            fehlendePunkte = (ziel.preisPunkte - kontostand).coerceAtLeast(0),
        )
    }
}
